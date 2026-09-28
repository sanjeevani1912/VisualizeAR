package com.example.dhruvar.domain.model

/**
 * Axis-aligned geographic bounding box in WGS84 degrees.
 *
 * Antimeridian-crossing regions (where [westLongitude] > [eastLongitude]) are **not supported**
 * in this version of bounds. Callers must split such regions or use a non-wrapping box.
 *
 * Validation rules:
 * - [northLatitude] ≥ [southLatitude]
 * - Latitudes in `[-90, 90]`
 * - Longitudes must be finite (no NaN / Infinity)
 * - [westLongitude] ≤ [eastLongitude] (non–antimeridian only)
 */
data class GeoBounds(
    val northLatitude: Double,
    val southLatitude: Double,
    val eastLongitude: Double,
    val westLongitude: Double
) {
    init {
        require(northLatitude.isFinite() && southLatitude.isFinite()) {
            "Latitudes must be finite"
        }
        require(eastLongitude.isFinite() && westLongitude.isFinite()) {
            "Longitudes must be finite"
        }
        require(northLatitude in -90.0..90.0 && southLatitude in -90.0..90.0) {
            "Latitudes must be in [-90, 90]"
        }
        require(northLatitude >= southLatitude) {
            "northLatitude ($northLatitude) must be >= southLatitude ($southLatitude)"
        }
        require(westLongitude <= eastLongitude) {
            "Antimeridian-crossing bounds are unsupported: westLongitude ($westLongitude) must be <= eastLongitude ($eastLongitude)"
        }
    }

    /**
     * Returns true if [latitude]/[longitude] lies inside this box (inclusive edges).
     *
     * Does not handle antimeridian wrap-around; such bounds are rejected at construction.
     */
    fun contains(latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (latitude !in -90.0..90.0) return false
        return latitude in southLatitude..northLatitude &&
            longitude in westLongitude..eastLongitude
    }

    companion object {
        /**
         * Creates a [GeoBounds] when all validation rules pass; otherwise returns `null`.
         * Prefer this for defensive deserialization paths that must not crash on bad input.
         */
        fun createOrNull(
            northLatitude: Double,
            southLatitude: Double,
            eastLongitude: Double,
            westLongitude: Double
        ): GeoBounds? {
            return try {
                GeoBounds(
                    northLatitude = northLatitude,
                    southLatitude = southLatitude,
                    eastLongitude = eastLongitude,
                    westLongitude = westLongitude
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
