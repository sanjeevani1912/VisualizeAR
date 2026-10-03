package com.example.dhruvar

import com.example.dhruvar.data.map.MapStyleRewriter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MapStyleRewriter].
 *
 * Fixtures are trimmed from the live India map server, which advertises
 * `http://localhost:8080` in both `style.json` and the TileJSON it points at. Rewriting only the
 * style is insufficient — hence the inlining assertions.
 */
class MapStyleRewriterTest {

    private val base = "http://192.168.1.205:8080"

    private val serverStyle = """
        {
          "version": 8,
          "name": "India Offline",
          "metadata": {"note": "Replace http://localhost:8080 with the public base URL"},
          "center": [78.9, 22.5],
          "zoom": 4,
          "sources": {
            "openmaptiles": {
              "type": "vector",
              "url": "http://localhost:8080/tiles/india.json"
            }
          },
          "glyphs": "http://localhost:8080/fonts/{fontstack}/{range}.pbf",
          "layers": [
            {"id": "background", "type": "background", "paint": {"background-color": "#f2efe9"}}
          ]
        }
    """.trimIndent()

    private val serverTileJson = """
        {
          "tilejson": "2.2.0",
          "tiles": ["http://localhost:8080/tiles/india/{z}/{x}/{y}.mvt"],
          "minzoom": 0,
          "maxzoom": 14,
          "bounds": [-180, -85.05, 180, 85.05],
          "attribution": "© OpenStreetMap contributors"
        }
    """.trimIndent()

    private fun source(styleJson: String, name: String = "openmaptiles"): JSONObject =
        JSONObject(styleJson).getJSONObject("sources").getJSONObject(name)

    @Test
    fun glyphsAreRepointedAtTheReachableBase() {
        val rewritten = MapStyleRewriter.rewrite(serverStyle, serverTileJson, base)

        assertEquals(
            "$base/fonts/{fontstack}/{range}.pbf",
            JSONObject(rewritten).getString("glyphs")
        )
    }

    @Test
    fun tileTemplatesAreInlinedSoTheTileJsonIsNeverFetched() {
        val rewritten = MapStyleRewriter.rewrite(serverStyle, serverTileJson, base)
        val source = source(rewritten)

        assertFalse("TileJSON reference must be gone", source.has("url"))
        assertEquals(
            "$base/tiles/india/{z}/{x}/{y}.mvt",
            source.getJSONArray("tiles").getString(0)
        )
    }

    @Test
    fun inlinedSourceInheritsZoomRangeAndAttributionFromTileJson() {
        val source = source(MapStyleRewriter.rewrite(serverStyle, serverTileJson, base))

        assertEquals(0, source.getInt("minzoom"))
        assertEquals(14, source.getInt("maxzoom"))
        assertEquals("© OpenStreetMap contributors", source.getString("attribution"))
    }

    @Test
    fun noPlaceholderSurvivesAnywhereInTheDocument() {
        val rewritten = MapStyleRewriter.rewrite(serverStyle, serverTileJson, base)

        assertFalse(rewritten.contains(MapStyleRewriter.PLACEHOLDER_BASE))
    }

    @Test
    fun loopbackVariantsAreAlsoRepointed() {
        val style = serverStyle.replace("http://localhost:8080", "http://127.0.0.1:8080")

        val rewritten = MapStyleRewriter.rewrite(style, serverTileJson, base)

        assertFalse(rewritten.contains("127.0.0.1"))
        assertEquals(
            "$base/fonts/{fontstack}/{range}.pbf",
            JSONObject(rewritten).getString("glyphs")
        )
    }

    /** Applying the server-side fix must not break the client-side one. */
    @Test
    fun alreadyCorrectStyleStillGetsTilesInlined() {
        val fixedStyle = serverStyle.replace(MapStyleRewriter.PLACEHOLDER_BASE, base)
        val fixedTileJson = serverTileJson.replace(MapStyleRewriter.PLACEHOLDER_BASE, base)

        val source = source(MapStyleRewriter.rewrite(fixedStyle, fixedTileJson, base))

        assertFalse(source.has("url"))
        assertEquals(
            "$base/tiles/india/{z}/{x}/{y}.mvt",
            source.getJSONArray("tiles").getString(0)
        )
    }

    @Test
    fun rewriteIsIdempotent() {
        val once = MapStyleRewriter.rewrite(serverStyle, serverTileJson, base)
        val twice = MapStyleRewriter.rewrite(once, serverTileJson, base)

        assertEquals(source(once).toString(), source(twice).toString())
    }

    @Test
    fun sourcesOnOtherHostsAreLeftAlone() {
        val style = JSONObject(serverStyle).apply {
            getJSONObject("sources").put(
                "thirdParty",
                JSONObject().put("type", "vector").put("url", "https://example.com/other.json")
            )
        }.toString()

        val rewritten = MapStyleRewriter.rewrite(style, serverTileJson, base)
        val thirdParty = source(rewritten, "thirdParty")

        assertEquals("https://example.com/other.json", thirdParty.getString("url"))
        assertFalse(thirdParty.has("tiles"))
    }

    @Test
    fun missingTileJsonLeavesTheRepointedReferenceInPlace() {
        val rewritten = MapStyleRewriter.rewrite(serverStyle, tileJson = null, serverBase = base)
        val source = source(rewritten)

        assertEquals("$base/tiles/india.json", source.getString("url"))
        assertFalse(source.has("tiles"))
    }

    @Test
    fun blankBaseReturnsStyleUntouched() {
        assertEquals(serverStyle, MapStyleRewriter.rewrite(serverStyle, serverTileJson, "   "))
    }

    @Test
    fun trailingSlashOnBaseDoesNotDoubleUp() {
        val rewritten = MapStyleRewriter.rewrite(serverStyle, serverTileJson, "$base/")

        assertTrue(rewritten.contains("$base/fonts/"))
        assertFalse(rewritten.contains("$base//"))
    }

    @Test
    fun unparseableStyleIsReturnedUnchangedRatherThanThrowing() {
        val malformed = "{ this is not json"

        assertEquals(malformed, MapStyleRewriter.rewrite(malformed, serverTileJson, base))
    }

    @Test
    fun unparseableTileJsonDegradesToRepointingOnly() {
        val rewritten = MapStyleRewriter.rewrite(serverStyle, "<html>502</html>", base)
        val source = source(rewritten)

        assertEquals("$base/tiles/india.json", source.getString("url"))
        assertNull(source.opt("tiles"))
    }
}
