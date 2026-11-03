package com.example.dhruvar.ui.ar.render

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GlbFormatException(message: String) : Exception(message)

/** One drawable piece of a model: baked world-space geometry plus its material. */
class GlbPart(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray?,
    val indices: ShortArray,
    val baseColor: FloatArray,
    /** Index into [GlbModel.images], or -1 when the material has no color texture. */
    val imageIndex: Int
)

class GlbModel(
    val parts: List<GlbPart>,
    /** Encoded (PNG / JPEG) image bytes by glTF image index. */
    val images: Map<Int, ByteArray>
) {
    val vertexCount: Int get() = parts.sumOf { it.positions.size / 3 }
    val triangleCount: Int get() = parts.sumOf { it.indices.size / 3 }
}

/**
 * Minimal binary glTF (GLB 2.0) reader for static, triangle-based models.
 *
 * Node transforms are baked into the vertices, then the whole model is rotated about
 * the vertical axis by `yawDegrees`, centered on X/Z, stood on the ground (min Y = 0)
 * and scaled so its bounding box is exactly the requested width (X), length (Z) and
 * height (Y) in meters. That matches the planner's authoritative asset footprint.
 *
 * Not supported: animation, skinning, sparse accessors, external buffers, Draco.
 */
object GlbParser {

    private const val MAGIC = 0x46546C67
    private const val CHUNK_JSON = 0x4E4F534A
    private const val CHUNK_BIN = 0x004E4942
    private const val MODE_TRIANGLES = 4
    private const val MAX_NODE_DEPTH = 64

    fun parse(
        data: ByteArray,
        targetWidthMeters: Float,
        targetLengthMeters: Float,
        targetHeightMeters: Float,
        yawDegrees: Float = 0f
    ): GlbModel {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        if (data.size < 20 || buf.getInt(0) != MAGIC) throw GlbFormatException("Not a GLB file")

        var jsonText: String? = null
        var binOffset = -1
        var pos = 12
        while (pos + 8 <= data.size) {
            val length = buf.getInt(pos)
            val type = buf.getInt(pos + 4)
            val start = pos + 8
            if (length < 0 || start + length > data.size) throw GlbFormatException("Truncated GLB chunk")
            when (type) {
                CHUNK_JSON -> jsonText = String(data, start, length, Charsets.UTF_8)
                CHUNK_BIN -> if (binOffset < 0) binOffset = start
            }
            pos = start + length
        }
        val root = try {
            JSONObject(jsonText ?: throw GlbFormatException("GLB has no JSON chunk"))
        } catch (e: org.json.JSONException) {
            throw GlbFormatException("Invalid GLB JSON: ${e.message}")
        }
        if (binOffset < 0) throw GlbFormatException("GLB has no binary chunk")

        val reader = AccessorReader(root, buf, binOffset)
        val rawParts = ArrayList<GlbPart>()
        val scene = root.optJSONArray("scenes")?.optJSONObject(root.optInt("scene", 0))
        val rootNodes = scene?.optJSONArray("nodes")
            ?: JSONArray().also { arr -> for (i in 0 until (root.optJSONArray("nodes")?.length() ?: 0)) arr.put(i) }

        val yaw = Math.toRadians(yawDegrees.toDouble()).toFloat()
        val rootMatrix = rotationY(yaw)
        for (i in 0 until rootNodes.length()) {
            collectNode(root, reader, rootNodes.getInt(i), rootMatrix, rawParts, 0)
        }
        if (rawParts.isEmpty()) throw GlbFormatException("GLB contains no triangle geometry")

        normalizeToTarget(rawParts, targetWidthMeters, targetLengthMeters, targetHeightMeters)

        val images = HashMap<Int, ByteArray>()
        for (part in rawParts) {
            if (part.imageIndex >= 0 && part.imageIndex !in images) {
                imageBytes(root, data, binOffset, part.imageIndex)?.let { images[part.imageIndex] = it }
            }
        }
        // A part whose image could not be read falls back to its plain color.
        val parts = rawParts.map { part ->
            if (part.imageIndex >= 0 && part.imageIndex !in images) {
                GlbPart(part.positions, part.normals, null, part.indices, part.baseColor, -1)
            } else part
        }
        return GlbModel(parts, images)
    }

