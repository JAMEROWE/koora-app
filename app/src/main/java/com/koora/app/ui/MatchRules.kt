package com.koora.app.ui

import com.koora.app.data.Match
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/*
 * RULES LAYER (v1.5.6, extended v1.6.0) — plain logic with no screens and
 * no network. The Live and Results screens only render what these pure
 * functions return, so a sorting, day-split or section bug is fixed once,
 * here, and covered by tests.
 *
 * Everything is unit-testable: no Android classes, injectable "now".
 */

data class Competition(val code: String, val label: String)

/**
 * Real competitions, in display order. Botola Pro first — our home
 * audience. BOT has no football-data.org resource yet (demo stamps it
 * locally; the API-Football upgrade brings the real one).
 */
val ALL_COMPETITIONS = listOf(
    Competition("BOT", "Botola Pro"),
    Competition("CL", "Champions League"),
    Competition("PL", "Premier League"),
    Competition("PD", "La Liga"),
    Competition("SA", "Serie A"),
    Competition("BL1", "Bundesliga"),
    Competition("FL1", "Ligue 1"),
    // v1.6.1: cups + European nights, served by the API-Football layer
    // (football-data.org free tier has none of these).
    Competition("CDR", "Copa del Rey"),
    Competition("FAC", "FA Cup"),
    Competition("COP", "Coppa Italia"),
    Competition("DFB", "DFB-Pokal"),
    Competition("CLG", "Coupe de France"),
    Competition("UEL", "Europa League"),
    Competition("UECL", "Conference League"),
    // v1.7.0: "max attractive leagues" — served by the PitchAPI layer
    // (football-data.org free tier offers none of these).
    Competition("SPL", "Saudi Pro League"),
    Competition("TUR", "Süper Lig"),
    Competition("ARG", "Liga Profesional"),
    Competition("MEX", "Liga MX"),
    Competition("MLS", "MLS"),
    Competition("SCO", "Scottish Premiership"),
    Competition("BEL", "Belgian Pro League"),
    Competition("JPN", "J. League"),
    Competition("KOR", "K League 1"),
    Competition("ELC", "Championship"),
    Competition("DED", "Eredivisie"),
    Competition("PPL", "Primeira Liga"),
    Competition("BSA", "Brasileirão"),
    Competition("CLI", "Copa Libertadores"),
    Competition("WC", "World Cup"),
    Competition("EC", "Euro")
)

/** Code -> human league name (fallback when a match carries no name). */
val competitionLabels = ALL_COMPETITIONS.associate { it.code to it.label }

private val utcParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun utcMillis(m: Match): Long? = runCatching {
    utcParser.parse(m.utcDate)?.time
}.getOrNull()

// ---- Day split (device timezone) ---------------------------------------

/** "yyyy-MM-dd" of a kickoff in the user's local day, or null when unparseable. */
fun localDayKey(m: Match): String? {
    val ms = utcMillis(m) ?: return null
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
}

/** Local day key of "now" plus [plusDays] (Calendar handles DST/month rolls). */
fun localDayKeyAt(nowMs: Long, plusDays: Int = 0): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMs
    if (plusDays != 0) cal.add(Calendar.DAY_OF_YEAR, plusDays)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

/** Matches whose LOCAL day equals [dayKey] ("yyyy-MM-dd"). Unparseable dates drop out. */
fun matchesOnDay(matches: List<Match>, dayKey: String): List<Match> =
    matches.filter { localDayKey(it) == dayKey }

// ---- Ordering -----------------------------------------------------------

/**
 * Mixed day list: LIVE first, then upcoming by soonest kickoff, then
 * finished results newest first.
 */
fun sortMixed(matches: List<Match>): List<Match> {
    val live = matches.filter { isLive(it) }.sortedBy { it.utcDate }
    val upcoming = matches.filter { !isLive(it) && !it.status.equals("FINISHED", true) }
        .sortedBy { it.utcDate }
    val finished = matches.filter { it.status.equals("FINISHED", true) }
        .sortedByDescending { it.utcDate }
    return live + upcoming + finished
}

/** Finished matches only, newest first (Results tabs). */
fun sortResultsNewestFirst(matches: List<Match>): List<Match> =
    matches.filter { it.status.equals("FINISHED", true) }.sortedByDescending { it.utcDate }

/** Not-yet-finished matches only, soonest kickoff first (Upcoming tabs). */
fun sortUpcomingSoonestFirst(matches: List<Match>): List<Match> =
    matches.filter { !it.status.equals("FINISHED", true) }.sortedBy { it.utcDate }

// ---- Pagination ---------------------------------------------------------

/** First [visible] rows of an already-sorted list ("load more on scroll"). */
fun paginate(matches: List<Match>, visible: Int): List<Match> =
    if (visible <= 0) emptyList() else matches.take(visible)

// ---- Votes (stage 1: local pick, locked at kick-off) ---------------------

