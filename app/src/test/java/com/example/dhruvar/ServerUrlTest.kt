package com.example.dhruvar

import com.example.dhruvar.data.remote.ServerUrl
import com.example.dhruvar.data.remote.ServerUrlCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {

    private fun ok(raw: String, cleartext: Boolean): String {
        val result = ServerUrl.normalize(raw, cleartext)
        assertTrue("expected Ok for '$raw' but was $result", result is ServerUrlCheck.Ok)
        return (result as ServerUrlCheck.Ok).baseUrl
    }

    private fun bad(raw: String, cleartext: Boolean): String {
        val result = ServerUrl.normalize(raw, cleartext)
        assertTrue("expected Bad for '$raw' but was $result", result is ServerUrlCheck.Bad)
        return (result as ServerUrlCheck.Bad).reason
    }

    @Test
    fun `release builds accept https and add it when the scheme is missing`() {
        assertEquals("https://dash.example.org", ok("https://dash.example.org", cleartext = false))
        assertEquals("https://dash.example.org", ok("dash.example.org", cleartext = false))
    }

    @Test
    fun `release builds refuse plain http`() {
        assertTrue(bad("http://192.168.1.214:4000", cleartext = false).contains("https"))
    }

    @Test
    fun `debug builds accept plain http and default a bare address to http`() {
        assertEquals("http://192.168.1.214:4000", ok("http://192.168.1.214:4000", cleartext = true))
        assertEquals("http://192.168.1.214:4000", ok("192.168.1.214:4000", cleartext = true))
    }

    @Test
    fun `a pasted path, query, fragment and credentials are dropped`() {
        assertEquals(
            "https://dash.example.org:8443",
            ok("  https://user:pw@dash.example.org:8443/plans/12?x=1#top  ", cleartext = false)
        )
    }

    @Test
    fun `trailing slash is removed so request paths join cleanly`() {
        assertEquals("https://dash.example.org", ok("https://dash.example.org/", cleartext = false))
    }

    @Test
    fun `map address is the server address plus slash map`() {
        assertEquals("http://localhost:8000/map", ServerUrl.mapBaseFor("http://localhost:8000"))
        assertEquals("http://192.168.1.42:8000/map", ServerUrl.mapBaseFor("http://192.168.1.42:8000/"))
        assertEquals(null, ServerUrl.mapBaseFor("  "))
    }

    @Test
    fun `typed map address keeps its path and drops the trailing slash`() {
        fun map(raw: String, cleartext: Boolean) =
            (ServerUrl.normalizeMap(raw, cleartext) as ServerUrlCheck.Ok).baseUrl
        assertEquals("http://localhost:8000/map", map("http://localhost:8000/map/", true))
        assertEquals("http://192.168.1.42:8000/map", map("192.168.1.42:8000/map", true))
        assertEquals("https://maps.example.org/map", map("maps.example.org/map", false))
    }

    @Test
    fun `typed map address rejects empty, malformed and release http`() {
        assertTrue(ServerUrl.normalizeMap("", true) is ServerUrlCheck.Bad)
        assertTrue(ServerUrl.normalizeMap("ht tp://bad host", true) is ServerUrlCheck.Bad)
        assertTrue(ServerUrl.normalizeMap("http://localhost:8000/map", false) is ServerUrlCheck.Bad)
    }

    @Test
    fun `empty and malformed addresses are rejected`() {
        bad("", cleartext = true)
        bad("   ", cleartext = true)
        bad("http://", cleartext = true)
        bad("ht tp://bad host", cleartext = true)
    }
}
