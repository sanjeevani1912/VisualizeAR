package com.example.dhruvar.domain.sensors

/**
 * Live device compass state derived from Android orientation sensors.
 *
 * [magneticHeadingDegrees] is the azimuth toward **Magnetic North** in degrees:
 * - 0°   = device top points Magnetic North
 * - 90°  = East
 * - 180° = South
 * - 270° = West
 *
 * Values are normalized to [0, 360). True-north correction can be layered later
 * without changing this contract.
 */
data class CompassState(
    val magneticHeadingDegrees: Float = 0f,
    val isAvailable: Boolean = false,
    val needsCalibration: Boolean = false,
    val accuracyLabel: String = "UNKNOWN"
) {
    val cardinalLabel: String
        get() = when (((magneticHeadingDegrees + 22.5f) % 360f) / 45f) {
            in 0f..1f -> "N"
            in 1f..2f -> "NE"
            in 2f..3f -> "E"
            in 3f..4f -> "SE"
            in 4f..5f -> "S"
            in 5f..6f -> "SW"
            in 6f..7f -> "W"
            else -> "NW"
        }
}
