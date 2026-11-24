package com.example.dhruvar.domain.ar

import kotlin.math.abs

/** A detected horizontal plane reduced to the two numbers the floor choice needs (no ARCore types). */
data class FloorPlaneSample(val heightY: Float, val areaM2: Float)

/**
 * Pure helpers for the "one steady floor height" ground fix. No ARCore or Android classes,
 * so everything here is covered by plain JVM unit tests.
 */
object GroundFloor {
    const val MIN_FLOOR_AREA_M2 = 0.8f
    const val FLOOR_RANGE_M = 1.5f
    const val RAISED_ORIGIN_THRESHOLD_M = 0.3f
    const val SMOOTHING = 0.15f
    const val JUMP_M = 0.4f

    /** Lowest large plane within [rangeM] of [originY]; null when there is none. */
    fun chooseFloor(
        planes: List<FloorPlaneSample>,
        originY: Float,
        minAreaM2: Float = MIN_FLOOR_AREA_M2,
        rangeM: Float = FLOOR_RANGE_M
    ): Float? = planes
        .filter { it.areaM2 >= minAreaM2 && abs(it.heightY - originY) <= rangeM }
        .minOfOrNull { it.heightY }

    /** True when the origin sits more than [thresholdM] above the floor (e.g. on a table). */
    fun isOriginRaised(
        originY: Float,
        floorY: Float?,
        thresholdM: Float = RAISED_ORIGIN_THRESHOLD_M
    ): Boolean = floorY != null && originY - floorY > thresholdM

    /** Contact-shadow radius from the asset footprint, kept within sane limits. */
    fun shadowRadius(widthM: Float, lengthM: Float, vizScale: Float, sizeFactor: Float): Float {
        val footprint = maxOf(widthM, lengthM) * vizScale * sizeFactor
        return (footprint * 0.6f).coerceIn(0.15f, 6f)
    }
}

/**
 * Keeps one smoothed floor height. A new candidate moves it by [smoothing] per frame, or sets it
 * outright when it jumps more than [jumpM]. With no candidate the LAST good value is kept.
 */
class FloorTracker(
    private val smoothing: Float = GroundFloor.SMOOTHING,
    private val jumpM: Float = GroundFloor.JUMP_M
) {
    /** Current floor height, or null until a floor has been found once. */
    var height: Float? = null
        private set

    /** True when the last [update] had a fresh floor; false when the old value was kept. */
    var foundThisFrame: Boolean = false
        private set

    fun update(candidate: Float?): Float? {
        foundThisFrame = candidate != null
        if (candidate != null) {
            val current = height
            height = if (current == null || abs(candidate - current) > jumpM) candidate
            else current + (candidate - current) * smoothing
        }
        return height
    }

    fun reset() {
        height = null
        foundThisFrame = false
    }
}
