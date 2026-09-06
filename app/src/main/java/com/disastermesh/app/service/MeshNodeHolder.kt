package com.disastermesh.app.service

import android.content.Context
import com.disastermesh.app.nearby.NearbyConnectionManager

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
}
