package com.example.dhruvar

import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.ChosenMapLocation
import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.MapInteractionMode
import com.example.dhruvar.domain.model.PlanningSetupSession
import com.example.dhruvar.domain.model.PlanningSetupStep
import com.example.dhruvar.domain.model.PlanningSurface
import com.example.dhruvar.domain.model.PlaceSearchHit
import com.example.dhruvar.domain.model.allowedModes
import com.example.dhruvar.domain.model.canConfirmSetupRegion
import com.example.dhruvar.domain.model.canContinueSetOrigin
import com.example.dhruvar.domain.model.canEnterAr
import com.example.dhruvar.domain.model.progressSubtitle
import com.example.dhruvar.domain.model.coerceMapInteractionMode
import com.example.dhruvar.domain.model.resolvePlanningSetupStep
import com.example.dhruvar.domain.model.showsRegionDraftOverlay
import com.example.dhruvar.domain.model.toChosenMapLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningSetupResolverTest {

    private val region = GeoBounds(
        northLatitude = 28.62,
        southLatitude = 28.60,
        eastLongitude = 77.22,
        westLongitude = 77.19
    )
    private val geoAnchor = Anchor(latitude = 28.6139, longitude = 77.2090, headingDegrees = 0f)
    private val chosen = ChosenMapLocation(28.61, 77.21, "Delhi", zoom = 14.0)

    @Test
    fun brandNewLayout_isAlwaysFindLocation() {
        val layout = Layout(regionBounds = region, anchor = geoAnchor)
        val session = PlanningSetupSession(chosenLocation = chosen)
        assertEquals(
            PlanningSetupStep.FIND_LOCATION,
            resolvePlanningSetupStep(layout, session, isBrandNewLayout = true)
        )
    }

    @Test
    fun emptyLayout_noSession_isFindLocation() {
        assertEquals(
            PlanningSetupStep.FIND_LOCATION,
            resolvePlanningSetupStep(Layout(), PlanningSetupSession(), isBrandNewLayout = false)
        )
    }

    @Test
    fun emptyLayout_withChosenLocation_isSetRegion() {
        val session = PlanningSetupSession(chosenLocation = chosen)
        assertEquals(
            PlanningSetupStep.SET_REGION,
            resolvePlanningSetupStep(Layout(), session, isBrandNewLayout = false)
        )
    }

    @Test
    fun regionOnly_isSetOrigin() {
        val layout = Layout(regionBounds = region)
        assertEquals(
            PlanningSetupStep.SET_ORIGIN,
            resolvePlanningSetupStep(layout, PlanningSetupSession(), isBrandNewLayout = false)
        )
    }

    @Test
    fun regionAndGeoFix_isPlaceAssets() {
        val layout = Layout(regionBounds = region, anchor = geoAnchor)
        assertEquals(
            PlanningSetupStep.PLACE_ASSETS,
            resolvePlanningSetupStep(layout, PlanningSetupSession(), isBrandNewLayout = false)
        )
    }

    @Test
    fun geoFixWithoutRegion_legacy_isSetRegion() {
        val layout = Layout(anchor = geoAnchor)
        assertEquals(
            PlanningSetupStep.SET_REGION,
            resolvePlanningSetupStep(layout, PlanningSetupSession(), isBrandNewLayout = false)
        )
    }

    @Test
    fun allowedModes_findLocationIsNavigateOnly() {
        assertEquals(
            setOf(MapInteractionMode.NAVIGATE),
            allowedModes(PlanningSetupStep.FIND_LOCATION)
        )
    }

    @Test
    fun allowedModes_setRegionIsSelectRegionOnly() {
        assertEquals(
            setOf(MapInteractionMode.SELECT_REGION),
            allowedModes(PlanningSetupStep.SET_REGION)
        )
    }

    @Test
    fun allowedModes_setOriginAllowsNavigateAndAnchor() {
        val allowed = allowedModes(PlanningSetupStep.SET_ORIGIN)
        assertTrue(MapInteractionMode.NAVIGATE in allowed)
        assertTrue(MapInteractionMode.SET_ANCHOR in allowed)
        assertFalse(MapInteractionMode.PLACE_ASSET in allowed)
    }

    @Test
    fun allowedModes_placeAssetsIsNavigatePlaceAndEdit() {
        val allowed = allowedModes(PlanningSetupStep.PLACE_ASSETS)
        assertEquals(
            setOf(
                MapInteractionMode.NAVIGATE,
                MapInteractionMode.PLACE_ASSET,
                MapInteractionMode.EDIT
            ),
            allowed
        )
        assertFalse(MapInteractionMode.SELECT_REGION in allowed)
        assertFalse(MapInteractionMode.SET_ANCHOR in allowed)
    }

    @Test
    fun coerce_illegalModeFallsBackToDefault() {
        assertEquals(
            MapInteractionMode.NAVIGATE,
            coerceMapInteractionMode(PlanningSetupStep.FIND_LOCATION, MapInteractionMode.PLACE_ASSET)
        )
        assertEquals(
            MapInteractionMode.SELECT_REGION,
            coerceMapInteractionMode(PlanningSetupStep.SET_REGION, MapInteractionMode.NAVIGATE)
        )
        assertEquals(
            MapInteractionMode.SET_ANCHOR,
            coerceMapInteractionMode(PlanningSetupStep.SET_ORIGIN, MapInteractionMode.SET_ANCHOR)
        )
        assertEquals(
            MapInteractionMode.PLACE_ASSET,
            coerceMapInteractionMode(PlanningSetupStep.PLACE_ASSETS, MapInteractionMode.PLACE_ASSET)
        )
        assertEquals(
            MapInteractionMode.NAVIGATE,
            coerceMapInteractionMode(PlanningSetupStep.PLACE_ASSETS, MapInteractionMode.SELECT_REGION)
        )
        assertEquals(
            MapInteractionMode.NAVIGATE,
            coerceMapInteractionMode(PlanningSetupStep.PLACE_ASSETS, MapInteractionMode.SET_ANCHOR)
        )
    }

    @Test
    fun confirmSetupRegion_requiresDraftOrCommittedBox() {
        assertFalse(canConfirmSetupRegion(draftBounds = null, committedBounds = null))
        assertTrue(canConfirmSetupRegion(draftBounds = null, committedBounds = region))
        assertTrue(canConfirmSetupRegion(draftBounds = region, committedBounds = null))
    }

    @Test
    fun canEnterAr_requiresRegionAndOrigin() {
        assertFalse(canEnterAr(Layout()))
        assertFalse(canEnterAr(Layout(regionBounds = region)))
        assertFalse(canEnterAr(Layout(anchor = geoAnchor)))
        assertTrue(canEnterAr(Layout(regionBounds = region, anchor = geoAnchor, objects = emptyList())))
    }

    @Test
    fun continueFromSetOrigin_requiresGeographicFix() {
        assertFalse(canContinueSetOrigin(hasGeographicFix = false))
        assertTrue(canContinueSetOrigin(hasGeographicFix = true))
    }

    @Test
    fun setOriginProgressNamesTheStep() {
        assertEquals("Step 3 of 3 · Set origin", PlanningSetupStep.SET_ORIGIN.progressSubtitle())
    }

    @Test
    fun searchHit_mapsToChosenLocationWithoutChangingIdentityFields() {
        val hit = PlaceSearchHit(
            id = "n1",
            name = "दिल्ली",
            englishName = "Delhi",
            kind = "place",
            subtype = "city",
            rank = 1,
            latitude = 28.61,
            longitude = 77.21
        )
        val mapped = hit.toChosenMapLocation(zoom = 15.0)
        assertEquals("Delhi", mapped.displayName)
        assertEquals(28.61, mapped.latitude, 0.0)
        assertEquals(77.21, mapped.longitude, 0.0)
        assertEquals(15.0, mapped.zoom)
    }

    @Test
    fun regionOverlay_onlyOnSetRegion() {
        assertTrue(
            showsRegionDraftOverlay(
                PlanningSetupStep.SET_REGION,
                MapInteractionMode.SELECT_REGION,
                PlanningSurface.MAP
            )
        )
        assertFalse(
            showsRegionDraftOverlay(
                PlanningSetupStep.FIND_LOCATION,
                MapInteractionMode.SELECT_REGION,
                PlanningSurface.MAP
            )
        )
        assertFalse(
            showsRegionDraftOverlay(
                PlanningSetupStep.SET_ORIGIN,
                MapInteractionMode.SELECT_REGION,
                PlanningSurface.MAP
            )
        )
        assertFalse(
            showsRegionDraftOverlay(
                PlanningSetupStep.PLACE_ASSETS,
                MapInteractionMode.SELECT_REGION,
                PlanningSurface.MAP
            )
        )
        assertFalse(
            showsRegionDraftOverlay(
                PlanningSetupStep.PLACE_ASSETS,
                MapInteractionMode.NAVIGATE,
                PlanningSurface.MAP
            )
        )
    }
}
