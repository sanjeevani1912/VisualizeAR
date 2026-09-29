package com.example.dhruvar.domain.ar

/**
 * Uniform visualization scale about the planning origin for AR rendering.
 *
 * Full scale is 1:1 field visualization. Miniature presets shrink horizontal offsets
 * and mesh scales about the origin for desk / sand-table briefings.
 *
 * GPS does not participate in this scaling — only planning meters → AR offsets.
 */
enum class ArVisualizationScale(
    val label: String,
    val scaleFactor: Float,
    val ratioLabel: String
) {
    FULL("Full", 1.0f, "1:1"),
    ONE_TO_20("1:20", 0.05f, "1:20"),
    ONE_TO_50("1:50", 0.02f, "1:50");

    val isMiniature: Boolean get() = scaleFactor < 1.0f

    /** Scales a planning-meter offset into AR meters for this mode. */
    fun scaleMeters(planMeters: Float): Float = planMeters * scaleFactor
}
