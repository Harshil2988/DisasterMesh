package com.disastermesh.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disastermesh.app.ui.theme.MeshColors

/**
 * The big red emergency control.
 *
 * Two safety features: it is disabled when there is nobody to send to, and it
 * always asks for confirmation before broadcasting.
 */
@Composable
fun SosButton(
    enabled: Boolean,
    categoryLabel: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirm by remember { mutableStateOf(false) }

    val transition = rememberInfiniteTransition(label = "sos")
    val glow by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sosGlow"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Soft halo behind the button. Only breathes while it is usable.
            Box(
                modifier = Modifier
                    .size(188.dp)
                    .scale(if (enabled) glow else 1f)
                    .background(
                        if (enabled) MeshColors.RedDeep.copy(alpha = 0.28f) else Color.Transparent,
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(156.dp)
                    .background(
                        if (enabled) MeshColors.Red.copy(alpha = 0.16f) else MeshColors.Surface,
                        CircleShape
                    )
                    .border(
                        2.dp,
                        if (enabled) MeshColors.Red else MeshColors.Border,
                        CircleShape
                    )
                    .clickable(enabled = enabled) { showConfirm = true },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "SOS",
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        color = if (enabled) MeshColors.Red else MeshColors.TextDim
                    )
                    Text(
                        "BROADCAST TRIAGE",
                        style = MaterialTheme.typography.bodySmall,
                        letterSpacing = 1.sp,
                        color = if (enabled) MeshColors.TextSecondary else MeshColors.TextDim
                    )
                }
            }
        }

        if (!enabled) {
            Text(
                "No connected nodes — nothing to broadcast to yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MeshColors.TextDim
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = MeshColors.SurfaceHigh,
            titleContentColor = MeshColors.TextPrimary,
            textContentColor = MeshColors.TextSecondary,
            title = { Text("Broadcast emergency SOS?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This sends an SOS marked \"$categoryLabel\" to every nearby mesh node, " +
                        "and they will relay it onwards. Only use it for a real emergency."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onConfirmed()
                }) {
                    Text("SEND SOS", color = MeshColors.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("CANCEL", color = MeshColors.TextSecondary)
                }
            }
        )
    }
}
