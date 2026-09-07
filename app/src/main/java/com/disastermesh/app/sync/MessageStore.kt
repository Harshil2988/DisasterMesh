package com.disastermesh.app.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

/**
 * The forwarding store: what this node carries on behalf of the mesh.
 *
 * Persisted, so a node that is killed and restarted still has something to give
 * the next phone it meets — which is the whole point of store-carry-forward.
 * The original sender is never rewritten.
 */
class MessageStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val messages = LinkedHashMap<String, StoredMessage>()

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    init {
        load()
    }

    @Synchronized
    fun has(messageId: String): Boolean = messages.containsKey(messageId)

    @Synchronized
    fun get(messageId: String): StoredMessage? = messages[messageId]

    /**
     * Ids to advertise, newest and most urgent first so a truncated inventory
     * still leads with the emergencies.
     */
    @Synchronized
    fun inventoryIds(limit: Int = SyncConfig.MAX_INVENTORY_IDS): List<String> {
        val now = System.currentTimeMillis()
        return messages.values
            .filter { it.isForwardable(now) }
            .sortedWith(compareBy<StoredMessage> { it.priority }.thenByDescending { it.storedAt })
            .take(limit)
            .map { it.messageId }
    }

    /**
     * Resolves a peer's request. Only messages that exist here AND remain
     * forwardable are returned, in emergency priority order.
     */
    @Synchronized
    fun forRequest(ids: List<String>, limit: Int): List<StoredMessage> {
        val now = System.currentTimeMillis()
        return ids.asSequence()
            .distinct()
            .take(SyncConfig.MAX_REQUEST_IDS)
            .mapNotNull { messages[it] }
            .filter { it.isForwardable(now) }
            .sortedWith(compareBy<StoredMessage> { it.priority }.thenByDescending { it.storedAt })
            .take(limit)
            .toList()
    }

    /** Which of a peer's advertised ids we do not hold. */
    @Synchronized
    fun missingFrom(peerIds: List<String>): List<String> =
        peerIds.asSequence()
            .distinct()
            .take(SyncConfig.MAX_ACCEPTED_INVENTORY)
            .filterNot { messages.containsKey(it) }
            .take(SyncConfig.MAX_REQUEST_IDS)
            .toList()

    /** @return true when this message was not already held. */
    @Synchronized
    fun store(message: StoredMessage): Boolean {
        if (messages.containsKey(message.messageId)) return false
        messages[message.messageId] = message
        evictIfNeeded()
        _count.value = messages.size
        save()
        return true
    }

    @Synchronized
    fun purgeExpired() {
        val now = System.currentTimeMillis()
        val before = messages.size
        val expired = messages.values
            .filter { (now - it.storedAt) > MessageRetentionConfig.MAX_MESSAGE_AGE_MS }
            .map { it.messageId }
        expired.forEach { messages.remove(it) }
        if (messages.size != before) {
            _count.value = messages.size
            save()
        }
    }

    /**
     * Priority-aware eviction.
     *
     * Ordinary chat is trimmed first, then the oldest low-priority items. A
     * fresh critical SOS is never dropped to make room for older normal chat.
     */
    private fun evictIfNeeded() {
        val normal = messages.values.filter { !it.isEmergency }
        if (normal.size > MessageRetentionConfig.MAX_STORED_NORMAL_MESSAGES) {
            normal.sortedBy { it.storedAt }
                .take(normal.size - MessageRetentionConfig.MAX_STORED_NORMAL_MESSAGES)
                .forEach { messages.remove(it.messageId) }
        }

        if (messages.size > MessageRetentionConfig.MAX_STORED_MESSAGES) {
            val excess = messages.size - MessageRetentionConfig.MAX_STORED_MESSAGES
            messages.values
                .sortedWith(
                    compareByDescending<StoredMessage> { it.priority }.thenBy { it.storedAt }
                )
                .take(excess)
                .forEach { messages.remove(it.messageId) }
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                StoredMessage.fromJson(row)?.let { messages[it.messageId] = it }
            }
            purgeExpired()
            _count.value = messages.size
            Log.d(TAG, "Restored ${messages.size} carried messages")
        } catch (e: Exception) {
            Log.w(TAG, "Could not read message store", e)
            runCatching { file.delete() }
            messages.clear()
            _count.value = 0
        }
    }

    private fun save() {
        val snapshot = messages.values.toList()
        io.launch {
            try {
                val array = JSONArray()
                snapshot.forEach { array.put(it.toJson()) }
                file.writeText(array.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Could not persist message store", e)
            }
        }
    }

    private companion object {
        const val TAG = "DisasterMesh"
        const val FILE_NAME = "carried_messages.json"
    }
}
