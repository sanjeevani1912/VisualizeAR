package com.example.dhruvar

import com.example.dhruvar.domain.ar.FloorPlaneSample
import com.example.dhruvar.domain.ar.FloorTracker
import com.example.dhruvar.domain.ar.GroundFloor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundFloorTest {

    @Test
    fun chooseFloor_picksLowestLargePlane() {
        val planes = listOf(
            FloorPlaneSample(0.0f, 4f),
            FloorPlaneSample(-0.7f, 5f),   // floor, lower
            FloorPlaneSample(-0.2f, 1.5f)
        )
        assertEquals(-0.7f, GroundFloor.chooseFloor(planes, originY = 0f)!!, 1e-6f)
    }

    @Test
    fun chooseFloor_ignoresSmallPlanes() {
        val planes = listOf(FloorPlaneSample(-0.7f, 0.3f), FloorPlaneSample(0f, 2f))
        assertEquals(0f, GroundFloor.chooseFloor(planes, 0f)!!, 1e-6f)
    }

    @Test
    fun chooseFloor_ignoresPlanesOutOfRange() {
        val planes = listOf(FloorPlaneSample(-3f, 9f), FloorPlaneSample(5f, 9f))
        assertNull(GroundFloor.chooseFloor(planes, 0f))
    }

    @Test
    fun chooseFloor_emptyIsNull() {
        assertNull(GroundFloor.chooseFloor(emptyList(), 0f))
    }

    @Test
    fun tracker_firstValueIsTakenDirectly() {
        val t = FloorTracker()
        assertNull(t.height)
        assertEquals(-1f, t.update(-1f)!!, 1e-6f)
        assertTrue(t.foundThisFrame)
    }

    @Test
    fun tracker_smallChangeIsSmoothed() {
        val t = FloorTracker(smoothing = 0.15f, jumpM = 0.4f)
        t.update(0f)
        assertEquals(0.015f, t.update(0.1f)!!, 1e-5f)
    }

    @Test
    fun tracker_bigJumpSnaps() {
        val t = FloorTracker(smoothing = 0.15f, jumpM = 0.4f)
        t.update(0f)
        assertEquals(1f, t.update(1f)!!, 1e-6f)
    }

    @Test
    fun tracker_keepsLastGoodValueWhenFloorMissing() {
        val t = FloorTracker()
        t.update(-0.5f)
        val kept = t.update(null)
        assertEquals(-0.5f, kept!!, 1e-6f)
        assertFalse(t.foundThisFrame)
    }

    @Test
    fun tracker_neverFoundStaysNull_andResetClears() {
        val t = FloorTracker()
        assertNull(t.update(null))
        t.update(1f)
        t.reset()
        assertNull(t.height)
    }

    @Test
    fun tracker_convergesTowardsNewFloor() {
        val t = FloorTracker()
        t.update(0f)
        repeat(60) { t.update(0.2f) }
        assertEquals(0.2f, t.height!!, 0.01f)
    }

    @Test
    fun originRaised_rule() {
        assertTrue(GroundFloor.isOriginRaised(originY = 0.75f, floorY = 0f))
        assertFalse(GroundFloor.isOriginRaised(originY = 0.2f, floorY = 0f))
        assertFalse(GroundFloor.isOriginRaised(originY = 0.3f, floorY = 0f))
        assertFalse(GroundFloor.isOriginRaised(originY = 5f, floorY = null))
    }

    @Test
    fun shadowRadius_scalesAndClamps() {
        assertEquals(1.2f, GroundFloor.shadowRadius(2f, 1f, 1f, 1f), 1e-5f)
        assertEquals(0.15f, GroundFloor.shadowRadius(0.01f, 0.01f, 1f, 1f), 1e-5f)
        assertEquals(6f, GroundFloor.shadowRadius(100f, 100f, 1f, 1f), 1e-5f)
        assertEquals(2.4f, GroundFloor.shadowRadius(2f, 1f, 1f, 2f), 1e-5f)
    }
}
