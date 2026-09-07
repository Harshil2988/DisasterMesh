package com.disastermesh.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.audio.AudioMessage
import com.disastermesh.app.audio.AudioState
import com.disastermesh.app.audio.AudioTransfer
import com.disastermesh.app.audio.PlaybackState
import com.disastermesh.app.ui.components.AudioBubbleContent
import com.disastermesh.app.nearby.MeshLogEntry
import com.disastermesh.app.nearby.MeshState
import com.disastermesh.app.ui.components.LiveDot
import com.disastermesh.app.ui.components.SectionLabel
import com.disastermesh.app.ui.components.formatCoordinates
import com.disastermesh.app.ui.components.MeshEmpty
import com.disastermesh.app.ui.components.MeshStatus
import com.disastermesh.app.ui.components.MeshStatusKind
import com.disastermesh.app.ui.theme.Mesh
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline messaging.
 *
 * Conversation entries are bubbles; relay and drop events stay as quiet system
 * lines, because watching a message get relayed is the clearest evidence that
 * the mesh is doing its job. Every field shown is real.
 */
@Composable
fun MessagesScreen(
    state: MeshState,
    highlightMessageId: String? = null,
    audioStateOf: (String) -> AudioState = { AudioState.PENDING },
    audioTransfers: Map<String, AudioTransfer> = emptyMap(),
    playback: PlaybackState = PlaybackState(),
    onTogglePlay: (String) -> Unit = {},
    onRetryAudio: (String) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.lg)) {

        Text(
            "Messages",
            style = MaterialTheme.typography.displaySmall,
            color = Mesh.Text.Primary
        )

        MeshStrip(state)

        if (state.messages.isEmpty()) {
            EmptyMessages(state.meshActive)
            return@Column
        }

        // Chronological, so the newest sits nearest the composer.
        state.messages.forEach { entry ->
            val highlighted = highlightMessageId != null && entry.messageId == highlightMessageId
            when (entry.kind) {
                MeshLogEntry.Kind.SENT -> Bubble(
                    entry, fromMe = true, highlighted = highlighted,
                    audioStateOf = audioStateOf, audioTransfers = audioTransfers,
                    playback = playback, onTogglePlay = onTogglePlay, onRetryAudio = onRetryAudio
                )
                MeshLogEntry.Kind.RECEIVED -> Bubble(
                    entry, fromMe = false, highlighted = highlighted,
                    audioStateOf = audioStateOf, audioTransfers = audioTransfers,
                    playback = playback, onTogglePlay = onTogglePlay, onRetryAudio = onRetryAudio
                )
                else -> SystemLine(entry)
            }
        }
    }
}

/** Compact live-status strip so the offline nature is always visible. */
@Composable
private fun MeshStrip(state: MeshState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mesh.Surface.Card, RoundedCornerShape(Mesh.Radius.md))
            .padding(horizontal = Mesh.Space.lg, vertical = Mesh.Space.md),
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Same status vocabulary as every other screen. The "internet not
        // required" line lived here, on Home and in the widget; saying it three
        // times made it read as reassurance rather than as a fact.
        MeshStatus(
            if (state.meshActive && state.isConnected) MeshStatusKind.ACTIVE
            else if (state.meshActive) MeshStatusKind.STARTING
            else MeshStatusKind.OFFLINE,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (state.connectedCount == 1) "1 node" else "${state.connectedCount} nodes",
            style = MaterialTheme.typography.bodySmall,
            color = Mesh.Text.Secondary
        )
    }
}

/**
 * Compact by design.
 *
 * This was a full-width card roughly a third of the viewport tall carrying one
 * sentence. An empty inbox is information, not furniture — it gets the room one
 * sentence deserves, and the composer stays the thing in reach.
 */
@Composable
private fun EmptyMessages(meshActive: Boolean) {
    MeshEmpty(
        icon = Icons.Filled.Email,
        title = "No messages yet",
        body = if (meshActive) {
            "Your node is live. Anything a nearby node sends appears here, including relayed messages."
        } else {
            "Start the mesh on the Home tab to begin receiving."
        }
    )
}

