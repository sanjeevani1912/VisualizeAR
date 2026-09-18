package com.example.dhruvar

import androidx.compose.ui.geometry.Offset
import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.spatial.CoordinateTransformer
import com.example.dhruvar.domain.spatial.SpatialDistanceCalculator
import com.example.dhruvar.viewmodel.PlanningViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying Part 6: Spatial Distance Measurement.
 *
 * Validates:
 * - Mathematical distance calculation across all quadrants
 * - Decimal coordinate precision and distance formatting
 * - Invariance under object rotation
 * - Invariance under viewport zoom and pan
 * - Anchor and object-to-object calculation APIs
 * - Measurement visibility toggle in ViewModel
 */
class SpatialDistanceTest {

    // 1. (0,0) -> (0,0) = 0.0m
    @Test
    fun distance_originToOrigin_isZero() {
        val dist = SpatialDistanceCalculator.calculateDistance(0.0f, 0.0f, 0.0f, 0.0f)
        assertEquals(0.0f, dist, 0.0001f)
        assertEquals("≈ 0.0 m", SpatialDistanceCalculator.formatDistance(dist))
    }

    // 2. (3,0) -> 3.0m (along X axis)
    @Test
    fun distance_alongXAxis_matchesOffset() {
        val dist = SpatialDistanceCalculator.calculateDistance(0.0f, 0.0f, 3.0f, 0.0f)
        assertEquals(3.0f, dist, 0.0001f)
        assertEquals("≈ 3.0 m", SpatialDistanceCalculator.formatDistance(dist))
    }

    // 3. (0,4) -> 4.0m (along Z axis)
    @Test
    fun distance_alongZAxis_matchesOffset() {
        val dist = SpatialDistanceCalculator.calculateDistance(0.0f, 0.0f, 0.0f, 4.0f)
        assertEquals(4.0f, dist, 0.0001f)
        assertEquals("≈ 4.0 m", SpatialDistanceCalculator.formatDistance(dist))
    }

    // 4. (3,4) -> 5.0m (Quadrant I: +X, +Z)
    @Test
    fun distance_quadrantI_matchesPythagoreanTriple() {
        val dist = SpatialDistanceCalculator.calculateDistance(0.0f, 0.0f, 3.0f, 4.0f)
        assertEquals(5.0f, dist, 0.0001f)
        assertEquals("≈ 5.0 m", SpatialDistanceCalculator.formatDistance(dist))
    }

    // 5. (-3,4) -> 5.0m (Quadrant II: -X, +Z)
    @Test
    fun distance_quadrantII_matchesPythagoreanTriple() {
        val dist = SpatialDistanceCalculator.calculateDistance(0.0f, 0.0f, -3.0f, 4.0f)
        assertEquals(5.0f, dist, 0.0001f)
        assertEquals("≈ 5.0 m", SpatialDistanceCalculator.formatDistance(dist))
    }

    // 6. (-3,-4) -> 5.0m (Quadrant III: -X, -Z)
    @Test
    fun distance_quadrantIII_matchesPythagoreanTriple() {
        val dist = SpatialDistanceCalculator.calculateDistance(0.0f, 0.0f, -3.0f, -4.0f)
        assertEquals(5.0f, dist, 0.0001f)
        assertEquals("≈ 5.0 m", SpatialDistanceCalculator.formatDistance(dist))
    }

    // 7. (3,-4) -> 5.0m (Quadrant IV: +X, -Z)
    @Test
    fun distance_quadrantIV_matchesPythagoreanTriple() {
        val dist = SpatialDistanceCalculator.calculateDistance(0.0f, 0.0f, 3.0f, -4.0f)
        assertEquals(5.0f, dist, 0.0001f)
        assertEquals("≈ 5.0 m", SpatialDistanceCalculator.formatDistance(dist))
    }

