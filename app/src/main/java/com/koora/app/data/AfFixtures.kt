package com.koora.app.data

import com.google.gson.Gson
import com.koora.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/*
 * v1.6.1 — CUP + BOTOLA PRO coverage via API-Football (api-football.com, v3).
 *
 * WHY THIS EXISTS: the football-data.org FREE tier covers exactly 12
 * competitions and not a single domestic cup — Copa del Rey, FA Cup,
 * Coppa Italia, DFB-Pokal, Coupe de France, the Europa/Conference League
 * and Botola Pro are all paid tiers there (or absent entirely). API-Football's
 * free plan covers all of them (quota: 100 requests/day).
 *
 * COST MODEL: ONE request per DATE returns every fixture of every competition
 * for that day. MatchRepository therefore pays at most (window day count)
 * requests per refresh round, and caches each date on its own TTL:
 *   - past days: fetched once per local day (results never change),
 *   - today: at most every 10 minutes,
 *   - future days: at most every 30 minutes.
 * That keeps a realistic day of use far below the 100/day free quota.
 *
 * WITHOUT the API_FOOTBALL_KEY build value this module is fully asleep:
 * no network, no UI change — the app behaves exactly like v1.6.0.
 *
 * Only whitelisted competitions are imported, so the window never floods
 * with the ~50 unrelated leagues API-Football also returns per day. Unknown
 * matches are skipped — we never show data we cannot label (no-fake-data rule).
 */
object AfFixtures {

    private const val BASE = "https://v3.football.api-sports.io"

    /** football-data ids stay far below this; API-Football ids live above it. */
    const val ID_OFFSET = 50_000_000L

    val available: Boolean get() = BuildConfig.API_FOOTBALL_KEY.isNotBlank()

    /** League-name -> Koora competition code (case-insensitive contains). */
    private val LEAGUE_RULES = listOf(
        "copa del rey" to "CDR",
        "fa cup" to "FAC",
        "coppa italia" to "COP",
        "dfb pokal" to "DFB",
        "coupe de france" to "CLG",
        "europa league" to "UEL",
        "conference league" to "UECL",
        "botola" to "BOT"          // Botola Pro (Morocco) — our home league
    )

    /** Names that must NOT match the rules above (youth/women/quals noise). */
    private val NAME_EXCLUSIONS = listOf("women", "qualification", "youth", "friendly")

    /** Koora code for an API-Football league name, or null to skip the league. */
    fun codeFor(leagueName: String?): String? {
        val n = (leagueName ?: return null).lowercase()
        if (NAME_EXCLUSIONS.any { n.contains(it) }) return null
        return LEAGUE_RULES.firstOrNull { n.contains(it.first) }?.second
    }

    /** API-Football short status -> football-data.org style status string. */
    fun statusOf(short: String?): String = when (short?.uppercase()) {
        "FT", "AET", "PEN" -> "FINISHED"
        "1H", "HT", "2H", "ET", "BT", "P", "LIVE", "INT" -> "IN_PLAY"
        "PST" -> "POSTPONED"
        "SUSP", "ABD" -> "SUSPENDED"
        "CANC" -> "CANCELLED"
        else -> "TIMED"   // NS, TBD and anything unknown: treat as scheduled
    }

    /** "2026-11-12T20:00:00+00:00" -> "2026-11-12T20:00:00Z" (UTC; AF is UTC). */
    fun normalizeDate(afDate: String): String =
        afDate
            .replace(Regex("\\+00:00$"), "Z")
            .replace(Regex("\\.\\d+Z$"), "Z")

    /** Short badge letters from the team name: "Valencia" -> "VAL". */
    fun tlaOf(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val words = name.replace(Regex("[^A-Za-z ]"), " ")
            .trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        return if (words.size == 1) words[0].take(3).uppercase()
        else words.take(3).map { it.first() }.joinToString("").uppercase()
    }

    /** One API-Football fixture -> our Match, or null when not whitelisted. */
    fun toMatch(f: AfFix): Match? {
        val fix = f.fixture ?: return null
        val lg = f.league ?: return null
        val ts = f.teams ?: return null
        val home = ts.home ?: return null
        val away = ts.away ?: return null
        val code = codeFor(lg.name) ?: return null
        val name = home.name ?: return null
        val nameAway = away.name ?: return null
        return Match(
            id = ID_OFFSET + fix.id,
            utcDate = normalizeDate(fix.date),
            status = statusOf(fix.status?.short),
            homeTeam = Team(id = ID_OFFSET + home.id, name = name, tla = tlaOf(name)),
            awayTeam = Team(id = ID_OFFSET + away.id, name = nameAway, tla = tlaOf(nameAway)),
            score = Score(fullTime = ScoreDetail(home = f.goals?.home, away = f.goals?.away)),
            competition = CompetitionRef(id = lg.id, code = code, name = lg.name ?: "")
        )
    }

    /**
     * Raw network fetch of one local day ("yyyy-MM-dd"). Throws on any
     * failure (HTTP, auth, quota) — the caller falls back to the cache.
     * API-Football signals quota/auth problems via the "errors" field even
     * on HTTP 200, so both paths are checked.
     */
    suspend fun fetchDay(day: String): List<Match> = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder()
            .url("$BASE/fixtures?date=$day")
            .header("x-apisports-key", BuildConfig.API_FOOTBALL_KEY)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "AF HTTP ${resp.code}" }
            val body = resp.body?.string() ?: throw IllegalStateException("AF empty body")
            val parsed = Gson().fromJson(body, AfResp::class.java)
            val errs = parsed.errors
            val hasErrors = when (errs) {
                null -> false
                is List<*> -> errs.isNotEmpty()
                is Map<*, *> -> errs.isNotEmpty()
                else -> errs.toString().isNotBlank()
            }
            check(!hasErrors) { "AF errors: $errs" }
            parsed.response.mapNotNull { toMatch(it) }.sortedBy { it.utcDate }
        }
    }
}

/* ---------- API-Football v3 /fixtures DTOs (parsed with Gson) ---------- */

data class AfResp(
    val response: List<AfFix> = emptyList(),
    val errors: Any? = null
)

data class AfFix(
    val fixture: AfFx? = null,
    val league: AfLg? = null,
    val teams: AfTs? = null,
    val goals: AfGl? = null
)

data class AfFx(val id: Long = 0, val date: String = "", val status: AfSt? = null)
data class AfSt(val short: String? = null, val elapsed: Int? = null)
data class AfLg(val id: Long = 0, val name: String? = null, val country: String? = null)
data class AfTs(val home: AfTm? = null, val away: AfTm? = null)
data class AfTm(val id: Long = 0, val name: String? = null)
data class AfGl(val home: Int? = null, val away: Int? = null)