    private fun collectNode(
        root: JSONObject,
        reader: AccessorReader,
        nodeIndex: Int,
        parent: FloatArray,
        out: MutableList<GlbPart>,
        depth: Int
    ) {
        if (depth > MAX_NODE_DEPTH) throw GlbFormatException("Node hierarchy too deep")
        val node = root.getJSONArray("nodes").getJSONObject(nodeIndex)
        val world = multiply(parent, localMatrix(node))

        if (node.has("mesh")) {
            val mesh = root.getJSONArray("meshes").getJSONObject(node.getInt("mesh"))
            val primitives = mesh.optJSONArray("primitives") ?: JSONArray()
            for (p in 0 until primitives.length()) {
                buildPart(root, reader, primitives.getJSONObject(p), world)?.let { out.add(it) }
            }
        }
        val children = node.optJSONArray("children")
        if (children != null) {
            for (c in 0 until children.length()) {
                collectNode(root, reader, children.getInt(c), world, out, depth + 1)
            }
        }
    }

    private fun buildPart(
        root: JSONObject,
        reader: AccessorReader,
        primitive: JSONObject,
        world: FloatArray
    ): GlbPart? {
        if (primitive.optInt("mode", MODE_TRIANGLES) != MODE_TRIANGLES) return null
        val attributes = primitive.optJSONObject("attributes") ?: return null
        if (!attributes.has("POSITION")) return null

        val localPositions = reader.readFloats(attributes.getInt("POSITION"), 3)
        val vertexCount = localPositions.size / 3
        if (vertexCount == 0) return null
        if (vertexCount > 65535) {
            throw GlbFormatException("Primitive has $vertexCount vertices; 16-bit indices allow 65535")
        }

        val indices: ShortArray = if (primitive.has("indices")) {
            val raw = reader.readIndices(primitive.getInt("indices"))
            ShortArray(raw.size - raw.size % 3) { i ->
                val v = raw[i]
                if (v < 0 || v >= vertexCount) throw GlbFormatException("Index $v out of range")
                v.toShort()
            }
        } else {
            ShortArray(vertexCount - vertexCount % 3) { it.toShort() }
        }
        if (indices.isEmpty()) return null

        val positions = FloatArray(localPositions.size)
        for (v in 0 until vertexCount) {
            val x = localPositions[v * 3]
            val y = localPositions[v * 3 + 1]
            val z = localPositions[v * 3 + 2]
            positions[v * 3] = world[0] * x + world[4] * y + world[8] * z + world[12]
            positions[v * 3 + 1] = world[1] * x + world[5] * y + world[9] * z + world[13]
            positions[v * 3 + 2] = world[2] * x + world[6] * y + world[10] * z + world[14]
        }

        val normals: FloatArray = if (attributes.has("NORMAL")) {
            val local = reader.readFloats(attributes.getInt("NORMAL"), 3)
            if (local.size != localPositions.size) throw GlbFormatException("Normal count mismatch")
            val out = FloatArray(local.size)
            for (v in 0 until vertexCount) {
                val x = local[v * 3]
                val y = local[v * 3 + 1]
                val z = local[v * 3 + 2]
                val nx = world[0] * x + world[4] * y + world[8] * z
                val ny = world[1] * x + world[5] * y + world[9] * z
                val nz = world[2] * x + world[6] * y + world[10] * z
                val len = sqrt(nx * nx + ny * ny + nz * nz)
                if (len > 1e-6f) {
                    out[v * 3] = nx / len; out[v * 3 + 1] = ny / len; out[v * 3 + 2] = nz / len
                } else {
                    out[v * 3 + 1] = 1f
                }
            }
            out
        } else {
            smoothNormals(positions, indices)
        }

        var color = floatArrayOf(1f, 1f, 1f, 1f)
        var imageIndex = -1
        var uvs: FloatArray? = null
        val materials = root.optJSONArray("materials")
        if (primitive.has("material") && materials != null) {
            val pbr = materials.optJSONObject(primitive.getInt("material"))?.optJSONObject("pbrMetallicRoughness")
            pbr?.optJSONArray("baseColorFactor")?.let { f ->
                if (f.length() >= 4) color = FloatArray(4) { f.getDouble(it).toFloat() }
            }
            val texInfo = pbr?.optJSONObject("baseColorTexture")
            if (texInfo != null && texInfo.optInt("texCoord", 0) == 0 && attributes.has("TEXCOORD_0")) {
                val source = root.optJSONArray("textures")
                    ?.optJSONObject(texInfo.getInt("index"))?.optInt("source", -1) ?: -1
                val coords = reader.readFloats(attributes.getInt("TEXCOORD_0"), 2)
                if (source >= 0 && coords.size == vertexCount * 2) {
                    imageIndex = source
                    uvs = coords
                }
            }
        }
        return GlbPart(positions, normals, uvs, indices, color, imageIndex)
    }

