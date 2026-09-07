package com.disastermesh.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.nearby.MeshLogEntry
import com.disastermesh.app.nearby.MeshState
import com.disastermesh.app.sync.SyncStatus
import com.disastermesh.app.ui.components.LiveDot
import com.disastermesh.app.ui.components.MeshPanel
import com.disastermesh.app.ui.components.NodeTag
import com.disastermesh.app.ui.components.ReadoutRow
import com.disastermesh.app.ui.components.SectionLabel
import com.disastermesh.app.ui.theme.Mesh
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Network view.
 *
 * Honest about its own limits: a node knows only its OWN direct links, because
 * that is all Nearby Connections reports. It therefore draws one hop out from
 * the centre and never invents relationships between two remote nodes.
 */
@Composable
fun MeshMapScreen(state: MeshState, syncStatus: SyncStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.xl)) {

        Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)) {
            Text(
                "Mesh",
                style = MaterialTheme.typography.displaySmall,
                color = Mesh.Text.Primary
            )
            Text(
                "How this node is connected",
                style = MaterialTheme.typography.bodySmall,
                color = Mesh.Text.Tertiary
            )
        }

        MeshPanel(
            background = Mesh.Surface.Card,
            border = if (state.isConnected) Mesh.Signal.LiveDeep.copy(alpha = 0.3f) else null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                TopologyGraph(state)
            }
            // One line, not three. The visualisation is the explanation; prose
            // that restates it just competes with it for attention.
            Text(
                "Each connected node forwards what it receives to its own neighbours.",
                style = MaterialTheme.typography.bodySmall,
                color = Mesh.Text.Tertiary
            )
        }

        MeshHealth(state)

        SyncSection(syncStatus)

        Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
            SectionLabel("Connected nodes (${state.connectedCount})")
            if (state.connectedPeers.isEmpty()) {
                Text(
                    if (state.meshActive) {
                        "No direct links yet. Nodes connect automatically once in range."
                    } else {
                        "Mesh is stopped. Start it from the Home tab."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mesh.Text.Tertiary
                )
            } else {
                state.connectedPeers.forEach { peer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Mesh.Surface.Card, RoundedCornerShape(Mesh.Radius.md))
                            .padding(horizontal = Mesh.Space.lg, vertical = Mesh.Space.md),
                        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiveDot(active = true, color = Mesh.Signal.Ok, size = 8)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                peer.nodeId,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = Mesh.Mono,
                                fontWeight = FontWeight.SemiBold,
                                color = Mesh.Text.Primary
                            )
                            Text(
                                peer.deviceModel,
                                style = MaterialTheme.typography.bodySmall,
                                color = Mesh.Text.Tertiary
                            )
                        }
                        Text(
                            "Connected",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mesh.Signal.Ok
                        )
                    }
                }
            }
        }
    }
}

/**
 * Session activity, counted from the real event log.
 *
 * These are genuine counts of events this node handled, not invented analytics.
 * The log keeps the most recent entries only, which the caption states plainly.
 */
@Composable
private fun MeshHealth(state: MeshState) {
    val received = remember(state.messages) {
        state.messages.count { it.kind == MeshLogEntry.Kind.RECEIVED }
    }
    val relayed = remember(state.messages) {
        state.messages.count { it.kind == MeshLogEntry.Kind.RELAYED }
    }
    val sent = remember(state.messages) {
        state.messages.count { it.kind == MeshLogEntry.Kind.SENT }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
        SectionLabel("Session activity")
        MeshPanel {
            ReadoutRow("Connected nodes", state.connectedCount.toString(), Mesh.Signal.Live, mono = true)
            ReadoutRow("Messages sent", sent.toString(), Mesh.Text.Primary, mono = true)
            ReadoutRow("Messages received", received.toString(), Mesh.Text.Primary, mono = true)
            ReadoutRow("Messages relayed", relayed.toString(), Mesh.Signal.Warning, mono = true)
            Text(
                "Counted from this node's recent event log, which keeps the latest 100 events.",
                style = MaterialTheme.typography.labelSmall,
                color = Mesh.Text.Tertiary
            )
        }
    }
}

/**
 * Store-carry-forward status. Every figure is a real counter maintained by the
 * sync layer — nothing here is estimated.
 */
