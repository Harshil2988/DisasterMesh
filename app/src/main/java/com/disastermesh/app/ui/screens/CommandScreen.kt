package com.disastermesh.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.disastermesh.app.command.EmergencyReport
import com.disastermesh.app.command.ReportCategory
import com.disastermesh.app.command.ReportCounts
import com.disastermesh.app.command.ReportStatus
import com.disastermesh.app.command.triageSorted
import com.disastermesh.app.nearby.MeshState
import com.disastermesh.app.ui.components.LiveDot
import com.disastermesh.app.ui.components.MeshPanel
import com.disastermesh.app.ui.components.MeshStatus
import com.disastermesh.app.ui.components.MeshStatusKind
import com.disastermesh.app.ui.components.ReportCard
import com.disastermesh.app.ui.components.SectionLabel
import com.disastermesh.app.ui.components.categoryColor
import com.disastermesh.app.ui.components.UplinkCard
import com.disastermesh.app.ui.components.UplinkTimeline
import com.disastermesh.app.ui.theme.Mesh
import com.disastermesh.app.uplink.DeliveryState
import com.disastermesh.app.uplink.UplinkEvent
import com.disastermesh.app.uplink.UplinkStatus

/**
 * Disaster Command.
 *
 * A triage board, not a message list: reports are ordered by severity first and
 * recency second, so a critical report never falls below a newer supply request.
 * Every number shown is counted from reports this device actually received.
 */
@Composable
fun CommandScreen(
    state: MeshState,
    reports: List<EmergencyReport>,
    counts: ReportCounts,
    onSetStatus: (String, ReportStatus) -> Unit,
    onViewOnMap: (String) -> Unit,
    uplinkStatus: UplinkStatus,
    uplinkEvents: List<UplinkEvent>,
    deliveryStates: Map<String, DeliveryState>
) {
    var filter by remember { mutableStateOf<ReportCategory?>(null) }
    var openOnly by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<EmergencyReport?>(null) }

    // Filtering is a cheap in-memory pass; remember keyed on its real inputs so
    // it does not rerun on unrelated recomposition.
    val visible = remember(reports, filter, openOnly, query) {
        reports
            .asSequence()
            .filter { filter == null || it.category == filter }
            .filter { !openOnly || it.isOpen }
            .filter { query.isBlank() || it.searchText.contains(query.trim().lowercase()) }
            .toList()
            .triageSorted()
    }

    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.xl)) {

        Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)) {
            Text(
                "Disaster Command",
                style = MaterialTheme.typography.displaySmall,
                color = Mesh.Text.Primary
            )
            Text(
                "Emergency reports received by this node",
                style = MaterialTheme.typography.bodySmall,
                color = Mesh.Text.Tertiary
            )
        }

        SituationSummary(state, counts)

        // The hybrid-uplink panel that used to sit here is a verbatim copy of the
        // one on Home. Command answers "what emergencies are we dealing with" —
        // transport belongs to Home, and its event log is at the foot of this
        // screen where an operator looks for it deliberately.

        SearchField(query = query, onQueryChange = { query = it })

        FilterRow(
            selected = filter,
            openOnly = openOnly,
            counts = counts,
            onSelect = { filter = it },
            onToggleOpen = { openOnly = !openOnly }
        )

        if (visible.isEmpty()) {
            EmptyFeed(hasAnyReports = reports.isNotEmpty(), meshActive = state.meshActive)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
                SectionLabel("Priority feed (${visible.size})")
                // The feed is bounded by the repository (500) and already filtered,
                // so a plain column keeps scrolling in one place with the rest of
                // the screen rather than nesting a second scroll container.
                visible.forEach { report ->
                    key(report.reportId) {
                        ReportCard(report = report, onClick = { detail = report })
                    }
                }
            }
        }

        UplinkTimeline(uplinkEvents)
    }

    detail?.let { report ->
        ReportDetailDialog(
            report = reports.firstOrNull { it.reportId == report.reportId } ?: report,
            onDismiss = { detail = null },
            onSetStatus = { status -> onSetStatus(report.reportId, status) },
            onViewOnMap = { onViewOnMap(report.reportId) },
            deliveryState = deliveryStates[report.reportId]
        )
    }
}

@Composable
private inline fun key(id: String, content: @Composable () -> Unit) {
    androidx.compose.runtime.key(id) { content() }
}

