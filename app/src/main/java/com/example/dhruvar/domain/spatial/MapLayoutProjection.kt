package com.example.dhruvar.domain.spatial

import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.model.LayoutObject
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Pure helpers for projecting layout objects onto the map and testing selection.
 *
 * Placement policy when [GeoBounds] is set: **reject** points outside the box
 * (do not silently clamp). Callers surface feedback to the user.
 */
object MapLayoutProjection {

    /** Floor for tap-to-select. A large asset uses its own half-diagonal instead. */
    const val SELECT_HIT_RADIUS_METERS: Float = 8f

    /** How far from the centre a finger can be and still grab this asset. */
    fun selectionRadiusMeters(obj: LayoutObject): Float {
        val halfDiagonal = hypot(obj.widthMeters / 2.0, obj.lengthMeters / 2.0).toFloat()
        return max(SELECT_HIT_RADIUS_METERS, halfDiagonal + 1.5f)
    }

    fun objectCenterLatLng(anchor: Anchor, obj: LayoutObject): LatLng? {
        if (!anchor.hasGeographicFix) return null
        return try {
            GeographicCoordinateConverter.metersOffsetToLatLng(anchor, obj.x, obj.z)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Footprint polygon corners (closed ring) in WGS84 for an object's ground rectangle.
     * Corner order is CW looking down; first point repeated at end for GeoJSON.
     */
    fun objectFootprintLatLngs(anchor: Anchor, obj: LayoutObject): List<LatLng>? {
        if (!anchor.hasGeographicFix) return null
        val heading = anchor.headingDegrees ?: 0f
        val halfW = obj.widthMeters / 2.0
        val halfL = obj.lengthMeters / 2.0
        val rot = Math.toRadians(obj.rotationDegrees.toDouble())
        val cosR = cos(rot)
        val sinR = sin(rot)
        val localCorners = listOf(
            -halfW to -halfL,
            halfW to -halfL,
            halfW to halfL,
            -halfW to halfL,
            -halfW to -halfL
        )
        return try {
            localCorners.map { (lx, lz) ->
                val rx = lx * cosR - lz * sinR
                val rz = lx * sinR + lz * cosR
                GeographicCoordinateConverter.metersOffsetToLatLng(
                    anchorLatitude = anchor.latitude!!,
                    anchorLongitude = anchor.longitude!!,
                    anchorHeadingDegrees = heading,
                    xMeters = (obj.x + rx).toFloat(),
                    zMeters = (obj.z + rz).toFloat()
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun metersFromAnchor(anchor: Anchor, latitude: Double, longitude: Double): Pair<Float, Float>? {
        if (!anchor.hasGeographicFix) return null
        return try {
            GeographicCoordinateConverter.latLngToMetersOffset(
                anchor,
                LatLng(latitude, longitude)
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns true when placement is allowed: no bounds, or point inside bounds.
     * Outside-bounds points are **rejected** (not clamped).
     */
    fun isInsideRegionOrUnbounded(bounds: GeoBounds?, latitude: Double, longitude: Double): Boolean {
        return bounds == null || bounds.contains(latitude, longitude)
    }

    /**
     * Origin must sit inside a confirmed region. Corners are inclusive.
     * Unlike [isInsideRegionOrUnbounded], a missing region is not treated as unbounded.
     */
    fun isUsableOrigin(region: GeoBounds, latitude: Double, longitude: Double): Boolean {
        return region.contains(latitude, longitude)
    }

    fun findNearestObjectId(
        anchor: Anchor,
        objects: List<LayoutObject>,
        latitude: Double,
        longitude: Double,
        maxDistanceMeters: Float = SELECT_HIT_RADIUS_METERS
    ): String? {
        if (!anchor.hasGeographicFix) return null
        val targetMeters = metersFromAnchor(anchor, latitude, longitude) ?: return null
        var bestId: String? = null
        var bestDist = Float.MAX_VALUE
        for (obj in objects) {
            val dx = obj.x - targetMeters.first
            val dz = obj.z - targetMeters.second
            val dist = hypot(dx.toDouble(), dz.toDouble()).toFloat()
            val reach = max(maxDistanceMeters, selectionRadiusMeters(obj))
            if (dist <= reach && dist < bestDist) {
                bestDist = dist
                bestId = obj.id
            }
        }
        return bestId
    }
}
