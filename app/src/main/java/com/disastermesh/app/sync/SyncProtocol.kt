package com.disastermesh.app.sync

import org.json.JSONArray
import org.json.JSONObject

/**
 * The synchronisation control protocol.
 *
 * Rides on the existing Nearby byte transport but is deliberately a separate
 * language: every packet starts with [MAGIC], which the mesh envelope parser
 * cannot mistake for a message. Sync traffic and mesh traffic never overlap.
 *
 *   A -> B   INVENTORY   "these are the ids I hold"
 *   B -> A   REQUEST     "send me these ones I lack"
 *   A -> B   BATCH       [the messages]
 *   B -> A   INVENTORY   (the reply half, making it bidirectional)
 */
object SyncProtocol {

    /** Distinct prefix so a control packet is never parsed as a mesh message. */
    const val MAGIC = "DMSYNC1:"

    const val TYPE_INVENTORY = "inv"
    const val TYPE_REQUEST = "req"
    const val TYPE_BATCH = "batch"

    private const val K_TYPE = "t"
    private const val K_IDS = "ids"
    private const val K_MESSAGES = "msgs"
    private const val K_MORE = "more"

    fun isSyncPacket(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC.length) return false
        // Compare only the prefix; avoids decoding a large hostile payload.
        val head = String(bytes, 0, MAGIC.length, Charsets.UTF_8)
        return head == MAGIC
    }

    fun inventory(ids: List<String>): ByteArray = encode(
        JSONObject().apply {
            put(K_TYPE, TYPE_INVENTORY)
            put(K_IDS, JSONArray(ids.take(SyncConfig.MAX_INVENTORY_IDS)))
        }
    )

    fun request(ids: List<String>): ByteArray = encode(
        JSONObject().apply {
            put(K_TYPE, TYPE_REQUEST)
            put(K_IDS, JSONArray(ids.take(SyncConfig.MAX_REQUEST_IDS)))
        }
    )

    fun batch(messages: List<StoredMessage>, more: Boolean): ByteArray = encode(
        JSONObject().apply {
            put(K_TYPE, TYPE_BATCH)
            put(K_MESSAGES, JSONArray().apply {
                messages.take(SyncConfig.MAX_MESSAGES_PER_BATCH).forEach { put(it.toJson()) }
            })
            put(K_MORE, more)
        }
    )

    private fun encode(json: JSONObject): ByteArray =
        (MAGIC + json.toString()).toByteArray(Charsets.UTF_8)

    /** A decoded packet, or null when the bytes were not valid or were too large. */
    fun decode(bytes: ByteArray): SyncPacket? {
        // Refuse an oversized packet before allocating a string from it.
        if (bytes.size > SyncConfig.MAX_PACKET_BYTES) return null
        if (!isSyncPacket(bytes)) return null

        return try {
            val body = String(bytes, Charsets.UTF_8).removePrefix(MAGIC)
            val json = JSONObject(body)
            when (json.optString(K_TYPE, "")) {
                TYPE_INVENTORY -> SyncPacket.Inventory(readIds(json))
                TYPE_REQUEST -> SyncPacket.Request(readIds(json))
                TYPE_BATCH -> {
                    val array = json.optJSONArray(K_MESSAGES) ?: JSONArray()
                    val messages = buildList {
                        val limit = minOf(array.length(), SyncConfig.MAX_MESSAGES_PER_BATCH)
                        for (i in 0 until limit) {
                            val row = array.optJSONObject(i) ?: continue
                            // A malformed entry is skipped, never fatal.
                            StoredMessage.fromJson(row)?.let { add(it) }
                        }
                    }
                    SyncPacket.Batch(messages, json.optBoolean(K_MORE, false))
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Bounded, de-duplicated, sanity-checked id list from an untrusted peer. */
    private fun readIds(json: JSONObject): List<String> {
        val array = json.optJSONArray(K_IDS) ?: return emptyList()
        val limit = minOf(array.length(), SyncConfig.MAX_ACCEPTED_INVENTORY)
        val out = LinkedHashSet<String>(limit.coerceAtMost(256))
        for (i in 0 until limit) {
            val id = array.optString(i, "").trim()
            if (id.isNotBlank() && id.length <= 128) out.add(id)
        }
        return out.toList()
    }
}

sealed interface SyncPacket {
    data class Inventory(val ids: List<String>) : SyncPacket
    data class Request(val ids: List<String>) : SyncPacket
    data class Batch(val messages: List<StoredMessage>, val more: Boolean) : SyncPacket
}
