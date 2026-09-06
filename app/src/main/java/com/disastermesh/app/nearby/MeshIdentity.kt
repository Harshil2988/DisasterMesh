package com.disastermesh.app.nearby

import android.content.Context
import android.os.Build
import kotlin.random.Random

/**
 * Gives this installation a short, stable identity such as "NODE-7F3A".
 *
 * The id is generated once and kept in SharedPreferences so it survives an app
 * restart. That matters for two reasons:
 *  - a message carries its originator's node id, and a node must still recognise
 *    its own id after being restarted mid-demo, and
 *  - two nodes use their ids to agree on which side opens the connection.
 *
 * No login, no account, no server.
 */
object MeshIdentity {

    private const val PREFS = "disaster_mesh"
    private const val KEY_NODE_ID = "node_id_v2"

    /** Separator between node id and device model on the wire. */
    private const val SEPARATOR = "|"

    /** Short id for this phone, e.g. "NODE-7F3A". */
    fun nodeId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_NODE_ID, null)?.let { return it }

        // Four hex characters: readable out loud, and plenty for a room of phones.
        val generated = "NODE-%04X".format(Random.nextInt(0x10000))
        prefs.edit().putString(KEY_NODE_ID, generated).apply()
        return generated
    }

    /**
     * The name this phone advertises, e.g. "NODE-7F3A|Pixel 7".
     *
     * Nearby only gives peers this one string, so it has to carry both the id we
     * compare on and something human-readable.
     */
    fun endpointName(context: Context): String =
        nodeId(context) + SEPARATOR + Build.MODEL.ifBlank { "Android" }

    /** Pulls "NODE-7F3A" back out of an advertised name. */
    fun parseNodeId(endpointName: String): String =
        endpointName.substringBefore(SEPARATOR).ifBlank { endpointName }

    /** Pulls the device model back out, e.g. "Pixel 7". */
    fun parseModel(endpointName: String): String =
        endpointName.substringAfter(SEPARATOR, "").ifBlank { "unknown device" }
}
