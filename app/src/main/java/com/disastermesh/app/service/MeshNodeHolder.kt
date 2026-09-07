package com.disastermesh.app.service

import android.content.Context
import com.disastermesh.app.command.EmergencyRepository
import com.disastermesh.app.nearby.NearbyConnectionManager
import com.disastermesh.app.sync.MeshSyncManager
import com.disastermesh.app.uplink.HybridUplinkManager

/**
 * Process-wide owner of the one and only [NearbyConnectionManager].
 *
 * Previously the manager was created by MeshViewModel, so it died with the
 * Activity — pressing Back tore the mesh down. Ownership now sits at process
 * level, so the service and the UI share exactly one mesh node.
 *
 * The manager itself is completely unchanged; only who holds it moved.
 */
object MeshNodeHolder {

    @Volatile
    private var instance: NearbyConnectionManager? = null

    /** The shared mesh node, created on first use. */
    fun get(context: Context): NearbyConnectionManager =
        instance ?: synchronized(this) {
            instance ?: NearbyConnectionManager(context.applicationContext).also { instance = it }
        }

    @Volatile
    private var reportsInstance: EmergencyRepository? = null

    /**
     * The one emergency report store. Shared by the Command Center, the map and
     * the uplink so there is exactly one source of truth for reports.
     */
    fun reports(context: Context): EmergencyRepository =
        reportsInstance ?: synchronized(this) {
            reportsInstance
                ?: EmergencyRepository(context.applicationContext).also { reportsInstance = it }
        }

    @Volatile
    private var syncInstance: MeshSyncManager? = null

    /**
     * The store-carry-forward layer. Shares the one mesh node, and installs
     * itself as that node's optional sync hook when started.
     */
    fun sync(context: Context): MeshSyncManager =
        syncInstance ?: synchronized(this) {
            syncInstance ?: MeshSyncManager(
                context.applicationContext,
                get(context)
            ).also { syncInstance = it }
        }

    @Volatile
    private var uplinkInstance: HybridUplinkManager? = null

    /**
     * The shared gateway/uplink manager.
     *
     * Kept beside the mesh node rather than inside it: the two are completely
     * independent, and nothing in the relay path calls into the uplink.
     */
    fun uplink(context: Context): HybridUplinkManager =
        uplinkInstance ?: synchronized(this) {
            uplinkInstance
                ?: HybridUplinkManager(context.applicationContext).also { uplinkInstance = it }
        }
}
