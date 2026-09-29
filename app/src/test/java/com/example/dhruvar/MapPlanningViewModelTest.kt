package com.example.dhruvar

import com.example.dhruvar.data.repository.InMemoryLayoutRepository
import com.example.dhruvar.domain.location.FakeDeviceLocationController
import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.model.MapInteractionMode
import com.example.dhruvar.domain.model.PlanningSurface
import com.example.dhruvar.domain.spatial.GeographicCoordinateConverter
import com.example.dhruvar.domain.spatial.MapLayoutProjection
import com.example.dhruvar.viewmodel.PlanningViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 4: MapLibre planning ViewModel behaviours (region, placement gate, geo meters).
 */
class MapPlanningViewModelTest {

    private lateinit var viewModel: PlanningViewModel
    private lateinit var repository: InMemoryLayoutRepository

    private val anchorLat = 28.6139
    private val anchorLng = 77.2090

    @Before
    fun setUp() {
        repository = InMemoryLayoutRepository()
        viewModel = PlanningViewModel(
            repository = repository,
            locationController = FakeDeviceLocationController(initialPermissionGranted = true)
        )
    }

    @Test
    fun defaultSurface_isMap() {
        assertEquals(PlanningSurface.MAP, viewModel.uiState.value.planningSurface)
        assertEquals(MapInteractionMode.NAVIGATE, viewModel.uiState.value.mapInteractionMode)
    }

    @Test
    fun setAndClearRegionBounds() {
        val bounds = GeoBounds(28.62, 28.60, 77.22, 77.19)
        assertTrue(viewModel.setRegionBounds(bounds))
        assertNotNull(viewModel.uiState.value.currentLayout.regionBounds)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.clearRegionBounds()
        assertNull(viewModel.uiState.value.currentLayout.regionBounds)
    }

    @Test
    fun setRegionFromViewport_rejectsInvalidBox() {
        assertFalse(
            viewModel.setRegionBoundsFromViewport(
                northLatitude = 28.0,
                southLatitude = 29.0,
                eastLongitude = 77.2,
                westLongitude = 77.1
            )
        )
        assertNull(viewModel.uiState.value.currentLayout.regionBounds)
    }

    @Test
    fun placeAsset_withoutGeographicFix_isBlocked() {
        assertFalse(viewModel.uiState.value.currentLayout.anchor.hasGeographicFix)
        assertFalse(viewModel.placeAssetAtGeographic(28.6140, 77.2095))
        assertTrue(viewModel.uiState.value.currentLayout.objects.isEmpty())
        assertTrue(viewModel.uiState.value.isGeoOriginPanelExpanded)
    }

    @Test
    fun placeAsset_withFix_storesMeterOffsets() {
        assertTrue(viewModel.setGeographicAnchorManual(anchorLat, anchorLng, 0f))
        val target = GeographicCoordinateConverter.metersOffsetToLatLng(
            anchorLat, anchorLng, 0f, 10f, 25f
        )
        assertTrue(viewModel.placeAssetAtGeographic(target.latitude, target.longitude))
        assertEquals(1, viewModel.uiState.value.currentLayout.objects.size)
        val obj = viewModel.uiState.value.currentLayout.objects.first()
        assertEquals(10f, obj.x, 0.25f)
        assertEquals(25f, obj.z, 0.25f)
    }

    @Test
    fun placeAsset_outsideRegion_isRejected() {
        assertTrue(viewModel.setGeographicAnchorManual(anchorLat, anchorLng, 0f))
        assertTrue(
            viewModel.setRegionBounds(
                GeoBounds(
                    northLatitude = anchorLat + 0.0005,
                    southLatitude = anchorLat - 0.0005,
                    eastLongitude = anchorLng + 0.0005,
                    westLongitude = anchorLng - 0.0005
                )
            )
        )
        // Far outside region
        assertFalse(viewModel.placeAssetAtGeographic(anchorLat + 0.05, anchorLng + 0.05))
        assertTrue(viewModel.uiState.value.currentLayout.objects.isEmpty())
    }

