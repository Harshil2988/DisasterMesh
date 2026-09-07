package com.disastermesh.app.map

import com.disastermesh.app.command.EmergencyReport
import com.disastermesh.app.command.ReportCategory

/** A geographic point that has already been validated. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    companion object {
        /**
         * Builds a point only from coordinates that are genuinely usable.
         * Anything out of range, non-finite, or null is rejected outright so an
         * invalid marker can never reach the map.
         */
        fun of(latitude: Double?, longitude: Double?): GeoPoint? {
            if (latitude == null || longitude == null) return null
            if (!latitude.isFinite() || !longitude.isFinite()) return null
            if (latitude < -90.0 || latitude > 90.0) return null
            if (longitude < -180.0 || longitude > 180.0) return null
            // Exact 0,0 is almost always an uninitialised value rather than a
            // real position in the Gulf of Guinea.
            if (latitude == 0.0 && longitude == 0.0) return null
            return GeoPoint(latitude, longitude)
        }
    }
}

/** Which overlays are currently drawn. Hiding a layer never discards data. */
data class MapLayers(
    val emergencies: Boolean = true,
    val safeReports: Boolean = true,
    val nodes: Boolean = true,
    val connections: Boolean = true,
    val myLocation: Boolean = true
)

/** Quick category filter across the map. */
enum class MapFilter(val label: String) {
    ALL("All"),
    CRITICAL("SOS"),
    MEDICAL("Medical"),
    WARNING("Warning"),
    SUPPLY("Supply"),
    SAFE("Safe");

    fun matches(category: ReportCategory): Boolean = when (this) {
        ALL -> true
        CRITICAL -> category == ReportCategory.CRITICAL
        MEDICAL -> category == ReportCategory.MEDICAL
        WARNING -> category == ReportCategory.WARNING
        SUPPLY -> category == ReportCategory.SUPPLY
        SAFE -> category == ReportCategory.SAFE
    }
}

/** Time window filter. Operates on real timestamps and never deletes anything. */
enum class MapWindow(val label: String, val millis: Long?) {
    ALL("All time", null),
    LAST_15("15 min", 15 * 60_000L),
    LAST_HOUR("1 hour", 60 * 60_000L),
    TODAY("Today", 24 * 60 * 60_000L);

    fun includes(timestamp: Long, now: Long): Boolean =
        millis == null || (now - timestamp) <= millis
}

/**
 * A node plotted on the map.
 *
 * IMPORTANT: [position] is the node's LAST REPORTED position — taken from the
 * most recent report that node sent with coordinates. The mesh does not
 * broadcast live node GPS, so this is never presented as a current position.
 */
data class MappedNode(
    val nodeId: String,
    val position: GeoPoint,
    val lastSeen: Long,
    /** True when this node is a direct peer of ours right now. */
    val directlyConnected: Boolean,
    val isSelf: Boolean
)

/** An emergency report that has a usable position. */
data class MappedReport(
    val report: EmergencyReport,
    val position: GeoPoint
)

/**
 * Everything the map draws, derived from data the app genuinely holds.
 *
 * Built by [buildMapData] so the screen itself performs no derivation.
 */
data class MapData(
    val reports: List<MappedReport> = emptyList(),
    val nodes: List<MappedNode> = emptyList(),
    val myLocation: GeoPoint? = null
) {
    val hasAnything: Boolean
        get() = reports.isNotEmpty() || nodes.isNotEmpty() || myLocation != null
}

/**
 * Turns real reports plus the real peer list into map geometry.
 *
 * Node positions come only from reports those nodes actually sent. A node that
 * has never sent a located report simply does not appear — no coordinate is ever
 * invented for it.
 */
fun buildMapData(
    reports: List<EmergencyReport>,
    connectedNodeIds: Set<String>,
    localNodeId: String,
    myLocation: GeoPoint?
): MapData {
    val mappedReports = reports.mapNotNull { report ->
        GeoPoint.of(report.latitude, report.longitude)?.let { MappedReport(report, it) }
    }

    // Last reported position per node, newest wins.
    val latestBySender = mutableMapOf<String, MappedReport>()
    mappedReports.forEach { mapped ->
        val existing = latestBySender[mapped.report.senderId]
        if (existing == null || mapped.report.receivedAt > existing.report.receivedAt) {
            latestBySender[mapped.report.senderId] = mapped
        }
    }

    val nodes = latestBySender.map { (nodeId, mapped) ->
        MappedNode(
            nodeId = nodeId,
            position = mapped.position,
            lastSeen = mapped.report.receivedAt,
            directlyConnected = connectedNodeIds.contains(nodeId),
            isSelf = nodeId == localNodeId
        )
    }.sortedBy { it.nodeId }

    return MapData(
        reports = mappedReports,
        nodes = nodes,
        myLocation = myLocation
    )
}
