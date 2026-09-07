package com.disastermesh.app.command

import com.disastermesh.app.nearby.MeshLogEntry
import org.json.JSONObject

/**
 * What kind of situation is being reported, and how urgent it is.
 *
 * [priority] is deterministic and transparent — a fixed number per category,
 * with no scoring, heuristics or machine learning anywhere. P0 outranks P4,
 * always, and a reader can predict the ordering exactly.
 */
enum class ReportCategory(
    val code: String,
    val label: String,
    val priority: Int,
    val priorityLabel: String
) {
    CRITICAL("CRIT", "Critical", 0, "P0"),
    MEDICAL("MED", "Medical", 1, "P1"),
    WARNING("WARN", "Warning", 2, "P2"),
    SUPPLY("SUP", "Supply", 3, "P3"),
    SAFE("SAFE", "Safe", 4, "P4");

    /** Emergencies that should reach the recipient loudly. */
    val isUrgent: Boolean get() = this == CRITICAL || this == MEDICAL

    companion object {
        /** Never throws on unknown input from another node. */
        fun fromCode(code: String?): ReportCategory =
            entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) }
                ?: entries.firstOrNull { it.label.equals(code?.trim(), ignoreCase = true) }
                ?: WARNING
    }
}

/** How this device is handling a report. Local to this phone. */
enum class ReportStatus(val label: String) {
    UNRESOLVED("Unresolved"),
    ACKNOWLEDGED("Acknowledged"),
    RESPONDING("Responding"),
    RESOLVED("Resolved");

    companion object {
        fun fromName(name: String?): ReportStatus =
            entries.firstOrNull { it.name == name } ?: UNRESOLVED
    }
}

/**
 * One emergency situation known to this device.
 *
 * Reports travel inside the EXISTING mesh envelope: the structured part is
 * encoded into [com.disastermesh.app.nearby.MeshMessage.payload], which the
 * networking layer treats as an opaque string. Nothing about relaying, TTL,
 * hop counting or duplicate suppression changes to carry one.
 *
 * [reportId] is the mesh message id, so the network's duplicate protection
 * remains the single authority on what counts as new.
 */
