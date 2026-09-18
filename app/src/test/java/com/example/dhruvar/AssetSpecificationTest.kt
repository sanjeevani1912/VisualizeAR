package com.example.dhruvar

import androidx.compose.ui.geometry.Offset
import com.example.dhruvar.domain.model.AssetSpecification
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.spatial.CoordinateTransformer
import com.example.dhruvar.domain.spatial.SpatialDistanceCalculator
import com.example.dhruvar.viewmodel.PlanningViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive unit test suite verifying Part 7: Physical Asset Specifications & Real-World Dimensions.
 *
 * Validates:
 * 1. Every AssetType has a valid, non-null AssetSpecification.
 * 2. All physical dimensions (width, length, height) are strictly positive (> 0.0m).
 * 3. Object rendering and volumetric footprint calculation use physical dimensions.
 * 4. Rotation alters orientation but does not alter physical dimensions.
 * 5. Moving an object does not alter its physical dimensions.
 * 6. Duplicating an object preserves exact physical dimensions.
 * 7. Deleting an object does not affect other objects or their dimensions.
 * 8. Spatial distance calculation remains strictly position-based and unaffected by dimensions.
 * 9. Viewport zoom does not alter logical world dimensions.
 * 10. Viewport pan does not alter logical world dimensions.
 * 11. Custom dimension editing rejects zero and negative values.
 * 12. Custom dimension updates preserve position and rotation.
 */
class AssetSpecificationTest {

    // 1. Every AssetType has a valid AssetSpecification
    @Test
    fun assetTypes_haveAuthoritativeSpecifications() {
        for (assetType in AssetType.entries) {
            val spec = assetType.defaultSpecification
            assertNotNull("Specification must not be null for ${assetType.name}", spec)
            assertEquals(assetType.defaultWidthMeters, spec.widthMeters, 0.001f)
            assertEquals(assetType.defaultLengthMeters, spec.lengthMeters, 0.001f)
            assertEquals(assetType.defaultHeightMeters, spec.heightMeters, 0.001f)
        }
    }

    // 2. All dimensions are strictly positive (> 0.0m)
    @Test
    fun assetSpecifications_dimensionsAreStrictlyPositive() {
        for (assetType in AssetType.entries) {
            val spec = assetType.defaultSpecification
            assertTrue("${assetType.name} width must be > 0", spec.widthMeters > 0.0f)
            assertTrue("${assetType.name} length must be > 0", spec.lengthMeters > 0.0f)
            assertTrue("${assetType.name} height must be > 0", spec.heightMeters > 0.0f)
            assertTrue("${assetType.name} footprint area must be > 0", spec.footprintAreaSqMeters > 0.0f)
            assertTrue("${assetType.name} volume must be > 0", spec.volumeCuMeters > 0.0f)
        }

        // Test that zero or negative values trigger IllegalArgumentException
        assertThrows(IllegalArgumentException::class.java) {
            AssetSpecification(widthMeters = 0.0f, lengthMeters = 5.0f, heightMeters = 2.0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AssetSpecification(widthMeters = 3.0f, lengthMeters = -2.0f, heightMeters = 2.0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AssetSpecification(widthMeters = 3.0f, lengthMeters = 5.0f, heightMeters = 0.0f)
        }
    }

    // 3. Object rendering and spatial calculations use physical dimensions
    @Test
    fun layoutObject_usesPhysicalFootprintDimensions() {
        val transformer = CoordinateTransformer(
            viewportWidth = 800f,
            viewportHeight = 600f,
            panOffset = Offset.Zero,
            zoom = 1.0f,
            basePixelsPerMeter = 28.0f
        )

        val truck = LayoutObject(
            assetType = AssetType.TRUCK,
            widthMeters = 2.5f,
            lengthMeters = 8.0f,
            heightMeters = 3.2f
        )

        assertEquals(2.5f, truck.widthMeters, 0.001f)
        assertEquals(8.0f, truck.lengthMeters, 0.001f)
        assertEquals(3.2f, truck.heightMeters, 0.001f)
        assertEquals(20.0f, truck.specification.footprintAreaSqMeters, 0.001f)
        assertEquals(64.0f, truck.specification.volumeCuMeters, 0.001f)

        // Verify conversion to screen pixels is proportional to meters
        val widthPx = transformer.metersToPixels(truck.widthMeters)
        val lengthPx = transformer.metersToPixels(truck.lengthMeters)
        assertEquals(70.0f, widthPx, 0.001f) // 2.5m * 28 px/m = 70px
        assertEquals(224.0f, lengthPx, 0.001f) // 8.0m * 28 px/m = 224px
    }

    // 4. Rotation does not modify dimensions
    @Test
    fun rotation_doesNotModifyPhysicalDimensions() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.CAR)
        viewModel.addAssetAt(5.0f, 5.0f)

        val initialObj = viewModel.uiState.value.selectedObject!!
        val initialW = initialObj.widthMeters
        val initialL = initialObj.lengthMeters
        val initialH = initialObj.heightMeters

        // Rotate through various angles
        viewModel.rotateSelectedObject(45.0f)
        viewModel.rotateSelectedObject(45.0f) // 90°
        viewModel.rotateSelectedObject(90.0f) // 180°
        viewModel.rotateSelectedObject(90.0f) // 270°

        val rotatedObj = viewModel.uiState.value.selectedObject!!
        assertEquals(270.0f, rotatedObj.rotationDegrees, 0.001f)
        assertEquals(initialW, rotatedObj.widthMeters, 0.001f)
        assertEquals(initialL, rotatedObj.lengthMeters, 0.001f)
        assertEquals(initialH, rotatedObj.heightMeters, 0.001f)
    }

