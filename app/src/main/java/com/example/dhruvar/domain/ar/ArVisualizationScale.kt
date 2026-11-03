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

/**
 * Display-only enlargement of the 3D models. Unlike [ArVisualizationScale] it never moves
 * objects: each model grows about its own ground pivot, so positions stay true to the plan.
 */
object ArAssetSize {
    const val MIN = 0.5f
    const val MAX = 10f
    const val STEP = 0.5f
    const val DEFAULT = 1.0f

    /** One step larger or smaller, kept inside [MIN]..[MAX]. */
    fun step(current: Float, increase: Boolean): Float =
        (current + if (increase) STEP else -STEP).coerceIn(MIN, MAX)

    fun label(factor: Float): String = String.format(java.util.Locale.US, "%.1f×", factor)
}
