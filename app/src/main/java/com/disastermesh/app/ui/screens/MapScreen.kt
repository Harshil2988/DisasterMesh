package com.disastermesh.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disastermesh.app.command.EmergencyReport
import com.disastermesh.app.command.ReportCategory
import com.disastermesh.app.map.GeoPoint
import com.disastermesh.app.map.MapData
import com.disastermesh.app.map.MapFilter
import com.disastermesh.app.map.MapLayers
import com.disastermesh.app.map.MapProjection
import com.disastermesh.app.map.MapWindow
import com.disastermesh.app.map.MappedNode
import com.disastermesh.app.map.MappedReport
import com.disastermesh.app.map.OfflineMapRegion
import com.disastermesh.app.nearby.MeshState
import com.disastermesh.app.ui.components.LiveDot
import com.disastermesh.app.ui.components.SectionLabel
import com.disastermesh.app.ui.components.categoryColor
import com.disastermesh.app.ui.components.categoryIcon
import com.disastermesh.app.ui.components.relativeTime
import com.disastermesh.app.ui.theme.Mesh
import kotlin.math.hypot

/**
 * Disaster Map.
 *
 * Draws only what the app genuinely knows: reports that arrived with valid
 * coordinates, this device's own position, and the last reported position of
 * nodes that have sent a located report. No node is placed at an invented
 * coordinate and no connection is drawn that the mesh has not confirmed.
 *
 * The base layer is a measured coordinate grid, which needs no downloaded tiles
 * and therefore works with the radio off. See [OfflineMapRegion.BaseLayer].
 */
