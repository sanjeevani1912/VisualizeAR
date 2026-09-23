package com.example.dhruvar

import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.viewmodel.PlanningViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying domain models and PlanningViewModel architecture for Step 1.
 */
class PlanningArchitectureTest {

    @Test
    fun anchor_initializesWithCorrectDefaultCoordinates() {
        val anchor = Anchor()
        assertEquals(0.0f, anchor.x, 0.001f)
        assertEquals(0.0f, anchor.z, 0.001f)
        assertEquals("Origin Anchor", anchor.label)
        assertNotNull(anchor.id)
    }

    @Test
    fun assetTypes_haveAccurateRealWorldMetricDimensions() {
        val tent = AssetType.TENT
        assertEquals(4.0f, tent.defaultWidthMeters, 0.001f)
        assertEquals(6.0f, tent.defaultLengthMeters, 0.001f)

        val truck = AssetType.TRUCK
        assertEquals(2.5f, truck.defaultWidthMeters, 0.001f)
        assertEquals(8.0f, truck.defaultLengthMeters, 0.001f)

        val antenna = AssetType.ANTENNA
        assertEquals(1.5f, antenna.defaultWidthMeters, 0.001f)
        assertEquals(1.5f, antenna.defaultLengthMeters, 0.001f)
    }

    @Test
    fun layoutObject_storesMetricSpatialCoordinatesNotPixels() {
        val obj = LayoutObject(
            assetType = AssetType.TENT,
            name = "Test Tent",
            x = 10.0f, // meters
            z = 15.0f, // meters
            rotationDegrees = 90.0f,
            scale = 1.0f
        )

        assertEquals(10.0f, obj.x, 0.001f)
        assertEquals(15.0f, obj.z, 0.001f)
        assertEquals(90.0f, obj.rotationDegrees, 0.001f)
        assertEquals(1.0f, obj.scale, 0.001f)
        assertFalse(obj.isSelected)
    }

    @Test
    fun planningViewModel_initialStateContainsDefaultLayout() {
        val viewModel = PlanningViewModel()
        val state = viewModel.uiState.value

        assertNotNull(state.currentLayout)
        assertNotNull(state.currentLayout.anchor)
        assertTrue(state.currentLayout.objects.isEmpty())
        assertEquals(AssetType.TENT, state.selectedAssetType)
    }

    @Test
    fun planningViewModel_selectAssetType_updatesState() {
        val viewModel = PlanningViewModel()
        viewModel.selectAssetType(AssetType.ANTENNA)

        assertEquals(AssetType.ANTENNA, viewModel.uiState.value.selectedAssetType)
    }

    @Test
    fun planningViewModel_addAssetAt_appendsObjectWithLogicalMetricCoordinates() {
        val viewModel = PlanningViewModel()
        viewModel.selectAssetType(AssetType.CAR)

        val initialCount = viewModel.uiState.value.currentLayout.objects.size
        viewModel.addAssetAt(xMeters = 5.0f, zMeters = -8.0f)

        val updatedObjects = viewModel.uiState.value.currentLayout.objects
        assertEquals(initialCount + 1, updatedObjects.size)

        val addedObj = updatedObjects.last()
        assertEquals(AssetType.CAR, addedObj.assetType)
        assertEquals(5.0f, addedObj.x, 0.001f)
        assertEquals(-8.0f, addedObj.z, 0.001f)
    }

    @Test
    fun planningViewModel_selectObject_marksTargetObjectAsSelected() {
        val viewModel = PlanningViewModel()
        viewModel.selectAssetType(AssetType.TENT)
        viewModel.addAssetAt(xMeters = 2.0f, zMeters = 2.0f)
        val targetObjId = viewModel.uiState.value.currentLayout.objects.first().id

        viewModel.selectObject(targetObjId)

        val selectedObj = viewModel.uiState.value.currentLayout.objects.first { it.id == targetObjId }
        assertTrue(selectedObj.isSelected)
        assertEquals(targetObjId, viewModel.uiState.value.selectedObjectId)
    }

    @Test
    fun planningViewModel_clearLayout_removesAllObjects() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()

        assertTrue(viewModel.uiState.value.currentLayout.objects.isEmpty())
    }
}
