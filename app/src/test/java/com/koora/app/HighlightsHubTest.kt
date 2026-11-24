package com.koora.app

import com.koora.app.data.Match
import com.koora.app.data.Score
import com.koora.app.data.ScoreDetail
import com.koora.app.data.Team
import com.koora.app.ui.screens.HighlightsHub
import com.koora.app.ui.screens.YtEntry
import com.koora.app.ui.screens.entryGroundedInFixture
import com.koora.app.ui.screens.fixtureSeasonStart
import com.koora.app.ui.screens.publishWindowAllows
import com.koora.app.ui.screens.titleScoreline
import com.koora.app.ui.screens.titleSeasonStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * v1.8.0 Highlights hub — the PURE pairing rule that decides which
 * released official videos may appear in the independent tab. Plain
 * JUnit (no Android APIs under test):
 *
 *  - a feed entry may surface ONLY when it names BOTH teams of one known
 *    finished fixture (the tab contains only released matches);
 *  - a fixture without a released video never appears (no placeholders);
 *  - blank video ids and unrelated titles are rejected, never guessed.
 */
class HighlightsHubTest {

    private fun ms(iso: String): Long = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .parse(iso)!!.time

    private fun fixture(id: Long, home: String, away: String, utc: String, score: Score = Score()) = Match(
        id = id,
        utcDate = utc,
        status = "FINISHED",
        homeTeam = Team(id * 10, home),
        awayTeam = Team(id * 10 + 1, away),
        score = score
    )

    private val palaceBrentford = fixture(1, "Crystal Palace", "Brentford", "2026-11-08T15:00:00Z")
    private val arsenalChelsea = fixture(2, "Arsenal", "Chelsea", "2026-11-09T16:30:00Z")

    @Test
    fun pairsEntryNamingBothTeams() {
        val entry = YtEntry(
            title = "Crystal Palace 1-0 Brentford | Premier League Highlights",
            videoId = "mV123",
            published = "2026-11-08T18:02:00Z",
            publishedAtMs = ms("2026-11-08T18:02:00Z")
        )
        val paired = HighlightsHub.pairWithFixture(entry, listOf(palaceBrentford, arsenalChelsea))
        assertNotNull(paired)
        assertEquals("Crystal Palace", paired!!.second.homeTeam.name)
        assertEquals("Brentford", paired.second.awayTeam.name)
    }

