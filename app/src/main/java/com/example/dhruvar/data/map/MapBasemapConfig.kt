package com.example.dhruvar.data.map

import com.example.dhruvar.BuildConfig

/**
 * Runtime map / PMTiles configuration.
 *
 * Prefer [BuildConfig.MAP_STYLE_URL] (default: OpenFreeMap global Liberty style).
 * Optional site-specific PMTiles: see `docs/PMTiles-Configuration.md`.
 */
object MapBasemapConfig {
    /** Default camera when no geographic anchor is set (India / Delhi area). */
    const val DEFAULT_LATITUDE = 28.6139
    const val DEFAULT_LONGITUDE = 77.2090
    const val DEFAULT_ZOOM = 11.0
    const val ANCHOR_ZOOM = 17.0

    fun styleUrl(): String {
        val configured = BuildConfig.MAP_STYLE_URL.trim()
        return configured.ifEmpty {
            // Global OSM vector style — not limited to a regional PMTiles archive
            "https://tiles.openfreemap.org/styles/liberty"
        }
    }

    /** Non-blank when a direct PMTiles URL override is configured. */
    fun directPmtilesUrl(): String? =
        BuildConfig.PMTILES_URL.trim().takeIf { it.isNotEmpty() }
}
