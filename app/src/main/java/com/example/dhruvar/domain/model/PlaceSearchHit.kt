package com.example.dhruvar.domain.model

/**
 * One result from the map server's `/search` endpoint.
 *
 * Only hits carrying usable coordinates are modelled; see `PlaceSearchResponseParser`.
 */
data class PlaceSearchHit(
    /** OSM object id: `n123` node, `w45` way, `r6` relation. */
    val id: String,
    /** Name in the local script — may be Urdu, Hindi, etc. */
    val name: String,
    /** English name. Absent when [name] is already Latin script. */
    val englishName: String?,
    /** Broad category, e.g. `place`, `amenity`, `railway`. */
    val kind: String?,
    /** Specific category, e.g. `city`, `station`, `hospital`. */
    val subtype: String?,
    /** 1 for cities and states through 9 for ordinary POIs; results arrive pre-ordered. */
    val rank: Int?,
    val latitude: Double,
    val longitude: Double
) {
    /** Label for English-reading users, per `docs/android.md` §6. */
    val displayName: String get() = englishName?.takeIf { it.isNotBlank() } ?: name

    /** Secondary label, most specific category available. */
    val displayCategory: String? get() =
        subtype?.takeIf { it.isNotBlank() } ?: kind?.takeIf { it.isNotBlank() }
}
