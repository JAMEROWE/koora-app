package com.koora.app

import com.google.gson.Gson
import com.koora.app.data.Match
import com.koora.app.data.MatchStatsRepository
import com.koora.app.data.Score
import com.koora.app.data.ScoreDetail
import com.koora.app.data.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ESPN provider tests (v1.5.1). The fixture is a REAL ESPN summary
 * response — Stade Rennais 1-2 Lille, Ligue 1, 2026-11-08, event id
 * 401876406 — trimmed to exactly the fields the app parses.
 */
class EspnStatsTest {

    private val gson = Gson()

    private fun summary(): MatchStatsRepository.EspnSummary {
        val text = javaClass.getResourceAsStream("/espn_summary_sample.json")!!
            .readBytes().decodeToString()
        return gson.fromJson(text, MatchStatsRepository.EspnSummary::class.java)
    }

    private fun match() = Match(
        id = 991_234L,
        utcDate = "2026-11-08T19:45:00Z",
        status = "FINISHED",
        homeTeam = Team(id = 1, name = "Stade Rennais FC", shortName = "Rennes", tla = "SRF"),
        awayTeam = Team(id = 2, name = "Lille OSC", shortName = "Lille", tla = "LOS"),
        score = Score(fullTime = ScoreDetail(1, 2), halfTime = ScoreDetail(0, 1))
    )

    @Test
    fun `espn league map covers every competition in the app`() {
        // v1.8.0: the map now also covers the v1.7.0 "max attractive leagues"
        // (PitchAPI-side competitions) — slugs verified live 2026-11-11.
        // KOR is deliberately absent: ESPN has no K League coverage, so
        // those detail pages show H2H/form only (never faked).
        val codes = listOf(
            "CL", "PL", "PD", "SA", "BL1", "FL1", "ELC", "DED", "PPL", "BSA", "CLI", "WC", "EC",
            "UEL", "UECL", "SPL", "TUR", "ARG", "MEX", "MLS", "SCO", "BEL", "JPN"
        )
        codes.forEach {
            assertTrue("missing ESPN slug for $it", MatchStatsRepository.espnLeagueMap.containsKey(it))
        }
        assertFalse("ESPN has no K League coverage — must stay unmapped", MatchStatsRepository.espnLeagueMap.containsKey("KOR"))
        assertEquals(23, MatchStatsRepository.espnLeagueMap.size)
    }

    @Test
    fun `real espn summary parses events lineups and bars`() {
        val stats = MatchStatsRepository.espnToStats(summary(), match())

        // 3 goals + 3 yellows + 10 subs; kickoff / halftime / delays filtered out
        assertEquals(16, stats.events.size)
        assertEquals(3, stats.events.count { it.kind == "goal" })
        assertEquals(3, stats.events.count { it.kind == "yellow" })
        assertEquals(10, stats.events.count { it.kind == "sub" })

        // First goal: 24' Lille (away side), scorer parsed from the text
        val first = stats.events.first { it.kind == "goal" }
        assertEquals("24", first.minute)
        assertEquals(1, first.side)
        assertEquals("Soriba Diaoune", first.player)

        // Rennes goal is home side
        val homeGoal = stats.events.first { it.kind == "goal" && it.side == 0 }
        assertEquals("31", homeGoal.minute)
        assertEquals("Boulaye Dia", homeGoal.player)

        // Substitution keeps the in/out pair
        val sub = stats.events.first { it.kind == "sub" }
        assertEquals("Romain Perraud", sub.player)
        assertEquals("Calvin Verdonk", sub.other)

        // Lineups: home first, formation + XI + bench size
        assertEquals(2, stats.lineups.size)
        val homeLu = stats.lineups[0]
        assertEquals("Stade Rennais", homeLu.team)
        assertEquals("3-5-2", homeLu.formation)
        assertEquals(11, homeLu.starters.size)
        assertEquals(9, homeLu.subsCount)
        assertEquals("4-3-3", stats.lineups[1].formation)
        assertEquals("Lille", stats.lineups[1].team)

        // Stat bars: all 9 mapped, in display order, possession sums to 100
        assertEquals(
            listOf(
                "possession", "shots", "shots_on", "corners",
                "fouls", "yellow", "red", "offsides", "saves"
            ),
            stats.bars.map { it.label }
        )
        val possession = stats.bars.first()
        assertEquals("56%", possession.home)
        assertEquals("44%", possession.away)
        assertEquals("15", stats.bars[1].home)   // totalShots
        assertEquals("8", stats.bars[1].away)
        assertEquals("0", stats.bars.first { it.label == "yellow" }.home)
        assertEquals("3", stats.bars.first { it.label == "yellow" }.away)
        assertEquals("0", stats.bars.first { it.label == "red" }.home)
        assertEquals(0.555f, possession.homeFraction!!, 0.001f)
    }

