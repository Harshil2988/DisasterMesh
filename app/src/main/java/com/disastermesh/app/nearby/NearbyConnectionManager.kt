package com.disastermesh.app.nearby

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

/**
 * The whole mesh node: Nearby Connections plus the relay logic that turns a set
 * of direct links into a multi-hop network.
 *
 * Every device runs this identically. There are no A/B/C roles and nothing is
 * hardcoded about how many peers there may be — the mesh is simply whatever
 * peers happen to be in range.
 *
 * Nearby Connections gives us ONE thing: a byte pipe between two phones that are
 * directly connected. It does not route, forward or deduplicate anything, so
 * A -> B -> C works only because this class explicitly re-sends what it receives.
 */
class NearbyConnectionManager(context: Context) {

    private val appContext = context.applicationContext
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(appContext)

    /** Stable identity of this phone, e.g. "NODE-7F3A". */
    private val nodeId: String = MeshIdentity.nodeId(appContext)

    /** What this phone advertises, e.g. "NODE-7F3A|Pixel 7". */
    private val localEndpointName: String = MeshIdentity.endpointName(appContext)

    /** Message ids already handled — the thing that stops the flood looping. */
    private val seen = SeenMessages()

    /** Used for connection retries. Nearby's callbacks are all on the main thread. */
    private val handler = Handler(Looper.getMainLooper())

    private var meshActive = false
    private var advertising = false
    private var discovering = false

    private val _state = MutableStateFlow(
        MeshState(
            nodeId = nodeId,
            deviceModel = Build.MODEL.ifBlank { "Android" }
        )
    )
    val state: StateFlow<MeshState> = _state.asStateFlow()

    // ---------------------------------------------------------------------
    // Public actions — the UI has exactly one networking control
    // ---------------------------------------------------------------------

    /**
     * Becomes a mesh node: advertise, discover, and from then on connect to
     * whatever turns up, without any further taps.
     */
    fun startMesh() {
        if (meshActive) return
        meshActive = true
        _state.update { it.copy(meshActive = true, status = "Starting mesh…") }
        beginAdvertising()
        beginDiscovery()
    }

    /** Leaves the mesh entirely and forgets every peer. */
    fun stopMesh() {
        meshActive = false
        handler.removeCallbacksAndMessages(null)
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        advertising = false
        discovering = false
        seen.clear()
        _state.update {
            it.copy(
                meshActive = false,
                advertising = false,
                discovering = false,
                status = "Mesh stopped",
                peers = emptyMap()
            )
        }
    }

    /**
     * Creates a NEW mesh message from this phone and sends it to every directly
     * connected peer. Those peers relay it onwards, which is how it reaches nodes
     * this one cannot see.
     */
    fun sendText(text: String) {
        val targets = connectedEndpointIds()
        if (targets.isEmpty()) {
            setStatus("Nothing to send to — no connected nodes yet")
            return
        }

        val message = MeshMessage.create(
            senderId = nodeId,
            senderName = "$nodeId (${_state.value.deviceModel})",
            payload = text
        )

        // Remember our own message id immediately. If the mesh loops it back to
        // us we will recognise and drop it instead of re-sending it forever.
        seen.markSeen(message.messageId)

        connectionsClient.sendPayload(targets, Payload.fromBytes(message.toBytes()))
            .addOnSuccessListener {
                log("Sent \"$text\" id=${message.messageId} ttl=${message.ttl} to $targets")
                addLog(
                    "Sent: $text  →  ${targets.size} node(s), TTL ${message.ttl}",
                    MeshLogEntry.Kind.SENT,
                    payload = message.payload,
                    fromNode = message.senderId,
                    hops = message.hops,
                    ttl = message.ttl
                )
            }
            .addOnFailureListener { error ->
                log("sendPayload failed", error)
                setStatus("Send failed: ${describe(error)}")
            }
    }

    // ---------------------------------------------------------------------
    // Advertising / discovery — both run for the whole life of the mesh node
    // ---------------------------------------------------------------------

    private fun beginAdvertising() {
        // Claimed up front, not in the success callback: otherwise a fast double tap
        // fires startAdvertising twice and the second call fails as ALREADY_ADVERTISING.
        if (advertising) return
        advertising = true

        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient
            .startAdvertising(localEndpointName, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                log("Advertising started as $localEndpointName")
                _state.update { it.copy(advertising = true) }
                setStatus(describeActivity())
            }
            .addOnFailureListener { error ->
                log("Advertising failed", error)
                advertising = false
                _state.update { it.copy(advertising = false) }
                setStatus("Could not advertise: ${describe(error)}")
                retryRadios()
            }
    }

