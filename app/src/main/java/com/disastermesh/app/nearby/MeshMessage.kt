package com.disastermesh.app.nearby

import org.json.JSONObject
import java.util.UUID

/**
 * The envelope every DisasterMesh message travels in.
 *
 * Nearby Connections only moves bytes between two phones that are directly
 * connected. It does NOT route anything. Multi-hop delivery is entirely this
 * app's job, and these fields are what make it possible.
 *
 * Encoded as JSON with org.json, which is part of Android itself — no extra library.
 */
data class MeshMessage(
    /** Unique per message. This is what stops a message looping forever. */
    val messageId: String,

    /** Node id of the phone that originally created the message. */
    val senderId: String,

    /** Human-readable name of the original sender, for the UI. */
    val senderName: String,

    /** [BROADCAST] for everyone, or a specific node id. */
    val destinationId: String,

    /** The actual content, e.g. "HELLO". */
    val payload: String,

    /** Hops remaining. Decremented at every relay; at 0 the message dies. */
    val ttl: Int,

    /** How many relays the message has already passed through. Display only. */
    val hops: Int,

    /**
     * The ORIGINAL sender's coordinates, attached only to SOS messages.
     *
     * Null for ordinary text — normal messages never carry location. Because
     * [relayed] is a data-class copy, these travel untouched through every hop,
     * so a relay can never substitute its own position for the sender's.
     */
    val latitude: Double? = null,
    val longitude: Double? = null,

    /** When the sender captured that fix (epoch millis). */
    val locationTime: Long? = null
) {

    /** True when this message carries usable coordinates. */
    val hasLocation: Boolean get() = latitude != null && longitude != null

    fun toJson(): String = JSONObject().apply {
        put(KEY_ID, messageId)
        put(KEY_SENDER, senderId)
        put(KEY_SENDER_NAME, senderName)
        put(KEY_DESTINATION, destinationId)
        put(KEY_PAYLOAD, payload)
        put(KEY_TTL, ttl)
        put(KEY_HOPS, hops)
        if (latitude != null) put(KEY_LAT, latitude)
        if (longitude != null) put(KEY_LON, longitude)
        if (locationTime != null) put(KEY_LOC_TIME, locationTime)
    }.toString()

    fun toBytes(): ByteArray = toJson().toByteArray(Charsets.UTF_8)

    /** The same message one hop further along: one less life, one more hop. */
    fun relayed(): MeshMessage = copy(ttl = ttl - 1, hops = hops + 1)

    companion object {
        /** Destination meaning "everyone who can hear this". */
        const val BROADCAST = "ALL"

        /**
         * Hops a new message is allowed to make. 5 is plenty for a hackathon
         * chain of phones and keeps a mistake from flooding the room.
         */
        const val DEFAULT_TTL = 5

        private const val KEY_ID = "id"
        private const val KEY_SENDER = "src"
        private const val KEY_SENDER_NAME = "srcName"
        private const val KEY_DESTINATION = "dst"
        private const val KEY_PAYLOAD = "body"
        private const val KEY_TTL = "ttl"
        private const val KEY_HOPS = "hops"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_LOC_TIME = "locTs"

        /** Builds a brand new message originating from this phone. */
        fun create(
            senderId: String,
            senderName: String,
            payload: String,
            destinationId: String = BROADCAST,
            ttl: Int = DEFAULT_TTL,
            latitude: Double? = null,
            longitude: Double? = null,
            locationTime: Long? = null
        ): MeshMessage = MeshMessage(
            messageId = UUID.randomUUID().toString(),
            senderId = senderId,
            senderName = senderName,
            destinationId = destinationId,
            payload = payload,
            ttl = ttl,
            hops = 0,
            latitude = latitude,
            longitude = longitude,
            locationTime = locationTime
        )

        /**
         * Parses bytes back into a message, or returns null if this is not a
         * DisasterMesh envelope (so a stray payload can never crash a node).
         */
        fun fromBytes(bytes: ByteArray): MeshMessage? = try {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            MeshMessage(
                messageId = json.getString(KEY_ID),
                senderId = json.getString(KEY_SENDER),
                senderName = json.optString(KEY_SENDER_NAME, json.getString(KEY_SENDER)),
                destinationId = json.optString(KEY_DESTINATION, BROADCAST),
                payload = json.getString(KEY_PAYLOAD),
                ttl = json.optInt(KEY_TTL, 1),
                hops = json.optInt(KEY_HOPS, 0),
                latitude = if (json.has(KEY_LAT)) json.getDouble(KEY_LAT) else null,
                longitude = if (json.has(KEY_LON)) json.getDouble(KEY_LON) else null,
                locationTime = if (json.has(KEY_LOC_TIME)) json.getLong(KEY_LOC_TIME) else null
            )
        } catch (e: Exception) {
            null
        }
    }
}