/**
 * Real mesh state plus real report counts. Nothing derived that is not measured.
 *
 * Five stacked rows and a paragraph of caveat became one scannable strip: the
 * feed is what this screen is for, and the summary should not push it below the
 * fold before an operator has seen a single report.
 */
@Composable
private fun SituationSummary(state: MeshState, counts: ReportCounts) {
    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MeshStatus(
                if (state.meshActive && state.isConnected) MeshStatusKind.ACTIVE
                else if (state.meshActive) MeshStatusKind.STARTING
                else MeshStatusKind.OFFLINE
            )
            Text(
                "· ${state.connectedCount} connected",
                style = MaterialTheme.typography.bodySmall,
                color = Mesh.Text.Secondary
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm)
        ) {
            ReportCategory.entries.forEach { category ->
                CountTile(category, countFor(category, counts))
            }
        }

        Text(
            "Counts reflect reports this device has received.",
            style = MaterialTheme.typography.labelSmall,
            color = Mesh.Text.Tertiary
        )
    }
}

private fun countFor(category: ReportCategory, counts: ReportCounts): Int = when (category) {
    ReportCategory.CRITICAL -> counts.critical
    ReportCategory.MEDICAL -> counts.medical
    ReportCategory.WARNING -> counts.warning
    ReportCategory.SUPPLY -> counts.supply
    ReportCategory.SAFE -> counts.safe
}

/**
 * One category, one tile. Colour appears only when the count is non-zero, so a
 * quiet situation reads as genuinely quiet rather than as five lit indicators.
 */
@Composable
private fun CountTile(category: ReportCategory, count: Int) {
    val color = categoryColor(category)
    val live = count > 0
    Column(
        modifier = Modifier
            .background(Mesh.Surface.Card, RoundedCornerShape(Mesh.Radius.md))
            .padding(horizontal = Mesh.Space.lg, vertical = Mesh.Space.md),
        verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 12.dp)
                    .background(
                        if (live) color else Mesh.Signal.Idle,
                        RoundedCornerShape(2.dp)
                    )
            )
            Text(
                category.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (live) Mesh.Text.Secondary else Mesh.Text.Tertiary
            )
        }
        Text(
            count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = if (live) color else Mesh.Text.Tertiary
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "Search reports, nodes, keywords…",
                style = MaterialTheme.typography.bodyMedium,
                color = Mesh.Text.Tertiary
            )
        },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Mesh.Text.Tertiary)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                TextButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = "Clear search",
                        tint = Mesh.Text.Tertiary
                    )
                }
            }
        },
        singleLine = true,
        textStyle = TextStyle(fontSize = 15.sp, color = Mesh.Text.Primary),
        shape = RoundedCornerShape(Mesh.Radius.md),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Mesh.Signal.Live,
            unfocusedBorderColor = Mesh.Line.Subtle,
            focusedContainerColor = Mesh.Surface.Card,
            unfocusedContainerColor = Mesh.Surface.Card,
            cursorColor = Mesh.Signal.Live
        )
    )
}

@Composable
private fun FilterRow(
    selected: ReportCategory?,
    openOnly: Boolean,
    counts: ReportCounts,
    onSelect: (ReportCategory?) -> Unit,
    onToggleOpen: () -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm)
    ) {
        FilterChip(
            label = "All",
            count = counts.total,
            selected = selected == null && !openOnly,
            color = Mesh.Signal.Live,
            onClick = { onSelect(null) }
        )
        FilterChip(
            label = "Unresolved",
            count = null,
            selected = openOnly,
            color = Mesh.Signal.Warning,
            onClick = onToggleOpen
        )
        ReportCategory.entries.forEach { category ->
            FilterChip(
                label = category.label,
                count = countFor(category, counts),
                selected = selected == category,
                color = categoryColor(category),
                onClick = { onSelect(if (selected == category) null else category) }
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    count: Int?,
    selected: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Mesh.Radius.md)
    Row(
        modifier = Modifier
            .heightIn(min = Mesh.TouchTarget)
            .background(if (selected) color.copy(alpha = 0.16f) else Mesh.Surface.Card, shape)
            .border(1.dp, if (selected) color else Mesh.Line.Subtle, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Mesh.Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Mesh.Text.Primary else Mesh.Text.Secondary
        )
        if (count != null) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = Mesh.Mono,
                color = if (selected) color else Mesh.Text.Tertiary
            )
        }
    }
}

