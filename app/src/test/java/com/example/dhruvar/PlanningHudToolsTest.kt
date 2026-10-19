package com.example.dhruvar

import com.example.dhruvar.domain.model.MapInteractionMode
import com.example.dhruvar.domain.model.PlanningSetupStep
import com.example.dhruvar.domain.model.PlanningSurface
import com.example.dhruvar.ui.components.planningHudToolsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningHudToolsTest {

    @Test
    fun `navigate mode shows only the rail so the map stays clear`() {
        val tools = planningHudToolsFor(PlanningSurface.MAP, MapInteractionMode.NAVIGATE)

        assertTrue(tools.showModeRail)
        assertFalse(tools.hasModeContext)
        assertFalse(tools.showSearch)
        assertTrue(tools.showAssetPicker)
        assertTrue(tools.showArAction)
        assertTrue(tools.showWorkspaceStatus)
    }

    @Test
    fun `region and anchor are not workspace rail modes`() {
        val region = planningHudToolsFor(PlanningSurface.MAP, MapInteractionMode.SELECT_REGION)
        val anchor = planningHudToolsFor(PlanningSurface.MAP, MapInteractionMode.SET_ANCHOR)

        assertFalse(region.showRegionTools)
        assertFalse(anchor.showAnchorHint)
        assertTrue(region.showAssetPicker)
        assertTrue(anchor.showModeRail)
    }

    @Test
    fun `place mode shows the place hint and nothing else`() {
        val tools = planningHudToolsFor(PlanningSurface.MAP, MapInteractionMode.PLACE_ASSET)

        assertTrue(tools.showPlaceHint)
        assertFalse(tools.showRegionTools)
        assertFalse(tools.showAnchorHint)
        assertFalse(tools.showEditHint)
    }

    @Test
    fun `edit mode hints while nothing is selected`() {
        val tools = planningHudToolsFor(
            surface = PlanningSurface.MAP,
            mode = MapInteractionMode.EDIT,
            hasSelection = false
        )

        assertTrue(tools.showEditHint)
    }

    @Test
    fun `edit mode yields to the selection editor once an asset is selected`() {
        val tools = planningHudToolsFor(
            surface = PlanningSurface.MAP,
            mode = MapInteractionMode.EDIT,
            hasSelection = true
        )

        assertFalse(tools.showEditHint)
        assertFalse(tools.hasModeContext)
        assertTrue(tools.showModeRail)
    }

    @Test
    fun `exactly one contextual strip is ever visible`() {
        for (mode in MapInteractionMode.entries) {
            val tools = planningHudToolsFor(PlanningSurface.MAP, mode)
            val visible = listOf(
                tools.showRegionTools,
                tools.showAnchorHint,
                tools.showPlaceHint,
                tools.showEditHint
            ).count { it }

            assertTrue("$mode shows $visible strips", visible <= 1)
        }
    }

    @Test
    fun `an open origin sheet suppresses mode context but keeps the rail`() {
        for (mode in MapInteractionMode.entries) {
            val tools = planningHudToolsFor(
                surface = PlanningSurface.MAP,
                mode = mode,
                isOriginExpanded = true
            )

            assertTrue("$mode lost its rail", tools.showModeRail)
            assertFalse("$mode kept context under the origin sheet", tools.hasModeContext)
        }
    }

    @Test
    fun `the metric grid hides the map mode rail and search entirely`() {
        for (mode in MapInteractionMode.entries) {
            val tools = planningHudToolsFor(PlanningSurface.METRIC_GRID, mode)

            assertFalse("$mode drew a rail on the grid", tools.showModeRail)
            assertFalse("$mode drew context on the grid", tools.hasModeContext)
            assertFalse("$mode drew search on the grid", tools.showSearch)
        }
    }

    @Test
    fun `the grid keeps the switcher, which is the only way back to the map`() {
        val tools = planningHudToolsFor(PlanningSurface.METRIC_GRID, MapInteractionMode.NAVIGATE)

        assertTrue(tools.showSurfaceSwitcher)
        assertTrue(tools.showOriginChip)
    }

    @Test
    fun `expanded search is ignored on the asset workspace`() {
        val tools = planningHudToolsFor(
            surface = PlanningSurface.MAP,
            mode = MapInteractionMode.NAVIGATE,
            isSearchExpanded = true
        )

        assertFalse(tools.showSearch)
        assertTrue(tools.showOriginChip)
        assertTrue(tools.showSurfaceSwitcher)
        assertTrue(tools.showModeRail)
        assertFalse(tools.hasModeContext)
    }

    @Test
    fun `expanded search on the grid is ignored since search is unavailable there`() {
        val tools = planningHudToolsFor(
            surface = PlanningSurface.METRIC_GRID,
            mode = MapInteractionMode.NAVIGATE,
            isSearchExpanded = true
        )

        assertTrue(tools.showSurfaceSwitcher)
        assertFalse(tools.showSearch)
    }

    @Test
    fun `an open editor leaves the top row alone`() {
        val tools = planningHudToolsFor(
            surface = PlanningSurface.MAP,
            mode = MapInteractionMode.EDIT,
            hasSelection = true,
            isEditorOpen = true
        )

        assertTrue(tools.showOriginChip)
        assertTrue(tools.showSurfaceSwitcher)
    }

    @Test
    fun `an open selection editor clears the bottom edge but keeps search`() {
        for (mode in MapInteractionMode.entries) {
            val tools = planningHudToolsFor(
                surface = PlanningSurface.MAP,
                mode = mode,
                hasSelection = true,
                isEditorOpen = true
            )

            assertFalse("$mode kept a rail under the editor", tools.showModeRail)
            assertFalse("$mode kept context under the editor", tools.hasModeContext)
            assertFalse("$mode showed search on the workspace", tools.showSearch)
        }
    }

    @Test
    fun `minimising the editor restores the mode rail`() {
        val tools = planningHudToolsFor(
            surface = PlanningSurface.MAP,
            mode = MapInteractionMode.EDIT,
            hasSelection = true,
            isEditorOpen = false
        )

        assertTrue(tools.showModeRail)
    }

    @Test
    fun `search stays hidden on the asset workspace`() {
        for (mode in MapInteractionMode.entries) {
            assertFalse(
                "$mode showed search",
                planningHudToolsFor(PlanningSurface.MAP, mode).showSearch
            )
        }
    }

    @Test
    fun `grid hides the rail regardless of selection or origin state`() {
        val tools = planningHudToolsFor(
            surface = PlanningSurface.METRIC_GRID,
            mode = MapInteractionMode.EDIT,
            hasSelection = true,
            isOriginExpanded = true
        )

        assertEquals(false, tools.showModeRail)
        assertEquals(false, tools.hasModeContext)
    }

    @Test
    fun `find location hides origin assets rail and AR`() {
        val tools = planningHudToolsFor(
            surface = PlanningSurface.MAP,
            mode = MapInteractionMode.NAVIGATE,
            setupStep = PlanningSetupStep.FIND_LOCATION
        )

        assertTrue(tools.showSearch)
        assertTrue(tools.showSetupContinue)
        assertFalse(tools.showOriginChip)
        assertFalse(tools.showSurfaceSwitcher)
        assertFalse(tools.showModeRail)
        assertFalse(tools.showRegionTools)
        assertFalse(tools.showAssetPicker)
        assertFalse(tools.showArAction)
    }

    @Test
    fun `set region shows only region tools`() {
        val tools = planningHudToolsFor(
            surface = PlanningSurface.MAP,
            mode = MapInteractionMode.SELECT_REGION,
            setupStep = PlanningSetupStep.SET_REGION
        )

        assertTrue(tools.showRegionTools)
        assertFalse(tools.showSearch)
        assertFalse(tools.showOriginChip)
        assertFalse(tools.showModeRail)
        assertFalse(tools.showAssetPicker)
        assertFalse(tools.showArAction)
    }

    @Test
    fun `set region hides search even when the field is still expanded`() {
        val tools = planningHudToolsFor(
            surface = PlanningSurface.MAP,
            mode = MapInteractionMode.SELECT_REGION,
            isSearchExpanded = true,
            setupStep = PlanningSetupStep.SET_REGION
        )

        assertFalse(tools.showSearch)
        assertTrue(tools.showRegionTools)
        assertFalse(tools.showModeRail)
    }

    @Test
    fun `set origin shows only the origin chip`() {
        val tools = planningHudToolsFor(
            surface = PlanningSurface.MAP,
            mode = MapInteractionMode.NAVIGATE,
            setupStep = PlanningSetupStep.SET_ORIGIN
        )

        assertTrue(tools.showOriginChip)
        assertFalse(tools.showSearch)
        assertFalse(tools.showRegionTools)
        assertFalse(tools.showModeRail)
        assertFalse(tools.showAssetPicker)
        assertFalse(tools.showArAction)
        assertFalse(tools.showSetupContinue)
        assertFalse(tools.showAnchorHint)
    }

    @Test
    fun `set origin continue appears when origin already exists`() {
        val tools = planningHudToolsFor(
            surface = PlanningSurface.MAP,
            mode = MapInteractionMode.NAVIGATE,
            setupStep = PlanningSetupStep.SET_ORIGIN,
            hasGeographicFix = true
        )

        assertTrue(tools.showOriginChip)
        assertTrue(tools.showSetupContinue)
        assertFalse(tools.showAssetPicker)
        assertFalse(tools.showArAction)
        assertFalse(tools.showModeRail)
    }

    @Test
    fun `set origin tap mode shows the inside-region hint and hides continue`() {
        val tools = planningHudToolsFor(
            surface = PlanningSurface.MAP,
            mode = MapInteractionMode.SET_ANCHOR,
            setupStep = PlanningSetupStep.SET_ORIGIN,
            hasGeographicFix = true
        )

        assertTrue(tools.showOriginChip)
        assertTrue(tools.showAnchorHint)
        assertFalse(tools.showSetupContinue)
        assertFalse(tools.showModeRail)
    }

    @Test
    fun `place assets restores workspace chrome including AR and assets`() {
        val tools = planningHudToolsFor(
            surface = PlanningSurface.MAP,
            mode = MapInteractionMode.NAVIGATE,
            setupStep = PlanningSetupStep.PLACE_ASSETS
        )

        assertTrue(tools.showModeRail)
        assertTrue(tools.showOriginChip)
        assertTrue(tools.showWorkspaceStatus)
        assertTrue(tools.showAssetPicker)
        assertTrue(tools.showArAction)
        assertFalse(tools.showSearch)
        assertFalse(tools.showSetupContinue)
        assertFalse(tools.showRegionTools)
    }
}
