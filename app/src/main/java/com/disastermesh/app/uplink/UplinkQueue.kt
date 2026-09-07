package com.disastermesh.app.uplink

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One report awaiting delivery out of the mesh. */
data class QueueEntry(
    val reportId: String,
    val state: DeliveryState,
    val attempts: Int = 0,
    val lastAttempt: Long = 0L,
    val lastError: String? = null
)

/**
 * The store-and-forward queue.
 *
 * Keyed by the report id, which is the mesh message id — so the network's own
 * duplicate protection carries straight through to the uplink and one emergency
 * can never be submitted externally twice. A delivered id is retained rather
 * than deleted, because forgetting it is exactly how a duplicate happens.
 *
 * Persisted as one small JSON file so the queue survives a restart.
 */
class UplinkQueue(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    private val _entries = MutableStateFlow<Map<String, QueueEntry>>(emptyMap())
    val entries: StateFlow<Map<String, QueueEntry>> = _entries.asStateFlow()

    init {
        load()
    }

    val pendingCount: Int
        get() = _entries.value.values.count { it.state != DeliveryState.DELIVERED }

    val deliveredCount: Int
        get() = _entries.value.values.count { it.state == DeliveryState.DELIVERED }

    /** True when this report has already been delivered externally. */
    fun isDelivered(reportId: String): Boolean =
        _entries.value[reportId]?.state == DeliveryState.DELIVERED

    fun stateOf(reportId: String): DeliveryState? = _entries.value[reportId]?.state

    /**
     * Adds reports that are not tracked yet. Existing entries are never reset,
     * so a delivered report cannot be re-queued by a later re-ingest.
     */
    @Synchronized
    fun enqueueMissing(reportIds: List<String>, initial: DeliveryState) {
        val current = _entries.value
        val additions = reportIds.filterNot { current.containsKey(it) }
        if (additions.isEmpty()) return

        _entries.value = current + additions.associateWith {
            QueueEntry(reportId = it, state = initial)
        }
        save()
    }

    @Synchronized
    fun update(reportId: String, transform: (QueueEntry) -> QueueEntry) {
        val current = _entries.value
        val existing = current[reportId] ?: return
        _entries.value = current + (reportId to transform(existing))
        save()
    }

    /** Moves every undelivered entry to a new waiting state as conditions change. */
    @Synchronized
    fun refreshWaitingState(newState: DeliveryState) {
        val current = _entries.value
        var changed = false
        val updated = current.mapValues { (_, entry) ->
            if (entry.state == DeliveryState.DELIVERED || entry.state == DeliveryState.UPLOADING) {
                entry
            } else if (entry.state != newState) {
                changed = true
                entry.copy(state = newState)
            } else {
                entry
            }
        }
        if (changed) {
            _entries.value = updated
            save()
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val array = JSONArray(file.readText())
            val restored = mutableMapOf<String, QueueEntry>()
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val id = row.optString(K_ID, "").takeIf { it.isNotBlank() } ?: continue
                restored[id] = QueueEntry(
                    reportId = id,
                    state = DeliveryState.fromName(row.optString(K_STATE, "")),
                    attempts = row.optInt(K_ATTEMPTS, 0).coerceAtLeast(0),
                    lastAttempt = row.optLong(K_LAST, 0L),
                    lastError = row.optString(K_ERROR, "").takeIf { it.isNotBlank() }
                )
            }
            _entries.value = restored
            Log.d(TAG, "Restored ${restored.size} uplink queue entries")
        } catch (e: Exception) {
            // A corrupt queue must not stop the app; start clean.
            Log.w(TAG, "Could not read uplink queue", e)
            runCatching { file.delete() }
            _entries.value = emptyMap()
        }
    }

    private fun save() {
        val snapshot = _entries.value.values.toList()
        try {
            val array = JSONArray()
            snapshot.forEach { entry ->
                array.put(
                    JSONObject().apply {
                        put(K_ID, entry.reportId)
                        put(K_STATE, entry.state.name)
                        put(K_ATTEMPTS, entry.attempts)
                        put(K_LAST, entry.lastAttempt)
                        entry.lastError?.let { put(K_ERROR, it) }
                    }
                )
            }
            file.writeText(array.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist uplink queue", e)
        }
    }

    private companion object {
        const val TAG = "DisasterMesh"
        const val FILE_NAME = "uplink_queue.json"
        const val K_ID = "id"
        const val K_STATE = "state"
        const val K_ATTEMPTS = "attempts"
        const val K_LAST = "last"
        const val K_ERROR = "error"
    }
}
