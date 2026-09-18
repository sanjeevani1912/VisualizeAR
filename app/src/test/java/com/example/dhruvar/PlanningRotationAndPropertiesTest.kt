package com.example.dhruvar

import androidx.compose.ui.geometry.Offset
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.spatial.CoordinateTransformer
import com.example.dhruvar.domain.spatial.ObjectHitTester
import com.example.dhruvar.viewmodel.PlanningViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive unit test suite verifying Part 5:
 * - Rotation model & normalization (0°, 90°, 180°, 270°, wrapping)
 * - Incremental rotation steppers (±15°, ±90°)
 * - Direct numeric angle and coordinate (X, Z) editing
 * - Dragging rotated objects with rotation preservation
 * - Duplicating rotated objects
 * - Deleting rotated objects
 * - Spatial hit-testing across various rotation angles
 * - Viewport pan/zoom independence
 */
class PlanningRotationAndPropertiesTest {

    // 1. Cardinal Rotations (0°, 90°, 180°, 270°)
    @Test
    fun rotation_cardinalAngles_0_90_180_270() {
        val angles = listOf(0.0f, 90.0f, 180.0f, 270.0f)
        for (angle in angles) {
            val obj = LayoutObject(
                assetType = AssetType.TRUCK,
                rotationDegrees = angle
            )
            assertEquals(angle, LayoutObject.normalizeDegrees(obj.rotationDegrees), 0.001f)
            assertEquals(angle, obj.withNormalizedRotation(angle).rotationDegrees, 0.001f)
        }
    }

    // 2. Rotation wrapping and normalization
    @Test
    fun rotation_wrapping_normalizesDegrees() {
        // Upper bound wrapping
        assertEquals(0.0f, LayoutObject.normalizeDegrees(360.0f), 0.001f)
        assertEquals(40.0f, LayoutObject.normalizeDegrees(400.0f), 0.001f)
        assertEquals(90.0f, LayoutObject.normalizeDegrees(450.0f), 0.001f)
        assertEquals(0.0f, LayoutObject.normalizeDegrees(720.0f), 0.001f)

        // Negative angle wrapping
        assertEquals(315.0f, LayoutObject.normalizeDegrees(-45.0f), 0.001f)
        assertEquals(270.0f, LayoutObject.normalizeDegrees(-90.0f), 0.001f)
        assertEquals(180.0f, LayoutObject.normalizeDegrees(-180.0f), 0.001f)
        assertEquals(0.0f, LayoutObject.normalizeDegrees(-360.0f), 0.001f)
        assertEquals(340.0f, LayoutObject.normalizeDegrees(-20.0f), 0.001f)
    }