@Composable
fun MapScreen(
    state: MeshState,
    data: MapData,
    focusReportId: String?,
    onFocusHandled: () -> Unit,
    onOpenReport: (EmergencyReport) -> Unit,
    onRequestMyLocation: () -> Unit,
    locationUnavailable: Boolean
) {
    var layers by remember { mutableStateOf(MapLayers()) }
    var filter by remember { mutableStateOf(MapFilter.ALL) }
    var window by remember { mutableStateOf(MapWindow.ALL) }
    var showLegend by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MapSelection?>(null) }

    var centerLat by remember { mutableStateOf(OfflineMapRegion.DEFAULT_CENTER_LAT) }
    var centerLon by remember { mutableStateOf(OfflineMapRegion.DEFAULT_CENTER_LON) }
    var span by remember { mutableStateOf(OfflineMapRegion.DEFAULT_SPAN_DEG) }
    var didInitialFit by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()

    val visibleReports = remember(data.reports, filter, window, layers, now) {
        data.reports.filter { mapped ->
            val category = mapped.report.category
            val layerAllows =
                if (category == ReportCategory.SAFE) layers.safeReports else layers.emergencies
            layerAllows &&
                filter.matches(category) &&
                window.includes(mapped.report.receivedAt, now)
        }
    }

    val visibleNodes = remember(data.nodes, layers, window, now) {
        if (!layers.nodes) emptyList()
        else data.nodes.filter { window.includes(it.lastSeen, now) }
    }

    // Open on something useful the first time there is anything to show.
    val fitPoints = remember(visibleReports, visibleNodes, data.myLocation) {
        buildList {
            visibleReports.forEach { add(it.position) }
            visibleNodes.forEach { add(it.position) }
            data.myLocation?.let { add(it) }
        }
    }
    if (!didInitialFit && fitPoints.isNotEmpty()) {
        MapProjection.fitting(fitPoints)?.let { (lat, lon, s) ->
            centerLat = lat; centerLon = lon; span = s
        }
        didInitialFit = true
    }

    // Command Center asked us to centre on a specific report.
    if (focusReportId != null) {
        data.reports.firstOrNull { it.report.reportId == focusReportId }?.let { target ->
            centerLat = target.position.latitude
            centerLon = target.position.longitude
            span = OfflineMapRegion.MIN_SPAN_DEG * 8
            selected = MapSelection.Report(target)
            didInitialFit = true
        }
        onFocusHandled()
    }

    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.lg)) {

        Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.xs)) {
            Text(
                "Disaster Map",
                style = MaterialTheme.typography.displaySmall,
                color = Mesh.Text.Primary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveDot(active = state.meshActive, color = Mesh.Signal.Ok, size = 8)
                Text(
                    if (state.meshActive) "Mesh active" else "Mesh inactive",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mesh.Text.Secondary
                )
                Text(
                    "·  Offline — no internet used",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mesh.Text.Tertiary
                )
            }
        }

        StatusBar(state, visibleReports, visibleNodes)

        FilterBar(filter, onSelect = { filter = it })
        WindowBar(window, onSelect = { window = it })

        // ---- The map surface -------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(Mesh.Surface.Sunken, RoundedCornerShape(Mesh.Radius.lg))
                .border(1.dp, Mesh.Line.Subtle, RoundedCornerShape(Mesh.Radius.lg))
        ) {
            var canvasSize by remember { mutableStateOf(Size.Zero) }
            val projection = remember(centerLat, centerLon, span, canvasSize) {
                MapProjection(centerLat, centerLon, span, canvasSize)
            }
            val density = LocalDensity.current
            val tapSlop = with(density) { 28.dp.toPx() }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (zoom != 1f) {
                                span = (span / zoom).coerceIn(
                                    OfflineMapRegion.MIN_SPAN_DEG,
                                    OfflineMapRegion.MAX_SPAN_DEG
                                )
                            }
                            if (pan != Offset.Zero && canvasSize.width > 0f) {
                                val p = MapProjection(centerLat, centerLon, span, canvasSize)
                                val (lat, lon) = p.panBy(pan.x, pan.y)
                                centerLat = lat; centerLon = lon
                            }
                        }
                    }
                    .pointerInput(visibleReports, visibleNodes, centerLat, centerLon, span) {
                        detectTapGestures(
                            onDoubleTap = {
                                span = (span / 2).coerceAtLeast(OfflineMapRegion.MIN_SPAN_DEG)
                            },
                            onTap = { tap ->
                                selected = hitTest(
                                    tap, projection, visibleReports, visibleNodes, tapSlop
                                )
                            }
                        )
                    }
            ) {
                canvasSize = size
                val p = MapProjection(centerLat, centerLon, span, size)

                drawCoordinateGrid(p)
                if (layers.connections) drawConnections(p, data, visibleNodes)
                if (layers.nodes) drawNodes(p, visibleNodes, span)
                drawReports(p, visibleReports)
                if (layers.myLocation) data.myLocation?.let { drawMyLocation(p, it) }
                drawScaleBar(p)
            }

            // Empty state sits over the grid rather than replacing the screen.
            if (!data.hasAnything) {
                EmptyMapOverlay(state.connectedCount)
            }

            // Map controls.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Mesh.Space.md),
                verticalArrangement = Arrangement.spacedBy(Mesh.Space.sm)
            ) {
                MapButton(Icons.Filled.Place, "Centre on my location") {
                    onRequestMyLocation()
                    data.myLocation?.let {
                        centerLat = it.latitude
                        centerLon = it.longitude
                        span = OfflineMapRegion.MIN_SPAN_DEG * 8
                    }
                }
                MapButton(Icons.Filled.Refresh, "Fit all markers") {
                    MapProjection.fitting(fitPoints)?.let { (lat, lon, s) ->
                        centerLat = lat; centerLon = lon; span = s
                    }
                }
                MapButton(Icons.Filled.Info, "Toggle legend") { showLegend = !showLegend }
            }

            if (showLegend) {
                Legend(modifier = Modifier.align(Alignment.TopStart).padding(Mesh.Space.md))
            }

            if (locationUnavailable) {
                Text(
                    "Location unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = Mesh.Signal.Warning,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(Mesh.Space.md)
                        .background(Mesh.Surface.Card, RoundedCornerShape(Mesh.Radius.sm))
                        .padding(horizontal = Mesh.Space.sm, vertical = Mesh.Space.xs)
                )
            }
        }

        LayerControls(layers) { layers = it }

        Text(
            "Node markers show each node's last reported position, taken from reports " +
                "it sent. The mesh does not broadcast live node GPS, so a node with no " +
                "located report does not appear.",
            style = MaterialTheme.typography.labelSmall,
            color = Mesh.Text.Tertiary
        )
    }

    selected?.let { selection ->
        MapDetailDialog(
            selection = selection,
            onDismiss = { selected = null },
            onOpenReport = { report ->
                selected = null
                onOpenReport(report)
            }
        )
    }
}

// ---------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------

private fun DrawScope.drawCoordinateGrid(p: MapProjection) {
    val lines = 6
    val color = Mesh.Line.Subtle.copy(alpha = 0.7f)
    for (i in 1 until lines) {
        val y = size.height * i / lines
        drawLine(color, Offset(0f, y), Offset(size.width, y), 1f)
        val x = size.width * i / lines
        drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f)
    }
}

