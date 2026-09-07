package com.disastermesh.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.disastermesh.app.MainActivity
import com.disastermesh.app.R
import com.disastermesh.app.command.ReportCategory
import com.disastermesh.app.service.MeshNodeHolder
import com.disastermesh.app.service.MeshForegroundService

/**
 * The home-screen emergency panel.
 *
 * Reads live state from the SAME [MeshNodeHolder] the app uses and triggers the
 * SAME actions — there is no second mesh manager, no second SOS format and no
 * second service. RemoteViews are inflated in this app's own process, so the
 * singleton is directly reachable; if the process is gone the mesh genuinely is
 * not running, so the state shown is never optimistically stale.
 *
 * RemoteViews rather than Glance: Glance would add a dependency whose
 * compatibility with this project's Compose/AGP versions is unverified, and the
 * brief is explicit about not risking dependency conflicts for the widget.
 */
class DisasterMeshWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> render(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_START_MESH -> handleStartMesh(context)
            ACTION_SEND_SAFE -> handleSendSafe(context)
            ACTION_REFRESH -> refresh(context)
        }
    }

    // -----------------------------------------------------------------
    // Actions — every one delegates to existing app functionality
    // -----------------------------------------------------------------

    /** Starts the EXISTING foreground mesh service. No new service, no new manager. */
    private fun handleStartMesh(context: Context) {
        try {
            MeshForegroundService.start(context)
            setTransient(context, MESH_STARTING)
        } catch (e: Exception) {
            // Background-start restrictions can refuse this; say so rather than lie.
            Log.w(TAG, "Widget could not start mesh", e)
            setTransient(context, MESH_FAILED)
        }
        refresh(context)
    }

    /**
     * Sends a SAFE check-in through the EXISTING pipeline.
     *
     * Uses the same payload builder and the same sendText() as the in-app
     * button, so it inherits message id, TTL, relay, dedup and store-and-forward
     * unchanged. Only reports "sent" when the pipeline actually accepted it.
     */
    private fun handleSendSafe(context: Context) {
        try {
            val mesh = MeshNodeHolder.get(context)
            val state = mesh.state.value

            if (!state.meshActive) {
                setTransient(context, SAFE_MESH_OFF)
            } else if (state.connectedCount == 0) {
                // Honest: with no peers the message cannot leave this phone.
                setTransient(context, SAFE_NO_PEERS)
            } else {
                mesh.sendText(
                    ReportCategory.SAFE.let {
                        com.disastermesh.app.command.EmergencyReport.buildPayload(
                            category = it,
                            description = "Safe check-in",
                            peopleAffected = null
                        )
                    }
                )
                setTransient(context, SAFE_SENT)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Widget SAFE send failed", e)
            setTransient(context, SAFE_FAILED)
        }
        refresh(context)
    }

    // -----------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_disaster_mesh)

        // Real state, read from the shared mesh node. Never invented.
        val state = runCatching { MeshNodeHolder.get(context).state.value }.getOrNull()

        if (state == null) {
            // The mesh singleton is unreachable. Say so rather than guess.
            views.setTextViewText(R.id.widget_status, "● STATUS UNAVAILABLE")
            views.setTextColor(R.id.widget_status, context.getColor(R.color.w_text_faint))
            views.setTextViewText(R.id.widget_node, "")
            views.setTextViewText(R.id.widget_mesh_title, "START MESH")
            views.setTextViewText(R.id.widget_mesh_sub, "Mesh control")
            views.setTextViewText(R.id.widget_devices, "● DEVICES CONNECTED: —")
            views.setTextColor(R.id.widget_devices, context.getColor(R.color.w_text_faint))
        } else {
            val active = state.meshActive
            // "Starting" is a real, derivable state: the mesh is on but at least
            // one radio has not come up yet. Not invented.
            val starting = active && !(state.advertising && state.discovering)
            val count = state.connectedCount

            views.setTextViewText(
                R.id.widget_status,
                when {
                    !active -> "● MESH OFFLINE"
                    starting -> "● STARTING MESH"
                    else -> "● MESH ACTIVE"
                }
            )
            views.setTextColor(
                R.id.widget_status,
                context.getColor(
                    when {
                        !active -> R.color.w_text_faint
                        starting -> R.color.w_cyan
                        else -> R.color.w_green
                    }
                )
            )
            views.setTextViewText(R.id.widget_node, state.nodeId.ifBlank { "NODE-••••" })

            views.setTextViewText(
                R.id.widget_mesh_title,
                if (active) "MESH ACTIVE" else "START MESH"
            )
            views.setTextViewText(
                R.id.widget_mesh_sub,
                when {
                    !active -> "Mesh control"
                    count == 1 -> "1 node"
                    else -> "$count nodes"
                }
            )

            // Bottom status line. The count comes straight from the peer list.
            views.setTextViewText(
                R.id.widget_devices,
                buildString {
                    append("● ")
                    append(count)
                    append(if (count == 1) " DEVICE CONNECTED" else " DEVICES CONNECTED")
                    if (!active) append("  •  MESH OFFLINE")
                }
            )
            views.setTextColor(
                R.id.widget_devices,
                context.getColor(
                    when {
                        !active -> R.color.w_text_faint
                        count > 0 -> R.color.w_green
                        else -> R.color.w_text_dim
                    }
                )
            )
        }

        // A short-lived result line, so an action never fails silently.
        transientMessage(context)?.let { message ->
            views.setTextViewText(R.id.widget_safe_sub, message)
        }

        views.setOnClickPendingIntent(R.id.widget_header, openApp(context))
        views.setOnClickPendingIntent(R.id.widget_sos, openSos(context))
        views.setOnClickPendingIntent(R.id.widget_message, openMessages(context))
        views.setOnClickPendingIntent(R.id.widget_safe, broadcast(context, ACTION_SEND_SAFE, 3))
        views.setOnClickPendingIntent(R.id.widget_mesh, broadcast(context, ACTION_START_MESH, 4))

        manager.updateAppWidget(widgetId, views)
    }

    // -----------------------------------------------------------------
    // Intents
    // -----------------------------------------------------------------

    private fun activityIntent(context: Context, requestCode: Int, extras: Intent.() -> Unit) =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                extras()
            },
            // IMMUTABLE is required from Android 12 and is correct here: nothing
            // needs to fill these intents in later.
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun openApp(context: Context) = activityIntent(context, 1) {}

    /**
     * SOS never fires from the widget directly.
     *
     * It opens the app's existing confirmation flow, so location acquisition,
     * the confirm/cancel choice and the SOS payload all remain the app's.
     */
    private fun openSos(context: Context) = activityIntent(context, 2) {
        putExtra(MainActivity.EXTRA_OPEN_SOS, true)
    }

    /** Reuses the deep link the notification system already uses. */
    private fun openMessages(context: Context) = activityIntent(context, 5) {
        putExtra(MainActivity.EXTRA_OPEN_MESSAGES, true)
    }

    private fun broadcast(context: Context, action: String, requestCode: Int) =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, DisasterMeshWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    companion object {
        private const val TAG = "DisasterMesh"

        const val ACTION_START_MESH = "com.disastermesh.app.widget.START_MESH"
        const val ACTION_SEND_SAFE = "com.disastermesh.app.widget.SEND_SAFE"
        const val ACTION_REFRESH = "com.disastermesh.app.widget.REFRESH"

        private const val SAFE_SENT = "Safe check-in sent"
        private const val SAFE_NO_PEERS = "No connected nodes"
        private const val SAFE_MESH_OFF = "Start the mesh first"
        private const val SAFE_FAILED = "Send failed"
        private const val MESH_STARTING = "Mesh starting…"
        private const val MESH_FAILED = "Mesh start failed"

        /** How long an action result stays on the widget. */
        private const val TRANSIENT_MS = 12_000L

        @Volatile private var transientText: String? = null
        @Volatile private var transientAt: Long = 0L

        private fun setTransient(context: Context, text: String) {
            transientText = text
            transientAt = System.currentTimeMillis()
        }

        private fun transientMessage(context: Context): String? {
            val text = transientText ?: return null
            if (System.currentTimeMillis() - transientAt > TRANSIENT_MS) {
                transientText = null
                return null
            }
            return text
        }

        /**
         * Redraws every placed widget.
         *
         * Called on real events only — mesh start/stop and peer-count changes —
         * rather than on a timer, so the widget costs nothing while idle.
         */
        fun refresh(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val component = ComponentName(context, DisasterMeshWidgetProvider::class.java)
                val ids = manager.getAppWidgetIds(component)
                if (ids.isEmpty()) return
                manager.updateAppWidget(
                    ids,
                    RemoteViews(context.packageName, R.layout.widget_disaster_mesh)
                )
                // Full re-render with live data and click handlers.
                val provider = DisasterMeshWidgetProvider()
                ids.forEach { provider.render(context, manager, it) }
            } catch (e: Exception) {
                // A widget problem must never affect the mesh.
                Log.w(TAG, "Widget refresh failed", e)
            }
        }
    }
}
