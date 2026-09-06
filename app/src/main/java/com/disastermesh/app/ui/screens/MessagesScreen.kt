package com.disastermesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.nearby.MeshLogEntry
import com.disastermesh.app.nearby.MeshState
import com.disastermesh.app.ui.components.SectionHeader
import com.disastermesh.app.ui.theme.MeshColors

/**
 * Message log as cards.
 *
 * Every field shown here exists on the real MeshMessage envelope (payload,
 * sender id, hops, TTL). Entries that carry no message — a relay failure, say —
 * fall back to their plain summary line rather than inventing values.
 */
@Composable
fun MessagesScreen(state: MeshState) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "MESSAGES",
            style = MaterialTheme.typography.headlineMedium,
            color = MeshColors.TextPrimary
        )
        SectionHeader("${state.messages.size} events")

        if (state.messages.isEmpty()) {
            Text(
                "Nothing yet. Start the mesh, wait for a node to connect, then send " +
                    "HELLO or an SOS.",
                style = MaterialTheme.typography.bodyMedium,
                color = MeshColors.TextDim
            )
            return@Column
        }

        // Newest first — the thing you care about in an emergency is the latest.
        state.messages.asReversed().forEach { entry ->
            MessageCard(entry)
        }
    }
}

@Composable
private fun MessageCard(entry: MeshLogEntry) {
    val accent = accentFor(entry)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MeshColors.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                labelFor(entry),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }

        // The payload, when this entry actually carried one.
        if (entry.payload != null) {
            Text(
                "\"${entry.displayPayload}\"",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MeshColors.TextPrimary
            )
        } else {
            Text(
                entry.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MeshColors.TextSecondary
            )
        }

        // Only render metadata fields that genuinely exist on this entry.
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            entry.fromNode?.let { Meta("From", it) }
            entry.hops?.let { Meta("Hops", it.toString()) }
            entry.ttl?.let { Meta("TTL", it.toString()) }
        }
    }
}

@Composable
private fun Meta(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MeshColors.TextDim
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MeshColors.TextSecondary
        )
    }
}

private fun labelFor(entry: MeshLogEntry): String = when {
    entry.isSos -> "SOS  ·  EMERGENCY"
    entry.kind == MeshLogEntry.Kind.SENT -> "SENT"
    entry.kind == MeshLogEntry.Kind.RECEIVED -> "RECEIVED"
    entry.kind == MeshLogEntry.Kind.RELAYED -> "RELAYED"
    else -> "DROPPED"
}

private fun accentFor(entry: MeshLogEntry): Color = when {
    entry.isSos -> MeshColors.Red
    entry.kind == MeshLogEntry.Kind.RECEIVED -> MeshColors.Cyan
    entry.kind == MeshLogEntry.Kind.RELAYED -> MeshColors.Amber
    entry.kind == MeshLogEntry.Kind.SENT -> MeshColors.Green
    else -> MeshColors.Border
}
