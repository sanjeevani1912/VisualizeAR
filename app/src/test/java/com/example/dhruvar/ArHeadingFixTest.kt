package com.example.dhruvar

import com.example.dhruvar.domain.spatial.ARCoordinateTransformer
import com.example.dhruvar.domain.spatial.ArHeadingFix
import com.example.dhruvar.domain.spatial.GeographicCoordinateConverter
import com.google.ar.core.Pose
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tests for the AR heading fix switch (domain/spatial/ArHeadingFix.kt). They run on the PC only.
 * They use the same stated assumption as HeadingReviewTest: ARCore world is right-handed, Y up; compass heading is
 * clockwise from above. NOT verified on a device.
 */
class ArHeadingFixTest {

    private fun norm(deg: Double): Double { var d = deg % 360.0; if (d < 0) d += 360.0; return d }

    private fun clockwiseDeg(fx: Double, fz: Double, dx: Double, dz: Double): Double =
        Math.toDegrees(atan2(fx * dz - fz * dx, fx * dx + fz * dz))

    /** The origin pose exactly as ARRenderer.northAlignedPose builds it from a yaw. */
    private fun originPoseForYaw(yawDeg: Float): Pose {
        val alpha = Math.toRadians((-yawDeg).toDouble()).toFloat()
        return Pose.makeRotation(0f, sin(alpha / 2f), 0f, cos(alpha / 2f))
    }

    @Test
    fun switchIsOffByDefault() {
        assertFalse("ArHeadingFix.ENABLED must be false until the field test passes", ArHeadingFix.ENABLED)
    }

    @Test
    fun withFixOff_positionAndRotationAreUnchanged() {
        val origin = Pose.IDENTITY
        val p = ARCoordinateTransformer.transformPlanningToWorldPosition(origin, 3f, 4f, headingFix = false)
        assertEquals(3f, p[0], 1e-5f)
        assertEquals(4f, p[2], 1e-5f)
        val pose = ARCoordinateTransformer.transformPlanningToWorldPose(origin, 3f, 4f, 30f, headingFix = false)
        // yaw -30 about Y: quaternion y component = sin(-15 deg)
        assertEquals(sin(Math.toRadians(-15.0)).toFloat(), pose.qy(), 1e-5f)
    }

    @Test
    fun withFixOn_xIsMirroredAndRotationSignFlipsWithIt() {
        val origin = Pose.IDENTITY
        val p = ARCoordinateTransformer.transformPlanningToWorldPosition(origin, 3f, 4f, headingFix = true)
        assertEquals(-3f, p[0], 1e-5f)
        assertEquals(4f, p[2], 1e-5f)
        val pose = ARCoordinateTransformer.transformPlanningToWorldPose(origin, 3f, 4f, 30f, headingFix = true)
        assertEquals(sin(Math.toRadians(15.0)).toFloat(), pose.qy(), 1e-5f)
    }

    @Test
    fun yawForPlanHeading_isSavedMinusLiveMinusCameraAngle() {
        // camera forward (0, 1) has forward angle 0
        assertEquals(30f, ARCoordinateTransformer.originYawDegreesForPlanHeading(0f, 1f, 90f, 60f), 1e-4f)
        // wraps into [0, 360)
        assertEquals(330f, ARCoordinateTransformer.originYawDegreesForPlanHeading(0f, 1f, 30f, 60f), 1e-4f)
        // degenerate camera direction: no change
        assertEquals(0f, ARCoordinateTransformer.originYawDegreesForPlanHeading(0f, 0f, 90f, 10f), 1e-4f)
    }

    /**
     * End to end with the real transformer functions: an object at planning (x, z) must land where the MAP puts it
     * (east, north metres from the origin) for every saved heading, live heading and camera direction tried.
     */
    @Test
    fun withFixOn_arAgreesWithMap_forManyHeadingsAndCameraDirections() {
        var checked = 0
        for (saved in listOf(0f, 30f, 90f, 180f, 270f, 359f)) {
            for (live in listOf(0f, 45f, 120f, 200f, 300f)) {
                for (camAngle in listOf(0.0, 77.0, 180.0, 290.0)) {
                    val a = Math.toRadians(camAngle)
                    val fx = sin(a).toFloat()
                    val fz = cos(a).toFloat()
                    val yaw = ARCoordinateTransformer.originYawDegreesForPlanHeading(fx, fz, saved, live)
                    val origin = originPoseForYaw(yaw)
                    for ((px, pz) in listOf(3f to 4f, -7f to 2f, 0f to 10f, 12f to -5f)) {
                        val w = ARCoordinateTransformer.transformPlanningToWorldPosition(origin, px, pz, headingFix = true)
                        val dist = hypot(w[0].toDouble(), w[2].toDouble())
                        val bearing = Math.toRadians(norm(live + clockwiseDeg(fx.toDouble(), fz.toDouble(), w[0].toDouble(), w[2].toDouble())))
                        val (me, mn) = GeographicCoordinateConverter.planningToEastNorth(px.toDouble(), pz.toDouble(), saved)
                        assertEquals("east saved=$saved live=$live cam=$camAngle p=($px,$pz)", me, dist * sin(bearing), 0.01)
                        assertEquals("north saved=$saved live=$live cam=$camAngle p=($px,$pz)", mn, dist * cos(bearing), 0.01)
                        checked++
                    }
                }
            }
        }
        println("ArHeadingFixTest: $checked placements agree with the map")
    }
}
