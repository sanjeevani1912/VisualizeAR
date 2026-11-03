package com.example.dhruvar.domain.spatial

import com.example.dhruvar.domain.model.GeoBounds
import kotlin.math.abs

/**
 * Turns two dragged corners into committable [GeoBounds].
 *
 * Pure, so the minimum-size rule can be asserted without a map or a touchscreen.
 */
object RegionDraftGeometry {

    /**
     * Shortest usable side. A region narrower than this is almost certainly a stray tap rather
     * than an intended box, and it would reject asset placement for no good reason.
     */
    const val MIN_SIDE_METERS = 25.0

    /** Guards against a box so large that "region" stops meaning anything. */
    const val MAX_SIDE_METERS = 500_000.0

    /** Side of the square region auto-created around the operator's own position. */
    const val AUTO_REGION_SIDE_METERS = 250.0

    private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

    /** Square of [sideMeters] centered on [latitude]/[longitude]; null near the poles or antimeridian. */
    fun squareAround(
        latitude: Double,
        longitude: Double,
        sideMeters: Double = AUTO_REGION_SIDE_METERS
    ): GeoBounds? {
        val half = sideMeters / 2.0
        val dLat = half / METERS_PER_DEGREE_LATITUDE
        val dLng = half / (METERS_PER_DEGREE_LATITUDE * kotlin.math.cos(Math.toRadians(latitude)))
        return GeoBounds.createOrNull(
            northLatitude = latitude + dLat,
            southLatitude = latitude - dLat,
            eastLongitude = longitude + dLng,
            westLongitude = longitude - dLng
        )
    }

    /** Corner order does not matter; the box is normalised. */
    fun boundsBetween(
        firstLatitude: Double,
        firstLongitude: Double,
        secondLatitude: Double,
        secondLongitude: Double
    ): GeoBounds? = GeoBounds.createOrNull(
        northLatitude = maxOf(firstLatitude, secondLatitude),
        southLatitude = minOf(firstLatitude, secondLatitude),
        eastLongitude = maxOf(firstLongitude, secondLongitude),
        westLongitude = minOf(firstLongitude, secondLongitude)
    )

    /** East–west and north–south extent in meters, measured across the box's middle. */
    fun sideLengthsMeters(bounds: GeoBounds): Pair<Double, Double> {
        val midLatitude = (bounds.northLatitude + bounds.southLatitude) / 2.0
        val midLongitude = (bounds.eastLongitude + bounds.westLongitude) / 2.0
        val width = GeographicCoordinateConverter.distanceMeters(
            LatLng(midLatitude, bounds.westLongitude),
            LatLng(midLatitude, bounds.eastLongitude)
        )
        val height = GeographicCoordinateConverter.distanceMeters(
            LatLng(bounds.southLatitude, midLongitude),
            LatLng(bounds.northLatitude, midLongitude)
        )
        return abs(width) to abs(height)
    }

    /** Null when [bounds] is committable, otherwise a message to show the operator. */
    fun rejectionReason(bounds: GeoBounds?): String? {
        if (bounds == null) {
            return "That box is not a valid region — try again"
        }
        val (width, height) = sideLengthsMeters(bounds)
        if (width < MIN_SIDE_METERS || height < MIN_SIDE_METERS) {
            return "Box too small — drag at least ${MIN_SIDE_METERS.toInt()} m on each side"
        }
        if (width > MAX_SIDE_METERS || height > MAX_SIDE_METERS) {
            return "Box too large to be a site region"
        }
        return null
    }

    /** Short "420 × 310 m" style label for the region strip. */
    fun sizeLabel(bounds: GeoBounds): String {
        val (width, height) = sideLengthsMeters(bounds)
        return "${format(width)} × ${format(height)}"
    }

    private fun format(meters: Double): String = if (meters >= 1_000) {
        String.format(java.util.Locale.US, "%.1f km", meters / 1_000)
    } else {
        "${meters.toInt()} m"
    }
}
