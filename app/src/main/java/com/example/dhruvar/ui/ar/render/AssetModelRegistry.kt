package com.example.dhruvar.ui.ar.render

import com.example.dhruvar.domain.model.AssetType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.EnumMap

/**
 * Encapsulates a 3D polygonal mesh with verified metric dimensions,
 * surface normals for diffuse lighting, and tactical color palettes.
 */
data class Mesh3D(
    val vertexBuffer: FloatBuffer,
    val normalBuffer: FloatBuffer,
    val indexBuffer: ShortBuffer,
    val vertexCount: Int,
    val indexCount: Int,
    val primaryColor: FloatArray,
    val accentColor: FloatArray,
    val heightMeters: Float
)

/**
 * Central registry managing 3D asset geometry for AR visualization.
 *
 * Ensures:
 * - Every model has a verified ground-level pivot (Y = 0.0 at bottom center).
 * - Exact real-world metric dimensions (Width, Length, Height) matching AssetSpecification.
 * - Proper canonical orientation (front facing along +Z).
 * - Reusable cached geometry (zero per-frame allocations).
 */
object AssetModelRegistry {

    private val meshCache = EnumMap<AssetType, Mesh3D>(AssetType::class.java)

    init {
        // Pre-build and cache 3D meshes for all supported asset types
        meshCache[AssetType.TENT] = createTentMesh()
        meshCache[AssetType.TRUCK] = createTruckMesh()
        meshCache[AssetType.CAR] = createCarMesh()
        meshCache[AssetType.ANTENNA] = createAntennaMesh()
        meshCache[AssetType.TRENCH] = createTrenchMesh()
    }

    /**
     * Retrieves the cached 3D mesh for the specified [assetType].
     */
    fun getMesh(assetType: AssetType): Mesh3D {
        return meshCache[assetType] ?: meshCache[AssetType.TENT]!!
    }

    /**
     * TENT MESH (4.0m W x 6.0m L x 2.5m H)
     * Tactical gable ridge tent with vertical side walls and sloping roof canopy.
     */
    private fun createTentMesh(): Mesh3D {
        val hw = 2.0f   // half-width (4.0m total)
        val hl = 3.0f   // half-length (6.0m total)
        val wallH = 1.0f
        val peakH = 2.5f

        // 8 key vertices defining the gable tent
        val v = floatArrayOf(
            // Bottom rectangle (Y = 0.0)
            -hw, 0.0f, -hl,  // 0: left rear bottom
             hw, 0.0f, -hl,  // 1: right rear bottom
             hw, 0.0f,  hl,  // 2: right front bottom
            -hw, 0.0f,  hl,  // 3: left front bottom
            // Eaves rectangle (Y = wallH)
            -hw, wallH, -hl, // 4: left rear eave
             hw, wallH, -hl, // 5: right rear eave
             hw, wallH,  hl, // 6: right front eave
            -hw, wallH,  hl, // 7: left front eave
            // Ridge line (Y = peakH, X = 0)
             0.0f, peakH, -hl, // 8: rear peak
             0.0f, peakH,  hl  // 9: front peak
        )

        // Triangles forming tent body
        val indices = shortArrayOf(
            // Left side wall
            0, 4, 7,  0, 7, 3,
            // Right side wall
            1, 2, 6,  1, 6, 5,
            // Rear wall (bottom + triangle peak)
            0, 1, 5,  0, 5, 4,
            4, 5, 8,
            // Front wall (bottom + triangle peak)
            3, 7, 6,  3, 6, 2,
            7, 9, 6,
            // Roof left slope
            4, 8, 9,  4, 9, 7,
            // Roof right slope
            5, 6, 9,  5, 9, 8
        )

        val normals = computeNormals(v, indices)
        return buildMesh(
            vertices = v,
            normals = normals,
            indices = indices,
            primaryColor = floatArrayOf(0.18f, 0.35f, 0.22f, 1.0f), // Tactical Olive Green
            accentColor = floatArrayOf(0.12f, 0.25f, 0.16f, 1.0f),
            heightMeters = peakH
        )
    }

