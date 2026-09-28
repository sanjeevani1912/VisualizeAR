package com.example.dhruvar.domain.spatial

import com.example.dhruvar.domain.model.Anchor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * WGS84 geographic position in degrees.
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude.isFinite() && longitude.isFinite()) {
            "LatLng components must be finite"
        }
        require(latitude in -90.0..90.0) {
            "Latitude must be in [-90, 90], was $latitude"
        }
    }
}

/**
 * Thrown by [GeographicCoordinateConverter] Anchor overloads when the anchor has no usable WGS84 fix.
 */
class MissingGeographicFixException(message: String) : IllegalStateException(message)

/**
 * Pure Kotlin conversion between WGS84 lat/lng and DhruvAR planning meters `(x, z)`.
 *
 * ## Planning frame (matches app / ARCore ground convention)
 * - **+X** = right / east of the plan when heading is 0
 * - **+Y** = up (unused for placement)
 * - **+Z** = forward / “north of plan” when heading is 0
 *
 * ## Heading convention
 * [anchorHeadingDegrees] is clockwise azimuth from geographic north to the planning **+Z** axis
 * (same sense as compass/azimuth elsewhere in the app).
 *
 * Worked examples (anchor at an arbitrary fix):
 * - **heading = 0°**: planning +Z → geographic north; planning +X → geographic east.
 *   A point ~10 m east → primarily **+X**; ~25 m north → primarily **+Z**.
 * - **heading = 90°**: planning +Z → geographic east; planning +X → geographic south.
 *   The same geographic east offset → primarily **+Z**.
 *
 * ## Approximation
 * Lat/lng deltas use a local equirectangular (local tangent) projection with WGS84 mean
 * Earth radius [WGS84_MEAN_RADIUS_METERS]. Typical horizontal error is well under 0.25 m
 * for offsets ≤ ~500 m at mid-latitudes; error grows toward the poles, across the
 * antimeridian (handled via longitude wrap), and beyond ~1–2 km field scale.
 *
 * ```
 * Geographic EN (east, north) ──rotate by heading──► Planning (x, z)
 *
 *   heading = 0:          heading = 90° (CW):
 *        +Z (N)                 +Z (E)
 *         ↑                      ↑
 *         │                      │
 *   +X ───┼───            +X ────┼───  (+X = S)
 *      (E)│                      │
 * ```
 */
object GeographicCoordinateConverter {

    /** WGS84 arithmetic mean radius (IUGG), meters. */
    const val WGS84_MEAN_RADIUS_METERS: Double = 6_371_008.7714

    private const val DEG_TO_RAD = Math.PI / 180.0
    private const val RAD_TO_DEG = 180.0 / Math.PI

    /**
     * Converts a geographic target to planning meters relative to the geographic anchor.
     *
     * @return `(xMeters, zMeters)` in the planning frame described in the class KDoc.
     * @throws IllegalArgumentException if any input is non-finite or latitude is out of range.
     */
    fun latLngToMetersOffset(
        anchorLatitude: Double,
        anchorLongitude: Double,
        anchorHeadingDegrees: Float,
        targetLatitude: Double,
        targetLongitude: Double
    ): Pair<Float, Float> {
        requireFiniteLatLng(anchorLatitude, anchorLongitude, "anchor")
        requireFiniteLatLng(targetLatitude, targetLongitude, "target")
        require(anchorHeadingDegrees.isFinite()) { "anchorHeadingDegrees must be finite" }

        val (east, north) = latLngDeltaToEastNorthMeters(
            anchorLatitude,
            anchorLongitude,
            targetLatitude,
            targetLongitude
        )
        return eastNorthToPlanning(east, north, anchorHeadingDegrees)
    }

    /**
     * Inverse of [latLngToMetersOffset]: planning meters → WGS84 [LatLng].
     */
    fun metersOffsetToLatLng(
        anchorLatitude: Double,
        anchorLongitude: Double,
        anchorHeadingDegrees: Float,
        xMeters: Float,
        zMeters: Float
    ): LatLng {
        requireFiniteLatLng(anchorLatitude, anchorLongitude, "anchor")
        require(anchorHeadingDegrees.isFinite()) { "anchorHeadingDegrees must be finite" }
        require(xMeters.isFinite() && zMeters.isFinite()) { "xMeters and zMeters must be finite" }

        val (east, north) = planningToEastNorth(xMeters.toDouble(), zMeters.toDouble(), anchorHeadingDegrees)
        return eastNorthToLatLng(anchorLatitude, anchorLongitude, east, north)
    }

    /**
     * Great-circle distance in meters (haversine) between two WGS84 positions.
     */
    fun distanceMeters(a: LatLng, b: LatLng): Double {
        val lat1 = a.latitude * DEG_TO_RAD
        val lat2 = b.latitude * DEG_TO_RAD
        val dLat = (b.latitude - a.latitude) * DEG_TO_RAD
        val dLng = wrapLongitudeDeltaDegrees(b.longitude - a.longitude) * DEG_TO_RAD

        val sinDLat = sin(dLat / 2.0)
        val sinDLng = sin(dLng / 2.0)
        val h = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLng * sinDLng
        return 2.0 * WGS84_MEAN_RADIUS_METERS * atan2(sqrt(h), sqrt((1.0 - h).coerceAtLeast(0.0)))
    }

    /**
     * Initial bearing from [from] to [to], degrees clockwise from geographic north in `[0, 360)`.
     */
    fun bearingDegrees(from: LatLng, to: LatLng): Float {
        val lat1 = from.latitude * DEG_TO_RAD
        val lat2 = to.latitude * DEG_TO_RAD
        val dLng = wrapLongitudeDeltaDegrees(to.longitude - from.longitude) * DEG_TO_RAD

        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        val bearing = atan2(y, x) * RAD_TO_DEG
        return normalizeDegrees(bearing.toFloat())
    }

