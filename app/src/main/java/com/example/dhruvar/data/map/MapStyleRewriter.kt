package com.example.dhruvar.data.map

import org.json.JSONArray
import org.json.JSONObject

/**
 * Repoints a map-server style document at the address the client can actually reach.
 *
 * The India map server builds absolute URLs from its own `PUBLIC_URL`, and that substitution has
 * not been applied on the current deployment: `style.json` advertises `http://localhost:8080` for
 * glyphs and for its TileJSON. A client on any other host resolves `localhost` to itself, so the
 * style parses but every tile and glyph request fails — a blank map with no error.
 *
 * Two steps are needed, because fixing only `style.json` is not enough:
 *  1. swap the placeholder base for the configured one, and
 *  2. inline the TileJSON's tile templates into the style source, since the TileJSON *it* points
 *     at repeats the placeholder base in its own `tiles` array.
 *
 * Safe once the server is fixed: with no placeholder present step 1 is a no-op and step 2 still
 * resolves against the real base.
 *
 * See `docs/Map-Server-Configuration.md`.
 */
object MapStyleRewriter {
    /** Base URL the current server deployment advertises instead of its real address. */
    const val PLACEHOLDER_BASE = "http://localhost:8080"

    private val PLACEHOLDER_BASES = listOf(
        PLACEHOLDER_BASE,
        "http://127.0.0.1:8080",
        "http://0.0.0.0:8080"
    )

    /** TileJSON fields worth carrying onto an inlined source when the style omits them. */
    private val INHERITED_TILEJSON_FIELDS =
        listOf("minzoom", "maxzoom", "bounds", "attribution", "scheme")

    /**
     * @param styleJson raw `style.json` body from the map server.
     * @param tileJson raw TileJSON body (`tiles/india.json`), or null to skip inlining.
     * @param serverBase reachable base URL, no trailing slash. Blank returns [styleJson] as-is.
     * @return rewritten style document, or [styleJson] unchanged if it cannot be parsed.
     */
    fun rewrite(styleJson: String, tileJson: String?, serverBase: String): String {
        val base = serverBase.trim().trimEnd('/')
        if (base.isEmpty()) return styleJson

        val style = parseOrNull(repoint(styleJson, base)) ?: return styleJson
        parseOrNull(tileJson?.let { repoint(it, base) })?.let { inlineTileSources(style, it, base) }
        return style.toString()
    }

    private fun repoint(json: String, base: String): String =
        PLACEHOLDER_BASES.fold(json) { acc, placeholder -> acc.replace(placeholder, base) }

    private fun parseOrNull(json: String?): JSONObject? =
        json?.let { runCatching { JSONObject(it) }.getOrNull() }

    /**
     * Replaces `sources.<name>.url` with the TileJSON's own `tiles` templates so MapLibre never
     * fetches the TileJSON. Only sources already pointing at [base] are touched, leaving any
     * third-party source in the style alone.
     */
    private fun inlineTileSources(style: JSONObject, tileJson: JSONObject, base: String) {
        val sources = style.optJSONObject("sources") ?: return
        val templates = tileJson.optJSONArray("tiles")?.takeIf { it.length() > 0 } ?: return

        for (name in sources.keys().asSequence().toList()) {
            val source = sources.optJSONObject(name) ?: continue
            val url = source.optString("url").takeIf { it.isNotEmpty() } ?: continue
            if (!url.startsWith(base)) continue

            source.remove("url")
            source.put("tiles", JSONArray(templates.toString()))
            for (field in INHERITED_TILEJSON_FIELDS) {
                if (!source.has(field) && tileJson.has(field)) {
                    source.put(field, tileJson.get(field))
                }
            }
        }
    }
}
