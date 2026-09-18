package com.example.dhruvar

import androidx.compose.ui.geometry.Offset
import com.example.dhruvar.domain.spatial.CoordinateTransformer
import com.example.dhruvar.viewmodel.PlanningViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying spatial coordinate transformations, directional conversions,
 * zoom scaling, pan translation, and viewport reset operations for Part 2.
 */
class CoordinateTransformerTest {

    private val defaultWidth = 1000f
    private val defaultHeight = 800f
    private val basePpm = 25.0f // 25 pixels per meter

    private fun createTransformer(
        panOffset: Offset = Offset.Zero,
        zoom: Float = 1.0f
    ): CoordinateTransformer {
        return CoordinateTransformer(
            viewportWidth = defaultWidth,
            viewportHeight = defaultHeight,
            panOffset = panOffset,
            zoom = zoom,
            basePixelsPerMeter = basePpm
        )
    }

    // 1. Origin Conversion
    @Test
    fun originConversion_worldZeroZeroMapsToCenterOfCanvas() {
        val transformer = createTransformer()

        val expectedScreenX = defaultWidth / 2f  // 500.0f
        val expectedScreenY = defaultHeight / 2f // 400.0f

        val canvasPoint = transformer.worldToCanvas(0.0f, 0.0f)
        assertEquals(expectedScreenX, canvasPoint.x, 0.001f)
        assertEquals(expectedScreenY, canvasPoint.y, 0.001f)

        // Inverse check
        val (worldX, worldZ) = transformer.canvasToWorld(Offset(expectedScreenX, expectedScreenY))
        assertEquals(0.0f, worldX, 0.001f)
        assertEquals(0.0f, worldZ, 0.001f)
    }

    // 2. Positive X (+X East / Right)
    @Test
    fun positiveX_mapsRightwardAlongHorizontalAxis() {
        val transformer = createTransformer()
        val metersX = 10.0f

        val canvasPoint = transformer.worldToCanvas(worldX = metersX, worldZ = 0.0f)

        // Screen X should be center + (10 * 25) = 500 + 250 = 750
        assertEquals(750.0f, canvasPoint.x, 0.001f)
        assertEquals(400.0f, canvasPoint.y, 0.001f)

        // Round-trip verification
        val (worldX, worldZ) = transformer.canvasToWorld(canvasPoint)
        assertEquals(10.0f, worldX, 0.001f)
        assertEquals(0.0f, worldZ, 0.001f)
    }

    // 3. Positive Z (+Z North / Upwards on Screen)
    @Test
    fun positiveZ_mapsUpwardAlongVerticalAxis() {
        val transformer = createTransformer()
        val metersZ = 10.0f

        val canvasPoint = transformer.worldToCanvas(worldX = 0.0f, worldZ = metersZ)

        // Screen Y decreases upwards: center - (10 * 25) = 400 - 250 = 150
        assertEquals(500.0f, canvasPoint.x, 0.001f)
        assertEquals(150.0f, canvasPoint.y, 0.001f)

        // Round-trip verification
        val (worldX, worldZ) = transformer.canvasToWorld(canvasPoint)
        assertEquals(0.0f, worldX, 0.001f)
        assertEquals(10.0f, worldZ, 0.001f)
    }

    // 4. Negative X (-X West / Left)
    @Test
    fun negativeX_mapsLeftwardAlongHorizontalAxis() {
        val transformer = createTransformer()
        val metersX = -8.0f

        val canvasPoint = transformer.worldToCanvas(worldX = metersX, worldZ = 0.0f)

        // Screen X should be center - (8 * 25) = 500 - 200 = 300
        assertEquals(300.0f, canvasPoint.x, 0.001f)
        assertEquals(400.0f, canvasPoint.y, 0.001f)

        val (worldX, worldZ) = transformer.canvasToWorld(canvasPoint)
        assertEquals(-8.0f, worldX, 0.001f)
        assertEquals(0.0f, worldZ, 0.001f)
    }

