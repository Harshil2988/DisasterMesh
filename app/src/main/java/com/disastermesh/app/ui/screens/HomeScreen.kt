package com.disastermesh.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.command.ReportCounts
import com.disastermesh.app.map.MapData
import com.disastermesh.app.nearby.MeshState
import com.disastermesh.app.ui.MessageCategory
import com.disastermesh.app.ui.SosLocationState
import com.disastermesh.app.ui.components.MeshAction
import com.disastermesh.app.ui.components.MeshChip
import com.disastermesh.app.ui.components.MeshErrorNotice
import com.disastermesh.app.ui.components.MeshIntent
import com.disastermesh.app.ui.components.MeshMetricLarge
import com.disastermesh.app.ui.components.MeshPreviewRow
import com.disastermesh.app.ui.components.MeshSectionHeader
import com.disastermesh.app.ui.components.MeshStatus
import com.disastermesh.app.ui.components.MeshStatusKind
import com.disastermesh.app.ui.components.SosButton
import com.disastermesh.app.ui.theme.Mesh
import com.disastermesh.app.uplink.UplinkStatus

/**
 * The control centre.
 *
 * Answers exactly three questions, in this order: am I connected, is anything
 * wrong, and what can I do right now. Everything else on this screen is a
 * one-line gateway into the screen that actually owns that subject.
 *
 * The previous version stacked ten full-width panels of identical weight, three
 * of which were miniature copies of other screens. Nothing was wrong with any
 * one of them; the flatness was the problem. Weight is now spent deliberately.
 *
 * Every value is read from the real [MeshState], [ReportCounts], [MapData] and
 * [UplinkStatus] — nothing here is invented, and an unavailable value says so.
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
    counts: ReportCounts,
    onOpenCommand: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenInfo: () -> Unit,
    mapData: MapData,
    uplinkStatus: UplinkStatus,
    onOpenAppSettings: () -> Unit,
    openSosNonce: Int = 0
) {
    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.xxl)) {

        Identity(state, onOpenInfo)

        NetworkBanner(
            state = state,
            permissionsGranted = permissionsGranted,
            onStartMesh = onStartMesh,
            onStopMesh = onStopMesh
        )

        // A genuine failure state: the mesh is meant to be on but a radio never
        // started. Shown only when true, never as decoration.
        AnimatedVisibility(
            visible = state.meshActive && !(state.advertising && state.discovering),
            enter = fadeIn(tween(220)) + expandVertically(tween(220)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
        ) {
            MeshErrorNotice(
                title = "Radio did not start",
                body = state.status.ifBlank {
                    "The mesh is on but a radio was refused. Open app settings and allow Nearby devices."
                }
            ) {
                TextButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.heightIn(min = Mesh.TouchTarget)
                ) {
                    Text(
                        "App settings",
                        color = Mesh.Signal.Warning,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ---- Primary action ------------------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
            MeshSectionHeader("Emergency broadcast")
            CategoryRow(selected = selectedCategory, onSelect = onSelectCategory)
            SosButton(
                enabled = state.isConnected,
                categoryLabel = selectedCategory.label,
                connectedCount = state.connectedCount,
                locationState = sosLocation,
                onOpened = onSosDialogOpened,
                onConfirmed = onSendSos,
                onSendWithoutLocation = onSendSosWithoutLocation,
                onCancelled = onSosCancelled,
                externalOpenNonce = openSosNonce,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ---- Gateways --------------------------------------------------------
        // One line each. Home says what is happening and where to go; it does
        // not try to be a small copy of four other screens.
        Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
            MeshSectionHeader("Overview")

            MeshPreviewRow("Emergency status", onOpenCommand) {
                MeshMetricLarge(
                    value = counts.critical.toString(),
                    label = "Critical",
                    valueColor = if (counts.critical > 0) Mesh.Signal.Emergency else Mesh.Text.Primary
                )
                MeshMetricLarge(
                    value = counts.medical.toString(),
                    label = "Medical",
                    valueColor = if (counts.medical > 0) Mesh.Signal.Medical else Mesh.Text.Primary
                )
                MeshMetricLarge(
                    value = counts.warning.toString(),
                    label = "Warning",
                    valueColor = if (counts.warning > 0) Mesh.Signal.Warning else Mesh.Text.Primary
                )
            }

            MeshPreviewRow("Disaster map", onOpenMap) {
                MeshMetricLarge(mapData.reports.size.toString(), "Located reports")
                MeshMetricLarge(mapData.nodes.size.toString(), "Mapped nodes")
            }

            UplinkLine(uplinkStatus)
        }

        // Kept from the working build: a one-tap plain message for confirming a
        // link on real phones without typing. Quiet, and last.
        if (state.isConnected) {
            TextButton(
                onClick = onSendHello,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Mesh.TouchTarget)
            ) {
                Text(
                    "Send test ping",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mesh.Text.Tertiary
                )
            }
        }
    }
}

/** Product name, node identity, and the way into the explainer. */
@Composable
private fun Identity(state: MeshState, onOpenInfo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.sm)) {
            Text(
                "DisasterMesh",
                style = MaterialTheme.typography.displaySmall,
                color = Mesh.Text.Primary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    state.nodeId.ifBlank { "NODE-••••" },
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = Mesh.Mono,
                    color = Mesh.Signal.Live,
                    modifier = Modifier
                        .background(Mesh.Surface.Raised, RoundedCornerShape(Mesh.Radius.sm))
                        .padding(horizontal = Mesh.Space.md, vertical = Mesh.Space.xs)
                )
                if (state.deviceModel.isNotBlank()) {
                    Text(
                        state.deviceModel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Mesh.Text.Tertiary
                    )
                }
            }
        }
        Icon(
            Icons.Filled.Info,
            contentDescription = "About DisasterMesh",
            tint = Mesh.Text.Tertiary,
            modifier = Modifier
                .size(Mesh.TouchTarget)
                .clickable(onClick = onOpenInfo)
                .padding(Mesh.Space.md)
        )
    }
}