enum class VotePick(val key: String) { HOME("HOME"), DRAW("DRAW"), AWAY("AWAY") }

/**
 * One pick per user per match, locked at kick-off: live, finished,
 * postponed/cancelled matches and matches whose kickoff time has passed
 * (even if the provider status lags) can no longer be voted on.
 * An unparseable kickoff time locks the vote (safe side).
 */
fun voteLocked(m: Match, nowMs: Long): Boolean {
    if (isLive(m)) return true
    val s = m.status.uppercase(Locale.US)
    if (s in setOf("FINISHED", "POSTPONED", "SUSPENDED", "CANCELED", "CANCELLED")) return true
    val t = utcMillis(m) ?: return true
    return t <= nowMs
}

// ---- Votes stage 2 (v1.10.0, spec §2.3): reactions + post-match check ----

/**
 * Spec §2.3 quick reaction chips. They are the USER'S OWN reactions,
 * stored on the phone like the pick — the community variant arrives only
 * with a real backend, and until then nothing here pretends otherwise.
 */
val VOTE_REACTIONS = listOf("clean_sheet", "goal_fest", "upset_alert")

/**
 * Was the pick right at full time? null while the result is unknown
 * (match not finished, or the provider never gave a final score).
 */
fun pickResult(m: Match, pick: String?): Boolean? {
    if (pick == null) return null
    if (!m.status.equals("FINISHED", true)) return null
    val h = m.score.fullTime?.home ?: return null
    val a = m.score.fullTime?.away ?: return null
    return when (pick) {
        VotePick.HOME.key -> h > a
        VotePick.AWAY.key -> a > h
        VotePick.DRAW.key -> h == a
        else -> null
    }
}

/**
 * Personal prediction accuracy over every pick that HAS a final result:
 * (correct, counted). Unresolved picks are never counted — no fake score.
 */
fun accuracyOf(picks: Map<Long, String>, matches: List<Match>): Pair<Int, Int> {
    var correct = 0
    var counted = 0
    matches.forEach { m ->
        when (pickResult(m, picks[m.id])) {
            true -> { correct++; counted++ }
            false -> counted++
            null -> {}
        }
    }
    return correct to counted
}

// ---- Team panel ----------------------------------------------------------

/**
 * A team's next 5 matches (soonest first — live ones included, they are
 * "now") and last 5 results (newest first), across whatever competitions
 * the list contains. Pure: the screen renders exactly this.
 */
fun next5Last5(all: List<Match>, teamId: Long, nowMs: Long, count: Int = 5): Pair<List<Match>, List<Match>> {
    val team = all.filter { it.homeTeam.id == teamId || it.awayTeam.id == teamId }
    val next = team.filter { !it.status.equals("FINISHED", true) }
        .sortedBy { it.utcDate }
        .take(count)
    val recent = team.filter { it.status.equals("FINISHED", true) }
        .sortedByDescending { it.utcDate }
        .take(count)
    return next to recent
}

// ---- League blocks -------------------------------------------------------

/**
 * Group matches into per-league blocks by the competition code each match
 * carries (football-data.org embeds it). Matches without a competition
 * reference never produce a fake league block.
 */
fun leagueBlocks(matches: List<Match>): Map<String, List<Match>> =
    matches
        .filter { !it.competition?.code.isNullOrBlank() }
        .groupBy { it.competition!!.code }

// ---- Day sections (v1.6.0 — the Live/Results screen structure) -----------

/** One league block of a day's matches (rendered under a LeagueHeader). */
data class LeagueSection(val code: String, val matches: List<Match>)

/**
 * The day grouped per league: ALL_COMPETITIONS order first (Botola Pro,
 * CL, PL, ...), unknown leagues after, matches without a competition
 * (should not happen, but demo/odd feeds) in the very first block so a
 * screen is never empty-shaped.
 */
fun daySections(dayMatches: List<Match>): List<LeagueSection> =
    dayMatches.groupBy { it.competition?.code ?: "" }
        .map { (code, ms) -> LeagueSection(code, ms) }
        .sortedBy { sec ->
            when {
                sec.code.isEmpty() -> -1
                else -> ALL_COMPETITIONS.indexOfFirst { it.code == sec.code }
                    .let { if (it >= 0) it else 999 }
            }
        }

// ---- Accordion default state (v1.10.0, spec §2.2) -------------------------

/**
 * Smart default for a league section: EXPAND when it holds a live match
 * (the user opened Live to watch something) or a favorited match, and
 * also when the whole block is small (three sections or fewer — a quiet
 * matchday must not hide its matches behind three taps). Everything else
 * stays collapsed by default, exactly as spec §2.2 prescribes. Pure,
 * unit-tested.
 */
fun sectionExpandedDefault(hasLive: Boolean, hasFavorite: Boolean, sectionsInBlock: Int): Boolean =
    hasLive || hasFavorite || sectionsInBlock <= 3
