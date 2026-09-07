package com.disastermesh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.audio.AudioMessage
import com.disastermesh.app.audio.AudioMessageConfig
import com.disastermesh.app.audio.AudioState
import com.disastermesh.app.audio.AudioTransfer
import com.disastermesh.app.ui.theme.Mesh

/**
 * A voice message inside a chat bubble.
 *
 * Deliberately shows no play control until the audio is genuinely complete: a
 * half-transferred clip is never presented as playable.
 */
@Composable
fun AudioBubbleContent(
    audio: AudioMessage,
    state: AudioState,
    transfer: AudioTransfer?,
    isPlaying: Boolean,
    progress: Float,
    accent: Color,
    onTogglePlay: () -> Unit,
    onRetry: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.sm)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (state) {
                AudioState.PLAYABLE -> {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(accent.copy(alpha = 0.20f), CircleShape)
                            .selectable(selected = isPlaying, onClick = onTogglePlay),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Clear else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause voice message" else "Play voice message",
                            tint = accent,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)
                    ) {
                        LinearProgressIndicator(
                            progress = { if (isPlaying || progress > 0f) progress else 0f },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = accent,
                            trackColor = Mesh.Surface.Sunken
                        )
                        Text(
                            "Voice message · ${audio.durationLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mesh.Text.Tertiary
                        )
                    }
                }

                AudioState.TRANSFERRING, AudioState.PENDING -> {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)
                    ) {
                        Text(
                            if (state == AudioState.TRANSFERRING) "Receiving voice message…"
                            else "Voice message · waiting for audio",
                            style = MaterialTheme.typography.bodySmall,
                            color = Mesh.Text.Secondary
                        )
                        LinearProgressIndicator(
                            progress = { transfer?.progress ?: 0f },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = Mesh.Signal.Live,
                            trackColor = Mesh.Surface.Sunken
                        )
                        Text(
                            "${audio.durationLabel} · ${audio.sizeBytes / 1024} KB",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mesh.Text.Tertiary
                        )
                    }
                }

                AudioState.FAILED -> {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)
                    ) {
                        Text(
                            "Audio transfer incomplete",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Mesh.Signal.Warning
                        )
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier.heightIn(min = Mesh.TouchTarget)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = null,
                                    tint = Mesh.Signal.Live,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text("Retry", color = Mesh.Signal.Live)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Live recording panel, replacing the composer while capture is running. */
@Composable
fun RecordingPanel(
    elapsedMs: Long,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val seconds = (elapsedMs / 1000).toInt()
    val remaining = AudioMessageConfig.MAX_DURATION_SECONDS - seconds

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mesh.Surface.Card)
            .padding(Mesh.Space.lg),
        verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // A visible, unmissable indication that the microphone is live.
            LiveDot(active = true, color = Mesh.Signal.Emergency, size = 10)
            Text(
                "Recording",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Mesh.Signal.Emergency
            )
            Text(
                "%d:%02d".format(seconds / 60, seconds % 60),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = Mesh.Mono,
                color = Mesh.Text.Primary
            )
            if (remaining <= 5) {
                Text(
                    "${remaining}s left",
                    style = MaterialTheme.typography.labelSmall,
                    color = Mesh.Signal.Warning
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).heightIn(min = Mesh.TouchTarget)
            ) { Text("Cancel", color = Mesh.Text.Secondary) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Mesh.TouchTarget)
                    .background(Mesh.Signal.Emergency, RoundedCornerShape(Mesh.Radius.md))
                    .selectable(selected = false, onClick = onStop),
                contentAlignment = Alignment.Center
            ) {
                Text("Stop", color = Mesh.Text.Primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Preview of a finished recording, before it is committed to the mesh. */
@Composable
fun AudioPreviewPanel(
    durationMs: Long,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    modifier: Modifier = Modifier
) {
    val seconds = (durationMs / 1000).toInt()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mesh.Surface.Card)
            .padding(Mesh.Space.lg),
        verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)
    ) {
        SectionLabel("Voice message")

        Row(
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Mesh.Signal.Live.copy(alpha = 0.18f), CircleShape)
                    .selectable(selected = isPlaying, onClick = onTogglePlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Clear else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause preview" else "Play preview",
                    tint = Mesh.Signal.Live,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                "%d:%02d".format(seconds / 60, seconds % 60),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = Mesh.Mono,
                color = Mesh.Text.Primary
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f).heightIn(min = Mesh.TouchTarget)
            ) { Text("Delete", color = Mesh.Text.Secondary) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Mesh.TouchTarget)
                    .background(
                        if (canSend) Mesh.Signal.Live else Mesh.Surface.Raised,
                        RoundedCornerShape(Mesh.Radius.md)
                    )
                    .selectable(selected = false, enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Send",
                    color = if (canSend) Mesh.Text.OnAccent else Mesh.Text.Tertiary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (!canSend) {
            Text(
                "No connected nodes — the voice message cannot leave this phone yet.",
                style = MaterialTheme.typography.labelSmall,
                color = Mesh.Text.Tertiary
            )
        }
    }
}
