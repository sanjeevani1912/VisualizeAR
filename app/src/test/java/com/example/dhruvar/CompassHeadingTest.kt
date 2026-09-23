package com.example.dhruvar

import com.example.dhruvar.domain.sensors.CompassHeadingController
import com.example.dhruvar.domain.sensors.CompassState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure logic tests for compass heading helpers (no Android sensors required).
 */
class CompassHeadingTest {

    @Test
    fun normalizeDegrees_wrapsIntoZeroToThreeSixty() {
        assertEquals(0f, CompassHeadingController.normalizeDegrees(0f), 0.001f)
        assertEquals(90f, CompassHeadingController.normalizeDegrees(90f), 0.001f)
        assertEquals(0f, CompassHeadingController.normalizeDegrees(360f), 0.001f)
        assertEquals(270f, CompassHeadingController.normalizeDegrees(-90f), 0.001f)
        assertEquals(10f, CompassHeadingController.normalizeDegrees(370f), 0.001f)
    }

    @Test
    fun cardinalLabel_mapsHeadingBands() {
        assertEquals("N", CompassState(magneticHeadingDegrees = 0f).cardinalLabel)
        assertEquals("E", CompassState(magneticHeadingDegrees = 90f).cardinalLabel)
        assertEquals("S", CompassState(magneticHeadingDegrees = 180f).cardinalLabel)
        assertEquals("W", CompassState(magneticHeadingDegrees = 270f).cardinalLabel)
        assertEquals("NE", CompassState(magneticHeadingDegrees = 45f).cardinalLabel)
    }

    @Test
    fun compassState_defaultsAreSafe() {
        val state = CompassState()
        assertTrue(state.magneticHeadingDegrees in 0f..360f)
        assertEquals("UNKNOWN", state.accuracyLabel)
    }
}
