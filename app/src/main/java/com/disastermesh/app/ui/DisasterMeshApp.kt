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
import androidx.compose.material.icons.filled.Place
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
import com.disastermesh.app.command.EmergencyReport
import com.disastermesh.app.command.ReportCounts
import com.disastermesh.app.command.ReportStatus
import com.disastermesh.app.nearby.MeshState
import com.disastermesh.app.map.MapData
import com.disastermesh.app.audio.AudioState
import com.disastermesh.app.audio.AudioTransfer
import com.disastermesh.app.audio.PlaybackState
import com.disastermesh.app.ui.components.AudioPreviewPanel
import com.disastermesh.app.ui.components.RecordingPanel
import com.disastermesh.app.sync.SyncStatus
import com.disastermesh.app.uplink.DeliveryState
import com.disastermesh.app.uplink.UplinkEvent
import com.disastermesh.app.uplink.UplinkStatus
import com.disastermesh.app.ui.screens.CommandScreen
import com.disastermesh.app.ui.screens.MapScreen
import com.disastermesh.app.ui.components.MeshPanel
import com.disastermesh.app.ui.components.MessageComposer
import com.disastermesh.app.ui.screens.HomeScreen
import com.disastermesh.app.ui.screens.InfoScreen
import com.disastermesh.app.ui.screens.MeshMapScreen
import com.disastermesh.app.ui.screens.MessagesScreen
import com.disastermesh.app.ui.theme.Mesh

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    MAP("Map", Icons.Filled.Place),
    COMMAND("Command", Icons.Filled.Warning),
    MESSAGES("Messages", Icons.Filled.Email),
    MESH("Mesh", Icons.Filled.Share),

    /**
     * Reached from the Home header rather than the bottom bar: five destinations
     * is the practical limit for a bottom nav, and Info is a read-once explainer
     * rather than somewhere you return to.
     */
    INFO("Info", Icons.Filled.Info)
}

