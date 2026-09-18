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

    companion object {
        val DEFAULT_TENT = AssetSpecification(widthMeters = 4.0f, lengthMeters = 6.0f, heightMeters = 2.5f)
        val DEFAULT_TRUCK = AssetSpecification(widthMeters = 2.5f, lengthMeters = 8.0f, heightMeters = 3.2f)
        val DEFAULT_CAR = AssetSpecification(widthMeters = 1.8f, lengthMeters = 4.5f, heightMeters = 1.5f)
        val DEFAULT_ANTENNA = AssetSpecification(widthMeters = 1.5f, lengthMeters = 1.5f, heightMeters = 8.0f)
        val DEFAULT_TRENCH = AssetSpecification(widthMeters = 1.2f, lengthMeters = 10.0f, heightMeters = 1.8f)

        fun forType(assetType: AssetType): AssetSpecification = when (assetType) {
            AssetType.TENT -> DEFAULT_TENT
            AssetType.TRUCK -> DEFAULT_TRUCK
            AssetType.CAR -> DEFAULT_CAR
            AssetType.ANTENNA -> DEFAULT_ANTENNA
            AssetType.TRENCH -> DEFAULT_TRENCH
        }
    }
}
