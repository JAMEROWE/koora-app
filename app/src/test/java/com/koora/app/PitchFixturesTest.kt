package com.koora.app

import com.google.gson.Gson
import com.koora.app.data.PitchFix
import com.koora.app.data.PitchFixtures
import com.koora.app.data.PitchResp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.7.0 — the PitchAPI mapper is pure logic (no network, no Android),
 * so every rule that decides what appears in Koora is pinned here:
 * only whitelisted league IDs are imported (football-data.org keeps its
 * 12 competitions — no double import), statuses translate safely, ids
 * live in their own 80M+ space and can never collide with the other
 * two sources, and incomplete matches are dropped (no-fake-data rule).
 */
class PitchFixturesTest {

    @Test
    fun `whitelisted league ids map to Koora codes`() {
        assertEquals("UEL", PitchFixtures.codeFor("l_38d9HA"))
        assertEquals("UECL", PitchFixtures.codeFor("l_2WZ2tt"))
        assertEquals("SPL", PitchFixtures.codeFor("l_0Lpz1f"))
        assertEquals("TUR", PitchFixtures.codeFor("l_0S1uaf"))
        assertEquals("ARG", PitchFixtures.codeFor("l_45VZuL"))
        assertEquals("MEX", PitchFixtures.codeFor("l_3v84VE"))
        assertEquals("MLS", PitchFixtures.codeFor("l_0eg6C9"))
        assertEquals("SCO", PitchFixtures.codeFor("l_1LMdEO"))
        assertEquals("BEL", PitchFixtures.codeFor("l_2L6d1F"))
        assertEquals("JPN", PitchFixtures.codeFor("l_2ApGa4"))
        assertEquals("KOR", PitchFixtures.codeFor("l_2EKrnc"))
    }

    @Test
    fun `football-data covered leagues are never double-imported`() {
        // Same names football-data.org already serves — IDs not in whitelist.
        assertNull(PitchFixtures.codeFor("l_0bfbkO"))  // Champions League
        assertNull(PitchFixtures.codeFor("l_3eDMDN"))  // some "Serie A"
        assertNull(PitchFixtures.codeFor("l_0aEfof"))  // UAE "Pro League" — not Belgium
        assertNull(PitchFixtures.codeFor(null))
        assertNull(PitchFixtures.codeFor(""))
        assertNull(PitchFixtures.codeFor("l_unknown"))
    }

    @Test
    fun `pitchapi statuses translate to football-data style`() {
        assertEquals("FINISHED", PitchFixtures.statusOf("finished"))
        assertEquals("TIMED", PitchFixtures.statusOf("not_started"))
        assertEquals("IN_PLAY", PitchFixtures.statusOf("in_progress"))
        assertEquals("IN_PLAY", PitchFixtures.statusOf("first_half"))
        assertEquals("POSTPONED", PitchFixtures.statusOf("postponed"))
        assertEquals("SUSPENDED", PitchFixtures.statusOf("suspended"))
        assertEquals("CANCELLED", PitchFixtures.statusOf("cancelled"))
        assertEquals("TIMED", PitchFixtures.statusOf("brand_new_status"))
        assertEquals("TIMED", PitchFixtures.statusOf(null))
        assertEquals("TIMED", PitchFixtures.statusOf(""))
    }

    @Test
    fun `ids are deterministic and live in their own space`() {
        val a = PitchFixtures.matchIdFrom("m_3AQ6Oe")
        val b = PitchFixtures.matchIdFrom("m_3AQ6Oe")
        assertEquals(a, b)
        assertTrue(a in 80_000_000L..90_000_000L)
        val t = PitchFixtures.teamIdFrom("t_15TWxO")
        assertTrue(t in 90_000_000L..100_000_000L)
        // football-data ids stay far below 50M (its season match ids are small)
        assertTrue(a > 80_000_000L)
    }

    @Test
    fun `full day json maps, filters and scores correctly`() {
        val json = """
        {"data":{"date":"2026-11-05","matches":[
          {"id":"m_testUEL","league":{"id":"l_38d9HA","name":"Europa League"},
           "home_team":{"id":"t_home1","name":"Real Sociedad"},"away_team":{"id":"t_away1","name":"Fenerbahce"},
           "time_utc":"2026-11-05T20:00:00Z","status":"finished","score_home":2,"score_away":1},
          {"id":"m_testUECL","league":{"id":"l_2WZ2tt","name":"Conference League"},
           "home_team":{"id":"t_home2","name":"Crystal Palace"},"away_team":{"id":"t_away2","name":"Djurgardens"},
           "time_utc":"2026-11-05T17:45:00Z","status":"not_started","score_home":null,"score_away":null},
          {"id":"m_testSA","league":{"id":"l_4e39df","name":"Serie A"},
           "home_team":{"id":"t_home3","name":"Barcelona"},"away_team":{"id":"t_away3","name":"Libertad"},
           "time_utc":"2026-11-05T00:00:00Z","status":"finished","score_home":3,"score_away":0},
          {"id":"m_testNoLg","league":null,
           "home_team":{"id":"t_home4","name":"Ghost FC"},"away_team":{"id":"t_away4","name":"Other FC"},
           "time_utc":"2026-11-05T21:00:00Z","status":"finished","score_home":1,"score_away":1}
        ]}}
        """.trimIndent()
        val parsed = Gson().fromJson(json, PitchResp::class.java)
        assertEquals(4, parsed.data?.matches?.size)
        val mapped = parsed.data!!.matches.mapNotNull { PitchFixtures.toMatch(it) }
        // Serie A (not whitelisted) and the league-less match are DROPPED.
        assertEquals(2, mapped.size)

        val uel = mapped.first { it.competition?.code == "UEL" }
        assertEquals("Real Sociedad", uel.homeTeam.name)
        assertEquals("Fenerbahce", uel.awayTeam.name)
        assertEquals("FINISHED", uel.status)
        assertEquals(2, uel.score.fullTime?.home)
        assertEquals(1, uel.score.fullTime?.away)
        assertEquals("2026-11-05T20:00:00Z", uel.utcDate)
        assertEquals("Europa League", uel.competition?.name)
        assertEquals("RS", uel.homeTeam.tla)
        assertTrue(uel.id >= 80_000_000L)

        val uecl = mapped.first { it.competition?.code == "UECL" }
        assertEquals("TIMED", uecl.status)
        assertNull(uecl.score.fullTime?.home)
        assertNull(uecl.score.fullTime?.away)
    }

    @Test
    fun `incomplete matches never become fake rows`() {
        val incomplete = listOf(
            PitchFix(),                                  // everything null
            PitchFix(id = "m_x"),                        // no league
            PitchFix(id = "m_x", league = com.koora.app.data.PitchLg(id = "l_38d9HA", name = "Europa League")), // no teams
            PitchFix(id = "m_x", league = com.koora.app.data.PitchLg(id = "l_38d9HA", name = "Europa League"),
                home_team = com.koora.app.data.PitchTm(id = "t_a", name = "A")), // no away team
            PitchFix(id = "m_x", league = com.koora.app.data.PitchLg(id = "l_38d9HA", name = "Europa League"),
                home_team = com.koora.app.data.PitchTm(id = "t_a", name = "A"),
                away_team = com.koora.app.data.PitchTm(id = "t_b", name = "B"))  // no kickoff time
        )
        assertTrue(incomplete.all { PitchFixtures.toMatch(it) == null })
    }
}