    private fun beginDiscovery() {
        // Same reasoning as beginAdvertising: claim the flag before the async call.
        if (discovering) return
        discovering = true

        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                log("Discovery started")
                _state.update { it.copy(discovering = true) }
                setStatus(describeActivity())
            }
            .addOnFailureListener { error ->
                log("Discovery failed", error)
                discovering = false
                _state.update { it.copy(discovering = false) }
                setStatus("Could not discover: ${describe(error)}")
                retryRadios()
            }
    }

    /** If a radio failed to start (usually transient), try again while the mesh is on. */
    private fun retryRadios() {
        if (!meshActive) return
        handler.postDelayed({
            if (!meshActive) return@postDelayed
            beginAdvertising()
            beginDiscovery()
        }, RADIO_RETRY_MS)
    }

    private fun describeActivity(): String = when {
        advertising && discovering -> "Mesh active — advertising and discovering"
        advertising -> "Advertising only — discovery not running"
        discovering -> "Discovering only — advertising not running"
        else -> "Mesh stopped"
    }

    // ---------------------------------------------------------------------
    // Automatic connection management
    // ---------------------------------------------------------------------

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val remoteNodeId = MeshIdentity.parseNodeId(info.endpointName)
            val remoteModel = MeshIdentity.parseModel(info.endpointName)
            log("Found $endpointId ($remoteNodeId / $remoteModel)")

            val existing = _state.value.peers[endpointId]
            if (existing != null && existing.state != PeerState.DISCOVERED) {
                // Already connecting or connected — never start a second attempt.
                return
            }

            upsertPeer(
                MeshPeer(
                    endpointId = endpointId,
                    nodeId = remoteNodeId,
                    deviceModel = remoteModel,
                    state = PeerState.DISCOVERED
                )
            )
            considerConnecting(endpointId)
        }

        override fun onEndpointLost(endpointId: String) {
            log("Lost endpoint $endpointId")
            // Only forget it if we never got connected. A connected peer can stop
            // being *discoverable* while the connection itself is still perfectly fine.
            val peer = _state.value.peers[endpointId] ?: return
            if (peer.state == PeerState.DISCOVERED) {
                removePeer(endpointId)
            }
        }
    }

    /**
     * Decides whether to open the connection to a freshly discovered peer.
     *
     * Both phones discover each other, so if both dialled at the same moment the
     * two half-connections would collide. The node ids break the tie: the lower id
     * dials immediately, the higher id waits and only dials as a fallback if the
     * other side has not managed it. This is symmetric — both phones run the same
     * code and reach opposite conclusions — so no A/B roles are needed.
     */
    private fun considerConnecting(endpointId: String) {
        if (!meshActive) return
        val peer = _state.value.peers[endpointId] ?: return
        if (peer.state != PeerState.DISCOVERED) return

        if (nodeId < peer.nodeId) {
            openConnection(endpointId)
        } else {
            handler.postDelayed({
                val current = _state.value.peers[endpointId]
                if (meshActive && current != null && current.state == PeerState.DISCOVERED) {
                    log("Fallback dial to $endpointId — other side did not connect in time")
                    openConnection(endpointId)
                }
            }, FALLBACK_DIAL_MS)
        }
    }

    private fun openConnection(endpointId: String) {
        val peer = _state.value.peers[endpointId] ?: return
        if (peer.state != PeerState.DISCOVERED) return

        setPeerState(endpointId, PeerState.CONNECTING)
        connectionsClient
            .requestConnection(localEndpointName, endpointId, connectionLifecycleCallback)
            .addOnSuccessListener { log("Connection requested to $endpointId (${peer.nodeId})") }
            .addOnFailureListener { error ->
                val code = (error as? ApiException)?.statusCode
                if (code == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) {
                    // The other side's request won the race. That is a success for us.
                    log("Already connected to $endpointId")
                    setPeerState(endpointId, PeerState.CONNECTED)
                    return@addOnFailureListener
                }
                log("requestConnection failed for $endpointId", error)
                setPeerState(endpointId, PeerState.DISCOVERED)
                scheduleRetry(endpointId)
            }
    }

    /** Connections fail for transient radio reasons all the time; just try again. */
    private fun scheduleRetry(endpointId: String) {
        if (!meshActive) return
        // Jitter stops two phones retrying in lockstep and colliding forever.
        val delay = RETRY_BASE_MS + Random.nextLong(RETRY_JITTER_MS)
        handler.postDelayed({ considerConnecting(endpointId) }, delay)
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val remoteNodeId = MeshIdentity.parseNodeId(info.endpointName)
            val remoteModel = MeshIdentity.parseModel(info.endpointName)
            log("Connection initiated with $endpointId ($remoteNodeId)")

            // Accept every DisasterMesh node automatically — that is the whole point.
            upsertPeer(
                MeshPeer(
                    endpointId = endpointId,
                    nodeId = remoteNodeId,
                    deviceModel = remoteModel,
                    state = PeerState.CONNECTING
                )
            )
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { error ->
                    log("acceptConnection failed", error)
                    setPeerState(endpointId, PeerState.DISCOVERED)
                    scheduleRetry(endpointId)
                }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            when (val code = resolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    val peer = _state.value.peers[endpointId]
                    log("Connected to $endpointId (${peer?.nodeId})")
                    setPeerState(endpointId, PeerState.CONNECTED)
                    setStatus("Connected to ${peer?.displayName ?: endpointId}")
                    // NOTE: discovery is deliberately NOT stopped here. A node must keep
                    // looking so it can also connect to the next phone in the chain
                    // while already connected to the previous one.
                }

                else -> {
                    log("Connection to $endpointId failed with code $code")
                    setPeerState(endpointId, PeerState.DISCOVERED)
                    scheduleRetry(endpointId)
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            log("Disconnected from $endpointId")
            // Drop it from the collection. Discovery is still running, so if the node
            // is merely out of range for a moment it will be found and reconnected.
            removePeer(endpointId)
            setStatus("A node left the mesh")
        }
    }

    // ---------------------------------------------------------------------
    // Peer collection helpers
    // ---------------------------------------------------------------------

    private fun upsertPeer(peer: MeshPeer) {
        _state.update { it.copy(peers = it.peers + (peer.endpointId to peer)) }
    }

    private fun setPeerState(endpointId: String, newState: PeerState) {
        _state.update { current ->
            val peer = current.peers[endpointId] ?: return@update current
            current.copy(peers = current.peers + (endpointId to peer.copy(state = newState)))
        }
    }

    private fun removePeer(endpointId: String) {
        _state.update { it.copy(peers = it.peers - endpointId) }
    }

    private fun connectedEndpointIds(): List<String> =
        _state.value.peers.values
            .filter { it.state == PeerState.CONNECTED }
            .map { it.endpointId }

    // ---------------------------------------------------------------------
    // Mesh relay
    // ---------------------------------------------------------------------

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            handleIncoming(endpointId, bytes)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Byte payloads arrive in one piece, so there is nothing to track here.
        }
    }

    /**
     * Runs for every message arriving from a directly connected peer, in order:
     *  1. ignore our own message if it loops back to us,
     *  2. drop it if we have handled this message id before,
     *  3. show it if it is addressed to us (or broadcast),
     *  4. drop it if its TTL is used up,
     *  5. otherwise decrement TTL and pass it on to our other peers.
     *
     * This is what makes the network multi-hop, and it works the same whether
     * there are two nodes or ten.
     */
    private fun handleIncoming(fromEndpointId: String, bytes: ByteArray) {
        val message = MeshMessage.fromBytes(bytes)
        if (message == null) {
            val raw = String(bytes, Charsets.UTF_8)
            log("Received non-mesh payload \"$raw\" from $fromEndpointId")
            addLog("Received: $raw", MeshLogEntry.Kind.RECEIVED)
            return
        }

        // 1. Our own message came back around the mesh. Never re-send it.
        if (message.senderId == nodeId) {
            log("Dropped own message ${message.messageId}")
            return
        }

        // 2. Duplicate suppression. Without this the mesh never stops talking.
        if (!seen.markSeen(message.messageId)) {
            log("Dropped duplicate ${message.messageId}")
            addLog(
                "Duplicate ignored: \"${message.payload}\" (id ${shortId(message.messageId)})",
                MeshLogEntry.Kind.DROPPED
            )
            return
        }

        // 3. Deliver to this phone if it is the intended recipient.
        val addressedToUs = message.destinationId == nodeId
        val isBroadcast = message.destinationId == MeshMessage.BROADCAST
        if (addressedToUs || isBroadcast) {
            val route = if (message.hops == 0) {
                "direct from ${message.senderName}"
            } else {
                "from ${message.senderName} via ${message.hops} hop(s)"
            }
            addLog(
                "Received: ${message.payload}  ($route)",
                MeshLogEntry.Kind.RECEIVED,
                payload = message.payload,
                fromNode = message.senderId,
                hops = message.hops,
                ttl = message.ttl
            )
        }

        // A message addressed to us personally has arrived. Nothing to forward.
        if (addressedToUs) return

        // 4. TTL check. relayed() decrements; at zero the message dies here.
        val next = message.relayed()
        if (next.ttl <= 0) {
            log("TTL expired for ${message.messageId}")
            addLog(
                "Not relayed: \"${message.payload}\" — TTL reached 0",
                MeshLogEntry.Kind.DROPPED
            )
            return
        }

        // 5. Forward to every connected node except the one it just came from.
        relay(next, arrivedFrom = fromEndpointId)
    }

    private fun relay(message: MeshMessage, arrivedFrom: String) {
        val targets = connectedEndpointIds().filterNot { it == arrivedFrom }

        if (targets.isEmpty()) {
            log("No onward peers for ${message.messageId}")
            addLog(
                "End of the line: \"${message.payload}\" — no other nodes to relay to",
                MeshLogEntry.Kind.DROPPED
            )
            return
        }

        connectionsClient.sendPayload(targets, Payload.fromBytes(message.toBytes()))
            .addOnSuccessListener {
                log("Relayed ${message.messageId} to $targets ttl=${message.ttl}")
                addLog(
                    "Relayed: \"${message.payload}\"  →  ${targets.size} node(s), TTL now ${message.ttl}",
                    MeshLogEntry.Kind.RELAYED,
                    payload = message.payload,
                    fromNode = message.senderId,
                    hops = message.hops,
                    ttl = message.ttl
                )
            }
            .addOnFailureListener { error ->
                log("Relay failed", error)
                addLog("Relay failed: ${describe(error)}", MeshLogEntry.Kind.DROPPED)
            }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun setStatus(status: String) {
        _state.update { it.copy(status = status) }
    }

    private fun addLog(
        text: String,
        kind: MeshLogEntry.Kind,
        payload: String? = null,
        fromNode: String? = null,
        hops: Int? = null,
        ttl: Int? = null
    ) {
        _state.update { current ->
            val entry = MeshLogEntry(text, kind, payload, fromNode, hops, ttl)
            val updated = current.messages + entry
            current.copy(messages = updated.takeLast(MAX_LOG_ENTRIES))
        }
    }

    private fun shortId(messageId: String): String = messageId.take(8)

    @Suppress("DEPRECATION") // MISSING_SETTING_LOCATION_MUST_BE_ON is deprecated but still returned by older Play Services.
    private fun describe(error: Exception): String {
        val statusCode = (error as? ApiException)?.statusCode ?: return error.message ?: error.toString()
        return when (statusCode) {
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_FINE_LOCATION ->
                "Precise location is required (8036). Tap \"Grant permissions\", allow " +
                    "Location, and choose PRECISE - approximate location is not enough."

            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_COARSE_LOCATION ->
                "Location permission is required (8034). Tap \"Grant permissions\" and allow Location."

            ConnectionsStatusCodes.MISSING_SETTING_LOCATION_MUST_BE_ON ->
                "Turn Location ON in the phone's quick settings, then try again."

            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_SCAN,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADVERTISE,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_CONNECT ->
                "Nearby devices permission is required. Tap \"Grant permissions\" and allow it."

            ConnectionsStatusCodes.MISSING_PERMISSION_NEARBY_WIFI_DEVICES ->
                "Nearby Wi-Fi permission is required. Tap \"Grant permissions\" and allow it."

            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_WIFI_STATE,
            ConnectionsStatusCodes.MISSING_PERMISSION_CHANGE_WIFI_STATE ->
                "Turn Wi-Fi ON (it does not need to join a network), then try again."

            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADMIN ->
                "Turn Bluetooth ON, then try again."

            // Anything unrecognised keeps its raw code so it can be looked up, rather
            // than being flattened into a vague message.
            else -> "Nearby error $statusCode: ${error.message ?: error.toString()}"
        }
    }

    private fun log(message: String, error: Throwable? = null) {
        if (error != null) Log.w(TAG, message, error) else Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "DisasterMesh"

        /** Must be identical on every phone — it is how they recognise each other. */
        const val SERVICE_ID = "com.disastermesh.app.SERVICE"

        /**
         * P2P_CLUSTER is the only strategy that allows a device to advertise and
         * discover at the same time AND hold many connections at once (M-to-N).
         * Both are required for an N-node relay.
         */
        val STRATEGY: Strategy = Strategy.P2P_CLUSTER

        const val MAX_LOG_ENTRIES = 100

        /** How long the higher node id waits before dialling as a fallback. */
        const val FALLBACK_DIAL_MS = 6_000L

        const val RETRY_BASE_MS = 3_000L
        const val RETRY_JITTER_MS = 2_000L
        const val RADIO_RETRY_MS = 5_000L
    }
}
