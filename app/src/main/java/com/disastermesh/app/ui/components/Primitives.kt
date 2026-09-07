package com.disastermesh.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.disastermesh.app.command.ReportCategory
import com.disastermesh.app.ui.theme.Mesh

/**
 * The shared component layer.
 *
 * The previous build grew three chip implementations and four metric displays
 * across five screens. Nothing there was wrong individually; the divergence is
 * what made the app read as assembled rather than designed. Everything visual
 * that appears on more than one screen now lives here, once.
 */

// ---------------------------------------------------------------------------
// Status — one vocabulary for the whole app
// ---------------------------------------------------------------------------

/**
 * Every state the network can be in, named once.
 *
 * The old screens said "Mesh inactive", "Mesh offline" and "Mesh only" for
 * overlapping ideas. A user under stress should not have to work out whether
 * those are three states or one.
 */
enum class MeshStatusKind(val label: String) {
    ACTIVE("MESH ACTIVE"),
    STARTING("MESH STARTING"),
    OFFLINE("MESH OFFLINE"),
    CONNECTING("CONNECTING"),
    SYNCING("SYNCING"),
    IDLE("IDLE");

    val color: Color
        get() = when (this) {
            ACTIVE -> Mesh.Signal.Ok
            STARTING -> Mesh.Signal.Warning
            CONNECTING, SYNCING -> Mesh.Signal.Live
            OFFLINE, IDLE -> Mesh.Signal.Idle
        }
}

/**
 * A dot and a word. The word always carries the meaning; the colour only
 * reinforces it, so the state survives greyscale, sunlight and colour blindness.
 */
@Composable
fun MeshStatus(
    kind: MeshStatusKind,
    modifier: Modifier = Modifier,
    label: String = kind.label
) {
    val tint by animateColorAsState(kind.color, tween(220), label = "statusTint")
    Row(
        modifier = modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(tint, CircleShape)
                .clearAndSetSemantics { }
        )
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = tint
        )
    }
}

// ---------------------------------------------------------------------------
// Category colour — the single source of truth
// ---------------------------------------------------------------------------

/**
 * One category-to-colour mapping for the entire app.
 *
 * Supply is deliberately the quietest: logistics is not danger, and giving
 * every category a loud accent is exactly how a palette becomes a rainbow.
 */
fun colorForCategory(category: ReportCategory): Color = when (category) {
    ReportCategory.CRITICAL -> Mesh.Signal.Emergency
    ReportCategory.MEDICAL -> Mesh.Signal.Medical
    ReportCategory.WARNING -> Mesh.Signal.Warning
    ReportCategory.SUPPLY -> Mesh.Signal.Supply
    ReportCategory.SAFE -> Mesh.Signal.Ok
}

// ---------------------------------------------------------------------------
// Chip — replaces Chip, FilterChip and CategoryChip
// ---------------------------------------------------------------------------

/**
 * Selection is carried by fill AND border weight, never by colour alone, so a
 * selected filter is still obvious to someone who cannot separate the hues.
 */
@Composable
fun MeshChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Mesh.Signal.Live,
    leadingDot: Color? = null,
    trailing: String? = null
) {
    val shape = RoundedCornerShape(Mesh.Radius.sm)
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .background(if (selected) Mesh.Surface.Raised else Mesh.Surface.Sunken, shape)
            .border(
                BorderStroke(if (selected) 1.5.dp else 1.dp,
                    if (selected) accent else Mesh.Line.Subtle),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Mesh.Space.lg, vertical = Mesh.Space.md),
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingDot != null) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (selected) leadingDot else Mesh.Signal.Idle, CircleShape)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Mesh.Text.Primary else Mesh.Text.Secondary
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) accent else Mesh.Text.Tertiary
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Metrics — replaces StatusStat, SummaryStat, CountRow and ReadoutRow
// ---------------------------------------------------------------------------

/** A headline figure. At most one per screen region, or it stops being a headline. */
@Composable
fun MeshMetricLarge(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Mesh.Text.Primary
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)) {
        Text(value, style = MaterialTheme.typography.displaySmall, color = valueColor)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Mesh.Text.Tertiary)
    }
}

/** Label left, value right. The console readout, used everywhere a pair belongs. */
@Composable
fun MeshMetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Mesh.Text.Primary,
    mono: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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

// ---------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------

enum class MeshIntent { PRIMARY, SECONDARY, EMERGENCY, SAFE }

