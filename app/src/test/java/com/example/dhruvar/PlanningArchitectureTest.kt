package com.example.dhruvar

import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.AssetSizeConfig
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
        // Real-world average lengths live in AssetSizeConfig; models are scaled uniformly to them.
        val expectedLength = mapOf(
            AssetType.MILITARY_VEHICLE to AssetSizeConfig.MILITARY_VEHICLE_LENGTH_METERS,
            AssetType.STR_MGS to AssetSizeConfig.STR_MGS_LENGTH_METERS,
            AssetType.GUNSHIP to AssetSizeConfig.GUNSHIP_LENGTH_METERS
        )
        expectedLength.forEach { (type, meters) ->
            assertEquals(meters, maxOf(type.defaultWidthMeters, type.defaultLengthMeters), 0.001f)
        }
        // The helicopter is the biggest asset and a vehicle stays car-sized.
        assertTrue(AssetType.GUNSHIP.defaultLengthMeters > AssetType.STR_MGS.defaultLengthMeters)
        assertTrue(AssetType.MILITARY_VEHICLE.defaultLengthMeters in 3.0f..6.0f)
    }

    @Test
    fun layoutObject_storesMetricSpatialCoordinatesNotPixels() {
        val obj = LayoutObject(
            assetType = AssetType.MILITARY_VEHICLE,
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
        assertEquals(AssetType.MILITARY_VEHICLE, state.selectedAssetType)
    }

    @Test
    fun planningViewModel_selectAssetType_updatesState() {
        val viewModel = PlanningViewModel()
        viewModel.selectAssetType(AssetType.K131)

        assertEquals(AssetType.K131, viewModel.uiState.value.selectedAssetType)
    }

    @Test
    fun planningViewModel_addAssetAt_appendsObjectWithLogicalMetricCoordinates() {
        val viewModel = PlanningViewModel()
        viewModel.selectAssetType(AssetType.OFFROAD_VEHICLE)

        val initialCount = viewModel.uiState.value.currentLayout.objects.size
        viewModel.addAssetAt(xMeters = 5.0f, zMeters = -8.0f)

        val updatedObjects = viewModel.uiState.value.currentLayout.objects
        assertEquals(initialCount + 1, updatedObjects.size)

        val addedObj = updatedObjects.last()
        assertEquals(AssetType.OFFROAD_VEHICLE, addedObj.assetType)
        assertEquals(5.0f, addedObj.x, 0.001f)
        assertEquals(-8.0f, addedObj.z, 0.001f)
    }

    @Test
    fun planningViewModel_selectObject_marksTargetObjectAsSelected() {
        val viewModel = PlanningViewModel()
        viewModel.selectAssetType(AssetType.MILITARY_VEHICLE)
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
