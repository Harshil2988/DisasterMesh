package com.disastermesh.app.nearby

/** Where a peer is in its connection lifecycle. */
enum class PeerState {
    /** Seen while scanning, not connected yet. */
    DISCOVERED,

    /** A connection is being negotiated right now. */
    CONNECTING,

    /** Connected — we can send to it and relay through it. */
    CONNECTED
}

/**
 * One other node in the mesh.
 *
 * There is no A/B/C role anywhere: a peer is just an endpoint with an id, a name
 * and a state. The mesh is whatever collection of these happens to exist.
 */
data class MeshPeer(
    /** Nearby's handle for this device. Changes between sessions. */
    val endpointId: String,

    /** Stable identity of that node, e.g. "NODE-7F3A". */
    val nodeId: String,

    /** Device model, for humans. */
    val deviceModel: String,

    val state: PeerState
) {
    val displayName: String get() = "$nodeId ($deviceModel)"
}

/**
 * One line in the message log.
 *
 * [text] is the human summary. The remaining fields are the real values taken
 * straight off the [MeshMessage] when one was involved, so the Messages screen
 * can render them as structured cards instead of parsing the summary string.
 * They are null when the event had no message (e.g. a relay failure).
 */
data class MeshLogEntry(
    val text: String,
    val kind: Kind,
    /** The message body itself, e.g. "HELLO". */
    val payload: String? = null,
    /** Node id of the ORIGINAL sender, e.g. "NODE-7F3A". */
    val fromNode: String? = null,
    /** Relays the message had already made when it reached us. */
    val hops: Int? = null,
    /** Hops the message had left at this point. */
    val ttl: Int? = null,
    /** The envelope's unique id, used to highlight a notification's message. */
    val messageId: String? = null,
    /** The ORIGINAL sender's coordinates, when the message carried them. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    /**
     * When this entry was created on THIS phone. A genuine local receipt time —
     * the wire envelope carries no clock, so this is not the sender's time and is
     * never presented as one. Defaulted, so no existing call site changes.
     */
    val timestamp: Long = System.currentTimeMillis()
) {

    /** True when this entry has coordinates worth showing. */
    val hasLocation: Boolean get() = latitude != null && longitude != null
    enum class Kind { SENT, RECEIVED, RELAYED, DROPPED }

    /** True when the payload was sent as an emergency broadcast. */
    val isSos: Boolean get() = payload?.startsWith(SOS_PREFIX) == true

    /** The payload without the SOS marker, for display. */
    val displayPayload: String
        get() = payload?.removePrefix(SOS_PREFIX)?.trim() ?: text

    companion object {
        /** Marker the SOS button puts at the front of an emergency payload. */
        const val SOS_PREFIX = "SOS:"
    }
}

/**
 * The complete picture the UI draws. Immutable, so Compose can diff it safely.
 *
 * Every device runs this same state — there is no per-role variant.
 */
data class MeshState(
    val nodeId: String = "",
    val deviceModel: String = "",
    val meshActive: Boolean = false,
    val advertising: Boolean = false,
    val discovering: Boolean = false,
    val status: String = "Mesh stopped",
    /** Every peer we currently know about, keyed by endpoint id. */
    val peers: Map<String, MeshPeer> = emptyMap(),
    val messages: List<MeshLogEntry> = emptyList()
) {
    /** Peers we can actually send to, in a stable order so the list does not jump. */
    val connectedPeers: List<MeshPeer>
        get() = peers.values.filter { it.state == PeerState.CONNECTED }.sortedBy { it.nodeId }

    /** Peers found but not connected yet — shown so discovery is visibly working. */
    val pendingPeers: List<MeshPeer>
        get() = peers.values.filter { it.state != PeerState.CONNECTED }.sortedBy { it.nodeId }

    val connectedCount: Int get() = connectedPeers.size

    val isConnected: Boolean get() = connectedCount > 0
}
