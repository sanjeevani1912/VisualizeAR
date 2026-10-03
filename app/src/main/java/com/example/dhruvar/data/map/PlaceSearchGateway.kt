package com.example.dhruvar.data.map

import com.example.dhruvar.domain.model.PlaceSearchHit

/**
 * Place search as the planning ViewModel sees it, so search state can be tested without a server.
 */
interface PlaceSearchGateway {
    /** False when no map server base is set; the UI says so instead of issuing doomed requests. */
    val isConfigured: Boolean

    suspend fun search(query: String, near: Pair<Double, Double>?): Result<List<PlaceSearchHit>>
}

/** Talks to the configured map server. */
object DefaultPlaceSearchGateway : PlaceSearchGateway {
    override val isConfigured: Boolean
        get() = MapBasemapConfig.isSearchConfigured()

    override suspend fun search(
        query: String,
        near: Pair<Double, Double>?
    ): Result<List<PlaceSearchHit>> = PlaceSearchClient.search(query = query, near = near)
}
