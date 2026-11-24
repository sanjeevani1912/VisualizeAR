package com.example.dhruvar.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Result of checking the server address a user typed in. */
sealed interface ServerUrlCheck {
    data class Ok(val baseUrl: String) : ServerUrlCheck
    data class Bad(val reason: String) : ServerUrlCheck
}

/**
 * Cleans up and vets the dashboard server address.
 *
 * Plain `http://` sends the password and the plan unencrypted, so it is only accepted when
 * [allowCleartext] is true (debug builds on a trusted LAN). Release builds require `https://`.
 */
object ServerUrl {
    fun normalize(raw: String, allowCleartext: Boolean): ServerUrlCheck {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ServerUrlCheck.Bad("Enter the server address")

        val withScheme = if (trimmed.contains("://")) {
            trimmed
        } else {
            (if (allowCleartext) "http://" else "https://") + trimmed
        }
        val url = withScheme.toHttpUrlOrNull()
            ?: return ServerUrlCheck.Bad("That is not a valid server address")

        if (url.scheme == "http" && !allowCleartext) {
            return ServerUrlCheck.Bad("The server address must start with https://")
        }
        // Keep only scheme, host and port: a pasted page path or query is never part of the base.
        val base = url.newBuilder()
            .username("")
            .password("")
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .trimEnd('/')
        return ServerUrlCheck.Ok(base)
    }

    /** Path under the server address where Nginx publishes the map server. */
    const val MAP_PATH = "/map"

    /**
     * The map address that goes with a vetted server address: `<server>/map`.
     * The stack serves the dashboard, `/api` and `/map` from one address, so one typed address is enough.
     * Takes the `baseUrl` of a [ServerUrlCheck.Ok]; returns null for a blank value.
     */
    fun mapBaseFor(serverBase: String): String? {
        val trimmed = serverBase.trim().trimEnd('/')
        return if (trimmed.isEmpty()) null else trimmed + MAP_PATH
    }

    /**
     * Vets a map address given on its own (same rules as [normalize], but a path such as `/map`
     * is kept). Returns the base without a trailing slash.
     */
    fun normalizeMap(raw: String, allowCleartext: Boolean): ServerUrlCheck {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ServerUrlCheck.Bad("Enter the map address")
        val withScheme = if (trimmed.contains("://")) {
            trimmed
        } else {
            (if (allowCleartext) "http://" else "https://") + trimmed
        }
        val url = withScheme.toHttpUrlOrNull()
            ?: return ServerUrlCheck.Bad("That is not a valid map address")
        if (url.scheme == "http" && !allowCleartext) {
            return ServerUrlCheck.Bad("The map address must start with https://")
        }
        val base = url.newBuilder()
            .username("")
            .password("")
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .trimEnd('/')
        return ServerUrlCheck.Ok(base)
    }
}
