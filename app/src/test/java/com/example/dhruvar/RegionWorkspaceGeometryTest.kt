package com.example.dhruvar

import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.model.PlanningSetupStep
import com.example.dhruvar.domain.spatial.GeographicCoordinateConverter
import com.example.dhruvar.domain.spatial.LatLng
import com.example.dhruvar.domain.spatial.RegionWorkspaceGeometry
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionWorkspaceGeometryTest {

    private val lat = 28.6139
    private val lng = 77.2090

    @Test
    fun `spacing buckets follow the shortest side`() {
        assertEquals(10.0, RegionWorkspaceGeometry.gridSpacingMeters(squareMeters(80.0)), 0.0)
        assertEquals(25.0, RegionWorkspaceGeometry.gridSpacingMeters(squareMeters(200.0)), 0.0)
        assertEquals(50.0, RegionWorkspaceGeometry.gridSpacingMeters(squareMeters(1_000.0)), 0.0)
        assertEquals(100.0, RegionWorkspaceGeometry.gridSpacingMeters(squareMeters(10_000.0)), 0.0)
    }

    @Test
    fun `every grid vertex lies inside the region`() {
        val bounds = squareMeters(200.0)
        val lines = RegionWorkspaceGeometry.gridLines(bounds)

        assertTrue(lines.isNotEmpty())
        for (line in lines) {
            assertTrue(line.points.size >= 2)
            for (point in line.points) {
                assertTrue(
                    "point $point outside $bounds",
                    nearlyInside(bounds, point)
                )
            }
        }
    }

    @Test
    fun `a huge region does not emit more than the per-axis cap`() {
        val bounds = squareMeters(10_000.0)
        val lines = RegionWorkspaceGeometry.gridLines(bounds)

        // Two axes, plus a couple of extra ticks from inclusive snapping.
        assertTrue(
            "line count ${lines.size} exceeded cap",
            lines.size <= RegionWorkspaceGeometry.MAX_GRID_LINES_PER_AXIS * 2 + 8
        )
    }

    @Test
    fun `null origin puts a line through the south-west corner`() {
        val bounds = squareMeters(200.0)
        val lines = RegionWorkspaceGeometry.gridLines(
            bounds = bounds,
            originLatLng = null,
            headingDegrees = null
        )
        val southWest = LatLng(bounds.southLatitude, bounds.westLongitude)

        assertTrue(
            lines.any { line ->
                line.points.any { GeographicCoordinateConverter.distanceMeters(it, southWest) < 2.0 }
            }
        )
    }

    @Test
    fun `origin plus heading 90 draws an east-west line through the origin`() {
        val bounds = squareMeters(200.0)
        val origin = RegionWorkspaceGeometry.centroid(bounds)
        val lines = RegionWorkspaceGeometry.gridLines(
            bounds = bounds,
            spacing = 25.0,
            headingDegrees = 90f,
            originLatLng = origin.latitude to origin.longitude
        )

        val throughOrigin = lines.filter { line ->
            pointToSegmentMeters(origin, line.points.first(), line.points.last()) < 3.0
        }
        assertTrue("expected two axes through the origin, got ${throughOrigin.size}", throughOrigin.size >= 2)

        val eastWest = throughOrigin.first {
            val dLat = abs(it.points.first().latitude - it.points.last().latitude)
            val dLng = abs(it.points.first().longitude - it.points.last().longitude)
            dLng > dLat
        }
        for (point in eastWest.points) {
            assertEquals(origin.latitude, point.latitude, 1.5e-5)
        }
    }

    @Test
    fun `workspace layers are only for origin and asset steps with a region`() {
        val bounds = squareMeters(200.0)
        assertTrue(RegionWorkspaceGeometry.showsWorkspace(PlanningSetupStep.SET_ORIGIN, bounds))
        assertTrue(RegionWorkspaceGeometry.showsWorkspace(PlanningSetupStep.PLACE_ASSETS, bounds))
        assertFalse(RegionWorkspaceGeometry.showsWorkspace(PlanningSetupStep.FIND_LOCATION, bounds))
        assertFalse(RegionWorkspaceGeometry.showsWorkspace(PlanningSetupStep.SET_REGION, bounds))
        assertFalse(RegionWorkspaceGeometry.showsWorkspace(PlanningSetupStep.SET_ORIGIN, null))
    }

    @Test
    fun `a larger span fits at a lower zoom`() {
        val tight = RegionWorkspaceGeometry.zoomToFitSpan(0.002)
        val wide = RegionWorkspaceGeometry.zoomToFitSpan(0.02)
        assertTrue("tight=$tight wide=$wide", tight > wide)
        assertTrue(tight in 10.0..22.0)
        assertTrue(wide in 8.0..20.0)
    }

    @Test
    fun `expanded bounds keep the original box inside with at least twenty metres of pad`() {
        val bounds = squareMeters(200.0)
        val padded = RegionWorkspaceGeometry.expandBounds(bounds)
        assertTrue(padded.northLatitude > bounds.northLatitude)
        assertTrue(padded.southLatitude < bounds.southLatitude)
        assertTrue(padded.eastLongitude > bounds.eastLongitude)
        assertTrue(padded.westLongitude < bounds.westLongitude)

        val northPad = GeographicCoordinateConverter.distanceMeters(
            LatLng(bounds.northLatitude, (bounds.westLongitude + bounds.eastLongitude) / 2.0),
            LatLng(padded.northLatitude, (padded.westLongitude + padded.eastLongitude) / 2.0)
        )
        assertTrue(
            "north pad $northPad m",
            northPad >= RegionWorkspaceGeometry.MIN_PAD_METERS * 0.95
        )
    }

    private fun squareMeters(side: Double): GeoBounds {
        val southWest = GeographicCoordinateConverter.metersOffsetToLatLng(lat, lng, 0f, 0f, 0f)
        val northEast = GeographicCoordinateConverter.metersOffsetToLatLng(
            lat,
            lng,
            0f,
            side.toFloat(),
            side.toFloat()
        )
        return GeoBounds(
            northLatitude = northEast.latitude,
            southLatitude = southWest.latitude,
            eastLongitude = northEast.longitude,
            westLongitude = southWest.longitude
        )
    }

    private fun nearlyInside(bounds: GeoBounds, point: LatLng, epsilonDegrees: Double = 1e-6): Boolean {
        return point.latitude in (bounds.southLatitude - epsilonDegrees)..(bounds.northLatitude + epsilonDegrees) &&
            point.longitude in (bounds.westLongitude - epsilonDegrees)..(bounds.eastLongitude + epsilonDegrees)
    }

    private fun pointToSegmentMeters(point: LatLng, a: LatLng, b: LatLng): Double {
        val (px, pz) = GeographicCoordinateConverter.latLngToMetersOffset(
            a.latitude, a.longitude, 0f, point.latitude, point.longitude
        )
        val (bx, bz) = GeographicCoordinateConverter.latLngToMetersOffset(
            a.latitude, a.longitude, 0f, b.latitude, b.longitude
        )
        val lengthSq = bx * bx + bz * bz
        if (lengthSq < 1e-6f) {
            return GeographicCoordinateConverter.distanceMeters(point, a)
        }
        val t = ((px * bx + pz * bz) / lengthSq).coerceIn(0f, 1f)
        val closest = GeographicCoordinateConverter.metersOffsetToLatLng(
            a.latitude, a.longitude, 0f, bx * t, bz * t
        )
        return GeographicCoordinateConverter.distanceMeters(point, closest)
    }
}
