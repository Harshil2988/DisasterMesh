package com.disastermesh.app.audio

import android.util.Log
import com.disastermesh.app.nearby.NearbyConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream

/** Live progress for one clip. */
data class AudioTransfer(
    val audioId: String,
    val state: AudioState,
    val transferred: Long = 0,
    val total: Long = 0
) {
    val progress: Float
        get() = if (total <= 0) 0f else (transferred.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * Moves voice-message binaries between directly connected nodes.
 *
 * Pull-based on purpose: metadata reaches a node through ordinary relay and
 * synchronisation, and only a node that actually lacks the bytes asks a peer for
 * them. Nothing is pushed at nodes that already have it, so a clip crosses each
 * link at most once.
 *
 * Nearby's FILE payload does the chunking, reassembly and progress reporting —
 * this class only decides who needs what.
 */
class AudioTransferManager(
    private val store: AudioStore,
    private val mesh: NearbyConnectionManager
) {

    private val _transfers = MutableStateFlow<Map<String, AudioTransfer>>(emptyMap())
    val transfers: StateFlow<Map<String, AudioTransfer>> = _transfers.asStateFlow()

    /** payloadId -> audioId, so a completion can be matched to a clip. */
    private val inbound = mutableMapOf<Long, String>()

    /** audioId -> when we last asked for it, to avoid hammering a peer. */
    private val lastRequested = mutableMapOf<String, Long>()

    /** Endpoints currently sending us something, to cap concurrency. */
    private val activeByEndpoint = mutableMapOf<String, Int>()

    // -----------------------------------------------------------------
    // Requesting
    // -----------------------------------------------------------------

    /**
     * Asks a peer for a clip we know about but do not hold.
     *
     * Silently does nothing when the audio is already present, already in
     * flight, or was requested moments ago.
     */
    fun requestIfMissing(endpointId: String, audio: AudioMessage) {
        if (store.has(audio.audioId)) return

        val now = System.currentTimeMillis()
        val last = lastRequested[audio.audioId] ?: 0L
        if (now - last < AudioMessageConfig.AUDIO_REQUEST_INTERVAL_MS) return

        val active = activeByEndpoint[endpointId] ?: 0
        if (active >= AudioMessageConfig.MAX_CONCURRENT_TRANSFERS) return

        lastRequested[audio.audioId] = now
        mark(audio.audioId, AudioState.PENDING, total = audio.sizeBytes.toLong())

        val packet = (REQUEST_PREFIX + JSONObject().apply {
            put(K_AUDIO_ID, audio.audioId)
        }.toString()).toByteArray(Charsets.UTF_8)

        runCatching { mesh.sendRawTo(endpointId, packet) }
            .onFailure { Log.w(TAG, "audio: request failed", it) }
    }

    /** True when these bytes are an audio control packet. */
    fun isAudioControl(bytes: ByteArray): Boolean {
        if (bytes.size < REQUEST_PREFIX.length || bytes.size > MAX_CONTROL_BYTES) return false
        return String(bytes, 0, REQUEST_PREFIX.length, Charsets.UTF_8) == REQUEST_PREFIX
    }

    /**
     * A peer asked us for a clip. Serve it only if we genuinely have it.
     *
     * @return true when the packet was ours.
     */
    fun handleControl(endpointId: String, bytes: ByteArray): Boolean {
        if (!isAudioControl(bytes)) return false
        try {
            val json = JSONObject(String(bytes, Charsets.UTF_8).removePrefix(REQUEST_PREFIX))
            val audioId = json.optString(K_AUDIO_ID, "").trim()
            if (audioId.isBlank() || audioId.length > 64) return true

            val file = store.fileFor(audioId)
            if (!file.exists() || file.length() <= 0) {
                // Requested something we do not hold. Ignore quietly.
                return true
            }
            val payloadId = mesh.sendFileTo(endpointId, file)
            Log.d(TAG, "audio: serving $audioId to $endpointId (payload $payloadId)")
        } catch (e: Exception) {
            Log.w(TAG, "audio: malformed control packet", e)
        }
        return true
    }

    // -----------------------------------------------------------------
    // Receiving
    // -----------------------------------------------------------------

    /**
     * A FILE payload arrived. Nearby hands us the file only once the transfer
     * completes, so this records the association and waits for the update.
     */
    fun onFilePayload(endpointId: String, payloadId: Long, descriptor: ParcelFileDescriptor?) {
        activeByEndpoint[endpointId] = (activeByEndpoint[endpointId] ?: 0) + 1
        pendingDescriptors[payloadId] = descriptor
    }

    private val pendingDescriptors = mutableMapOf<Long, ParcelFileDescriptor?>()

    /**
     * Streams the completed payload into app-private storage.
     *
     * Copied in a bounded buffer rather than read whole into memory, so a long
     * clip cannot spike heap on a low-memory phone.
     */
    private fun drainToTemp(audioId: String, descriptor: ParcelFileDescriptor): File? = try {
        val temp = store.tempFileFor(audioId)
        temp.parentFile?.mkdirs()
        FileInputStream(descriptor.fileDescriptor).use { input ->
            temp.outputStream().use { output ->
                val buffer = ByteArray(COPY_BUFFER)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    copied += read
                    if (copied > AudioMessageConfig.MAX_AUDIO_BYTES) {
                        // A peer must not be able to write an unbounded file here.
                        output.flush()
                        temp.delete()
                        return null
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        temp
    } catch (e: Exception) {
        Log.w(TAG, "audio: could not read transferred payload", e)
        null
    } finally {
        runCatching { descriptor.close() }
    }

    /**
     * Progress and completion. Only a SUCCESS with a verified size publishes the
     * clip; anything else leaves it unplayable rather than half-playable.
     */
    fun onTransferUpdate(
        endpointId: String,
        payloadId: Long,
        status: Int,
        transferred: Long,
        total: Long,
        expectedFor: (Long) -> AudioMessage?
    ) {
        val expected = expectedFor(payloadId)
        val audioId = inbound[payloadId] ?: expected?.audioId

        when (status) {
            STATUS_IN_PROGRESS -> {
                audioId?.let { mark(it, AudioState.TRANSFERRING, transferred, total) }
            }

            STATUS_SUCCESS -> {
                activeByEndpoint[endpointId] = ((activeByEndpoint[endpointId] ?: 1) - 1)
                    .coerceAtLeast(0)
                val descriptor = pendingDescriptors.remove(payloadId)
                if (audioId == null || descriptor == null) return

                val temp = drainToTemp(audioId, descriptor)
                val ok = temp != null && store.publish(audioId, temp, expected?.sizeBytes ?: 0)
                mark(
                    audioId,
                    if (ok) AudioState.PLAYABLE else AudioState.FAILED,
                    transferred,
                    total
                )
                inbound.remove(payloadId)
                Log.d(TAG, "audio: $audioId ${if (ok) "ready" else "verification failed"}")
            }

            STATUS_FAILURE, STATUS_CANCELED -> {
                activeByEndpoint[endpointId] = ((activeByEndpoint[endpointId] ?: 1) - 1)
                    .coerceAtLeast(0)
                pendingDescriptors.remove(payloadId)?.let { runCatching { it.close() } }
                // Keep it retryable with the SAME ids rather than inventing new ones.
                audioId?.let { mark(it, AudioState.FAILED, transferred, total) }
                inbound.remove(payloadId)
            }
        }
    }

    /** Associates an inbound payload with the clip it carries. */
    fun expectAudio(payloadId: Long, audioId: String) {
        inbound[payloadId] = audioId
    }

    /** Marks a clip this device recorded as immediately playable. */
    fun markLocal(audioId: String) {
        mark(audioId, AudioState.PLAYABLE)
    }

    fun stateOf(audioId: String): AudioState = when {
        store.has(audioId) -> AudioState.PLAYABLE
        else -> _transfers.value[audioId]?.state ?: AudioState.PENDING
    }

    /** Lets a failed clip be asked for again. */
    fun allowRetry(audioId: String) {
        lastRequested.remove(audioId)
        mark(audioId, AudioState.PENDING)
    }

    private fun mark(audioId: String, state: AudioState, transferred: Long = 0, total: Long = 0) {
        _transfers.update { current ->
            val existing = current[audioId]
            current + (audioId to AudioTransfer(
                audioId = audioId,
                state = state,
                transferred = if (transferred > 0) transferred else existing?.transferred ?: 0,
                total = if (total > 0) total else existing?.total ?: 0
            ))
        }
    }

    private companion object {
        const val TAG = "DisasterMesh"

        /** Distinct from the mesh envelope and from DMSYNC1. */
        const val REQUEST_PREFIX = "DMAREQ1:"
        const val MAX_CONTROL_BYTES = 512
        const val K_AUDIO_ID = "aid"
        const val COPY_BUFFER = 16 * 1024

        const val STATUS_SUCCESS = 1
        const val STATUS_FAILURE = 2
        const val STATUS_IN_PROGRESS = 3
        const val STATUS_CANCELED = 4
    }
}