@Composable
private fun Bubble(
    entry: MeshLogEntry,
    fromMe: Boolean,
    highlighted: Boolean,
    audioStateOf: (String) -> AudioState,
    audioTransfers: Map<String, AudioTransfer>,
    playback: PlaybackState,
    onTogglePlay: (String) -> Unit,
    onRetryAudio: (String) -> Unit
) {
    val audio = AudioMessage.decode(entry.payload)
    val sos = entry.isSos
    val accent = when {
        sos -> Mesh.Signal.Emergency
        fromMe -> Mesh.Signal.Live
        else -> Mesh.Line.Subtle
    }
    val container = when {
        sos -> Mesh.Signal.EmergencyDeep.copy(alpha = 0.16f)
        fromMe -> Mesh.Signal.LiveDeep.copy(alpha = 0.14f)
        else -> Mesh.Surface.Card
    }
    val shape = RoundedCornerShape(Mesh.Radius.lg)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .background(container, shape)
                .then(
                    if (sos || highlighted) Modifier.border(
                        if (highlighted) 2.dp else 1.dp, accent, shape
                    ) else Modifier
                )
                .padding(Mesh.Space.lg),
            verticalArrangement = Arrangement.spacedBy(Mesh.Space.sm)
        ) {
            if (sos) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Mesh.Signal.Emergency,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Emergency SOS",
                        style = MaterialTheme.typography.labelLarge,
                        color = Mesh.Signal.Emergency
                    )
                }
            }

            Text(
                if (fromMe) "You" else (entry.fromNode ?: "Unknown node"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (fromMe) null else Mesh.Mono,
                fontWeight = FontWeight.Bold,
                color = if (fromMe) Mesh.Signal.Live else Mesh.Text.Secondary
            )

            if (audio != null) {
                AudioBubbleContent(
                    audio = audio,
                    state = audioStateOf(audio.audioId),
                    transfer = audioTransfers[audio.audioId],
                    isPlaying = playback.audioId == audio.audioId && playback.playing,
                    progress = if (playback.audioId == audio.audioId) playback.progress else 0f,
                    accent = if (fromMe) Mesh.Signal.Live else Mesh.Text.Secondary,
                    onTogglePlay = { onTogglePlay(audio.audioId) },
                    onRetry = { onRetryAudio(audio.audioId) }
                )
            } else {
                Text(
                    entry.displayPayload,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Mesh.Text.Primary
                )
            }

            if (entry.hasLocation) {
                LocationBlock(entry)
            }

            Text(
                metadataFor(entry, fromMe),
                style = MaterialTheme.typography.labelSmall,
                color = Mesh.Text.Tertiary
            )
        }
    }
}

/**
 * Coordinates that travelled with the message. They belong to the ORIGINAL
 * sender — a relay never overwrites them.
 */
@Composable
private fun LocationBlock(entry: MeshLogEntry) {
    val context = LocalContext.current
    val latitude = entry.latitude ?: return
    val longitude = entry.longitude ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mesh.Surface.Sunken, RoundedCornerShape(Mesh.Radius.md))
            .padding(Mesh.Space.md),
        verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = Mesh.Signal.Live,
                modifier = Modifier.size(15.dp)
            )
            Text(
                "Location attached",
                style = MaterialTheme.typography.labelSmall,
                color = Mesh.Signal.Live
            )
        }
        Text(
            formatCoordinates(latitude, longitude),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = Mesh.Mono,
            color = Mesh.Text.Primary
        )
        TextButton(
            onClick = { openInMaps(context, latitude, longitude, entry.fromNode) },
            modifier = Modifier.heightIn(min = Mesh.TouchTarget)
        ) {
            Text(
                "View location",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Mesh.Signal.Live
            )
        }
    }
}

/** Opens any installed maps app. Works offline if that app has offline data. */
private fun openInMaps(context: Context, latitude: Double, longitude: Double, fromNode: String?) {
    val label = Uri.encode("DisasterMesh SOS ${fromNode ?: ""}".trim())
    val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        // The coordinates are already on screen, so nothing is lost.
        Toast.makeText(
            context,
            "No maps app installed. Coordinates: ${formatCoordinates(latitude, longitude)}",
            Toast.LENGTH_LONG
        ).show()
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

/**
 * Real fields only. There is no delivery confirmation in the mesh, so a sent
 * message says "Sent" and never claims to have arrived.
 */
private fun metadataFor(entry: MeshLogEntry, fromMe: Boolean): String {
    val parts = mutableListOf<String>()
    parts += timeFormat.format(Date(entry.timestamp))
    if (fromMe) {
        parts += "Sent"
    } else {
        entry.hops?.let { parts += if (it == 1) "1 hop" else "$it hops" }
    }
    entry.ttl?.let { parts += "TTL $it" }
    return parts.joinToString("  ·  ")
}

/** Relay / drop events — the visible evidence of multi-hop. */
@Composable
private fun SystemLine(entry: MeshLogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            entry.text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = Mesh.Mono,
            color = if (entry.kind == MeshLogEntry.Kind.RELAYED) {
                Mesh.Signal.Warning
            } else {
                Mesh.Text.Tertiary
            },
            modifier = Modifier
                .background(Mesh.Surface.Sunken, RoundedCornerShape(Mesh.Radius.sm))
                .padding(horizontal = Mesh.Space.md, vertical = Mesh.Space.sm)
        )
    }
}
