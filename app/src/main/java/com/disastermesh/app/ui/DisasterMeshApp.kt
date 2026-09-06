package com.disastermesh.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.nearby.MeshState
import com.disastermesh.app.ui.components.MeshCard
import com.disastermesh.app.ui.screens.HomeScreen
import com.disastermesh.app.ui.screens.InfoScreen
import com.disastermesh.app.ui.screens.MeshMapScreen
import com.disastermesh.app.ui.screens.MessagesScreen
import com.disastermesh.app.ui.theme.MeshColors

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("HOME", Icons.Filled.Home),
    MESSAGES("MESSAGES", Icons.Filled.Email),
    MESH("MESH", Icons.Filled.Share),
    INFO("INFO", Icons.Filled.Info)
}

/**
 * App shell: a scrolling content area plus the bottom navigation bar.
 *
 * Pure presentation — it reads the real [MeshState] and calls straight through
 * to the existing ViewModel actions. Tab state is local, so no navigation
 * library is needed.
 */
@Composable
fun DisasterMeshApp(
    state: MeshState,
    permissionsGranted: Boolean,
    missingPermissions: List<String>,
    selectedCategory: MessageCategory,
    onSelectCategory: (MessageCategory) -> Unit,
    onGrantPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onStartMesh: () -> Unit,
    onStopMesh: () -> Unit,
    onSendHello: () -> Unit,
    onSendSos: () -> Unit
) {
    var tab by remember { mutableStateOf(Tab.HOME) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeshColors.Background)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permissions gate everything, so it is shown above every tab.
            if (!permissionsGranted) {
                PermissionCard(missingPermissions, onGrantPermissions, onOpenAppSettings)
            }

            when (tab) {
                Tab.HOME -> HomeScreen(
                    state = state,
                    permissionsGranted = permissionsGranted,
                    selectedCategory = selectedCategory,
                    onSelectCategory = onSelectCategory,
                    onStartMesh = onStartMesh,
                    onStopMesh = onStopMesh,
                    onSendHello = onSendHello,
                    onSendSos = onSendSos
                )

                Tab.MESSAGES -> MessagesScreen(state)
                Tab.MESH -> MeshMapScreen(state)
                Tab.INFO -> InfoScreen()
            }
        }

        BottomBar(current = tab, onSelect = { tab = it })
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(modifier = Modifier.background(MeshColors.Surface)) {
        HorizontalDivider(color = MeshColors.Border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Tab.entries.forEach { entry ->
                val selected = entry == current
                Column(
                    modifier = Modifier
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(entry) }
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = entry.label,
                        tint = if (selected) MeshColors.Cyan else MeshColors.TextDim,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MeshColors.Cyan else MeshColors.TextDim
                    )
                }
            }
        }
    }
}

/**
 * Unchanged in behaviour from the working build — same wording about PRECISE
 * location, same "open app settings" escape hatch.
 */
@Composable
private fun PermissionCard(
    missing: List<String>,
    onGrant: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    MeshCard(accent = MeshColors.Red) {
        Text(
            "PERMISSIONS NEEDED",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MeshColors.Red
        )
        Text(
            "DisasterMesh finds nearby phones using Bluetooth and Wi-Fi. Android treats " +
                "scanning for nearby devices as location access, so it asks for Location too - " +
                "choose PRECISE, because Nearby will not scan with approximate location. " +
                "Your location is never read and nothing is sent to the internet.",
            style = MaterialTheme.typography.bodySmall,
            color = MeshColors.TextSecondary
        )
        if (missing.isNotEmpty()) {
            Text(
                "Still missing: ${missing.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MeshColors.TextDim
            )
        }
        Box {
            Button(onClick = onGrant, shape = RoundedCornerShape(12.dp)) {
                Text("Grant permissions", fontWeight = FontWeight.SemiBold)
            }
        }
        TextButton(onClick = onOpenAppSettings) {
            Text(
                "Dialog not appearing? Open app settings",
                style = MaterialTheme.typography.bodySmall,
                color = MeshColors.TextDim
            )
        }
    }
}
