package com.disastermesh.app.map

/**
 * The single place the demonstration area is configured.
 *
 * Nothing else in the app hardcodes a location — change these values and the map
 * moves. [BaseLayer] documents the slot where bundled street data can be added
 * later without touching the map screen.
 */
object OfflineMapRegion {

    /** Where the map opens when there is nothing else to centre on. */
    const val DEFAULT_CENTER_LAT = 12.9716
    const val DEFAULT_CENTER_LON = 77.5946

    /** Half-height of the initial viewport, in degrees of latitude. */
    const val DEFAULT_SPAN_DEG = 0.010

    /** Zoom guards, expressed as latitude span. Prevents unusable zoom levels. */
    const val MIN_SPAN_DEG = 0.0004   // ≈ 45 m tall
    const val MAX_SPAN_DEG = 2.0      // ≈ 220 km tall

    /** Padding applied by "Fit all" so markers are never flush to the edge. */
    const val FIT_PADDING_FACTOR = 1.35

    /**
     * Base-map layers this build can draw underneath the live data.
     *
     * Only [COORDINATE_GRID] is implemented. It needs no downloaded data and so
     * is offline by construction. Street-level imagery would require a tile or
     * vector archive bundled into the APK; the renderer is written so that layer
     * can be added beneath the overlays without changing any overlay code.
     */
    enum class BaseLayer {
        /** A measured coordinate grid with a scale bar. Always available. */
        COORDINATE_GRID
    }

    val baseLayer: BaseLayer = BaseLayer.COORDINATE_GRID
}
