package com.example.dhruvar

import com.example.dhruvar.domain.model.MapInteractionMode
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
    }

    @Test
    fun `region mode shows the region tools and nothing else`() {
        val tools = planningHudToolsFor(PlanningSurface.MAP, MapInteractionMode.SELECT_REGION)

        assertTrue(tools.showRegionTools)
        assertFalse(tools.showAnchorHint)
        assertFalse(tools.showPlaceHint)
        assertFalse(tools.showEditHint)
    }

    @Test
    fun `anchor mode shows the anchor hint and nothing else`() {
        val tools = planningHudToolsFor(PlanningSurface.MAP, MapInteractionMode.SET_ANCHOR)

        assertTrue(tools.showAnchorHint)
        assertFalse(tools.showRegionTools)
        assertFalse(tools.showPlaceHint)
        assertFalse(tools.showEditHint)
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
    fun `expanded search takes the whole top row`() {
        val tools = planningHudToolsFor(
            surface = PlanningSurface.MAP,
            mode = MapInteractionMode.SELECT_REGION,
            isSearchExpanded = true
        )

        assertFalse(tools.showSearch)
        assertFalse(tools.showOriginChip)
        assertFalse(tools.showSurfaceSwitcher)
        assertFalse("region tools competed with the results list", tools.hasModeContext)
        assertTrue("the rail is at the bottom and does not conflict", tools.showModeRail)
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
            assertTrue("$mode hid search needlessly", tools.showSearch)
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
    fun `search stays reachable in every map mode`() {
        for (mode in MapInteractionMode.entries) {
            assertTrue(
                "$mode hid search",
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
}