    // 8. Decimal coordinates: (1.5, 2.0) -> 2.5m, (12.4, 8.7) -> ≈ 15.1 m
    @Test
    fun distance_decimalCoordinates_calculatesAccurately() {
        val dist1 = SpatialDistanceCalculator.calculateDistance(0.0f, 0.0f, 1.5f, 2.0f)
        assertEquals(2.5f, dist1, 0.0001f)
        assertEquals("≈ 2.5 m", SpatialDistanceCalculator.formatDistance(dist1))

        val dist2 = SpatialDistanceCalculator.calculateDistance(0.0f, 0.0f, 12.4f, 8.7f)
        assertEquals(15.1476f, dist2, 0.001f)
        assertEquals("≈ 15.1 m", SpatialDistanceCalculator.formatDistance(dist2))
    }

    // 9. Invariance under object rotation: rotation does NOT alter distance from anchor
    @Test
    fun distance_isInvariantUnderObjectRotation() {
        val anchor = Anchor(x = 0.0f, z = 0.0f)
        val rotations = listOf(0.0f, 45.0f, 90.0f, 180.0f, 270.0f, 315.0f)

        for (deg in rotations) {
            val obj = LayoutObject(
                assetType = AssetType.TRUCK,
                x = 6.0f,
                z = 8.0f,
                rotationDegrees = deg
            )
            val dist = SpatialDistanceCalculator.calculateDistanceToAnchor(obj, anchor)
            assertEquals(10.0f, dist, 0.0001f)
        }
    }

    // 10. Invariance under viewport zoom and pan
    @Test
    fun distance_isInvariantUnderViewportPanAndZoom() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TENT)
        viewModel.addAssetAt(9.0f, 12.0f)

        val tent = viewModel.uiState.value.selectedObject!!
        val baselineDist = SpatialDistanceCalculator.calculateDistanceToAnchor(tent)
        assertEquals(15.0f, baselineDist, 0.0001f)

        // Viewport Pan
        viewModel.onPan(Offset(320.0f, -450.0f))
        val distAfterPan = SpatialDistanceCalculator.calculateDistanceToAnchor(
            viewModel.uiState.value.selectedObject!!
        )
        assertEquals(15.0f, distAfterPan, 0.0001f)

        // Viewport Zoom In & Zoom Out
        viewModel.zoomIn()
        viewModel.zoomIn()
        val distAfterZoom = SpatialDistanceCalculator.calculateDistanceToAnchor(
            viewModel.uiState.value.selectedObject!!
        )
        assertEquals(15.0f, distAfterZoom, 0.0001f)

        viewModel.resetView()
        val distAfterReset = SpatialDistanceCalculator.calculateDistanceToAnchor(
            viewModel.uiState.value.selectedObject!!
        )
        assertEquals(15.0f, distAfterReset, 0.0001f)
    }

    // 11. Object-to-Object distance calculation (future-ready architecture)
    @Test
    fun distance_betweenTwoObjects_calculatesCorrectly() {
        val obj1 = LayoutObject(assetType = AssetType.TENT, x = 2.0f, z = 3.0f)
        val obj2 = LayoutObject(assetType = AssetType.CAR, x = 5.0f, z = 7.0f)

        val dist = SpatialDistanceCalculator.calculateDistanceBetween(obj1, obj2)
        assertEquals(5.0f, dist, 0.0001f)
        assertEquals("≈ 5.0 m", SpatialDistanceCalculator.formatDistance(dist))
    }

    // 12. Measurement toggle state in PlanningViewModel
    @Test
    fun measurementToggle_controlsVisibilityState() {
        val viewModel = PlanningViewModel()
        assertTrue(viewModel.uiState.value.showDistances)

        viewModel.toggleShowDistances()
        assertFalse(viewModel.uiState.value.showDistances)

        viewModel.toggleShowDistances()
        assertTrue(viewModel.uiState.value.showDistances)

        viewModel.setShowDistances(false)
        assertFalse(viewModel.uiState.value.showDistances)
    }
}
