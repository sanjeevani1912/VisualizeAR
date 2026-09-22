package com.example.dhruvar

import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.spatial.ARCoordinateTransformer
import com.google.ar.core.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests validating Part 10: AR Calibration and Coordinate Transformation.
 * Verifies 1-to-1 metric scale, planning-to-world coordinate conventions,
 * rotation normalization, and layout preservation across calibration sessions.
 */
class ARCalibrationTest {

    @Test
    fun scaleFactor_isStrictlyOneToOne() {
        assertEquals(1.0f, ARCoordinateTransformer.METERS_PER_PLANNING_UNIT, 0.0001f)
    }

    @Test
    fun coordinateMapping_transformsPlanningXZtoARWorldOffsets() {
        // Origin located at world (0, 0, 0) facing forward (yaw = 0 deg)
        val worldPos = ARCoordinateTransformer.computeWorldPositionPure(
            originX = 0.0f,
            originY = 0.0f,
            originZ = 0.0f,
            originYawDegrees = 0.0f,
            planX = 10.0f,
            planZ = 5.0f
        )

        // Planning +X maps to world +X (lateral offset)
        // Planning +Z maps to world +Z (depth offset)
        // Ground plane height Y remains 0.0
        assertEquals(10.0f, worldPos[0], 0.001f)
        assertEquals(0.0f, worldPos[1], 0.001f)
        assertEquals(5.0f, worldPos[2], 0.001f)
    }

    @Test
    fun coordinateMapping_accountsForOriginTranslation() {
        // Physical origin anchor placed at world position (2.5, -0.4, -3.0)
        val worldPos = ARCoordinateTransformer.computeWorldPositionPure(
            originX = 2.5f,
            originY = -0.4f,
            originZ = -3.0f,
            originYawDegrees = 0.0f,
            planX = 4.0f,
            planZ = 6.0f
        )

        assertEquals(6.5f, worldPos[0], 0.001f)
        assertEquals(-0.4f, worldPos[1], 0.001f)
        assertEquals(3.0f, worldPos[2], 0.001f)
    }

    @Test
    fun coordinateMapping_transformsWithOriginYawRotation() {
        // Origin rotated 90 degrees clockwise around vertical Y axis
        val worldPos = ARCoordinateTransformer.computeWorldPositionPure(
            originX = 0.0f,
            originY = 0.0f,
            originZ = 0.0f,
            originYawDegrees = 90.0f,
            planX = 10.0f,
            planZ = 0.0f
        )

        // At 90 deg yaw, lateral +X rotates into +Z depth axis
        assertEquals(0.0f, worldPos[0], 0.001f)
        assertEquals(0.0f, worldPos[1], 0.001f)
        assertEquals(10.0f, worldPos[2], 0.001f)
    }

    @Test
    fun rotationComposition_normalizesDegrees() {
        // Simple addition
        val r1 = ARCoordinateTransformer.computeWorldRotationDegrees(45.0f, 90.0f)
        assertEquals(135.0f, r1, 0.001f)

        // Wrap around 360
        val r2 = ARCoordinateTransformer.computeWorldRotationDegrees(350.0f, 20.0f)
        assertEquals(10.0f, r2, 0.001f)

        // Negative offset normalization
        val r3 = ARCoordinateTransformer.computeWorldRotationDegrees(30.0f, -45.0f)
        assertEquals(345.0f, r3, 0.001f)
    }

    @Test
    fun arCorePoseTransformation_computesExactTranslationAndPosition() {
        val originPose = Pose.makeTranslation(0.0f, 0.0f, 0.0f)

        val objectPose = ARCoordinateTransformer.transformPlanningToWorldPose(
            originPose = originPose,
            planXMeters = 10.0f,
            planZMeters = 5.0f,
            planRotationDegrees = 45.0f
        )

        val pos = objectPose.translation
        assertEquals(10.0f, pos[0], 0.001f)
        assertEquals(0.0f, pos[1], 0.001f)
        assertEquals(5.0f, pos[2], 0.001f)

        val point = ARCoordinateTransformer.transformPlanningToWorldPosition(
            originPose = originPose,
            planXMeters = -8.0f,
            planZMeters = 12.0f
        )
        assertEquals(-8.0f, point[0], 0.001f)
        assertEquals(0.0f, point[1], 0.001f)
        assertEquals(12.0f, point[2], 0.001f)
    }

    @Test
    fun savedLayout_remainsPristine_independentOfARSession() {
        // Verify that the domain Layout and Anchor remain clean and unpolluted by ephemeral AR session data
        val initialLayout = Layout(
            id = "test-plan-1",
            name = "Forward Base Delta",
            anchor = Anchor(x = 0.0f, z = 0.0f, label = "Origin Anchor"),
            objects = listOf(
                LayoutObject(
                    id = "obj-1",
                    assetType = AssetType.TENT,
                    name = "Command Post",
                    x = 10.0f,
                    z = 5.0f,
                    rotationDegrees = 90.0f
                )
            )
        )

        // Simulating calibration: we take the layout coordinates, compute world poses
        val originPose = Pose.makeTranslation(1.0f, -0.5f, -2.0f)
        val worldPoint = ARCoordinateTransformer.transformPlanningToWorldPosition(
            originPose = originPose,
            planXMeters = initialLayout.objects[0].x,
            planZMeters = initialLayout.objects[0].z
        )

        assertNotNull(worldPoint)

        // Layout itself is strictly unchanged
        assertEquals("test-plan-1", initialLayout.id)
        assertEquals("Forward Base Delta", initialLayout.name)
        assertEquals(0.0f, initialLayout.anchor.x, 0.001f)
        assertEquals(0.0f, initialLayout.anchor.z, 0.001f)
        assertEquals(1, initialLayout.objects.size)
        assertEquals(10.0f, initialLayout.objects[0].x, 0.001f)
        assertEquals(5.0f, initialLayout.objects[0].z, 0.001f)
    }
}