    @Test
    fun moveObject_updatesMetersViaConverter() {
        assertTrue(viewModel.setGeographicAnchorManual(anchorLat, anchorLng, 0f))
        val first = GeographicCoordinateConverter.metersOffsetToLatLng(
            anchorLat, anchorLng, 0f, 5f, 5f
        )
        assertTrue(viewModel.placeAssetAtGeographic(first.latitude, first.longitude))
        val id = viewModel.uiState.value.currentLayout.objects.first().id
        val second = GeographicCoordinateConverter.metersOffsetToLatLng(
            anchorLat, anchorLng, 0f, -8f, 12f
        )
        assertTrue(viewModel.moveObjectToGeographic(id, second.latitude, second.longitude))
        val obj = viewModel.uiState.value.currentLayout.objects.first()
        assertEquals(-8f, obj.x, 0.25f)
        assertEquals(12f, obj.z, 0.25f)
    }

    @Test
    fun editTap_selectsNearestThenMoves() {
        assertTrue(viewModel.setGeographicAnchorManual(anchorLat, anchorLng, 0f))
        val at = GeographicCoordinateConverter.metersOffsetToLatLng(
            anchorLat, anchorLng, 0f, 0f, 0f
        )
        assertTrue(viewModel.placeAssetAtGeographic(at.latitude, at.longitude))
        val id = viewModel.uiState.value.currentLayout.objects.first().id

        assertTrue(viewModel.handleMapEditTap(at.latitude, at.longitude))
        assertEquals(id, viewModel.uiState.value.selectedObjectId)

        val moveTo = GeographicCoordinateConverter.metersOffsetToLatLng(
            anchorLat, anchorLng, 0f, 15f, 0f
        )
        assertTrue(viewModel.handleMapEditTap(moveTo.latitude, moveTo.longitude))
        assertEquals(15f, viewModel.uiState.value.currentLayout.objects.first().x, 0.25f)
    }

    @Test
    fun mapPlacement_persistsThroughSaveLoad() = runBlocking {
        assertTrue(viewModel.setGeographicAnchorManual(anchorLat, anchorLng, 0f))
        assertTrue(
            viewModel.setRegionBounds(
                GeoBounds(anchorLat + 0.01, anchorLat - 0.01, anchorLng + 0.01, anchorLng - 0.01)
            )
        )
        val target = GeographicCoordinateConverter.metersOffsetToLatLng(
            anchorLat, anchorLng, 0f, 3f, -4f
        )
        assertTrue(viewModel.placeAssetAtGeographic(target.latitude, target.longitude))
        assertTrue(viewModel.saveCurrentLayoutDirect("Map Plan"))

        val loaded = repository.getLayoutById(viewModel.uiState.value.currentLayout.id)!!
        assertTrue(loaded.anchor.hasGeographicFix)
        assertNotNull(loaded.regionBounds)
        assertEquals(1, loaded.objects.size)
        assertEquals(3f, loaded.objects[0].x, 0.25f)
        assertEquals(-4f, loaded.objects[0].z, 0.25f)

        val projected = MapLayoutProjection.objectCenterLatLng(loaded.anchor, loaded.objects[0])
        assertNotNull(projected)
        assertEquals(target.latitude, projected!!.latitude, 1e-5)
        assertEquals(target.longitude, projected.longitude, 1e-5)
    }

    @Test
    fun projection_rejectsOutsideRegionPolicy() {
        val bounds = GeoBounds(29.0, 28.0, 78.0, 77.0)
        assertTrue(MapLayoutProjection.isInsideRegionOrUnbounded(bounds, 28.5, 77.5))
        assertFalse(MapLayoutProjection.isInsideRegionOrUnbounded(bounds, 30.0, 77.5))
        assertTrue(MapLayoutProjection.isInsideRegionOrUnbounded(null, 30.0, 77.5))
    }
}