    @Test
    fun oneTeamOnly_neverPairs() {
        // The title names Chelsea but the only fixture with Chelsea also
        // involved Everton — pairing would be a guess, so it must fail.
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
    fun twoPlausibleMeetings_neverAssignedOnRecencyAlone() {
        // Both meetings pass every gate (publish window overlaps, scores
        // unknown): recency order is NOT evidence, so no association.
        val older = fixture(4, "Arsenal", "Chelsea", "2026-11-08T12:00:00Z")
        val newer = fixture(5, "Chelsea", "Arsenal", "2026-11-09T20:00:00Z")
        val entry = YtEntry("Arsenal v Chelsea | Highlights", "mV126", "",
            publishedAtMs = ms("2026-11-09T23:00:00Z"))
        assertNull(HighlightsHub.pairWithFixture(entry, listOf(newer, older)))
    }

    @Test
    fun agreeingScoreline_disambiguatesBetweenMeetings() {
        // The ONE stronger-metadata case: both meetings pass every other
        // gate (the older one's score is unknown, so nothing excludes it),
        // and the title's own scoreline equals exactly ONE recorded score.
        val older = fixture(4, "Arsenal", "Chelsea", "2026-11-08T12:00:00Z")
        val newer = fixture(5, "Chelsea", "Arsenal", "2026-11-09T20:00:00Z",
            Score(fullTime = ScoreDetail(home = 2, away = 2)))
        val entry = YtEntry("Chelsea 2-2 Arsenal | Highlights", "mV127", "",
            publishedAtMs = ms("2026-11-09T23:00:00Z"))
        val paired = HighlightsHub.pairWithFixture(entry, listOf(newer, older))
        assertEquals(5L, paired!!.second.id)
    }

    @Test
    fun identicalScoresStayAmbiguous_neverAssigned() {
        // Two meetings with the SAME recorded score: even the title
        // scoreline cannot tell them apart, so no association.
        val older = fixture(4, "Arsenal", "Chelsea", "2026-11-08T12:00:00Z",
            Score(fullTime = ScoreDetail(home = 2, away = 2)))
        val newer = fixture(5, "Chelsea", "Arsenal", "2026-11-09T20:00:00Z",
            Score(fullTime = ScoreDetail(home = 2, away = 2)))
        val entry = YtEntry("Chelsea 2-2 Arsenal | Highlights", "mV128", "",
            publishedAtMs = ms("2026-11-09T23:00:00Z"))
        assertNull(HighlightsHub.pairWithFixture(entry, listOf(newer, older)))
    }

    @Test
    fun nonHighlightGenres_neverPair() {
        // Previews, pressers, training and interviews name both teams and
        // pass the metadata gates — only their declared genre betrays them.
        val fresh = ms("2026-11-22T19:00:00Z")
        listOf(
            "Liverpool v Manchester United | PREVIEW",
            "Liverpool 4-0 Manchester United | Slot press conference",
            "Manchester United training before Liverpool",
            "Liverpool star interview: facing Manchester United"
        ).forEachIndexed { i, title ->
            val entry = YtEntry(title, "mV40$i", "1d ago", publishedAtMs = fresh)
            assertNull(title, HighlightsHub.pairWithFixture(entry, listOf(liverpoolManUtd)))
        }
    }

    @Test
    fun genreWordsInsideHighlightTitles_doNotFalseReject() {
        // A genuine highlights title mentioning none of the genres passes.
        val entry = YtEntry(
            "Liverpool 4-0 Manchester United | Premier League Highlights", "mV410", "1d ago",
            publishedAtMs = ms("2026-11-22T19:00:00Z")
        )
        assertNotNull(HighlightsHub.pairWithFixture(entry, listOf(liverpoolManUtd)))
    }

    @Test
    fun shortDistinctiveTokensStillMatch() {
        // Real football-data naming vs channel phrasing: "Manchester City"
        // vs "Man City"-style titles must still pair on distinctive tokens.
        val city = fixture(6, "Manchester City", "Sunderland", "2026-11-08T17:30:00Z")
        val entry = YtEntry("Manchester City 4-0 Sunderland | Highlights", "mV127", "",
            publishedAtMs = ms("2026-11-08T19:00:00Z"))
        assertNotNull(HighlightsHub.pairWithFixture(entry, listOf(city)))
    }

    // ---- publish-time grounding + score cross-check (owner report 2026-11-24:
    // a Liverpool 4-0 row surfaced with the thumbnail of an older 1-2 meeting) ----

    private val liverpoolManUtd = fixture(
        20, "Liverpool", "Manchester United", "2026-11-22T16:30:00Z",
        Score(fullTime = ScoreDetail(home = 4, away = 0))
    )

    @Test
    fun staleThrowbackReleasedBeforeKickoff_neverPairs() {
        // The exact reported shape: the channel re-released the CLASSIC
        // 2025/26 meeting the day BEFORE this season's 4-0 was played.
        val entry = YtEntry(
            "Maguire's Late Heroics! | Liverpool v Manchester United 2025/26", "mV300", "1d ago",
            publishedAtMs = ms("2026-11-21T12:00:00Z")
        )
        assertNull(HighlightsHub.pairWithFixture(entry, listOf(liverpoolManUtd)))
    }

    @Test
    fun unknownPublishTime_neverPairs() {
        // No publish metadata = no verified match, even for a perfect title.
        val entry = YtEntry("Liverpool 4-0 Manchester United | Highlights", "mV301", "")
        assertNull(HighlightsHub.pairWithFixture(entry, listOf(liverpoolManUtd)))
    }

    @Test
    fun publishedLongAfterKickoff_neverPairs() {
        // A clip released ten days later is a magazine piece, not highlights.
        val entry = YtEntry(
            "Liverpool v Manchester United | The Story", "mV302", "1w ago",
            publishedAtMs = ms("2026-12-02T16:30:00Z")
        )
        assertNull(HighlightsHub.pairWithFixture(entry, listOf(liverpoolManUtd)))
    }

    @Test
    fun scoreDisagreeingTitle_neverPairs() {
        // Fresh enough to pass the time gate, but the title's own score
        // betrays it as another meeting's video.
        val entry = YtEntry(
            "Liverpool 1-2 Manchester United | Premier League Highlights", "mV303", "1d ago",
            publishedAtMs = ms("2026-11-22T19:00:00Z")
        )
        assertNull(HighlightsHub.pairWithFixture(entry, listOf(liverpoolManUtd)))
    }

    @Test
    fun scoreAgreeingTitle_pairs() {
        val entry = YtEntry(
            "Liverpool 4-0 Manchester United | Premier League Highlights", "mV304", "1d ago",
            publishedAtMs = ms("2026-11-22T19:00:00Z")
        )
        assertEquals(20L, HighlightsHub.pairWithFixture(entry, listOf(liverpoolManUtd))!!.second.id)
    }

    @Test
    fun swappedTitleOrder_scoreReadInTitleOrientation() {
        // "Away X-Y Home" titles are legitimate when the swapped score
        // matches, and rejected when the unswapped reading would be wrong.
        val agreeing = YtEntry(
            "Manchester United 0-4 Liverpool | Highlights", "mV305", "1d ago",
            publishedAtMs = ms("2026-11-22T19:00:00Z")
        )
        assertNotNull(HighlightsHub.pairWithFixture(agreeing, listOf(liverpoolManUtd)))
        val disagreeing = YtEntry(
            "Manchester United 4-0 Liverpool | Highlights", "mV306", "1d ago",
            publishedAtMs = ms("2026-11-22T19:00:00Z")
        )
        assertNull(HighlightsHub.pairWithFixture(disagreeing, listOf(liverpoolManUtd)))
    }

    @Test
    fun seasonLabelsAndDatesAreNotScorelines() {
        assertNull(titleScoreline("Liverpool v Manchester United | 2025-26 Classics"))
        assertNull(titleScoreline("Liverpool v Manchester United 2026-11-08 rerun"))
        assertEquals(4 to 0, titleScoreline("Liverpool 4-0 Manchester United | Highlights"))
        // A CURRENT-season label must not false-reject the real highlights.
        val entry = YtEntry(
            "Liverpool v Manchester United 2026-27 | Extended Highlights", "mV307", "1d ago",
            publishedAtMs = ms("2026-11-22T19:00:00Z")
        )
        assertNotNull(HighlightsHub.pairWithFixture(entry, listOf(liverpoolManUtd)))
    }

    @Test
    fun oldSeasonLabel_neverPairsEvenWhenPublishedAfterKickoff() {
        // The gap the publish window cannot see: a classic RE-RELEASED
        // after the new meeting. Its own season declaration betrays it.
        val entry = YtEntry(
            "Liverpool v Manchester United 2025/26 | Classics", "mV309", "1d ago",
            publishedAtMs = ms("2026-11-23T12:00:00Z")
        )
        assertNull(HighlightsHub.pairWithFixture(entry, listOf(liverpoolManUtd)))
    }

    @Test
    fun seasonHelpers_readLabelsAndFixtureSeasons() {
        assertEquals(2025, titleSeasonStart("Liverpool v Manchester United 2025/26"))
        assertEquals(2026, titleSeasonStart("EVERY Weekend Goal | Matchweek 11 | 2026/27 Premier League Highlights"))
        assertNull(titleSeasonStart("Liverpool v Manchester United 2026-11-08 rerun")) // date, not a season
        assertNull(titleSeasonStart("Liverpool 4-0 Manchester United | Highlights"))
        assertEquals(2026, fixtureSeasonStart("2026-11-22T16:30:00Z"))  // autumn
        assertEquals(2026, fixtureSeasonStart("2027-02-14T20:00:00Z"))  // spring, same season
        assertEquals(2025, fixtureSeasonStart("2026-06-30T18:00:00Z"))  // June still old season
    }

    @Test
    fun videoReleasedBeforeRematch_pairsWithTheMeetingItDocuments() {
        // Two meetings inside the window: the video of the FIRST leg must
        // not steal the rematch's row — it pairs with the meeting whose
        // kick-off its publish time actually follows.
        val older = fixture(30, "Arsenal", "Chelsea", "2026-11-08T12:00:00Z")
        val newer = fixture(31, "Chelsea", "Arsenal", "2026-11-09T20:00:00Z")
        val entry = YtEntry(
            "Arsenal v Chelsea | Highlights", "mV308", "1d ago",
            publishedAtMs = ms("2026-11-08T18:00:00Z")
        )
        val paired = HighlightsHub.pairWithFixture(entry, listOf(newer, older))
        assertEquals(30L, paired!!.second.id)
    }

    @Test
    fun detailResolver_usesTheSameGrounding() {
        // The shared gate the detail screen now applies: a stale throwback
        // fails, the verified highlight passes — same rule as the hub.
        val stale = YtEntry(
            "Maguire's Late Heroics! | Liverpool v Manchester United 2025/26", "mV420", "3d ago",
            publishedAtMs = ms("2026-11-21T12:00:00Z")
        )
        val real = YtEntry(
            "Liverpool 4-0 Manchester United | Highlights", "mV421", "1d ago",
            publishedAtMs = ms("2026-11-22T19:00:00Z")
        )
        val grounded = listOf(stale, real).filter {
            entryGroundedInFixture(it, "Liverpool", "Manchester United", "2026-11-22T16:30:00Z", 4, 0)
        }
        assertEquals(listOf("mV421"), grounded.map { it.videoId })
    }

    @Test
    fun publishWindow_edges() {
        val ko = "2026-11-22T16:30:00Z"
        assertTrue(publishWindowAllows(ms("2026-11-22T19:00:00Z"), ko))   // same evening
        assertTrue(publishWindowAllows(ms("2026-11-23T10:00:00Z"), ko))   // next morning
        assertTrue(!publishWindowAllows(0L, ko))                          // unknown publish
        assertTrue(!publishWindowAllows(ms("2026-11-21T12:00:00Z"), ko))  // before the meeting
        assertTrue(!publishWindowAllows(ms("2026-11-22T19:00:00Z"), "not a date"))
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
        // first official release (list order) wins — all rows are playable
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
