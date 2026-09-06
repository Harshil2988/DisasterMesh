package com.disastermesh.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.disastermesh.app.nearby.MeshPermissions
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The UI is dark-only, so the system bars get light icons on a transparent ground.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        missingPermissions.value = MeshPermissions.missing(this)

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
                    onSendSos = viewModel::sendSos
                )
            }
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
}