/** Only these appear in the bottom bar. */
private val BOTTOM_TABS = listOf(Tab.HOME, Tab.MAP, Tab.COMMAND, Tab.MESSAGES, Tab.MESH)

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
    reports: List<EmergencyReport>,
    reportCounts: ReportCounts,
    onSetReportStatus: (String, ReportStatus) -> Unit,
    mapData: MapData,
    mapFocusReportId: String?,
    onMapFocusHandled: () -> Unit,
    onFocusReportOnMap: (String) -> Unit,
    onRequestMyLocation: () -> Unit,
    locationUnavailable: Boolean,
    uplinkStatus: UplinkStatus,
    syncStatus: SyncStatus,
    recording: Boolean,
    recordElapsedMs: Long,
    pendingAudioDurationMs: Long?,
    audioError: String?,
    audioStateOf: (String) -> AudioState,
    audioTransfers: Map<String, AudioTransfer>,
    playback: PlaybackState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onDiscardAudio: () -> Unit,
    onSendAudio: () -> Unit,
    onTogglePreview: () -> Unit,
    onTogglePlay: (String) -> Unit,
    onRetryAudio: (String) -> Unit,
    onDismissAudioError: () -> Unit,
    uplinkEvents: List<UplinkEvent>,
    deliveryStates: Map<String, DeliveryState>,
    messageNotifications: Boolean,
    sosNotifications: Boolean,
    onMessageNotificationsChange: (Boolean) -> Unit,
    onSosNotificationsChange: (Boolean) -> Unit,
    openMessagesNonce: Int = 0,
    openSosNonce: Int = 0,
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

    // The widget's SOS button lands on Home, where the existing confirmation
    // dialog opens itself.
    LaunchedEffect(openSosNonce) {
        if (openSosNonce > 0) tab = Tab.HOME
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
                    counts = reportCounts,
                    onOpenCommand = { tab = Tab.COMMAND },
                    onOpenMap = { tab = Tab.MAP },
                    onOpenInfo = { tab = Tab.INFO },
                    mapData = mapData,
                    uplinkStatus = uplinkStatus,
                    onOpenAppSettings = onOpenAppSettings,
                    openSosNonce = openSosNonce
                )

                Tab.COMMAND -> CommandScreen(
                    state = state,
                    reports = reports,
                    counts = reportCounts,
                    onSetStatus = onSetReportStatus,
                    uplinkStatus = uplinkStatus,
                    uplinkEvents = uplinkEvents,
                    deliveryStates = deliveryStates,
                    onViewOnMap = { reportId ->
                        onFocusReportOnMap(reportId)
                        tab = Tab.MAP
                    }
                )

                Tab.MAP -> MapScreen(
                    state = state,
                    data = mapData,
                    focusReportId = mapFocusReportId,
                    onFocusHandled = onMapFocusHandled,
                    onOpenReport = { tab = Tab.COMMAND },
                    onRequestMyLocation = onRequestMyLocation,
                    locationUnavailable = locationUnavailable
                )

                Tab.MESSAGES -> MessagesScreen(
                    state = state,
                    highlightMessageId = highlightMessageId,
                    audioStateOf = audioStateOf,
                    audioTransfers = audioTransfers,
                    playback = playback,
                    onTogglePlay = onTogglePlay,
                    onRetryAudio = onRetryAudio
                )
                Tab.MESH -> MeshMapScreen(state, syncStatus)
                Tab.INFO -> InfoScreen(
                    messageNotifications = messageNotifications,
                    sosNotifications = sosNotifications,
                    onMessageNotificationsChange = onMessageNotificationsChange,
                    onSosNotificationsChange = onSosNotificationsChange
                )
            }
        }

        if (tab == Tab.MESSAGES) {
            audioError?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Mesh.Surface.Card)
                        .padding(horizontal = Mesh.Space.lg, vertical = Mesh.Space.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Mesh.Signal.Warning,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissAudioError) {
                        Text("Dismiss", color = Mesh.Text.Secondary)
                    }
                }
            }

            when {
                recording -> RecordingPanel(
                    elapsedMs = recordElapsedMs,
                    onCancel = onCancelRecording,
                    onStop = onStopRecording
                )

                pendingAudioDurationMs != null -> AudioPreviewPanel(
                    durationMs = pendingAudioDurationMs,
                    isPlaying = playback.playing,
                    onTogglePlay = onTogglePreview,
                    onDelete = onDiscardAudio,
                    onSend = onSendAudio,
                    canSend = state.isConnected
                )

                else -> MessageComposer(
                    text = draft,
                    onTextChange = { draft = it },
                    onSend = {
                        onSendMessage(draft)
                        draft = ""
                    },
                    connected = state.isConnected,
                    onStartRecording = onStartRecording
                )
            }
        }

        BottomBar(current = tab, onSelect = { tab = it })
    }
}

/**
 * Bottom navigation. Four destinations, each a full 48dp target with an
 * animated indicator above the active item.
 */
/**
 * Fixed bottom navigation.
 *
 * Each destination takes an equal share of the width rather than being spaced
 * evenly by content, so "Messages" can never crowd its neighbours on a narrow
 * device. Selection is carried by three quiet signals at once — a rule, a tint
 * and a weight change — instead of one loud one.
 */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(modifier = Modifier.background(Mesh.Surface.Card)) {
        HorizontalDivider(color = Mesh.Line.Subtle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = Mesh.Space.sm, bottom = Mesh.Space.md),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            BOTTOM_TABS.forEach { entry ->
                NavItem(
                    entry = entry,
                    selected = entry == current,
                    onClick = { onSelect(entry) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    entry: Tab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint by animateColorAsState(
        targetValue = if (selected) Mesh.Signal.Live else Mesh.Text.Tertiary,
        animationSpec = tween(200),
        label = "navTint"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 18.dp else 0.dp,
        animationSpec = tween(220),
        label = "navIndicator"
    )

    Column(
        modifier = modifier
            .heightIn(min = Mesh.TouchTarget)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = Mesh.Space.xs),
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
            modifier = Modifier.size(22.dp)
        )
        Text(
            entry.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            maxLines = 1
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
