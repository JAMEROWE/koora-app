package com.koora.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Sample data so the app works immediately, before you add a
 * football-data.org key. Delete-able later; the repository
 * automatically switches to real data once a key is present.
 */
object DemoData {

    private fun iso(hoursFromNow: Int): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(System.currentTimeMillis() + hoursFromNow * 3_600_000L))
    }

    fun matches(competition: String): List<Match> = listOf(
        Match(
            id = 9001, utcDate = iso(-1), status = "FINISHED",
            homeTeam = Team(101, "Raja Club Athletic", tla = "RCA"),
            awayTeam = Team(102, "Wydad AC", tla = "WAC"),
            score = Score(fullTime = ScoreDetail(2, 1))
        ),
        Match(
            id = 9002, utcDate = iso(0), status = "IN_PLAY",
            homeTeam = Team(103, "Paris Saint-Germain", tla = "PSG"),
            awayTeam = Team(104, "Olympique de Marseille", tla = "OM"),
            score = Score(fullTime = ScoreDetail(1, 1), halfTime = ScoreDetail(0, 1))
        ),
        Match(
            id = 9003, utcDate = iso(3), status = "TIMED",
            homeTeam = Team(105, "Real Madrid", tla = "RMA"),
            awayTeam = Team(106, "Manchester City", tla = "MCI"),
            score = Score()
        ),
        Match(
            id = 9004, utcDate = iso(5), status = "TIMED",
            homeTeam = Team(107, "Olympique Lyonnais", tla = "OL"),
            awayTeam = Team(108, "Lille OSC", tla = "LIL"),
            score = Score()
        ),
        Match(
            id = 9005, utcDate = iso(-24), status = "FINISHED",
            homeTeam = Team(109, "AS FAR Rabat", tla = "FAR"),
            awayTeam = Team(110, "MC Oujda", tla = "MCO"),
            score = Score(fullTime = ScoreDetail(3, 0))
        )
    )

    fun standings(): List<TableRow> = listOf(
        TableRow(1, Team(103, "Paris Saint-Germain"), 11, 9, 1, 1, 28, 30, 9),
        TableRow(2, Team(104, "Olympique de Marseille"), 11, 8, 2, 1, 26, 26, 12),
        TableRow(3, Team(107, "Olympique Lyonnais"), 11, 7, 2, 2, 23, 22, 13),
        TableRow(4, Team(108, "Lille OSC"), 11, 6, 3, 2, 21, 20, 14),
        TableRow(5, Team(111, "AS Monaco"), 11, 5, 3, 3, 18, 18, 15),
        TableRow(6, Team(112, "OGC Nice"), 11, 4, 3, 4, 15, 15, 16)
    )
}
