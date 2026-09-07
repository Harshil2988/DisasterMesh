package com.disastermesh.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.ui.MessageCategory
import com.disastermesh.app.ui.theme.Mesh

/**
 * Emergency category selector.
 *
 * A horizontal strip rather than a side rail: on a narrow screen a rail steals
 * width the status readouts need. Chips are 48dp tall with 8dp gaps to meet
 * Android touch-target guidance, and scroll rather than wrap.
 */
@Composable
fun CategorySelector(
    selected: MessageCategory,
    onSelect: (MessageCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = Mesh.Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm)
    ) {
        MessageCategory.entries.forEach { category ->
            CategoryChip(
                category = category,
                isSelected = category == selected,
                onClick = { onSelect(category) }
            )
        }
    }
}

@Composable
private fun CategoryChip(
    category: MessageCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accent = accentFor(category)
    val shape = RoundedCornerShape(Mesh.Radius.md)

    val container by animateColorAsState(
        targetValue = if (isSelected) accent.copy(alpha = 0.14f) else Mesh.Surface.Card,
        animationSpec = tween(220),
        label = "chipBg"
    )
    val outline by animateColorAsState(
        targetValue = if (isSelected) accent else Mesh.Line.Subtle,
        animationSpec = tween(220),
        label = "chipLine"
    )

    Row(
        modifier = Modifier
            .heightIn(min = Mesh.TouchTarget)
            .background(container, shape)
            .border(1.dp, outline, shape)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Mesh.Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = iconFor(category),
            contentDescription = null,
            tint = if (isSelected) accent else Mesh.Text.Tertiary,
            modifier = Modifier.size(17.dp)
        )
        Text(
            // Sentence case rather than shouting: the SOS button is the loud element.
            category.label.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Mesh.Text.Primary else Mesh.Text.Secondary
        )
    }
}

private fun iconFor(category: MessageCategory): ImageVector = when (category) {
    MessageCategory.SAFE -> Icons.Filled.CheckCircle
    MessageCategory.MEDICAL -> Icons.Filled.Favorite
    MessageCategory.WARNING -> Icons.Filled.Warning
    MessageCategory.SUPPLY -> Icons.Filled.ShoppingCart
}

/** Semantic colour per category, drawn from the shared palette. */
fun accentFor(category: MessageCategory): Color = when (category) {
    MessageCategory.SAFE -> Mesh.Signal.Ok
    MessageCategory.MEDICAL -> Mesh.Signal.Emergency
    MessageCategory.WARNING -> Mesh.Signal.Warning
    MessageCategory.SUPPLY -> Mesh.Signal.Live
}
