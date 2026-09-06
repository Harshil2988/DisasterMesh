package com.disastermesh.app

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.disastermesh.app.nearby.MeshPermissions
import com.disastermesh.app.notify.NotificationPreferences
import com.disastermesh.app.ui.DisasterMeshApp
import com.disastermesh.app.ui.MeshViewModel
import com.disastermesh.app.ui.theme.DisasterMeshTheme

/**
 * Single screen of the app. Its only jobs are:
 *  1. ask for the runtime permissions Nearby needs, and
 *  2. wire [DisasterMeshApp] up to [MeshViewModel].
 */
class MainActivity : ComponentActivity() {

    /**
     * Permissions still not granted. Held by the Activity (not by Compose) so that
     * [onResume] can refresh it after the user returns from the system Settings screen.
     */
    private val missingPermissions = mutableStateOf<List<String>>(emptyList())

    /** Bumped when a notification is tapped, so the UI jumps to the Messages tab. */
    private val openMessagesNonce = mutableStateOf(0)

    /** Which message the tapped notification referred to, for highlighting. */
    private val highlightMessageId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The UI is dark-only, so the system bars get light icons on a transparent ground.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        missingPermissions.value = MeshPermissions.missing(this)
        handleNotificationIntent(intent)

        setContent {
            DisasterMeshTheme {
                val viewModel: MeshViewModel = viewModel()
                val state by viewModel.state.collectAsState()
                val missing = missingPermissions.value
                val category by viewModel.selectedCategory

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    // Runs whether the user allowed or denied — we just re-read the truth.
                    missingPermissions.value = MeshPermissions.missing(this)
                }

                // Separate from the Nearby permissions on purpose: notifications are
                // optional, so a refusal here must never block the mesh.
                val notificationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* Allowed or denied, the mesh is unaffected. */ }

                LaunchedEffect(Unit) {
                    maybeAskForNotificationPermission { notificationLauncher.launch(it) }
                }

                DisasterMeshApp(
                    state = state,
                    permissionsGranted = missing.isEmpty(),
                    missingPermissions = missing.map { MeshPermissions.shortName(it) },
                    selectedCategory = category,
                    onSelectCategory = viewModel::selectCategory,
                    onGrantPermissions = {
                        val toRequest = MeshPermissions.missing(this)
                        if (toRequest.isEmpty()) {
                            missingPermissions.value = emptyList()
                        } else {
                            permissionLauncher.launch(toRequest.toTypedArray())
                        }
                    },
                    onOpenAppSettings = ::openAppSettings,
                    onStartMesh = viewModel::startMesh,
                    onStopMesh = viewModel::stopMesh,
                    onSendHello = viewModel::sendHello,
                    onSendMessage = viewModel::sendMessage,
                    onSendSos = viewModel::confirmSos,
                    onSosDialogOpened = viewModel::beginSosFlow,
                    onSendSosWithoutLocation = viewModel::sendSosWithoutLocation,
                    onSosCancelled = viewModel::cancelSosFlow,
                    sosLocation = viewModel.sosLocation.value,
                    messageNotifications = viewModel.messageNotifications.value,
                    sosNotifications = viewModel.sosNotifications.value,
                    onMessageNotificationsChange = viewModel::setMessageNotifications,
                    onSosNotificationsChange = viewModel::setSosNotifications,
                    openMessagesNonce = openMessagesNonce.value,
                    highlightMessageId = highlightMessageId.value
                )
            }
        }
    }

    /** The activity is singleTop, so a tap while it is already running lands here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /** Reads the extras a notification's PendingIntent carries. */
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_MESSAGES, false) != true) return
        highlightMessageId.value = intent.getStringExtra(EXTRA_MESSAGE_ID)
        openMessagesNonce.value += 1
    }

    /**
     * Android 13+ needs POST_NOTIFICATIONS at runtime. Asked once, and entirely
     * separate from the Nearby permissions — declining leaves the mesh untouched.
     */
    private fun maybeAskForNotificationPermission(request: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val prefs = NotificationPreferences(this)
        if (prefs.permissionAsked) return

        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!granted) {
            prefs.permissionAsked = true
            request(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up permissions the user granted in Settings while the app was in the background.
        missingPermissions.value = MeshPermissions.missing(this)
    }

    /**
     * Escape hatch: once a permission has been denied twice, Android silently stops
     * showing the dialog. The only way through is the app's Settings page.
     */
    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    companion object {
        const val EXTRA_OPEN_MESSAGES = "open_messages"
        const val EXTRA_MESSAGE_ID = "message_id"
    }
}
