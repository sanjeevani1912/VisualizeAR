package com.example.dhruvar.data.model

import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.LayoutObject
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Exception thrown when JSON layout parsing or validation fails.
 */
class LayoutSerializationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Robust JSON serializer and deserializer for [Layout] entities.
 *
 * Implements:
 * - Versioned schema (`"version": 1`) for future-proof migrations.
 * - Defensive decoding: gracefully sanitizes corrupted fields, non-positive dimensions,
 *   unrecognized asset types, and non-finite coordinates without crashing.
 * - 100% offline, zero-dependency serialization based on standard Android [JSONObject].
 */
object LayoutJsonSerializer {

    const val CURRENT_SCHEMA_VERSION = 1

    /**
     * Serializes a [Layout] into a formatted JSON string.
     */
    fun serialize(layout: Layout): String {
        try {
            val root = JSONObject()
            root.put("version", CURRENT_SCHEMA_VERSION)
            root.put("id", layout.id)
            root.put("name", layout.name)
            root.put("createdAtEpochMs", layout.createdAtEpochMs)
            root.put("updatedAtEpochMs", layout.updatedAtEpochMs)

            // Anchor serialization
            val anchorObj = JSONObject()
            anchorObj.put("id", layout.anchor.id)
            anchorObj.put("label", layout.anchor.label)
            anchorObj.put("x", layout.anchor.x.toDouble())
            anchorObj.put("z", layout.anchor.z.toDouble())
            anchorObj.put("description", layout.anchor.description)
            root.put("anchor", anchorObj)

            // LayoutObjects serialization
            val objectsArray = JSONArray()
            for (obj in layout.objects) {
                val objJson = JSONObject()
                objJson.put("id", obj.id)
                objJson.put("assetType", obj.assetType.name)
                objJson.put("name", obj.name)
                objJson.put("x", obj.x.toDouble())
                objJson.put("z", obj.z.toDouble())
                objJson.put("rotationDegrees", obj.rotationDegrees.toDouble())
                objJson.put("widthMeters", obj.widthMeters.toDouble())
                objJson.put("lengthMeters", obj.lengthMeters.toDouble())
                objJson.put("heightMeters", obj.heightMeters.toDouble())
                objJson.put("scale", obj.scale.toDouble())
                objectsArray.put(objJson)
            }
            root.put("objects", objectsArray)

            return root.toString(2)
        } catch (e: JSONException) {
            throw LayoutSerializationException("Failed to serialize layout '${layout.id}': ${e.message}", e)
        }
    }

    /**
     * Deserializes a JSON string into a validated [Layout] entity.
     * Gracefully falls back on corrupted or missing fields.
     */
    fun deserialize(jsonString: String): Layout {
        try {
            val root = JSONObject(jsonString)

            val id = root.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
            val name = root.optString("name").takeIf { it.isNotBlank() } ?: "Untitled Plan"
            val createdAt = root.optLong("createdAtEpochMs", System.currentTimeMillis())
            val updatedAt = root.optLong("updatedAtEpochMs", System.currentTimeMillis())

            // Deserialize Anchor
            val anchorJson = root.optJSONObject("anchor")
            val anchor = if (anchorJson != null) {
                Anchor(
                    id = anchorJson.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
                    label = anchorJson.optString("label", "Origin Anchor"),
                    x = sanitizeFloat(anchorJson.optDouble("x", 0.0).toFloat()),
                    z = sanitizeFloat(anchorJson.optDouble("z", 0.0).toFloat()),
                    description = anchorJson.optString("description", "Primary deployment reference datum")
                )
            } else {
                Anchor()
            }

            // Deserialize LayoutObjects
            val objectsList = mutableListOf<LayoutObject>()
            val objectsArray = root.optJSONArray("objects")
            if (objectsArray != null) {
                for (i in 0 until objectsArray.length()) {
                    val objJson = objectsArray.optJSONObject(i) ?: continue

                    // Safe AssetType recovery
                    val typeStr = objJson.optString("assetType", "TENT")
                    val assetType = try {
                        AssetType.valueOf(typeStr.uppercase())
                    } catch (_: Exception) {
                        AssetType.TENT
                    }

                    val objId = objJson.optString("id").takeIf { it.isNotBlank() }
                        ?: java.util.UUID.randomUUID().toString()
                    val objName = objJson.optString("name").takeIf { it.isNotBlank() }
                        ?: "${assetType.displayName} ${i + 1}"

                    val x = sanitizeFloat(objJson.optDouble("x", 0.0).toFloat())
                    val z = sanitizeFloat(objJson.optDouble("z", 0.0).toFloat())
                    val rot = LayoutObject.normalizeDegrees(
                        sanitizeFloat(objJson.optDouble("rotationDegrees", 0.0).toFloat())
                    )

                    // Validate physical dimensions strictly > 0
                    val defaultSpec = assetType.defaultSpecification
                    val wRaw = sanitizeFloat(objJson.optDouble("widthMeters", defaultSpec.widthMeters.toDouble()).toFloat())
                    val lRaw = sanitizeFloat(objJson.optDouble("lengthMeters", defaultSpec.lengthMeters.toDouble()).toFloat())
                    val hRaw = sanitizeFloat(objJson.optDouble("heightMeters", defaultSpec.heightMeters.toDouble()).toFloat())

                    val widthMeters = if (wRaw > 0f) wRaw else defaultSpec.widthMeters
                    val lengthMeters = if (lRaw > 0f) lRaw else defaultSpec.lengthMeters
                    val heightMeters = if (hRaw > 0f) hRaw else defaultSpec.heightMeters
                    val scale = sanitizeFloat(objJson.optDouble("scale", 1.0).toFloat()).coerceIn(0.1f, 10.0f)

                    objectsList.add(
                        LayoutObject(
                            id = objId,
                            assetType = assetType,
                            name = objName,
                            x = x,
                            z = z,
                            rotationDegrees = rot,
                            widthMeters = widthMeters,
                            lengthMeters = lengthMeters,
                            heightMeters = heightMeters,
                            scale = scale,
                            isSelected = false
                        )
                    )
                }
            }

            return Layout(
                id = id,
                name = name,
                anchor = anchor,
                objects = objectsList,
                createdAtEpochMs = createdAt,
                updatedAtEpochMs = updatedAt
            )
        } catch (e: Exception) {
            throw LayoutSerializationException("Malformed layout JSON: ${e.message}", e)
        }
    }

    private fun sanitizeFloat(value: Float): Float {
        return if (value.isNaN() || value.isInfinite()) 0.0f else value
    }
}
