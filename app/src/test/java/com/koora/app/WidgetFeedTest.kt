package com.koora.app

import com.koora.app.data.Match
import com.koora.app.data.Score
import com.koora.app.data.ScoreDetail
import com.koora.app.data.Team
import com.koora.app.widget.pickWidgetMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.5.5 widget picking — pure JVM: LIVE first (famous clubs, then earliest
 * kickoff), then upcoming fixtures, then recent finals (36h window); dedup
 * and cap. The widget never shows invented matches.
 */
class WidgetFeedTest {

    private fun team(name: String, id: Long) = Team(id = id, name = name)

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
            fullTime = if (homeGoals == null) null else ScoreDetail(homeGoals, awayGoals)
        )
    )

    private val now = 1_760_000_000_000L          // fixed "now"
    private val hoursAgo = { h: Long -> now - h * 3_600_000L }

    private fun iso(epochMs: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(epochMs))

    @Test
    fun liveMatches_comeFirst() {
        val picks = pickWidgetMatches(
            listOf(
                match(1, "Angers", "Le Havre", "FINISHED", iso(hoursAgo(2)), 1, 0),
                match(2, "Le Havre", "Nantes", "SCHEDULED", iso(now + 3_600_000L)),
                match(3, "PSG", "Marseille", "IN_PLAY", iso(hoursAgo(1)), 1, 0)
            ),
            now
        )
        assertEquals("PSG", picks.first().homeTeam.name)
        assertEquals(3, picks.size)
    }

    @Test
    fun famousClubs_winTies() {
        val picks = pickWidgetMatches(
            listOf(
                match(1, "Angers", "Le Havre", "IN_PLAY", iso(hoursAgo(1)), 0, 0),
                match(2, "Real Madrid", "Barcelona", "IN_PLAY", iso(hoursAgo(2)), 1, 1)
            ),
            now
        )
        assertEquals("Real Madrid", picks.first().homeTeam.name)
    }

    @Test
    fun upcoming_whenNothingIsLive() {
        // Unknown clubs on purpose: fame is equal, so the earliest kickoff wins
        val picks = pickWidgetMatches(
            listOf(
                match(1, "Alpha United", "Beta FC", "SCHEDULED", iso(now + 5 * 3_600_000L)),
                match(2, "Gamma SC", "Delta CA", "SCHEDULED", iso(now + 2 * 3_600_000L))
            ),
            now
        )
        assertEquals("Gamma SC", picks.first().homeTeam.name)
    }

    @Test
    fun oldFinishedMatches_areExcluded() {
        val picks = pickWidgetMatches(
            listOf(match(1, "Brest", "Toulouse", "FINISHED", iso(hoursAgo(72)), 2, 1)),
            now
        )
        assertTrue(picks.isEmpty())
    }

    @Test
    fun recentFinished_isKeptAsFallback() {
        val picks = pickWidgetMatches(
            listOf(match(1, "Brest", "Toulouse", "FINISHED", iso(hoursAgo(10)), 2, 1)),
            now
        )
        assertEquals("Brest", picks.first().homeTeam.name)
    }

    @Test
    fun duplicates_areRemoved_andListCapped() {
        val dup = match(1, "PSG", "Lyon", "IN_PLAY", iso(hoursAgo(1)), 2, 0)
        val picks = pickWidgetMatches(
            listOf(dup, dup) +
                List(6) { i -> match(100 + i, "TeamA$i", "TeamB$i", "SCHEDULED", iso(now + (i + 1) * 3_600_000L)) },
            now,
            max = 4
        )
        assertEquals(4, picks.size)
        assertEquals(1, picks.count { it.homeTeam.name == "PSG" })
    }
}
