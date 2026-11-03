package com.example.dhruvar.ui.ar.render

import com.example.dhruvar.domain.model.AssetType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Encoded image shared by every [MeshPart] that uses it; uploaded to the GPU on first draw. */
class TextureSource(@Volatile var imageData: ByteArray?) {
    var glId: Int = 0
}

/** One draw call: a chunk of geometry with a single color / texture. */
class MeshPart(
    val vertexBuffer: FloatBuffer,
    val normalBuffer: FloatBuffer,
    val uvBuffer: FloatBuffer?,
    val indexBuffer: ShortBuffer,
    val vertexCount: Int,
    val indexCount: Int,
    val color: FloatArray,
    val texture: TextureSource?
)

/**
 * A model as drawn in AR: ground-level pivot (Y = 0 at bottom center), real metric
 * dimensions matching its AssetSpecification, front facing +Z.
 */
class Mesh3D(
    val parts: List<MeshPart>,
    val heightMeters: Float,
    /** True while the real model is still loading (or failed to load) and a plain box is shown. */
    val isPlaceholder: Boolean
) {
    val vertexCount: Int get() = parts.sumOf { it.vertexCount }
    val indexCount: Int get() = parts.sumOf { it.indexCount }
}

fun interface ModelSource {
    /** Returns the bytes of the named model file, or null when it does not exist. */
    fun read(fileName: String): ByteArray?
}

/**
 * Central registry of 3D asset geometry for AR visualization.
 *
 * Real models are GLB files under `assets/models/`. They are parsed on a background
 * thread the first time an asset type is drawn; until then [getMesh] returns a
 * box of the asset's footprint so the AR frame never stalls on a large file.
 */
object AssetModelRegistry {

    private class ModelConfig(val fileName: String, val yawDegrees: Float)

    // The K-131 and STR-MGS models are modelled lengthwise along X; the planner puts
    // length on Z, so they are turned a quarter-turn on load.
    private val configs = mapOf(
        AssetType.GUNSHIP to ModelConfig("gunship.glb", 0f),
        AssetType.K131 to ModelConfig("k131.glb", 90f),
        AssetType.K131_TAKEOFF to ModelConfig("k131_takeoff.glb", 90f),
        AssetType.MILITARY_VEHICLE to ModelConfig("military_vehicle.glb", 0f),
        AssetType.OFFROAD_VEHICLE to ModelConfig("offroad_vehicle.glb", 0f),
        AssetType.STR_MGS to ModelConfig("str_mgs.glb", 0f)
    )

    @Volatile private var source: ModelSource? = null
    private val ready = ConcurrentHashMap<AssetType, Mesh3D>()
    private val failed = ConcurrentHashMap.newKeySet<AssetType>()
    private val loading = ConcurrentHashMap.newKeySet<AssetType>()
    private val placeholders = EnumMap<AssetType, Mesh3D>(AssetType::class.java)
    private val loader = Executors.newSingleThreadExecutor { r ->
        Thread(r, "asset-model-loader").apply { isDaemon = true }
    }

    fun install(modelSource: ModelSource) {
        source = modelSource
    }

    /** Drops loaded meshes. Their textures belong to a GL context that no longer exists. */
    fun reset() {
        ready.clear()
        failed.clear()
    }

    fun isReady(assetType: AssetType): Boolean = ready.containsKey(assetType)

    /**
     * Returns the mesh for [assetType]: the real model once loaded, otherwise a box
     * placeholder while the load runs in the background.
     */
    fun getMesh(assetType: AssetType): Mesh3D {
        ready[assetType]?.let { return it }
        requestLoad(assetType)
        return synchronized(placeholders) {
            placeholders.getOrPut(assetType) { createPlaceholder(assetType) }
        }
    }

    private fun requestLoad(assetType: AssetType) {
        if (source == null || failed.contains(assetType)) return
        if (!loading.add(assetType)) return
        loader.execute {
            try {
                loadBlocking(assetType)
            } finally {
                loading.remove(assetType)
            }
        }
    }

    /** Loads and caches the real model on the calling thread. Null if it cannot be loaded. */
    fun loadBlocking(assetType: AssetType): Mesh3D? {
        ready[assetType]?.let { return it }
        val config = configs.getValue(assetType)
        val spec = assetType.defaultSpecification
        return try {
            val bytes = source?.read(config.fileName) ?: throw GlbFormatException("Missing ${config.fileName}")
            val model = GlbParser.parse(bytes, spec.widthMeters, spec.lengthMeters, spec.heightMeters, config.yawDegrees)
            toMesh(model, spec.heightMeters).also { ready[assetType] = it }
        } catch (e: Exception) {
            failed.add(assetType)
            null
        } catch (e: OutOfMemoryError) {
            failed.add(assetType)
            null
        }
    }

    private fun toMesh(model: GlbModel, heightMeters: Float): Mesh3D {
        val textures = HashMap<Int, TextureSource>()
        val parts = model.parts.map { part ->
            val texture = if (part.imageIndex >= 0) {
                textures.getOrPut(part.imageIndex) { TextureSource(model.images[part.imageIndex]) }
            } else null
            MeshPart(
                vertexBuffer = floatBuffer(part.positions),
                normalBuffer = floatBuffer(part.normals),
                uvBuffer = part.uvs?.let { floatBuffer(it) },
                indexBuffer = shortBuffer(part.indices),
                vertexCount = part.positions.size / 3,
                indexCount = part.indices.size,
                color = part.baseColor,
                texture = texture
            )
        }
        return Mesh3D(parts, heightMeters, isPlaceholder = false)
    }

    /** A flat-shaded box of the asset's footprint, standing on the ground. */
    private fun createPlaceholder(assetType: AssetType): Mesh3D {
        val spec = assetType.defaultSpecification
        val hw = spec.widthMeters / 2f
        val hl = spec.lengthMeters / 2f
        val h = spec.heightMeters

        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val indices = ArrayList<Short>()
        fun face(nx: Float, ny: Float, nz: Float, vararg corners: Float) {
            val base = positions.size / 3
            for (i in corners.indices step 3) {
                positions.add(corners[i]); positions.add(corners[i + 1]); positions.add(corners[i + 2])
                normals.add(nx); normals.add(ny); normals.add(nz)
            }
            indices.addAll(listOf(base, base + 1, base + 2, base, base + 2, base + 3).map { it.toShort() })
        }
        face(0f, 1f, 0f, -hw, h, -hl, -hw, h, hl, hw, h, hl, hw, h, -hl)
        face(0f, 0f, 1f, -hw, 0f, hl, hw, 0f, hl, hw, h, hl, -hw, h, hl)
        face(0f, 0f, -1f, hw, 0f, -hl, -hw, 0f, -hl, -hw, h, -hl, hw, h, -hl)
        face(1f, 0f, 0f, hw, 0f, hl, hw, 0f, -hl, hw, h, -hl, hw, h, hl)
        face(-1f, 0f, 0f, -hw, 0f, -hl, -hw, 0f, hl, -hw, h, hl, -hw, h, -hl)

        val part = MeshPart(
            vertexBuffer = floatBuffer(positions.toFloatArray()),
            normalBuffer = floatBuffer(normals.toFloatArray()),
            uvBuffer = null,
            indexBuffer = shortBuffer(indices.toShortArray()),
            vertexCount = positions.size / 3,
            indexCount = indices.size,
            color = floatArrayOf(0.35f, 0.40f, 0.30f, 1.0f),
            texture = null
        )
        return Mesh3D(listOf(part), h, isPlaceholder = true)
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(values); position(0) }

    private fun shortBuffer(values: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().apply { put(values); position(0) }
}
