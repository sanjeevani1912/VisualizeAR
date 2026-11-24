package com.example.dhruvar

import com.example.dhruvar.data.repository.InMemoryLayoutRepository
import com.example.dhruvar.domain.location.DeviceLocationFix
import com.example.dhruvar.domain.location.FakeDeviceLocationController
import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.model.ChosenMapLocation
import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.MapInteractionMode
import com.example.dhruvar.domain.model.PlaceSearchHit
import com.example.dhruvar.domain.model.PlanningSetupStep
import com.example.dhruvar.domain.spatial.RegionDraftGeometry
import com.example.dhruvar.domain.spatial.RegionWorkspaceGeometry
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
    fun loadLayout_empty_opensFindLocation() {
        viewModel.loadLayout(Layout(name = "Blank"))
        assertEquals(PlanningSetupStep.FIND_LOCATION, viewModel.uiState.value.setupStep)
        assertNull(viewModel.uiState.value.setupSession.chosenLocation)
    }

    @Test
    fun loadLayout_originWithoutRegion_legacyOpensSetRegion() {
        viewModel.loadLayout(Layout(name = "Legacy", anchor = geoAnchor))
        assertEquals(PlanningSetupStep.SET_REGION, viewModel.uiState.value.setupStep)
        assertEquals(MapInteractionMode.SELECT_REGION, viewModel.uiState.value.mapInteractionMode)
    }

    @Test
    fun advanceSetupStep_notBlockedWithoutLocation() {
        viewModel.createNewLayout()
        viewModel.advanceSetupStep()
        assertEquals(PlanningSetupStep.SET_REGION, viewModel.uiState.value.setupStep)
        assertNull(viewModel.uiState.value.setupSession.chosenLocation)
    }

    @Test
    fun beginAutoLocate_centersOnFirstGnssFix() {
        viewModel.createNewLayout()
        viewModel.beginAutoLocate()
        assertNull(viewModel.uiState.value.setupSession.chosenLocation)

        fakeLocation.emitFix(DeviceLocationFix(latitude = 12.9716, longitude = 77.5946, horizontalAccuracyMeters = 4f))
        viewModel.refreshLocation()

        val location = viewModel.uiState.value.setupSession.chosenLocation
        assertEquals(12.9716, location!!.latitude, 1e-9)
        assertEquals(77.5946, location.longitude, 1e-9)
        assertEquals(PlanningViewModel.CURRENT_LOCATION_NAME, location.displayName)
        // Receiver is switched off once the map has been centered
        assertFalse(fakeLocation.state.value.isUpdating)
    }

    @Test
    fun beginAutoLocate_roughFixIsRefinedUntilAccurate() {
        viewModel.createNewLayout()
        viewModel.beginAutoLocate()

        // Network-grade fix: shown, but the search keeps going
        fakeLocation.emitFix(
            DeviceLocationFix(latitude = 12.97, longitude = 77.59, horizontalAccuracyMeters = 400f, satelliteDerived = false)
        )
        viewModel.refreshLocation()
        assertEquals(12.97, viewModel.uiState.value.setupSession.chosenLocation!!.latitude, 1e-9)
        assertTrue(fakeLocation.state.value.isUpdating)

        // A worse fix never moves the map back
        fakeLocation.emitFix(
            DeviceLocationFix(latitude = 13.50, longitude = 78.00, horizontalAccuracyMeters = 900f, satelliteDerived = false)
        )
        viewModel.refreshLocation()
        assertEquals(12.97, viewModel.uiState.value.setupSession.chosenLocation!!.latitude, 1e-9)

        // A satellite fix that is still converging replaces it and the search continues
        fakeLocation.emitFix(DeviceLocationFix(latitude = 12.9710, longitude = 77.5940, horizontalAccuracyMeters = 40f))
        viewModel.refreshLocation()
        assertEquals(12.9710, viewModel.uiState.value.setupSession.chosenLocation!!.latitude, 1e-9)
        assertTrue(fakeLocation.state.value.isUpdating)

        // An accurate satellite fix ends the search
        fakeLocation.emitFix(DeviceLocationFix(latitude = 12.97162, longitude = 77.59458, horizontalAccuracyMeters = 5f))
        viewModel.refreshLocation()
        assertEquals(12.97162, viewModel.uiState.value.setupSession.chosenLocation!!.latitude, 1e-9)
        assertFalse(fakeLocation.state.value.isUpdating)
    }

    @Test
    fun beginAutoLocate_onNewPlanKeepsAPlaceTheUserAlreadyChose() {
        viewModel.createNewLayout()
        viewModel.setChosenLocation(chosen)
        viewModel.beginAutoLocate(replaceChosenLocation = false)
        fakeLocation.emitFix(DeviceLocationFix(latitude = 12.9716, longitude = 77.5946))
        viewModel.refreshLocation()
        assertEquals("Delhi", viewModel.uiState.value.setupSession.chosenLocation!!.displayName)
    }

    @Test
    fun detectButton_replacesTheChosenPlace() {
        viewModel.createNewLayout()
        viewModel.setChosenLocation(chosen)
        viewModel.beginAutoLocate(replaceChosenLocation = true)
        fakeLocation.emitFix(DeviceLocationFix(latitude = 12.9716, longitude = 77.5946))
        viewModel.refreshLocation()
        assertEquals(
            PlanningViewModel.CURRENT_LOCATION_NAME,
            viewModel.uiState.value.setupSession.chosenLocation!!.displayName
        )
    }

    @Test
    fun beginAutoLocate_withoutPermission_waitsThenCentersAfterGrant() {
        val denied = FakeDeviceLocationController(initialPermissionGranted = false)
        val vm = PlanningViewModel(repository = InMemoryLayoutRepository(), locationController = denied)
        vm.createNewLayout()
        vm.beginAutoLocate()
        assertNull(vm.uiState.value.setupSession.chosenLocation)

        vm.onLocationPermissionResult(true)
        assertTrue(denied.state.value.isUpdating)
        denied.emitFix(DeviceLocationFix(latitude = 19.076, longitude = 72.8777))
        vm.refreshLocation()
        assertEquals(19.076, vm.uiState.value.setupSession.chosenLocation!!.latitude, 1e-9)
    }

    @Test
    fun autoLocate_ignoredOutsideFindLocationStep() {
        viewModel.createNewLayout()
        viewModel.advanceSetupStep()
        viewModel.beginAutoLocate()
        fakeLocation.emitFix(DeviceLocationFix(latitude = 12.9716, longitude = 77.5946))
        viewModel.refreshLocation()
        assertNull(viewModel.uiState.value.setupSession.chosenLocation)
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
        viewModel.loadLayout(Layout(name = "Region", regionBounds = region))
        assertEquals(PlanningSetupStep.SET_ORIGIN, viewModel.uiState.value.setupStep)
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
    fun confirmRegionAndAdvance_validBounds_setsOriginAtCenterAndOpensWorkspace() {
        enterSetRegion()
        assertTrue(viewModel.confirmRegionAndAdvance(region))
        val state = viewModel.uiState.value
        val center = RegionWorkspaceGeometry.centroid(region)
        assertEquals(region, state.currentLayout.regionBounds)
        assertEquals(PlanningSetupStep.PLACE_ASSETS, state.setupStep)
        assertEquals(MapInteractionMode.NAVIGATE, state.mapInteractionMode)
        assertEquals(center.latitude, state.currentLayout.anchor.latitude!!, 1e-9)
        assertEquals(center.longitude, state.currentLayout.anchor.longitude!!, 1e-9)
        assertEquals(0f, state.currentLayout.anchor.headingDegrees!!, 0.001f)
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
        assertEquals(PlanningSetupStep.PLACE_ASSETS, viewModel.uiState.value.setupStep)
        assertEquals(region, viewModel.uiState.value.currentLayout.regionBounds)
        val center = RegionWorkspaceGeometry.centroid(region)
        assertEquals(center.latitude, viewModel.uiState.value.currentLayout.anchor.latitude!!, 1e-9)
        assertEquals(center.longitude, viewModel.uiState.value.currentLayout.anchor.longitude!!, 1e-9)
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
    fun enteringSetRegion_doesNotMoveTheCamera() {
        // The map already flew to the chosen place when it was picked. Moving it again on
        // Continue threw away wherever the planner had dragged the map (Connaught Place bug).
        MapCameraBridge.target.value?.let { MapCameraBridge.consumed(it) }
        viewModel.createNewLayout()
        viewModel.setChosenLocation(chosen)
        viewModel.advanceSetupStep()
        assertEquals(PlanningSetupStep.SET_REGION, viewModel.uiState.value.setupStep)
        assertEquals(null, MapCameraBridge.target.value)
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
    fun confirmingARegionThatStillContainsOrigin_returnsToPlaceAssets() {
        viewModel.loadLayout(Layout(name = "Legacy", anchor = geoAnchor))
        assertEquals(PlanningSetupStep.SET_REGION, viewModel.uiState.value.setupStep)
        assertTrue(viewModel.confirmRegionAndAdvance(region))
        val state = viewModel.uiState.value
        assertEquals(PlanningSetupStep.PLACE_ASSETS, state.setupStep)
        assertEquals(28.6139, state.currentLayout.anchor.latitude!!, 1e-9)
        assertEquals(77.2090, state.currentLayout.anchor.longitude!!, 1e-9)
        assertEquals(0f, state.currentLayout.anchor.headingDegrees!!, 0.001f)
        assertEquals(region, state.currentLayout.regionBounds)
    }

    @Test
    fun confirmingARegionMovesOriginToTheNewCenter() {
        viewModel.loadLayout(Layout(name = "Legacy", anchor = geoAnchor))
        val elsewhere = GeoBounds(19.10, 19.05, 72.90, 72.85)
        assertTrue(viewModel.confirmRegionAndAdvance(elsewhere))
        val state = viewModel.uiState.value
        val center = RegionWorkspaceGeometry.centroid(elsewhere)
        assertEquals(PlanningSetupStep.PLACE_ASSETS, state.setupStep)
        assertEquals(center.latitude, state.currentLayout.anchor.latitude!!, 1e-9)
        assertEquals(center.longitude, state.currentLayout.anchor.longitude!!, 1e-9)
        assertEquals(0f, state.currentLayout.anchor.headingDegrees!!, 0.001f)
        assertEquals(elsewhere, state.currentLayout.regionBounds)
    }

    @Test
    fun requestChangeRegion_keepsObjectsAndReturnsToSetRegion() {
        val tent = LayoutObject(assetType = AssetType.MILITARY_VEHICLE, x = 4f, z = 6f)
        viewModel.loadLayout(
            Layout(name = "Done", anchor = geoAnchor, regionBounds = region, objects = listOf(tent))
        )
        viewModel.requestChangeRegion()
        val state = viewModel.uiState.value
        assertEquals(PlanningSetupStep.SET_REGION, state.setupStep)
        assertEquals(1, state.currentLayout.objects.size)
        assertEquals(4f, state.currentLayout.objects.first().x, 0.001f)
        assertTrue(state.currentLayout.anchor.hasGeographicFix)
        assertEquals(MapInteractionMode.SELECT_REGION, state.mapInteractionMode)

        assertTrue(viewModel.confirmRegionAndAdvance(region))
        val done = viewModel.uiState.value
        assertEquals(PlanningSetupStep.PLACE_ASSETS, done.setupStep)
        assertEquals(28.6139, done.currentLayout.anchor.latitude!!, 1e-9)
        assertEquals(77.2090, done.currentLayout.anchor.longitude!!, 1e-9)
        assertEquals(1, done.currentLayout.objects.size)
        assertEquals(4f, done.currentLayout.objects.first().x, 0.001f)
        assertEquals(6f, done.currentLayout.objects.first().z, 0.001f)
    }

    @Test
    fun useRegionCenter_overridesAChosenOrigin() {
        viewModel.loadLayout(Layout(name = "Done", anchor = geoAnchor, regionBounds = region))
        assertTrue(viewModel.setOriginToRegionCenter())
        val center = RegionWorkspaceGeometry.centroid(region)
        val anchor = viewModel.uiState.value.currentLayout.anchor
        assertEquals(center.latitude, anchor.latitude!!, 1e-9)
        assertEquals(center.longitude, anchor.longitude!!, 1e-9)
        assertEquals(PlanningSetupStep.PLACE_ASSETS, viewModel.uiState.value.setupStep)
    }

    @Test
    fun requestChangeOrigin_keepsMeterOffsetsAndReturnsToSetOrigin() {
        val tent = LayoutObject(assetType = AssetType.MILITARY_VEHICLE, x = 4f, z = 6f)
        viewModel.loadLayout(
            Layout(name = "Done", anchor = geoAnchor, regionBounds = region, objects = listOf(tent))
        )
        viewModel.requestChangeOrigin()
        val during = viewModel.uiState.value
        assertEquals(PlanningSetupStep.SET_ORIGIN, during.setupStep)
        assertEquals(4f, during.currentLayout.objects.first().x, 0.001f)
        assertEquals(6f, during.currentLayout.objects.first().z, 0.001f)
        assertTrue(during.currentLayout.anchor.hasGeographicFix)

        assertTrue(viewModel.setGeographicAnchorFromMap(28.615, 77.20, headingDegrees = 0f))
        val after = viewModel.uiState.value
        assertEquals(PlanningSetupStep.PLACE_ASSETS, after.setupStep)
        assertEquals(4f, after.currentLayout.objects.first().x, 0.001f)
        assertEquals(6f, after.currentLayout.objects.first().z, 0.001f)
    }

    @Test
    fun clearLayout_keepsRegionOriginAndWorkspaceStep() {
        val tent = LayoutObject(assetType = AssetType.MILITARY_VEHICLE, x = 4f, z = 6f)
        viewModel.loadLayout(
            Layout(name = "Done", anchor = geoAnchor, regionBounds = region, objects = listOf(tent))
        )
        viewModel.clearLayout()
        val state = viewModel.uiState.value
        assertEquals(PlanningSetupStep.PLACE_ASSETS, state.setupStep)
        assertTrue(state.currentLayout.objects.isEmpty())
        assertEquals(region, state.currentLayout.regionBounds)
        assertTrue(state.currentLayout.anchor.hasGeographicFix)
    }

    @Test
    fun deletingTheLastAsset_staysOnPlaceAssets() {
        val tent = LayoutObject(assetType = AssetType.MILITARY_VEHICLE)
        viewModel.loadLayout(
            Layout(name = "Done", anchor = geoAnchor, regionBounds = region, objects = listOf(tent))
        )
        viewModel.deleteObject(tent.id)
        val state = viewModel.uiState.value
        assertTrue(state.currentLayout.objects.isEmpty())
        assertEquals(PlanningSetupStep.PLACE_ASSETS, state.setupStep)
    }

    @Test
    fun advanceSetupStep_isIdempotentOnceThePrerequisiteIsMissing() {
        viewModel.createNewLayout()
        viewModel.setChosenLocation(chosen)
        viewModel.advanceSetupStep()
        viewModel.advanceSetupStep()
        assertEquals(PlanningSetupStep.SET_REGION, viewModel.uiState.value.setupStep)

        viewModel.loadLayout(Layout(name = "Done", anchor = geoAnchor, regionBounds = region))
        viewModel.advanceSetupStep()
        assertEquals(PlanningSetupStep.PLACE_ASSETS, viewModel.uiState.value.setupStep)
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
        viewModel.loadLayout(Layout(name = "Region", regionBounds = region))
        assertEquals(PlanningSetupStep.SET_ORIGIN, viewModel.uiState.value.setupStep)
    }
}
