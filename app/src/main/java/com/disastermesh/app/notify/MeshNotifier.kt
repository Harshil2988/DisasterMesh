package com.disastermesh.app.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.disastermesh.app.MainActivity
import com.disastermesh.app.R
import com.disastermesh.app.nearby.MeshLogEntry
import com.disastermesh.app.nearby.MeshMessage

/**
 * Turns an incoming mesh message into an Android notification.
 *
 * Everything here is local to the phone: no Firebase, no FCM, no server. The
 * mesh delivers the message over Bluetooth/Wi-Fi and this class posts a
 * notification about it.
 *
 * It is deliberately incapable of affecting the network — it only reads a
 * message that the mesh has already accepted.
 */
class MeshNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)
    private val prefs = NotificationPreferences(appContext)

    init {
        createChannels()
    }

    /**
     * Posts a notification for a genuinely new incoming message.
     *
     * The caller guarantees novelty: this is only reached after the manager has
     * discarded the phone's own messages and after SeenMessages has confirmed the
     * message id has never been handled. So one unique message can produce at
     * most one notification here, no matter how many copies arrive.
     */
    fun notifyIncoming(message: MeshMessage) {
        val isSos = message.payload.startsWith(MeshLogEntry.SOS_PREFIX)

        // Respect the user's switches.
        if (isSos && !prefs.sosEnabled) return
        if (!isSos && !prefs.messagesEnabled) return

        if (!canPostNotifications()) return

        val body = message.payload.removePrefix(MeshLogEntry.SOS_PREFIX).trim()
        val hopText = if (message.hops == 1) "1 hop" else "${message.hops} hops"

        val title = if (isSos) {
            // The category the sender picked travels in the payload, e.g. "SOS: MEDICAL".
            "🚨 SOS · ${body.ifBlank { "EMERGENCY" }}"
        } else {
            "New message from ${message.senderId}"
        }

        val summary = if (isSos) {
            "From ${message.senderId} · $hopText"
        } else {
            body
        }

        val detail = buildString {
            if (isSos) appendLine(body.ifBlank { "Emergency broadcast" })
            else appendLine(body)
            appendLine("From: ${message.senderId}")
            // Only stated when the envelope actually carried coordinates.
            if (message.hasLocation) {
                appendLine("📍 Location attached")
                appendLine("%.6f, %.6f".format(message.latitude, message.longitude))
            }
            append("Hops: ${message.hops}  ·  TTL: ${message.ttl}")
        }

        val builder = NotificationCompat.Builder(
            appContext,
            if (isSos) CHANNEL_EMERGENCY else CHANNEL_MESSAGES
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setAutoCancel(true)
            .setContentIntent(openMessagesIntent(message.messageId))
            .setPriority(
                if (isSos) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
            )
            .setCategory(
                if (isSos) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE
            )

        if (isSos) {
            // SOS stays ungrouped so a burst of ordinary chatter can never bury it,
            // and gets a direct action to open the message.
            builder.addAction(0, "VIEW SOS", openMessagesIntent(message.messageId))
        } else {
            // Ordinary messages collapse under one summary instead of spamming.
            builder.setGroup(GROUP_MESSAGES)
        }

        try {
            // Keyed on the message id, so even a retry could only replace this
            // notification rather than stack a second copy.
            manager.notify(message.messageId.hashCode(), builder.build())
            if (!isSos) postGroupSummary()
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post. Never crash the mesh.
            Log.w(TAG, "Notification not posted: ${e.message}")
        }
    }

    /**
     * The single "DisasterMesh · new messages" header the individual message
     * notifications collapse under. SOS never joins this group.
     */
    private fun postGroupSummary() {
        val summary = NotificationCompat.Builder(appContext, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DisasterMesh")
            .setContentText("New mesh messages")
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("New mesh messages"))
            .setGroup(GROUP_MESSAGES)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(openMessagesIntent(""))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            manager.notify(GROUP_SUMMARY_ID, summary)
        } catch (e: SecurityException) {
            Log.w(TAG, "Group summary not posted: ${e.message}")
        }
    }

    /** Tapping the notification opens the app on the Messages tab. */
    private fun openMessagesIntent(messageId: String): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_MESSAGES, true)
            putExtra(MainActivity.EXTRA_MESSAGE_ID, messageId)
        }
        return PendingIntent.getActivity(
            appContext,
            messageId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Two channels so the user can silence chatter without silencing emergencies.
     * NotificationChannelCompat handles the pre-Android-8 case by doing nothing.
     */
    private fun createChannels() {
        val messages = NotificationChannelCompat
            .Builder(CHANNEL_MESSAGES, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("DisasterMesh Messages")
            .setDescription("Ordinary messages received from nearby mesh nodes.")
            .build()

        val emergency = NotificationChannelCompat
            .Builder(CHANNEL_EMERGENCY, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("DisasterMesh Emergency")
            .setDescription("SOS broadcasts relayed through the mesh.")
            .setVibrationEnabled(true)
            .build()

        manager.createNotificationChannelsCompat(listOf(messages, emergency))
    }

    /** Android 13+ needs POST_NOTIFICATIONS; older versions do not. */
    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return manager.areNotificationsEnabled()
    }

    companion object {
        private const val TAG = "DisasterMesh"
        const val CHANNEL_MESSAGES = "mesh_messages"
        const val CHANNEL_EMERGENCY = "mesh_emergency"
        private const val GROUP_MESSAGES = "mesh_message_group"

        /** Fixed id: the summary is replaced, never stacked. */
        private const val GROUP_SUMMARY_ID = 2
    }
}
