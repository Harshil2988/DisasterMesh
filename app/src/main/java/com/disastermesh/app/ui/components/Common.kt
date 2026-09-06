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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.ui.theme.Mesh

/**
 * Standard panel. Depth comes from surface colour; a border is opt-in and used
 * only when a card must be visually separated from another card behind it.
 */
@Composable
fun MeshPanel(
    modifier: Modifier = Modifier,
    background: Color = Mesh.Surface.Card,
    border: Color? = null,
    padding: androidx.compose.ui.unit.Dp = Mesh.Space.lg,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(Mesh.Radius.lg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background, shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(Mesh.Space.md),
        content = content
    )
}

/** Small uppercase section label. Caps are reserved for labels this short. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Mesh.Text.Tertiary,
        modifier = modifier
    )
}

/**
 * A status pill carrying an icon, a name and a state word.
 *
 * Deliberately never encodes its meaning in colour alone: the label and the
 * state text carry it, and colour only reinforces. That keeps it readable for
 * colour-blind users and in bright sunlight.
 */
@Composable
fun StatusChip(
    icon: ImageVector,
    label: String,
    state: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val tint = if (active) Mesh.Signal.Ok else Mesh.Text.Tertiary
    Row(
        modifier = modifier
            .background(
                if (active) Mesh.Surface.Raised else Mesh.Surface.Sunken,
                RoundedCornerShape(Mesh.Radius.sm)
            )
            .padding(horizontal = Mesh.Space.md, vertical = Mesh.Space.sm)
            .semantics { contentDescription = "$label: $state" },
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Mesh.Text.Secondary)
        Text(
            state,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = tint
        )
    }
}

/** Label on the left, value on the right — the console readout. */
@Composable
fun ReadoutRow(
    label: String,
    value: String,
    valueColor: Color = Mesh.Text.Primary,
    mono: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Mesh.Text.Secondary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            fontFamily = if (mono) Mesh.Mono else null
        )
    }
}

/**
 * A dot that breathes slowly while [active].
 *
 * Purely decorative reinforcement of a state that is always also written in
 * words nearby, so it is hidden from screen readers.
 */
@Composable
fun LiveDot(
    active: Boolean,
    color: Color,
    size: Int = 9,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "live")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .size((size + 8).dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        if (active) {
            // Soft halo, sized off the same pulse so the two stay in step.
            Box(
                modifier = Modifier
                    .size((size + 8).dp)
                    .scale(0.7f + (pulse * 0.3f))
                    .alpha(0.18f * pulse)
                    .background(color, CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(size.dp)
                .alpha(if (active) 0.55f + (pulse * 0.45f) else 0.4f)
                .background(if (active) color else Mesh.Signal.Idle, CircleShape)
        )
    }
}

/** A monospaced identity tag, e.g. NODE-7F3A. */
@Composable
fun NodeTag(
    nodeId: String,
    modifier: Modifier = Modifier,
    color: Color = Mesh.Signal.Live,
    background: Color = Mesh.Surface.Raised
) {
    Text(
        nodeId,
        style = MaterialTheme.typography.labelLarge,
        fontFamily = Mesh.Mono,
        color = color,
        modifier = modifier
            .background(background, RoundedCornerShape(Mesh.Radius.sm))
            .padding(horizontal = Mesh.Space.md, vertical = Mesh.Space.xs)
    )
}
