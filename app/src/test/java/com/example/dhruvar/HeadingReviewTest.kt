package com.example.dhruvar

import com.example.dhruvar.domain.spatial.ARCoordinateTransformer
import com.example.dhruvar.domain.spatial.GeographicCoordinateConverter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * D2 heading review (see docs/HEADING-REVIEW.md). These are pen-and-paper checks run as JVM tests.
 *
 * Model of the AR world (stated assumption, NOT verified on a device):
 *  - ARCore world is right-handed with +Y up. Seen from above, +X is to the right and +Z is toward the
 *    bottom of the page, so a camera looking along -Z faces "up the page".
 *  - A compass heading is clockwise from above.
 *  - A camera looking along world direction f has live magnetic heading Hlive. Any other ground direction d
 *    then has compass heading Hlive + (clockwise angle from f to d seen from above).
 */
class HeadingReviewTest {

    /** Clockwise angle from f to d, seen from above, in degrees. Page coords: x right, z down. */
    private fun clockwiseDeg(fx: Double, fz: Double, dx: Double, dz: Double): Double =
        Math.toDegrees(atan2(fx * dz - fz * dx, fx * dx + fz * dz))

    private fun norm(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }

    /** Compass heading, in AR, of the planning +Z axis when the origin is placed using [yawDeg]. */
    private fun planPlusZCompassHeading(
        yawDeg: Float,
        fx: Float,
        fz: Float,
        liveHeading: Float
    ): Double {
        val p = ARCoordinateTransformer.computeWorldPositionPure(
            originX = 0f, originY = 0f, originZ = 0f,
            originYawDegrees = yawDeg, planX = 0f, planZ = 1f
        )
        return norm(liveHeading + clockwiseDeg(fx.toDouble(), fz.toDouble(), p[0].toDouble(), p[2].toDouble()))
    }

    private fun forward(angleDeg: Double): Pair<Float, Float> {
        // World direction at "angle from +Z toward +X", as ARRenderer measures it.
        val a = Math.toRadians(angleDeg)
        return sin(a).toFloat() to cos(a).toFloat()
    }

    private val sampleLiveHeadings = listOf(0f, 45f, 90f, 135f, 180f, 270f)
    private val sampleCameraYaws = listOf(0.0, 90.0, 180.0, 270.0, 33.0)

    /** The map's rule: planning +Z points at compass heading h (clockwise from north). */
    @Test
    fun mapConvention_plusZPointsAtSavedHeading() {
        for (h in listOf(0f, 30f, 90f, 200f, 359f)) {
            val (e, n) = GeographicCoordinateConverter.planningToEastNorth(0.0, 1.0, h)
            val bearing = norm(Math.toDegrees(atan2(e, n)))
            assertEquals("h=$h", norm(h.toDouble()), bearing, 0.01)
        }
    }

    /** Today's call: saved heading h is passed where the live heading is expected. */
    @Test
    fun ar_currentCall_withSavedHeading_doesNotMatchTheMap() {
        var mismatches = 0
        var total = 0
        for (saved in listOf(0f, 30f, 90f, 200f)) {
            for (live in sampleLiveHeadings) {
                for (cam in sampleCameraYaws) {
                    val (fx, fz) = forward(cam)
                    val yaw = ARCoordinateTransformer.originYawDegreesForMagneticNorth(fx, fz, saved)
                    val arHeading = planPlusZCompassHeading(yaw, fx, fz, live)
                    total++
                    val diff = Math.abs(((arHeading - saved + 540.0) % 360.0) - 180.0)
                    if (diff > 0.5) mismatches++
                }
            }
        }
        // Agrees only when the live heading happens to be 0 (the arithmetic gives live + saved).
        println("HEADING-REVIEW current call: $mismatches of $total combinations disagree with the map")
        assertNotEquals("expected disagreement to be demonstrated", 0, mismatches)
    }

    /** The function's own promise: with the LIVE heading, plan +Z should point at north (0). */
    @Test
    fun ar_currentCall_withLiveHeading_northIsOnlyRightWhenFacingNorth() {
        val (fx, fz) = forward(0.0)
        for (live in sampleLiveHeadings) {
            val yaw = ARCoordinateTransformer.originYawDegreesForMagneticNorth(fx, fz, live)
            val arHeading = planPlusZCompassHeading(yaw, fx, fz, live)
            // Result is 2*live (mod 360): correct only for live = 0 or 180.
            assertEquals("live=$live", norm(2.0 * live), arHeading, 0.01)
        }
    }

    /**
     * Candidate fix, shown here only (NOT applied to app code): yaw = target - live - forwardAngle.
     * With target = saved heading it agrees with the map for every live heading and camera yaw.
     */
    @Test
    fun candidateFix_agreesWithMapForAllSamples() {
        for (saved in listOf(0f, 30f, 90f, 200f, 359f)) {
            for (live in sampleLiveHeadings) {
                for (cam in sampleCameraYaws) {
                    val (fx, fz) = forward(cam)
                    val forwardAngle = Math.toDegrees(atan2(fx.toDouble(), fz.toDouble()))
                    val yaw = norm(saved - live - forwardAngle).toFloat()
                    val arHeading = planPlusZCompassHeading(yaw, fx, fz, live)
                    val diff = Math.abs(((arHeading - saved + 540.0) % 360.0) - 180.0)
                    assertEquals("saved=$saved live=$live cam=$cam", 0.0, diff, 0.05)
                }
            }
        }
    }

    /** Different degree-to-metre constants: RegionDraftGeometry vs the converter. */
    @Test
    fun metersPerDegreeLatitude_constantsDiffer_byAboutOneTenthOfAPercent() {
        val regionDraft = 111_320.0
        val converter = GeographicCoordinateConverter.WGS84_MEAN_RADIUS_METERS * PI / 180.0
        val relativeDiff = (regionDraft - converter) / converter
        println("HEADING-REVIEW metres/degree: draft=$regionDraft converter=$converter diff=${relativeDiff * 100}%")
        assertEquals(0.00117, relativeDiff, 0.0001)
        // On a 250 m box edge that is under 0.3 m.
        assertEquals(0.29, 250.0 * relativeDiff, 0.02)
    }
}
