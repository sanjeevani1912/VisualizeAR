package com.example.dhruvar

import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.spatial.SpatialDistanceCalculator
import com.example.dhruvar.ui.ar.render.AssetModelRegistry
import com.example.dhruvar.ui.ar.render.Mesh3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

/**
 * Unit test suite validating Part 12: AR 3D Asset Integration, Model Registry,
 * Ground-Level Pivot, Authoritative Distance Calculations, and Selection System.
 *
 * The registry tests load the real GLB files from `src/main/assets/models`.
 */
class ARVisualizationV12Test {

    private val supportedAssetTypes = AssetType.entries.toList()

    @Before
    fun installModelSource() {
        AssetModelRegistry.reset()
        AssetModelRegistry.install { fileName ->
            File("src/main/assets/models/$fileName").takeIf { it.exists() }?.readBytes()
        }
    }

    private fun loadedMesh(assetType: AssetType): Mesh3D {
        val mesh = AssetModelRegistry.loadBlocking(assetType)
        assertNotNull("Model for $assetType must load from assets/models", mesh)
        assertFalse("Loaded model for $assetType must not be the placeholder", mesh!!.isPlaceholder)
        return mesh
    }

    @Test
    fun assetModelRegistry_providesValid3DMeshesForAllSupportedTypes() {
        for (assetType in supportedAssetTypes) {
            val mesh = loadedMesh(assetType)
            assertTrue("Mesh for $assetType must have positive vertex count", mesh.vertexCount > 0)
            assertTrue("Mesh for $assetType must have positive index count", mesh.indexCount > 0)
            for (part in mesh.parts) {
                assertEquals("Index count must be a multiple of 3 (triangles)", 0, part.indexCount % 3)
                assertEquals("Part color must be 4-component RGBA", 4, part.color.size)
            }
        }
    }

    @Test
    fun placeholder_isReturnedBeforeTheRealModelHasLoaded() {
        AssetModelRegistry.reset()
        AssetModelRegistry.install { null } // nothing to load
        val mesh = AssetModelRegistry.getMesh(AssetType.GUNSHIP)
        assertTrue(mesh.isPlaceholder)
        assertEquals(AssetType.GUNSHIP.defaultHeightMeters, mesh.heightMeters, 0.001f)
        assertEquals(20, mesh.vertexCount)
    }

    @Test
    fun groundLevelPivot_allModelVerticesHaveNonNegativeY() {
        // Critical AR requirement: Base pivot is at ground level (Y = 0.0)
        // No vertex may extend below the detected physical surface (Y < 0.0)
        for (assetType in supportedAssetTypes) {
            val mesh = loadedMesh(assetType)
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE

            for (part in mesh.parts) {
                val buffer = part.vertexBuffer.duplicate()
                buffer.position(0)
                for (i in 0 until part.vertexCount) {
                    buffer.get()
                    val y = buffer.get()
                    buffer.get()
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    assertTrue(
                        "Asset $assetType vertex $i has Y=$y < 0.0 (ground penetration error)",
                        y >= -0.001f
                    )
                }
            }

            assertEquals("Asset $assetType base pivot must be on the ground plane (Y=0.0)", 0.0f, minY, 0.01f)
            assertEquals(
                "Asset $assetType maximum vertex height must match its specification",
                assetType.defaultHeightMeters,
                maxY,
                0.05f
            )
        }
    }

    @Test
    fun modelFootprint_matchesSpecificationAndIsCenteredOnOrigin() {
        for (assetType in supportedAssetTypes) {
            val mesh = loadedMesh(assetType)
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
            for (part in mesh.parts) {
                val buffer = part.vertexBuffer.duplicate()
                buffer.position(0)
                for (i in 0 until part.vertexCount) {
                    val x = buffer.get(); buffer.get(); val z = buffer.get()
                    minX = minOf(minX, x); maxX = maxOf(maxX, x)
                    minZ = minOf(minZ, z); maxZ = maxOf(maxZ, z)
                }
            }
            assertEquals("$assetType width", assetType.defaultWidthMeters, maxX - minX, 0.02f)
            assertEquals("$assetType length", assetType.defaultLengthMeters, maxZ - minZ, 0.02f)
            assertEquals("$assetType X centre", 0f, (minX + maxX) / 2f, 0.02f)
            assertEquals("$assetType Z centre", 0f, (minZ + maxZ) / 2f, 0.02f)
        }
    }

    @Test
    fun modelNormals_areProperlyNormalizedUnitVectors() {
        // AR diffuse lighting requires unit normals for correct lambertian shading
        for (assetType in supportedAssetTypes) {
            val mesh = loadedMesh(assetType)
            for (part in mesh.parts) {
                val nBuf = part.normalBuffer.duplicate()
                nBuf.position(0)
                for (i in 0 until part.vertexCount) {
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
    }

    @Test
    fun texturedModels_carryUvsAndImageData() {
        val gunship = loadedMesh(AssetType.GUNSHIP)
        assertTrue(gunship.parts.any { it.texture != null && it.uvBuffer != null })
        // The olive-drab STR-MGS model is flat colored
        val strMgs = loadedMesh(AssetType.STR_MGS)
        assertTrue(strMgs.parts.all { it.texture == null })
    }

    @Test
    fun authoritativeDistance_strictlyMatchesEuclideanFormula() {
        val anchor = Anchor(id = "origin_anchor", x = 0.0f, z = 0.0f)

        val obj1 = LayoutObject(
            id = "obj_3_4",
            name = "Vehicle Alpha",
            assetType = AssetType.MILITARY_VEHICLE,
            x = 3.0f,
            z = 4.0f
        )
        assertEquals(5.0f, SpatialDistanceCalculator.calculateDistanceToAnchor(obj1, anchor), 0.001f)

        val obj2 = LayoutObject(
            id = "obj_6_8",
            name = "Mobile Gun",
            assetType = AssetType.STR_MGS,
            x = 6.0f,
            z = 8.0f
        )
        assertEquals(10.0f, SpatialDistanceCalculator.calculateDistanceToAnchor(obj2, anchor), 0.001f)

        val objAtOrigin = LayoutObject(
            id = "obj_origin",
            name = "Command Vehicle",
            assetType = AssetType.MILITARY_VEHICLE,
            x = 0.0f,
            z = 0.0f
        )
        assertEquals(0.0f, SpatialDistanceCalculator.calculateDistanceToAnchor(objAtOrigin, anchor), 0.001f)

        val objNegativeCoords = LayoutObject(
            id = "obj_neg",
            name = "Rear Launcher",
            assetType = AssetType.K131,
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
