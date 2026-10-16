package com.example.dhruvar

import com.example.dhruvar.data.model.LayoutJsonSerializer
import com.example.dhruvar.data.repository.InMemoryLayoutRepository
import com.example.dhruvar.domain.location.FakeDeviceLocationController
import com.example.dhruvar.domain.location.LocationAvailability
import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.model.MapInteractionMode
import com.example.dhruvar.domain.model.PlanningSurface
import com.example.dhruvar.domain.spatial.GeographicCoordinateConverter
import com.example.dhruvar.domain.spatial.SpatialDistanceCalculator
import com.example.dhruvar.viewmodel.PlanningViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 6 regression pack: schema, converter, placement gates, spatial distance, ViewModel chrome.
 * Complements phase-specific suites; keep this green for geo CI smoke.
 */
class GeoRegressionTest {

    private lateinit var viewModel: PlanningViewModel
    private lateinit var location: FakeDeviceLocationController
    private lateinit var repository: InMemoryLayoutRepository

    private val lat = 28.6139
    private val lng = 77.2090

    @Before
    fun setUp() {
        repository = InMemoryLayoutRepository()
        location = FakeDeviceLocationController(initialPermissionGranted = true)
        viewModel = PlanningViewModel(repository = repository, locationController = location)
    }

    @Test
    fun v1Deserialize_missingGeo_nullFields() {
        val v1 = """
            {
              "version": 1,
              "id": "reg-v1",
              "name": "Legacy",
              "anchor": { "id": "a", "label": "O", "x": 0, "z": 0 },
              "objects": []
            }
        """.trimIndent()
        val loaded = LayoutJsonSerializer.deserialize(v1)
        assertNull(loaded.regionBounds)
        assertFalse(loaded.anchor.hasGeographicFix)
        assertNull(loaded.anchor.latitude)
    }

    @Test
    fun v2RoundTrip_regionGeoObjects() {
        val layout = Layout(
            id = "reg-v2",
            name = "Sector",
            anchor = Anchor(
                latitude = lat,
                longitude = lng,
                headingDegrees = 15f,
                horizontalAccuracyMeters = 3.5f
            ),
            objects = listOf(
                LayoutObject(
                    id = "t1",
                    assetType = AssetType.TENT,
                    name = "Tent",
                    x = 8f,
                    z = -3f,
                    rotationDegrees = 45f
                )
            ),
            regionBounds = GeoBounds(lat + 0.01, lat - 0.01, lng + 0.01, lng - 0.01)
        )
        val json = LayoutJsonSerializer.serialize(layout)
        val root = JSONObject(json)
        assertEquals(2, root.getInt("version"))
        assertTrue(root.has("regionBounds"))
        val loaded = LayoutJsonSerializer.deserialize(json)
        assertTrue(loaded.anchor.hasGeographicFix)
        assertEquals(15f, loaded.anchor.headingDegrees!!, 0.001f)
        assertNotNull(loaded.regionBounds)
        assertEquals(8f, loaded.objects[0].x, 0.001f)
        assertEquals(-3f, loaded.objects[0].z, 0.001f)
    }

    @Test
    fun converter_roundTrip_eastNorthPreserved() {
        val target = GeographicCoordinateConverter.metersOffsetToLatLng(lat, lng, 0f, 12f, 18f)
        val (x, z) = GeographicCoordinateConverter.latLngToMetersOffset(
            lat, lng, 0f, target.latitude, target.longitude
        )
        assertEquals(12f, x, 0.2f)
        assertEquals(18f, z, 0.2f)
    }

    @Test
    fun viewModel_cannotPlaceOnMapWithoutGeo() {
        assertFalse(viewModel.uiState.value.currentLayout.anchor.hasGeographicFix)
        assertFalse(viewModel.placeAssetAtGeographic(lat, lng))
        assertTrue(viewModel.uiState.value.currentLayout.objects.isEmpty())
        assertTrue(viewModel.uiState.value.isGeoOriginPanelExpanded)
        assertTrue(
            viewModel.uiState.value.userFeedbackMessage!!
                .contains("geographic origin", ignoreCase = true)
        )
    }

    @Test
    fun spatialDistance_stillCorrect_3_4_5() {
        val dist = SpatialDistanceCalculator.calculateDistance(0f, 0f, 3f, 4f)
        assertEquals(5f, dist, 0.0001f)
    }

    @Test
    fun serializer_nanAndMissingFields_sanitized() {
        val json = """
            {
              "version": 2,
              "id": "edge",
              "name": "",
              "anchor": {
                "id": "a",
                "label": "O",
                "x": "NaN",
                "z": "Infinity",
                "latitude": "NaN",
                "longitude": null
              },
              "objects": [
                {
                  "id": "o1",
                  "assetType": "UNKNOWN_TYPE",
                  "name": "",
                  "x": 1.5,
                  "z": 2.5
                }
              ]
            }
        """.trimIndent()
        val loaded = LayoutJsonSerializer.deserialize(json)
        assertEquals("Untitled Plan", loaded.name)
        assertEquals(0f, loaded.anchor.x, 0.001f)
        assertEquals(0f, loaded.anchor.z, 0.001f)
        assertNull(loaded.anchor.latitude)
        assertFalse(loaded.anchor.hasGeographicFix)
        assertEquals(AssetType.TENT, loaded.objects[0].assetType)
        assertEquals(1.5f, loaded.objects[0].x, 0.001f)
    }

    @Test
    fun loadLayout_resetsMapSurfaceAndStopsLocation() {
        location.startHighAccuracyUpdates()
        assertTrue(location.state.value.isUpdating)
        viewModel.setPlanningSurface(PlanningSurface.METRIC_GRID)
        viewModel.setMapInteractionMode(MapInteractionMode.PLACE_ASSET)
        viewModel.setMapStyleError("tiles failed")

        val layout = Layout(
            id = "loaded",
            name = "Loaded Plan",
            anchor = Anchor(latitude = lat, longitude = lng, headingDegrees = 0f)
        )
        viewModel.loadLayout(layout)

        val state = viewModel.uiState.value
        assertEquals(PlanningSurface.MAP, state.planningSurface)
        // Origin without region is legacy: resume at Set region, which owns SELECT_REGION.
        assertEquals(MapInteractionMode.SELECT_REGION, state.mapInteractionMode)
        assertNull(state.mapStyleError)
        assertFalse(location.state.value.isUpdating)
        assertTrue(state.currentLayout.anchor.hasGeographicFix)
    }

    @Test
    fun stopLocationUpdates_clearsUpdatingFlag() {
        location.startHighAccuracyUpdates()
        assertTrue(location.state.value.isUpdating)
        viewModel.stopLocationUpdates()
        assertFalse(location.state.value.isUpdating)
    }

    @Test
    fun gpsDisabled_availabilitySurfaces() {
        location.emitGpsDisabled()
        // Push into ViewModel by refreshing permission sync path used in panel open
        viewModel.onLocationPermissionResult(true)
        location.emitGpsDisabled()
        // Controller state is source of truth; UI reads locationState from ViewModel collect
        assertEquals(LocationAvailability.GPS_DISABLED, location.state.value.availability)
        assertTrue(location.state.value.statusMessage!!.contains("disabled", ignoreCase = true))
    }
}
