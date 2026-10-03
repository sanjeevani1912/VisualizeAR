package com.example.dhruvar.data.map

import com.example.dhruvar.domain.model.PlaceSearchHit
import org.json.JSONObject

/**
 * Parses `/search` responses from the India map server.
 *
 * Shape as observed on the live server (which differs slightly from `docs/android.md`: the default
 * `limit` is 20, and the envelope also carries `processingTimeMs`, `limit`, `offset` and
 * `requestUid`). Only `hits` is consumed here.
 *
 * `name_en` appears only when `name` is in a non-Latin script, so it is optional.
 */
object PlaceSearchResponseParser {
    fun parse(json: String): List<PlaceSearchHit> {
        val hits = runCatching { JSONObject(json).optJSONArray("hits") }.getOrNull()
            ?: return emptyList()

        return (0 until hits.length()).mapNotNull { index ->
            hits.optJSONObject(index)?.let(::toHit)
        }
    }

    /** Returns null for hits the map cannot act on: no coordinates, or no name to label them. */
    private fun toHit(hit: JSONObject): PlaceSearchHit? {
        val geo = hit.optJSONObject("_geo") ?: return null
        if (!geo.has("lat") || !geo.has("lng")) return null
        val latitude = geo.optDouble("lat", Double.NaN)
        val longitude = geo.optDouble("lng", Double.NaN)
        if (latitude.isNaN() || longitude.isNaN()) return null

        val name = hit.optString("name").trim()
        val englishName = hit.optString("name_en").trim().takeIf { it.isNotEmpty() }
        if (name.isEmpty() && englishName == null) return null

        return PlaceSearchHit(
            id = hit.optString("id"),
            name = name,
            englishName = englishName,
            kind = hit.optString("kind").takeIf { it.isNotEmpty() },
            subtype = hit.optString("subtype").takeIf { it.isNotEmpty() },
            rank = if (hit.has("rank")) hit.optInt("rank") else null,
            latitude = latitude,
            longitude = longitude
        )
    }
}
