package com.example.dhruvar

import com.example.dhruvar.data.repository.InMemoryLayoutRepository
import com.example.dhruvar.domain.location.DeviceLocationFix
import com.example.dhruvar.domain.location.DeviceLocationState
import com.example.dhruvar.domain.location.FakeDeviceLocationController
import com.example.dhruvar.domain.location.GeoOriginReadiness
import com.example.dhruvar.domain.location.LocationAvailability
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
 * Phase 3: GNSS geographic origin ViewModel behaviour with a fake location source.
 */
class GeoOriginViewModelTest {

    private lateinit var fakeLocation: FakeDeviceLocationController
    private lateinit var repository: InMemoryLayoutRepository
    private lateinit var viewModel: PlanningViewModel

    @Before
    fun setUp() {
        fakeLocation = FakeDeviceLocationController(initialPermissionGranted = false)
        repository = InMemoryLayoutRepository()
        viewModel = PlanningViewModel(repository = repository, locationController = fakeLocation)
    }

    @Test
    fun permissionMissing_stateReflectsDenied() {
        viewModel.onLocationPermissionResult(false)

        val loc = viewModel.uiState.value.locationState
        assertFalse(loc.permissionGranted)
        assertEquals(LocationAvailability.PERMISSION_MISSING, loc.availability)
        assertEquals(GeoOriginReadiness.NO_FIX, loc.readiness)
        assertFalse(viewModel.setGeographicAnchorFromDeviceFix(12f))
        assertFalse(viewModel.uiState.value.currentLayout.anchor.hasGeographicFix)
    }

    @Test
    fun fixApplied_anchorHasGeographicFix() {
        fakeLocation.onPermissionResult(true)
        fakeLocation.emitFix(
            DeviceLocationFix(
                latitude = 28.6139,
                longitude = 77.2090,
                horizontalAccuracyMeters = 4.5f,
                timestampEpochMs = 1_000L
            )
        )

        assertTrue(viewModel.setGeographicAnchorFromDeviceFix(45.5f))

        val anchor = viewModel.uiState.value.currentLayout.anchor
        assertTrue(anchor.hasGeographicFix)
        assertEquals(28.6139, anchor.latitude!!, 1e-9)
        assertEquals(77.2090, anchor.longitude!!, 1e-9)
        assertEquals(4.5f, anchor.horizontalAccuracyMeters!!, 0.001f)
        assertEquals(45.5f, anchor.headingDegrees!!, 0.001f)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        assertNull(viewModel.uiState.value.geoAccuracyWarning)
        assertEquals(GeoOriginReadiness.READY, viewModel.uiState.value.locationState.readiness)
    }

    @Test
    fun poorAccuracy_stillSetsWithWarning() {
        fakeLocation.onPermissionResult(true)
        fakeLocation.emitFix(
            DeviceLocationFix(
                latitude = 19.0760,
                longitude = 72.8777,
                horizontalAccuracyMeters = 22f
            )
        )

        assertTrue(viewModel.setGeographicAnchorFromDeviceFix(10f))
        assertTrue(viewModel.uiState.value.currentLayout.anchor.hasGeographicFix)
        assertEquals(
            PlanningViewModel.SITE_LEVEL_ACCURACY_WARNING,
            viewModel.uiState.value.geoAccuracyWarning
        )
        assertEquals(GeoOriginReadiness.ACCURACY_POOR, viewModel.uiState.value.locationState.readiness)
    }

    @Test
    fun clear_resetsGeoFieldsToNull() {
        fakeLocation.onPermissionResult(true)
        fakeLocation.emitFix(
            DeviceLocationFix(latitude = 1.0, longitude = 2.0, horizontalAccuracyMeters = 3f)
        )
        assertTrue(viewModel.setGeographicAnchorFromDeviceFix(90f))

        viewModel.clearGeographicAnchor()

        val anchor = viewModel.uiState.value.currentLayout.anchor
        assertFalse(anchor.hasGeographicFix)
        assertNull(anchor.latitude)
        assertNull(anchor.longitude)
        assertNull(anchor.headingDegrees)
        assertNull(anchor.horizontalAccuracyMeters)
        assertNull(viewModel.uiState.value.geoAccuracyWarning)
    }

    @Test
    fun manualSet_validatesAndApplies() {
        assertFalse(viewModel.setGeographicAnchorManual(95.0, 77.0, 0f))
        assertFalse(viewModel.uiState.value.currentLayout.anchor.hasGeographicFix)

        assertTrue(viewModel.setGeographicAnchorManual(28.5, 77.1, 370f))
        val anchor = viewModel.uiState.value.currentLayout.anchor
        assertTrue(anchor.hasGeographicFix)
        assertEquals(10f, anchor.headingDegrees!!, 0.001f) // normalized 370 → 10
    }

    @Test
    fun captureHeading_updatesHeadingOnly() {
        viewModel.updateGeographicHeading(180f)
        assertEquals(180f, viewModel.uiState.value.currentLayout.anchor.headingDegrees!!, 0.001f)
        assertFalse(viewModel.uiState.value.currentLayout.anchor.hasGeographicFix)
    }

    @Test
    fun geoFields_persistThroughSaveAndLoad() = runBlocking {
        fakeLocation.onPermissionResult(true)
        fakeLocation.emitFix(
            DeviceLocationFix(
                latitude = 12.9716,
                longitude = 77.5946,
                horizontalAccuracyMeters = 5f
            )
        )
        assertTrue(viewModel.setGeographicAnchorFromDeviceFix(30f))
        assertTrue(viewModel.saveCurrentLayoutDirect("Geo Persisted Plan"))

        val loaded = repository.getLayoutById(viewModel.uiState.value.currentLayout.id)
        assertNotNull(loaded)
        assertTrue(loaded!!.anchor.hasGeographicFix)
        assertEquals(12.9716, loaded.anchor.latitude!!, 1e-9)
        assertEquals(77.5946, loaded.anchor.longitude!!, 1e-9)
        assertEquals(30f, loaded.anchor.headingDegrees!!, 0.001f)
        assertEquals(5f, loaded.anchor.horizontalAccuracyMeters!!, 0.001f)
    }

    @Test
    fun deviceLocationState_poorAccuracyThresholdIs15m() {
        assertEquals(15f, DeviceLocationState.POOR_ACCURACY_METERS, 0.001f)
    }
}
