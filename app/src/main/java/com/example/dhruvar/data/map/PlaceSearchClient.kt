package com.example.dhruvar.data.map

import com.example.dhruvar.domain.model.PlaceSearchHit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Place search against the map server (`GET {BASE}/search`). No credentials — the server attaches
 * its own search-only key on the way to the search engine, and only `/search` is proxied.
 *
 * Prefix matching and typo tolerance are server-side, so the query is passed through untouched.
 * Cancelling the calling coroutine cancels the request, which is what lets the UI fire on every
 * keystroke; debouncing belongs to the caller.
 *
 * Street names are not indexed. Latin-script queries are the most reliable — see
 * `docs/android.md` §6.
 */
object PlaceSearchClient {
    const val DEFAULT_LIMIT = 10

    /**
     * @param near when the device has a fix, biases results to the nearest first.
     * @return empty list for a blank query; failure when unconfigured or the request fails.
     */
    suspend fun search(
        query: String,
        limit: Int = DEFAULT_LIMIT,
        near: Pair<Double, Double>? = null
    ): Result<List<PlaceSearchHit>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())

        val endpoint = MapBasemapConfig.searchUrl()
            ?: return Result.failure(IllegalStateException("Map search server not configured"))
        val url = endpoint.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("q", trimmed)
            ?.addQueryParameter("limit", limit.toString())
            ?.apply {
                near?.let { (latitude, longitude) ->
                    addQueryParameter("sort", "_geoPoint($latitude,$longitude):asc")
                }
            }
            ?.build()
            ?: return Result.failure(IllegalArgumentException("Invalid search URL: $endpoint"))

        return runCatching { PlaceSearchResponseParser.parse(MapServerHttp.getString(url.toString())) }
    }
}
