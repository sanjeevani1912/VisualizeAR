package com.example.dhruvar.data.map

/**
 * Fetches the map server's style document and repoints it at a reachable address via
 * [MapStyleRewriter] before MapLibre ever sees it.
 *
 * Returns failure when no map server is configured, so callers can fall back to handing
 * [MapBasemapConfig.styleUrl] straight to MapLibre.
 */
object MapStyleLoader {
    suspend fun loadRewrittenStyle(): Result<String> {
        val base = MapBasemapConfig.serverBase()
        if (base.isEmpty()) {
            return Result.failure(IllegalStateException("No map server configured"))
        }
        return runCatching {
            val style = MapServerHttp.getString(MapBasemapConfig.styleUrl())
            // Optional: without it the style keeps its TileJSON reference, which still carries
            // the server's placeholder base. Tiles would fail but glyphs and layers survive.
            val tileJson = MapBasemapConfig.tileJsonUrl()?.let { url ->
                runCatching { MapServerHttp.getString(url) }.getOrNull()
            }
            MapStyleRewriter.rewrite(style, tileJson, base)
        }
    }
}
