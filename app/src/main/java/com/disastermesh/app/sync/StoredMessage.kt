package com.disastermesh.app.sync

import com.disastermesh.app.command.EmergencyReport
import com.disastermesh.app.command.ReportCategory
import com.disastermesh.app.nearby.MeshLogEntry
import com.disastermesh.app.nearby.MeshMessage
import org.json.JSONObject

/**
 * A message retained for onward carriage.
 *
 * Holds everything needed to reconstruct the ORIGINAL envelope. The original
 * sender, timestamp, coordinates and category are preserved exactly — a node
 * that carries a message never becomes its author.
 */
data class StoredMessage(
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val destinationId: String,
    val payload: String,
    /** TTL as it stood when we received it. Forwarding rules still apply. */
    val ttl: Int,
    val hops: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationTime: Long? = null,
    /** The ORIGINAL send time, not when this device saw it. */
    val originalTimestamp: Long,
    /** When this device first stored it. */
    val storedAt: Long,
    /** Node that handed it to us via sync, when it arrived that way. */
    val syncedFrom: String? = null
) {
    /**
     * Emergency priority, reused from the command taxonomy so there is one
     * ranking in the app. 5 = ordinary chat.
     */
    val priority: Int
        get() = when {
            payload.contains(EmergencyReport.WIRE_PREFIX) || isSos -> categoryPriority()
            else -> NORMAL_PRIORITY
        }

    val isSos: Boolean get() = payload.startsWith(MeshLogEntry.SOS_PREFIX)

    val isEmergency: Boolean get() = priority < NORMAL_PRIORITY

    private fun categoryPriority(): Int {
        val structured = payload.substringAfter(EmergencyReport.WIRE_PREFIX, "")
        if (structured.isNotBlank()) {
            runCatching {
                val code = JSONObject(structured).optString("cat", "")
                return ReportCategory.fromCode(code).priority
            }
        }
        return if (isSos) ReportCategory.CRITICAL.priority else NORMAL_PRIORITY
    }

    /** Rebuilds the exact envelope for onward transmission. */
    fun toMeshMessage(): MeshMessage = MeshMessage(
        messageId = messageId,
        senderId = senderId,
        senderName = senderName,
        destinationId = destinationId,
        payload = payload,
        ttl = ttl,
        hops = hops,
        latitude = latitude,
        longitude = longitude,
        locationTime = locationTime
    )

    /**
     * Whether this may still be offered onward.
     *
     * Separate from visibility: an expired message stays in the user's history
     * but stops travelling. TTL 0 means the existing relay rules have retired
     * it, and this layer does not override them.
     */
    fun isForwardable(now: Long): Boolean =
        ttl > 0 && (now - storedAt) <= MessageRetentionConfig.MAX_MESSAGE_AGE_MS

    fun toJson(): JSONObject = JSONObject().apply {
        put(K_ID, messageId)
        put(K_SENDER, senderId)
        put(K_SENDER_NAME, senderName)
        put(K_DEST, destinationId)
        put(K_PAYLOAD, payload)
        put(K_TTL, ttl)
        put(K_HOPS, hops)
        latitude?.let { put(K_LAT, it) }
        longitude?.let { put(K_LON, it) }
        locationTime?.let { put(K_LOC_TIME, it) }
        put(K_ORIGINAL_TS, originalTimestamp)
        put(K_STORED_AT, storedAt)
        syncedFrom?.let { put(K_SYNCED_FROM, it) }
    }

    companion object {
        const val NORMAL_PRIORITY = 5

        private const val K_ID = "id"
        private const val K_SENDER = "src"
        private const val K_SENDER_NAME = "srcName"
        private const val K_DEST = "dst"
        private const val K_PAYLOAD = "body"
        private const val K_TTL = "ttl"
        private const val K_HOPS = "hops"
        private const val K_LAT = "lat"
        private const val K_LON = "lon"
        private const val K_LOC_TIME = "locTs"
        private const val K_ORIGINAL_TS = "ots"
        private const val K_STORED_AT = "sat"
        private const val K_SYNCED_FROM = "from"

        fun from(message: MeshMessage, originalTimestamp: Long, syncedFrom: String? = null) =
            StoredMessage(
                messageId = message.messageId,
                senderId = message.senderId,
                senderName = message.senderName,
                destinationId = message.destinationId,
                payload = message.payload,
                ttl = message.ttl,
                hops = message.hops,
                latitude = message.latitude,
                longitude = message.longitude,
                locationTime = message.locationTime,
                originalTimestamp = originalTimestamp,
                storedAt = System.currentTimeMillis(),
                syncedFrom = syncedFrom
            )

        /**
         * Parses a message offered by a peer.
         *
         * Every field is bounds-checked: a remote node is not trusted to send
         * sane values, and anything malformed is rejected rather than stored.
         */
        fun fromJson(json: JSONObject): StoredMessage? = try {
            val id = json.optString(K_ID, "").trim()
            val sender = json.optString(K_SENDER, "").trim()
            val payload = json.optString(K_PAYLOAD, "")

            when {
                id.isBlank() || id.length > 128 -> null
                sender.isBlank() || sender.length > 64 -> null
                payload.length > SyncConfig.MAX_PAYLOAD_CHARS -> null
                else -> StoredMessage(
                    messageId = id,
                    senderId = sender,
                    senderName = json.optString(K_SENDER_NAME, sender).take(128),
                    destinationId = json.optString(K_DEST, MeshMessage.BROADCAST).take(64),
                    payload = payload,
                    ttl = json.optInt(K_TTL, 0).coerceIn(0, 32),
                    hops = json.optInt(K_HOPS, 0).coerceIn(0, 64),
                    latitude = validLat(json),
                    longitude = validLon(json),
                    locationTime = json.optLong(K_LOC_TIME, 0L).takeIf { it > 0 },
                    originalTimestamp = sanitizeTime(json.optLong(K_ORIGINAL_TS, 0L)),
                    storedAt = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            null
        }

        private fun validLat(json: JSONObject): Double? =
            if (!json.has(K_LAT)) null
            else json.optDouble(K_LAT, Double.NaN)
                .takeIf { it.isFinite() && it >= -90.0 && it <= 90.0 }

        private fun validLon(json: JSONObject): Double? =
            if (!json.has(K_LON)) null
            else json.optDouble(K_LON, Double.NaN)
                .takeIf { it.isFinite() && it >= -180.0 && it <= 180.0 }

        /** A peer clock far in the future is not allowed to pin a message forever. */
        private fun sanitizeTime(value: Long): Long {
            val now = System.currentTimeMillis()
            return if (value <= 0 || value > now + 24 * 60 * 60 * 1000L) now else value
        }
    }
}
