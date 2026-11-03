package com.example.dhruvar.data.model

import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.GeoBounds
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
 * - Versioned schema (`"version": 2`) for geographic anchor / region fields with
 *   backward-compatible load of schema v1 (missing geo → null).
 * - Defensive decoding: gracefully sanitizes corrupted fields, non-positive dimensions,
 *   unrecognized asset types, non-finite coordinates, and invalid geo values without crashing.
 * - 100% offline, zero-dependency serialization based on standard Android [JSONObject].
 */
object LayoutJsonSerializer {

    const val CURRENT_SCHEMA_VERSION = 2

    private val DEFAULT_ASSET_TYPE = AssetType.MILITARY_VEHICLE

    /** Asset types removed from the library, mapped to their nearest replacement. */
    private val LEGACY_ASSET_TYPES = mapOf(
        "TENT" to AssetType.MILITARY_VEHICLE,
        "TRUCK" to AssetType.MILITARY_VEHICLE,
        "CAR" to AssetType.OFFROAD_VEHICLE,
        "ANTENNA" to AssetType.MILITARY_VEHICLE,
        "TRENCH" to AssetType.MILITARY_VEHICLE
    )

    /**
     * Serializes a [Layout] into a formatted JSON string.
     * Optional geographic fields are written only when present.
     */
    fun serialize(layout: Layout): String {
        try {
            val root = JSONObject()
            root.put("version", CURRENT_SCHEMA_VERSION)
            root.put("id", layout.id)
            root.put("name", layout.name)
            root.put("createdAtEpochMs", layout.createdAtEpochMs)
            root.put("updatedAtEpochMs", layout.updatedAtEpochMs)

            layout.regionBounds?.let { bounds ->
                val boundsObj = JSONObject()
                boundsObj.put("northLatitude", bounds.northLatitude)
                boundsObj.put("southLatitude", bounds.southLatitude)
                boundsObj.put("eastLongitude", bounds.eastLongitude)
                boundsObj.put("westLongitude", bounds.westLongitude)
                root.put("regionBounds", boundsObj)
            }

            // Anchor serialization
            val anchorObj = JSONObject()
            anchorObj.put("id", layout.anchor.id)
            anchorObj.put("label", layout.anchor.label)
            anchorObj.put("x", layout.anchor.x.toDouble())
            anchorObj.put("z", layout.anchor.z.toDouble())
            anchorObj.put("description", layout.anchor.description)
            putOptionalDouble(anchorObj, "latitude", layout.anchor.latitude)
            putOptionalDouble(anchorObj, "longitude", layout.anchor.longitude)
            putOptionalFloat(anchorObj, "headingDegrees", layout.anchor.headingDegrees)
            putOptionalFloat(anchorObj, "horizontalAccuracyMeters", layout.anchor.horizontalAccuracyMeters)
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
     *
     * Schema v1 (or any document missing geo keys) loads with null geographic fields.
     * Schema v2 with partial/invalid geo sanitizes: non-finite coords → null;
     * invalid [GeoBounds] (e.g. south > north, antimeridian) → null regionBounds.
     */
    fun deserialize(jsonString: String): Layout {
        try {
            val root = JSONObject(jsonString)

            val id = root.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
            val name = root.optString("name").takeIf { it.isNotBlank() } ?: "Untitled Plan"
            val createdAt = root.optLong("createdAtEpochMs", System.currentTimeMillis())
            val updatedAt = root.optLong("updatedAtEpochMs", System.currentTimeMillis())

            val regionBounds = deserializeRegionBounds(root.optJSONObject("regionBounds"))

            // Deserialize Anchor
            val anchorJson = root.optJSONObject("anchor")
            val anchor = if (anchorJson != null) {
                Anchor(
                    id = anchorJson.optString("id").takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
                    label = anchorJson.optString("label", "Origin Anchor"),
                    x = sanitizeFloat(anchorJson.optDouble("x", 0.0).toFloat()),
                    z = sanitizeFloat(anchorJson.optDouble("z", 0.0).toFloat()),
                    description = anchorJson.optString("description", "Primary deployment reference datum"),
                    latitude = sanitizeLatitude(optFiniteDoubleOrNull(anchorJson, "latitude")),
                    longitude = sanitizeLongitude(optFiniteDoubleOrNull(anchorJson, "longitude")),
                    headingDegrees = optFiniteFloatOrNull(anchorJson, "headingDegrees"),
                    horizontalAccuracyMeters = optFiniteFloatOrNull(anchorJson, "horizontalAccuracyMeters")
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

                    // Safe AssetType recovery. Plans saved before the asset library changed
                    // carry retired type names; those map to the closest current asset and
                    // take its default dimensions instead of the stored ones.
                    val typeStr = objJson.optString("assetType", DEFAULT_ASSET_TYPE.name).uppercase()
                    val legacyType = LEGACY_ASSET_TYPES[typeStr]
                    val assetType = legacyType ?: try {
                        AssetType.valueOf(typeStr)
                    } catch (_: Exception) {
                        DEFAULT_ASSET_TYPE
                    }
                    val isLegacyObject = legacyType != null

                    val objId = objJson.optString("id").takeIf { it.isNotBlank() }
                        ?: java.util.UUID.randomUUID().toString()
                    val objName = objJson.optString("name").takeIf { it.isNotBlank() && !isLegacyObject }
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

                    val widthMeters = if (wRaw > 0f && !isLegacyObject) wRaw else defaultSpec.widthMeters
                    val lengthMeters = if (lRaw > 0f && !isLegacyObject) lRaw else defaultSpec.lengthMeters
                    val heightMeters = if (hRaw > 0f && !isLegacyObject) hRaw else defaultSpec.heightMeters
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
                updatedAtEpochMs = updatedAt,
                regionBounds = regionBounds
            )
        } catch (e: Exception) {
            throw LayoutSerializationException("Malformed layout JSON: ${e.message}", e)
        }
    }

    private fun deserializeRegionBounds(boundsJson: JSONObject?): GeoBounds? {
        if (boundsJson == null) return null
        val north = optFiniteDoubleOrNull(boundsJson, "northLatitude") ?: return null
        val south = optFiniteDoubleOrNull(boundsJson, "southLatitude") ?: return null
        val east = optFiniteDoubleOrNull(boundsJson, "eastLongitude") ?: return null
        val west = optFiniteDoubleOrNull(boundsJson, "westLongitude") ?: return null
        return GeoBounds.createOrNull(
            northLatitude = north,
            southLatitude = south,
            eastLongitude = east,
            westLongitude = west
        )
    }

    private fun putOptionalDouble(json: JSONObject, key: String, value: Double?) {
        if (value != null && value.isFinite()) {
            json.put(key, value)
        }
    }

    private fun putOptionalFloat(json: JSONObject, key: String, value: Float?) {
        if (value != null && value.isFinite()) {
            json.put(key, value.toDouble())
        }
    }

    private fun optFiniteDoubleOrNull(json: JSONObject, key: String): Double? {
        if (!json.has(key) || json.isNull(key)) return null
        // optDouble returns NaN when the value is non-numeric
        val value = json.optDouble(key, Double.NaN)
        return if (value.isFinite()) value else null
    }

    private fun optFiniteFloatOrNull(json: JSONObject, key: String): Float? {
        val doubleValue = optFiniteDoubleOrNull(json, key) ?: return null
        val floatValue = doubleValue.toFloat()
        return if (floatValue.isFinite()) floatValue else null
    }

    private fun sanitizeLatitude(value: Double?): Double? {
        if (value == null || !value.isFinite()) return null
        if (value !in -90.0..90.0) return null
        return value
    }

    private fun sanitizeLongitude(value: Double?): Double? {
        if (value == null || !value.isFinite()) return null
        return value
    }

    private fun sanitizeFloat(value: Float): Float {
        return if (value.isNaN() || value.isInfinite()) 0.0f else value
    }
}
