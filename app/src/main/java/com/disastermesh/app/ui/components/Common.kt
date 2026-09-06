package com.disastermesh.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.ui.theme.MeshColors

/** Standard rounded panel used by every screen. */
@Composable
fun MeshCard(
    modifier: Modifier = Modifier,
    accent: Color = MeshColors.Border,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MeshColors.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, accent, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** Small uppercase heading above a section. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MeshColors.TextDim,
        modifier = modifier
    )
}

/** A label on the left, a value on the right — the console readout style. */
@Composable
fun ReadoutRow(label: String, value: String, valueColor: Color = MeshColors.TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MeshColors.TextSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * A dot that softly pulses while [active], and sits still when not.
 * Used for the live-mesh indicator and for connected peers.
 */
@Composable
fun PulsingDot(
    active: Boolean,
    color: Color,
    size: Int = 10,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "dot")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Box(
        modifier = modifier
            .size(size.dp)
            .alpha(if (active) pulse else 0.25f)
            .background(color, CircleShape)
    )
}
