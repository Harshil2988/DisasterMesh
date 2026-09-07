package com.disastermesh.app.sync

import android.content.Context
import android.util.Log
import com.disastermesh.app.audio.AudioMessage
import com.disastermesh.app.audio.AudioStore
import com.disastermesh.app.audio.AudioTransferManager
import com.disastermesh.app.nearby.MeshMessage
import com.disastermesh.app.nearby.MeshSyncHook
import com.disastermesh.app.nearby.NearbyConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What this device knows about one peer's synchronisation. */
data class PeerSyncState(
    val nodeId: String,
    val lastSyncAt: Long = 0L,
    val received: Int = 0,
    val sent: Int = 0,
    val syncing: Boolean = false
) {
    val complete: Boolean get() = lastSyncAt > 0 && !syncing
}

/** Aggregate sync status for the UI. */
data class SyncStatus(
    val carrying: Int = 0,
    val peers: Map<String, PeerSyncState> = emptyMap(),
    val lastEvent: String? = null
) {
    val activeSyncs: Int get() = peers.values.count { it.syncing }
    val totalReceived: Int get() = peers.values.sumOf { it.received }
    val totalSent: Int get() = peers.values.sumOf { it.sent }
}

/**
 * Store-carry-forward synchronisation.
 *
 * Lives entirely outside the mesh: it plugs into [NearbyConnectionManager]
 * through the optional [MeshSyncHook] and can be removed by setting that hook to
 * null, which restores the previous behaviour exactly.
 *
 * Real-time relay is untouched and still handles live traffic. This layer only
 * fills in what a node MISSED — the history it was not present for.
 */
