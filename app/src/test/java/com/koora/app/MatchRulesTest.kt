package com.koora.app

import com.koora.app.data.CompetitionRef
import com.koora.app.data.DemoData
import com.koora.app.data.Match
import com.koora.app.data.Score
import com.koora.app.data.ScoreDetail
import com.koora.app.data.Team
import com.koora.app.ui.daySections
import com.koora.app.ui.leagueBlocks
import com.koora.app.ui.localDayKey
import com.koora.app.ui.localDayKeyAt
import com.koora.app.ui.matchesOnDay
import com.koora.app.ui.next5Last5
import com.koora.app.ui.paginate
import com.koora.app.ui.pickResult
import com.koora.app.ui.accuracyOf
import com.koora.app.ui.sectionExpandedDefault
import com.koora.app.ui.sortMixed
import com.koora.app.ui.sortResultsNewestFirst
import com.koora.app.ui.sortUpcomingSoonestFirst
import com.koora.app.ui.screens.VideoCache
import com.koora.app.ui.voteLocked
import com.koora.app.ui.VotePick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * v1.5.6 RULES LAYER — pure logic, no screens, no network (doc §3):
 * day split, sort orders, pagination, vote lock at kick-off, team
 * panel selectors, league grouping, and the video-cache TTL rule
 * (found = 24h, miss = 45 min).
 */
class MatchRulesTest {

    private fun team(name: String, id: Long) = Team(id = id, name = name, tla = name.take(3))

    private fun match(
        id: Long,
        home: String,
        away: String,
        status: String,
        utcDate: String,
        homeGoals: Int? = null,
        awayGoals: Int? = null,
        compCode: String? = null
    ) = Match(
        id = id,
        utcDate = utcDate,
        status = status,
        homeTeam = team(home, id * 10),
        awayTeam = team(away, id * 10 + 1),
        score = Score(fullTime = ScoreDetail(homeGoals, awayGoals)),
        competition = compCode?.let { CompetitionRef(code = it, name = it) }
    )

    private fun utc(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(ms))

    private fun now(): Long = System.currentTimeMillis()

