package com.example.dhruvar

import androidx.compose.ui.geometry.Offset
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.spatial.CoordinateTransformer
import com.example.dhruvar.domain.spatial.ObjectHitTester
import com.example.dhruvar.viewmodel.PlanningViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying Part 4: Interactive Object Movement, Drag Gestures,
 * Duplication, Hit-Testing under Pan/Zoom, and Anchor Isolation.
 */
class PlanningObjectInteractionTest {

    // 1 & 2. Moving an object changes its logical X and Z coordinates
    @Test
    fun moveObject_updatesTargetObjectWorldCoordinates() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TRUCK)
        viewModel.addAssetAt(5.0f, 2.0f)

        val truck = viewModel.uiState.value.currentLayout.objects.first()
        val newX = 12.37f
        val newZ = 8.62f

        viewModel.moveObject(truck.id, newX, newZ)

        val movedTruck = viewModel.uiState.value.currentLayout.objects.first { it.id == truck.id }
        assertEquals(newX, movedTruck.x, 0.001f)
        assertEquals(newZ, movedTruck.z, 0.001f)
    }

    // 3. Moving an object does NOT modify the spatial Anchor datum
    @Test
    fun moveObject_preservesAnchorOriginAtZeroZero() {
        val viewModel = PlanningViewModel()
        val initialAnchor = viewModel.uiState.value.currentLayout.anchor

        viewModel.selectAssetType(AssetType.TENT)
        viewModel.addAssetAt(1.0f, 1.0f)
        val tent = viewModel.uiState.value.currentLayout.objects.last()

        viewModel.moveObject(tent.id, 25.0f, -40.0f)

        val currentAnchor = viewModel.uiState.value.currentLayout.anchor
        assertEquals(0.0f, currentAnchor.x, 0.001f)
        assertEquals(0.0f, currentAnchor.z, 0.001f)
        assertEquals(initialAnchor.id, currentAnchor.id)
    }

    // 4. Moving an object does NOT modify any other placed objects
    @Test
    fun moveObject_doesNotAlterOtherPlacedObjects() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()

        viewModel.selectAssetType(AssetType.CAR)
        viewModel.addAssetAt(10.0f, 10.0f)
        val carId = viewModel.uiState.value.currentLayout.objects.last().id

        viewModel.selectAssetType(AssetType.ANTENNA)
        viewModel.addAssetAt(-5.0f, -5.0f)
        val antennaId = viewModel.uiState.value.currentLayout.objects.last().id

        // Move only the car
        viewModel.moveObject(carId, 22.0f, 18.0f)

        val car = viewModel.uiState.value.currentLayout.objects.first { it.id == carId }
        val antenna = viewModel.uiState.value.currentLayout.objects.first { it.id == antennaId }

        assertEquals(22.0f, car.x, 0.001f)
        assertEquals(18.0f, car.z, 0.001f)
        // Antenna should remain exactly at (-5, -5)
        assertEquals(-5.0f, antenna.x, 0.001f)
        assertEquals(-5.0f, antenna.z, 0.001f)
    }

    // 5, 6 & 7. Duplicate receives a new ID, matches asset type, and has an independent offset position
    @Test
    fun duplicateSelectedObject_createsIndependentCloneWithUniqueId() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()

        viewModel.selectAssetType(AssetType.TENT)
        viewModel.addAssetAt(10.0f, 8.0f)
        val original = viewModel.uiState.value.currentLayout.objects.first()

        viewModel.selectObject(original.id)
        viewModel.duplicateSelectedObject(offsetXMeters = 1.5f, offsetZMeters = 1.5f)

        val objects = viewModel.uiState.value.currentLayout.objects
        assertEquals(2, objects.size)

        val duplicate = objects.first { it.id != original.id }
        assertNotEquals(original.id, duplicate.id)
        assertEquals(AssetType.TENT, duplicate.assetType)
        assertEquals(original.x + 1.5f, duplicate.x, 0.001f)
        assertEquals(original.z + 1.5f, duplicate.z, 0.001f)
        assertEquals(original.rotationDegrees, duplicate.rotationDegrees, 0.001f)
        assertEquals(original.scale, duplicate.scale, 0.001f)
        assertEquals(duplicate.id, viewModel.uiState.value.selectedObjectId)
        assertTrue(duplicate.isSelected)

        // Moving duplicate should not affect original
        viewModel.moveObject(duplicate.id, 50.0f, 50.0f)
        val originalAfter = viewModel.uiState.value.currentLayout.objects.first { it.id == original.id }
        assertEquals(10.0f, originalAfter.x, 0.001f)
        assertEquals(8.0f, originalAfter.z, 0.001f)
    }

    // 8. Delete removes only the selected object
    @Test
    fun deleteSelectedObject_removesOnlySelectedEntity() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()

        viewModel.selectAssetType(AssetType.TRENCH)
        viewModel.addAssetAt(0.0f, 0.0f)
        val trenchId = viewModel.uiState.value.currentLayout.objects.last().id

        viewModel.selectAssetType(AssetType.CAR)
        viewModel.addAssetAt(10.0f, 0.0f)
        val carId = viewModel.uiState.value.currentLayout.objects.last().id

        viewModel.selectObject(trenchId)
        viewModel.deleteSelectedObject()

        val objects = viewModel.uiState.value.currentLayout.objects
        assertEquals(1, objects.size)
        assertEquals(carId, objects.first().id)
        assertNull(viewModel.uiState.value.selectedObjectId)
    }

    // 9 & 10. Canvas Pan and Zoom do NOT change object world coordinates
    @Test
    fun panAndZoom_doNotModifyObjectWorldCoordinates() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()

        viewModel.selectAssetType(AssetType.TRUCK)
        viewModel.addAssetAt(8.5f, -14.2f)
        val truckId = viewModel.uiState.value.currentLayout.objects.first().id

        // Execute multiple pans and zooms
        viewModel.onPan(Offset(150f, -220f))
        viewModel.onZoom(1.5f)
        viewModel.onPan(Offset(-80f, 40f))
        viewModel.onZoom(0.8f)

        val truck = viewModel.uiState.value.currentLayout.objects.first { it.id == truckId }
        assertEquals(8.5f, truck.x, 0.001f)
        assertEquals(-14.2f, truck.z, 0.001f)
    }

    // 11. Hit-testing works accurately after Canvas Pan
    @Test
    fun hitTesting_worksAccuratelyAfterPan() {
        val panOffset = Offset(200f, -150f)
        val transformer = CoordinateTransformer(
            viewportWidth = 1000f,
            viewportHeight = 800f,
            panOffset = panOffset,
            zoom = 1.0f,
            basePixelsPerMeter = 25.0f
        )

        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.CAR)
        viewModel.addAssetAt(10.0f, 5.0f) // (X=10m, Z=5m)
        val car = viewModel.uiState.value.currentLayout.objects.first()

        // Calculate car position on screen after pan
        val carScreenPos = transformer.worldToCanvas(car.x, car.z)

        // Hit testing at this panned screen position must successfully find the car
        val hit = ObjectHitTester.findHitObject(
            tapOffset = carScreenPos,
            objects = viewModel.uiState.value.currentLayout.objects,
            transformer = transformer
        )

        assertNotNull(hit)
        assertEquals(car.id, hit?.id)
    }

    // 12. Hit-testing works accurately after Canvas Zoom
    @Test
    fun hitTesting_worksAccuratelyAfterZoom() {
        val zoom = 2.5f // 2.5x zoom
        val transformer = CoordinateTransformer(
            viewportWidth = 1000f,
            viewportHeight = 800f,
            panOffset = Offset.Zero,
            zoom = zoom,
            basePixelsPerMeter = 25.0f
        )

        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.ANTENNA)
        viewModel.addAssetAt(-6.0f, 8.0f) // (X=-6m, Z=8m)
        val antenna = viewModel.uiState.value.currentLayout.objects.first()

        // Calculate antenna position on screen under 2.5x zoom
        val antennaScreenPos = transformer.worldToCanvas(antenna.x, antenna.z)

        val hit = ObjectHitTester.findHitObject(
            tapOffset = antennaScreenPos,
            objects = viewModel.uiState.value.currentLayout.objects,
            transformer = transformer
        )

        assertNotNull(hit)
        assertEquals(antenna.id, hit?.id)
    }
}
