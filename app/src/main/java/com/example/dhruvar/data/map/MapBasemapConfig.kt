package com.example.dhruvar.data.map

import com.example.dhruvar.BuildConfig

/**
 * Runtime map / PMTiles configuration.
 *
 * Prefer [BuildConfig.MAP_STYLE_URL] (style JSON that already references `pmtiles://` sources).
 * Optionally set [BuildConfig.PMTILES_URL] to a direct archive and load a minimal raster style
 * programmatically — see `docs/PMTiles-Configuration.md`.
 */
object MapBasemapConfig {
    /** Default camera when no geographic anchor is set (demo Alps imagery). */
    const val DEFAULT_LATITUDE = 47.5
    const val DEFAULT_LONGITUDE = 12.0
    const val DEFAULT_ZOOM = 9.0
    const val ANCHOR_ZOOM = 17.0

    fun styleUrl(): String {
        val configured = BuildConfig.MAP_STYLE_URL.trim()
        return configured.ifEmpty {
            "https://demotiles.maplibre.org/pmtiles/raster/style-imagery.json"
        }
    }

    /** Non-blank when a direct PMTiles URL override is configured. */
    fun directPmtilesUrl(): String? =
        BuildConfig.PMTILES_URL.trim().takeIf { it.isNotEmpty() }
}
