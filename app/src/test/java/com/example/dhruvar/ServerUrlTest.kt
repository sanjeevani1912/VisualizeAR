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
    fun `empty and malformed addresses are rejected`() {
        bad("", cleartext = true)
        bad("   ", cleartext = true)
        bad("http://", cleartext = true)
        bad("ht tp://bad host", cleartext = true)
    }
}
