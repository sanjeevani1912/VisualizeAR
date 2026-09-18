package com.example.dhruvar.domain.spatial

import androidx.compose.ui.geometry.Offset

/**
 * Information required to render a dynamic metric scale indicator bar on the canvas.
 *
 * @param distanceMeters The physical real-world distance represented by the bar.
 * @param widthPx The width of the scale bar in screen pixels.
 * @param label Human-readable label (e.g., "5m", "10m").
 */
data class ScaleBarInfo(
    val distanceMeters: Float,
    val widthPx: Float,
    val label: String
)

/**
 * Canonical coordinate transformation engine between real-world metric coordinates and
 * screen canvas pixel space.
 *
 * COORDINATE SYSTEM CONVENTION (Right-Handed Top-Down Cartographic System):
 * - Logical Origin: (0.0, 0.0) meters is anchored at the primary spatial datum.
 * - +X axis: Positive East (rightwards on screen).
 * - +Z axis: Positive North (upwards on screen).
 * - Screen Pixels: (0, 0) is top-left, increasing X rightwards, increasing Y downwards.
 *
 * Therefore:
 * - Screen X = OriginScreenX + (World X * pixelsPerMeter)
 * - Screen Y = OriginScreenY - (World Z * pixelsPerMeter)   [note the minus sign for upward +Z]
 *
 * @param viewportWidth Width of canvas area in screen pixels.
 * @param viewportHeight Height of canvas area in screen pixels.
 * @param panOffset Pan translation in screen pixels.
 * @param zoom Scaling factor relative to [basePixelsPerMeter] (clamped between 0.25f and 5.0f).
 * @param basePixelsPerMeter Baseline screen pixels per real-world meter (default 28.0f).
 */
data class CoordinateTransformer(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val panOffset: Offset = Offset.Zero,
    val zoom: Float = 1.0f,
    val basePixelsPerMeter: Float = 28.0f
) {
    /**
     * Effective screen pixels per 1 real-world meter under current zoom level.
     */
    val pixelsPerMeter: Float = basePixelsPerMeter * zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /**
     * Screen pixel coordinate corresponding to logical origin (X=0, Z=0).
     */
    val originScreenX: Float = (viewportWidth / 2f) + panOffset.x
    val originScreenY: Float = (viewportHeight / 2f) + panOffset.y

    /**
     * Converts a logical real-world coordinate (meters) to screen canvas pixels.
     *
     * @param worldX Lateral horizontal offset in meters (East = +X, West = -X).
     * @param worldZ Longitudinal offset in meters (North = +Z, South = -Z).
     * @return Screen pixel [Offset].
     */
    fun worldToCanvas(worldX: Float, worldZ: Float): Offset {
        val screenX = originScreenX + (worldX * pixelsPerMeter)
        val screenY = originScreenY - (worldZ * pixelsPerMeter)
        return Offset(screenX, screenY)
    }

    /**
     * Converts a screen canvas pixel offset to logical real-world coordinates (meters).
     *
     * @param canvasOffset Screen position in pixels.
     * @return Pair of (worldX, worldZ) in meters.
     */
    fun canvasToWorld(canvasOffset: Offset): Pair<Float, Float> {
        val worldX = (canvasOffset.x - originScreenX) / pixelsPerMeter
        val worldZ = (originScreenY - canvasOffset.y) / pixelsPerMeter
        return Pair(worldX, worldZ)
    }

    /**
     * Converts physical meters to screen pixels under current zoom.
     */
    fun metersToPixels(meters: Float): Float = meters * pixelsPerMeter

    /**
     * Converts screen pixels to physical meters under current zoom.
     */
    fun pixelsToMeters(pixels: Float): Float = pixels / pixelsPerMeter

    /**
     * Calculates an optimal round metric scale indicator bar for the current zoom level.
     *
     * @param targetMaxBarWidthPx Maximum allowable bar width in pixels.
     */
    fun calculateDynamicScaleBar(targetMaxBarWidthPx: Float = 140f): ScaleBarInfo {
        val candidateDistances = listOf(0.5f, 1f, 2f, 5f, 10f, 20f, 50f, 100f, 200f)
        val distance = candidateDistances
            .filter { it * pixelsPerMeter <= targetMaxBarWidthPx }
            .maxOrNull() ?: candidateDistances.first()

        val widthPx = distance * pixelsPerMeter
        val label = if (distance >= 1f) "${distance.toInt()}m" else "${(distance * 100).toInt()}cm"
        return ScaleBarInfo(
            distanceMeters = distance,
            widthPx = widthPx,
            label = label
        )
    }

    companion object {
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 5.0f
        const val DEFAULT_ZOOM = 1.0f
    }
}
