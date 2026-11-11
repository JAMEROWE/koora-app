package com.koora.app.data

import com.google.gson.Gson
import com.koora.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

/*
 * v1.7.0 — "MAX ATTRACTIVE LEAGUES" via PitchAPI (pitchapi.dev, free key).
 *
 * WHY THIS EXISTS: the football-data.org free tier covers exactly 12
 * competitions. PitchAPI's free plan covers 77 — including two the app
 * promised but never had (Europa League, Conference League) and major
 * leagues football-data does not offer at all: Saudi Pro League, Süper
 * Lig, Argentina, Liga MX, MLS, Scotland, Belgium, J1, K1.
 *
 * MERGE RULE (cover each other, never double): football-data.org stays
 * the PRIMARY source for the 12 competitions it already serves. The
 * PitchAPI whitelist below contains ONLY leagues football-data does not
 * offer, keyed by PitchAPI's stable league ID (names like "Serie A" or
 * "Premier League" are ambiguous across countries — IDs are not).
 *
 * QUOTA MODEL: TWO requests per DATE (plain call = finished+live,
 * ?status=upcoming = scheduled), cached per day on its own TTL exactly
 * like the API-Football layer:
 *   - past days: fetched once per local day (results never change),
 *   - today: at most every 10 minutes,
 *   - future days: at most every 30 minutes.
 * PitchAPI advertises no hard per-minute limit (fair-use burst guard).
 *
 * WITHOUT the PITCH_API_KEY build value this module is fully asleep:
 * no network, no UI change.
 *
 * Only whitelisted competitions are imported, so the window never floods
 * with the ~77 unrelated leagues PitchAPI also returns per day. Unknown
 * matches are skipped — we never show data we cannot label (no-fake rule).
 */
object PitchFixtures {

    private const val BASE = "https://api.pitchapi.dev"

    /** football-data ids stay far below 50M; API-Football 50-60M; PitchAPI above 80M. */
    const val MATCH_ID_OFFSET = 80_000_000L
    const val TEAM_ID_OFFSET = 90_000_000L
    private const val LEAGUE_ID_OFFSET = 70_000_000L
    private const val ID_SPACE = 10_000_000L

    val available: Boolean get() = BuildConfig.PITCH_API_KEY.isNotBlank()

    /**
     * PitchAPI league ID -> Koora competition code. Whitelist = leagues
     * football-data.org does NOT cover (IDs from /v1/leagues, free=true,
     * verified 2026-11-11). UEL/UECL reuse the codes reserved in v1.6.1.
     */
    private val LEAGUE_CODES = mapOf(
        "l_38d9HA" to "UEL",   // Europa League
        "l_2WZ2tt" to "UECL",  // Conference League
        "l_0Lpz1f" to "SPL",   // Saudi Pro League
        "l_0S1uaf" to "TUR",   // Süper Lig
        "l_45VZuL" to "ARG",   // Liga Profesional (Argentina)
        "l_3v84VE" to "MEX",   // Liga MX
        "l_0eg6C9" to "MLS",   // Major League Soccer
        "l_1LMdEO" to "SCO",   // Premiership (Scotland)
        "l_2L6d1F" to "BEL",   // First Division A (Belgium)
        "l_2ApGa4" to "JPN",   // J. League
        "l_2EKrnc" to "KOR"    // K League 1
    )

    /** Koora code for a PitchAPI league id, or null to skip the league. */
    fun codeFor(leagueId: String?): String? = leagueId?.let { LEAGUE_CODES[it] }

    /** PitchAPI status -> football-data.org style status string. */
    fun statusOf(raw: String?): String {
        val s = (raw ?: return "TIMED").lowercase()
        return when {
            s == "finished" -> "FINISHED"
            s == "not_started" -> "TIMED"
            s.contains("progress") || s.contains("half") ||
                s.contains("live") || s.contains("pause") -> "IN_PLAY"
            s.contains("postponed") -> "POSTPONED"
            s.contains("suspended") -> "SUSPENDED"
            s.contains("cancel") -> "CANCELLED"
            else -> "TIMED"   // unknown: treat as scheduled (safe side for votes)
        }
    }

    /** Stable numeric id from a PitchAPI string id ("m_3AQ6Oe" -> 80M..90M). */
    fun matchIdFrom(pitchId: String?): Long =
        MATCH_ID_OFFSET + crc(pitchId) % ID_SPACE

    fun teamIdFrom(pitchId: String?): Long =
        TEAM_ID_OFFSET + crc(pitchId) % ID_SPACE

    private fun crc(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        val c = CRC32()
        c.update(s.toByteArray(Charsets.UTF_8))
        return c.value
    }

    private fun leagueLongId(leagueId: String): Long =
        LEAGUE_ID_OFFSET + crc(leagueId) % 1_000_000L

    /** One PitchAPI match -> our Match, or null when not whitelisted/incomplete. */
    fun toMatch(f: PitchFix): Match? {
        val lg = f.league ?: return null
        val home = f.home_team ?: return null
        val away = f.away_team ?: return null
        val code = codeFor(lg.id) ?: return null
        val utc = f.time_utc ?: return null
        val homeName = home.name ?: return null
        val awayName = away.name ?: return null
        return Match(
            id = matchIdFrom(f.id),
            utcDate = utc,
            status = statusOf(f.status),
            homeTeam = Team(id = teamIdFrom(home.id), name = homeName, tla = AfFixtures.tlaOf(homeName)),
            awayTeam = Team(id = teamIdFrom(away.id), name = awayName, tla = AfFixtures.tlaOf(awayName)),
            score = Score(fullTime = ScoreDetail(home = f.score_home, away = f.score_away)),
            competition = CompetitionRef(
                id = leagueLongId(lg.id ?: ""),
                code = code,
                name = lg.name ?: ""
            )
        )
    }

    /**
     * Raw network fetch of one local day ("yyyy-MM-dd"): the plain date
     * call (finished + live) merged with the upcoming call (scheduled).
     * Throws on any failure — the caller falls back to the cache.
     */
    suspend fun fetchDay(day: String): List<Match> = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val gson = Gson()
        val merged = LinkedHashMap<String, Match>()
        for (suffix in listOf("", "?status=upcoming")) {
            val req = Request.Builder()
                .url("$BASE/v1/date/$day$suffix")
                .header("X-API-KEY", BuildConfig.PITCH_API_KEY)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "Pitch HTTP ${resp.code}" }
                val body = resp.body?.string() ?: throw IllegalStateException("Pitch empty body")
                val parsed = gson.fromJson(body, PitchResp::class.java)
                parsed.data?.matches.orEmpty().forEach { f ->
                    val m = toMatch(f) ?: return@forEach
                    val key = f.id ?: return@forEach
                    merged[key] = m
                }
            }
        }
        merged.values.sortedBy { it.utcDate }
    }
}

/* ---------- PitchAPI /v1/date DTOs (parsed with Gson) ---------- */

data class PitchResp(val data: PitchDay? = null)

data class PitchDay(val date: String? = null, val matches: List<PitchFix> = emptyList())

data class PitchFix(
    val id: String? = null,
    val league: PitchLg? = null,
    val home_team: PitchTm? = null,
    val away_team: PitchTm? = null,
    val time_utc: String? = null,
    val status: String? = null,
    val score_home: Int? = null,
    val score_away: Int? = null
)

data class PitchLg(val id: String? = null, val name: String? = null)

data class PitchTm(val id: String? = null, val name: String? = null)
