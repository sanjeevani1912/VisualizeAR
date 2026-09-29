package com.example.dhruvar.domain.spatial

import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.model.LayoutObject
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure helpers for projecting layout objects onto the map and testing selection.
 *
 * Placement policy when [GeoBounds] is set: **reject** points outside the box
 * (do not silently clamp). Callers surface feedback to the user.
 */
object MapLayoutProjection {

    /** Maximum distance (meters) for tap-to-select on the map. */
    const val SELECT_HIT_RADIUS_METERS: Float = 8f

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
            val dist = kotlin.math.hypot(dx.toDouble(), dz.toDouble()).toFloat()
            if (dist <= maxDistanceMeters && dist < bestDist) {
                bestDist = dist
                bestId = obj.id
            }
        }
        return bestId
    }
}
