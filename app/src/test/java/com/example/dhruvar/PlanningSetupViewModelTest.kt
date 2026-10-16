package com.example.dhruvar

import com.example.dhruvar.data.repository.InMemoryLayoutRepository
import com.example.dhruvar.domain.location.DeviceLocationFix
import com.example.dhruvar.domain.location.FakeDeviceLocationController
import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.ChosenMapLocation
import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.MapInteractionMode
import com.example.dhruvar.domain.model.PlaceSearchHit
import com.example.dhruvar.domain.model.PlanningSetupStep
import com.example.dhruvar.domain.spatial.RegionDraftGeometry
import com.example.dhruvar.ui.map.MapCameraBridge
import com.example.dhruvar.viewmodel.PlanningViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlanningSetupViewModelTest {

    private lateinit var viewModel: PlanningViewModel
    private lateinit var fakeLocation: FakeDeviceLocationController

    private val region = GeoBounds(28.62, 28.60, 77.22, 77.19)
    private val geoAnchor = Anchor(latitude = 28.6139, longitude = 77.2090, headingDegrees = 0f)
    private val chosen = ChosenMapLocation(28.61, 77.21, "Delhi", 14.0)

    @Before
    fun setUp() {
        fakeLocation = FakeDeviceLocationController(initialPermissionGranted = true)
        viewModel = PlanningViewModel(
            repository = InMemoryLayoutRepository(),
            locationController = fakeLocation
        )
    }

    @Test
    fun createNewLayout_startsAtFindLocation() {
        viewModel.setChosenLocation(chosen)
        viewModel.setMapInteractionMode(MapInteractionMode.PLACE_ASSET)
        viewModel.createNewLayout("New Field")

        val state = viewModel.uiState.value
        assertEquals(PlanningSetupStep.FIND_LOCATION, state.setupStep)
        assertTrue(state.isBrandNewLayout)
        assertNull(state.setupSession.chosenLocation)
        assertTrue(state.placeSearch.isExpanded)
        assertEquals(MapInteractionMode.NAVIGATE, state.mapInteractionMode)
        assertEquals("New Field", state.currentLayout.name)
    }

    @Test
    fun loadLayout_withRegionAndOrigin_opensWorkspace() {
        val layout = Layout(
            name = "Complete",
            anchor = geoAnchor,
            regionBounds = region
        )
        viewModel.loadLayout(layout)

        val state = viewModel.uiState.value
        assertEquals(PlanningSetupStep.PLACE_ASSETS, state.setupStep)
        assertEquals(false, state.isBrandNewLayout)
        assertEquals(MapInteractionMode.NAVIGATE, state.mapInteractionMode)
    }

    @Test
    fun loadLayout_regionOnly_opensSetOrigin() {
        viewModel.loadLayout(Layout(name = "Region", regionBounds = region))
        assertEquals(PlanningSetupStep.SET_ORIGIN, viewModel.uiState.value.setupStep)
        assertEquals(MapInteractionMode.NAVIGATE, viewModel.uiState.value.mapInteractionMode)
    }

    @Test
    fun loadLayout_originWithoutRegion_legacyOpensSetRegion() {
        viewModel.loadLayout(Layout(name = "Legacy", anchor = geoAnchor))
        assertEquals(PlanningSetupStep.SET_REGION, viewModel.uiState.value.setupStep)
        assertEquals(MapInteractionMode.SELECT_REGION, viewModel.uiState.value.mapInteractionMode)
    }

    @Test
    fun advanceSetupStep_blockedWithoutLocation() {
        viewModel.createNewLayout()
        viewModel.advanceSetupStep()
        assertEquals(PlanningSetupStep.FIND_LOCATION, viewModel.uiState.value.setupStep)
        assertEquals(
            PlanningViewModel.ADVANCE_NEEDS_LOCATION,
            viewModel.uiState.value.userFeedbackMessage
        )
    }

    @Test
    fun advanceAndGoBack_findLocationToOrigin() {
        viewModel.createNewLayout()
        viewModel.setChosenLocation(chosen)
        viewModel.advanceSetupStep()
        assertEquals(PlanningSetupStep.SET_REGION, viewModel.uiState.value.setupStep)
        assertEquals(MapInteractionMode.SELECT_REGION, viewModel.uiState.value.mapInteractionMode)
        assertEquals(false, viewModel.uiState.value.isBrandNewLayout)
        assertEquals(chosen, viewModel.uiState.value.setupSession.chosenLocation)
        MapCameraBridge.target.value?.let { MapCameraBridge.consumed(it) }

        assertTrue(viewModel.setRegionBounds(region))
        assertEquals(
            "Region confirm does not auto-advance in GS-01",
            PlanningSetupStep.SET_REGION,
            viewModel.uiState.value.setupStep
        )

        viewModel.advanceSetupStep()
        assertEquals(PlanningSetupStep.SET_ORIGIN, viewModel.uiState.value.setupStep)
        assertEquals(MapInteractionMode.NAVIGATE, viewModel.uiState.value.mapInteractionMode)

        viewModel.goToPreviousSetupStep()
        assertEquals(PlanningSetupStep.SET_REGION, viewModel.uiState.value.setupStep)
        assertEquals(chosen, viewModel.uiState.value.setupSession.chosenLocation)

        viewModel.goToPreviousSetupStep()
        assertEquals(PlanningSetupStep.FIND_LOCATION, viewModel.uiState.value.setupStep)
        assertEquals(chosen, viewModel.uiState.value.setupSession.chosenLocation)

        viewModel.goToPreviousSetupStep()
        assertEquals(PlanningSetupStep.FIND_LOCATION, viewModel.uiState.value.setupStep)
    }

    @Test
    fun advanceSetupStep_blockedWithoutRegion() {
        viewModel.createNewLayout()
        viewModel.setChosenLocation(chosen)
        viewModel.advanceSetupStep()
        assertEquals(PlanningSetupStep.SET_REGION, viewModel.uiState.value.setupStep)

        viewModel.advanceSetupStep()
        assertEquals(PlanningSetupStep.SET_REGION, viewModel.uiState.value.setupStep)
        assertEquals(
            PlanningViewModel.ADVANCE_NEEDS_REGION,
            viewModel.uiState.value.userFeedbackMessage
        )
    }

    @Test
    fun searchHit_onFindLocation_storesChosenPlaceAndDoesNotAdvance() {
        viewModel.createNewLayout()
        val before = viewModel.uiState.value.currentLayout
        val hit = PlaceSearchHit(
            id = "n1",
            name = "Delhi",
            englishName = null,
            kind = "place",
            subtype = "city",
            rank = 1,
            latitude = 28.61,
            longitude = 77.21
        )
        viewModel.onSearchResultSelected(hit)

        val state = viewModel.uiState.value
        assertEquals(PlanningSetupStep.FIND_LOCATION, state.setupStep)
        assertEquals("Delhi", state.setupSession.chosenLocation?.displayName)
        assertEquals(before.anchor, state.currentLayout.anchor)
        assertEquals(before.regionBounds, state.currentLayout.regionBounds)
        assertTrue(state.placeSearch.isExpanded)
        assertTrue(state.placeSearch.results.isEmpty())
        assertEquals("Centered on Delhi", state.userFeedbackMessage)

        viewModel.advanceSetupStep()
        assertEquals(PlanningSetupStep.SET_REGION, viewModel.uiState.value.setupStep)
        assertEquals("Delhi", viewModel.uiState.value.setupSession.chosenLocation?.displayName)

        MapCameraBridge.target.value?.let { MapCameraBridge.consumed(it) }
    }

    @Test
    fun setChosenLocation_doesNotWriteLayoutOrAdvance() {
        viewModel.createNewLayout()
        viewModel.setChosenLocation(chosen)
        assertEquals(PlanningSetupStep.FIND_LOCATION, viewModel.uiState.value.setupStep)
        assertNull(viewModel.uiState.value.currentLayout.regionBounds)
        assertEquals(chosen, viewModel.uiState.value.setupSession.chosenLocation)
    }

    @Test
    fun clearRegion_onCompleteLayout_dropsToSetRegion() {
        viewModel.loadLayout(Layout(anchor = geoAnchor, regionBounds = region))
        viewModel.clearRegionBounds()
        assertEquals(PlanningSetupStep.SET_REGION, viewModel.uiState.value.setupStep)
        assertTrue(viewModel.uiState.value.currentLayout.anchor.hasGeographicFix)
        assertNull(viewModel.uiState.value.currentLayout.regionBounds)
    }

    @Test
    fun advance_fromOrigin_blockedWithoutFix() {
        viewModel.createNewLayout()
        viewModel.setChosenLocation(chosen)
        viewModel.advanceSetupStep()
        viewModel.setRegionBounds(region)
        viewModel.advanceSetupStep()
        viewModel.advanceSetupStep()
        assertEquals(PlanningSetupStep.SET_ORIGIN, viewModel.uiState.value.setupStep)
        assertEquals(
            PlanningViewModel.ADVANCE_NEEDS_ORIGIN,
            viewModel.uiState.value.userFeedbackMessage
        )
    }

    @Test
    fun goToPrevious_fromPlaceAssets_isNoOp() {
        viewModel.loadLayout(Layout(anchor = geoAnchor, regionBounds = region))
        assertEquals(PlanningSetupStep.PLACE_ASSETS, viewModel.uiState.value.setupStep)
        viewModel.goToPreviousSetupStep()
        assertEquals(PlanningSetupStep.PLACE_ASSETS, viewModel.uiState.value.setupStep)
    }

    @Test
    fun clearOrigin_onCompleteLayout_dropsToSetOrigin() {
        viewModel.loadLayout(Layout(anchor = geoAnchor, regionBounds = region))
        viewModel.clearGeographicAnchor()
        assertEquals(PlanningSetupStep.SET_ORIGIN, viewModel.uiState.value.setupStep)
        assertEquals(region, viewModel.uiState.value.currentLayout.regionBounds)
    }

    @Test
    fun setMapInteractionMode_isCoercedOnFindLocation() {
        viewModel.createNewLayout()
        viewModel.setMapInteractionMode(MapInteractionMode.PLACE_ASSET)
        assertEquals(MapInteractionMode.NAVIGATE, viewModel.uiState.value.mapInteractionMode)
        viewModel.setMapInteractionMode(MapInteractionMode.SELECT_REGION)
        assertNotEquals(MapInteractionMode.SELECT_REGION, viewModel.uiState.value.mapInteractionMode)
    }

    @Test
    fun confirmRegionAndAdvance_validBounds_movesToSetOrigin() {
        enterSetRegion()
        assertTrue(viewModel.confirmRegionAndAdvance(region))
        assertEquals(region, viewModel.uiState.value.currentLayout.regionBounds)
        assertEquals(PlanningSetupStep.SET_ORIGIN, viewModel.uiState.value.setupStep)
        assertEquals(MapInteractionMode.NAVIGATE, viewModel.uiState.value.mapInteractionMode)
    }

    @Test
    fun confirmRegionAndAdvance_tinyBox_staysOnSetRegion() {
        enterSetRegion()
        val tiny = RegionDraftGeometry.boundsBetween(
            28.6139,
            77.2090,
            28.6139 + 0.00018,
            77.2090 + 0.00018
        )
        assertFalse(tiny == null)
        assertFalse(viewModel.confirmRegionAndAdvance(tiny!!))
        assertEquals(PlanningSetupStep.SET_REGION, viewModel.uiState.value.setupStep)
        assertNull(viewModel.uiState.value.currentLayout.regionBounds)
    }

    @Test
    fun setRegionBoundsFromViewport_onSetRegion_advances() {
        enterSetRegion()
        assertTrue(
            viewModel.setRegionBoundsFromViewport(
                northLatitude = 28.62,
                southLatitude = 28.60,
                eastLongitude = 77.22,
                westLongitude = 77.19
            )
        )
        assertEquals(PlanningSetupStep.SET_ORIGIN, viewModel.uiState.value.setupStep)
        assertEquals(region, viewModel.uiState.value.currentLayout.regionBounds)
    }

    @Test
    fun clearRegion_onSetRegion_staysSetRegion() {
        enterSetRegion()
        assertTrue(viewModel.setRegionBounds(region))
        viewModel.clearRegionBounds()
        assertEquals(PlanningSetupStep.SET_REGION, viewModel.uiState.value.setupStep)
        assertNull(viewModel.uiState.value.currentLayout.regionBounds)
    }

    @Test
    fun enteringSetRegion_fliesToChosenLocation() {
        viewModel.createNewLayout()
        viewModel.setChosenLocation(chosen)
        viewModel.advanceSetupStep()
        val target = MapCameraBridge.target.value
        assertEquals(chosen.latitude, target?.latitude)
        assertEquals(chosen.longitude, target?.longitude)
        assertEquals(chosen.zoom, target?.zoom)
        target?.let { MapCameraBridge.consumed(it) }
    }

    @Test
    fun mapTapInsideRegion_setsOriginAndAdvances() {
        enterSetOrigin()
        assertTrue(viewModel.setGeographicAnchorFromMap(28.6139, 77.2090, headingDegrees = null))
        val state = viewModel.uiState.value
        assertTrue(state.currentLayout.anchor.hasGeographicFix)
        assertEquals(0f, state.currentLayout.anchor.headingDegrees!!, 0.001f)
        assertEquals(PlanningSetupStep.PLACE_ASSETS, state.setupStep)
        assertEquals(MapInteractionMode.NAVIGATE, state.mapInteractionMode)
        assertFalse(state.isGeoOriginPanelExpanded)
    }

    @Test
    fun mapTapOutsideRegion_doesNotSetOriginOrAdvance() {
        enterSetOrigin()
        viewModel.setMapInteractionMode(MapInteractionMode.SET_ANCHOR)
        assertFalse(viewModel.setGeographicAnchorFromMap(28.70, 77.21, headingDegrees = 12f))
        val state = viewModel.uiState.value
        assertFalse(state.currentLayout.anchor.hasGeographicFix)
        assertEquals(PlanningSetupStep.SET_ORIGIN, state.setupStep)
        assertEquals(MapInteractionMode.SET_ANCHOR, state.mapInteractionMode)
        assertEquals(
            PlanningViewModel.ORIGIN_MUST_BE_INSIDE_REGION,
            state.userFeedbackMessage
        )
    }

    @Test
    fun deviceFixInsideRegion_stampsAndAdvances() {
        enterSetOrigin()
        fakeLocation.emitFix(
            DeviceLocationFix(latitude = 28.6139, longitude = 77.2090, horizontalAccuracyMeters = 4f)
        )
        assertTrue(viewModel.setGeographicAnchorFromDeviceFix(45f))
        val state = viewModel.uiState.value
        assertTrue(state.currentLayout.anchor.hasGeographicFix)
        assertEquals(45f, state.currentLayout.anchor.headingDegrees!!, 0.001f)
        assertEquals(PlanningSetupStep.PLACE_ASSETS, state.setupStep)
        assertEquals("Origin stamped", state.userFeedbackMessage)
    }

    @Test
    fun deviceFixOutsideRegion_doesNotStampOrAdvance() {
        enterSetOrigin()
        fakeLocation.emitFix(
            DeviceLocationFix(latitude = 19.0760, longitude = 72.8777, horizontalAccuracyMeters = 5f)
        )
        assertFalse(viewModel.setGeographicAnchorFromDeviceFix(10f))
        val state = viewModel.uiState.value
        assertFalse(state.currentLayout.anchor.hasGeographicFix)
        assertEquals(PlanningSetupStep.SET_ORIGIN, state.setupStep)
        assertEquals(PlanningViewModel.GPS_OUTSIDE_REGION, state.userFeedbackMessage)
    }

    @Test
    fun poorAccuracyInsideRegion_stillStamps() {
        enterSetOrigin()
        fakeLocation.emitFix(
            DeviceLocationFix(latitude = 28.6139, longitude = 77.2090, horizontalAccuracyMeters = 22f)
        )
        assertTrue(viewModel.setGeographicAnchorFromDeviceFix(0f))
        assertEquals(PlanningSetupStep.PLACE_ASSETS, viewModel.uiState.value.setupStep)
        assertEquals(
            PlanningViewModel.SITE_LEVEL_ACCURACY_WARNING,
            viewModel.uiState.value.geoAccuracyWarning
        )
    }

    @Test
    fun continueUsesExistingOriginAfterReturningToRegion() {
        viewModel.loadLayout(Layout(name = "Legacy", anchor = geoAnchor))
        assertEquals(PlanningSetupStep.SET_REGION, viewModel.uiState.value.setupStep)
        assertTrue(viewModel.confirmRegionAndAdvance(region))
        assertEquals(PlanningSetupStep.SET_ORIGIN, viewModel.uiState.value.setupStep)
        assertTrue(viewModel.uiState.value.currentLayout.anchor.hasGeographicFix)

        viewModel.advanceSetupStep()
        assertEquals(PlanningSetupStep.PLACE_ASSETS, viewModel.uiState.value.setupStep)
    }

    @Test
    fun confirmingARegionThatExcludesOrigin_clearsOriginAndStaysOnSetOrigin() {
        viewModel.loadLayout(Layout(name = "Legacy", anchor = geoAnchor))
        val elsewhere = GeoBounds(19.10, 19.05, 72.90, 72.85)
        assertTrue(viewModel.confirmRegionAndAdvance(elsewhere))
        val state = viewModel.uiState.value
        assertEquals(PlanningSetupStep.SET_ORIGIN, state.setupStep)
        assertFalse(state.currentLayout.anchor.hasGeographicFix)
        assertEquals(elsewhere, state.currentLayout.regionBounds)
        assertEquals(PlanningViewModel.ORIGIN_OUTSIDE_NEW_REGION, state.userFeedbackMessage)
    }

    @Test
    fun placeAssetsCannotMoveOriginOutsideRegion() {
        viewModel.loadLayout(Layout(name = "Done", anchor = geoAnchor, regionBounds = region))
        assertEquals(PlanningSetupStep.PLACE_ASSETS, viewModel.uiState.value.setupStep)
        assertFalse(viewModel.setGeographicAnchorFromMap(28.70, 77.21, headingDegrees = 0f))
        assertEquals(28.6139, viewModel.uiState.value.currentLayout.anchor.latitude!!, 1e-9)
        assertEquals(PlanningSetupStep.PLACE_ASSETS, viewModel.uiState.value.setupStep)
    }

    private fun enterSetRegion() {
        viewModel.createNewLayout()
        viewModel.setChosenLocation(chosen)
        viewModel.advanceSetupStep()
        MapCameraBridge.target.value?.let { MapCameraBridge.consumed(it) }
    }

    private fun enterSetOrigin() {
        enterSetRegion()
        assertTrue(viewModel.confirmRegionAndAdvance(region))
        assertEquals(PlanningSetupStep.SET_ORIGIN, viewModel.uiState.value.setupStep)
    }
}
