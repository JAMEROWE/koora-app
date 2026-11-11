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
 * v1.9.0 (spec §4.2) — does an event's player name belong to this
 * starter? Both sides come from the SAME provider (ESPN display names);
 * events sometimes abbreviate the first name ("B. Saka"). Rule: the
 * SURNAME (last distinctive token) must be equal — nothing looser, so a
 * goal can never be pinned on the wrong player. Two starters sharing one
 * surname would both receive the badge; that ambiguity is inherent to
 * the free data and is disclosed here rather than guessed around.
 * Pure, tested.
 */
internal fun playerMatches(eventPlayer: String, starterName: String): Boolean {
    fun toks(s: String): Set<String> = s.lowercase()
        .replace(Regex("[^\\p{L}\\p{Nd} ]"), " ")
        .split(Regex("\\s+"))
        .filter { it.length >= 3 && it !in setOf("jr", "sr") }
        .toSet()
    val e = toks(eventPlayer)
    val s = toks(starterName)
    if (e.isEmpty() || s.isEmpty()) return false
    return e.last() == s.last()
}

/**
 * v1.9.0 (spec §5) — stat NUMBERS must stay WCAG-readable on top of the
 * team kit color: in dark theme a dark kit is lifted toward white, in
 * light theme a near-white kit is pushed toward black. Pure ARGB math
 * (no Compose types) so it is unit-testable. Returns a new ARGB int.
 */
internal fun statNumberColor(kitBg: Int, darkTheme: Boolean): Int {
    fun r(c: Int) = (c shr 16) and 0xFF
    fun g(c: Int) = (c shr 8) and 0xFF
    fun b(c: Int) = c and 0xFF
    // Relative luminance (sRGB approximation, WCAG formula).
    val lum = 0.2126 * r(kitBg) / 255.0 + 0.7152 * g(kitBg) / 255.0 + 0.0722 * b(kitBg) / 255.0
    val mixWith = if (darkTheme) 0xFF else 0x00
    val t = if (darkTheme) {
        if (lum < 0.30) 0.55 else 0.0
    } else {
        if (lum > 0.72) 0.45 else 0.0
    }
    if (t == 0.0) return kitBg
    val nr = (r(kitBg) + (mixWith - r(kitBg)) * t).toInt().coerceIn(0, 255)
    val ng = (g(kitBg) + (mixWith - g(kitBg)) * t).toInt().coerceIn(0, 255)
    val nb = (b(kitBg) + (mixWith - b(kitBg)) * t).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
}

/**
 * v1.9.0 (spec §4.3) — structural position group from the formation rows:
 * index 0 is the goalkeeper; the first outfield band defends, the last
 * attacks, everything between is midfield. This is the formation's own
 * structure, NOT an invented per-player role. Returns a stable key that
 * the UI translates (pos_gk / pos_df / pos_mf / pos_fw).
 */
internal fun positionLabel(rows: List<Int>, starterIndex: Int): String {
    if (rows.isEmpty() || starterIndex < 0 || starterIndex >= rows.sum()) return "MF"
    if (starterIndex == 0) return "GK"
    val outfieldRows = rows.drop(1)
    var i = starterIndex - 1
    for ((band, count) in outfieldRows.withIndex()) {
        if (i < count) {
            return when (band) {
                0 -> "DF"
                outfieldRows.size - 1 -> "FW"
                else -> "MF"
            }
        }
        i -= count
    }
    return "MF"
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
