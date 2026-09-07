package com.disastermesh.app.command

import android.content.Context
import android.util.Log
import com.disastermesh.app.nearby.MeshLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The command centre's store of emergency reports.
 *
 * Deliberately reads from [com.disastermesh.app.nearby.MeshState.messages] rather
 * than hooking the networking layer. That log is only appended for messages the
 * network has ALREADY accepted as new, so SeenMessages stays the single authority
 * on duplicates and no second, competing detector exists here.
 *
 * Persistence is a single JSON file — enough that a restart does not lose the
 * picture, without dragging a database into a hackathon build.
 */
class EmergencyRepository(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    /** Disk writes go off the main thread; the UI never waits on storage. */
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _reports = MutableStateFlow<List<EmergencyReport>>(emptyList())
    val reports: StateFlow<List<EmergencyReport>> = _reports.asStateFlow()

    /** Statuses set on this device, kept even as reports are re-derived. */
    private val localStatus = mutableMapOf<String, ReportStatus>()

    init {
        load()
    }

    /**
     * Folds the current message log into the store.
     *
     * Idempotent: a report id already known is not added twice, so replaying the
     * same log — which happens on every state emission — cannot create duplicates.
     */
    @Synchronized
    fun ingest(entries: List<MeshLogEntry>, localNodeId: String) {
        val known = _reports.value.associateBy { it.reportId }
        val additions = mutableListOf<EmergencyReport>()

        entries.forEach { entry ->
            // Only inbound and outbound messages describe a situation; relay and
            // drop events are network bookkeeping, not reports.
            if (entry.kind != MeshLogEntry.Kind.RECEIVED &&
                entry.kind != MeshLogEntry.Kind.SENT
            ) return@forEach

            val report = EmergencyReport.fromLogEntry(entry, localNodeId) ?: return@forEach
            if (known.containsKey(report.reportId)) return@forEach
            if (additions.any { it.reportId == report.reportId }) return@forEach

            additions += report.copy(
                status = localStatus[report.reportId] ?: ReportStatus.UNRESOLVED
            )
        }

        if (additions.isEmpty()) return

        _reports.update { current -> (current + additions).take(MAX_REPORTS) }
        save()
    }

    /** Changes a report's status on this device only. */
    @Synchronized
    fun setStatus(reportId: String, status: ReportStatus) {
        localStatus[reportId] = status
        _reports.update { current ->
            current.map { if (it.reportId == reportId) it.copy(status = status) else it }
        }
        save()
    }

    @Synchronized
    fun clearAll() {
        localStatus.clear()
        _reports.value = emptyList()
        save()
    }

    // -----------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------

    private fun load() {
        if (!file.exists()) return
        try {
            val array = JSONArray(file.readText())
            val restored = buildList {
                for (i in 0 until array.length()) {
                    val row = array.optJSONObject(i) ?: continue
                    // A corrupt row is skipped, never fatal.
                    EmergencyReport.fromStoredJson(row)?.let { add(it) }
                }
            }
            restored.forEach { localStatus[it.reportId] = it.status }
            _reports.value = restored.take(MAX_REPORTS)
            Log.d(TAG, "Restored ${restored.size} emergency reports")
        } catch (e: Exception) {
            // Corrupted file: start clean rather than refusing to open.
            Log.w(TAG, "Could not read stored reports; starting empty", e)
            runCatching { file.delete() }
            _reports.value = emptyList()
        }
    }

    private fun save() {
        // Snapshot on the caller's thread, serialise and write on IO.
        val snapshot = _reports.value
        io.launch {
            try {
                val array = JSONArray()
                snapshot.forEach { array.put(it.toJson()) }
                file.writeText(array.toString())
            } catch (e: Exception) {
                // Never let a storage problem take the command view down.
                Log.w(TAG, "Could not persist reports", e)
            }
        }
    }

    private companion object {
        const val TAG = "DisasterMesh"
        const val FILE_NAME = "emergency_reports.json"

        /** Bounded so a long session cannot grow the file without limit. */
        const val MAX_REPORTS = 500
    }
}

/** Live counts per category, derived from real reports only. */
data class ReportCounts(
    val critical: Int = 0,
    val medical: Int = 0,
    val warning: Int = 0,
    val supply: Int = 0,
    val safe: Int = 0
) {
    val total: Int get() = critical + medical + warning + supply + safe

    /** Emergencies worth surfacing on the home screen. */
    val urgent: Int get() = critical + medical

    companion object {
        fun from(reports: List<EmergencyReport>): ReportCounts = ReportCounts(
            critical = reports.count { it.category == ReportCategory.CRITICAL },
            medical = reports.count { it.category == ReportCategory.MEDICAL },
            warning = reports.count { it.category == ReportCategory.WARNING },
            supply = reports.count { it.category == ReportCategory.SUPPLY },
            safe = reports.count { it.category == ReportCategory.SAFE }
        )
    }
}

/**
 * Command feed ordering: severity first, newest second.
 *
 * This is the rule that makes the feed a triage list rather than a chat log —
 * a critical report stays above a supply request no matter how recent the
 * supply request is.
 */
fun List<EmergencyReport>.triageSorted(): List<EmergencyReport> =
    sortedWith(compareBy<EmergencyReport> { it.category.priority }.thenByDescending { it.receivedAt })