private fun DrawScope.drawScaleBar(p: MapProjection) {
    val bar = p.scaleBar() ?: return
    val margin = 18f
    val y = size.height - margin
    val start = Offset(margin, y)
    val end = Offset(margin + bar.widthPx, y)
    drawLine(Mesh.Text.Secondary, start, end, 3f)
    drawLine(Mesh.Text.Secondary, start, Offset(start.x, y - 8f), 3f)
    drawLine(Mesh.Text.Secondary, end, Offset(end.x, y - 8f), 3f)
    drawCanvasText(bar.label, margin, y - 14f, 26f, Mesh.Text.Secondary.toArgb(), left = true)
}

/**
 * Lines from this device to nodes it is DIRECTLY connected to right now.
 *
 * Nothing else is drawn: the app knows its own peers and no one else's, so any
 * further line would be an invention.
 */
private fun DrawScope.drawConnections(
    p: MapProjection,
    data: MapData,
    nodes: List<MappedNode>
) {
    val me = data.myLocation ?: return
    val origin = p.toScreen(me)
    nodes.filter { it.directlyConnected && !it.isSelf }.forEach { node ->
        val target = p.toScreen(node.position)
        drawLine(
            color = Mesh.Signal.Live.copy(alpha = 0.45f),
            start = origin,
            end = target,
            strokeWidth = 2.5f
        )
    }
}

private fun DrawScope.drawNodes(p: MapProjection, nodes: List<MappedNode>, span: Double) {
    val showLabels = span <= OfflineMapRegion.DEFAULT_SPAN_DEG
    nodes.forEach { node ->
        val at = p.toScreen(node.position)
        if (!p.isVisible(at)) return@forEach
        val color = if (node.directlyConnected) Mesh.Signal.Live else Mesh.Text.Tertiary
        drawCircle(color.copy(alpha = 0.16f), 20f, at)
        drawCircle(Mesh.Surface.Raised, 11f, at)
        drawCircle(color, 11f, at, style = Stroke(width = 2.5f))
        if (showLabels) {
            drawCanvasText(node.nodeId, at.x, at.y + 30f, 24f, Mesh.Text.Secondary.toArgb())
        }
    }
}

/** Reports are drawn last among data layers so an SOS is never hidden by a node. */
private fun DrawScope.drawReports(p: MapProjection, reports: List<MappedReport>) {
    // Least urgent first, so critical ends up on top.
    reports.sortedByDescending { it.report.category.priority }.forEach { mapped ->
        val at = p.toScreen(mapped.position)
        if (!p.isVisible(at)) return@forEach
        val category = mapped.report.category
        val color = categoryColor(category)
        val critical = category == ReportCategory.CRITICAL

        val radius = if (critical) 16f else 13f
        drawCircle(color.copy(alpha = 0.20f), radius * 2.1f, at)
        drawCircle(color, radius, at)
        drawCircle(Mesh.Surface.Backdrop, radius * 0.42f, at)

        // Shape reinforces the colour: critical gets a ring, so the two most
        // urgent classes remain distinguishable without relying on hue.
        if (critical) {
            drawCircle(color, radius * 1.55f, at, style = Stroke(width = 2f))
        }
    }
}

private fun DrawScope.drawMyLocation(p: MapProjection, me: GeoPoint) {
    val at = p.toScreen(me)
    if (!p.isVisible(at)) return
    drawCircle(Mesh.Signal.Action.copy(alpha = 0.20f), 26f, at)
    drawCircle(Mesh.Signal.Action, 9f, at)
    drawCircle(Color.White, 9f, at, style = Stroke(width = 2.5f))
    drawCanvasText("YOU", at.x, at.y - 22f, 24f, Mesh.Signal.Action.toArgb())
}

private fun DrawScope.drawCanvasText(
    text: String,
    x: Float,
    y: Float,
    textSize: Float,
    argb: Int,
    left: Boolean = false
) {
    drawContext.canvas.nativeCanvas.drawText(
        text, x, y,
        android.graphics.Paint().apply {
            color = argb
            this.textSize = textSize
            textAlign = if (left) {
                android.graphics.Paint.Align.LEFT
            } else {
                android.graphics.Paint.Align.CENTER
            }
            isAntiAlias = true
        }
    )
}

// ---------------------------------------------------------------------
// Interaction
// ---------------------------------------------------------------------

sealed interface MapSelection {
    data class Report(val mapped: MappedReport) : MapSelection
    data class Node(val node: MappedNode) : MapSelection
}

/** Reports win ties — an emergency must always be reachable by tap. */
private fun hitTest(
    tap: Offset,
    p: MapProjection,
    reports: List<MappedReport>,
    nodes: List<MappedNode>,
    slop: Float
): MapSelection? {
    val report = reports.minByOrNull { distance(tap, p.toScreen(it.position)) }
    if (report != null && distance(tap, p.toScreen(report.position)) <= slop) {
        return MapSelection.Report(report)
    }
    val node = nodes.minByOrNull { distance(tap, p.toScreen(it.position)) }
    if (node != null && distance(tap, p.toScreen(node.position)) <= slop) {
        return MapSelection.Node(node)
    }
    return null
}

