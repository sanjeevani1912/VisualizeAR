package com.example.dhruvar

import com.example.dhruvar.domain.model.GeoBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GeoBounds] validation and containment (Phase 1 geographic data model).
 *
 * Invalid bounds rule: construction throws / [GeoBounds.createOrNull] returns null when
 * south > north, latitudes outside [-90, 90], non-finite values, or antimeridian wrap
 * (west > east). Deserialization uses createOrNull and ignores invalid bounds.
 */
class GeoBoundsTest {

    @Test
    fun validBounds_constructAndContainInteriorPoint() {
        val bounds = GeoBounds(
            northLatitude = 29.0,
            southLatitude = 28.0,
            eastLongitude = 78.0,
            westLongitude = 77.0
        )
        assertTrue(bounds.contains(28.5, 77.5))
        assertTrue(bounds.contains(28.0, 77.0)) // inclusive edges
        assertTrue(bounds.contains(29.0, 78.0))
        assertFalse(bounds.contains(27.9, 77.5))
        assertFalse(bounds.contains(28.5, 76.9))
        assertFalse(bounds.contains(Double.NaN, 77.5))
    }

    @Test
    fun southGreaterThanNorth_createOrNullReturnsNull() {
        assertNull(
            GeoBounds.createOrNull(
                northLatitude = 28.0,
                southLatitude = 29.0,
                eastLongitude = 78.0,
                westLongitude = 77.0
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun southGreaterThanNorth_constructorThrows() {
        GeoBounds(
            northLatitude = 28.0,
            southLatitude = 29.0,
            eastLongitude = 78.0,
            westLongitude = 77.0
        )
    }

    @Test
    fun latitudeOutOfRange_createOrNullReturnsNull() {
        assertNull(
            GeoBounds.createOrNull(
                northLatitude = 91.0,
                southLatitude = 28.0,
                eastLongitude = 78.0,
                westLongitude = 77.0
            )
        )
    }

    @Test
    fun antimeridianWrap_createOrNullReturnsNull() {
        // west > east implies antimeridian crossing — unsupported in v1 of bounds
        assertNull(
            GeoBounds.createOrNull(
                northLatitude = 10.0,
                southLatitude = -10.0,
                eastLongitude = -170.0,
                westLongitude = 170.0
            )
        )
    }

    @Test
    fun nonFiniteLongitude_createOrNullReturnsNull() {
        assertNull(
            GeoBounds.createOrNull(
                northLatitude = 29.0,
                southLatitude = 28.0,
                eastLongitude = Double.POSITIVE_INFINITY,
                westLongitude = 77.0
            )
        )
    }

    @Test
    fun equalNorthSouth_isValid() {
        val bounds = GeoBounds.createOrNull(
            northLatitude = 28.6139,
            southLatitude = 28.6139,
            eastLongitude = 77.2090,
            westLongitude = 77.2090
        )
        assertNotNull(bounds)
        assertTrue(bounds!!.contains(28.6139, 77.2090))
        assertEquals(28.6139, bounds.northLatitude, 0.0)
    }
}