class MeshSyncManager(
    context: Context,
    private val mesh: NearbyConnectionManager
) : MeshSyncHook {

    val store = MessageStore(context)

    /** Voice-message binaries. Metadata still travels as an ordinary message. */
    val audioStore = AudioStore(context)
    val audioTransfers = AudioTransferManager(audioStore, mesh)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** endpointId -> nodeId for peers currently connected. */
    private val peerNodeIds = mutableMapOf<String, String>()

    /** Ids we have already asked a given endpoint for, to avoid re-requesting. */
    private val inFlight = mutableMapOf<String, MutableSet<String>>()

    @Volatile
    private var active = false

    fun start() {
        active = true
        mesh.syncHook = this
        store.purgeExpired()
        refreshCarrying()
        Log.d(TAG, "sync: started, carrying ${store.count.value} messages")
    }

    fun stop() {
        active = false
        mesh.syncHook = null
        peerNodeIds.clear()
        inFlight.clear()
        _status.update { it.copy(peers = emptyMap()) }
        Log.d(TAG, "sync: stopped")
    }

    // -----------------------------------------------------------------
    // Hook from the mesh
    // -----------------------------------------------------------------

    /**
     * Retains every genuinely new message the mesh accepts.
     *
     * Called only after SeenMessages has confirmed novelty, so this cannot
     * double-store and cannot disagree with the network's duplicate authority.
     */
    override fun onMessageStored(message: MeshMessage) {
        if (!active) return
        val stored = StoredMessage.from(message, originalTimestamp = System.currentTimeMillis())
        if (store.store(stored)) refreshCarrying()

        // A voice message arrives as metadata first. Fetch the audio from any
        // connected peer that might hold it — pull, never push, so a clip
        // crosses each link at most once.
        AudioMessage.decode(message.payload)?.let { audio ->
            if (!audioStore.has(audio.audioId)) {
                peerNodeIds.keys.forEach { endpoint ->
                    audioTransfers.requestIfMissing(endpoint, audio)
                }
            }
        }
    }

    /** A peer arrived: offer our inventory. Never blocks the connection. */
    override fun onPeerConnected(endpointId: String, nodeId: String) {
        if (!active) return
        peerNodeIds[endpointId] = nodeId

        val previous = _status.value.peers[nodeId]
        val now = System.currentTimeMillis()
        if (previous != null && now - previous.lastSyncAt < SyncConfig.MIN_RESYNC_INTERVAL_MS) {
            // Synced with this node moments ago; nothing can have changed much.
            return
        }

        updatePeer(nodeId) { it.copy(syncing = true) }
        scope.launch {
            // Small delay so the connection settles before extra traffic.
            delay(SETTLE_MS)
            sendInventory(endpointId)
        }
    }

    override fun onFilePayload(
        endpointId: String,
        payloadId: Long,
        descriptor: android.os.ParcelFileDescriptor?
    ) {
        if (!active) return
        audioTransfers.onFilePayload(endpointId, payloadId, descriptor)
    }

    override fun onTransferUpdate(
        endpointId: String,
        payloadId: Long,
        status: Int,
        transferred: Long,
        total: Long
    ) {
        if (!active) return
        audioTransfers.onTransferUpdate(endpointId, payloadId, status, transferred, total) { id ->
            expectedAudio[id]
        }
    }

    /** payloadId -> the clip it should contain, filled in when we request one. */
    private val expectedAudio = mutableMapOf<Long, AudioMessage>()

    override fun onPeerDisconnected(endpointId: String) {
        val nodeId = peerNodeIds.remove(endpointId)
        inFlight.remove(endpointId)
        if (nodeId != null) updatePeer(nodeId) { it.copy(syncing = false) }
    }

    /**
     * Handles a sync control packet.
     *
     * @return true when the bytes were ours, so the mesh ignores them entirely.
     */
    override fun onRawPayload(endpointId: String, bytes: ByteArray): Boolean {
        if (!active) return false

        // Audio requests share the transport but are their own small protocol.
        if (audioTransfers.isAudioControl(bytes)) {
            return audioTransfers.handleControl(endpointId, bytes)
        }

        if (!SyncProtocol.isSyncPacket(bytes)) return false

        // From here on the packet is ours whatever happens — a malformed one is
        // dropped rather than falling through to the mesh parser.
        val packet = SyncProtocol.decode(bytes)
        if (packet == null) {
            Log.w(TAG, "sync: discarded malformed packet from $endpointId")
            return true
        }

        when (packet) {
            is SyncPacket.Inventory -> handleInventory(endpointId, packet.ids)
            is SyncPacket.Request -> handleRequest(endpointId, packet.ids)
            is SyncPacket.Batch -> handleBatch(endpointId, packet.messages)
        }
        return true
    }

    // -----------------------------------------------------------------
    // Protocol
    // -----------------------------------------------------------------

    private fun sendInventory(endpointId: String) {
        val ids = store.inventoryIds()
        send(endpointId, SyncProtocol.inventory(ids))
    }

    /**
     * A peer told us what it holds. We ask for what we lack, and answer with our
     * own inventory so the exchange is symmetric — either side may hold history
     * the other needs.
     */
    private fun handleInventory(endpointId: String, ids: List<String>) {
        val missing = store.missingFrom(ids)
        val pending = inFlight.getOrPut(endpointId) { mutableSetOf() }
        val toRequest = missing.filterNot { pending.contains(it) }

        if (toRequest.isNotEmpty()) {
            pending.addAll(toRequest)
            send(endpointId, SyncProtocol.request(toRequest))
        } else {
            // Nothing wanted from this peer: the exchange is finished on our side.
            peerNodeIds[endpointId]?.let { node ->
                updatePeer(node) { it.copy(syncing = false, lastSyncAt = System.currentTimeMillis()) }
            }
        }

        // Reply half. Guarded by the resync interval so two nodes cannot volley
        // inventories at each other indefinitely.
        val node = peerNodeIds[endpointId]
        val last = node?.let { _status.value.peers[it]?.lastSyncAt } ?: 0L
        if (System.currentTimeMillis() - last >= SyncConfig.MIN_RESYNC_INTERVAL_MS) {
            scope.launch {
                delay(SETTLE_MS)
                sendInventory(endpointId)
            }
        }
    }

    /** A peer asked for specific messages. Send only real, still-forwardable ones. */
    private fun handleRequest(endpointId: String, ids: List<String>) {
        scope.launch {
            val wanted = store.forRequest(ids, limit = SyncConfig.MAX_REQUEST_IDS)
            if (wanted.isEmpty()) {
                send(endpointId, SyncProtocol.batch(emptyList(), more = false))
                return@launch
            }

            // Emergency-first batching, with a pause so a burst cannot swamp the radio.
            wanted.chunked(SyncConfig.MAX_MESSAGES_PER_BATCH).forEachIndexed { index, chunk ->
                if (!active) return@launch
                val more = (index + 1) * SyncConfig.MAX_MESSAGES_PER_BATCH < wanted.size
                send(endpointId, SyncProtocol.batch(chunk, more))
                peerNodeIds[endpointId]?.let { node ->
                    updatePeer(node) { it.copy(sent = it.sent + chunk.size) }
                }
                if (more) delay(SyncConfig.BATCH_PAUSE_MS)
            }
        }
    }

    /**
     * Messages arrived from a peer's store.
     *
     * These are HISTORICAL: they are surfaced in Messages, Command Center and the
     * map through the normal pipeline, but deliberately raise no notification —
     * an SOS from twenty minutes ago should not buzz like a live one.
     */
    private fun handleBatch(endpointId: String, messages: List<StoredMessage>) {
        val fromNode = peerNodeIds[endpointId]
        var accepted = 0

        messages.forEach { incoming ->
            val pending = inFlight[endpointId]
            pending?.remove(incoming.messageId)

            val tagged = incoming.copy(syncedFrom = fromNode)
            if (store.store(tagged)) {
                accepted++
                // Historical voice messages need their audio pulled too, which
                // is what makes a newly joined node able to play old clips.
                AudioMessage.decode(tagged.payload)?.let { audio ->
                    audioTransfers.requestIfMissing(endpointId, audio)
                }
                // Same validation and duplicate protection as any other message.
                mesh.ingestHistorical(tagged.toMeshMessage(), syncedFrom = fromNode)
            }
        }

        if (accepted > 0) refreshCarrying()

        fromNode?.let { node ->
            updatePeer(node) {
                it.copy(
                    received = it.received + accepted,
                    syncing = false,
                    lastSyncAt = System.currentTimeMillis()
                )
            }
        }
        _status.update {
            it.copy(
                lastEvent = if (accepted > 0) {
                    "Synced $accepted message(s) from ${fromNode ?: "a node"}"
                } else {
                    it.lastEvent
                }
            )
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private fun send(endpointId: String, bytes: ByteArray) {
        // A transmission failure must never take the mesh down with it.
        runCatching { mesh.sendRawTo(endpointId, bytes) }
            .onFailure { Log.w(TAG, "sync: send failed", it) }
    }

    private fun updatePeer(nodeId: String, transform: (PeerSyncState) -> PeerSyncState) {
        _status.update { current ->
            val existing = current.peers[nodeId] ?: PeerSyncState(nodeId)
            current.copy(peers = current.peers + (nodeId to transform(existing)))
        }
    }

    private fun refreshCarrying() {
        _status.update { it.copy(carrying = store.count.value) }
    }

    private companion object {
        const val TAG = "DisasterMesh"
        const val SETTLE_MS = 700L
    }
}