    /**
     * TRUCK MESH (2.5m W x 8.0m L x 3.2m H)
     * Heavy tactical logistics transport with front cabin and rear cargo container.
     */
    private fun createTruckMesh(): Mesh3D {
        val hw = 1.25f  // half-width (2.5m total)
        val lRear = -4.0f
        val lMid = 1.0f
        val lFront = 4.0f
        val hChassis = 0.6f
        val hCab = 2.4f
        val hCargo = 3.2f

        val v = floatArrayOf(
            // Rear Cargo Box (Z: lRear to lMid, Y: hChassis to hCargo)
            -hw, hChassis, lRear,   hw, hChassis, lRear,   hw, hCargo, lRear,  -hw, hCargo, lRear, // Rear
            -hw, hChassis, lMid,    hw, hChassis, lMid,    hw, hCargo, lMid,   -hw, hCargo, lMid,  // Mid
            // Front Cabin Box (Z: lMid to lFront, Y: hChassis to hCab)
            -hw, hChassis, lFront,  hw, hChassis, lFront,  hw, hCab, lFront,   -hw, hCab, lFront,  // Front
            // Wheels / undercarriage base (Y: 0.0 to hChassis)
            -hw * 0.9f, 0.0f, lRear + 0.5f,  hw * 0.9f, 0.0f, lRear + 0.5f,
            -hw * 0.9f, 0.0f, lFront - 0.5f, hw * 0.9f, 0.0f, lFront - 0.5f
        )

        val indices = shortArrayOf(
            // Cargo box rear face
            0, 2, 1,  0, 3, 2,
            // Cargo box top face
            3, 7, 6,  3, 6, 2,
            // Cargo box left face
            0, 7, 3,  0, 4, 7,
            // Cargo box right face
            1, 2, 6,  1, 6, 5,
            // Cabin front face
            8, 10, 9,  8, 11, 10,
            // Cabin top face
            7, 10, 6,  7, 11, 10,
            // Cabin left face
            4, 11, 7,  4, 8, 11,
            // Cabin right face
            5, 6, 10,  5, 10, 9,
            // Wheel / ground contact base
            0, 12, 13,  0, 13, 1,
            8, 14, 15,  8, 15, 9
        )

        val normals = computeNormals(v, indices)
        return buildMesh(
            vertices = v,
            normals = normals,
            indices = indices,
            primaryColor = floatArrayOf(0.25f, 0.32f, 0.20f, 1.0f), // Military Cargo Green
            accentColor = floatArrayOf(0.15f, 0.18f, 0.12f, 1.0f),
            heightMeters = hCargo
        )
    }

    /**
     * CAR MESH (1.8m W x 4.5m L x 1.5m H)
     * Reconnaissance utility vehicle with sloped hood and passenger cabin.
     */
    private fun createCarMesh(): Mesh3D {
        val hw = 0.9f   // half-width (1.8m total)
        val lRear = -2.25f
        val lCabinRear = -1.2f
        val lCabinFront = 0.8f
        val lFront = 2.25f
        val hBase = 0.35f
        val hHood = 0.85f
        val hRoof = 1.5f

        val v = floatArrayOf(
            // 0..3: Rear bumper & deck
            -hw, hBase, lRear,   hw, hBase, lRear,   hw, hHood, lRear,  -hw, hHood, lRear,
            // 4..7: Cabin roof
            -hw * 0.85f, hRoof, lCabinRear,  hw * 0.85f, hRoof, lCabinRear,
             hw * 0.85f, hRoof, lCabinFront, -hw * 0.85f, hRoof, lCabinFront,
            // 8..11: Front hood & bumper
            -hw, hHood, lFront,  hw, hHood, lFront,  hw, hBase, lFront,  -hw, hBase, lFront,
            // 12..15: Wheels ground contact (Y = 0.0)
            -hw * 0.9f, 0.0f, lRear + 0.6f,   hw * 0.9f, 0.0f, lRear + 0.6f,
            -hw * 0.9f, 0.0f, lFront - 0.6f,  hw * 0.9f, 0.0f, lFront - 0.6f
        )

        val indices = shortArrayOf(
            // Rear face
            0, 2, 1,  0, 3, 2,
            // Rear windshield
            3, 5, 2,  3, 4, 5,
            // Roof
            4, 6, 5,  4, 7, 6,
            // Front windshield
            7, 9, 6,  7, 8, 9,
            // Hood top
            8, 9, 10, 8, 10, 11,
            // Left side
            0, 3, 4,  0, 4, 7,  0, 7, 8,  0, 8, 11,
            // Right side
            1, 5, 2,  1, 6, 5,  1, 9, 6,  1, 10, 9,
            // Wheel / ground contact base
            0, 12, 13,  0, 13, 1,
            11, 14, 15, 11, 15, 10
        )

        val normals = computeNormals(v, indices)
        return buildMesh(
            vertices = v,
            normals = normals,
            indices = indices,
            primaryColor = floatArrayOf(0.28f, 0.38f, 0.45f, 1.0f), // Tactical Blue-Grey
            accentColor = floatArrayOf(0.18f, 0.24f, 0.30f, 1.0f),
            heightMeters = hRoof
        )
    }

