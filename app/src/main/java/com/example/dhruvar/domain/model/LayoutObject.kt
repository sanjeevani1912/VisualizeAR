package com.example.dhruvar.domain.model

import java.util.UUID

/**
 * Represents an individual spatial entity placed within a field layout.
 *
 * CRITICAL ARCHITECTURAL PRINCIPLE:
 * Objects are stored using real-world metric coordinates (meters) relative to the [Anchor],
 * NOT UI screen pixels.
 *
 * Coordinate Convention (Right-handed spatial system aligned with ARCore):
 * - [x]: Lateral horizontal offset in meters (positive = right/east of anchor, negative = left/west)
 * - [z]: Forward/backward horizontal offset in meters (positive = forward/north, negative = backward/south)
 * - [rotationDegrees]: Clockwise azimuth orientation around vertical Y axis (0 to 360 degrees)
 * - [widthMeters], [lengthMeters], [heightMeters]: Physical 3D footprint in meters
 */
data class LayoutObject(
    val id: String = UUID.randomUUID().toString(),
    val assetType: AssetType,
    val name: String = "${assetType.displayName} 1",
    val x: Float = 0.0f, // meters relative to anchor
    val z: Float = 0.0f, // meters relative to anchor
    val rotationDegrees: Float = 0.0f, // degrees (0.0 to 360.0)
    val widthMeters: Float = assetType.defaultWidthMeters,
    val lengthMeters: Float = assetType.defaultLengthMeters,
    val heightMeters: Float = assetType.defaultHeightMeters,
    val scale: Float = 1.0f,
    val isSelected: Boolean = false
) {
    /**
     * Authoritative 3D physical asset specification for this placed object.
     */
    val specification: AssetSpecification
        get() = AssetSpecification(
            widthMeters = widthMeters,
            lengthMeters = lengthMeters,
            heightMeters = heightMeters
        )

    /**
     * Returns a copy of this object with its rotation set and normalized to [0.0, 360.0).
     */
    fun withNormalizedRotation(degrees: Float): LayoutObject =
        copy(rotationDegrees = normalizeDegrees(degrees))

    /**
     * Returns a copy of this object with updated logical metric coordinates.
     */
    fun withPosition(newX: Float, newZ: Float): LayoutObject =
        copy(x = newX, z = newZ)

    /**
     * Returns a copy of this object with updated physical metric dimensions.
     * Enforces strictly positive values (> 0.0m).
     */
    fun withDimensions(
        newWidth: Float,
        newLength: Float,
        newHeight: Float = heightMeters
    ): LayoutObject {
        require(newWidth > 0f) { "width must be strictly positive (> 0), was $newWidth" }
        require(newLength > 0f) { "length must be strictly positive (> 0), was $newLength" }
        require(newHeight > 0f) { "height must be strictly positive (> 0), was $newHeight" }
        return copy(
            widthMeters = newWidth,
            lengthMeters = newLength,
            heightMeters = newHeight
        )
    }

    companion object {
        /**
         * Normalizes any angle in degrees to the standard azimuth range [0.0, 360.0).
         * Examples:
         * - 360.0f -> 0.0f
         * - -90.0f -> 270.0f
         * - 450.0f -> 90.0f
         */
        fun normalizeDegrees(degrees: Float): Float {
            val mod = degrees % 360.0f
            val normalized = if (mod < 0.0f) mod + 360.0f else mod
            return if (normalized == -0.0f || normalized >= 360.0f) 0.0f else normalized
        }
    }
}
