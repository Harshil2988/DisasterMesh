package com.disastermesh.app.nearby

/**
 * The seam between the mesh and the optional store-carry-forward layer.
 *
 * Declared here so [NearbyConnectionManager] depends on an interface it owns
 * rather than on the sync implementation. With no hook installed the mesh
 * behaves exactly as it did before synchronisation existed.
 */
interface MeshSyncHook {

    /**
     * Offered every raw payload before mesh parsing.
     * @return true if this was a sync control packet and is fully handled.
     */
    fun onRawPayload(endpointId: String, bytes: ByteArray): Boolean

    /** A peer finished connecting and may hold history this node lacks. */
    fun onPeerConnected(endpointId: String, nodeId: String)

    fun onPeerDisconnected(endpointId: String)

    /** A genuinely new message the mesh accepted, offered for retention. */
    fun onMessageStored(message: MeshMessage)

    /**
     * A FILE payload arrived. Used for voice-message binaries, which are too
     * large to sit inside the envelope.
     *
     * Defaulted so an implementation that does not care about files — and the
     * previous behaviour of this class — remains valid.
     */
    fun onFilePayload(
        endpointId: String,
        payloadId: Long,
        descriptor: android.os.ParcelFileDescriptor?
    ) {}

    /** Progress or completion of a FILE transfer. Defaulted for the same reason. */
    fun onTransferUpdate(endpointId: String, payloadId: Long, status: Int, transferred: Long, total: Long) {}
}
