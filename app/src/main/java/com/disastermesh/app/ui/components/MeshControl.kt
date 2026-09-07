package com.disastermesh.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.ui.theme.Mesh

/**
 * The single networking control.
 *
 * Reads as one deliberate switch rather than a generic Material button: filled
 * and inviting when the mesh is off, quiet and outlined once it is running, so
 * the loud element on screen is always the SOS and never this.
 */
@Composable
fun MeshControl(
    active: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Tactile feedback: a small, fast dip on press.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(120),
        label = "press"
    )

    val container by animateColorAsState(
        targetValue = when {
            !enabled -> Mesh.Surface.Sunken
            active -> Mesh.Surface.Raised
            else -> Mesh.Signal.Action
        },
        animationSpec = tween(300),
        label = "container"
    )

    val content = when {
        !enabled -> Mesh.Text.Tertiary
        active -> Mesh.Text.Primary
        else -> Mesh.Text.Primary
    }

    val shape = RoundedCornerShape(Mesh.Radius.md)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .background(container, shape)
            .then(
                if (active && enabled) Modifier.border(1.dp, Mesh.Line.Strong, shape)
                else Modifier
            )
            .toggleable(
                value = active,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null,
                onValueChange = { onToggle() }
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (active) Icons.Filled.Clear else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = if (active) "  Stop mesh" else "  Start mesh",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = content
        )
    }
}
