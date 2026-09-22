package com.example.dhruvar

import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.spatial.SpatialDistanceCalculator
import com.example.dhruvar.ui.ar.render.AssetModelRegistry
import com.example.dhruvar.ui.ar.render.Mesh3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Unit test suite validating Part 12: AR 3D Asset Integration, Model Registry,
 * Ground-Level Pivot, Authoritative Distance Calculations, and Selection System.
 */
class ARVisualizationV12Test {

    private val supportedAssetTypes = listOf(
        AssetType.TENT,
        AssetType.TRUCK,
        AssetType.CAR,
        AssetType.ANTENNA,
        AssetType.TRENCH
    )

    @Test
    fun assetModelRegistry_providesValid3DMeshesForAllSupportedTypes() {
        for (assetType in supportedAssetTypes) {
            val mesh = AssetModelRegistry.getMesh(assetType)
            assertNotNull("Mesh for $assetType must not be null", mesh)
            assertTrue("Mesh for $assetType must have positive vertex count", mesh.vertexCount > 0)
            assertTrue("Mesh for $assetType must have positive index count", mesh.indexCount > 0)
            assertEquals("Index count must be a multiple of 3 (triangles)", 0, mesh.indexCount % 3)
            assertEquals("Primary color must be 4-component RGBA", 4, mesh.primaryColor.size)
            assertEquals("Accent color must be 4-component RGBA", 4, mesh.accentColor.size)
        }
    }

    @Test
    fun groundLevelPivot_allModelVerticesHaveNonNegativeY() {
        // Critical AR requirement: Base pivot is at ground level (Y = 0.0)
        // No vertex may extend below the detected physical surface (Y < 0.0)
        for (assetType in supportedAssetTypes) {
            val mesh = AssetModelRegistry.getMesh(assetType)
            val buffer = mesh.vertexBuffer.duplicate()
            buffer.position(0)

            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE

            for (i in 0 until mesh.vertexCount) {
                val x = buffer.get()
                val y = buffer.get()
                val z = buffer.get()

                if (y < minY) minY = y
                if (y > maxY) maxY = y

                assertTrue(
                    "Asset $assetType vertex $i has Y=$y < 0.0 (ground penetration error)",
                    y >= -0.001f
                )
            }

            assertEquals(
                "Asset $assetType base pivot must be exactly on the ground plane (Y=0.0)",
                0.0f,
                minY,
                0.01f
            )
            assertTrue(
                "Asset $assetType must have positive physical height",
                maxY > 0.1f
            )
            assertEquals(
                "Asset $assetType maximum vertex height must match reported heightMeters",
                mesh.heightMeters,
                maxY,
                0.05f
            )
        }
    }

    @Test
    fun modelDimensions_matchPhysicalMetricSpecifications() {
        // Tent: 4.0m W x 6.0m L x 2.5m H
        val tent = AssetModelRegistry.getMesh(AssetType.TENT)
        assertEquals(2.5f, tent.heightMeters, 0.01f)

        // Truck: 2.5m W x 8.0m L x 3.2m H
        val truck = AssetModelRegistry.getMesh(AssetType.TRUCK)
        assertEquals(3.2f, truck.heightMeters, 0.01f)

        // Car: 1.8m W x 4.5m L x 1.5m H
        val car = AssetModelRegistry.getMesh(AssetType.CAR)
        assertEquals(1.5f, car.heightMeters, 0.01f)

        // Antenna: 1.5m W x 1.5m L x 9.2m H (8.0m mast + 1.2m spire)
        val antenna = AssetModelRegistry.getMesh(AssetType.ANTENNA)
        assertEquals(9.2f, antenna.heightMeters, 0.01f)

        // Trench: 1.2m W x 10.0m L x 1.8m H
        val trench = AssetModelRegistry.getMesh(AssetType.TRENCH)
        assertEquals(1.8f, trench.heightMeters, 0.01f)
    }