@Composable
private fun EmptyFeed(hasAnyReports: Boolean, meshActive: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mesh.Surface.Card, RoundedCornerShape(Mesh.Radius.lg))
            .padding(Mesh.Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (hasAnyReports) Mesh.Text.Tertiary else Mesh.Signal.Ok,
            modifier = Modifier.size(28.dp)
        )
        Text(
            if (hasAnyReports) "No reports match" else "No active emergencies",
            style = MaterialTheme.typography.titleMedium,
            color = Mesh.Text.Primary
        )
        Text(
            when {
                hasAnyReports -> "Clear the filters or search to see all reports."
                meshActive -> "Nothing has been reported yet. Emergency reports from " +
                    "nearby nodes — including ones relayed from further away — appear here."
                else -> "Start the mesh on the Home tab to begin receiving reports."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Mesh.Text.Tertiary
        )
    }
}

/** Full detail plus the local triage actions. */
@Composable
private fun ReportDetailDialog(
    report: EmergencyReport,
    onDismiss: () -> Unit,
    onSetStatus: (ReportStatus) -> Unit,
    onViewOnMap: () -> Unit,
    deliveryState: DeliveryState?
) {
    val context = LocalContext.current
    val accent = categoryColor(report.category)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Mesh.Surface.Raised,
        titleContentColor = Mesh.Text.Primary,
        textContentColor = Mesh.Text.Secondary,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)) {
                Text(
                    "${report.category.priorityLabel} · ${report.category.label.uppercase()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent
                )
                Text(
                    report.description,
                    style = MaterialTheme.typography.titleMedium,
                    color = Mesh.Text.Primary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.md)) {
                DetailRow("Sender", report.senderId, mono = true)
                DetailRow("Received", relativeTimeFull(report.receivedAt))
                report.hops?.let { DetailRow("Hops", it.toString(), mono = true) }
                report.ttl?.let { DetailRow("TTL remaining", it.toString(), mono = true) }
                report.peopleAffected?.let { DetailRow("People affected", it.toString(), mono = true) }
                DetailRow("Status", report.status.label)
                deliveryState?.let { DetailRow("External delivery", it.label) }

                if (report.hasLocation) {
                    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = Mesh.Signal.Live,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                "Location attached",
                                style = MaterialTheme.typography.labelSmall,
                                color = Mesh.Signal.Live
                            )
                        }
                        Text(
                            "%.6f, %.6f".format(report.latitude, report.longitude),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = Mesh.Mono,
                            color = Mesh.Text.Primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm)) {
                            TextButton(
                                onClick = onViewOnMap,
                                modifier = Modifier.heightIn(min = Mesh.TouchTarget)
                            ) {
                                Text(
                                    "View on map",
                                    color = Mesh.Signal.Live,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(
                                onClick = {
                                    openInMaps(
                                        context,
                                        report.latitude!!,
                                        report.longitude!!,
                                        report.senderId
                                    )
                                },
                                modifier = Modifier.heightIn(min = Mesh.TouchTarget)
                            ) {
                                Text("Open externally", color = Mesh.Text.Secondary)
                            }
                        }
                    }
                } else {
                    Text(
                        "Location unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mesh.Text.Tertiary
                    )
                }

                SectionLabel("Triage")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm)
                ) {
                    ReportStatus.entries.forEach { status ->
                        TextButton(
                            onClick = { onSetStatus(status) },
                            modifier = Modifier.heightIn(min = Mesh.TouchTarget)
                        ) {
                            Text(
                                status.label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (status == report.status) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                                color = if (status == report.status) {
                                    Mesh.Signal.Live
                                } else {
                                    Mesh.Text.Secondary
                                }
                            )
                        }
                    }
                }
                Text(
                    "Status is recorded on this device only. It is not shared across " +
                        "the mesh — syncing it would require a protocol change.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Mesh.Text.Tertiary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Mesh.Text.Secondary)
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Mesh.Text.Tertiary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (mono) Mesh.Mono else null,
            color = Mesh.Text.Primary
        )
    }
}

private fun relativeTimeFull(timestamp: Long): String {
    val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return "${format.format(java.util.Date(timestamp))}  ·  ${
        com.disastermesh.app.ui.components.relativeTime(timestamp)
    }"
}

/** Standard geo URI — no Maps SDK, and the coordinates stay readable without one. */
private fun openInMaps(context: Context, latitude: Double, longitude: Double, sender: String) {
    val label = Uri.encode("DisasterMesh report $sender")
    val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "No maps app installed. Coordinates: %.6f, %.6f".format(latitude, longitude),
            Toast.LENGTH_LONG
        ).show()
    }
}
