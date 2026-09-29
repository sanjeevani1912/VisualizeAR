package com.example.dhruvar.domain.spatial

import com.google.ar.core.Pose
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Coordinate Transformation Engine for AR Calibration.
 *
 * Establishes the spatial correspondence between:
 * 2D Planning Coordinates (xMeters, zMeters)
 * and
 * 3D Real-World ARCore Coordinates (x, y, z).
 *
 * Coordinate Convention:
 * - Planning +X: Lateral horizontal axis (Right). Maps to +X offset relative to planning origin.
 * - Planning +Z: Depth horizontal axis (Forward/Backward on the ground plane). Maps to +Z offset relative to planning origin.
 * - Vertical +Y: Normal axis perpendicular to the ground plane (Height clearance). Ground plane is Y = 0.0.
 * - Scale: 1 planning meter corresponds to [METERS_PER_PLANNING_UNIT] real-world meters at full scale;
 *   [visualizationScale] (e.g. miniature sand-table) multiplies horizontal offsets uniformly about the origin.
 *
 * Geographic GPS is **not** applied here — the user places the AR origin on-site; meshes use meter offsets only.
 */
object ARCoordinateTransformer {

    /**
     * Metric scale factor: exactly 1.0 meter in the 2D planner equals 1.0 meter in ARCore at full scale.
     */
    const val METERS_PER_PLANNING_UNIT = 1.0f

    /**
     * Transforms a 2D planning coordinate and rotation into an ARCore 3D world [Pose],
     * anchored to the physical planning origin [originPose].
     *
     * Pipeline:
     * Layout (X, Z) -> meters * [visualizationScale] -> AR world offset from planning origin -> ARCore Pose
     *
     * @param visualizationScale Uniform sand-table / full-scale factor about the origin (default 1.0).
     */
    fun transformPlanningToWorldPose(
        originPose: Pose,
        planXMeters: Float,
        planZMeters: Float,
        planRotationDegrees: Float = 0.0f,
        visualizationScale: Float = 1.0f
    ): Pose {
        val s = visualizationScale.coerceAtLeast(0.001f)
        // Translation in horizontal ground plane (Y = 0.0)
        val translationOffset = Pose.makeTranslation(
            planXMeters * METERS_PER_PLANNING_UNIT * s,
            0.0f,
            planZMeters * METERS_PER_PLANNING_UNIT * s
        )

        // Rotation around vertical axis (Y in ARCore right-handed system)
        val yawRad = Math.toRadians(-planRotationDegrees.toDouble()).toFloat()
        val rotationOffset = Pose.makeRotation(
            0.0f,
            sin(yawRad / 2.0f),
            0.0f,
            cos(yawRad / 2.0f)
        )

        return originPose.compose(translationOffset).compose(rotationOffset)
    }

    /**
     * Transforms a 2D planning coordinate into an ARCore 3D world position [x, y, z].
     */
    fun transformPlanningToWorldPosition(
        originPose: Pose,
        planXMeters: Float,
        planZMeters: Float,
        visualizationScale: Float = 1.0f
    ): FloatArray {
        val s = visualizationScale.coerceAtLeast(0.001f)
        val localPoint = floatArrayOf(
            planXMeters * METERS_PER_PLANNING_UNIT * s,
            0.0f,
            planZMeters * METERS_PER_PLANNING_UNIT * s
        )
        return originPose.transformPoint(localPoint)
    }

    /**
     * Pure geometric computation of world position without depending on native ARCore JNI.
     * Useful for decoupled testing and validation.
     *
     * @param visualizationScale Uniform miniature / full-scale factor (default 1.0).
     */
    fun computeWorldPositionPure(
        originX: Float,
        originY: Float,
        originZ: Float,
        originYawDegrees: Float,
        planX: Float,
        planZ: Float,
        visualizationScale: Float = 1.0f
    ): FloatArray {
        val s = visualizationScale.coerceAtLeast(0.001f)
        val rad = Math.toRadians(originYawDegrees.toDouble()).toFloat()
        val cosYaw = cos(rad)
        val sinYaw = sin(rad)

        val scaledX = planX * s
        val scaledZ = planZ * s
        // Rotate offset by origin yaw
        val rotatedX = (scaledX * cosYaw) - (scaledZ * sinYaw)
        val rotatedZ = (scaledX * sinYaw) + (scaledZ * cosYaw)

        return floatArrayOf(
            originX + (rotatedX * METERS_PER_PLANNING_UNIT),
            originY,
            originZ + (rotatedZ * METERS_PER_PLANNING_UNIT)
        )
    }

    /**
     * Yaw to apply to the planning origin so canvas north (+Z, up on the map) points at
     * magnetic north in the current ARCore session.
     *
     * [cameraForwardX] and [cameraForwardZ] are the camera look direction on the ground
     * plane (ARCore: look direction is the pose's negative Z, projected to XZ).
     * [magneticHeadingDegrees] is degrees clockwise from magnetic north to that same
     * facing direction (0 = facing north).
     *
     * The result matches [computeWorldPositionPure]'s clockwise yaw: it is captured once
     * at origin placement and then left fixed on the anchor.
     */
    fun originYawDegreesForMagneticNorth(
        cameraForwardX: Float,
        cameraForwardZ: Float,
        magneticHeadingDegrees: Float
    ): Float {
        val magSq = cameraForwardX * cameraForwardX + cameraForwardZ * cameraForwardZ
        if (magSq < 1e-6f) return 0f
        val forwardAngleDeg = Math.toDegrees(
            atan2(cameraForwardX.toDouble(), cameraForwardZ.toDouble())
        ).toFloat()
        var yaw = magneticHeadingDegrees - forwardAngleDeg
        yaw %= 360f
        if (yaw < 0f) yaw += 360f
        return if (yaw >= 360f) 0f else yaw
    }

    /**
     * Combines origin yaw and planning object rotation into a normalized world orientation [0, 360).
     */
    fun computeWorldRotationDegrees(originYawDegrees: Float, planRotationDegrees: Float): Float {
        var total = (originYawDegrees + planRotationDegrees) % 360.0f
        if (total < 0.0f) {
            total += 360.0f
        }
        return if (total == 360.0f) 0.0f else total
    }
}
