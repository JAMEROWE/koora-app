package com.koora.app

import com.koora.app.data.CompetitionRef
import com.koora.app.data.Match
import com.koora.app.data.Score
import com.koora.app.data.ScoreDetail
import com.koora.app.data.Team
import com.koora.app.ui.localDayKey
import com.koora.app.ui.localDayKeyAt
import com.koora.app.ui.matchesOnDay
import com.koora.app.ui.searchMatches
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DATE-BOUNDARY + SEARCH RULES — pure JVM tests, no Android.
 *
 * The display timezone rule is the app's established one: the DEVICE's
 * local timezone (SimpleDateFormat default). These tests pin the boundary
 * behavior around midnight, especially where the local date differs from
 * the UTC date — a 23:30 UTC kickoff is TOMORROW in a UTC+1 zone and
 * still TODAY in a UTC-5 zone. One match must land in exactly one day
 * bucket, never two.
 */
class DateBoundaryTest {

    private val realZone = TimeZone.getDefault()

    @After
    fun restoreZone() {
        TimeZone.setDefault(realZone)
    }

    private fun match(id: Long, utc: String, home: String = "Home$id", away: String = "Away$id") = Match(
        id = id,
        utcDate = utc,
        status = "SCHEDULED",
        homeTeam = Team(id = id * 10 + 1, name = home),
        awayTeam = Team(id = id * 10 + 2, name = away),
        score = Score(fullTime = ScoreDetail(null, null)),
        competition = CompetitionRef(code = "CL", name = "Champions League")
    )

    @Test
    fun midnightUtcKickoff_landsOnNextLocalDay_eastOfUtc() {
        // Europe/Paris: reliably UTC+1 in winter — 2026-11-24 23:30 UTC is
        // 2026-11-25 00:30 local, so the match belongs to TOMORROW's bucket.
        // (Zone choice is about the +1 offset, not any country's DST politics.)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
        assertEquals("2026-11-25", localDayKey(match(1, "2026-11-24T23:30:00Z")))
        // 21:30 UTC is still the 24th locally.
        assertEquals("2026-11-24", localDayKey(match(2, "2026-11-24T21:30:00Z")))
    }

    @Test
    fun sameKickoff_staysOnSameUtcDay_inNewYork() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York")) // UTC-5 in November
        // 2026-11-24 23:30 UTC = 2026-11-24 18:30 in New York.
        assertEquals("2026-11-24", localDayKey(match(1, "2026-11-24T23:30:00Z")))
        // 2026-11-25 02:30 UTC = 2026-11-24 21:30 in New York.
        assertEquals("2026-11-24", localDayKey(match(2, "2026-11-25T02:30:00Z")))
    }

    @Test
    fun aMatchAppearsInExactlyOneDayBucket_aroundMidnight() {
        for (zone in listOf("Europe/Paris", "America/New_York", "Asia/Tokyo", "UTC")) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            val around = listOf(
                match(1, "2026-11-24T22:59:59Z"),
                match(2, "2026-11-24T23:00:00Z"),
                match(3, "2026-11-24T23:59:59Z"),
                match(4, "2026-11-25T00:00:00Z"),
                match(5, "2026-11-25T00:00:01Z")
            )
            val today = matchesOnDay(around, "2026-11-24")
            val tomorrow = matchesOnDay(around, "2026-11-25")
            val overlap = today.map { it.id }.toSet() intersect tomorrow.map { it.id }.toSet()
            assertTrue("match in two day buckets in $zone: $overlap", overlap.isEmpty())
            assertEquals(
                "every parseable match is in exactly one of the two days in $zone",
                5, (today + tomorrow).size
            )
        }
    }

    @Test
    fun dayKeysRollWithDeviceClock_todayTomorrowDistinct() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
        val now = System.currentTimeMillis()
        assertTrue(localDayKeyAt(now, 0) != localDayKeyAt(now, 1))
    }
}

/** Team & league search over the loaded window — pure rules. */
class SearchLogicTest {

    private fun match(id: Long, home: String, away: String, code: String = "CL", league: String = "Champions League") = Match(
        id = id,
        utcDate = "2026-11-2${id}T20:00:00Z",
        status = "SCHEDULED",
        homeTeam = Team(id = id * 10 + 1, name = home),
        awayTeam = Team(id = id * 10 + 2, name = away),
        score = Score(fullTime = ScoreDetail(null, null)),
        competition = CompetitionRef(code = code, name = league)
    )

    private val window = listOf(
        match(1, "Raja Casablanca", "Wydad AC", code = "BOT", league = "Botola Pro"),
        match(2, "Real Madrid", "Manchester City"),
        match(3, "Paris Saint-Germain", "Marseille", code = "FL1", league = "Ligue 1")
    )

    @Test
    fun findsByTeamNameCaseInsensitive() {
        assertEquals(listOf(1L), searchMatches(window, "raja").map { it.id })
        assertEquals(listOf(2L), searchMatches(window, "CITY").map { it.id })
    }

    @Test
    fun findsByLeagueNameOrCode() {
        assertEquals(listOf(2L), searchMatches(window, "champions").map { it.id })
        assertEquals(listOf(2L), searchMatches(window, "cl").map { it.id })
        assertEquals(listOf(3L), searchMatches(window, "ligue").map { it.id })
    }

    @Test
    fun knownCompetitionLabelMatchesProviderlessMatches() {
        // Even if a feed omits the competition name, the known label ("Botola Pro") matches.
        val noName = match(1, "Raja Casablanca", "Wydad AC", code = "BOT", league = "")
            .copy(competition = CompetitionRef(code = "BOT", name = ""))
        assertEquals(listOf(1L), searchMatches(listOf(noName), "botola").map { it.id })
    }

    @Test
    fun shortOrBlankQueriesReturnNothing() {
        assertTrue(searchMatches(window, "r").isEmpty())
        assertTrue(searchMatches(window, "  ").isEmpty())
    }

    @Test
    fun resultsComeSoonestFirst() {
        val two = listOf(
            match(5, "AAA", "BBB", code = "PL", league = "Premier League"),
            match(1, "CCC", "DDD", code = "PL", league = "Premier League")
        )
        assertEquals(listOf(1L, 5L), searchMatches(two, "premier").map { it.id })
    }
}
