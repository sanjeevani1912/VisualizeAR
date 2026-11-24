package com.example.dhruvar

import com.example.dhruvar.data.remote.AttachmentUploader
import com.example.dhruvar.data.remote.SendEligibility
import com.example.dhruvar.data.remote.SentPlan
import com.example.dhruvar.data.remote.UploadProblem
import com.example.dhruvar.data.remote.UploadResult
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The picture uploader against a fake server: what it sends, and what it says for each failure. */
class AttachmentUploaderTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder().readTimeout(3, TimeUnit.SECONDS).build()
    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3, 4)

    @Before fun start() { server = MockWebServer().apply { start() } }
    @After fun stop() { server.shutdown() }

    private fun base() = server.url("/").toString().trimEnd('/')

    private fun send(): UploadResult =
        AttachmentUploader.upload(base(), "tok-abc", "plan-7", jpegBytes, "Panorama (phone) 2026-11-24 14:05", "DHRUV_Plan_1.jpg", client)

    @Test fun successReturnsTheAttachmentId() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"att-1"}"""))
        assertEquals(UploadResult.Success("att-1"), send())
    }

    @Test fun requestShapeMatchesTheServerContract() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"att-1"}"""))
        send()
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        assertEquals("/api/plans/plan-7/attachments", r.path)
        assertEquals("Bearer tok-abc", r.getHeader("Authorization"))
        assertTrue(r.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = r.body.readByteString().utf8()
        assertTrue(body.contains("name=\"file\"; filename=\"DHRUV_Plan_1.jpg\""))
        assertTrue(body.contains("Content-Type: image/jpeg"))
        assertTrue(body.contains("name=\"caption\""))
        assertTrue(body.contains("Panorama (phone) 2026-11-24 14:05"))
    }

    @Test fun fileBytesArriveUnchanged() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"a"}"""))
        send()
        val raw = server.takeRequest().body.readByteArray()
        val at = indexOf(raw, jpegBytes)
        assertTrue("file bytes not found in body", at >= 0)
        assertArrayEquals(jpegBytes, raw.copyOfRange(at, at + jpegBytes.size))
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun failureOf(code: Int, body: String): UploadResult.Failure {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
        return send() as UploadResult.Failure
    }

    @Test fun status401MeansSignInAgain() =
        assertEquals(UploadProblem.NOT_SIGNED_IN, failureOf(401, """{"error":"Not signed in"}""").problem)

    @Test fun expiredTokenArrivesAs403MissingRequestHeader() =
        assertEquals(UploadProblem.NOT_SIGNED_IN, failureOf(403, """{"error":"Missing request header"}""").problem)

    @Test fun realForbiddenMeansNotAllowed() =
        assertEquals(UploadProblem.NOT_ALLOWED, failureOf(403, """{"error":"You cannot add images to this plan"}""").problem)

    @Test fun status413MeansTooLarge() =
        assertEquals(UploadProblem.TOO_LARGE, failureOf(413, """{"error":"Image is too large"}""").problem)

    @Test fun status409And415AreNamed() {
        assertEquals(UploadProblem.TOO_MANY, failureOf(409, """{"error":"x"}""").problem)
        assertEquals(UploadProblem.BAD_TYPE, failureOf(415, """{"error":"x"}""").problem)
    }

    @Test fun unknownStatusKeepsTheNumber() {
        val f = failureOf(500, "oops not json")
        assertEquals(UploadProblem.OTHER, f.problem)
        assertTrue(f.message.contains("500"))
    }

    @Test fun unreachableServerIsReported() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertEquals(UploadProblem.UNREACHABLE, (send() as UploadResult.Failure).problem)
        val dead = base()
        server.shutdown()
        val r = AttachmentUploader.upload(dead, "t", "p", jpegBytes, "c", "f.jpg", client)
        assertEquals(UploadProblem.UNREACHABLE, (r as UploadResult.Failure).problem)
    }

    @Test fun badSuccessBodyIsNotASuccess() {
        server.enqueue(MockResponse().setResponseCode(201).setBody("<html>"))
        assertTrue(send() is UploadResult.Failure)
    }

    @Test fun hugeFileIsRefusedWithoutCallingTheServer() {
        val big = ByteArray(AttachmentUploader.MAX_UPLOAD_BYTES + 1024 * 1024)
        val r = AttachmentUploader.upload(base(), "t", "p", big, "c", "f.jpg", client)
        assertEquals(UploadProblem.TOO_LARGE, (r as UploadResult.Failure).problem)
        assertEquals(0, server.requestCount)
    }

    @Test fun sizeGuardPicksTheFirstQualityThatFits() {
        val sizes = mapOf(95 to 12_000_000, 85 to 9_500_000, 75 to 8_000_000, 65 to 6_000_000)
        val tried = ArrayList<Int>()
        val out = AttachmentUploader.encodeWithin(AttachmentUploader.MAX_UPLOAD_BYTES, AttachmentUploader.QUALITY_STEPS) { q ->
            tried.add(q); ByteArray(sizes.getValue(q))
        }
        assertEquals(8_000_000, out!!.size)
        assertEquals(listOf(95, 85, 75), tried)
    }

    @Test fun sizeGuardGivesUpWhenNothingFits() {
        assertNull(AttachmentUploader.encodeWithin(100, AttachmentUploader.QUALITY_STEPS) { ByteArray(101) })
    }

    @Test fun sizeGuardKeepsFirstWhenItAlreadyFits() {
        var calls = 0
        assertNotNull(AttachmentUploader.encodeWithin(100, AttachmentUploader.QUALITY_STEPS) { calls++; ByteArray(10) })
        assertEquals(1, calls)
    }

    @Test fun captionsNameThePictureKindAndTime() {
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals("Panorama (phone) 2023-11-14 22:13", AttachmentUploader.defaultCaption(true, 1700000000000L, utc))
        assertEquals("AR view (phone) 2023-11-14 22:13", AttachmentUploader.defaultCaption(false, 1700000000000L, utc))
    }

    private fun plan(status: String) = SentPlan("plan-9", "TP-2026-0009", 1, status, 0L)

    @Test fun sendIsOfferedOnlyWhenSignedInAndPlanSent() {
        assertTrue(AttachmentUploader.eligibility(false, plan("SUBMITTED")) is SendEligibility.Blocked)
        assertTrue(AttachmentUploader.eligibility(true, null) is SendEligibility.Blocked)
        assertTrue(AttachmentUploader.eligibility(true, plan("WITHDRAWN")) is SendEligibility.Blocked)
        assertEquals(SendEligibility.Ready("plan-9", "TP-2026-0009"), AttachmentUploader.eligibility(true, plan("UNDER_REVIEW")))
    }
}