/**
 * The network answer, compact.
 *
 * One status word, one figure, one control. This used to be a tall panel with
 * four readouts and a separate full-width start button below it — the same
 * information at three times the height.
 */
@Composable
private fun NetworkBanner(
    state: MeshState,
    permissionsGranted: Boolean,
    onStartMesh: () -> Unit,
    onStopMesh: () -> Unit
) {
    val kind = when {
        state.meshActive && state.isConnected -> MeshStatusKind.ACTIVE
        state.meshActive && (state.advertising || state.discovering) -> MeshStatusKind.STARTING
        state.meshActive -> MeshStatusKind.STARTING
        else -> MeshStatusKind.OFFLINE
    }
    val count by animateFloatAsState(
        state.connectedCount.toFloat(),
        tween(320),
        label = "peerCount"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mesh.Surface.Card, RoundedCornerShape(Mesh.Radius.lg))
            .padding(Mesh.Space.xl),
        verticalArrangement = Arrangement.spacedBy(Mesh.Space.lg)
    ) {
        MeshStatus(kind)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    count.toInt().toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (state.isConnected) Mesh.Text.Primary else Mesh.Text.Tertiary
                )
                Text(
                    if (state.connectedCount == 1) "device\nconnected" else "devices\nconnected",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mesh.Text.Secondary,
                    modifier = Modifier.padding(bottom = Mesh.Space.sm)
                )
            }

            MeshAction(
                label = if (state.meshActive) "Stop" else "Start mesh",
                onClick = { if (state.meshActive) onStopMesh() else onStartMesh() },
                intent = if (state.meshActive) MeshIntent.SECONDARY else MeshIntent.PRIMARY,
                enabled = permissionsGranted
            )
        }

        if (state.pendingPeers.isNotEmpty()) {
            Text(
                "${state.pendingPeers.size} nearby, connecting…",
                style = MaterialTheme.typography.bodySmall,
                color = Mesh.Signal.Live
            )
        }
    }
}

/** What the SOS will be tagged as. Horizontally scrollable so nothing clips. */
@Composable
private fun CategoryRow(
    selected: MessageCategory,
    onSelect: (MessageCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm)
    ) {
        MessageCategory.entries.forEach { category ->
            MeshChip(
                label = category.label,
                selected = category == selected,
                onClick = { onSelect(category) },
                accent = accentFor(category),
                leadingDot = accentFor(category)
            )
        }
    }
}

/**
 * Hybrid uplink, demoted to one honest line.
 *
 * It used to occupy a nine-row panel on Home AND an identical one on Command.
 * On a mesh-first product the interesting fact is almost always "mesh only",
 * which is one line long.
 */
@Composable
private fun UplinkLine(status: UplinkStatus) {
    val shape = RoundedCornerShape(Mesh.Radius.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mesh.Surface.Card, shape)
            .border(BorderStroke(1.dp, Mesh.Line.Subtle), shape)
            .padding(horizontal = Mesh.Space.lg, vertical = Mesh.Space.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)) {
            Text(
                "UPLINK",
                style = MaterialTheme.typography.labelSmall,
                color = Mesh.Text.Tertiary
            )
            Text(
                if (status.hasExternalPath) status.transport.label else "Mesh only",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (status.hasExternalPath) Mesh.Signal.Ok else Mesh.Text.Primary
            )
        }
        if (status.pending > 0) {
            Text(
                "${status.pending} pending",
                style = MaterialTheme.typography.bodySmall,
                color = Mesh.Signal.Warning
            )
        } else {
            Text(
                "No external path",
                style = MaterialTheme.typography.bodySmall,
                color = Mesh.Text.Tertiary
            )
        }
    }
}

/** Category accent, matching the single mapping used by the report screens. */
private fun accentFor(category: MessageCategory): Color = when (category) {
    MessageCategory.SAFE -> Mesh.Signal.Ok
    MessageCategory.MEDICAL -> Mesh.Signal.Medical
    MessageCategory.WARNING -> Mesh.Signal.Warning
    MessageCategory.SUPPLY -> Mesh.Signal.Supply
}
