package com.disastermesh.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
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
import com.disastermesh.app.command.EmergencyReport
import com.disastermesh.app.command.ReportCategory
import com.disastermesh.app.command.ReportStatus
import com.disastermesh.app.ui.theme.Mesh

/** Semantic colour per category. Critical is the only saturated red in the feed. */
fun categoryColor(category: ReportCategory): Color = when (category) {
    ReportCategory.CRITICAL -> Mesh.Signal.Emergency
    ReportCategory.MEDICAL -> Mesh.Signal.Warning
    ReportCategory.WARNING -> Mesh.Signal.Live
    ReportCategory.SUPPLY -> Mesh.Signal.Action
    ReportCategory.SAFE -> Mesh.Signal.Ok
}

fun categoryIcon(category: ReportCategory): ImageVector = when (category) {
    ReportCategory.CRITICAL -> Icons.Filled.Warning
    ReportCategory.MEDICAL -> Icons.Filled.Favorite
    ReportCategory.WARNING -> Icons.Filled.Warning
    ReportCategory.SUPPLY -> Icons.Filled.ShoppingCart
    ReportCategory.SAFE -> Icons.Filled.CheckCircle
}

/**
 * One emergency in the triage feed.
 *
 * A coloured severity rail runs down the left edge so urgency is scannable at a
 * glance, while the priority code (P0…P4) and category name carry the same
 * information in text — never colour alone.
 */
@Composable
fun ReportCard(
    report: EmergencyReport,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = categoryColor(report.category)
    val shape = RoundedCornerShape(Mesh.Radius.lg)
    val critical = report.category == ReportCategory.CRITICAL

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (critical) Mesh.Signal.EmergencyDeep.copy(alpha = 0.13f) else Mesh.Surface.Card,
                shape
            )
            .then(
                if (critical) Modifier.border(1.dp, accent.copy(alpha = 0.55f), shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(end = Mesh.Space.lg)
    ) {
        // Severity rail.
        Box(
            modifier = Modifier
                .padding(vertical = Mesh.Space.md)
                .padding(start = Mesh.Space.sm)
                .width(3.dp)
                .height(if (critical) 72.dp else 56.dp)
                .background(accent, RoundedCornerShape(2.dp))
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Mesh.Space.md, top = Mesh.Space.lg, bottom = Mesh.Space.lg),
            verticalArrangement = Arrangement.spacedBy(Mesh.Space.sm)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    categoryIcon(report.category),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    "${report.category.priorityLabel} · ${report.category.label.uppercase()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent
                )
                if (report.status != ReportStatus.UNRESOLVED) {
                    StatusTag(report.status)
                }
            }

            Text(
                report.description,
                style = MaterialTheme.typography.bodyLarge,
                color = Mesh.Text.Primary,
                fontWeight = if (critical) FontWeight.SemiBold else FontWeight.Normal
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    report.senderId,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = Mesh.Mono,
                    color = Mesh.Text.Secondary
                )
                report.hops?.let {
                    Text(
                        if (it == 1) "1 hop" else "$it hops",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mesh.Text.Tertiary
                    )
                }
                Text(
                    relativeTime(report.receivedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Mesh.Text.Tertiary
                )
                if (report.hasLocation) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = Mesh.Signal.Live,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            "Location",
                            style = MaterialTheme.typography.labelSmall,
                            color = Mesh.Signal.Live
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusTag(status: ReportStatus) {
    val color = when (status) {
        ReportStatus.UNRESOLVED -> Mesh.Text.Tertiary
        ReportStatus.ACKNOWLEDGED -> Mesh.Signal.Live
        ReportStatus.RESPONDING -> Mesh.Signal.Warning
        ReportStatus.RESOLVED -> Mesh.Signal.Ok
    }
    Text(
        status.label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(Mesh.Radius.sm))
            .padding(horizontal = Mesh.Space.sm, vertical = 2.dp)
    )
}

/** Coarse, honest relative time from this device's own clock. */
fun relativeTime(timestamp: Long): String {
    val delta = System.currentTimeMillis() - timestamp
    val minutes = delta / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> "${minutes / (60 * 24)} d ago"
    }
}
