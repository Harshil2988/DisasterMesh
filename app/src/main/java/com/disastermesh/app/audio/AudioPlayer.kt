package com.disastermesh.app.audio

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Which clip is playing, and how far through it is. */
data class PlaybackState(
    val audioId: String? = null,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0
) {
    val progress: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * Playback through Android's own MediaPlayer — no extra dependency.
 *
 * One clip at a time: starting another stops the first, so a screen full of
 * voice messages can never play over itself.
 */
class AudioPlayer {

    private var player: MediaPlayer? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** Starts [file], or stops it if that clip is already playing. */
    fun toggle(audioId: String, file: File) {
        if (_state.value.audioId == audioId && _state.value.playing) {
            pause()
            return
        }
        play(audioId, file)
    }

    private fun play(audioId: String, file: File) {
        stop()
        if (!file.exists() || file.length() <= 0) {
            Log.w(TAG, "Refusing to play missing or empty audio $audioId")
            return
        }
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stop() }
                setOnErrorListener { _, _, _ ->
                    // A corrupt file must not take the screen down.
                    stop()
                    true
                }
                prepare()
                start()
            }
            _state.value = PlaybackState(
                audioId = audioId,
                playing = true,
                positionMs = 0,
                durationMs = player?.duration ?: 0
            )
        } catch (e: Exception) {
            Log.w(TAG, "Playback failed for $audioId", e)
            stop()
        }
    }

    fun pause() {
        runCatching { player?.pause() }
        _state.value = _state.value.copy(playing = false)
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.reset() }
        runCatching { player?.release() }
        player = null
        _state.value = PlaybackState()
    }

    /** Called by the UI ticker so the progress bar tracks real position. */
    fun refreshPosition() {
        val active = player ?: return
        if (!_state.value.playing) return
        runCatching {
            _state.value = _state.value.copy(
                positionMs = active.currentPosition,
                durationMs = active.duration
            )
        }
    }

    private companion object {
        const val TAG = "DisasterMesh"
    }
}
