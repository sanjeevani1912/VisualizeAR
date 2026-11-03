package com.example.dhruvar.data.map

import com.example.dhruvar.BuildConfig

/**
 * Runtime map / PMTiles configuration.
 *
 * When [BuildConfig.MAP_SERVER_BASE] is set, the self-hosted India map server provides tiles,
 * glyphs, the offline archive and place search. Otherwise the app falls back to
 * [BuildConfig.MAP_STYLE_URL] (a global style with no `/search` endpoint) and search stays off.
 *
 * See `docs/Map-Server-Configuration.md`.
 */
object MapBasemapConfig {
    /** Default camera when no geographic anchor is set (India / Delhi area). */
    const val DEFAULT_LATITUDE = 28.6139
    const val DEFAULT_LONGITUDE = 77.2090
    const val DEFAULT_ZOOM = 11.0
    const val ANCHOR_ZOOM = 17.0

    /** Camera zoom used after jumping to a place-search result. */
    const val SEARCH_RESULT_ZOOM = 15.0

    /** Street-level zoom used when centering on the device's own GNSS fix. */
    const val GNSS_FIX_ZOOM = 18.0

    /** Mandatory on every map screen — see `docs/android.md` §9. */
    const val ATTRIBUTION = "© OpenStreetMap contributors · " +
        "Boundaries: Survey of India via DataMeet (CC BY 4.0)"

    /** Map server base URL without a trailing slash, or empty when unconfigured. */
    fun serverBase(): String = BuildConfig.MAP_SERVER_BASE.trim().trimEnd('/')

    fun isServerConfigured(): Boolean = serverBase().isNotEmpty()

    /**
     * Style document URL. Points at the map server when configured; the returned style still
     * needs [MapStyleRewriter] applied because the server may advertise placeholder URLs.
     */
    fun styleUrl(): String {
        if (isServerConfigured()) return "${serverBase()}/style.json"
        val configured = BuildConfig.MAP_STYLE_URL.trim()
        return configured.ifEmpty {
            // Global OSM vector style — not limited to a regional PMTiles archive
            "https://tiles.openfreemap.org/styles/liberty"
        }
    }

    /** TileJSON describing the India vector tiles, or null when no map server is configured. */
    fun tileJsonUrl(): String? =
        if (isServerConfigured()) "${serverBase()}/tiles/india.json" else null

    /** Place-search endpoint, or null when no map server is configured. */
    fun searchUrl(): String? =
        if (isServerConfigured()) "${serverBase()}/search" else null

    fun isSearchConfigured(): Boolean = searchUrl() != null

    /** Complete offline archive (~1.9 GB, supports HTTP Range), or null when unconfigured. */
    fun pmtilesArchiveUrl(): String? =
        if (isServerConfigured()) "${serverBase()}/files/india.pmtiles" else null

    /** Non-blank when a direct PMTiles URL override is configured. */
    fun directPmtilesUrl(): String? =
        BuildConfig.PMTILES_URL.trim().takeIf { it.isNotEmpty() }
}
