package com.example.dhruvar.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject

/**
 * An error from the dashboard server. [status] is the HTTP status, or 0 when the server could
 * not be reached at all. [message] is always safe to show to the user.
 */
class ServerException(val status: Int, message: String) : IOException(message) {
    val isSessionExpired: Boolean get() = status == 401
}

data class SignedInUser(
    val id: String,
    val username: String,
    val name: String,
    val rank: String,
    val role: String
)

data class TokenSession(val token: String, val user: SignedInUser)

/** What the server returns for a newly created plan. */
data class SubmittedPlan(val planId: String, val code: String)

/** One plan as the server sees it, reduced to what the app needs to show a status. */
data class RemotePlanStatus(
    val planId: String,
    val code: String,
    val status: String,
    val latestVersion: Int,
    val layoutId: String?,
    /** What the reviewers wrote about the latest version: the decision's reason, then their comments. */
    val feedback: List<String> = emptyList()
)

/**
 * The reviewers' words on a plan's latest version, oldest first: the reason given with the latest
 * decision (a reason is required to reject or request changes) and comments by anyone other than the
 * plan's owner. At most [MAX_FEEDBACK] lines, keeping the newest; blank text is skipped.
 */
fun reviewerFeedback(plan: JSONObject, latestVersion: Int): List<String> {
    val ownerId = plan.optString("ownerId")
    val entries = ArrayList<Pair<Long, String>>()
    val decisions = plan.optJSONArray("decisions")
    var lastDecision: JSONObject? = null
    if (decisions != null) {
        for (i in 0 until decisions.length()) {
            val d = decisions.optJSONObject(i) ?: continue
            if (d.optInt("version", -1) != latestVersion) continue
            if (lastDecision == null || d.optLong("decidedAt") >= lastDecision.optLong("decidedAt")) lastDecision = d
        }
    }
    lastDecision?.optString("reason")?.trim()?.takeIf { it.isNotEmpty() }?.let {
        entries.add(lastDecision.optLong("decidedAt") to it)
    }
    val comments = plan.optJSONArray("comments")
    if (comments != null) {
        for (i in 0 until comments.length()) {
            val c = comments.optJSONObject(i) ?: continue
            if (c.optInt("version", -1) != latestVersion) continue
            if (ownerId.isNotEmpty() && c.optString("authorId") == ownerId) continue
            val text = c.optString("text").trim()
            if (text.isNotEmpty()) entries.add(c.optLong("createdAt") to text)
        }
    }
    return entries.sortedBy { it.first }.map { it.second }.distinct().takeLast(MAX_FEEDBACK)
}

const val MAX_FEEDBACK = 3

/**
 * Talks to the DhruvAR dashboard server (docs/Web-Dashboard-Plan.md, server/README.md).
 *
 * The app signs in once for a bearer token and sends it with every later request. Nothing here
 * touches the offline planning flow: it is only reached when the user signs in or sends a plan.
 */
object ServerClient {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** The one HTTP client, shared with the picture uploader so timeouts and settings stay the same. */
    internal val httpClient: OkHttpClient get() = client

    suspend fun signIn(baseUrl: String, username: String, password: String): TokenSession {
        val body = JSONObject().put("username", username).put("password", password)
        val json = call(post("$baseUrl/api/auth/token", body, token = null))
        return try {
            TokenSession(token = json.getString("token"), user = parseUser(json.getJSONObject("user")))
        } catch (e: JSONException) {
            throw ServerException(0, "The server sent an unexpected reply")
        }
    }

    suspend fun signOut(baseUrl: String, token: String) {
        // Best effort: the local session is dropped whether or not the server could be reached.
        try {
            call(post("$baseUrl/api/auth/logout", JSONObject(), token))
        } catch (_: ServerException) {
        }
    }

    /** Creates a new plan (version 1). [layoutJson] is exactly what the app saves on the phone. */
    suspend fun submitPlan(baseUrl: String, token: String, layoutJson: String, note: String): SubmittedPlan {
        val body = JSONObject().put("layout", JSONObject(layoutJson)).put("note", note)
        val json = call(post("$baseUrl/api/plans", body, token))
        return try {
            SubmittedPlan(planId = json.getString("id"), code = json.getString("code"))
        } catch (e: JSONException) {
            throw ServerException(0, "The server sent an unexpected reply")
        }
    }

    /** Sends a changed layout as the next version of a plan that was rejected or sent back. */
    suspend fun submitVersion(baseUrl: String, token: String, planId: String, layoutJson: String, note: String) {
        val body = JSONObject().put("layout", JSONObject(layoutJson)).put("note", note)
        call(post("$baseUrl/api/plans/$planId/versions", body, token))
    }

    suspend fun fetchPlanStatuses(baseUrl: String, token: String): List<RemotePlanStatus> {
        val request = Request.Builder().url("$baseUrl/api/plans").header("Authorization", "Bearer $token").get().build()
        val array = callArray(request)
        val out = ArrayList<RemotePlanStatus>(array.length())
        for (i in 0 until array.length()) {
            val p = array.optJSONObject(i) ?: continue
            val versions = p.optJSONArray("versions")
            val last = versions?.optJSONObject((versions.length() - 1).coerceAtLeast(0))
            val latestVersion = last?.optInt("version", 1) ?: 1
            out.add(
                RemotePlanStatus(
                    planId = p.optString("id"),
                    code = p.optString("code"),
                    status = p.optString("status"),
                    latestVersion = latestVersion,
                    layoutId = last?.optJSONObject("layout")?.optString("id")?.takeIf { it.isNotBlank() },
                    feedback = reviewerFeedback(p, latestVersion)
                )
            )
        }
        return out
    }

    private fun parseUser(u: JSONObject) = SignedInUser(
        id = u.getString("id"),
        username = u.getString("username"),
        name = u.getString("name"),
        rank = u.optString("rank"),
        role = u.getString("role")
    )

    private fun post(url: String, body: JSONObject, token: String?): Request {
        val builder = Request.Builder().url(url).post(body.toString().toRequestBody(jsonType))
        if (token != null) builder.header("Authorization", "Bearer $token")
        return builder.build()
    }

    private suspend fun call(request: Request): JSONObject {
        val text = execute(request)
        return try {
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (e: JSONException) {
            throw ServerException(0, "The server sent an unexpected reply")
        }
    }

    private suspend fun callArray(request: Request): org.json.JSONArray {
        val text = execute(request)
        return try {
            org.json.JSONArray(text)
        } catch (e: JSONException) {
            throw ServerException(0, "The server sent an unexpected reply")
        }
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = try {
                        JSONObject(text).optString("error").takeIf { it.isNotBlank() }
                    } catch (_: JSONException) {
                        null
                    }
                    throw ServerException(response.code, message ?: "The server answered with error ${response.code}")
                }
                text
            }
        } catch (e: ServerException) {
            throw e
        } catch (e: IOException) {
            throw ServerException(0, "Cannot reach the server. Check the address and your network.")
        }
    }
}