data class EmergencyReport(
    val reportId: String,
    val senderId: String,
    val category: ReportCategory,
    val description: String,
    /** When this device received or created it. */
    val receivedAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val peopleAffected: Int? = null,
    val hops: Int? = null,
    val ttl: Int? = null,
    val status: ReportStatus = ReportStatus.UNRESOLVED,
    /** True when this device is the origin. */
    val fromSelf: Boolean = false
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null

    val isOpen: Boolean get() = status != ReportStatus.RESOLVED

    /** Free-text haystack for the local search box. */
    val searchText: String
        get() = "$senderId ${category.label} $description".lowercase()

    fun toJson(): JSONObject = JSONObject().apply {
        put(K_ID, reportId)
        put(K_SENDER, senderId)
        put(K_CATEGORY, category.code)
        put(K_DESC, description)
        put(K_TIME, receivedAt)
        latitude?.let { put(K_LAT, it) }
        longitude?.let { put(K_LON, it) }
        peopleAffected?.let { put(K_PEOPLE, it) }
        hops?.let { put(K_HOPS, it) }
        ttl?.let { put(K_TTL, it) }
        put(K_STATUS, status.name)
        put(K_SELF, fromSelf)
    }

    companion object {
        /** Marks a structured report inside an otherwise free-text payload. */
        const val WIRE_PREFIX = "DMR1:"

        private const val MAX_DESCRIPTION = 240

        private const val K_ID = "id"
        private const val K_SENDER = "src"
        private const val K_CATEGORY = "cat"
        private const val K_DESC = "desc"
        private const val K_TIME = "ts"
        private const val K_LAT = "lat"
        private const val K_LON = "lon"
        private const val K_PEOPLE = "ppl"
        private const val K_HOPS = "hops"
        private const val K_TTL = "ttl"
        private const val K_STATUS = "st"
        private const val K_SELF = "self"

        /**
         * Builds the payload string that goes into the existing mesh envelope.
         *
         * Urgent categories keep the legacy "SOS:" prefix so the existing
         * notifier still routes them to the high-priority channel. That is why
         * the notification system needed no changes at all.
         */
        fun buildPayload(
            category: ReportCategory,
            description: String,
            peopleAffected: Int?
        ): String {
            val body = JSONObject().apply {
                put(K_CATEGORY, category.code)
                put(K_DESC, sanitizeDescription(description))
                peopleAffected?.let { if (it > 0) put(K_PEOPLE, it) }
            }.toString()

            val structured = WIRE_PREFIX + body
            return if (category.isUrgent) {
                "${MeshLogEntry.SOS_PREFIX} ${category.label} | $structured"
            } else {
                structured
            }
        }

        /**
         * Turns a received log entry into a report, or null when the message was
         * ordinary chat rather than a report.
         *
         * Defensive throughout: anything malformed from an unknown phone is
         * sanitised or rejected, never allowed to crash the command view.
         */
        fun fromLogEntry(entry: MeshLogEntry, localNodeId: String): EmergencyReport? {
            val payload = entry.payload ?: return null
            val messageId = entry.messageId ?: return null

            val structured = payload.substringAfter(WIRE_PREFIX, "")
            val isLegacySos = entry.isSos

            // Plain chat is not a report.
            if (structured.isBlank() && !isLegacySos) return null

            var category: ReportCategory
            var description: String
            var people: Int? = null

            if (structured.isNotBlank()) {
                val json = try {
                    JSONObject(structured)
                } catch (e: Exception) {
                    null
                }
                if (json == null) {
                    // Corrupt structured block. Fall back rather than dropping an
                    // emergency entirely — a garbled SOS is still an SOS.
                    if (!isLegacySos) return null
                    category = ReportCategory.CRITICAL
                    description = "Emergency broadcast (unreadable details)"
                } else {
                    category = ReportCategory.fromCode(json.optString(K_CATEGORY, ""))
                    description = sanitizeDescription(json.optString(K_DESC, ""))
                    people = json.optInt(K_PEOPLE, -1).takeIf { it > 0 }
                    if (description.isBlank()) description = defaultDescription(category)
                }
            } else {
                // Legacy "SOS: MEDICAL" with no structured block.
                val label = payload.removePrefix(MeshLogEntry.SOS_PREFIX)
                    .substringBefore("|")
                    .trim()
                category = ReportCategory.fromCode(label)
                if (category == ReportCategory.WARNING) category = ReportCategory.CRITICAL
                description = defaultDescription(category)
            }

            return EmergencyReport(
                reportId = messageId,
                senderId = entry.fromNode?.takeIf { it.isNotBlank() } ?: "UNKNOWN",
                category = category,
                description = description,
                receivedAt = sanitizeTimestamp(entry.timestamp),
                latitude = validLatitude(entry.latitude),
                longitude = validLongitude(entry.longitude),
                peopleAffected = people,
                hops = entry.hops?.takeIf { it >= 0 },
                ttl = entry.ttl?.takeIf { it >= 0 },
                fromSelf = entry.fromNode == localNodeId
            )
        }

        /** Restores a persisted report, or null when the stored row is unusable. */
        fun fromStoredJson(json: JSONObject): EmergencyReport? = try {
            val id = json.getString(K_ID)
            EmergencyReport(
                reportId = id,
                senderId = json.optString(K_SENDER, "UNKNOWN"),
                category = ReportCategory.fromCode(json.optString(K_CATEGORY, "")),
                description = sanitizeDescription(json.optString(K_DESC, "")),
                receivedAt = sanitizeTimestamp(json.optLong(K_TIME, 0L)),
                latitude = validLatitude(if (json.has(K_LAT)) json.getDouble(K_LAT) else null),
                longitude = validLongitude(if (json.has(K_LON)) json.getDouble(K_LON) else null),
                peopleAffected = json.optInt(K_PEOPLE, -1).takeIf { it > 0 },
                hops = json.optInt(K_HOPS, -1).takeIf { it >= 0 },
                ttl = json.optInt(K_TTL, -1).takeIf { it >= 0 },
                status = ReportStatus.fromName(json.optString(K_STATUS, "")),
                fromSelf = json.optBoolean(K_SELF, false)
            )
        } catch (e: Exception) {
            null
        }

        private fun defaultDescription(category: ReportCategory): String = when (category) {
            ReportCategory.CRITICAL -> "Critical emergency reported"
            ReportCategory.MEDICAL -> "Medical assistance required"
            ReportCategory.WARNING -> "Hazard reported"
            ReportCategory.SUPPLY -> "Supplies required"
            ReportCategory.SAFE -> "Reported safe"
        }

        /** Trims and bounds text arriving from an unknown phone. */
        private fun sanitizeDescription(raw: String): String =
            raw.trim().replace(Regex("\\s+"), " ").take(MAX_DESCRIPTION)

        /** Rejects absurd clocks; falls back to now. */
        private fun sanitizeTimestamp(value: Long): Long {
            val now = System.currentTimeMillis()
            val tenYears = 10L * 365 * 24 * 60 * 60 * 1000
            return if (value <= 0 || value > now + tenYears) now else value
        }

        private fun validLatitude(value: Double?): Double? =
            value?.takeIf { it.isFinite() && it >= -90.0 && it <= 90.0 }

        private fun validLongitude(value: Double?): Double? =
            value?.takeIf { it.isFinite() && it >= -180.0 && it <= 180.0 }
    }
}
