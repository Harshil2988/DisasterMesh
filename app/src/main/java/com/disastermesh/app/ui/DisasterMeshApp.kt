package com.disastermesh.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.nearby.MeshState
import com.disastermesh.app.ui.components.MeshPanel
import com.disastermesh.app.ui.components.MessageComposer
import com.disastermesh.app.ui.screens.HomeScreen
import com.disastermesh.app.ui.screens.InfoScreen
import com.disastermesh.app.ui.screens.MeshMapScreen
import com.disastermesh.app.ui.screens.MessagesScreen
import com.disastermesh.app.ui.theme.Mesh

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    MESSAGES("Messages", Icons.Filled.Email),
    MESH("Mesh", Icons.Filled.Share),
    INFO("Info", Icons.Filled.Info)
}

/**
 * App shell: a scrolling content area, a docked composer on Messages, and the
 * bottom navigation.
 *
 * Pure presentation — it reads the real [MeshState] and calls straight through
 * to existing ViewModel actions. Tab state is local, so no navigation library.
 */
@Composable
fun DisasterMeshApp(
    state: MeshState,
    permissionsGranted: Boolean,
    missingPermissions: List<String>,
    selectedCategory: MessageCategory,
    sosLocation: SosLocationState,
    onSelectCategory: (MessageCategory) -> Unit,
    onGrantPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onStartMesh: () -> Unit,
    onStopMesh: () -> Unit,
    onSendSos: () -> Unit,
    onSosDialogOpened: () -> Unit,
    onSendSosWithoutLocation: () -> Unit,
    onSosCancelled: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendHello: () -> Unit,
    messageNotifications: Boolean,
    sosNotifications: Boolean,
    onMessageNotificationsChange: (Boolean) -> Unit,
    onSosNotificationsChange: (Boolean) -> Unit,
    openMessagesNonce: Int = 0,
    highlightMessageId: String? = null
) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var draft by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // A tapped notification switches to Messages. Keyed on the nonce so tapping
    // the same notification twice still works.
    LaunchedEffect(openMessagesNonce) {
        if (openMessagesNonce > 0) tab = Tab.MESSAGES
    }

    // Keep the newest message in view while reading the thread.
    LaunchedEffect(state.messages.size, tab) {
        if (tab == Tab.MESSAGES) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Mesh.Surface.Backdrop)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(scrollState)
                .padding(horizontal = Mesh.Space.xl, vertical = Mesh.Space.xl),
            verticalArrangement = Arrangement.spacedBy(Mesh.Space.xl)
        ) {
            // Permissions gate everything, so this sits above every tab.
            if (!permissionsGranted) {
                PermissionCard(missingPermissions, onGrantPermissions, onOpenAppSettings)
            }

            when (tab) {
                Tab.HOME -> HomeScreen(
                    state = state,
                    permissionsGranted = permissionsGranted,
                    selectedCategory = selectedCategory,
                    sosLocation = sosLocation,
                    onSelectCategory = onSelectCategory,
                    onStartMesh = onStartMesh,
                    onStopMesh = onStopMesh,
                    onSendSos = onSendSos,
                    onSosDialogOpened = onSosDialogOpened,
                    onSendSosWithoutLocation = onSendSosWithoutLocation,
                    onSosCancelled = onSosCancelled,
                    onSendHello = onSendHello,
                    onOpenAppSettings = onOpenAppSettings
                )

                Tab.MESSAGES -> MessagesScreen(state, highlightMessageId)
                Tab.MESH -> MeshMapScreen(state)
                Tab.INFO -> InfoScreen(
                    messageNotifications = messageNotifications,
                    sosNotifications = sosNotifications,
                    onMessageNotificationsChange = onMessageNotificationsChange,
                    onSosNotificationsChange = onSosNotificationsChange
                )
            }
        }

        if (tab == Tab.MESSAGES) {
            MessageComposer(
                text = draft,
                onTextChange = { draft = it },
                onSend = {
                    onSendMessage(draft)
                    draft = ""
                },
                connected = state.isConnected
            )
        }

        BottomBar(current = tab, onSelect = { tab = it })
    }
}

/**
 * Bottom navigation. Four destinations, each a full 48dp target with an
 * animated indicator above the active item.
 */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(modifier = Modifier.background(Mesh.Surface.Card)) {
        HorizontalDivider(color = Mesh.Line.Subtle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = Mesh.Space.sm),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Tab.entries.forEach { entry ->
                NavItem(
                    entry = entry,
                    selected = entry == current,
                    onClick = { onSelect(entry) }
                )
            }
        }
    }
}

@Composable
private fun NavItem(entry: Tab, selected: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(
        targetValue = if (selected) Mesh.Signal.Live else Mesh.Text.Tertiary,
        animationSpec = tween(200),
        label = "navTint"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 20.dp else 0.dp,
        animationSpec = tween(220),
        label = "navIndicator"
    )

    Column(
        modifier = Modifier
            .heightIn(min = Mesh.TouchTarget)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Mesh.Space.md, vertical = Mesh.Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)
    ) {
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(indicatorWidth)
                .background(Mesh.Signal.Live, RoundedCornerShape(1.dp))
        )
        Icon(
            imageVector = entry.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
        Text(
            entry.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = tint
        )
    }
}

/**
 * Permission gate. Same wording as the working build about PRECISE location and
 * the same escape hatch when Android stops showing the dialog.
 */
@Composable
private fun PermissionCard(
    missing: List<String>,
    onGrant: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    MeshPanel(
        background = Mesh.Signal.EmergencyDeep.copy(alpha = 0.16f),
        border = Mesh.Signal.Emergency.copy(alpha = 0.5f)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = Mesh.Signal.Emergency,
                modifier = Modifier.size(18.dp)
            )
            Text(
                "Permissions needed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Mesh.Text.Primary
            )
        }
        Text(
            "DisasterMesh finds nearby phones using Bluetooth and Wi-Fi. Android treats " +
                "scanning for nearby devices as location access, so it asks for Location " +
                "too — choose Precise, because Nearby will not scan with approximate " +
                "location. Your location is never read and nothing is sent to the internet.",
            style = MaterialTheme.typography.bodyMedium,
            color = Mesh.Text.Secondary
        )
        if (missing.isNotEmpty()) {
            Text(
                "Still missing: ${missing.joinToString()}",
                style = MaterialTheme.typography.labelSmall,
                color = Mesh.Text.Tertiary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm)) {
            TextButton(
                onClick = onGrant,
                modifier = Modifier.heightIn(min = Mesh.TouchTarget)
            ) {
                Text(
                    "Grant permissions",
                    color = Mesh.Signal.Live,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(
                onClick = onOpenAppSettings,
                modifier = Modifier.heightIn(min = Mesh.TouchTarget)
            ) {
                Text("App settings", color = Mesh.Text.Secondary)
            }
        }
    }
}
