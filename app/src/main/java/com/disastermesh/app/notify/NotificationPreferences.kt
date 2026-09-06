package com.disastermesh.app.notify

import android.content.Context

/**
 * The two notification switches, stored on the device.
 *
 * These control ONLY whether a notification is posted. Receiving and relaying
 * messages is completely unaffected by them — the mesh keeps working with every
 * switch off.
 */
class NotificationPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Notify for ordinary incoming messages. Default on. */
    var messagesEnabled: Boolean
        get() = prefs.getBoolean(KEY_MESSAGES, true)
        set(value) = prefs.edit().putBoolean(KEY_MESSAGES, value).apply()

    /** Notify for incoming SOS broadcasts. Default on. */
    var sosEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOS, true)
        set(value) = prefs.edit().putBoolean(KEY_SOS, value).apply()

    /** Whether we have already shown the Android 13+ notification prompt once. */
    var permissionAsked: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED, value).apply()

    private companion object {
        // Same prefs file MeshIdentity uses; different keys.
        const val PREFS = "disaster_mesh"
        const val KEY_MESSAGES = "notify_messages"
        const val KEY_SOS = "notify_sos"
        const val KEY_ASKED = "notify_permission_asked"
    }
}
