package com.example.dhruvar

import com.example.dhruvar.data.map.PlaceSearchResponseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PlaceSearchResponseParser].
 *
 * Payloads are real responses captured from the India map server on 2026-10-03, trimmed. Note the
 * `name_en` asymmetry: `Delhi` omits it because `name` is already Latin, while `Muzaffarabad`
 * carries an Urdu `name` plus `name_en`. Both must label correctly.
 */
class PlaceSearchResponseParserTest {

    private val delhiResponse = """
        {
          "hits": [
            {
              "id": "n316416994",
              "name": "Delhi",
              "names": ["Dehli", "Deli"],
              "kind": "place",
              "subtype": "state",
              "rank": 1,
              "_geo": {"lat": 28.632803, "lng": 77.219771}
            },
            {
              "id": "n554257763",
              "name": "Delhi Aerocity",
              "names": ["दिल्ली एरोसिटी"],
              "kind": "railway",
              "subtype": "station",
              "rank": 9,
              "_geo": {"lat": 28.548779, "lng": 77.120795}
            }
          ],
          "query": "delhi",
          "processingTimeMs": 72,
          "limit": 20,
          "offset": 0,
          "estimatedTotalHits": 1042,
          "requestUid": "01a1001d-e4f1-7132-b186-f21a11c3c015"
        }
    """.trimIndent()

    private val muzaffarabadResponse = """
        {
          "hits": [
            {
              "id": "n212677583",
              "name": "مظفر آباد",
              "names": ["Muzafarabad", "Muzaffarabad"],
              "kind": "place",
              "subtype": "city",
              "rank": 1,
              "_geo": {"lat": 34.373475, "lng": 73.470241},
              "name_en": "Muzaffarabad"
            }
          ],
          "query": "muzaffarabad",
          "estimatedTotalHits": 8
        }
    """.trimIndent()

    @Test
    fun parsesEveryFieldOfTheFirstHit() {
        val hit = PlaceSearchResponseParser.parse(delhiResponse).first()

        assertEquals("n316416994", hit.id)
        assertEquals("Delhi", hit.name)
        assertEquals("place", hit.kind)
        assertEquals("state", hit.subtype)
        assertEquals(1, hit.rank)
        assertEquals(28.632803, hit.latitude, 1e-9)
        assertEquals(77.219771, hit.longitude, 1e-9)
    }

    @Test
    fun hitOrderFromTheServerIsPreserved() {
        val hits = PlaceSearchResponseParser.parse(delhiResponse)

        assertEquals(listOf("Delhi", "Delhi Aerocity"), hits.map { it.displayName })
    }

    @Test
    fun latinNameIsUsedWhenTheServerSendsNoEnglishName() {
        val hit = PlaceSearchResponseParser.parse(delhiResponse).first()

        assertNull(hit.englishName)
        assertEquals("Delhi", hit.displayName)
    }

    @Test
    fun englishNameIsPreferredOverNonLatinLocalName() {
        val hit = PlaceSearchResponseParser.parse(muzaffarabadResponse).single()

        assertEquals("مظفر آباد", hit.name)
        assertEquals("Muzaffarabad", hit.englishName)
        assertEquals("Muzaffarabad", hit.displayName)
    }

    @Test
    fun displayCategoryPrefersTheMoreSpecificSubtype() {
        val hits = PlaceSearchResponseParser.parse(delhiResponse)

        assertEquals(listOf("state", "station"), hits.map { it.displayCategory })
    }

    @Test
    fun hitsWithoutCoordinatesAreSkipped() {
        val json = """
            {"hits": [
              {"id": "n1", "name": "No geo", "kind": "place"},
              {"id": "n2", "name": "Has geo", "_geo": {"lat": 28.6, "lng": 77.2}}
            ]}
        """.trimIndent()

        val hits = PlaceSearchResponseParser.parse(json)

        assertEquals(listOf("n2"), hits.map { it.id })
    }

    @Test
    fun hitsWithPartialOrNonNumericCoordinatesAreSkipped() {
        val json = """
            {"hits": [
              {"id": "n1", "name": "Lat only", "_geo": {"lat": 28.6}},
              {"id": "n2", "name": "Not a number", "_geo": {"lat": "abc", "lng": "def"}}
            ]}
        """.trimIndent()

        assertTrue(PlaceSearchResponseParser.parse(json).isEmpty())
    }

    @Test
    fun namelessHitsAreSkipped() {
        val json = """
            {"hits": [{"id": "n1", "name": "  ", "_geo": {"lat": 28.6, "lng": 77.2}}]}
        """.trimIndent()

        assertTrue(PlaceSearchResponseParser.parse(json).isEmpty())
    }

    @Test
    fun emptyResultSetParsesToEmptyList() {
        val json = """{"hits": [], "query": "zzzz", "estimatedTotalHits": 0}"""

        assertTrue(PlaceSearchResponseParser.parse(json).isEmpty())
    }

    @Test
    fun malformedPayloadYieldsEmptyListRatherThanThrowing() {
        assertTrue(PlaceSearchResponseParser.parse("<html>502 Bad Gateway</html>").isEmpty())
        assertTrue(PlaceSearchResponseParser.parse("").isEmpty())
        assertTrue(PlaceSearchResponseParser.parse("""{"error": "nope"}""").isEmpty())
    }
}
