package com.disastermesh.app.audio

/**
 * Every audio tunable, in one place.
 *
 * Values are chosen for speech over a constrained mesh: intelligible voice at
 * the smallest size that stays reliable, never studio quality.
 *
 * AAC in an M4A container at 16 kHz mono, 24 kbps gives roughly 3 KB per second,
 * so a full 30-second message is about 90 KB — comfortably inside Nearby's
 * 1,047,552-byte BYTES limit, and far inside a FILE payload's capability.
 */
object AudioMessageConfig {

    /** Container/codec. AAC is hardware-accelerated on effectively every Android device. */
    const val FILE_EXTENSION = "m4a"
    const val MIME_TYPE = "audio/mp4"

    /** Mono: a voice message gains nothing from a second channel. */
    const val CHANNELS = 1

    /** 16 kHz captures the speech band without paying for music bandwidth. */
    const val SAMPLE_RATE_HZ = 16_000

    /** 24 kbps ≈ 3 KB/s. Clear speech, tiny files. */
    const val BIT_RATE_BPS = 24_000

    /** Recording stops itself here. */
    const val MAX_DURATION_SECONDS = 30

    /** Anything shorter is almost certainly an accidental tap. */
    const val MIN_DURATION_MS = 800L

    /**
     * Hard ceiling on one encoded message. Generous against the ~90 KB expected
     * at 30 s, so a device that encodes less efficiently still succeeds.
     */
    const val MAX_AUDIO_BYTES = 512 * 1024

    /** Total space voice messages may occupy before old ones are evicted. */
    const val MAX_TOTAL_AUDIO_BYTES = 32L * 1024 * 1024

    /** Requests for the same clip are not repeated faster than this. */
    const val AUDIO_REQUEST_INTERVAL_MS = 15_000L

    /** One clip in flight per peer, so audio cannot monopolise the link. */
    const val MAX_CONCURRENT_TRANSFERS = 1
}