@Composable
private fun SyncSection(status: SyncStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
        SectionLabel("Message sync")
        MeshPanel {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveDot(
                    active = status.activeSyncs > 0,
                    color = if (status.activeSyncs > 0) Mesh.Signal.Warning else Mesh.Signal.Ok,
                    size = 8
                )
                Text(
                    when {
                        status.activeSyncs > 0 -> "Synchronising…"
                        status.peers.isNotEmpty() -> "Sync complete"
                        else -> "Idle"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Mesh.Text.Primary
                )
            }

            ReadoutRow("Messages carried", status.carrying.toString(), Mesh.Signal.Live, mono = true)
            ReadoutRow("Received via sync", status.totalReceived.toString(), Mesh.Text.Primary, mono = true)
            ReadoutRow("Shared with peers", status.totalSent.toString(), Mesh.Text.Primary, mono = true)

            status.lastEvent?.let { event ->
                Text(event, style = MaterialTheme.typography.labelSmall, color = Mesh.Signal.Ok)
            }

            if (status.peers.isNotEmpty()) {
                status.peers.values.sortedBy { it.nodeId }.forEach { peer ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            peer.nodeId,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = Mesh.Mono,
                            color = Mesh.Text.Secondary
                        )
                        Text(
                            if (peer.syncing) "syncing…" else "+${peer.received} / -${peer.sent}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = Mesh.Mono,
                            color = if (peer.syncing) Mesh.Signal.Warning else Mesh.Text.Tertiary
                        )
                    }
                }
            }

            Text(
                "This node carries messages for the mesh and offers them to any node " +
                    "it meets that does not have them yet.",
                style = MaterialTheme.typography.labelSmall,
                color = Mesh.Text.Tertiary
            )
        }
    }
}

/** Centre node = this phone; each connected peer on a ring around it. */
@Composable
private fun TopologyGraph(state: MeshState) {
    val peers = state.connectedPeers
    val transition = rememberInfiniteTransition(label = "graph")
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "flow"
    )

    val centreLabel = state.nodeId.ifBlank { "THIS NODE" }
    val liveArgb = Mesh.Signal.Live.toArgb()
    val textArgb = Mesh.Text.Secondary.toArgb()

    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.60f
        val nodeRadius = size.minDimension * 0.068f

        // Links first so nodes paint over them.
        peers.forEachIndexed { index, _ ->
            val target = ringPosition(centre, radius, index, peers.size)
            drawLine(
                color = Mesh.Signal.Live.copy(alpha = 0.22f + 0.30f * flow),
                start = centre,
                end = target,
                strokeWidth = size.minDimension * 0.007f
            )
        }

        peers.forEachIndexed { index, peer ->
            val target = ringPosition(centre, radius, index, peers.size)
            drawCircle(Mesh.Signal.Ok.copy(alpha = 0.14f), nodeRadius * 1.6f, target)
            drawCircle(Mesh.Surface.Raised, nodeRadius, target)
            drawCircle(
                Mesh.Signal.Ok, nodeRadius, target,
                style = Stroke(width = size.minDimension * 0.005f)
            )
            drawLabel(
                peer.nodeId, target.x, target.y + nodeRadius * 2.2f,
                size.minDimension * 0.035f, textArgb
            )
        }

        // This phone, at the centre, visibly the anchor of its own view.
        drawCircle(Mesh.Signal.Live.copy(alpha = 0.08f + 0.06f * flow), nodeRadius * 2.4f, centre)
        drawCircle(Mesh.Surface.Raised, nodeRadius * 1.4f, centre)
        drawCircle(
            Mesh.Signal.Live, nodeRadius * 1.4f, centre,
            style = Stroke(width = size.minDimension * 0.008f)
        )
        drawLabel(
            centreLabel, centre.x, centre.y + nodeRadius * 2.9f,
            size.minDimension * 0.040f, liveArgb
        )

        if (peers.isEmpty()) {
            drawLabel(
                "no direct links yet", centre.x, centre.y - nodeRadius * 2.6f,
                size.minDimension * 0.034f, textArgb
            )
        }
    }
}

/** Evenly spaced around the ring, starting at the top. */
private fun ringPosition(centre: Offset, radius: Float, index: Int, count: Int): Offset {
    val angle = (2.0 * Math.PI * index / count) - Math.PI / 2
    return Offset(
        centre.x + (radius * cos(angle)).toFloat(),
        centre.y + (radius * sin(angle)).toFloat()
    )
}

/** Compose Canvas has no text primitive, so labels go via the native canvas. */
private fun DrawScope.drawLabel(text: String, x: Float, y: Float, textSize: Float, argb: Int) {
    drawContext.canvas.nativeCanvas.drawText(
        text,
        x,
        y,
        android.graphics.Paint().apply {
            color = argb
            this.textSize = textSize
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    )
}
