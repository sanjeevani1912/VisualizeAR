package com.example.dhruvar.domain.model

/**
 * Physical real-world specifications and 3D volumetric footprint for a field planning asset.
 *
 * Defines authoritative physical dimensions in meters:
 * - [widthMeters]: Lateral dimension along local X axis (cross-section).
 * - [lengthMeters]: Longitudinal dimension along local Z axis (front-to-back).
 * - [heightMeters]: Vertical elevation along local Y axis (height above ground in AR).
 *
 * Used for:
 * 1. 2D top-down planning footprint rendering on Canvas.
 * 2. Oriented bounding box calculation and spatial hit-testing.
 * 3. Preparing 3D bounding volumes and real-world scale for ARCore visualization.
 */
data class AssetSpecification(
    val widthMeters: Float,
    val lengthMeters: Float,
    val heightMeters: Float
) {
    init {
        require(widthMeters > 0f) { "widthMeters must be strictly positive (> 0), was $widthMeters" }
        require(lengthMeters > 0f) { "lengthMeters must be strictly positive (> 0), was $lengthMeters" }
        require(heightMeters > 0f) { "heightMeters must be strictly positive (> 0), was $heightMeters" }
    }

    /**
     * Approximate horizontal footprint area in square meters (m²).
     */
    val footprintAreaSqMeters: Float
        get() = widthMeters * lengthMeters

    /**
     * Approximate 3D bounding volume in cubic meters (m³).
     */
    val volumeCuMeters: Float
        get() = widthMeters * lengthMeters * heightMeters

    /** Same shape, scaled uniformly so the longest horizontal side becomes [targetLengthMeters]. */
    fun scaledToLength(targetLengthMeters: Float): AssetSpecification {
        val k = targetLengthMeters / maxOf(widthMeters, lengthMeters)
        return AssetSpecification(widthMeters * k, lengthMeters * k, heightMeters * k)
    }

    companion object {
        // Proportions come from the supplied 3D models (X = width, Z = length, Y = height after the
        // model is stood on the ground); the real size comes from AssetSizeConfig.
        val DEFAULT_GUNSHIP = AssetSpecification(12.4f, 18.07f, 5.55f)
            .scaledToLength(AssetSizeConfig.GUNSHIP_LENGTH_METERS)
        val DEFAULT_K131 = AssetSpecification(3.45f, 6.24f, 5.45f)
            .scaledToLength(AssetSizeConfig.K131_LENGTH_METERS)
        val DEFAULT_K131_TAKEOFF = AssetSpecification(3.31f, 6.24f, 3.54f)
            .scaledToLength(AssetSizeConfig.K131_TAKEOFF_LENGTH_METERS)
        val DEFAULT_MILITARY_VEHICLE = AssetSpecification(1.47f, 3.41f, 2.64f)
            .scaledToLength(AssetSizeConfig.MILITARY_VEHICLE_LENGTH_METERS)
        val DEFAULT_OFFROAD_VEHICLE = AssetSpecification(2.43f, 4.66f, 3.54f)
            .scaledToLength(AssetSizeConfig.OFFROAD_VEHICLE_LENGTH_METERS)
        val DEFAULT_STR_MGS = AssetSpecification(3.75f, 8.33f, 3.79f)
            .scaledToLength(AssetSizeConfig.STR_MGS_LENGTH_METERS)

        fun forType(assetType: AssetType): AssetSpecification = assetType.defaultSpecification
    }
}
