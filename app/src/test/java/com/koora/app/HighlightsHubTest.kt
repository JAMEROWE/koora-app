package com.koora.app

import com.koora.app.data.Match
import com.koora.app.data.Score
import com.koora.app.data.Team
import com.koora.app.ui.screens.HighlightsHub
import com.koora.app.ui.screens.YtEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.8.0 Highlights hub â the PURE pairing rule that decides which
 * released official videos may appear in the independent tab. Plain
 * JUnit (no Android APIs under test):
 *
 *  - a feed entry may surface ONLY when it names BOTH teams of one known
 *    finished fixture (the tab contains only released matches);
 *  - a fixture without a released video never appears (no placeholders);
 *  - blank video ids and unrelated titles are rejected, never guessed.
 */
class HighlightsHubTest {

    private fun fixture(id: Long, home: String, away: String, utc: String) = Match(
        id = id,
        utcDate = utc,
        status = "FINISHED",
        homeTeam = Team(id * 10, home),
        awayTeam = Team(id * 10 + 1, away),
        score = Score()
    )

    private val palaceBrentford = fixture(1, "Crystal Palace", "Brentford", "2026-11-08T15:00:00Z")
    private val arsenalChelsea = fixture(2, "Arsenal", "Chelsea", "2026-11-09T16:30:00Z")

    @Test
    fun pairsEntryNamingBothTeams() {
        val entry = YtEntry(
            title = "Crystal Palace 1-0 Brentford | Premier League Highlights",
            videoId = "mV123",
            published = "2026-11-08T18:02:00Z"
        )
        val paired = HighlightsHub.pairWithFixture(entry, listOf(palaceBrentford, arsenalChelsea))
        assertNotNull(paired)
        assertEquals("Crystal Palace", paired!!.second.homeTeam.name)
        assertEquals("Brentford", paired.second.awayTeam.name)
    }

    @Test
    fun oneTeamOnly_neverPairs() {
        // The title names Chelsea but the only fixture with Chelsea also
        // involved Everton â pairing would be a guess, so it must fail.
        val entry = YtEntry("Chelsea 1-0 Arsenal | Highlights", "mV124", "")
        assertNull(HighlightsHub.pairWithFixture(entry, listOf(fixture(3, "Chelsea", "Everton", "2026-11-09T14:00:00Z"))))
    }

    @Test
    fun blankVideoId_rejected() {
        val entry = YtEntry("Arsenal 2-2 Chelsea | Highlights", "", "")
        assertNull(HighlightsHub.pairWithFixture(entry, listOf(arsenalChelsea)))
    }

    @Test
    fun unrelatedTitle_neverPairs() {
        val entry = YtEntry("Post-match press conference with the manager", "mV125", "")
        assertNull(HighlightsHub.pairWithFixture(entry, listOf(palaceBrentford, arsenalChelsea)))
    }

    @Test
    fun newestFixtureWinsWhenTeamsMetTwice() {
        val older = fixture(4, "Arsenal", "Chelsea", "2026-11-08T12:00:00Z")
        val newer = fixture(5, "Chelsea", "Arsenal", "2026-11-09T20:00:00Z")
        val entry = YtEntry("Arsenal 2-2 Chelsea | Highlights", "mV126", "")
        // The caller sorts fixtures newest-first; the newest meeting wins.
        val pairedNewestFirst = HighlightsHub.pairWithFixture(entry, listOf(newer, older))
        assertEquals(5L, pairedNewestFirst!!.second.id)
        // And when the entry names both teams, pairing succeeds at all.
        assertTrue(pairedNewestFirst.second.awayTeam.name == "Arsenal")
    }

    @Test
    fun shortDistinctiveTokensStillMatch() {
        // Real football-data naming vs channel phrasing: "Manchester City"
        // vs "Man City"-style titles must still pair on distinctive tokens.
        val city = fixture(6, "Manchester City", "Sunderland", "2026-11-08T17:30:00Z")
        val entry = YtEntry("Manchester City 4-0 Sunderland | Highlights", "mV127", "")
        assertNotNull(HighlightsHub.pairWithFixture(entry, listOf(city)))
    }

    // ---- one row per fixture (user-reported duplicate highlight) ----

    private fun row(m: Match, videoId: String, title: String) =
        HighlightsHub.Row(m, YtEntry(title, videoId, "2026-11-09T18:00:00Z"))

    @Test
    fun twoVideosOfSameFixture_collapseToOneRow() {
        val rows = listOf(
            row(palaceBrentford, "mV200", "Palace 1-0 Brentford | Highlights"),
            row(palaceBrentford, "mV201", "Palace v Brentford | EXTENDED Highlights")
        )
        val collapsed = HighlightsHub.oneRowPerFixture(rows)
        assertEquals(1, collapsed.size)
        // first official release (list order) wins â all rows are playable
        assertEquals("mV200", collapsed[0].video.videoId)
    }

    @Test
    fun rematchBetweenSameClubs_keepsItsOwnRow() {
        val firstLeg = fixture(10, "Arsenal", "Chelsea", "2026-09-01T15:00:00Z")
        val secondLeg = fixture(11, "Arsenal", "Chelsea", "2026-11-09T16:30:00Z")
        val rows = listOf(
            row(firstLeg, "mV210", "Arsenal 0-0 Chelsea | Highlights"),
            row(secondLeg, "mV211", "Arsenal 2-1 Chelsea | Highlights")
        )
        assertEquals(2, HighlightsHub.oneRowPerFixture(rows).size)
    }

    @Test
    fun sameVideoListedTwice_surfacesOnce() {
        val rows = listOf(
            row(arsenalChelsea, "mV220", "Arsenal 2-1 Chelsea | Highlights"),
            row(arsenalChelsea, "mV220", "Arsenal 2-1 Chelsea | Highlights")
        )
        assertEquals(1, HighlightsHub.oneRowPerFixture(rows).size)
    }

    @Test
    fun unsetFixtureId_fallsBackToDateAndTeams() {
        val dayOne = fixture(0, "Arsenal", "Chelsea", "2026-11-01T16:30:00Z")
        val dayTwo = fixture(0, "Arsenal", "Chelsea", "2026-11-09T16:30:00Z")
        val rows = listOf(
            row(dayOne, "mV230", "Arsenal v Chelsea"),
            row(dayTwo, "mV231", "Arsenal v Chelsea"),
            row(dayOne, "mV232", "Arsenal v Chelsea | Extended")
        )
        // different days never merge; two videos of the same day do
        assertEquals(2, HighlightsHub.oneRowPerFixture(rows).size)
    }
}
