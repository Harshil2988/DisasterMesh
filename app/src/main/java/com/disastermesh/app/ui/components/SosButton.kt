package com.disastermesh.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disastermesh.app.ui.SosLocationState
import com.disastermesh.app.ui.theme.Mesh

/**
 * The emergency control — the single most important action in the product.
 *
 * Weighted to dominate its section without turning the whole screen red: one
 * saturated ring on a dark ground, breathing slowly only while it is usable.
 * Disabled with an explicit reason when there is nobody to reach.
 */
@Composable
fun SosButton(
    enabled: Boolean,
    categoryLabel: String,
    connectedCount: Int,
    locationState: SosLocationState,
    onOpened: () -> Unit,
    onConfirmed: () -> Unit,
    onSendWithoutLocation: () -> Unit,
    onCancelled: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirm by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val transition = rememberInfiniteTransition(label = "sos")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "breath"
    )
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(120),
        label = "sosPress"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Outer breath. Only animates while the action is available, so a
            // disabled control is visually still.
            if (enabled) {
                Box(
                    modifier = Modifier
                        .size(206.dp)
                        .scale(0.92f + breath * 0.08f)
                        .alpha(0.10f + breath * 0.10f)
                        .background(Mesh.Signal.Emergency, CircleShape)
                )
            }

            Box(
                modifier = Modifier
                    .size(168.dp)
                    .scale(press)
                    .background(
                        if (enabled) Mesh.Signal.EmergencyDeep.copy(alpha = 0.22f)
                        else Mesh.Surface.Card,
                        CircleShape
                    )
                    .border(
                        width = if (enabled) 2.dp else 1.dp,
                        color = if (enabled) Mesh.Signal.Emergency else Mesh.Line.Subtle,
                        shape = CircleShape
                    )
                    .clickable(
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button
                    ) {
                        showConfirm = true
                        onOpened()
                    }
                    .semantics {
                        contentDescription =
                            "Send emergency SOS, category $categoryLabel, to $connectedCount connected nodes"
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "SOS",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                        color = if (enabled) Mesh.Signal.Emergency else Mesh.Text.Tertiary
                    )
                    Text(
                        "Broadcast emergency",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled) Mesh.Text.Secondary else Mesh.Text.Tertiary
                    )
                }
            }
        }

        if (!enabled) {
            Text(
                "No connected nodes yet — an SOS cannot leave this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = Mesh.Text.Tertiary
            )
        }
    }

    if (showConfirm) {
        SosConfirmDialog(
            categoryLabel = categoryLabel,
            connectedCount = connectedCount,
            locationState = locationState,
            onDismiss = {
                showConfirm = false
                onCancelled()
            },
            onConfirm = {
                showConfirm = false
                onConfirmed()
            },
            onSendWithoutLocation = {
                showConfirm = false
                onSendWithoutLocation()
            }
        )
    }
}

@Composable
private fun SosConfirmDialog(
    categoryLabel: String,
    connectedCount: Int,
    locationState: SosLocationState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onSendWithoutLocation: () -> Unit
) {
    val failed = locationState as? SosLocationState.Failed
    val fix = locationState as? SosLocationState.Acquired

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Mesh.Surface.Raised,
        titleContentColor = Mesh.Text.Primary,
        textContentColor = Mesh.Text.Secondary,
        icon = {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Mesh.Signal.Emergency)
        },
        title = { Text("Emergency SOS", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.lg)) {

                DialogRow("Category", categoryLabel)
                DialogRow(
                    "Mesh",
                    if (connectedCount == 1) "1 nearby node" else "$connectedCount nearby nodes"
                )

                Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.sm)) {
                    SectionLabel("Location")
                    when {
                        fix != null -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = Mesh.Signal.Ok,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Location acquired",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Mesh.Signal.Ok
                                )
                            }
                            Text(
                                formatCoordinates(fix.latitude, fix.longitude),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = Mesh.Mono,
                                color = Mesh.Text.Primary
                            )
                            if (fix.fromCache) {
                                Text(
                                    "Last known position — no fresh GPS fix yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Mesh.Signal.Warning
                                )
                            }
                        }

                        failed != null -> {
                            Text(
                                "Location unavailable",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Mesh.Signal.Warning
                            )
                            Text(
                                failed.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = Mesh.Text.Tertiary
                            )
                        }

                        else -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = Mesh.Signal.Live
                                )
                                Text(
                                    "Getting your location…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Mesh.Text.Secondary
                                )
                            }
                        }
                    }
                }

                Text(
                    "This is broadcast to every nearby node and relayed onward. " +
                        "Use it only for a real emergency.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mesh.Text.Tertiary
                )
            }
        },
        confirmButton = {
            if (failed != null) {
                // Sending without coordinates is always an explicit, separate choice.
                TextButton(onClick = onSendWithoutLocation) {
                    Text(
                        "Send without location",
                        color = Mesh.Signal.Emergency,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                TextButton(enabled = fix != null, onClick = onConfirm) {
                    Text(
                        "Send SOS",
                        color = if (fix != null) Mesh.Signal.Emergency else Mesh.Text.Tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Mesh.Text.Secondary)
            }
        }
    )
}

@Composable
private fun DialogRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Mesh.Text.Tertiary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Mesh.Text.Primary
        )
    }
}

/** Six decimals is roughly a tenth of a metre — plenty for a rescue. */
internal fun formatCoordinates(latitude: Double, longitude: Double): String =
    "%.6f, %.6f".format(latitude, longitude)

/** Small rounded surface used by the SOS dialog for grouping. */
@Composable
internal fun DialogSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .background(Mesh.Surface.Card, RoundedCornerShape(Mesh.Radius.md))
            .padding(Mesh.Space.md)
    ) { content() }
}
