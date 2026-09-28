package com.example.dhruvar

import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.spatial.GeographicCoordinateConverter
import com.example.dhruvar.domain.spatial.LatLng
import com.example.dhruvar.domain.spatial.MissingGeographicFixException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Phase 2: WGS84 ↔ planning `(x, z)` conversion.
 *
 * Axis behavior summary (heading = 0 unless noted):
 * | Input                         | Expected planning result      |
 * |-------------------------------|-------------------------------|
 * | ~10 m geographic east         | primarily +X                  |
 * | ~25 m geographic north        | primarily +Z                  |
 * | same east, heading = 90°      | primarily +Z                  |
 * | same north, heading = 90°     | primarily −X                  |
 */
class GeographicCoordinateConverterTest {

    private val delhiLat = 28.6139
    private val delhiLng = 77.2090

    // ── Known vectors (heading = 0) ──────────────────────────────────────────

    @Test
    fun latLngToMeters_tenMetersEast_primarilyPlusX_whenHeadingZero() {
        val target = offsetLatLng(delhiLat, delhiLng, eastMeters = 10.0, northMeters = 0.0)
        val (x, z) = GeographicCoordinateConverter.latLngToMetersOffset(
            delhiLat, delhiLng, 0f, target.latitude, target.longitude
        )
        assertEquals(10f, x, 0.15f)
        assertEquals(0f, z, 0.15f)
        assertTrue("east should dominate X, got x=$x z=$z", abs(x) > abs(z) * 5)
        assertTrue(x > 0f)
    }

    @Test
    fun latLngToMeters_twentyFiveMetersNorth_primarilyPlusZ_whenHeadingZero() {
        val target = offsetLatLng(delhiLat, delhiLng, eastMeters = 0.0, northMeters = 25.0)
        val (x, z) = GeographicCoordinateConverter.latLngToMetersOffset(
            delhiLat, delhiLng, 0f, target.latitude, target.longitude
        )
        assertEquals(0f, x, 0.15f)
        assertEquals(25f, z, 0.15f)
        assertTrue("north should dominate Z, got x=$x z=$z", abs(z) > abs(x) * 5)
        assertTrue(z > 0f)
    }

    // ── Heading rotation (90°) ───────────────────────────────────────────────

    @Test
    fun latLngToMeters_tenMetersEast_primarilyPlusZ_whenHeading90() {
        val target = offsetLatLng(delhiLat, delhiLng, eastMeters = 10.0, northMeters = 0.0)
        val (x, z) = GeographicCoordinateConverter.latLngToMetersOffset(
            delhiLat, delhiLng, 90f, target.latitude, target.longitude
        )
        assertEquals(0f, x, 0.15f)
        assertEquals(10f, z, 0.15f)
        assertTrue("with heading 90, east → +Z, got x=$x z=$z", abs(z) > abs(x) * 5)
        assertTrue(z > 0f)
    }

    @Test
    fun latLngToMeters_twentyFiveMetersNorth_primarilyMinusX_whenHeading90() {
        val target = offsetLatLng(delhiLat, delhiLng, eastMeters = 0.0, northMeters = 25.0)
        val (x, z) = GeographicCoordinateConverter.latLngToMetersOffset(
            delhiLat, delhiLng, 90f, target.latitude, target.longitude
        )
        assertEquals(-25f, x, 0.15f)
        assertEquals(0f, z, 0.15f)
        assertTrue("with heading 90, north → −X, got x=$x z=$z", abs(x) > abs(z) * 5)
        assertTrue(x < 0f)
    }

    // ── Round-trip accuracy ≤ 500 m ──────────────────────────────────────────

