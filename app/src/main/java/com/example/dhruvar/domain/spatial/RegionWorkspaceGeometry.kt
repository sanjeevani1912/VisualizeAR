package com.example.dhruvar.domain.spatial

import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.model.PlanningSetupStep
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** One clipped grid segment in WGS84. [points] has at least two positions. */
data class GridPolyline(
    val points: List<LatLng>,
    val major: Boolean
)

/**
 * Visual-only metric grid and camera helpers for a confirmed planning region.
 * Not used for snapping or persisted layout math.
 */
object RegionWorkspaceGeometry {

    const val MAX_GRID_LINES_PER_AXIS = 40
    const val PAD_FRACTION = 0.08
    const val MIN_PAD_METERS = 20.0
    /** ~70–85% of the viewport should be the region when fitting. */
    const val FIT_FILL_FRACTION = 0.78

    fun showsWorkspace(step: PlanningSetupStep, bounds: GeoBounds?): Boolean =
        bounds != null &&
            (step == PlanningSetupStep.SET_ORIGIN || step == PlanningSetupStep.PLACE_ASSETS)

    fun gridSpacingMeters(bounds: GeoBounds): Double {
        val shortest = RegionDraftGeometry.sideLengthsMeters(bounds).let { min(it.first, it.second) }
        return when {
            shortest < 100.0 -> 10.0
            shortest < 500.0 -> 25.0
            shortest < 2_000.0 -> 50.0
            else -> 100.0
        }
    }

    fun centroid(bounds: GeoBounds): LatLng = LatLng(
        latitude = (bounds.northLatitude + bounds.southLatitude) / 2.0,
        longitude = (bounds.eastLongitude + bounds.westLongitude) / 2.0
    )

    /**
     * Web-Mercator-style zoom so [spanDegrees] of longitude (or latitude) fills
     * [fillFraction] of a [viewportPixels]-wide view.
     */
    fun zoomToFitSpan(
        spanDegrees: Double,
        viewportPixels: Double = 1080.0,
        fillFraction: Double = FIT_FILL_FRACTION
    ): Double {
        val span = spanDegrees.coerceAtLeast(1e-9)
        val twoZ = fillFraction * 360.0 * viewportPixels / (256.0 * span)
        return ln(twoZ.coerceAtLeast(1e-9)) / ln(2.0)
    }

    fun expandBounds(
        bounds: GeoBounds,
        fraction: Double = PAD_FRACTION,
        minPadMeters: Double = MIN_PAD_METERS
    ): GeoBounds {
        val (width, height) = RegionDraftGeometry.sideLengthsMeters(bounds)
        val padW = max(width * fraction, minPadMeters)
        val padH = max(height * fraction, minPadMeters)
        val midLat = (bounds.northLatitude + bounds.southLatitude) / 2.0
        val midLng = (bounds.eastLongitude + bounds.westLongitude) / 2.0
        val south = GeographicCoordinateConverter.metersOffsetToLatLng(
            bounds.southLatitude, midLng, 0f, 0f, (-padH).toFloat()
        )
        val north = GeographicCoordinateConverter.metersOffsetToLatLng(
            bounds.northLatitude, midLng, 0f, 0f, padH.toFloat()
        )
        val west = GeographicCoordinateConverter.metersOffsetToLatLng(
            midLat, bounds.westLongitude, 0f, (-padW).toFloat(), 0f
        )
        val east = GeographicCoordinateConverter.metersOffsetToLatLng(
            midLat, bounds.eastLongitude, 0f, padW.toFloat(), 0f
        )
        return GeoBounds.createOrNull(
            northLatitude = north.latitude.coerceIn(-90.0, 90.0),
            southLatitude = south.latitude.coerceIn(-90.0, 90.0),
            eastLongitude = east.longitude,
            westLongitude = west.longitude
        ) ?: bounds
    }