private fun distance(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

// ---------------------------------------------------------------------
// Overlay UI
// ---------------------------------------------------------------------

/** Real counts only, taken from what is actually plotted. */
@Composable
private fun StatusBar(
    state: MeshState,
    reports: List<MappedReport>,
    nodes: List<MappedNode>
) {
    val counts = remember(reports) {
        ReportCategory.entries.associateWith { c -> reports.count { it.report.category == c } }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mesh.Surface.Card, RoundedCornerShape(Mesh.Radius.md))
            .padding(horizontal = Mesh.Space.lg, vertical = Mesh.Space.md)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.xl)
    ) {
        StatusStat("Nodes", nodes.size.toString(), Mesh.Signal.Live)
        StatusStat(
            "Critical",
            (counts[ReportCategory.CRITICAL] ?: 0).toString(),
            Mesh.Signal.Emergency
        )
        StatusStat(
            "Medical",
            (counts[ReportCategory.MEDICAL] ?: 0).toString(),
            Mesh.Signal.Warning
        )
        StatusStat(
            "Supply",
            (counts[ReportCategory.SUPPLY] ?: 0).toString(),
            Mesh.Signal.Action
        )
        StatusStat("Connected", state.connectedCount.toString(), Mesh.Text.Secondary)
    }
}

@Composable
private fun StatusStat(label: String, value: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = Mesh.Mono,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = Mesh.Text.Tertiary)
    }
}

@Composable
private fun FilterBar(selected: MapFilter, onSelect: (MapFilter) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm)
    ) {
        MapFilter.entries.forEach { entry ->
            val active = entry == selected
            val color = when (entry) {
                MapFilter.CRITICAL -> Mesh.Signal.Emergency
                MapFilter.MEDICAL -> Mesh.Signal.Warning
                MapFilter.WARNING -> Mesh.Signal.Live
                MapFilter.SUPPLY -> Mesh.Signal.Action
                MapFilter.SAFE -> Mesh.Signal.Ok
                MapFilter.ALL -> Mesh.Signal.Live
            }
            Chip(entry.label, active, color) { onSelect(entry) }
        }
    }
}

@Composable
private fun WindowBar(selected: MapWindow, onSelect: (MapWindow) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm)
    ) {
        MapWindow.entries.forEach { entry ->
            Chip(entry.label, entry == selected, Mesh.Text.Secondary) { onSelect(entry) }
        }
    }
}

@Composable
private fun Chip(label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Mesh.Radius.md)
    Box(
        modifier = Modifier
            .heightIn(min = Mesh.TouchTarget)
            .background(if (active) color.copy(alpha = 0.16f) else Mesh.Surface.Card, shape)
            .border(1.dp, if (active) color else Mesh.Line.Subtle, shape)
            .selectable(selected = active, onClick = onClick)
            .padding(horizontal = Mesh.Space.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) Mesh.Text.Primary else Mesh.Text.Secondary
        )
    }
}

/** Toggling a layer hides the overlay only — no underlying data is discarded. */
@Composable
private fun LayerControls(layers: MapLayers, onChange: (MapLayers) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.sm)) {
        SectionLabel("Map layers")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm)
        ) {
            LayerToggle("Emergencies", layers.emergencies) {
                onChange(layers.copy(emergencies = it))
            }
            LayerToggle("Safe", layers.safeReports) { onChange(layers.copy(safeReports = it)) }
            LayerToggle("Nodes", layers.nodes) { onChange(layers.copy(nodes = it)) }
            LayerToggle("Connections", layers.connections) {
                onChange(layers.copy(connections = it))
            }
            LayerToggle("My location", layers.myLocation) {
                onChange(layers.copy(myLocation = it))
            }
        }
    }
}

@Composable
private fun LayerToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(Mesh.Radius.md)
    Row(
        modifier = Modifier
            .heightIn(min = Mesh.TouchTarget)
            .background(Mesh.Surface.Card, shape)
            .border(1.dp, if (checked) Mesh.Signal.Live else Mesh.Line.Subtle, shape)
            .toggleable(value = checked, onValueChange = onChange)
            .padding(horizontal = Mesh.Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Filled.Info,
            contentDescription = null,
            tint = if (checked) Mesh.Signal.Live else Mesh.Text.Tertiary,
            modifier = Modifier.size(15.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) Mesh.Text.Primary else Mesh.Text.Tertiary
        )
    }
}