    // 3. Incremental rotation stepping (±15°, ±90°)
    @Test
    fun rotation_stepping_updatesModelCorrectly() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TENT)
        viewModel.addAssetAt(2.0f, 2.0f)

        val tent = viewModel.uiState.value.selectedObject!!
        assertEquals(0.0f, tent.rotationDegrees, 0.001f)

        // Step +15°
        viewModel.rotateSelectedObject(15.0f)
        assertEquals(15.0f, viewModel.uiState.value.selectedObject!!.rotationDegrees, 0.001f)

        // Step +15° again -> 30°
        viewModel.rotateSelectedObject(15.0f)
        assertEquals(30.0f, viewModel.uiState.value.selectedObject!!.rotationDegrees, 0.001f)

        // Step +90° -> 120°
        viewModel.rotateSelectedObject(90.0f)
        assertEquals(120.0f, viewModel.uiState.value.selectedObject!!.rotationDegrees, 0.001f)

        // Step -150° -> 330° (wraps below 0)
        viewModel.rotateSelectedObject(-150.0f)
        assertEquals(330.0f, viewModel.uiState.value.selectedObject!!.rotationDegrees, 0.001f)
    }

    // 4. Direct numeric angle editing
    @Test
    fun rotation_directAngleEditing_setsExactNormalizedAngle() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.CAR)
        viewModel.addAssetAt(0.0f, 0.0f)

        // Set 45°
        viewModel.setSelectedObjectRotation(45.0f)
        assertEquals(45.0f, viewModel.uiState.value.selectedObject!!.rotationDegrees, 0.001f)

        // Set 225.5°
        viewModel.setSelectedObjectRotation(225.5f)
        assertEquals(225.5f, viewModel.uiState.value.selectedObject!!.rotationDegrees, 0.001f)

        // Set negative value -30° -> 330°
        viewModel.setSelectedObjectRotation(-30.0f)
        assertEquals(330.0f, viewModel.uiState.value.selectedObject!!.rotationDegrees, 0.001f)

        // Set oversized value 540° -> 180°
        viewModel.setSelectedObjectRotation(540.0f)
        assertEquals(180.0f, viewModel.uiState.value.selectedObject!!.rotationDegrees, 0.001f)
    }

    // 5. Direct coordinate editing (X and Z in meters)
    @Test
    fun properties_directEditingXAndZ_updatesLogicalCoordinates() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TRUCK)
        viewModel.addAssetAt(1.0f, 1.0f)

        val truckId = viewModel.uiState.value.selectedObjectId!!

        // Edit X and Z
        val targetX = 14.8f
        val targetZ = -7.3f
        viewModel.updateSelectedObjectPosition(targetX, targetZ)

        val updated = viewModel.uiState.value.selectedObject!!
        assertEquals(targetX, updated.x, 0.001f)
        assertEquals(targetZ, updated.z, 0.001f)
        assertEquals(truckId, updated.id)

        // Verify anchor (0, 0) remains completely unaffected
        val anchor = viewModel.uiState.value.currentLayout.anchor
        assertEquals(0.0f, anchor.x, 0.001f)
        assertEquals(0.0f, anchor.z, 0.001f)
    }

    // 6. Dragging rotated objects preserves rotation angle
    @Test
    fun dragging_rotatedObject_preservesRotationAngle() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.ANTENNA)
        viewModel.addAssetAt(5.0f, 5.0f)

        // Rotate antenna to 67.5°
        viewModel.setSelectedObjectRotation(67.5f)
        val initialRotation = viewModel.uiState.value.selectedObject!!.rotationDegrees
        assertEquals(67.5f, initialRotation, 0.001f)

        // Simulate dragging to a new position
        val antennaId = viewModel.uiState.value.selectedObjectId!!
        viewModel.moveObject(antennaId, 22.0f, 35.0f)

        val draggedAntenna = viewModel.uiState.value.currentLayout.objects.first { it.id == antennaId }
        assertEquals(22.0f, draggedAntenna.x, 0.001f)
        assertEquals(35.0f, draggedAntenna.z, 0.001f)
        assertEquals(initialRotation, draggedAntenna.rotationDegrees, 0.001f)
    }

    // 7. Duplicating rotated object retains exact rotation and dimensions
    @Test
    fun duplicate_rotatedObject_retainsExactRotationAndDimensions() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TENT)
        viewModel.addAssetAt(3.0f, 4.0f)

        // Set rotation to 135°
        viewModel.setSelectedObjectRotation(135.0f)
        val original = viewModel.uiState.value.selectedObject!!

        // Duplicate
        viewModel.duplicateSelectedObject(offsetXMeters = 2.0f, offsetZMeters = 2.0f)

        val duplicate = viewModel.uiState.value.selectedObject!!
        assertNotEquals(original.id, duplicate.id)
        assertEquals(original.assetType, duplicate.assetType)
        assertEquals(original.rotationDegrees, duplicate.rotationDegrees, 0.001f)
        assertEquals(original.widthMeters, duplicate.widthMeters, 0.001f)
        assertEquals(original.lengthMeters, duplicate.lengthMeters, 0.001f)
        assertEquals(original.scale, duplicate.scale, 0.001f)
        assertEquals(original.x + 2.0f, duplicate.x, 0.001f)
        assertEquals(original.z + 2.0f, duplicate.z, 0.001f)
        assertTrue(duplicate.isSelected)
    }

    // 8. Deleting rotated object removes only target entity
    @Test
    fun delete_rotatedObject_removesOnlyTarget() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()

        // Place object 1 (rotated 45°)
        viewModel.selectAssetType(AssetType.TENT)
        viewModel.addAssetAt(2.0f, 2.0f)
        viewModel.setSelectedObjectRotation(45.0f)
        val obj1Id = viewModel.uiState.value.selectedObjectId!!

        // Place object 2 (rotated 90°)
        viewModel.selectAssetType(AssetType.TRUCK)
        viewModel.addAssetAt(10.0f, 10.0f)
        viewModel.setSelectedObjectRotation(90.0f)
        val obj2Id = viewModel.uiState.value.selectedObjectId!!

        assertEquals(2, viewModel.uiState.value.currentLayout.objects.size)

        // Delete object 2
        viewModel.deleteSelectedObject()

        assertEquals(1, viewModel.uiState.value.currentLayout.objects.size)
        assertNull(viewModel.uiState.value.selectedObjectId)

        val remaining = viewModel.uiState.value.currentLayout.objects.first()
        assertEquals(obj1Id, remaining.id)
        assertEquals(45.0f, remaining.rotationDegrees, 0.001f)
    }

    // 9. Spatial hit testing on rotated objects across 0°, 45°, 90°, 180°, 270°
    @Test
    fun hitTesting_rotatedObjects_evaluatesOrientedBoundingBox() {
        val transformer = CoordinateTransformer(
            viewportWidth = 1000f,
            viewportHeight = 1000f,
            panOffset = Offset.Zero,
            zoom = 1.0f,
            basePixelsPerMeter = 28.0f
        )

        // Logistics Truck: 2m wide x 8m long -> 56px wide x 224px long
        val truckAtOrigin = LayoutObject(
            assetType = AssetType.TRUCK,
            x = 0.0f,
            z = 0.0f,
            rotationDegrees = 0.0f,
            widthMeters = 2.0f,
            lengthMeters = 8.0f
        )
        val originCenter = transformer.worldToCanvas(0.0f, 0.0f) // (500, 500)

        // At 0°: Length is vertical (Y from 388 to 612), Width is horizontal (X from 472 to 528)
        // Hit inside
        assertNotNull(ObjectHitTester.findHitObject(Offset(500f, 580f), listOf(truckAtOrigin), transformer))
        // Outside lateral width
        assertNull(ObjectHitTester.findHitObject(Offset(540f, 500f), listOf(truckAtOrigin), transformer))

        // At 90°: Vehicle is horizontal! Length is along X (from 388 to 612), Width along Y (from 472 to 528)
        val truckAt90 = truckAtOrigin.copy(rotationDegrees = 90.0f)
        // Point (580, 500) which was OUTSIDE at 0° is now INSIDE at 90°!
        val hit90 = ObjectHitTester.findHitObject(Offset(580f, 500f), listOf(truckAt90), transformer)
        assertNotNull(hit90)
        assertEquals(truckAt90.id, hit90!!.id)
        // Point (500, 580) which was INSIDE at 0° is now OUTSIDE at 90°!
        val miss90 = ObjectHitTester.findHitObject(Offset(500f, 580f), listOf(truckAt90), transformer)
        assertNull(miss90)

        // At 180°: Vehicle points opposite direction; center stays at (500, 500)
        val truckAt180 = truckAtOrigin.copy(rotationDegrees = 180.0f)
        assertNotNull(ObjectHitTester.findHitObject(Offset(500f, 580f), listOf(truckAt180), transformer))

        // At 270°: Same horizontal envelope as 90°
        val truckAt270 = truckAtOrigin.copy(rotationDegrees = 270.0f)
        assertNotNull(ObjectHitTester.findHitObject(Offset(580f, 500f), listOf(truckAt270), transformer))
        assertNull(ObjectHitTester.findHitObject(Offset(500f, 580f), listOf(truckAt270), transformer))
    }

    // 10. Viewport Pan and Zoom do not alter rotated object world coordinates or angles
    @Test
    fun viewport_panAndZoom_preservesRotatedObjectState() {
        val viewModel = PlanningViewModel()
        viewModel.clearLayout()
        viewModel.selectAssetType(AssetType.TRENCH)
        viewModel.addAssetAt(8.5f, -12.0f)
        viewModel.setSelectedObjectRotation(75.0f)

        val originalObj = viewModel.uiState.value.selectedObject!!

        // Execute Pan
        viewModel.onPan(Offset(150.0f, -80.0f))
        // Execute Zoom In
        viewModel.zoomIn()
        // Execute Zoom Out
        viewModel.zoomOut()

        val afterTransform = viewModel.uiState.value.currentLayout.objects.first { it.id == originalObj.id }
        assertEquals(originalObj.x, afterTransform.x, 0.001f)
        assertEquals(originalObj.z, afterTransform.z, 0.001f)
        assertEquals(originalObj.rotationDegrees, afterTransform.rotationDegrees, 0.001f)
        assertEquals(originalObj.widthMeters, afterTransform.widthMeters, 0.001f)
        assertEquals(originalObj.lengthMeters, afterTransform.lengthMeters, 0.001f)
    }
}
