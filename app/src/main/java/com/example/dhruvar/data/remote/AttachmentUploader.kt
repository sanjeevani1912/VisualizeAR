package com.example.dhruvar.data.remote

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject

/** Why an upload did not work. Each has a plain-words [UploadResult.Failure.message]. */
enum class UploadProblem { NOT_SIGNED_IN, PLAN_NOT_SENT, TOO_LARGE, NOT_ALLOWED, TOO_MANY, BAD_TYPE, UNREACHABLE, OTHER }

sealed interface UploadResult {
    data class Success(val attachmentId: String) : UploadResult
    data class Failure(val problem: UploadProblem, val message: String) : UploadResult
}

/** Whether "Send to plan" can be pressed, and if not, the one-line reason to show. */
sealed interface SendEligibility {
    data class Ready(val planId: String, val planCode: String) : SendEligibility
    data class Blocked(val reason: String) : SendEligibility
}

/**
 * Sends a picture to a plan on the dashboard server as a "field image".
 *
 * Server rules (docs/PANORAMA-FEATURE.md): `POST /api/plans/<planId>/attachments`, bearer token,
 * multipart with the file in a part named `file` and an optional `caption`; JPEG/PNG/WebP up to 10 MB.
 * This never touches the saved gallery copy and never throws for ordinary failures: it returns a result.
 */
object AttachmentUploader {
    const val MAX_UPLOAD_BYTES = 9 * 1024 * 1024 // server limit is 10 MB; leave room for the form headers
    val QUALITY_STEPS = intArrayOf(95, 85, 75, 65)
    private val jpeg = "image/jpeg".toMediaType()

    fun eligibility(signedIn: Boolean, sent: SentPlan?): SendEligibility = when {
        !signedIn -> SendEligibility.Blocked("Sign in first to send pictures to a plan")
        sent == null -> SendEligibility.Blocked("Send this plan for approval first, then send pictures")
        sent.status == "WITHDRAWN" -> SendEligibility.Blocked("${sent.code} was withdrawn and takes no pictures")
        else -> SendEligibility.Ready(sent.planId, sent.code)
    }

    /** "Panorama (phone) 2026-11-24 14:05" or "AR view (phone) ...": well under the server's 200 characters. */
    fun defaultCaption(isPanorama: Boolean, timeMillis: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = zone }
        return "${if (isPanorama) "Panorama" else "AR view"} (phone) ${format.format(Date(timeMillis))}"
    }

    /**
     * Tries each quality in turn (highest first) and returns the first result that fits in [maxBytes],
     * or null when even the lowest does not. [encode] is given a quality and returns the JPEG bytes.
     */
    fun encodeWithin(maxBytes: Int, qualities: IntArray, encode: (Int) -> ByteArray): ByteArray? {
        for (q in qualities) {
            val bytes = encode(q)
            if (bytes.size <= maxBytes) return bytes
        }
        return null
    }

    /** Compresses [bitmap] (the bitmap itself is not copied or recycled) and uploads it. */
    suspend fun sendBitmap(
        baseUrl: String,
        token: String,
        planId: String,
        bitmap: Bitmap,
        caption: String,
        fileName: String,
        client: OkHttpClient = ServerClient.httpClient
    ): UploadResult = withContext(Dispatchers.IO) {
        val bytes = encodeWithin(MAX_UPLOAD_BYTES, QUALITY_STEPS) { q ->
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, q, out)
            out.toByteArray()
        } ?: return@withContext failure(UploadProblem.TOO_LARGE)
        upload(baseUrl, token, planId, bytes, caption, fileName, client)
    }

    /** Uploads ready-made JPEG [jpegBytes]. Blocking: call it off the main thread. */
    fun upload(
        baseUrl: String,
        token: String,
        planId: String,
        jpegBytes: ByteArray,
        caption: String,
        fileName: String,
        client: OkHttpClient = ServerClient.httpClient
    ): UploadResult {
        if (jpegBytes.size > MAX_UPLOAD_BYTES + 512 * 1024) return failure(UploadProblem.TOO_LARGE)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("caption", caption.take(200))
            .addFormDataPart("file", fileName, jpegBytes.toRequestBody(jpeg))
            .build()
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/plans/$planId/attachments")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val id = try {
                        JSONObject(text).getString("id")
                    } catch (_: JSONException) {
                        null
                    }
                    if (id != null) UploadResult.Success(id) else failure(UploadProblem.OTHER, "The server sent an unexpected reply")
                } else {
                    val serverText = try {
                        JSONObject(text).optString("error")
                    } catch (_: JSONException) {
                        ""
                    }
                    failureFor(response.code, serverText)
                }
            }
        } catch (_: IOException) {
            failure(UploadProblem.UNREACHABLE)
        }
    }

    /**
     * Maps the server's answer to plain words. Note: a missing or expired token on a POST is answered
     * 403 "Missing request header" (not 401) by this server, so that text also means "sign in again".
     */
    fun failureFor(status: Int, serverText: String): UploadResult.Failure = when {
        status == 401 || (status == 403 && serverText.contains("Missing request header", ignoreCase = true)) ->
            failure(UploadProblem.NOT_SIGNED_IN)
        status == 403 -> failure(UploadProblem.NOT_ALLOWED)
        status == 404 -> failure(UploadProblem.PLAN_NOT_SENT, "The plan was not found on the server. Send the plan for approval first.")
        status == 409 -> failure(UploadProblem.TOO_MANY)
        status == 413 -> failure(UploadProblem.TOO_LARGE)
        status == 415 -> failure(UploadProblem.BAD_TYPE)
        else -> failure(UploadProblem.OTHER, "The server answered with error $status.")
    }

    private fun failure(problem: UploadProblem, message: String? = null) =
        UploadResult.Failure(problem, message ?: defaultMessage(problem))

    private fun defaultMessage(p: UploadProblem) = when (p) {
        UploadProblem.NOT_SIGNED_IN -> "Not signed in. Sign in again, then retry."
        UploadProblem.PLAN_NOT_SENT -> "Send this plan for approval first."
        UploadProblem.TOO_LARGE -> "Picture is too large to send (limit 10 MB)."
        UploadProblem.NOT_ALLOWED -> "This plan does not accept pictures (not your plan, or it was withdrawn)."
        UploadProblem.TOO_MANY -> "This plan already has 30 pictures."
        UploadProblem.BAD_TYPE -> "The server does not accept this picture type."
        UploadProblem.UNREACHABLE -> "Cannot reach the server. Check the USB link or network, then retry."
        UploadProblem.OTHER -> "The picture could not be sent."
    }
}
