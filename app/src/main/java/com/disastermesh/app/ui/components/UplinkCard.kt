package com.disastermesh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.ui.theme.Mesh
import com.disastermesh.app.uplink.SatelliteSupport
import com.disastermesh.app.uplink.UplinkEvent
import com.disastermesh.app.uplink.UplinkStatus
import com.disastermesh.app.uplink.UplinkTransport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hybrid uplink status.
 *
 * Every line is read from the platform. Satellite is shown as active only when
 * Android itself reports it — never inferred from the absence of internet.
 */
@Composable
fun UplinkCard(
    status: UplinkStatus,
    modifier: Modifier = Modifier
) {
    val accent = when {
        status.isSatelliteGateway -> Mesh.Signal.Live
        status.isGateway -> Mesh.Signal.Ok
        status.hasExternalPath -> Mesh.Signal.Warning
        else -> Mesh.Text.Tertiary
    }

    MeshPanel(
        modifier = modifier,
        border = if (status.isGateway) accent.copy(alpha = 0.45f) else null
    ) {
        SectionLabel("Hybrid uplink")

        Row(
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiveDot(active = status.hasExternalPath, color = accent, size = 9)
            Text(
                if (status.isGateway) "Gateway active" else "Mesh only",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (status.isGateway) Mesh.Text.Primary else Mesh.Text.Tertiary
            )
        }

        Text(
            status.summary,
            style = MaterialTheme.typography.bodySmall,
            color = Mesh.Text.Secondary
        )

        ReadoutRow(
            "External path",
            if (status.transport == UplinkTransport.NONE) "None" else status.transport.label,
            if (status.hasExternalPath) accent else Mesh.Text.Tertiary
        )
        ReadoutRow(
            "Internet verified",
            if (status.internetValidated) "Yes" else "No",
            if (status.internetValidated) Mesh.Signal.Ok else Mesh.Text.Tertiary
        )
        ReadoutRow("Pending reports", status.pending.toString(), Mesh.Signal.Warning, mono = true)
        ReadoutRow("Delivered", status.delivered.toString(), Mesh.Signal.Ok, mono = true)

        SatelliteRow(status.satellite)

        if (!status.endpointConfigured) {
            Text(
                "No external endpoint is configured, so nothing is transmitted. " +
                    "Reports are held on this device.",
                style = MaterialTheme.typography.labelSmall,
                color = Mesh.Text.Tertiary
            )
        }

        status.lastError?.let { error ->
            Text(
                "Last attempt: $error",
                style = MaterialTheme.typography.labelSmall,
                color = Mesh.Signal.Warning
            )
        }
    }
}

/** Three genuinely distinct satellite states, never collapsed into one. */
@Composable
private fun SatelliteRow(support: SatelliteSupport) {
    val color = when (support) {
        SatelliteSupport.ACTIVE -> Mesh.Signal.Live
        SatelliteSupport.SUPPORTED_INACTIVE -> Mesh.Text.Secondary
        SatelliteSupport.UNSUPPORTED -> Mesh.Text.Tertiary
    }
    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)) {
        ReadoutRow("Satellite", support.label, color)
        Text(
            support.detail,
            style = MaterialTheme.typography.labelSmall,
            color = Mesh.Text.Tertiary
        )
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

/** The real event timeline. Nothing is synthesised for presentation. */
@Composable
fun UplinkTimeline(events: List<UplinkEvent>, modifier: Modifier = Modifier) {
    if (events.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)
    ) {
        SectionLabel("Uplink activity")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Mesh.Surface.Card, RoundedCornerShape(Mesh.Radius.lg))
                .padding(Mesh.Space.lg),
            verticalArrangement = Arrangement.spacedBy(Mesh.Space.sm)
        ) {
            events.asReversed().take(12).forEach { event ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(colorFor(event.kind), CircleShape)
                    )
                    Text(
                        timeFormat.format(Date(event.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = Mesh.Mono,
                        color = Mesh.Text.Tertiary
                    )
                    Text(
                        event.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = Mesh.Text.Secondary
                    )
                }
            }
        }
    }
}

private fun colorFor(kind: UplinkEvent.Kind): Color = when (kind) {
    UplinkEvent.Kind.SUCCESS -> Mesh.Signal.Ok
    UplinkEvent.Kind.FAILURE -> Mesh.Signal.Emergency
    UplinkEvent.Kind.GATEWAY -> Mesh.Signal.Live
    UplinkEvent.Kind.UPLOAD -> Mesh.Signal.Warning
    UplinkEvent.Kind.QUEUED -> Mesh.Signal.Action
    UplinkEvent.Kind.INFO -> Mesh.Text.Tertiary
}