    private fun normalizeToTarget(parts: List<GlbPart>, width: Float, length: Float, height: Float) {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (part in parts) {
            val p = part.positions
            for (i in p.indices step 3) {
                minX = minOf(minX, p[i]); maxX = maxOf(maxX, p[i])
                minY = minOf(minY, p[i + 1]); maxY = maxOf(maxY, p[i + 1])
                minZ = minOf(minZ, p[i + 2]); maxZ = maxOf(maxZ, p[i + 2])
            }
        }
        val centerX = (minX + maxX) / 2f
        val centerZ = (minZ + maxZ) / 2f
        val sx = if (maxX - minX > 1e-6f) width / (maxX - minX) else 1f
        val sy = if (maxY - minY > 1e-6f) height / (maxY - minY) else 1f
        val sz = if (maxZ - minZ > 1e-6f) length / (maxZ - minZ) else 1f
        for (part in parts) {
            val p = part.positions
            for (i in p.indices step 3) {
                p[i] = (p[i] - centerX) * sx
                p[i + 1] = (p[i + 1] - minY) * sy
                p[i + 2] = (p[i + 2] - centerZ) * sz
            }
        }
    }

    private fun smoothNormals(positions: FloatArray, indices: ShortArray): FloatArray {
        val normals = FloatArray(positions.size)
        for (t in indices.indices step 3) {
            val a = indices[t].toInt() and 0xFFFF
            val b = indices[t + 1].toInt() and 0xFFFF
            val c = indices[t + 2].toInt() and 0xFFFF
            val e1x = positions[b * 3] - positions[a * 3]
            val e1y = positions[b * 3 + 1] - positions[a * 3 + 1]
            val e1z = positions[b * 3 + 2] - positions[a * 3 + 2]
            val e2x = positions[c * 3] - positions[a * 3]
            val e2y = positions[c * 3 + 1] - positions[a * 3 + 1]
            val e2z = positions[c * 3 + 2] - positions[a * 3 + 2]
            val nx = e1y * e2z - e1z * e2y
            val ny = e1z * e2x - e1x * e2z
            val nz = e1x * e2y - e1y * e2x
            for (v in intArrayOf(a, b, c)) {
                normals[v * 3] += nx; normals[v * 3 + 1] += ny; normals[v * 3 + 2] += nz
            }
        }
        for (i in normals.indices step 3) {
            val len = sqrt(normals[i] * normals[i] + normals[i + 1] * normals[i + 1] + normals[i + 2] * normals[i + 2])
            if (len > 1e-6f) {
                normals[i] /= len; normals[i + 1] /= len; normals[i + 2] /= len
            } else {
                normals[i + 1] = 1f
            }
        }
        return normals
    }

    private fun imageBytes(root: JSONObject, data: ByteArray, binOffset: Int, imageIndex: Int): ByteArray? {
        val image = root.optJSONArray("images")?.optJSONObject(imageIndex) ?: return null
        if (!image.has("bufferView")) return null
        val view = root.getJSONArray("bufferViews").getJSONObject(image.getInt("bufferView"))
        val start = binOffset + view.optInt("byteOffset", 0)
        val end = start + view.getInt("byteLength")
        if (start < 0 || end > data.size || end <= start) return null
        return data.copyOfRange(start, end)
    }

    // --- Column-major 4x4 matrix helpers (glTF convention) ---

    private fun rotationY(radians: Float): FloatArray {
        val c = cos(radians); val s = sin(radians)
        return floatArrayOf(c, 0f, -s, 0f, 0f, 1f, 0f, 0f, s, 0f, c, 0f, 0f, 0f, 0f, 1f)
    }

