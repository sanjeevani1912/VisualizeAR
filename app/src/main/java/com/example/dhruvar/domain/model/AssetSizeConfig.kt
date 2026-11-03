package com.example.dhruvar.domain.model

/**
 * Real-world size of every placeable asset, plus the area choices offered in the planner.
 *
 * Each model is scaled uniformly (so it is never stretched) until its longest side equals the
 * real-world average length below, then multiplied by [regionSizeFactor] for the selected region.
 * Change a value here and the 2D map, hit-testing and AR all follow.
 *
 * Sources (average of the common variants):
 * - Attack helicopter: AH-64 Apache 17.73 m and Mi-24 Hind 21.6 m overall length, rotors turning.
 * - Light military utility vehicle: about 4.0 m (Maruti Gypsy 3.4 m up to 4.6 m class jeeps).
 * - Off-road SUV: Mahindra Scorpio-N 4.66 m.
 * - Stryker MGS: 7.63 m overall with the gun forward (6.95 m hull only).
 * - K-131 launcher: no public reference found, so its supplied model size is kept.
 */
object AssetSizeConfig {

    const val GUNSHIP_LENGTH_METERS = 19.7f
    const val MILITARY_VEHICLE_LENGTH_METERS = 4.0f
    const val OFFROAD_VEHICLE_LENGTH_METERS = 4.66f
    const val K131_LENGTH_METERS = 6.24f
    const val K131_TAKEOFF_LENGTH_METERS = 6.24f
    const val STR_MGS_LENGTH_METERS = 7.63f

    /** Region side (m) at which assets are drawn at their real-world size (factor 1.0). */
    const val REFERENCE_REGION_SIDE_METERS = 150.0

    const val MIN_REGION_SIZE_FACTOR = 0.5f
    const val MAX_REGION_SIZE_FACTOR = 3.0f

    /**
     * Multiplier on every asset's real-world size for a region of [sideMeters]: a small region
     * shrinks assets, a big one enlarges them, so they stay readable against the area.
     * 100 m -> 0.67x, 150 m -> 1x, 250 m -> 1.67x, 300 m -> 2x (clamped to 0.5x..3x).
     */
    fun regionSizeFactor(sideMeters: Double): Float =
        (sideMeters / REFERENCE_REGION_SIDE_METERS).toFloat()
            .coerceIn(MIN_REGION_SIZE_FACTOR, MAX_REGION_SIZE_FACTOR)

    /** Region sides (m) offered by the area picker. */
    val regionSideChoicesMeters: List<Double> = listOf(100.0, 150.0, 200.0, 250.0, 300.0)

    /** Region side used until the operator picks another. */
    const val DEFAULT_REGION_SIDE_METERS = 250.0
}