    // 5. Negative Z (-Z South / Downwards on Screen)
    @Test
    fun negativeZ_mapsDownwardAlongVerticalAxis() {
        val transformer = createTransformer()
        val metersZ = -6.0f

        val canvasPoint = transformer.worldToCanvas(worldX = 0.0f, worldZ = metersZ)

        // Screen Y increases downwards: center + (6 * 25) = 400 + 150 = 550
        assertEquals(500.0f, canvasPoint.x, 0.001f)
        assertEquals(550.0f, canvasPoint.y, 0.001f)

        val (worldX, worldZ) = transformer.canvasToWorld(canvasPoint)
        assertEquals(0.0f, worldX, 0.001f)
        assertEquals(-6.0f, worldZ, 0.001f)
    }

    // 6. Zoom Transformation
    @Test
    fun zoomTransformation_scalesPhysicalPixelDistancesProportionally() {
        val zoom2x = createTransformer(zoom = 2.0f)
        val zoomHalf = createTransformer(zoom = 0.5f)

        assertEquals(50.0f, zoom2x.pixelsPerMeter, 0.001f)
        assertEquals(12.5f, zoomHalf.pixelsPerMeter, 0.001f)

        val point2x = zoom2x.worldToCanvas(10.0f, 0.0f)
        val pointHalf = zoomHalf.worldToCanvas(10.0f, 0.0f)

        // At 2x: 500 + (10 * 50) = 1000
        assertEquals(1000.0f, point2x.x, 0.001f)
        // At 0.5x: 500 + (10 * 12.5) = 625
        assertEquals(625.0f, pointHalf.x, 0.001f)
    }

    // 7. Pan Transformation
    @Test
    fun panTransformation_translatesAnchorAndEntitiesOnScreen() {
        val pan = Offset(120.0f, -80.0f)
        val transformer = createTransformer(panOffset = pan)

        // Origin at (0, 0) should shift by the pan offset
        val originOnScreen = transformer.worldToCanvas(0.0f, 0.0f)
        assertEquals(500.0f + 120.0f, originOnScreen.x, 0.001f)
        assertEquals(400.0f - 80.0f, originOnScreen.y, 0.001f)

        // Inverse test
        val (worldX, worldZ) = transformer.canvasToWorld(originOnScreen)
        assertEquals(0.0f, worldX, 0.001f)
        assertEquals(0.0f, worldZ, 0.001f)
    }

    // 8. Dynamic Scale Bar
    @Test
    fun dynamicScaleBar_computesProperPhysicalUnit() {
        val transformer1x = createTransformer(zoom = 1.0f)
        val scaleInfo1x = transformer1x.calculateDynamicScaleBar(targetMaxBarWidthPx = 140f)

        assertTrue(scaleInfo1x.distanceMeters > 0f)
        assertTrue(scaleInfo1x.widthPx <= 140f)
        assertEquals(scaleInfo1x.distanceMeters * transformer1x.pixelsPerMeter, scaleInfo1x.widthPx, 0.001f)
    }

    // 9. PlanningViewModel Viewport State & Reset View
    @Test
    fun planningViewModel_panZoomAndResetView() {
        val viewModel = PlanningViewModel()

        // 1. Initial viewport state
        assertEquals(Offset.Zero, viewModel.uiState.value.panOffset)
        assertEquals(1.0f, viewModel.uiState.value.zoom, 0.001f)
        assertNull(viewModel.uiState.value.cursorCoordinatesMeters)

        // 2. Pan
        viewModel.onPan(Offset(50f, 75f))
        assertEquals(Offset(50f, 75f), viewModel.uiState.value.panOffset)

        // 3. Zoom
        viewModel.zoomIn()
        assertTrue(viewModel.uiState.value.zoom > 1.0f)

        // 4. Cursor position
        viewModel.updateCursorCoordinates(12.4f, -5.2f)
        assertEquals(12.4f, viewModel.uiState.value.cursorCoordinatesMeters?.first ?: 0f, 0.001f)
        assertEquals(-5.2f, viewModel.uiState.value.cursorCoordinatesMeters?.second ?: 0f, 0.001f)

        // 5. Reset View
        viewModel.resetView()
        assertEquals(Offset.Zero, viewModel.uiState.value.panOffset)
        assertEquals(1.0f, viewModel.uiState.value.zoom, 0.001f)
        assertNull(viewModel.uiState.value.cursorCoordinatesMeters)
    }
}
