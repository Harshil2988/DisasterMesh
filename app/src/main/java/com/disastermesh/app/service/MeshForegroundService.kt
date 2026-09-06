package com.disastermesh.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.disastermesh.app.MainActivity
import com.disastermesh.app.R

/**
 * Keeps the mesh node alive while DisasterMesh is not on screen.
 *
 * Why this exists: Nearby Connections needs a living process to keep advertising,
 * discovering and receiving. With the manager owned by the ViewModel, pressing
 * Back destroyed it, and Android's background limits (plus aggressive OEM process
 * killing) would stop reception soon after the app left the screen. A foreground
 * service is the supported way to tell Android this work must continue.
 *
 * It does no networking of its own — it starts and stops the SAME shared
 * [com.disastermesh.app.nearby.NearbyConnectionManager] the UI uses.
 */
class MeshForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createServiceChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android allows 5 seconds to post the notification, so do it first.
        // From API 34 the type must be supplied here as well as in the manifest.
        ServiceCompat.startForeground(
            this,
            SERVICE_NOTIFICATION_ID,
            buildServiceNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
        )

        if (intent?.action == ACTION_STOP) {
            stopMeshAndSelf()
            return START_NOT_STICKY
        }

        MeshNodeHolder.get(this).startMesh()
        Log.d(TAG, "Mesh service started")

        // Restart if Android reclaims the process while the mesh is meant to run.
        return START_STICKY
    }

    override fun onDestroy() {
        MeshNodeHolder.get(this).stopMesh()
        Log.d(TAG, "Mesh service destroyed; mesh stopped")
        super.onDestroy()
    }

    /** Not a bound service. */
    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopMeshAndSelf() {
        MeshNodeHolder.get(this).stopMesh()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * The persistent "mesh is running" notification. Deliberately low importance
     * and on its own channel so it is never confused with an incoming message.
     */
    private fun buildServiceNotification(): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MeshForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DisasterMesh is active")
            .setContentText("Relaying messages over the offline mesh")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(0, "STOP MESH", stop)
            .build()
    }

    private fun createServiceChannel() {
        val channel = NotificationChannelCompat
            .Builder(CHANNEL_SERVICE, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("DisasterMesh Service")
            .setDescription("Shows that the mesh is running in the background.")
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannelsCompat(listOf(channel))
    }

    companion object {
        private const val TAG = "DisasterMesh"
        private const val CHANNEL_SERVICE = "mesh_service"
        private const val SERVICE_NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.disastermesh.app.STOP_MESH"

        /** Starts the mesh. Called from the UI, so foreground-start rules are satisfied. */
        fun start(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Stops the mesh and removes the persistent notification. */
        fun stop(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java)
                .setAction(ACTION_STOP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
