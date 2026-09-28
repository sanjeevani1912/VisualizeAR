package com.example.dhruvar.domain.model

import java.util.UUID

/**
 * Represents the central spatial reference point for all objects within a Layout.
 *
 * In 2D planning, the anchor corresponds to the origin (x=0, z=0) of the field coordinate system.
 * In AR visualization, this anchor maps directly to a detected real-world ARCore Anchor,
 * allowing all layout objects to be projected into the physical world with exact offsets.
 *
 * Optional geographic fields (WGS84) allow the same origin to be shared across map planning,
 * GNSS, and AR. Object positions remain meter offsets `(x, z)` relative to this anchor.
 *
 * [headingDegrees] is degrees clockwise from the planning “+Z / north” alignment heading used
 * by the app’s compass convention (magnetic heading as elsewhere in the app).
 */
data class Anchor(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "Origin Anchor",
    val x: Float = 0.0f, // meters
    val z: Float = 0.0f, // meters
    val description: String = "Primary deployment reference datum",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val headingDegrees: Float? = null,
    val horizontalAccuracyMeters: Float? = null
) {
    /** True when both latitude and longitude are present and finite. */
    val hasGeographicFix: Boolean
        get() {
            val lat = latitude
            val lng = longitude
            return lat != null && lng != null && lat.isFinite() && lng.isFinite()
        }
}
