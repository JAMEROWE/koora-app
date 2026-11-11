package com.koora.app

import com.google.gson.Gson
import com.koora.app.data.AfFix
import com.koora.app.data.AfFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.6.1 — the API-Football mapper is pure logic (no network, no Android),
 * so every rule that decides what appears in Koora is pinned here:
 * which competitions are imported, how statuses translate, and that ids
 * can never collide with football-data.org ids.
 */
class AfFixturesTest {

    @Test
    fun `whitelisted league names map to Koora codes`() {
        assertEquals("CDR", AfFixtures.codeFor("Copa del Rey"))
        assertEquals("FAC", AfFixtures.codeFor("FA Cup"))
        assertEquals("COP", AfFixtures.codeFor("Coppa Italia"))
        assertEquals("DFB", AfFixtures.codeFor("DFB Pokal"))
        assertEquals("CLG", AfFixtures.codeFor("Coupe de France"))
        assertEquals("UEL", AfFixtures.codeFor("UEFA Europa League"))
        assertEquals("UECL", AfFixtures.codeFor("UEFA Conference League"))
        assertEquals("BOT", AfFixtures.codeFor("Botola Pro"))
    }

    @Test
    fun `non-whitelisted or ambiguous leagues are skipped`() {
        assertNull(AfFixtures.codeFor("Premier League"))
        assertNull(AfFixtures.codeFor("Ligue 1"))
        assertNull(AfFixtures.codeFor("FA Community Shield"))
        assertNull(AfFixtures.codeFor("UEFA Europa League Qualification"))
        assertNull(AfFixtures.codeFor("Women's FA Cup"))
        assertNull(AfFixtures.codeFor("Club Friendly Games"))
        assertNull(AfFixtures.codeFor(null))
        assertNull(AfFixtures.codeFor(""))
    }

    @Test
    fun `api-football statuses translate to football-data style`() {
        assertEquals("FINISHED", AfFixtures.statusOf("FT"))
        assertEquals("FINISHED", AfFixtures.statusOf("AET"))
        assertEquals("FINISHED", AfFixtures.statusOf("PEN"))
        assertEquals("IN_PLAY", AfFixtures.statusOf("1H"))
        assertEquals("IN_PLAY", AfFixtures.statusOf("HT"))
        assertEquals("IN_PLAY", AfFixtures.statusOf("2H"))
        assertEquals("IN_PLAY", AfFixtures.statusOf("LIVE"))
        assertEquals("POSTPONED", AfFixtures.statusOf("PST"))
        assertEquals("SUSPENDED", AfFixtures.statusOf("ABD"))
        assertEquals("CANCELLED", AfFixtures.statusOf("CANC"))
        assertEquals("TIMED", AfFixtures.statusOf("NS"))
        assertEquals("TIMED", AfFixtures.statusOf("TBD"))
        assertEquals("TIMED", AfFixtures.statusOf(null))
        assertEquals("TIMED", AfFixtures.statusOf("SOMETHING_NEW"))
    }

    @Test
    fun `iso dates normalize to the Z format the parser uses`() {
        assertEquals("2026-11-12T20:00:00Z", AfFixtures.normalizeDate("2026-11-12T20:00:00+00:00"))
        assertEquals("2026-11-12T20:00:00Z", AfFixtures.normalizeDate("2026-11-12T20:00:00Z"))
        assertEquals("2026-11-12T20:00:00Z", AfFixtures.normalizeDate("2026-11-12T20:00:00.000+00:00"))
    }

    @Test
    fun `full fixture json maps into the Match model`() {
        val json = """
            {"fixture":{"id":1035048,"date":"2026-11-12T20:00:00+00:00",
              "status":{"short":"NS","elapsed":null},
              "venue":{"name":"San Mames"},"referee":null},
             "league":{"id":143,"name":"Copa del Rey","country":"Spain",
              "logo":"l.png","round":"1st Round","season":2026},
             "teams":{"home":{"id":531,"name":"Athletic Club","logo":"h.png"},
              "away":{"id":532,"name":"Valencia","logo":"a.png"}},
             "goals":{"home":null,"away":null},
             "score":{"halftime":{"home":null,"away":null},
              "fulltime":{"home":null,"away":null}}}
        """.trimIndent()
        val m = AfFixtures.toMatch(Gson().fromJson(json.trim(), AfFix::class.java))
        assertNotNull(m)
        assertEquals(50_000_000L + 1035048, m!!.id)          // offset: never collides with fd ids
        assertEquals("TIMED", m.status)
        assertEquals("2026-11-12T20:00:00Z", m.utcDate)
        assertEquals("CDR", m.competition?.code)
        assertEquals("Copa del Rey", m.competition?.name)
        assertEquals(143L, m.competition?.id)
        assertEquals("Athletic Club", m.homeTeam.name)
        assertEquals(50_000_000L + 531, m.homeTeam.id)
        assertNotNull(m.homeTeam.tla)
        assertEquals("Valencia", m.awayTeam.name)
        assertNull(m.score.fullTime?.home)
    }

    @Test
    fun `finished fixture carries the score`() {
        val json = """
            {"fixture":{"id":9,"date":"2026-11-10T18:00:00+00:00","status":{"short":"FT"}},
             "league":{"id":281,"name":"Botola Pro","country":"Morocco"},
             "teams":{"home":{"id":951,"name":"Raja Club Athletic"},
              "away":{"id":968,"name":"Wydad AC"}},
             "goals":{"home":2,"away":1}}
        """.trimIndent()
        val m = AfFixtures.toMatch(Gson().fromJson(json.trim(), AfFix::class.java))
        assertNotNull(m)
        assertEquals("BOT", m!!.competition?.code)
        assertEquals("FINISHED", m.status)
        assertEquals(2, m.score.fullTime?.home)
        assertEquals(1, m.score.fullTime?.away)
        assertEquals("RCA", m.homeTeam.tla)  // three words -> 3 initials
        assertEquals("WA", m.awayTeam.tla)   // two words -> initials
    }

    @Test
    fun `broken fixtures never crash the mapper`() {
        assertNull(AfFixtures.toMatch(AfFix()))                 // all-null DTO
        val noLeague = Gson().fromJson(
            """{"fixture":{"id":1,"date":"2026-11-12T20:00:00+00:00","status":{"short":"NS"}},
                "teams":{"home":{"id":1,"name":"A"},"away":{"id":2,"name":"B"}}}""",
            AfFix::class.java
        )
        assertNull(AfFixtures.toMatch(noLeague))
    }
}
