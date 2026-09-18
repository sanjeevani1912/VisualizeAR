package com.example.dhruvar.domain.spatial

import androidx.compose.ui.geometry.Offset
import com.example.dhruvar.domain.model.LayoutObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Utility responsible for spatial hit-testing between screen touch coordinates
 * and positioned [LayoutObject] entities.
 */
object ObjectHitTester {

    /**
     * Determines which [LayoutObject], if any, was touched by a screen tap.
     *
     * Evaluates objects in reverse render order (topmost / most recently placed first).
     * Accounts for object rotation and enforces a minimum touch target size (44px)
     * so small-footprint assets remain easily selectable on mobile touchscreens.
     *
     * @param tapOffset Screen pixel position of the user's touch.
     * @param objects Current list of objects placed on the canvas.
     * @param transformer Active [CoordinateTransformer] projecting world to canvas.
     * @param minTouchTargetPx Minimum hitbox dimension for accessibility (default 44px).
     * @return The touched [LayoutObject], or null if tap fell on empty canvas.
     */
    fun findHitObject(
        tapOffset: Offset,
        objects: List<LayoutObject>,
        transformer: CoordinateTransformer,
        minTouchTargetPx: Float = 44.0f
    ): LayoutObject? {
        for (obj in objects.asReversed()) {
            val screenPos = transformer.worldToCanvas(obj.x, obj.z)
            val widthPx = transformer.metersToPixels(obj.widthMeters)
            val lengthPx = transformer.metersToPixels(obj.lengthMeters)

            val halfW = maxOf(widthPx, minTouchTargetPx) / 2f
            val halfL = maxOf(lengthPx, minTouchTargetPx) / 2f

            val isHit = if (obj.rotationDegrees != 0.0f) {
                val rad = Math.toRadians(-obj.rotationDegrees.toDouble())
                val cosVal = cos(rad).toFloat()
                val sinVal = sin(rad).toFloat()
                val translatedX = tapOffset.x - screenPos.x
                val translatedY = tapOffset.y - screenPos.y
                val localX = translatedX * cosVal - translatedY * sinVal
                val localY = translatedX * sinVal + translatedY * cosVal
                abs(localX) <= halfW && abs(localY) <= halfL
            } else {
                abs(tapOffset.x - screenPos.x) <= halfW && abs(tapOffset.y - screenPos.y) <= halfL
            }

            if (isHit) {
                return obj
            }
        }
        return null
    }
}