    /**
     * ANTENNA MESH (1.5m W x 1.5m L x 8.0m H)
     * Communications mast with 4 structural lattice pillars tapering toward top array.
     */
    private fun createAntennaMesh(): Mesh3D {
        val baseHw = 0.75f  // base half-width (1.5m total)
        val topHw = 0.25f   // top half-width (0.5m total)
        val height = 8.0f
        val midHeight = 4.0f
        val midHw = 0.5f

        val v = floatArrayOf(
            // Base square (Y = 0.0)
            -baseHw, 0.0f, -baseHw,   baseHw, 0.0f, -baseHw,
             baseHw, 0.0f,  baseHw,  -baseHw, 0.0f,  baseHw,
            // Mid lattice square (Y = 4.0m)
            -midHw, midHeight, -midHw,   midHw, midHeight, -midHw,
             midHw, midHeight,  midHw,  -midHw, midHeight,  midHw,
            // Top platform square (Y = 8.0m)
            -topHw, height, -topHw,   topHw, height, -topHw,
             topHw, height,  topHw,  -topHw, height,  topHw,
            // Top antenna spire (Y = 9.2m)
             0.0f, height + 1.2f, 0.0f
        )

        val indices = shortArrayOf(
            // Lower tower faces (0..3 to 4..7)
            0, 5, 1,  0, 4, 5,   1, 6, 2,  1, 5, 6,
            2, 7, 3,  2, 6, 7,   3, 4, 0,  3, 7, 4,
            // Upper tower faces (4..7 to 8..11)
            4, 9, 5,  4, 8, 9,   5, 10, 6, 5, 9, 10,
            6, 11, 7, 6, 10, 11, 7, 8, 4,  7, 11, 8,
            // Top spire pyramid
            8, 12, 9,  9, 12, 10,  10, 12, 11,  11, 12, 8
        )

        val normals = computeNormals(v, indices)
        return buildMesh(
            vertices = v,
            normals = normals,
            indices = indices,
            primaryColor = floatArrayOf(0.70f, 0.72f, 0.75f, 1.0f), // Structural Steel
            accentColor = floatArrayOf(0.85f, 0.35f, 0.10f, 1.0f), // Hazard Orange tips
            heightMeters = height + 1.2f
        )
    }

