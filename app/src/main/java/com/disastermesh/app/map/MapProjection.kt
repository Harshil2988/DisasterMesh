package com.disastermesh.app.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Maps latitude/longitude onto the canvas.
 *
 * Equirectangular with a cosine correction on longitude, which keeps distances
 * and shapes honest across the few kilometres a mesh can actually span. It needs
 * no tiles, no downloads and no network — the projection is arithmetic.
 */
class MapProjection(
    val centerLat: Double,
    val centerLon: Double,
    /** Half the visible height, in degrees of latitude. */
    val spanLat: Double,
    val size: Size
) {
    private val latScale = cos(Math.toRadians(centerLat.coerceIn(-85.0, 85.0)))
        .coerceAtLeast(0.05)

    /** Half the visible width in degrees of longitude, corrected for latitude. */
    private val spanLon: Double =
        if (size.height <= 0f) spanLat
        else spanLat * (size.width / size.height) / latScale

    fun toScreen(point: GeoPoint): Offset {
        val x = size.width / 2f +
            ((point.longitude - centerLon) / spanLon * (size.width / 2f)).toFloat()
        // Screen y grows downward; latitude grows upward.
        val y = size.height / 2f -
            ((point.latitude - centerLat) / spanLat * (size.height / 2f)).toFloat()
        return Offset(x, y)
    }

    /** Screen delta (pixels) converted back into a centre shift. */
    fun panBy(dx: Float, dy: Float): Pair<Double, Double> {
        if (size.width <= 0f || size.height <= 0f) return centerLat to centerLon
        val dLon = -(dx / (size.width / 2f)) * spanLon
        val dLat = (dy / (size.height / 2f)) * spanLat
        return (centerLat + dLat).coerceIn(-85.0, 85.0) to (centerLon + dLon).coerceIn(-180.0, 180.0)
    }

    /** True when a projected point is inside the canvas, with a small margin. */
    fun isVisible(offset: Offset, margin: Float = 64f): Boolean =
        offset.x >= -margin && offset.x <= size.width + margin &&
            offset.y >= -margin && offset.y <= size.height + margin

    /**
     * A round distance for the scale bar, plus its width in pixels.
     * Returns null when the canvas has no size yet.
     */
    fun scaleBar(): ScaleBar? {
        if (size.width <= 0f) return null
        val metresPerDegreeLat = 111_320.0
        val visibleMetres = spanLat * 2 * metresPerDegreeLat
        if (visibleMetres <= 0 || !visibleMetres.isFinite()) return null

        val target = visibleMetres / 4.0
        val nice = NICE_STEPS.minByOrNull { abs(it - target) } ?: return null
        val fraction = nice / visibleMetres
        val pixels = (fraction * size.height).toFloat()
        if (!pixels.isFinite() || pixels <= 1f) return null

        val label = if (nice >= 1000) "${(nice / 1000).roundToInt()} km" else "${nice.roundToInt()} m"
        return ScaleBar(label, pixels.coerceAtMost(size.width * 0.5f))
    }

    companion object {
        private val NICE_STEPS = listOf(
            10.0, 25.0, 50.0, 100.0, 250.0, 500.0,
            1_000.0, 2_500.0, 5_000.0, 10_000.0, 25_000.0, 50_000.0
        )

        /**
         * A viewport containing every supplied point, clamped to sane zoom.
         * Returns null when there is nothing to fit.
         */
        fun fitting(points: List<GeoPoint>): Triple<Double, Double, Double>? {
            if (points.isEmpty()) return null
            val minLat = points.minOf { it.latitude }
            val maxLat = points.maxOf { it.latitude }
            val minLon = points.minOf { it.longitude }
            val maxLon = points.maxOf { it.longitude }

            val centerLat = (minLat + maxLat) / 2
            val centerLon = (minLon + maxLon) / 2

            val latScale = cos(Math.toRadians(centerLat.coerceIn(-85.0, 85.0)))
                .coerceAtLeast(0.05)
            val neededLat = (maxLat - minLat) / 2
            val neededFromLon = ((maxLon - minLon) / 2) * latScale

            val span = max(neededLat, neededFromLon) * OfflineMapRegion.FIT_PADDING_FACTOR
            val clamped = min(
                OfflineMapRegion.MAX_SPAN_DEG,
                max(OfflineMapRegion.MIN_SPAN_DEG, span)
            )
            return Triple(centerLat, centerLon, clamped)
        }
    }
}

data class ScaleBar(val label: String, val widthPx: Float)
