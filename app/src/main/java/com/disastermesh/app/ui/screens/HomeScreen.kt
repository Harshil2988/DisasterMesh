package com.disastermesh.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disastermesh.app.nearby.MeshState
import com.disastermesh.app.ui.MessageCategory
import com.disastermesh.app.ui.SosLocationState
import com.disastermesh.app.ui.components.CategorySelector
import com.disastermesh.app.ui.components.LiveDot
import com.disastermesh.app.ui.components.MeshControl
import com.disastermesh.app.ui.components.MeshPanel
import com.disastermesh.app.ui.components.NodeTag
import com.disastermesh.app.ui.components.SectionLabel
import com.disastermesh.app.ui.components.SosButton
import com.disastermesh.app.ui.components.StatusChip
import com.disastermesh.app.ui.theme.Mesh

/**
 * The dashboard.
 *
 * Answers four questions in the first two seconds: who am I, is the mesh live,
 * how many nodes can I reach, and how do I raise an alarm. Every value is read
 * from the real [MeshState]; nothing here is hardcoded.
 */
@Composable
fun HomeScreen(
    state: MeshState,
    permissionsGranted: Boolean,
    selectedCategory: MessageCategory,
    sosLocation: SosLocationState,
    onSelectCategory: (MessageCategory) -> Unit,
    onStartMesh: () -> Unit,
    onStopMesh: () -> Unit,
    onSendSos: () -> Unit,
    onSosDialogOpened: () -> Unit,
    onSendSosWithoutLocation: () -> Unit,
    onSosCancelled: () -> Unit,
    onSendHello: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.xl)) {

        Identity(state)

        MeshStatusCard(state)

        MeshControl(
            active = state.meshActive,
            enabled = permissionsGranted,
            onToggle = { if (state.meshActive) onStopMesh() else onStartMesh() }
        )

        // Real error state: the mesh is meant to be on, but a radio did not start.
        AnimatedVisibility(
            visible = state.meshActive && !(state.advertising && state.discovering),
            enter = fadeIn(tween(250)) + expandVertically(),
            exit = fadeOut(tween(200)) + shrinkVertically()
        ) {
            MeshErrorCard(state.status, onOpenAppSettings)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
            SectionLabel("Emergency broadcast")
            CategorySelector(selected = selectedCategory, onSelect = onSelectCategory)
        }

        SosButton(
            enabled = state.isConnected,
            categoryLabel = selectedCategory.label,
            connectedCount = state.connectedCount,
            locationState = sosLocation,
            onOpened = onSosDialogOpened,
            onConfirmed = onSendSos,
            onSendWithoutLocation = onSendSosWithoutLocation,
            onCancelled = onSosCancelled,
            modifier = Modifier.fillMaxWidth()
        )

        // Kept from the working build: a one-tap plain message for verifying a
        // link on real phones without typing. Demoted to a quiet secondary action.
        if (state.isConnected) {
            TextButton(
                onClick = onSendHello,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Mesh.TouchTarget)
            ) {
                Text(
                    "Send test ping",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mesh.Text.Secondary
                )
            }
        }

        if (state.meshActive) {
            NearbyNodes(state)
        }
    }
}

@Composable
private fun Identity(state: MeshState) {
    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.sm)) {
        Text(
            "DisasterMesh",
            style = MaterialTheme.typography.displaySmall,
            color = Mesh.Text.Primary
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NodeTag(state.nodeId.ifBlank { "NODE-····" })
            Text(
                state.deviceModel,
                style = MaterialTheme.typography.bodySmall,
                color = Mesh.Text.Tertiary
            )
        }
    }
}

/**
 * The hero readout. The connected-node count is the largest number on screen
 * because it is the thing that decides whether a message can go anywhere.
 */
@Composable
private fun MeshStatusCard(state: MeshState) {
    val live = state.meshActive
    MeshPanel(
        background = if (live) Mesh.Surface.Card else Mesh.Surface.Sunken,
        border = if (live) Mesh.Signal.LiveDeep.copy(alpha = 0.35f) else Mesh.Line.Subtle,
        padding = Mesh.Space.xl
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiveDot(active = live, color = Mesh.Signal.Ok, size = 9)
            Text(
                if (live) "Mesh active" else "Mesh inactive",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (live) Mesh.Text.Primary else Mesh.Text.Tertiary
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                state.connectedCount.toString(),
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.isConnected) Mesh.Signal.Live else Mesh.Text.Tertiary
            )
            Text(
                if (state.connectedCount == 1) "  connected node" else "  connected nodes",
                style = MaterialTheme.typography.bodyMedium,
                color = Mesh.Text.Secondary,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        // Capability chips carry icon + name + state, never colour alone.
        Row(horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm)) {
            StatusChip(
                icon = Icons.Filled.Share,
                label = "Advertising",
                state = if (state.advertising) "on" else "off",
                active = state.advertising
            )
            StatusChip(
                icon = Icons.Filled.Search,
                label = "Discovering",
                state = if (state.discovering) "on" else "off",
                active = state.discovering
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = Mesh.Text.Tertiary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                "Internet not required",
                style = MaterialTheme.typography.bodySmall,
                color = Mesh.Text.Tertiary
            )
        }
    }
}

/** Shown only when the mesh genuinely failed to start a radio. */
@Composable
private fun MeshErrorCard(status: String, onOpenAppSettings: () -> Unit) {
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
                "Mesh unavailable",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Mesh.Text.Primary
            )
        }
        // The status string is already a plain-language sentence, never a raw stack trace.
        Text(status, style = MaterialTheme.typography.bodyMedium, color = Mesh.Text.Secondary)
        Text(
            "Check Bluetooth, Nearby devices and Location, then start the mesh again.",
            style = MaterialTheme.typography.bodySmall,
            color = Mesh.Text.Tertiary
        )
        TextButton(onClick = onOpenAppSettings) {
            Text("Open app settings", color = Mesh.Signal.Live, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun NearbyNodes(state: MeshState) {
    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
        SectionLabel("Nearby nodes")

        if (state.connectedPeers.isEmpty() && state.pendingPeers.isEmpty()) {
            // Empty state with guidance rather than a blank gap.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Mesh.Surface.Card, RoundedCornerShape(Mesh.Radius.lg))
                    .padding(Mesh.Space.xl),
                verticalArrangement = Arrangement.spacedBy(Mesh.Space.sm)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = Mesh.Signal.Live,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Looking for nodes…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Mesh.Text.Primary
                    )
                }
                Text(
                    "Other phones running DisasterMesh connect on their own. " +
                        "Keep them within about 30 metres — there is nothing to tap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mesh.Text.Tertiary
                )
            }
        } else {
            state.connectedPeers.forEach { peer ->
                PeerRow(peer.nodeId, peer.deviceModel, connected = true)
            }
            state.pendingPeers.forEach { peer ->
                PeerRow(peer.nodeId, "connecting…", connected = false)
            }
        }
    }
}

@Composable
private fun PeerRow(nodeId: String, detail: String, connected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mesh.Surface.Card, RoundedCornerShape(Mesh.Radius.md))
            .padding(horizontal = Mesh.Space.lg, vertical = Mesh.Space.md),
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiveDot(active = connected, color = Mesh.Signal.Ok, size = 8)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                nodeId,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = Mesh.Mono,
                color = Mesh.Text.Primary
            )
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Mesh.Text.Tertiary)
        }
        Text(
            if (connected) "Connected" else "Pending",
            style = MaterialTheme.typography.labelSmall,
            color = if (connected) Mesh.Signal.Ok else Mesh.Signal.Warning
        )
    }
}