    /**
     * Grid lines clipped to [bounds].
     *
     * When [originLatLng] is null, the south-west corner is the datum and heading is true north.
     * When non-null, lines follow planning +X / +Z for [headingDegrees] (null heading = 0).
     */
    fun gridLines(
        bounds: GeoBounds,
        spacing: Double = gridSpacingMeters(bounds),
        headingDegrees: Float? = null,
        originLatLng: Pair<Double, Double>? = null
    ): List<GridPolyline> {
        val origin = if (originLatLng != null) {
            LatLng(originLatLng.first, originLatLng.second)
        } else {
            LatLng(bounds.southLatitude, bounds.westLongitude)
        }
        val heading = headingDegrees ?: 0f
        val (width, height) = RegionDraftGeometry.sideLengthsMeters(bounds)
        val longest = max(width, height)
        var step = spacing.coerceAtLeast(1.0)
        if (longest / step > MAX_GRID_LINES_PER_AXIS) {
            step = longest / MAX_GRID_LINES_PER_AXIS.toDouble()
        }

        val corners = listOf(
            LatLng(bounds.northLatitude, bounds.westLongitude),
            LatLng(bounds.northLatitude, bounds.eastLongitude),
            LatLng(bounds.southLatitude, bounds.eastLongitude),
            LatLng(bounds.southLatitude, bounds.westLongitude)
        ).map { corner ->
            GeographicCoordinateConverter.latLngToMetersOffset(
                origin.latitude,
                origin.longitude,
                heading,
                corner.latitude,
                corner.longitude
            )
        }
        val minX = corners.minOf { it.first.toDouble() }
        val maxX = corners.maxOf { it.first.toDouble() }
        val minZ = corners.minOf { it.second.toDouble() }
        val maxZ = corners.maxOf { it.second.toDouble() }

        val lines = mutableListOf<GridPolyline>()
        for (x in snappedValues(minX, maxX, step)) {
            val a = GeographicCoordinateConverter.metersOffsetToLatLng(
                origin.latitude, origin.longitude, heading, x.toFloat(), minZ.toFloat()
            )
            val b = GeographicCoordinateConverter.metersOffsetToLatLng(
                origin.latitude, origin.longitude, heading, x.toFloat(), maxZ.toFloat()
            )
            clipToBounds(a, b, bounds)?.let { pts ->
                lines += GridPolyline(pts, isMajor(x, step))
            }
        }
        for (z in snappedValues(minZ, maxZ, step)) {
            val a = GeographicCoordinateConverter.metersOffsetToLatLng(
                origin.latitude, origin.longitude, heading, minX.toFloat(), z.toFloat()
            )
            val b = GeographicCoordinateConverter.metersOffsetToLatLng(
                origin.latitude, origin.longitude, heading, maxX.toFloat(), z.toFloat()
            )
            clipToBounds(a, b, bounds)?.let { pts ->
                lines += GridPolyline(pts, isMajor(z, step))
            }
        }
        return lines
    }

    private fun isMajor(valueMeters: Double, spacing: Double): Boolean {
        val index = kotlin.math.round(valueMeters / spacing).toInt()
        return index % 5 == 0
    }

    private fun snappedValues(min: Double, max: Double, spacing: Double): List<Double> {
        val start = floor(min / spacing) * spacing
        val values = ArrayList<Double>(MAX_GRID_LINES_PER_AXIS + 2)
        var v = start
        var guard = 0
        while (v <= max + spacing * 0.01 && guard <= MAX_GRID_LINES_PER_AXIS + 4) {
            if (v >= min - spacing * 0.01) values += v
            v += spacing
            guard++
        }
        return values
    }

    /** Liang–Barsky clip of a segment to an axis-aligned lat/lng box. */
    internal fun clipToBounds(p1: LatLng, p2: LatLng, bounds: GeoBounds): List<LatLng>? {
        var t0 = 0.0
        var t1 = 1.0
        val dx = p2.longitude - p1.longitude
        val dy = p2.latitude - p1.latitude

        fun clip(p: Double, q: Double): Boolean {
            if (abs(p) < 1e-18) return q >= -1e-12
            val r = q / p
            if (p < 0) {
                if (r > t1) return false
                if (r > t0) t0 = r
            } else {
                if (r < t0) return false
                if (r < t1) t1 = r
            }
            return true
        }

        if (!clip(-dx, p1.longitude - bounds.westLongitude)) return null
        if (!clip(dx, bounds.eastLongitude - p1.longitude)) return null
        if (!clip(-dy, p1.latitude - bounds.southLatitude)) return null
        if (!clip(dy, bounds.northLatitude - p1.latitude)) return null
        if (t1 < t0) return null

        val a = LatLng(
            latitude = (p1.latitude + t0 * dy).coerceIn(-90.0, 90.0),
            longitude = p1.longitude + t0 * dx
        )
        val b = LatLng(
            latitude = (p1.latitude + t1 * dy).coerceIn(-90.0, 90.0),
            longitude = p1.longitude + t1 * dx
        )
        val dist = GeographicCoordinateConverter.distanceMeters(a, b)
        if (dist < 0.5) return null
        return listOf(a, b)
    }
}