    private fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(16)
        for (col in 0 until 4) for (row in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) sum += a[k * 4 + row] * b[col * 4 + k]
            out[col * 4 + row] = sum
        }
        return out
    }

    private fun localMatrix(node: JSONObject): FloatArray {
        node.optJSONArray("matrix")?.let { m ->
            if (m.length() == 16) return FloatArray(16) { m.getDouble(it).toFloat() }
        }
        val t = node.optJSONArray("translation")?.let { a -> FloatArray(3) { a.getDouble(it).toFloat() } }
            ?: floatArrayOf(0f, 0f, 0f)
        val q = node.optJSONArray("rotation")?.let { a -> FloatArray(4) { a.getDouble(it).toFloat() } }
            ?: floatArrayOf(0f, 0f, 0f, 1f)
        val s = node.optJSONArray("scale")?.let { a -> FloatArray(3) { a.getDouble(it).toFloat() } }
            ?: floatArrayOf(1f, 1f, 1f)
        val (x, y, z, w) = q
        val m = floatArrayOf(
            1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0f,
            2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0f,
            2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0f,
            0f, 0f, 0f, 1f
        )
        for (i in 0 until 3) {
            m[i] *= s[0]; m[4 + i] *= s[1]; m[8 + i] *= s[2]
        }
        m[12] = t[0]; m[13] = t[1]; m[14] = t[2]
        return m
    }

    /** Reads accessor data out of the GLB's binary chunk, honoring byte strides. */
    private class AccessorReader(
        private val root: JSONObject,
        private val buf: ByteBuffer,
        private val binOffset: Int
    ) {
        private class Layout(val base: Int, val stride: Int, val count: Int, val componentType: Int,
                              val components: Int, val normalized: Boolean)

        private fun layout(accessorIndex: Int): Layout {
            val accessor = root.getJSONArray("accessors").getJSONObject(accessorIndex)
            if (accessor.has("sparse")) throw GlbFormatException("Sparse accessors are not supported")
            if (!accessor.has("bufferView")) throw GlbFormatException("Accessor without bufferView")
            val view = root.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"))
            val componentType = accessor.getInt("componentType")
            val components = when (accessor.getString("type")) {
                "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4
                else -> throw GlbFormatException("Unsupported accessor type")
            }
            val elementBytes = components * componentSize(componentType)
            val stride = view.optInt("byteStride", 0).takeIf { it > 0 } ?: elementBytes
            val base = binOffset + view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
            val count = accessor.getInt("count")
            if (count > 0 && base + (count - 1) * stride + elementBytes > buf.capacity()) {
                throw GlbFormatException("Accessor $accessorIndex reads past end of file")
            }
            return Layout(base, stride, count, componentType, components, accessor.optBoolean("normalized", false))
        }

        fun readFloats(accessorIndex: Int, expectedComponents: Int): FloatArray {
            val l = layout(accessorIndex)
            if (l.components != expectedComponents) throw GlbFormatException("Unexpected accessor width")
            val out = FloatArray(l.count * l.components)
            val size = componentSize(l.componentType)
            for (i in 0 until l.count) for (c in 0 until l.components) {
                out[i * l.components + c] = readComponent(l.base + i * l.stride + c * size, l.componentType, l.normalized)
            }
            return out
        }

        fun readIndices(accessorIndex: Int): IntArray {
            val l = layout(accessorIndex)
            if (l.components != 1) throw GlbFormatException("Index accessor must be SCALAR")
            return IntArray(l.count) { i ->
                val at = l.base + i * l.stride
                when (l.componentType) {
                    5121 -> buf.get(at).toInt() and 0xFF
                    5123 -> buf.getShort(at).toInt() and 0xFFFF
                    5125 -> buf.getInt(at)
                    else -> throw GlbFormatException("Unsupported index type ${l.componentType}")
                }
            }
        }

        private fun componentSize(type: Int) = when (type) {
            5120, 5121 -> 1
            5122, 5123 -> 2
            5125, 5126 -> 4
            else -> throw GlbFormatException("Unsupported component type $type")
        }

        private fun readComponent(at: Int, type: Int, normalized: Boolean): Float = when (type) {
            5126 -> buf.getFloat(at)
            5120 -> buf.get(at).toFloat().let { if (normalized) maxOf(it / 127f, -1f) else it }
            5121 -> (buf.get(at).toInt() and 0xFF).toFloat().let { if (normalized) it / 255f else it }
            5122 -> buf.getShort(at).toFloat().let { if (normalized) maxOf(it / 32767f, -1f) else it }
            5123 -> (buf.getShort(at).toInt() and 0xFFFF).toFloat().let { if (normalized) it / 65535f else it }
            5125 -> (buf.getInt(at).toLong() and 0xFFFFFFFFL).toFloat()
            else -> throw GlbFormatException("Unsupported component type $type")
        }
    }
}