    /**
     * [latLngToMetersOffset] using [Anchor] geographic fields.
     *
     * **Policy:** throws [MissingGeographicFixException] when [Anchor.hasGeographicFix] is false.
     * Null [Anchor.headingDegrees] is treated as `0f` (planning +Z = geographic north).
     */
    fun latLngToMetersOffset(anchor: Anchor, target: LatLng): Pair<Float, Float> {
        requireGeographicFix(anchor)
        return latLngToMetersOffset(
            anchorLatitude = anchor.latitude!!,
            anchorLongitude = anchor.longitude!!,
            anchorHeadingDegrees = anchor.headingDegrees ?: 0f,
            targetLatitude = target.latitude,
            targetLongitude = target.longitude
        )
    }

    /**
     * [metersOffsetToLatLng] using [Anchor] geographic fields.
     *
     * **Policy:** throws [MissingGeographicFixException] when [Anchor.hasGeographicFix] is false.
     * Null [Anchor.headingDegrees] is treated as `0f`.
     */
    fun metersOffsetToLatLng(anchor: Anchor, xMeters: Float, zMeters: Float): LatLng {
        requireGeographicFix(anchor)
        return metersOffsetToLatLng(
            anchorLatitude = anchor.latitude!!,
            anchorLongitude = anchor.longitude!!,
            anchorHeadingDegrees = anchor.headingDegrees ?: 0f,
            xMeters = xMeters,
            zMeters = zMeters
        )
    }

    // ── Internal math ────────────────────────────────────────────────────────

    /**
     * Equirectangular projection of a lat/lng delta into local east/north meters
     * about [anchorLatitude]/[anchorLongitude].
     */
    internal fun latLngDeltaToEastNorthMeters(
        anchorLatitude: Double,
        anchorLongitude: Double,
        targetLatitude: Double,
        targetLongitude: Double
    ): Pair<Double, Double> {
        val dLat = targetLatitude - anchorLatitude
        val dLng = wrapLongitudeDeltaDegrees(targetLongitude - anchorLongitude)
        val cosLat = cos(anchorLatitude * DEG_TO_RAD)
        val north = dLat * DEG_TO_RAD * WGS84_MEAN_RADIUS_METERS
        val east = dLng * DEG_TO_RAD * WGS84_MEAN_RADIUS_METERS * cosLat
        return east to north
    }

    internal fun eastNorthToLatLng(
        anchorLatitude: Double,
        anchorLongitude: Double,
        eastMeters: Double,
        northMeters: Double
    ): LatLng {
        val dLat = (northMeters / WGS84_MEAN_RADIUS_METERS) * RAD_TO_DEG
        val cosLat = cos(anchorLatitude * DEG_TO_RAD).coerceAtLeast(1e-12)
        val dLng = (eastMeters / (WGS84_MEAN_RADIUS_METERS * cosLat)) * RAD_TO_DEG
        val latitude = (anchorLatitude + dLat).coerceIn(-90.0, 90.0)
        val longitude = wrapLongitudeAbsolute(anchorLongitude + dLng)
        return LatLng(latitude, longitude)
    }

    /**
     * EN → planning `(x, z)` with clockwise heading of +Z from north:
     * `x = e·cos(h) − n·sin(h)`, `z = e·sin(h) + n·cos(h)`.
     */
    internal fun eastNorthToPlanning(
        eastMeters: Double,
        northMeters: Double,
        headingDegrees: Float
    ): Pair<Float, Float> {
        val h = headingDegrees * DEG_TO_RAD
        val cosH = cos(h)
        val sinH = sin(h)
        val x = eastMeters * cosH - northMeters * sinH
        val z = eastMeters * sinH + northMeters * cosH
        return x.toFloat() to z.toFloat()
    }

    /**
     * Planning `(x, z)` → EN (inverse of [eastNorthToPlanning]).
     */
    internal fun planningToEastNorth(
        xMeters: Double,
        zMeters: Double,
        headingDegrees: Float
    ): Pair<Double, Double> {
        val h = headingDegrees * DEG_TO_RAD
        val cosH = cos(h)
        val sinH = sin(h)
        val east = xMeters * cosH + zMeters * sinH
        val north = -xMeters * sinH + zMeters * cosH
        return east to north
    }

    /** Wraps a longitude delta into `(-180, 180]`. */
    fun wrapLongitudeDeltaDegrees(deltaDegrees: Double): Double {
        var d = deltaDegrees % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }

    private fun wrapLongitudeAbsolute(longitude: Double): Double {
        var lng = longitude % 360.0
        if (lng > 180.0) lng -= 360.0
        if (lng <= -180.0) lng += 360.0
        return lng
    }

    private fun normalizeDegrees(degrees: Float): Float {
        var d = degrees % 360f
        if (d < 0f) d += 360f
        return if (d >= 360f) 0f else d
    }

    private fun requireFiniteLatLng(latitude: Double, longitude: Double, label: String) {
        require(latitude.isFinite() && longitude.isFinite()) {
            "$label latitude/longitude must be finite"
        }
        require(latitude in -90.0..90.0) {
            "$label latitude must be in [-90, 90], was $latitude"
        }
    }

    private fun requireGeographicFix(anchor: Anchor) {
        if (!anchor.hasGeographicFix) {
            throw MissingGeographicFixException(
                "Anchor '${anchor.id}' has no geographic fix (latitude/longitude required)"
            )
        }
    }
}