    /** UTC instant at [hour]:00 local time of a given local day key —
     *  deterministic in ANY device timezone (noon cannot leave its day). */
    private fun instantOf(dayKey: String, hour: Int): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val d = df.parse("$dayKey ${hour.toString().padStart(2, '0')}:00")!!
        return utc(d.time)
    }

    // ---- Day split ---------------------------------------------------------

    @Test
    fun matchesOnDay_keepsSameDay_dropsNeighbours() {
        val today = localDayKeyAt(now(), 0)
        val tomorrow = localDayKeyAt(now(), 1)
        val yesterday = localDayKeyAt(now(), -1)

        val inToday = match(1, "Team A", "Team B", "TIMED", instantOf(today, 12))
        val inTomorrow = match(2, "Team C", "Team D", "TIMED", instantOf(tomorrow, 12))
        val inYesterday = match(3, "Team E", "Team F", "FINISHED", instantOf(yesterday, 12), 2, 0)

        val day = matchesOnDay(listOf(inToday, inTomorrow, inYesterday), today)
        assertEquals(listOf(1L), day.map { it.id })
        // And the key round-trips through localDayKey
        assertEquals(today, localDayKey(inToday))
    }

    // ---- Ordering ----------------------------------------------------------

    @Test
    fun sortMixed_liveFirst_thenUpcoming_thenFinishedNewest() {
        val finishedOld = match(1, "A", "B", "FINISHED", "2026-11-08T18:00:00Z", 1, 1)
        val finishedNew = match(2, "C", "D", "FINISHED", "2026-11-10T18:00:00Z", 2, 0)
        val upcomingLate = match(3, "E", "F", "TIMED", "2026-11-12T21:00:00Z")
        val upcomingSoon = match(4, "G", "H", "TIMED", "2026-11-11T19:00:00Z")
        val live = match(5, "I", "J", "IN_PLAY", "2026-11-10T20:00:00Z", 1, 0)

        val sorted = sortMixed(listOf(finishedOld, upcomingLate, live, finishedNew, upcomingSoon))
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun resultsTab_finishedOnly_newestFirst() {
        val list = listOf(
            match(1, "A", "B", "FINISHED", "2026-11-08T18:00:00Z", 1, 0),
            match(2, "C", "D", "TIMED", "2026-11-09T18:00:00Z"),
            match(3, "E", "F", "FINISHED", "2026-11-10T18:00:00Z", 3, 1)
        )
        assertEquals(listOf(3L, 1L), sortResultsNewestFirst(list).map { it.id })
    }

    @Test
    fun upcomingTab_notFinished_soonestFirst() {
        val list = listOf(
            match(1, "A", "B", "FINISHED", "2026-11-08T18:00:00Z", 1, 0),
            match(2, "C", "D", "TIMED", "2026-11-12T18:00:00Z"),
            match(3, "E", "F", "SCHEDULED", "2026-11-10T18:00:00Z")
        )
        assertEquals(listOf(3L, 2L), sortUpcomingSoonestFirst(list).map { it.id })
    }

    // ---- Pagination ----------------------------------------------------------

    @Test
    fun paginate_loadsFirstRows_thenMore() {
        val big = (1..25L).map { match(it, "A$it", "B$it", "TIMED", "2026-11-11T1${(it % 10)}:00:00Z") }
        assertEquals(20, paginate(big, 20).size)
        assertEquals(25, paginate(big, 40).size)
        assertEquals(3, paginate(big.take(3), 20).size)
        assertTrue(paginate(big, 0).isEmpty())
    }

    // ---- Votes ---------------------------------------------------------------

    @Test
    fun vote_openBeforeKickoff() {
        val m = match(1, "A", "B", "TIMED", utc(now() + 2 * 3_600_000L))
        assertFalse(voteLocked(m, now()))
    }

    @Test
    fun vote_lockedOnceKickoffTimePasses_evenIfStatusLags() {
        val m = match(1, "A", "B", "TIMED", utc(now() - 3_600_000L))
        assertTrue(voteLocked(m, now()))
    }

    @Test
    fun vote_lockedForLiveFinishedAndCancelled() {
        val now = now()
        val future = utc(now + 2 * 3_600_000L)
        assertTrue(voteLocked(match(1, "A", "B", "IN_PLAY", future, 1, 0), now))
        assertTrue(voteLocked(match(2, "C", "D", "FINISHED", future, 2, 1), now))
        assertTrue(voteLocked(match(3, "E", "F", "POSTPONED", future), now))
        assertTrue(voteLocked(match(4, "G", "H", "CANCELLED", future), now))
    }

    @Test
    fun vote_lockedWhenKickoffTimeUnparseable_safeSide() {
        val m = match(1, "A", "B", "TIMED", "not-a-date")
        assertTrue(voteLocked(m, now()))
    }

    /** Match where "MyTeam" (id 10) always plays — [home] picks the side. */
    private fun teamMatch(
        id: Long,
        opponent: String,
        status: String,
        utcDate: String,
        homeGoals: Int? = null,
        awayGoals: Int? = null,
        home: Boolean = true,
        compCode: String? = null
    ) = Match(
        id = id,
        utcDate = utcDate,
        status = status,
        homeTeam = if (home) team("MyTeam", 10) else team(opponent, 99),
        awayTeam = if (home) team(opponent, 99) else team("MyTeam", 10),
        score = Score(fullTime = ScoreDetail(homeGoals, awayGoals)),
        competition = compCode?.let { CompetitionRef(code = it, name = it) }
    )

    // ---- Team panel ------------------------------------------------------------

    @Test
    fun next5Last5_splitAcrossCompetitions() {
        val future = (1L..7L).map {
            teamMatch(it, "Opp$it", "TIMED", utc(now() + it * 3_600_000L),
                home = it % 2 == 0L, compCode = if (it % 2 == 0L) "PL" else "CL")
        }
        val past = (8L..14L).map {
            teamMatch(it, "Old$it", "FINISHED", utc(now() - it * 3_600_000L), 1, 0)
        }
        val other = match(15, "Other", "Team", "TIMED", utc(now() + 60_000L))

        val (next, recent) = next5Last5(future + past + other, teamId = 10L, nowMs = now())
        assertEquals(5, next.size)
        assertEquals(5, recent.size)
        // next: soonest first, ids 1..5 (both home and away sides count)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), next.map { it.id })
        // recent: newest first (now - id*1h) -> smallest id first
        assertEquals(listOf(8L, 9L, 10L, 11L, 12L), recent.map { it.id })
        // other team's match never appears
        assertFalse((next + recent).any { it.id == 15L })
    }

    // ---- League blocks -----------------------------------------------------------

    @Test
    fun leagueBlocks_groupByCompetition_ignoreDemoMatches() {
        val pl = match(1, "A", "B", "FINISHED", "2026-11-10T18:00:00Z", 1, 0, compCode = "PL")
        val cl = match(2, "C", "D", "TIMED", "2026-11-10T20:00:00Z", compCode = "CL")
        val demo = match(3, "Raja Club Athletic", "Wydad AC", "FINISHED", "2026-11-10T19:00:00Z", 2, 1)

        val blocks = leagueBlocks(listOf(pl, cl, demo))
        assertEquals(setOf("PL", "CL"), blocks.keys)
        assertEquals(listOf(1L), blocks["PL"]!!.map { it.id })
    }

    // ---- Day sections (v1.6.0 Live/Results structure) ---------------------

    @Test
    fun daySections_groupByLeague_inDisplayOrder() {
        // PL match, BOT match, an unknown league, and one without competition.
        val bot = match(1, "Raja", "Wydad", "FINISHED", "2026-11-10T18:00:00Z", 2, 1, compCode = "BOT")
        val pl = match(2, "A", "B", "TIMED", "2026-11-10T20:00:00Z", compCode = "PL")
        val unknown = match(3, "C", "D", "TIMED", "2026-11-10T21:00:00Z", compCode = "ZZZ")
        val noComp = match(4, "E", "F", "FINISHED", "2026-11-10T19:00:00Z", 0, 0)

        val sections = daySections(listOf(unknown, noComp, pl, bot))

        // No-competition block first, then Botola Pro (display order),
        // then Premier League, unknown league last.
        assertEquals(
            listOf("", "BOT", "PL", "ZZZ"),
            sections.map { it.code }
        )
        assertEquals(listOf(1L), sections.first { it.code == "BOT" }.matches.map { it.id })
    }

    @Test
    fun daySections_demoMatchesCarryCompetition() {
        // v1.6.0: the demo sample is stamped with competitions so demo mode
        // renders the same league sections as real data (never an empty
        // screen), with unique ids after deduplication.
        val demo = DemoData.matches("ALL").distinctBy { it.id }
        assertEquals(5, demo.size)
        assertTrue(demo.all { !it.competition?.code.isNullOrBlank() })
        assertTrue(demo.any { it.competition?.code == "BOT" })
    }

    // ---- Video cache TTL ------------------------------------------------------------

    @Test
    fun videoCache_foundLives24h_missOnly45min() {
        val now = now()
        val hour = 3_600_000L
        // found: fresh 1h and 23h later, dead at 25h
        assertTrue(VideoCache.isFresh(found = true, at = now - 1 * hour, now = now))
        assertTrue(VideoCache.isFresh(found = true, at = now - 23 * hour, now = now))
        assertFalse(VideoCache.isFresh(found = true, at = now - 25 * hour, now = now))
        // miss: fresh at 44 min, dead at 46 min
        assertTrue(VideoCache.isFresh(found = false, at = now - 44 * 60_000L, now = now))
        assertFalse(VideoCache.isFresh(found = false, at = now - 46 * 60_000L, now = now))
        // timestamps from the future are never trusted
        assertFalse(VideoCache.isFresh(found = true, at = now + hour, now = now))
    }

    // ---- v1.10.0 (spec §2.3): pick validation + personal accuracy --------------------

    @Test
    fun pickResult_checksRealFinalScore() {
        val finished = match(60, "Arsenal", "Chelsea", "FINISHED", utc(now() - hour()), 2, 1)
        val draw = match(61, "Raja", "Wydad", "FINISHED", utc(now() - hour()), 1, 1)
        assertTrue(pickResult(finished, VotePick.HOME.key) == true)
        assertTrue(pickResult(finished, VotePick.AWAY.key) == false)
        assertTrue(pickResult(draw, VotePick.DRAW.key) == true)
        assertTrue(pickResult(draw, VotePick.HOME.key) == false)
        // Unknown pick keys can never be counted
        assertEquals(null, pickResult(finished, "MAYBE"))
    }

    @Test
    fun pickResult_unresolvedWhenNoFinalScoreYet() {
        val upcoming = match(62, "Arsenal", "Chelsea", "TIMED", utc(now() + hour()))
        val live = match(63, "Raja", "Wydad", "IN_PLAY", utc(now()), 1, 0)
        val noScore = match(64, "PSG", "OM", "FINISHED", utc(now() - hour()))
        // Not finished -> unresolved; finished but scoreless -> still unresolved
        assertEquals(null, pickResult(upcoming, VotePick.HOME.key))
        assertEquals(null, pickResult(live, VotePick.HOME.key))
        assertEquals(null, pickResult(noScore, VotePick.HOME.key))
    }

    @Test
    fun accuracyOf_countsOnlyResolvedPicks() {
        val win = match(65, "Arsenal", "Chelsea", "FINISHED", utc(now() - 3 * hour()), 2, 0)
        val loss = match(66, "Raja", "Wydad", "FINISHED", utc(now() - 2 * hour()), 0, 1)
        val pending = match(67, "PSG", "OM", "TIMED", utc(now() + hour()))
        val picks = mapOf(65L to VotePick.HOME.key, 66L to VotePick.HOME.key, 67L to VotePick.AWAY.key)
        val (correct, counted) = accuracyOf(picks, listOf(win, loss, pending))
        // 1 correct (Arsenal) + 1 missed (Raja); the pending pick is NOT counted
        assertEquals(1, correct)
        assertEquals(2, counted)
        // A pickless history counts nothing at all
        assertEquals(0 to 0, accuracyOf(emptyMap(), listOf(win, loss)))
    }

    // ---- v1.10.0 (spec §2.2): accordion smart default --------------------------------

    @Test
    fun sectionExpanded_smartDefault() {
        // live section always opens, favorites always open
        assertTrue(sectionExpandedDefault(hasLive = true, hasFavorite = false, sectionsInBlock = 9))
        assertTrue(sectionExpandedDefault(hasLive = false, hasFavorite = true, sectionsInBlock = 9))
        // small blocks stay open (a quiet matchday hides nothing)
        assertTrue(sectionExpandedDefault(hasLive = false, hasFavorite = false, sectionsInBlock = 3))
        // a big block keeps non-live, unfavorited leagues collapsed (spec §2.2)
        assertFalse(sectionExpandedDefault(hasLive = false, hasFavorite = false, sectionsInBlock = 4))
    }

    private fun hour(): Long = 3_600_000L
}