@Composable
private fun MapButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(Mesh.TouchTarget)
            .background(Mesh.Surface.Card, CircleShape)
            .border(1.dp, Mesh.Line.Strong, CircleShape)
            .selectable(selected = false, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Mesh.Signal.Live, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Mesh.Surface.Card, RoundedCornerShape(Mesh.Radius.md))
            .border(1.dp, Mesh.Line.Subtle, RoundedCornerShape(Mesh.Radius.md))
            .padding(Mesh.Space.md),
        verticalArrangement = Arrangement.spacedBy(Mesh.Space.sm)
    ) {
        SectionLabel("Legend")
        ReportCategory.entries.forEach { category ->
            LegendRow(categoryColor(category), category.label, ring = category == ReportCategory.CRITICAL)
        }
        LegendRow(Mesh.Signal.Live, "Mesh node", hollow = true)
        LegendRow(Mesh.Signal.Action, "You")
    }
}

@Composable
private fun LegendRow(color: Color, label: String, ring: Boolean = false, hollow: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Mesh.Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .background(if (hollow) Color.Transparent else color, CircleShape)
                .border(if (hollow || ring) 2.dp else 0.dp, color, CircleShape)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = Mesh.Text.Secondary)
    }
}

@Composable
private fun EmptyMapOverlay(connectedCount: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Mesh.Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Mesh.Signal.Ok,
            modifier = Modifier.size(30.dp)
        )
        Text(
            "No active emergencies",
            style = MaterialTheme.typography.titleMedium,
            color = Mesh.Text.Primary,
            modifier = Modifier.padding(top = Mesh.Space.md)
        )
        Text(
            "No reports with known locations have been received.",
            style = MaterialTheme.typography.bodySmall,
            color = Mesh.Text.Tertiary,
            modifier = Modifier.padding(top = Mesh.Space.xs)
        )
        Text(
            "Connected nodes: $connectedCount",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = Mesh.Mono,
            color = Mesh.Text.Tertiary,
            modifier = Modifier.padding(top = Mesh.Space.md)
        )
    }
}

/** Marker detail. Reports can jump into the Command Center record. */
@Composable
private fun MapDetailDialog(
    selection: MapSelection,
    onDismiss: () -> Unit,
    onOpenReport: (EmergencyReport) -> Unit
) {
    when (selection) {
        is MapSelection.Report -> {
            val report = selection.mapped.report
            val accent = categoryColor(report.category)
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = Mesh.Surface.Raised,
                icon = {
                    Icon(categoryIcon(report.category), contentDescription = null, tint = accent)
                },
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
                    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.sm)) {
                        InfoLine("Sender", report.senderId, mono = true)
                        InfoLine(
                            "Position",
                            "%.6f, %.6f".format(
                                selection.mapped.position.latitude,
                                selection.mapped.position.longitude
                            ),
                            mono = true
                        )
                        // Hop count is known; the exact relay path is NOT, so it
                        // is reported as a count and never drawn as a route.
                        report.hops?.let {
                            InfoLine("Reach", "Received through mesh — $it hop(s)")
                        }
                        InfoLine("Received", relativeTime(report.receivedAt))
                        InfoLine("Status", report.status.label)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onOpenReport(report) }) {
                        Text("View details", color = Mesh.Signal.Live, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = Mesh.Text.Secondary)
                    }
                }
            )
        }

        is MapSelection.Node -> {
            val node = selection.node
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = Mesh.Surface.Raised,
                icon = {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = if (node.directlyConnected) Mesh.Signal.Live else Mesh.Text.Tertiary
                    )
                },
                title = {
                    Text(
                        node.nodeId,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = Mesh.Mono,
                        color = Mesh.Text.Primary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Mesh.Space.sm)) {
                        InfoLine(
                            "Status",
                            when {
                                node.isSelf -> "This device"
                                node.directlyConnected -> "Directly connected"
                                else -> "Not directly connected"
                            }
                        )
                        InfoLine("Role", "Mesh node")
                        InfoLine(
                            "Last reported position",
                            "%.6f, %.6f".format(node.position.latitude, node.position.longitude),
                            mono = true
                        )
                        InfoLine("Reported", relativeTime(node.lastSeen))
                        Text(
                            "Position comes from the last report this node sent. " +
                                "The mesh does not broadcast live node GPS, so the node " +
                                "may have moved since.",
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
    }
}

@Composable
private fun InfoLine(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Mesh.Text.Tertiary)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (mono) Mesh.Mono else null,
            color = Mesh.Text.Primary
        )
    }
}
