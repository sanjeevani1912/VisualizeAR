package com.example.dhruvar

import com.example.dhruvar.domain.ar.ArGeoGuidance
import com.example.dhruvar.domain.ar.ArGeoProximityStatus
import com.example.dhruvar.domain.ar.ArVisualizationScale
import com.example.dhruvar.domain.location.DeviceLocationFix
import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.spatial.ARCoordinateTransformer
import com.example.dhruvar.domain.spatial.GeographicCoordinateConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5: AR geo briefing helpers + miniature visualization scale math.
 */
class ArGeoWorkflowTest {

    private val anchorLat = 28.6139
    private val anchorLng = 77.2090

    @Test
    fun guidance_noGeo_whenAnchorLacksFix() {
        val g = ArGeoGuidance.from(Anchor(), deviceFix = null)
        assertEquals(ArGeoProximityStatus.NO_GEO, g.status)
        assertEquals("NO GEO", g.statusLabel)
        assertEquals("Geo · Local", g.compactChipLabel())
        assertNull(g.distanceMeters)
    }

    @Test
    fun guidance_noDeviceFix_whenLayoutHasGeoButNoGnss() {
        val anchor = Anchor(latitude = anchorLat, longitude = anchorLng, headingDegrees = 12f)
        val g = ArGeoGuidance.from(anchor, deviceFix = null)
        assertEquals(ArGeoProximityStatus.NO_DEVICE_FIX, g.status)
        assertEquals("Geo · No fix", g.compactChipLabel())
        assertEquals(anchorLat, g.anchorLatitude!!, 1e-9)
        assertEquals(12f, g.anchorHeadingDegrees!!, 0.001f)
    }

    @Test
    fun guidance_nearOrigin_within50m() {
        val anchor = Anchor(latitude = anchorLat, longitude = anchorLng, headingDegrees = 0f)
        val nearby = GeographicCoordinateConverter.metersOffsetToLatLng(
            anchorLat, anchorLng, 0f, 10f, 10f
        )
        val g = ArGeoGuidance.from(
            anchor,
            DeviceLocationFix(
                latitude = nearby.latitude,
                longitude = nearby.longitude,
                horizontalAccuracyMeters = 4f
            )
        )
        assertEquals(ArGeoProximityStatus.NEAR_ORIGIN, g.status)
        assertEquals("Geo · Near", g.compactChipLabel())
        assertTrue(g.distanceMeters!! < ArGeoGuidance.DEFAULT_NEAR_ORIGIN_METERS)
        assertTrue(g.distanceMeters!! > 10.0)
    }

    @Test
    fun guidance_tooFar_beyond50m() {
        val anchor = Anchor(latitude = anchorLat, longitude = anchorLng)
        val far = GeographicCoordinateConverter.metersOffsetToLatLng(
            anchorLat, anchorLng, 0f, 200f, 0f
        )
        val g = ArGeoGuidance.from(
            anchor,
            DeviceLocationFix(latitude = far.latitude, longitude = far.longitude)
        )
        assertEquals(ArGeoProximityStatus.TOO_FAR, g.status)
        assertEquals("Geo · Far", g.compactChipLabel())
        assertTrue(g.distanceMeters!! > 50.0)
    }

    @Test
    fun distanceAndBearingHelpers_matchConverter() {
        // Place device ~100 m south of anchor (planning −Z when heading = 0)
        val device = GeographicCoordinateConverter.metersOffsetToLatLng(
            anchorLat, anchorLng, 0f, 0f, -100f
        )
        val dist = ArGeoGuidance.distanceMetersToAnchor(
            anchorLat, anchorLng, device.latitude, device.longitude
        )
        assertEquals(100.0, dist, 0.5)
        val bearing = ArGeoGuidance.bearingDegreesToAnchor(
            anchorLat, anchorLng, device.latitude, device.longitude
        )
        // From south of anchor toward anchor → roughly north (0°)
        assertEquals(0f, bearing, 5f)
    }

    @Test
    fun miniatureScale_presets() {
        assertEquals(1.0f, ArVisualizationScale.FULL.scaleFactor, 0.0001f)
        assertEquals(0.05f, ArVisualizationScale.ONE_TO_20.scaleFactor, 0.0001f)
        assertEquals(0.02f, ArVisualizationScale.ONE_TO_50.scaleFactor, 0.0001f)
        assertFalse(ArVisualizationScale.FULL.isMiniature)
        assertTrue(ArVisualizationScale.ONE_TO_20.isMiniature)
        assertEquals(0.5f, ArVisualizationScale.ONE_TO_20.scaleMeters(10f), 0.0001f)
    }

    @Test
    fun transformer_appliesVisualizationScaleToOffsets() {
        val full = ARCoordinateTransformer.computeWorldPositionPure(
            originX = 0f, originY = 0f, originZ = 0f,
            originYawDegrees = 0f,
            planX = 10f, planZ = 20f,
            visualizationScale = 1f
        )
        assertEquals(10f, full[0], 0.001f)
        assertEquals(20f, full[2], 0.001f)

        val mini = ARCoordinateTransformer.computeWorldPositionPure(
            originX = 0f, originY = 0f, originZ = 0f,
            originYawDegrees = 0f,
            planX = 10f, planZ = 20f,
            visualizationScale = ArVisualizationScale.ONE_TO_20.scaleFactor
        )
        assertEquals(0.5f, mini[0], 0.001f)
        assertEquals(1.0f, mini[2], 0.001f)
    }

    @Test
    fun transformer_defaultScaleRemainsOneToOne() {
        assertEquals(1.0f, ARCoordinateTransformer.METERS_PER_PLANNING_UNIT, 0.0001f)
        val pos = ARCoordinateTransformer.computeWorldPositionPure(
            0f, 0f, 0f, 0f, 5f, 0f
        )
        assertEquals(5f, pos[0], 0.001f)
    }
}
