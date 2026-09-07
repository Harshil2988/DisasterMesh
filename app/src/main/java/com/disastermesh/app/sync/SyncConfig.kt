package com.disastermesh.app.sync

/**
 * Every tunable for store-carry-forward, in one place.
 *
 * Retention deliberately does NOT depend on whether a message was handed to a
 * peer: a message one node already has may still be the only copy another node
 * will ever see. Messages age out; they are not "used up" by delivery.
 */
object MessageRetentionConfig {

    /** How long a message stays eligible to be offered to other nodes. */
    const val MAX_MESSAGE_AGE_MS = 24 * 60 * 60 * 1000L

    /** Hard cap on the forwarding store. Eviction is priority-aware. */
    const val MAX_STORED_MESSAGES = 400

    /** Emergencies outlive ordinary chat when space runs short. */
    const val MAX_STORED_NORMAL_MESSAGES = 200
}

/** Limits for the synchronisation protocol itself. */
object SyncConfig {

    /** Ids offered in one inventory. Bounds our own packet size. */
    const val MAX_INVENTORY_IDS = 200

    /** Messages carried in one batch, so no single packet is enormous. */
    const val MAX_MESSAGES_PER_BATCH = 25

    /** Ids a peer may ask for at once. Anything beyond this is truncated. */
    const val MAX_REQUEST_IDS = 100

    /**
     * Largest sync packet we will parse from a peer. A remote node cannot make
     * this device allocate more than this by claiming a huge inventory.
     */
    const val MAX_PACKET_BYTES = 96 * 1024

    /** Longest single message body accepted from a peer. */
    const val MAX_PAYLOAD_CHARS = 2_000

    /** Do not re-run a full inventory exchange with the same peer faster than this. */
    const val MIN_RESYNC_INTERVAL_MS = 20_000L

    /** Small gap between batches so a burst cannot swamp the radio. */
    const val BATCH_PAUSE_MS = 250L

    /** A peer claiming more than this many ids is treated as hostile/broken. */
    const val MAX_ACCEPTED_INVENTORY = 1_000
}
