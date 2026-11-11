package com.koora.app.ui

import com.koora.app.data.Match
import com.koora.app.data.TeamColors
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Small formatting helpers shared by all screens. */

private val utcParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun isLive(m: Match): Boolean = m.status == "LIVE" || m.status == "IN_PLAY"

/** Kickoff time in the user's local timezone, e.g. "19:00". */
fun kickoffLabel(m: Match): String = try {
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(utcParser.parse(m.utcDate)!!)
} catch (_: Exception) {
    m.utcDate.take(16).replace('T', ' ')
}

/** Full date for the detail screen, e.g. "Sat 9 Nov, 19:00". */
fun dateLabel(m: Match): String = try {
    SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(utcParser.parse(m.utcDate)!!)
} catch (_: Exception) {
    m.utcDate.take(16).replace('T', ' ')
}

/** "2 - 1" when a score exists, otherwise the kickoff time. */
fun scoreLabel(m: Match): String {
    val ft = m.score.fullTime
    return if (ft?.home != null && ft.away != null) "${ft.home} - ${ft.away}" else kickoffLabel(m)
}

/** Small badge under the score: LIVE / FT / kickoff time. */
fun statusLabel(m: Match): String = when {
    isLive(m) -> "LIVE"
    m.status == "FINISHED" -> "FT"
    else -> kickoffLabel(m)
}

/** 1 for a club in the known-colors map ("famous"), else 0. Pure, unit-tested. */
fun fameScore(m: Match): Int =
    (if (TeamColors.isKnown(m.homeTeam.name)) 1 else 0) +
        (if (TeamColors.isKnown(m.awayTeam.name)) 1 else 0)

/**
 * v1.8.0 — event minute as a number: "45+1" -> 46.0, "90" -> 90.0.
 * Returns null for anything unparseable (the timeline simply skips it —
 * we never guess a position for an event).
 */
internal fun minuteValue(minute: String): Double? {
    val clean = minute.trim().trimEnd('\'', '’', 'm', 'M', '”', '\"')
    val parts = clean.split("+")
    val base = parts.getOrNull(0)?.trim()?.toDoubleOrNull() ?: return null
    if (base < 0.0) return null
    val extra = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
    if (extra != null && extra < 0.0) return null
    return base + (extra ?: 0.0)
}

/**
 * v1.8.0 — "4-2-3-1" -> [1, 4, 2, 3, 1] (GK first). Returns null for
 * malformed formations; callers fall back to the text lineup list rather
 * than inventing positions (no-fake rule).
 */
internal fun formationRows(formation: String?): List<Int>? {
    val f = formation?.trim() ?: return null
    val rows = f.split("-").map { it.trim().toIntOrNull() ?: return null }
    if (rows.size < 2 || rows.size > 5) return null
    if (rows.any { it <= 0 }) return null
    if (rows.sum() != 10) return null   // 10 outfield players + GK
    return listOf(1) + rows
}

/**
 * Match of the Day pick: a LIVE match always wins; otherwise the most
 * "famous" non-finished match (earliest kickoff as tie-break); falls back
 * to any match. Pure function — unit-tested in DesignLogicTest.
 */
fun pickMatchOfTheDay(matches: List<Match>): Match? {
    if (matches.isEmpty()) return null
    val live = matches.filter { isLive(it) }
    val pool = live.ifEmpty {
        matches.filter { !it.status.equals("FINISHED", true) }.ifEmpty { matches }
    }
    return pool.sortedWith(
        compareByDescending<Match> { fameScore(it) }.thenBy { it.utcDate }
    ).first()
}