    // 5. Moving does not modify dimensions
    @Test
    fun moving_doesNotModifyPhysicalDimensions() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TENT)
        viewModel.addAssetAt(2.0f, 3.0f)

        val tent = viewModel.uiState.value.selectedObject!!
        val w = tent.widthMeters
        val l = tent.lengthMeters
        val h = tent.heightMeters

        viewModel.moveObject(tent.id, 25.0f, -40.0f)

        val movedTent = viewModel.uiState.value.currentLayout.objects.first { it.id == tent.id }
        assertEquals(25.0f, movedTent.x, 0.001f)
        assertEquals(-40.0f, movedTent.z, 0.001f)
        assertEquals(w, movedTent.widthMeters, 0.001f)
        assertEquals(l, movedTent.lengthMeters, 0.001f)
        assertEquals(h, movedTent.heightMeters, 0.001f)
    }

    // 6. Duplicating preserves dimensions
    @Test
    fun duplicate_preservesPhysicalDimensions() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TRUCK)
        viewModel.addAssetAt(0.0f, 0.0f)

        // Custom dimension override
        viewModel.updateSelectedObjectDimensions(3.0f, 10.0f)
        val original = viewModel.uiState.value.selectedObject!!

        viewModel.duplicateSelectedObject(2.0f, 2.0f)

        val duplicate = viewModel.uiState.value.selectedObject!!
        assertEquals(original.widthMeters, duplicate.widthMeters, 0.001f)
        assertEquals(original.lengthMeters, duplicate.lengthMeters, 0.001f)
        assertEquals(original.heightMeters, duplicate.heightMeters, 0.001f)
        assertEquals(30.0f, duplicate.specification.footprintAreaSqMeters, 0.001f)
    }

    // 7. Deleting does not affect other objects' dimensions
    @Test
    fun delete_doesNotAffectOtherObjectsDimensions() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()

        viewModel.selectAssetType(AssetType.ANTENNA)
        viewModel.addAssetAt(1.0f, 1.0f)
        val antennaId = viewModel.uiState.value.selectedObjectId!!

        viewModel.selectAssetType(AssetType.TRENCH)
        viewModel.addAssetAt(10.0f, 10.0f)
        val trenchId = viewModel.uiState.value.selectedObjectId!!

        // Delete trench
        viewModel.deleteSelectedObject()

        assertEquals(1, viewModel.uiState.value.currentLayout.objects.size)
        val remainingAntenna = viewModel.uiState.value.currentLayout.objects.first()
        assertEquals(antennaId, remainingAntenna.id)
        assertEquals(AssetType.ANTENNA.defaultWidthMeters, remainingAntenna.widthMeters, 0.001f)
        assertEquals(AssetType.ANTENNA.defaultLengthMeters, remainingAntenna.lengthMeters, 0.001f)
    }

    // 8. Distance calculation remains correct and independent of dimensions
    @Test
    fun distance_remainsIndependentOfPhysicalDimensions() {
        val objSmall = LayoutObject(
            assetType = AssetType.ANTENNA,
            x = 3.0f,
            z = 4.0f,
            widthMeters = 1.0f,
            lengthMeters = 1.0f
        )
        val objLarge = LayoutObject(
            assetType = AssetType.TRENCH,
            x = 3.0f,
            z = 4.0f,
            widthMeters = 5.0f,
            lengthMeters = 25.0f
        )

        val distSmall = SpatialDistanceCalculator.calculateDistanceToAnchor(objSmall)
        val distLarge = SpatialDistanceCalculator.calculateDistanceToAnchor(objLarge)

        assertEquals(5.0f, distSmall, 0.0001f)
        assertEquals(5.0f, distLarge, 0.0001f)
        assertEquals(distSmall, distLarge, 0.0001f)
    }

    // 9. Viewport zoom does not alter logical dimensions
    @Test
    fun zoom_doesNotAlterLogicalDimensions() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TENT)
        viewModel.addAssetAt(4.0f, 4.0f)

        val initialObj = viewModel.uiState.value.selectedObject!!
        val initialW = initialObj.widthMeters
        val initialL = initialObj.lengthMeters

        viewModel.zoomIn()
        viewModel.zoomIn()
        viewModel.zoomOut()

        val afterZoom = viewModel.uiState.value.selectedObject!!
        assertEquals(initialW, afterZoom.widthMeters, 0.001f)
        assertEquals(initialL, afterZoom.lengthMeters, 0.001f)
    }

    // 10. Viewport pan does not alter logical dimensions
    @Test
    fun pan_doesNotAlterLogicalDimensions() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TRUCK)
        viewModel.addAssetAt(2.0f, 2.0f)

        val initialObj = viewModel.uiState.value.selectedObject!!
        val initialW = initialObj.widthMeters
        val initialL = initialObj.lengthMeters

        viewModel.onPan(Offset(250f, -180f))

        val afterPan = viewModel.uiState.value.selectedObject!!
        assertEquals(initialW, afterPan.widthMeters, 0.001f)
        assertEquals(initialL, afterPan.lengthMeters, 0.001f)
    }

    // 11. Custom dimension editing rejects non-positive values and applies valid updates
    @Test
    fun customDimensionEditing_validatesAndApplies() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TENT)
        viewModel.addAssetAt(0.0f, 0.0f)

        val initial = viewModel.uiState.value.selectedObject!!
        val initialW = initial.widthMeters
        val initialL = initial.lengthMeters

        // Attempt invalid updates: zero or negative
        viewModel.updateSelectedObjectDimensions(0.0f, 5.0f)
        assertEquals(initialW, viewModel.uiState.value.selectedObject!!.widthMeters, 0.001f)

        viewModel.updateSelectedObjectDimensions(-3.0f, 5.0f)
        assertEquals(initialW, viewModel.uiState.value.selectedObject!!.widthMeters, 0.001f)

        viewModel.updateSelectedObjectDimensions(5.0f, 0.0f)
        assertEquals(initialL, viewModel.uiState.value.selectedObject!!.lengthMeters, 0.001f)

        // Valid update
        viewModel.updateSelectedObjectDimensions(6.5f, 9.0f)
        val updated = viewModel.uiState.value.selectedObject!!
        assertEquals(6.5f, updated.widthMeters, 0.001f)
        assertEquals(9.0f, updated.lengthMeters, 0.001f)
    }

    // 12. Custom dimension updates preserve position and rotation
    @Test
    fun customDimensionUpdates_preservePositionAndRotation() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.CAR)
        viewModel.addAssetAt(8.5f, -12.3f)
        viewModel.setSelectedObjectRotation(45.0f)

        viewModel.updateSelectedObjectDimensions(2.2f, 5.2f)

        val updated = viewModel.uiState.value.selectedObject!!
        assertEquals(8.5f, updated.x, 0.001f)
        assertEquals(-12.3f, updated.z, 0.001f)
        assertEquals(45.0f, updated.rotationDegrees, 0.001f)
        assertEquals(2.2f, updated.widthMeters, 0.001f)
        assertEquals(5.2f, updated.lengthMeters, 0.001f)
    }
}