    @Test
    fun `injury delays and half-time never become cards or goals`() {
        val stats = MatchStatsRepository.espnToStats(summary(), match())
        // Start Delay / End Delay events at 48'-57' and 82' (82' also has a
        // real substitution), plus "Start 2nd Half"/"Halftime"/"End Regular Time"
        assertTrue(stats.events.none { it.kind != "sub" && it.minute == "48" })
        assertTrue(stats.events.none { it.minute == "49" })
        assertTrue(stats.events.none { it.kind != "sub" && it.minute == "82" })
        assertTrue(stats.events.none { it.minute == "45+2" })
        assertTrue(stats.events.none { it.minute == "45" })
        assertTrue(stats.events.none { it.minute == "90+3" })
        assertTrue(stats.events.none { it.minute == "90+4" })
        // only real events survive: goal/yellow/sub
        stats.events.forEach { assertTrue(it.kind in setOf("goal", "yellow", "sub")) }
    }

    @Test
    fun `red card own goal and missed penalty without machine types`() {
        val s = summary().copy(
            keyEvents = listOf(
                MatchStatsRepository.EspnKey(
                    type = MatchStatsRepository.EspnType(text = "G. Kanga (Lille) is sent off.", type = null),
                    clock = MatchStatsRepository.EspnClock("79'"),
                    team = MatchStatsRepository.EspnTeamRef("166", "Lille"),
                    text = "G. Kanga (Lille) is sent off."
                ),
                MatchStatsRepository.EspnKey(
                    type = MatchStatsRepository.EspnType(text = "Own Goal", type = null),
                    clock = MatchStatsRepository.EspnClock("34'"),
                    team = MatchStatsRepository.EspnTeamRef("169", "Stade Rennais"),
                    text = "Own Goal by H. Mouaddib (Stade Rennais)."
                ),
                MatchStatsRepository.EspnKey(
                    type = MatchStatsRepository.EspnType(text = "Penalty - Missed", type = "penalty-missed"),
                    clock = MatchStatsRepository.EspnClock("61'"),
                    team = MatchStatsRepository.EspnTeamRef("166", "Lille"),
                    text = "Penalty missed by X (Lille)."
                )
            )
        )
        val stats = MatchStatsRepository.espnToStats(s, match())

        // missed penalty dropped, sent-off -> red, own goal -> goal("Own goal")
        assertEquals(2, stats.events.size)
        val red = stats.events.first { it.kind == "red" }
        assertEquals("79", red.minute)
        assertEquals("G. Kanga", red.player)
        val og = stats.events.first { it.kind == "goal" }
        assertEquals("Own goal", og.detail)
        assertEquals("H. Mouaddib", og.player)
    }

    @Test
    fun `name extraction helpers`() {
        assertEquals(
            "Calvin Verdonk",
            MatchStatsRepository.nameFromChunk("Calvin Verdonk (Lille) is shown the yellow card.")
        )
        assertEquals(
            "S. Diaoune",
            MatchStatsRepository.nameFromChunk("Goal! Rennes 0, Lille 1. S. Diaoune (Lille) left footed shot.")
        )
        assertEquals("H. Mouaddib", MatchStatsRepository.nameFromChunk("Own Goal by H. Mouaddib (Rennes)."))
        assertEquals("M. Perez", MatchStatsRepository.nameFromChunk("Penalty by M. Perez (Lille) converted."))
        assertEquals("", MatchStatsRepository.nameFromChunk("No parentheses here"))

        val sub = MatchStatsRepository.espnEventPeople(
            "sub",
            MatchStatsRepository.EspnKey(
                text = "Substitution, Lille. Romain Perraud replaces Calvin Verdonk."
            )
        )
        assertEquals("Romain Perraud", sub.first)
        assertEquals("Calvin Verdonk", sub.second)
    }

    @Test
    fun `fuzzy team matching across providers`() {
        assertTrue(MatchStatsRepository.nameMatches("Lille", "Lille OSC"))
        assertTrue(MatchStatsRepository.nameMatches("Paris Saint-Germain FC", "Paris Saint Germain"))
        assertTrue(MatchStatsRepository.nameMatches("Le Havre AC", "Le Havre"))
        assertTrue(MatchStatsRepository.nameMatches("Angers SCO", "Angers"))
        assertFalse(MatchStatsRepository.nameMatches("Olympique Lyonnais", "Olympique de Marseille"))
    }
}
