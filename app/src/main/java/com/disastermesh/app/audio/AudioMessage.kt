package com.disastermesh.app.audio

import org.json.JSONObject

/** Where a voice message stands locally. */
enum class AudioState {
    /** Metadata known, bytes not here yet. */
    PENDING,

    /** Bytes are arriving. */
    TRANSFERRING,

    /** Complete and verified — safe to play. */
    PLAYABLE,

    /** Transfer failed; may be retried with the SAME ids. */
    FAILED
}

/**
 * The metadata half of a voice message.
 *
 * Deliberately tiny: it rides inside the ordinary mesh envelope's payload
 * string, so it relays, expires by TTL, deduplicates and synchronises through
 * the existing machinery with no protocol change. The audio itself travels
 * separately as a Nearby FILE payload.
 */
data class AudioMessage(
    /** Identifies the audio blob. Stable across relays and retries. */
    val audioId: String,
    val durationMs: Long,
    val sizeBytes: Int,
    val format: String = AudioMessageConfig.FILE_EXTENSION
) {
    val durationLabel: String
        get() {
            val total = (durationMs / 1000).toInt()
            return "%d:%02d".format(total / 60, total % 60)
        }

    fun encode(): String = WIRE_PREFIX + JSONObject().apply {
        put(K_AUDIO_ID, audioId)
        put(K_DURATION, durationMs)
        put(K_SIZE, sizeBytes)
        put(K_FORMAT, format)
    }.toString()

    companion object {
        /** Marks a payload as a voice message. Distinct from text and reports. */
        const val WIRE_PREFIX = "DMAUD1:"

        private const val K_AUDIO_ID = "aid"
        private const val K_DURATION = "dur"
        private const val K_SIZE = "size"
        private const val K_FORMAT = "fmt"

        fun isAudioPayload(payload: String?): Boolean =
            payload?.startsWith(WIRE_PREFIX) == true

        /**
         * Parses metadata from a peer. Every field is bounds-checked — a remote
         * node cannot make this device allocate or expect something absurd.
         */
        fun decode(payload: String?): AudioMessage? {
            if (!isAudioPayload(payload)) return null
            return try {
                val json = JSONObject(payload!!.removePrefix(WIRE_PREFIX))
                val id = json.optString(K_AUDIO_ID, "").trim()
                val size = json.optInt(K_SIZE, -1)
                val duration = json.optLong(K_DURATION, -1L)

                when {
                    id.isBlank() || id.length > 64 -> null
                    !id.all { it.isLetterOrDigit() || it == '-' } -> null
                    size <= 0 || size > AudioMessageConfig.MAX_AUDIO_BYTES -> null
                    duration <= 0 ||
                        duration > AudioMessageConfig.MAX_DURATION_SECONDS * 1000L * 2 -> null
                    else -> AudioMessage(
                        audioId = id,
                        durationMs = duration,
                        sizeBytes = size,
                        format = json.optString(K_FORMAT, AudioMessageConfig.FILE_EXTENSION)
                            .take(8)
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
