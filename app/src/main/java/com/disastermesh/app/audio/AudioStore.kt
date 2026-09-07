package com.disastermesh.app.audio

import android.content.Context
import android.util.Log
import java.io.File

/**
 * App-private storage for voice messages.
 *
 * Files live in filesDir/audio and are never exposed to other apps: no public
 * storage, no MediaStore, no raw paths handed out. Audio can contain someone's
 * emergency, so it stays inside the sandbox.
 */
class AudioStore(context: Context) {

    private val root = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }

    /** Partial downloads live here until verified, so a half file is never played. */
    private val incoming = File(root, "incoming").apply { mkdirs() }

    fun fileFor(audioId: String): File = File(root, "$audioId.${AudioMessageConfig.FILE_EXTENSION}")

    fun tempFileFor(audioId: String): File =
        File(incoming, "$audioId.${AudioMessageConfig.FILE_EXTENSION}.part")

    /** True when the complete clip is on disk. */
    fun has(audioId: String): Boolean {
        val file = fileFor(audioId)
        return file.exists() && file.length() > 0
    }

    fun sizeOf(audioId: String): Long = fileFor(audioId).let { if (it.exists()) it.length() else 0L }

    /**
     * Promotes a finished transfer into the playable store.
     *
     * Verifies the size matches what the sender advertised before publishing it,
     * so a truncated transfer never becomes a playable file.
     */
    fun publish(audioId: String, source: File, expectedBytes: Int): Boolean {
        if (!source.exists()) return false
        val actual = source.length()
        if (actual <= 0 || actual > AudioMessageConfig.MAX_AUDIO_BYTES) {
            source.delete()
            return false
        }
        // Allow a small tolerance: some encoders pad the container slightly.
        if (expectedBytes > 0 && kotlin.math.abs(actual - expectedBytes) > SIZE_TOLERANCE) {
            Log.w(TAG, "audio $audioId size mismatch: got $actual expected $expectedBytes")
            source.delete()
            return false
        }
        val target = fileFor(audioId)
        return try {
            if (target.exists()) target.delete()
            val moved = source.renameTo(target)
            if (!moved) source.copyTo(target, overwrite = true).let { source.delete() }
            enforceStorageLimit()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not publish audio $audioId", e)
            false
        }
    }

    /** Stores a clip this device just recorded. */
    fun adopt(audioId: String, recorded: File): Boolean = try {
        val target = fileFor(audioId)
        recorded.copyTo(target, overwrite = true)
        recorded.delete()
        enforceStorageLimit()
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not adopt recording", e)
        false
    }

    fun delete(audioId: String) {
        runCatching { fileFor(audioId).delete() }
        runCatching { tempFileFor(audioId).delete() }
    }

    /** Removes leftovers from cancelled or failed transfers. */
    fun clearIncoming() {
        runCatching { incoming.listFiles()?.forEach { it.delete() } }
    }

    fun totalBytes(): Long = root.listFiles()
        ?.filter { it.isFile }
        ?.sumOf { it.length() }
        ?: 0L

    /**
     * Evicts oldest clips when the cap is exceeded.
     *
     * Age-based rather than importance-based on purpose: the metadata layer
     * already governs what matters, and deleting the newest arrival would be
     * exactly wrong during an unfolding emergency.
     */
    private fun enforceStorageLimit() {
        var total = totalBytes()
        if (total <= AudioMessageConfig.MAX_TOTAL_AUDIO_BYTES) return

        val oldestFirst = root.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.lastModified() }
            ?: return

        for (file in oldestFirst) {
            if (total <= AudioMessageConfig.MAX_TOTAL_AUDIO_BYTES) break
            val size = file.length()
            if (file.delete()) {
                total -= size
                Log.d(TAG, "Evicted audio ${file.name} to stay within storage limit")
            }
        }
    }

    private companion object {
        const val TAG = "DisasterMesh"
        const val DIR = "audio"

        /** Container padding tolerance when verifying a received clip. */
        const val SIZE_TOLERANCE = 4096
    }
}
