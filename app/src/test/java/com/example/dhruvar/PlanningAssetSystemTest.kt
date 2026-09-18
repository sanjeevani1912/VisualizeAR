package com.example.dhruvar

import androidx.compose.ui.geometry.Offset
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.spatial.CoordinateTransformer
import com.example.dhruvar.domain.spatial.ObjectHitTester
import com.example.dhruvar.viewmodel.PlanningViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Part 3: Asset Placement, Object Selection, Deletion,
 * Spatial Coordinate Integrity, and Hit Testing.
 */
class PlanningAssetSystemTest {

    // 1. Adding a TENT creates a LayoutObject of type TENT
    @Test
    fun addAsset_tent_createsLayoutObjectWithTentType() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TENT)

        viewModel.addAssetAt(xMeters = 5.0f, zMeters = 3.0f)

        val objects = viewModel.uiState.value.currentLayout.objects
        assertEquals(1, objects.size)
        val tent = objects.first()
        assertEquals(AssetType.TENT, tent.assetType)
        assertEquals(5.0f, tent.x, 0.001f)
        assertEquals(3.0f, tent.z, 0.001f)
    }

    // 2. Adding a TRUCK creates a separate LayoutObject of type TRUCK
    @Test
    fun addAsset_truck_createsLayoutObjectWithTruckType() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TRUCK)

        viewModel.addAssetAt(xMeters = -8.0f, zMeters = 4.0f)

        val objects = viewModel.uiState.value.currentLayout.objects
        assertEquals(1, objects.size)
        val truck = objects.first()
        assertEquals(AssetType.TRUCK, truck.assetType)
        assertEquals(-8.0f, truck.x, 0.001f)
        assertEquals(4.0f, truck.z, 0.001f)
    }

    // 3. Multiple objects of the same AssetType are allowed
    @Test
    fun addAsset_multipleSameType_createsDistinctInstances() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TENT)

        viewModel.addAssetAt(xMeters = 2.0f, zMeters = 2.0f)
        viewModel.addAssetAt(xMeters = 6.0f, zMeters = 2.0f)
        viewModel.addAssetAt(xMeters = 10.0f, zMeters = 2.0f)

        val objects = viewModel.uiState.value.currentLayout.objects
        assertEquals(3, objects.size)
        assertTrue(objects.all { it.assetType == AssetType.TENT })
    }

    // 4. Every object has a unique ID
    @Test
    fun addAsset_everyObjectHasUniqueId() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()

        viewModel.selectAssetType(AssetType.CAR)
        viewModel.addAssetAt(1f, 1f)
        viewModel.addAssetAt(2f, 2f)

        viewModel.selectAssetType(AssetType.ANTENNA)
        viewModel.addAssetAt(3f, 3f)

        val objects = viewModel.uiState.value.currentLayout.objects
        val ids = objects.map { it.id }
        assertEquals(3, ids.distinct().size)
    }

    // 5. Logical coordinates are calculated from the tapped canvas position
    @Test
    fun addAsset_logicalCoordinatesPreservedExactFromTap() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TRENCH)

        val targetX = 14.5f
        val targetZ = -9.2f
        viewModel.addAssetAt(targetX, targetZ)

        val trench = viewModel.uiState.value.currentLayout.objects.first()
        assertEquals(targetX, trench.x, 0.001f)
        assertEquals(targetZ, trench.z, 0.001f)
    }

    // 6. Newly created object rotation starts at 0 degrees and scale is 1.0
    @Test
    fun addAsset_defaultsRotationToZeroAndScaleToOne() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.ANTENNA)

        viewModel.addAssetAt(0.0f, 5.0f)

        val antenna = viewModel.uiState.value.currentLayout.objects.first()
        assertEquals(0.0f, antenna.rotationDegrees, 0.001f)
        assertEquals(1.0f, antenna.scale, 0.001f)
    }

    // 7. Selecting an object updates selectedObjectId and isSelected flag
    @Test
    fun selectObject_updatesSelectedObjectIdAndIsSelectedFlags() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()

        viewModel.selectAssetType(AssetType.TENT)
        viewModel.addAssetAt(0f, 0f)
        viewModel.selectAssetType(AssetType.TRUCK)
        viewModel.addAssetAt(10f, 10f)

        val objects = viewModel.uiState.value.currentLayout.objects
        val firstObjId = objects[0].id
        val secondObjId = objects[1].id

        // Select the first object
        viewModel.selectObject(firstObjId)
        assertEquals(firstObjId, viewModel.uiState.value.selectedObjectId)
        assertTrue(viewModel.uiState.value.currentLayout.objects.first { it.id == firstObjId }.isSelected)
        assertFalse(viewModel.uiState.value.currentLayout.objects.first { it.id == secondObjId }.isSelected)

        // Deselect
        viewModel.deselectObject()
        assertNull(viewModel.uiState.value.selectedObjectId)
        assertFalse(viewModel.uiState.value.currentLayout.objects.any { it.isSelected })
    }

    // 8. Deleting the selected object removes only that object
    @Test
    fun deleteSelectedObject_removesOnlySelectedObject() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()

        viewModel.selectAssetType(AssetType.TENT)
        viewModel.addAssetAt(1f, 1f)
        val tentId = viewModel.uiState.value.currentLayout.objects.last().id

        viewModel.selectAssetType(AssetType.CAR)
        viewModel.addAssetAt(2f, 2f)
        val carId = viewModel.uiState.value.currentLayout.objects.last().id

        assertEquals(2, viewModel.uiState.value.currentLayout.objects.size)

        // Select and delete the tent
        viewModel.selectObject(tentId)
        viewModel.deleteSelectedObject()

        val remainingObjects = viewModel.uiState.value.currentLayout.objects
        assertEquals(1, remainingObjects.size)
        assertEquals(carId, remainingObjects.first().id)
        assertNull(viewModel.uiState.value.selectedObjectId)
    }

    // 9. Deleting an object does not modify the anchor
    @Test
    fun deleteObject_preservesAnchorOrigin() {
        val viewModel = PlanningViewModel()
        val originalAnchor = viewModel.uiState.value.currentLayout.anchor

        viewModel.deleteSelectedObject()

        val currentAnchor = viewModel.uiState.value.currentLayout.anchor
        assertEquals(originalAnchor.x, currentAnchor.x, 0.001f)
        assertEquals(originalAnchor.z, currentAnchor.z, 0.001f)
        assertEquals(originalAnchor.id, currentAnchor.id)
    }

    // 10 & 11. Zooming and panning do not alter the logical metric coordinates of placed objects
    @Test
    fun zoomAndPan_doNotModifyLogicalCoordinatesOfPlacedObjects() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TRUCK)
        viewModel.addAssetAt(15.0f, -20.0f)

        val initialObj = viewModel.uiState.value.currentLayout.objects.first()
        assertEquals(15.0f, initialObj.x, 0.001f)
        assertEquals(-20.0f, initialObj.z, 0.001f)

        // Perform pan
        viewModel.onPan(Offset(350f, -200f))
        val postPanObj = viewModel.uiState.value.currentLayout.objects.first()
        assertEquals(15.0f, postPanObj.x, 0.001f)
        assertEquals(-20.0f, postPanObj.z, 0.001f)

        // Perform zoom
        viewModel.zoomIn()
        viewModel.zoomIn()
        val postZoomObj = viewModel.uiState.value.currentLayout.objects.first()
        assertEquals(15.0f, postZoomObj.x, 0.001f)
        assertEquals(-20.0f, postZoomObj.z, 0.001f)
    }

    // 12. Spatial Hit-Testing
    @Test
    fun hitTesting_identifiesPlacedObjectAtScreenOffset() {
        val transformer = CoordinateTransformer(
            viewportWidth = 1000f,
            viewportHeight = 800f,
            panOffset = Offset.Zero,
            zoom = 1.0f,
            basePixelsPerMeter = 25.0f
        )

        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TENT) // width = 4m, length = 6m
        viewModel.addAssetAt(10.0f, 5.0f) // (X=10m, Z=5m)

        val tent = viewModel.uiState.value.currentLayout.objects.first()
        val tentScreenPos = transformer.worldToCanvas(tent.x, tent.z)

        // Direct hit on tent center
        val hitResult = ObjectHitTester.findHitObject(
            tapOffset = tentScreenPos,
            objects = viewModel.uiState.value.currentLayout.objects,
            transformer = transformer
        )
        assertNotNull(hitResult)
        assertEquals(tent.id, hitResult?.id)

        // Miss on empty canvas (far away at screen origin)
        val missResult = ObjectHitTester.findHitObject(
            tapOffset = Offset(10f, 10f),
            objects = viewModel.uiState.value.currentLayout.objects,
            transformer = transformer
        )
        assertNull(missResult)
    }
}
