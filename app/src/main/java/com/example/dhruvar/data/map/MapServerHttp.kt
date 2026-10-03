package com.example.dhruvar.data.map

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Shared HTTP access to the map server for the small JSON documents the app fetches itself
 * (`style.json`, TileJSON, `/search`). Tile and glyph traffic is MapLibre's own business.
 *
 * The server takes no credentials — see `docs/android.md`. Nothing here may add any.
 *
 * Timeouts are short because every caller is on a user-visible path: a LAN server that has gone
 * away should surface quickly rather than hang the map or the search field.
 */
internal object MapServerHttp {
    private const val CONNECT_TIMEOUT_SECONDS = 5L
    private const val READ_TIMEOUT_SECONDS = 10L

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * GETs [url] as text. Cancelling the calling coroutine cancels the request, which is what
     * makes search-as-you-type safe to fire on every keystroke.
     *
     * @throws IOException on transport failure, a non-2xx status, or an empty body.
     */
    suspend fun getString(url: String): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        continuation.resumeWithException(
                            IOException("HTTP ${it.code} from $url")
                        )
                        return
                    }
                    val body = it.body?.string()
                    if (body.isNullOrEmpty()) {
                        continuation.resumeWithException(IOException("Empty response from $url"))
                    } else {
                        continuation.resume(body)
                    }
                }
            }
        })
    }
}
