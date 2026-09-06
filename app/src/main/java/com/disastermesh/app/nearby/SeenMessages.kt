package com.disastermesh.app.nearby

/**
 * Remembers which message ids this node has already handled.
 *
 * Without this, a mesh loops forever: A sends to B, B relays to C, C relays back
 * to B, B relays to A... Every node drops a message it has seen before, which is
 * what makes the flood terminate.
 *
 * Bounded so a long-running node cannot grow without limit — the oldest ids are
 * evicted first, which is safe because old messages are no longer in flight.
 */
class SeenMessages(private val capacity: Int = 256) {

    private val ids = LinkedHashSet<String>()

    /**
     * Records [messageId].
     * @return true if this is the first time we have seen it (so it may be
     *         processed), false if it is a duplicate (so it must be dropped).
     */
    @Synchronized
    fun markSeen(messageId: String): Boolean {
        if (!ids.add(messageId)) return false

        if (ids.size > capacity) {
            val oldest = ids.iterator()
            oldest.next()
            oldest.remove()
        }
        return true
    }

    @Synchronized
    fun clear() = ids.clear()
}