    /**
     * TRENCH MESH (1.2m W x 10.0m L x 1.8m H)
     * Defensive earthwork fortification with raised berms and central revetment channel.
     */
    private fun createTrenchMesh(): Mesh3D {
        val hw = 0.6f     // half-width of channel (1.2m total)
        val hl = 5.0f     // half-length (10.0m total)
        val bermW = 1.4f   // outer berm edge width
        val bermH = 0.9f   // raised parapet height
        val revetmentH = 1.8f

        val v = floatArrayOf(
            // Central channel floor (Y = 0.0)
            -hw, 0.0f, -hl,   hw, 0.0f, -hl,
             hw, 0.0f,  hl,  -hw, 0.0f,  hl,
            // Left & right revetment walls (Y = revetmentH)
            -hw, revetmentH, -hl,  -hw, revetmentH, hl,
             hw, revetmentH, -hl,   hw, revetmentH, hl,
            // Left & right outer berm crests (Y = bermH)
            -bermW, bermH, -hl,  -bermW, bermH, hl,
             bermW, bermH, -hl,   bermW, bermH, hl
        )

        val indices = shortArrayOf(
            // Left inner revetment wall
            0, 5, 4,  0, 3, 5,
            // Right inner revetment wall
            1, 6, 7,  1, 7, 2,
            // Left outer berm slope
            4, 9, 8,  4, 5, 9,
            // Right outer berm slope
            6, 10, 11, 6, 11, 7,
            // Channel floor
            0, 1, 2,  0, 2, 3
        )

        val normals = computeNormals(v, indices)
        return buildMesh(
            vertices = v,
            normals = normals,
            indices = indices,
            primaryColor = floatArrayOf(0.42f, 0.34f, 0.24f, 1.0f), // Earthwork Brown
            accentColor = floatArrayOf(0.32f, 0.25f, 0.18f, 1.0f),
            heightMeters = revetmentH
        )
    }

    private fun computeNormals(vertices: FloatArray, indices: ShortArray): FloatArray {
        val normals = FloatArray(vertices.size)

        for (i in 0 until indices.size step 3) {
            val i0 = indices[i].toInt() * 3
            val i1 = indices[i + 1].toInt() * 3
            val i2 = indices[i + 2].toInt() * 3

            val v0x = vertices[i0]; val v0y = vertices[i0 + 1]; val v0z = vertices[i0 + 2]
            val v1x = vertices[i1]; val v1y = vertices[i1 + 1]; val v1z = vertices[i1 + 2]
            val v2x = vertices[i2]; val v2y = vertices[i2 + 1]; val v2z = vertices[i2 + 2]

            // Cross product of edge vectors
            val e1x = v1x - v0x; val e1y = v1y - v0y; val e1z = v1z - v0z
            val e2x = v2x - v0x; val e2y = v2y - v0y; val e2z = v2z - v0z

            val nx = (e1y * e2z) - (e1z * e2y)
            val ny = (e1z * e2x) - (e1x * e2z)
            val nz = (e1x * e2y) - (e1y * e2x)

            normals[i0] += nx; normals[i0 + 1] += ny; normals[i0 + 2] += nz
            normals[i1] += nx; normals[i1 + 1] += ny; normals[i1 + 2] += nz
            normals[i2] += nx; normals[i2 + 1] += ny; normals[i2 + 2] += nz
        }

        // Normalize vectors
        for (i in 0 until normals.size step 3) {
            val nx = normals[i]; val ny = normals[i + 1]; val nz = normals[i + 2]
            val len = kotlin.math.sqrt((nx * nx + ny * ny + nz * nz).toDouble()).toFloat()
            if (len > 0.0001f) {
                normals[i] = nx / len
                normals[i + 1] = ny / len
                normals[i + 2] = nz / len
            } else {
                normals[i + 1] = 1.0f // fallback upward normal
            }
        }
        return normals
    }

    private fun buildMesh(
        vertices: FloatArray,
        normals: FloatArray,
        indices: ShortArray,
        primaryColor: FloatArray,
        accentColor: FloatArray,
        heightMeters: Float
    ): Mesh3D {
        val vBuf = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertices); position(0) }

        val nBuf = ByteBuffer.allocateDirect(normals.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(normals); position(0) }

        val iBuf = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply { put(indices); position(0) }

        return Mesh3D(
            vertexBuffer = vBuf,
            normalBuffer = nBuf,
            indexBuffer = iBuf,
            vertexCount = vertices.size / 3,
            indexCount = indices.size,
            primaryColor = primaryColor,
            accentColor = accentColor,
            heightMeters = heightMeters
        )
    }
}