/**
 * The one button.
 *
 * EMERGENCY is a restrained critical-red action AREA rather than a novelty
 * circle: at a glance it must read as serious, not dramatic. Disabled keeps the
 * exact same footprint so the layout never jumps when connectivity changes —
 * a control that moves while someone is reaching for it is a real hazard here.
 */
@Composable
fun MeshAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    intent: MeshIntent = MeshIntent.PRIMARY,
    sublabel: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(Mesh.Radius.md)
    val fill = when {
        !enabled -> Mesh.Surface.Sunken
        intent == MeshIntent.PRIMARY -> Mesh.Signal.Action
        intent == MeshIntent.EMERGENCY -> Mesh.Signal.EmergencyGround
        intent == MeshIntent.SAFE -> Mesh.Signal.OkGround
        else -> Color.Transparent
    }
    val accent = when {
        !enabled -> Mesh.Text.Tertiary
        intent == MeshIntent.EMERGENCY -> Mesh.Signal.Emergency
        intent == MeshIntent.SAFE -> Mesh.Signal.Ok
        intent == MeshIntent.PRIMARY -> Mesh.Text.Primary
        else -> Mesh.Text.Primary
    }
    val border = when {
        !enabled -> Mesh.Line.Subtle
        intent == MeshIntent.EMERGENCY -> Mesh.Signal.Emergency
        intent == MeshIntent.SAFE -> Mesh.Signal.Ok
        intent == MeshIntent.SECONDARY -> Mesh.Line.Subtle
        else -> Color.Transparent
    }

    Column(
        modifier = modifier
            .heightIn(min = Mesh.TouchTarget)
            .background(fill, shape)
            .then(
                if (border != Color.Transparent) {
                    Modifier.border(BorderStroke(1.5.dp, border), shape)
                } else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Mesh.Space.xl, vertical = Mesh.Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Text(
                label,
                style = if (sublabel != null) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        }
        if (sublabel != null) {
            Text(
                sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) Mesh.Text.Secondary else Mesh.Text.Tertiary
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Section header
// ---------------------------------------------------------------------------

/** Small uppercase label, optionally with a quiet trailing action on the right. */
@Composable
fun MeshSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Mesh.Text.Tertiary
        )
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Mesh.Signal.Live,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(horizontal = Mesh.Space.sm, vertical = Mesh.Space.xs)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Empty / error states
// ---------------------------------------------------------------------------

/**
 * Compact by design.
 *
 * The previous Messages screen rendered "no messages" as a card roughly a third
 * of the viewport tall holding one sentence. An empty state is information, not
 * furniture: it gets the room one sentence deserves and no more.
 */
@Composable
fun MeshEmpty(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Mesh.Space.xxxl, horizontal = Mesh.Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)
    ) {
        Icon(icon, contentDescription = null, tint = Mesh.Text.Tertiary, modifier = Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Mesh.Text.Primary)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = Mesh.Text.Tertiary,
            textAlign = TextAlign.Center
        )
        if (action != null) action()
    }
}

/** Calm, clear, actionable. Never surfaces a raw exception to the user. */
@Composable
fun MeshErrorNotice(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable ColumnScope.() -> Unit)? = null
) {
    val shape = RoundedCornerShape(Mesh.Radius.md)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mesh.Signal.WarningGround, shape)
            .border(BorderStroke(1.dp, Mesh.Signal.Warning.copy(alpha = 0.55f)), shape)
            .padding(Mesh.Space.lg),
        verticalArrangement = Arrangement.spacedBy(Mesh.Space.sm)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = Mesh.Signal.Warning,
                modifier = Modifier.size(18.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Mesh.Text.Primary
            )
        }
        Text(body, style = MaterialTheme.typography.bodySmall, color = Mesh.Text.Secondary)
        if (action != null) action()
    }
}

// ---------------------------------------------------------------------------
// Preview row — the Home screen's gateway into a full screen
// ---------------------------------------------------------------------------

/**
 * A quiet, tappable summary that hands off to another destination.
 *
 * This is what replaces the full-size preview cards Home used to carry. Home
 * should say what is happening and where to go; it should not try to be a small
 * copy of four other screens.
 */
@Composable
fun MeshPreviewRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(Mesh.Radius.md)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Mesh.Surface.Card, shape)
            .clickable(onClick = onClick)
            .padding(Mesh.Space.lg),
        verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Mesh.Text.Tertiary
            )
            Text(
                "Open",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Mesh.Signal.Live
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.xxl),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
