package com.example.dhruvar

import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.spatial.RegionDraftGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionDraftGeometryTest {

    // Roughly 0.01° ~ 1.1 km of latitude, and about 0.98 km of longitude at Delhi's latitude.
    private val lat = 28.6139
    private val lng = 77.2090

    @Test
    fun `corner order does not matter`() {
        val topLeftFirst = RegionDraftGeometry.boundsBetween(lat + 0.01, lng, lat, lng + 0.01)
        val bottomRightFirst = RegionDraftGeometry.boundsBetween(lat, lng + 0.01, lat + 0.01, lng)

        assertEquals(topLeftFirst, bottomRightFirst)
    }

    @Test
    fun `a dragged box normalises into north-south and west-east order`() {
        val bounds = RegionDraftGeometry.boundsBetween(lat, lng + 0.01, lat + 0.01, lng)!!

        assertTrue(bounds.northLatitude >= bounds.southLatitude)
        assertTrue(bounds.westLongitude <= bounds.eastLongitude)
        assertEquals(lat + 0.01, bounds.northLatitude, 1e-9)
        assertEquals(lng, bounds.westLongitude, 1e-9)
    }

    @Test
    fun `non-finite corners produce no bounds`() {
        assertNull(RegionDraftGeometry.boundsBetween(Double.NaN, lng, lat, lng))
        assertNull(RegionDraftGeometry.boundsBetween(lat, Double.POSITIVE_INFINITY, lat, lng))
    }

    @Test
    fun `side lengths are measured in meters`() {
        val bounds = RegionDraftGeometry.boundsBetween(lat, lng, lat + 0.01, lng + 0.01)!!
        val (width, height) = RegionDraftGeometry.sideLengthsMeters(bounds)

        // 0.01 degrees of latitude is ~1110 m anywhere; longitude shrinks by cos(latitude).
        assertEquals(1110.0, height, 15.0)
        assertEquals(975.0, width, 25.0)
        assertTrue("longitude side must be shorter this far north", width < height)
    }

    @Test
    fun `a realistic site box is accepted`() {
        val bounds = RegionDraftGeometry.boundsBetween(lat, lng, lat + 0.003, lng + 0.003)

        assertNotNull(bounds)
        assertNull(RegionDraftGeometry.rejectionReason(bounds))
    }

    @Test
    fun `a stray tap is rejected rather than committed as a zero-area region`() {
        val bounds = RegionDraftGeometry.boundsBetween(lat, lng, lat, lng)

        val reason = RegionDraftGeometry.rejectionReason(bounds)
        assertNotNull(reason)
        assertTrue(reason!!.contains("too small"))
    }

    @Test
    fun `a box just under the minimum side is rejected`() {
        // ~20 m of latitude, below the 25 m floor.
        val bounds = RegionDraftGeometry.boundsBetween(lat, lng, lat + 0.00018, lng + 0.00018)

        assertTrue(RegionDraftGeometry.rejectionReason(bounds)!!.contains("too small"))
    }

    @Test
    fun `a box comfortably over the minimum side is accepted`() {
        // ~55 m on the shorter side.
        val bounds = RegionDraftGeometry.boundsBetween(lat, lng, lat + 0.0005, lng + 0.00057)

        assertNull(RegionDraftGeometry.rejectionReason(bounds))
    }

    @Test
    fun `an absent box reports an invalid region rather than crashing`() {
        val reason = RegionDraftGeometry.rejectionReason(null)

        assertNotNull(reason)
        assertTrue(reason!!.contains("not a valid region"))
    }

    @Test
    fun `a continent-sized box is rejected as not a site`() {
        val bounds = GeoBounds(
            northLatitude = 60.0,
            southLatitude = -10.0,
            eastLongitude = 120.0,
            westLongitude = 20.0
        )

        assertTrue(RegionDraftGeometry.rejectionReason(bounds)!!.contains("too large"))
    }

    @Test
    fun `size labels switch to kilometres above a thousand meters`() {
        val small = RegionDraftGeometry.boundsBetween(lat, lng, lat + 0.003, lng + 0.003)!!
        val large = RegionDraftGeometry.boundsBetween(lat, lng, lat + 0.05, lng + 0.05)!!

        assertTrue(RegionDraftGeometry.sizeLabel(small).endsWith(" m"))
        assertTrue(RegionDraftGeometry.sizeLabel(large).endsWith(" km"))
    }

    @Test
    fun `the confirmed bounds are the same type the layout already stores`() {
        val bounds = RegionDraftGeometry.boundsBetween(lat, lng, lat + 0.003, lng + 0.003)!!

        // Guards against a parallel bounds type creeping in; placement checks rely on this.
        assertTrue(bounds.contains(lat + 0.001, lng + 0.001))
        assertTrue(!bounds.contains(lat - 0.001, lng + 0.001))
    }
}