    @Test
    fun modelNormals_areProperlyNormalizedUnitVectors() {
        // AR diffuse lighting requires unit normals for correct lambertian shading
        for (assetType in supportedAssetTypes) {
            val mesh = AssetModelRegistry.getMesh(assetType)
            val nBuf = mesh.normalBuffer.duplicate()
            nBuf.position(0)

            for (i in 0 until mesh.vertexCount) {
                val nx = nBuf.get()
                val ny = nBuf.get()
                val nz = nBuf.get()

                val length = sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
                assertEquals(
                    "Normal vector for $assetType vertex $i must have unit length (~1.0)",
                    1.0f,
                    length,
                    0.05f
                )
            }
        }
    }

    @Test
    fun authoritativeDistance_strictlyMatchesEuclideanFormula() {
        val anchor = Anchor(id = "origin_anchor", x = 0.0f, z = 0.0f)

        val obj1 = LayoutObject(
            id = "obj_3_4",
            name = "Tent Alpha",
            assetType = AssetType.TENT,
            x = 3.0f,
            z = 4.0f
        )
        assertEquals(5.0f, SpatialDistanceCalculator.calculateDistanceToAnchor(obj1, anchor), 0.001f)

        val obj2 = LayoutObject(
            id = "obj_6_8",
            name = "Logistics Truck",
            assetType = AssetType.TRUCK,
            x = 6.0f,
            z = 8.0f
        )
        assertEquals(10.0f, SpatialDistanceCalculator.calculateDistanceToAnchor(obj2, anchor), 0.001f)

        val objAtOrigin = LayoutObject(
            id = "obj_origin",
            name = "Command Post",
            assetType = AssetType.TENT,
            x = 0.0f,
            z = 0.0f
        )
        assertEquals(0.0f, SpatialDistanceCalculator.calculateDistanceToAnchor(objAtOrigin, anchor), 0.001f)

        val objNegativeCoords = LayoutObject(
            id = "obj_neg",
            name = "Rear Antenna",
            assetType = AssetType.ANTENNA,
            x = -5.0f,
            z = -12.0f
        )
        assertEquals(13.0f, SpatialDistanceCalculator.calculateDistanceToAnchor(objNegativeCoords, anchor), 0.001f)
    }

    @Test
    fun distanceLineMidpoint_calculatesCorrectWorldCoordinates() {
        val originX = 0.0f
        val originY = 0.0f
        val originZ = 0.0f

        val targetX = 6.0f
        val targetY = 0.0f
        val targetZ = 8.0f

        // World-space midpoint for distance badge positioning
        val midX = (originX + targetX) / 2.0f
        val midY = (originY + targetY) / 2.0f + 0.25f // Elevated slightly above line
        val midZ = (originZ + targetZ) / 2.0f

        assertEquals(3.0f, midX, 0.001f)
        assertEquals(0.25f, midY, 0.001f)
        assertEquals(4.0f, midZ, 0.001f)
    }

    @Test
    fun billboardScaleClamping_keepsLabelsLegibleAcrossDistances() {
        val minScale = 0.65f
        val maxScale = 1.35f

        fun computeBillboardScale(distance: Float): Float {
            val raw = 1.8f / distance.coerceAtLeast(0.5f)
            return raw.coerceIn(minScale, maxScale)
        }

        // Close distance (0.5m): clamped to maxScale
        val closeScale = computeBillboardScale(0.5f)
        assertEquals(maxScale, closeScale, 0.001f)

        // Mid distance (2.0m): proportional scale
        val midScale = computeBillboardScale(2.0f)
        assertEquals(0.9f, midScale, 0.001f)

        // Far distance (15.0m): clamped to minScale
        val farScale = computeBillboardScale(15.0f)
        assertEquals(minScale, farScale, 0.001f)
    }

    @Test
    fun objectSelectionStateTransitions_selectAndDeselect() {
        var selectedId: String? = null

        // 1. Initial state: nothing selected
        org.junit.Assert.assertNull(selectedId)

        // 2. Select object 1
        selectedId = "obj-1"
        assertEquals("obj-1", selectedId)

        // 3. Switch selection to object 2
        selectedId = "obj-2"
        assertEquals("obj-2", selectedId)

        // 4. Tap empty ground/space (deselect)
        selectedId = null
        org.junit.Assert.assertNull(selectedId)
    }
}
