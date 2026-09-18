package com.example.dhruvar.domain.spatial

import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.LayoutObject
import java.util.Locale
import kotlin.math.hypot

/**
 * Reusable engine for spatial distance calculations in the logical planning coordinate system (meters).
 *
 * All calculations use authoritative physical world coordinates (X, Z) in meters,
 * completely independent of screen pixels, device resolution, zoom, viewport pan, or object rotation.
 */
object SpatialDistanceCalculator {

    /**
     * Calculates the Euclidean distance in meters between two arbitrary logical points in the horizontal (X, Z) plane.
     *
     * @param x1 Lateral coordinate of first point in meters.
     * @param z1 Longitudinal coordinate of first point in meters.
     * @param x2 Lateral coordinate of second point in meters.
     * @param z2 Longitudinal coordinate of second point in meters.
     * @return Straight-line distance in meters.
     */
    fun calculateDistance(x1: Float, z1: Float, x2: Float, z2: Float): Float {
        val dx = (x2 - x1).toDouble()
        val dz = (z2 - z1).toDouble()
        return hypot(dx, dz).toFloat()
    }

    /**
     * Calculates the Euclidean distance in meters from the spatial Anchor datum to a [LayoutObject].
     *
     * @param obj The target positioned entity.
     * @param anchor The reference datum anchor (defaults to logical origin (0, 0)).
     * @return Distance from anchor to object center in meters.
     */
    fun calculateDistanceToAnchor(
        obj: LayoutObject,
        anchor: Anchor = Anchor(x = 0.0f, z = 0.0f)
    ): Float {
        return calculateDistance(anchor.x, anchor.z, obj.x, obj.z)
    }

    /**
     * Calculates the Euclidean distance in meters between two [LayoutObject] instances.
     *
     * @param obj1 First placed entity.
     * @param obj2 Second placed entity.
     * @return Distance between object centers in meters.
     */
    fun calculateDistanceBetween(obj1: LayoutObject, obj2: LayoutObject): Float {
        return calculateDistance(obj1.x, obj1.z, obj2.x, obj2.z)
    }

    /**
     * Formats a metric distance into a standardized tactical display string with one decimal place.
     * Examples:
     * - 18.437f -> "≈ 18.4 m"
     * - 0.0f -> "≈ 0.0 m"
     *
     * @param distanceMeters Distance in real-world meters.
     * @return Human-readable tactical distance string.
     */
    fun formatDistance(distanceMeters: Float): String {
        return String.format(Locale.US, "≈ %.1f m", distanceMeters)
    }
}
