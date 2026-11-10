package com.koora.app

import com.koora.app.data.Match
import com.koora.app.data.Score
import com.koora.app.data.ScoreDetail
import com.koora.app.data.Team
import com.koora.app.data.TeamColors
import com.koora.app.ui.fameScore
import com.koora.app.ui.pickMatchOfTheDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.5.3 "Night Stadium" logic tests — pure JVM, no Robolectric needed:
 *  - TeamColors: known clubs resolve, unknown teams fall back deterministically,
 *    initials are derived correctly (trademark-safe badges);
 *  - Match of the Day picking: LIVE wins, fame breaks ties, earliest kickoff.
 */
class DesignLogicTest {

    private fun team(name: String, id: Long = 1, tla: String? = null) =
        Team(id = id, name = name, tla = tla)

    private fun match(
        id: Long,
        home: String,
        away: String,
        status: String,
        utcDate: String,
        homeGoals: Int? = null,
        awayGoals: Int? = null
    ) = Match(
        id = id,
        utcDate = utcDate,
        status = status,
        homeTeam = team(home, id = id * 10),
        awayTeam = team(away, id = id * 10 + 1),
        score = Score(
            fullTime = ScoreDetail(homeGoals, awayGoals),
            halfTime = null
        )
    )

    // ---- TeamColors -------------------------------------------------------

    @Test
    fun knownClub_resolvesColors() {
        val c = TeamColors.colorsFor("Manchester United FC")
        assertEquals(0xFFDA291C, c.bg)
        assertEquals(0xFFFFFFFF, c.fg)
    }

    @Test
    fun moroccanClubsResolve() {
        assertEquals(0xFF00A651, TeamColors.colorsFor("Raja Club Athletic").bg)
        assertEquals(0xFFC8102E, TeamColors.colorsFor("Wydad AC").bg)
    }

    @Test
    fun unknownTeam_fallsBackDeterministically() {
        val a = TeamColors.colorsFor("Totally Unknown FC 11")
        val b = TeamColors.colorsFor("Totally Unknown FC 11")
        assertEquals(a, b)                       // same team -> same colors, always
        assertTrue(TeamColors.colorsFor("Other Unknown FC").bg != 0x00000000L)
    }

    @Test
    fun initials_derivedFromName() {
        assertEquals("MU", TeamColors.initialsFor("Manchester United FC"))
        assertEquals("PSG", TeamColors.initialsFor("Paris Saint-Germain FC"))
        assertEquals("RM", TeamColors.initialsFor("Real Madrid"))
    }

    @Test
    fun isKnown_flagsFamousClubs() {
        assertTrue(TeamColors.isKnown("FC Barcelona"))
        assertFalse(TeamColors.isKnown("KAC Kenitra"))
    }

    // ---- Match of the Day -------------------------------------------------

    @Test
    fun emptyList_returnsNull() {
        assertNull(pickMatchOfTheDay(emptyList()))
    }

    @Test
    fun liveMatch_winsOverUpcoming() {
        val upcoming = match(1, "Norwich City", "Watford", "TIMED", "2026-11-12T19:00:00Z")
        val live = match(2, "Real Madrid", "Barcelona", "IN_PLAY", "2026-11-10T20:00:00Z", 1, 0)
        assertEquals(2L, pickMatchOfTheDay(listOf(upcoming, live))?.id)
    }

    @Test
    fun fame_breaksTies_thenEarliestKickoff() {
        val unknownVsUnknown = match(
            1, "Angers SCO", "Le Havre AC", "TIMED", "2026-11-11T19:00:00Z"
        )
        val famous = match(
            2, "Real Madrid", "Barcelona", "TIMED", "2026-11-11T21:00:00Z"
        )
        val picked = pickMatchOfTheDay(listOf(unknownVsUnknown, famous))
        assertNotNull(picked)
        assertEquals(2L, picked?.id)
    }

    @Test
    fun finishedOnlyList_stillPicksSomething() {
        val m = match(3, "Juventus", "Milan", "FINISHED", "2026-11-09T19:45:00Z", 2, 1)
        assertEquals(3L, pickMatchOfTheDay(listOf(m))?.id)
    }

    @Test
    fun fameScore_countsKnownTeams() {
        val both = match(4, "Liverpool", "Chelsea", "TIMED", "2026-11-12T19:00:00Z")
        val none = match(5, "KAC Kenitra", "Union Touarga", "TIMED", "2026-11-12T19:00:00Z")
        assertEquals(2, fameScore(both))
        assertEquals(0, fameScore(none))
    }
}
