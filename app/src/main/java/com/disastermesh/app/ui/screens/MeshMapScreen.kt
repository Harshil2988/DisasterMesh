package com.disastermesh.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.nearby.MeshState
import com.disastermesh.app.ui.components.MeshCard
import com.disastermesh.app.ui.components.PulsingDot
import com.disastermesh.app.ui.components.SectionHeader
import com.disastermesh.app.ui.theme.MeshColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Network topology view.
 *
 * Honest about what it can show: this phone knows its OWN direct links, because
 * that is all Nearby Connections tells it. It does not know who its peers are
 * connected to, so this draws one hop out from the centre rather than pretending
 * to map the whole mesh.
 */
@Composable
fun MeshMapScreen(state: MeshState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "MESH",
            style = MaterialTheme.typography.headlineMedium,
            color = MeshColors.TextPrimary
        )
        SectionHeader("Your direct links")

        MeshCard(accent = if (state.isConnected) MeshColors.Cyan else MeshColors.Border) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                TopologyGraph(state)
            }
            Text(
                "This phone sits at the centre. Each line is a live direct connection. " +
                    "Messages travel further than this by hopping through these nodes.",
                style = MaterialTheme.typography.bodySmall,
                color = MeshColors.TextDim
            )
        }

        SectionHeader("Connected nodes (${state.connectedCount})")
        if (state.connectedPeers.isEmpty()) {
            Text(
                if (state.meshActive) {
                    "No direct links yet. Nodes connect automatically once in range."
                } else {
                    "Mesh is stopped. Start it from the Home tab."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MeshColors.TextDim
            )
        } else {
            state.connectedPeers.forEach { peer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MeshColors.Surface, RoundedCornerShape(12.dp))
                        .border(1.dp, MeshColors.Border, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PulsingDot(active = true, color = MeshColors.Green, size = 9)
                    Column {
                        Text(
                            peer.nodeId,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MeshColors.TextPrimary
                        )
                        Text(
                            "${peer.deviceModel} · Connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeshColors.TextDim
                        )
                    }
                }
            }
        }
    }
}

/** Centre node = this phone; each connected peer on a ring around it. */
@Composable
private fun TopologyGraph(state: MeshState) {
    val peers = state.connectedPeers
    val transition = rememberInfiniteTransition(label = "graph")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "graphPulse"
    )

    val centreLabel = state.nodeId.ifBlank { "THIS NODE" }

    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.62f
        val nodeRadius = size.minDimension * 0.075f

        // Links first so nodes are drawn over them.
        peers.forEachIndexed { index, _ ->
            val angle = (2.0 * Math.PI * index / peers.size) - Math.PI / 2
            val target = Offset(
                centre.x + (radius * cos(angle)).toFloat(),
                centre.y + (radius * sin(angle)).toFloat()
            )
            drawLine(
                color = MeshColors.Cyan.copy(alpha = 0.30f + 0.35f * pulse),
                start = centre,
                end = target,
                strokeWidth = size.minDimension * 0.008f
            )
        }

        // Peer nodes.
        peers.forEachIndexed { index, peer ->
            val angle = (2.0 * Math.PI * index / peers.size) - Math.PI / 2
            val target = Offset(
                centre.x + (radius * cos(angle)).toFloat(),
                centre.y + (radius * sin(angle)).toFloat()
            )
            drawCircle(MeshColors.Green.copy(alpha = 0.18f), nodeRadius * 1.5f, target)
            drawCircle(MeshColors.Surface, nodeRadius, target)
            drawCircle(MeshColors.Green, nodeRadius, target, style = Stroke(width = size.minDimension * 0.006f))
            drawLabel(peer.nodeId, target.x, target.y + nodeRadius * 2.1f, size.minDimension * 0.038f)
        }

        // This phone, at the centre.
        drawCircle(MeshColors.Cyan.copy(alpha = 0.12f * pulse + 0.10f), nodeRadius * 2.1f, centre)
        drawCircle(MeshColors.Surface, nodeRadius * 1.35f, centre)
        drawCircle(MeshColors.Cyan, nodeRadius * 1.35f, centre, style = Stroke(width = size.minDimension * 0.008f))
        drawLabel(centreLabel, centre.x, centre.y + nodeRadius * 2.6f, size.minDimension * 0.042f)

        if (peers.isEmpty()) {
            drawLabel(
                "no direct links",
                centre.x,
                centre.y - nodeRadius * 2.4f,
                size.minDimension * 0.036f
            )
        }
    }
}

/**
 * Canvas has no text primitive in Compose, so labels go through the native
 * canvas. Cheap, and avoids pulling in a drawing library.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(
    text: String,
    x: Float,
    y: Float,
    textSize: Float
) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#93AECB")
            this.textSize = textSize
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        drawText(text, x, y, paint)
    }
}
