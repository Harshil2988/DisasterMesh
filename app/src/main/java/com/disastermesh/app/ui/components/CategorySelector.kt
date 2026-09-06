package com.disastermesh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.ui.MessageCategory
import com.disastermesh.app.ui.theme.MeshColors

/**
 * Horizontal emergency category selector.
 *
 * A horizontal strip rather than the reference's left rail: on a phone a side
 * rail steals width the status readouts need. It scrolls, so it never crowds
 * a narrow screen.
 */
@Composable
fun CategorySelector(
    selected: MessageCategory,
    onSelect: (MessageCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
    Column(
        modifier = Modifier
            .background(
                if (isSelected) accent.copy(alpha = 0.16f) else MeshColors.Surface,
                RoundedCornerShape(14.dp)
            )
            .border(
                1.dp,
                if (isSelected) accent else MeshColors.Border,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = iconFor(category),
            contentDescription = null,
            tint = if (isSelected) accent else MeshColors.TextDim,
            modifier = Modifier.size(22.dp)
        )
        Text(
            category.label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MeshColors.TextPrimary else MeshColors.TextDim
        )
    }
}

private fun iconFor(category: MessageCategory): ImageVector = when (category) {
    MessageCategory.SAFE -> Icons.Filled.CheckCircle
    MessageCategory.MEDICAL -> Icons.Filled.Favorite
    MessageCategory.WARNING -> Icons.Filled.Warning
    MessageCategory.SUPPLY -> Icons.Filled.ShoppingCart
}

fun accentFor(category: MessageCategory): Color = when (category) {
    MessageCategory.SAFE -> MeshColors.Green
    MessageCategory.MEDICAL -> MeshColors.Red
    MessageCategory.WARNING -> MeshColors.Amber
    MessageCategory.SUPPLY -> MeshColors.Cyan
}
