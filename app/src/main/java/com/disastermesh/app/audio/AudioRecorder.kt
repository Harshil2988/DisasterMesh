package com.disastermesh.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.util.UUID

/** Outcome of a finished recording. */
sealed interface RecordingResult {
    data class Success(val audioId: String, val file: File, val durationMs: Long) : RecordingResult
    data class Failed(val reason: String) : RecordingResult
}

/**
 * Microphone capture via Android's own MediaRecorder — no third-party library.
 *
 * Records only between an explicit [start] and [stop]/[cancel], and releases the
 * microphone the moment either happens. There is no code path that captures
 * audio without the user having pressed record.
 */
class AudioRecorder(context: Context) {

    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var currentAudioId: String? = null
    private var startedAt: Long = 0L

    val isRecording: Boolean get() = recorder != null

    /** Elapsed milliseconds, for the live timer. */
    fun elapsedMs(): Long = if (startedAt == 0L) 0L else System.currentTimeMillis() - startedAt

    /**
     * Begins capture. Returns a human-readable reason on failure — a busy
     * microphone or an unsupported codec must never crash the app.
     */
    fun start(): String? {
        if (isRecording) return "Already recording."

        val audioId = UUID.randomUUID().toString().replace("-", "").take(24)
        val cacheDir = File(appContext.cacheDir, "audio_rec").apply { mkdirs() }
        val file = File(cacheDir, "$audioId.${AudioMessageConfig.FILE_EXTENSION}")

        return try {
            @Suppress("DEPRECATION") // The Context constructor requires API 31.
            val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                MediaRecorder()
            }

            created.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(AudioMessageConfig.CHANNELS)
                setAudioSamplingRate(AudioMessageConfig.SAMPLE_RATE_HZ)
                setAudioEncodingBitRate(AudioMessageConfig.BIT_RATE_BPS)
                // The platform stops us at the limit even if the UI timer misses it.
                setMaxDuration(AudioMessageConfig.MAX_DURATION_SECONDS * 1000)
                setMaxFileSize(AudioMessageConfig.MAX_AUDIO_BYTES.toLong())
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = created
            currentFile = file
            currentAudioId = audioId
            startedAt = System.currentTimeMillis()
            null
        } catch (e: Exception) {
            Log.w(TAG, "Recording failed to start", e)
            releaseQuietly()
            runCatching { file.delete() }
            when (e) {
                is SecurityException -> "Microphone permission is required to record audio messages."
                is IllegalStateException -> "The microphone is busy. Close other apps using it."
                else -> "Audio recording is unavailable on this device."
            }
        }
    }

    /** Ends capture and returns the encoded clip. */
    fun stop(): RecordingResult {
        val file = currentFile
        val audioId = currentAudioId
        val duration = elapsedMs()

        val stopped = try {
            recorder?.stop()
            true
        } catch (e: Exception) {
            // Stopping too early yields no valid container.
            Log.w(TAG, "Recorder stop failed", e)
            false
        } finally {
            releaseQuietly()
        }

        if (!stopped || file == null || audioId == null) {
            file?.let { runCatching { it.delete() } }
            return RecordingResult.Failed("Recording was too short.")
        }

        return when {
            !file.exists() || file.length() <= 0 -> {
                runCatching { file.delete() }
                RecordingResult.Failed("Recording produced no audio.")
            }

            duration < AudioMessageConfig.MIN_DURATION_MS -> {
                runCatching { file.delete() }
                RecordingResult.Failed("Hold a little longer to record a message.")
            }

            file.length() > AudioMessageConfig.MAX_AUDIO_BYTES -> {
                runCatching { file.delete() }
                RecordingResult.Failed("Audio message is too large to send.")
            }

            else -> RecordingResult.Success(audioId, file, duration)
        }
    }

    /** Aborts and deletes the temporary file — nothing is retained. */
    fun cancel() {
        runCatching { recorder?.stop() }
        releaseQuietly()
        currentFile?.let { runCatching { it.delete() } }
        currentFile = null
        currentAudioId = null
    }

    private fun releaseQuietly() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        startedAt = 0L
    }

    private companion object {
        const val TAG = "DisasterMesh"
    }
}