    @Test
    fun metersToLatLngToMeters_roundTrip_within025m_forOffsetsUpTo500m() {
        val samples = listOf(
            0f to 0f,
            10f to 0f,
            0f to 25f,
            -40f to 60f,
            100f to -100f,
            250f to 250f,
            -500f to 0f,
            0f to 500f,
            353.5f to -212.25f
        )
        val headings = listOf(0f, 45f, 90f, 180f, 270f)

        for (heading in headings) {
            for ((x0, z0) in samples) {
                val latLng = GeographicCoordinateConverter.metersOffsetToLatLng(
                    delhiLat, delhiLng, heading, x0, z0
                )
                val (x1, z1) = GeographicCoordinateConverter.latLngToMetersOffset(
                    delhiLat, delhiLng, heading, latLng.latitude, latLng.longitude
                )
                val err = hypot((x1 - x0).toDouble(), (z1 - z0).toDouble())
                assertTrue(
                    "round-trip error ${err}m exceeds 0.25m for ($x0,$z0) heading=$heading",
                    err <= 0.25
                )
                // Prefer ≤ 0.1 m for these mid-latitude field scales
                assertTrue(
                    "round-trip error ${err}m exceeds preferred 0.1m for ($x0,$z0) heading=$heading",
                    err <= 0.1
                )
            }
        }
    }

    @Test
    fun metersOffsetToLatLng_thenDistance_matchesHypotForAxisAligned() {
        val target = GeographicCoordinateConverter.metersOffsetToLatLng(
            delhiLat, delhiLng, 0f, 30f, 40f
        )
        val dist = GeographicCoordinateConverter.distanceMeters(
            LatLng(delhiLat, delhiLng),
            target
        )
        assertEquals(50.0, dist, 0.25)
    }

    // ── Bearing ──────────────────────────────────────────────────────────────

    @Test
    fun bearingDegrees_dueNorth_isNearZero() {
        val from = LatLng(delhiLat, delhiLng)
        val north = offsetLatLng(delhiLat, delhiLng, eastMeters = 0.0, northMeters = 100.0)
        val bearing = GeographicCoordinateConverter.bearingDegrees(from, north)
        assertEquals(0f, bearing, 1f)
    }

    @Test
    fun bearingDegrees_dueEast_isNearNinety() {
        val from = LatLng(delhiLat, delhiLng)
        val east = offsetLatLng(delhiLat, delhiLng, eastMeters = 100.0, northMeters = 0.0)
        val bearing = GeographicCoordinateConverter.bearingDegrees(from, east)
        assertEquals(90f, bearing, 1f)
    }

    // ── Anchor overloads / guards ────────────────────────────────────────────

    @Test
    fun anchorOverload_usesGeoFixAndDefaultHeadingZero() {
        val anchor = Anchor(latitude = delhiLat, longitude = delhiLng, headingDegrees = null)
        val target = offsetLatLng(delhiLat, delhiLng, eastMeters = 10.0, northMeters = 0.0)
        val (x, z) = GeographicCoordinateConverter.latLngToMetersOffset(anchor, target)
        assertEquals(10f, x, 0.15f)
        assertEquals(0f, z, 0.15f)
    }

    @Test
    fun anchorOverload_withoutGeoFix_throwsMissingGeographicFixException() {
        val bare = Anchor()
        try {
            GeographicCoordinateConverter.latLngToMetersOffset(bare, LatLng(delhiLat, delhiLng))
            fail("expected MissingGeographicFixException")
        } catch (_: MissingGeographicFixException) {
            // expected
        }
        try {
            GeographicCoordinateConverter.metersOffsetToLatLng(bare, 1f, 1f)
            fail("expected MissingGeographicFixException")
        } catch (_: MissingGeographicFixException) {
            // expected
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun latLngToMeters_nonFiniteInput_throws() {
        GeographicCoordinateConverter.latLngToMetersOffset(
            delhiLat, delhiLng, 0f, Double.NaN, delhiLng
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun metersOffsetToLatLng_nonFiniteOffset_throws() {
        GeographicCoordinateConverter.metersOffsetToLatLng(
            delhiLat, delhiLng, 0f, Float.POSITIVE_INFINITY, 0f
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun latLng_rejectsOutOfRangeLatitude() {
        LatLng(95.0, 0.0)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Builds a LatLng approximately [eastMeters]/[northMeters] from the given anchor. */
    private fun offsetLatLng(
        anchorLat: Double,
        anchorLng: Double,
        eastMeters: Double,
        northMeters: Double
    ): LatLng {
        return GeographicCoordinateConverter.eastNorthToLatLng(
            anchorLat, anchorLng, eastMeters, northMeters
        )
    }
}
