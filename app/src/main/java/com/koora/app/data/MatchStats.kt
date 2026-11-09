package com.koora.app.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.koora.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/*
 * PER-MATCH DEEP STATS: events (goals / yellow & red cards / subs),
 * official lineups and match statistics.
 *
 * football-data.org's FREE tier does NOT include events or lineups
 * (verified live on 2026-11-09: the /v4/matches/{id} response returns
 * both null). This data comes from API-Football (api-sports.io), whose
 * FREE plan includes ALL endpoints with a 100 requests/day quota
 * (reset at 00:00 UTC). We only call it when a user opens a match
 * detail screen and we cache the result in memory, so a small user
 * base stays far below the quota. When the app outgrows it, the $19/mo
 * plan removes the cap — zero code change (same key, same code).
 *
 * Mapping: football-data and API-Football use different match ids, so
 * we resolve the API-Football fixture by date + league + team-name
 * fuzzy match, then fetch the full fixture (events + lineups +
 * statistics arrive in ONE response for ?id=).
 *
 * Key wiring: local.properties / GitHub secret API_FOOTBALL_KEY.
 * No key -> sections hidden on real data, demo sample in demo mode.
 */

data class StatsEvent(
    val minute: String,     // "45+1"
    val side: Int,          // 0 = home, 1 = away
    val kind: String,       // "goal" | "yellow" | "red" | "sub"
    val detail: String,     // "Penalty", "Own goal", "Second yellow"...
    val player: String,
    val other: String?      // assist / player replaced
)

data class StatsPlayer(val number: String?, val name: String)

data class StatsLineup(
    val team: String,
    val formation: String?,
    val coach: String?,
    val starters: List<StatsPlayer>,
    val subsCount: Int
)

data class StatsBar(val label: String, val home: String, val away: String, val homeFraction: Float?)

data class MatchStats(
    val homeShort: String = "",
    val awayShort: String = "",
    val events: List<StatsEvent> = emptyList(),
    val lineups: List<StatsLineup> = emptyList(),
    val bars: List<StatsBar> = emptyList()
)

object MatchStatsRepository {

    // football-data competition code -> API-Football league id
    private val leagueMap = mapOf(
        "PL" to 39,   // Premier League
        "PD" to 140,  // LaLiga
        "SA" to 135,  // Serie A
        "BL1" to 78,  // Bundesliga
        "FL1" to 61,  // Ligue 1
        "CL" to 2     // UEFA Champions League
    )

    private val hasKey: Boolean get() = BuildConfig.API_FOOTBALL_KEY.isNotBlank()
    private val mem = mutableMapOf<Long, MatchStats?>()
    private val gson = Gson()

    // ------------------------------------------------------------------
    // Demo sample — Raja 2-1 Wydad flavored, shown in demo mode so the
    // feature is visible before any key exists.
    // ------------------------------------------------------------------
    fun demo(match: Match): MatchStats = MatchStats(
        homeShort = match.homeTeam.tla ?: "HOM",
        awayShort = match.awayTeam.tla ?: "AWY",
        events = listOf(
            StatsEvent("12", 0, "yellow", "", "M. Benama", null),
            StatsEvent("28", 0, "goal", "", "A. Sbai", "M. Zabiri"),
            StatsEvent("45+1", 1, "goal", "", "C. Bangre", "G. Kanga"),
            StatsEvent("61", 0, "sub", "", "Y. Belammari", "O. Marzouki"),
            StatsEvent("79", 1, "red", "Second yellow", "G. Kanga", null),
            StatsEvent("90+3", 0, "goal", "Penalty", "M. Zabiri", null)
        ),
        lineups = listOf(
            StatsLineup(
                match.homeTeam.shortName ?: match.homeTeam.name, "4-3-3", "J. Sektioui",
                listOf(
                    StatsPlayer("1", "M. El Kamel"), StatsPlayer("3", "Y. Belammari"), StatsPlayer("4", "A. Sbai"), StatsPlayer("5", "M. Benama"), StatsPlayer("2", "H. Mouaddib"), StatsPlayer("8", "O. Marzouki"), StatsPlayer("6", "N. Zouheiry"), StatsPlayer("10", "M. Zabiri"), StatsPlayer("7", "H. Rakraki"), StatsPlayer("9", "M. Rabeh"), StatsPlayer("11", "Y. El Fahimi")
                ),
                7
            ),
            StatsLineup(
                match.awayTeam.shortName ?: match.awayTeam.name, "4-2-3-1", "R. El Amrani",
                listOf(
                    StatsPlayer("1", "A. Lekrati"), StatsPlayer("26", "Y. Atouch"), StatsPlayer("18", "J. Nkoma"), StatsPlayer("4", "B. Martaud"), StatsPlayer("5", "V. Betoukoumi"), StatsPlayer("6", "M. El Moussaoui"), StatsPlayer("8", "A. Abahaf"), StatsPlayer("10", "C. Bangre"), StatsPlayer("7", "G. Kanga"), StatsPlayer("11", "S. Tiaib"), StatsPlayer("9", "E. Aguerd")
                ),
                5
            )
        ),
        bars = listOf(
            StatsBar("possession", "54%", "46%", 0.54f),
            StatsBar("shots", "13", "9", 13f / 22f),
            StatsBar("shots_on", "6", "3", 6f / 9f),
            StatsBar("corners", "7", "4", 7f / 11f),
            StatsBar("fouls", "11", "14", 11f / 25f),
            StatsBar("yellow", "2", "3", 2f / 5f),
            StatsBar("red", "0", "1", 0f),
            StatsBar("offsides", "3", "2", 3f / 5f),
            StatsBar("saves", "2", "5", 2f / 7f)
        )
    )

    /** Entry point. demo=true -> sample data. No key -> null (sections hidden). */
    suspend fun statsFor(match: Match, compCode: String, demo: Boolean): MatchStats? =
        withContext(Dispatchers.IO) {
            if (demo) return@withContext demo(match)
            if (!hasKey) return@withContext null
            synchronized(mem) { mem[match.id] }?.let { return@withContext it }
            val resolved = runCatching { resolve(match, compCode) }.getOrNull()
            synchronized(mem) { mem[match.id] = resolved }
            resolved
        }

    // ------------------------------------------------------------------
    // API-Football (api-sports.io v3)
    // ------------------------------------------------------------------

    private fun resolve(match: Match, compCode: String): MatchStats? {
        val league = leagueMap[compCode.trim().uppercase()] ?: return null
        val date = match.utcDate.take(10)
        val season = seasonOf(date)

        val byDay = call("fixtures?date=$date&league=$league&season=$season")
        val fixture = byDay.response.firstOrNull { f ->
            nameMatches(f.teams?.home?.name ?: "", match.homeTeam.name) &&
                nameMatches(f.teams?.away?.name ?: "", match.awayTeam.name)
        } ?: return null

        val full = call("fixtures?id=${fixture.fixture?.id ?: return null}")
            .response.firstOrNull() ?: return null
        return toStats(full, match)
    }

    private fun seasonOf(date: String): Int {
        val y = date.substring(0, 4).toIntOrNull() ?: 2026
        val m = date.substring(5, 7).toIntOrNull() ?: 8
        return if (m >= 7) y else y - 1
    }

    private fun call(path: String): AfResponse {
        val conn = URL("https://v3.football.api-sports.io/$path").openConnection()
            as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("x-apisports-key", BuildConfig.API_FOOTBALL_KEY)
        try {
            val text = conn.inputStream.use { it.readBytes() }.decodeToString()
            return gson.fromJson(text, AfResponse::class.java) ?: AfResponse()
        } finally {
            conn.disconnect()
        }
    }

    private fun toStats(f: AfFixtureNode, match: Match): MatchStats {
        val homeNorm = normalize(match.homeTeam.name)
        val awayNorm = normalize(match.awayTeam.name)

        fun sideOf(teamName: String?): Int {
            val n = normalize(teamName ?: "")
            return if (n.isNotEmpty() && homeNorm.contains(n)) 0 else 1
        }

        val events = (f.events ?: emptyList()).filter { !it.type.equals("var", true) }.map { e ->
            val kind = when {
                e.type.equals("goal", true) -> "goal"
                e.type.equals("card", true) -> {
                    val d = e.detail ?: ""
                    if (d.contains("red", true) || d.contains("second yellow", true)) "red" else "yellow"
                }
                e.type.equals("subst", true) -> "sub"
                else -> "goal"
            }
            StatsEvent(
                minute = buildString {
                    append(e.time?.elapsed ?: 0)
                    e.time?.extra?.let { append("+$it") }
                },
                side = sideOf(e.team?.name),
                kind = kind,
                detail = e.detail ?: "",
                player = e.player?.name ?: "",
                other = e.assist?.name
            )
        }

        val lineups = (f.lineups ?: emptyList()).map { l ->
            StatsLineup(
                team = l.team?.name ?: "",
                formation = l.formation,
                coach = l.coach?.name,
                starters = l.startXI.take(11).mapNotNull { x ->
                    x.player?.name?.let { StatsPlayer(x.player?.number, it) }
                },
                subsCount = l.subs.size
            )
        }.let { pair ->
            // order [home, away]
            val h = pair.firstOrNull { homeNorm.contains(normalize(it.team)) }
            val a = pair.firstOrNull { awayNorm.contains(normalize(it.team)) }
            listOfNotNull(h, a)
        }

        val hStats = f.statistics?.firstOrNull { homeNorm.contains(normalize(it.team?.name ?: "")) }
        val aStats = f.statistics?.firstOrNull { it !== hStats }
        val bars = (hStats?.statistics ?: emptyList()).mapIndexed { i, e ->
            val hv = e.value ?: ""
            val av = aStats?.statistics?.getOrNull(i)?.value ?: ""
            StatsBar(label = e.type ?: "", home = hv, away = av, homeFraction = fraction(hv, av))
        }

        return MatchStats(
            homeShort = match.homeTeam.tla ?: match.homeTeam.shortName ?: match.homeTeam.name,
            awayShort = match.awayTeam.tla ?: match.awayTeam.shortName ?: match.awayTeam.name,
            events = events,
            lineups = lineups,
            bars = bars
        )
    }

    private fun fraction(hv: String, av: String): Float? {
        fun num(s: String) = s.replace("%", "").trim().toDoubleOrNull()
        val h = num(hv) ?: return null
        val a = num(av) ?: return null
        val t = h + a
        return if (t <= 0.0) null else (h / t).toFloat()
    }

    /** Loose team-name comparison across the two providers. */
    internal fun nameMatches(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        return na.contains(nb) || nb.contains(na)
    }

    private val stopTokens = setOf(
        "fc", "cf", "club", "athletic", "ac", "sc", "afc", "as", "ca", "cd",
        "if", "de", "the"
    )

    internal fun normalize(name: String): String =
        name.lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd} ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it !in stopTokens }
            .joinToString(" ")

    // ---------------- API-Football JSON (Gson) ----------------

    private data class AfResponse(val response: List<AfFixtureNode> = emptyList())
    private data class AfFixtureInfo(val id: Long? = null)
    private data class AfTeamNode(val id: Int? = null, val name: String? = null)
    private data class AfTeamsNode(val home: AfTeamNode? = null, val away: AfTeamNode? = null)
    private data class AfPlayerNode(val name: String? = null)
    private data class AfTime(val elapsed: Int? = null, val extra: Int? = null)
    private data class AfEventNode(
        val time: AfTime? = null,
        val team: AfTeamNode? = null,
        val player: AfPlayerNode? = null,
        val assist: AfPlayerNode? = null,
        val type: String? = null,
        val detail: String? = null
    )
    private data class AfPlayerFull(val name: String? = null, val number: String? = null, val grid: String? = null)
    private data class AfXIP(val player: AfPlayerFull? = null)
    private data class AfCoachNode(val name: String? = null)
    private data class AfLineupNode(
        val team: AfTeamNode? = null,
        val formation: String? = null,
        val coach: AfCoachNode? = null,
        val startXI: List<AfXIP> = emptyList(),
        @SerializedName("substitutes") val subs: List<AfXIP> = emptyList()
    )
    private data class AfStatEntry(val type: String? = null, val value: String? = null)
    private data class AfStatNode(val team: AfTeamNode? = null, val statistics: List<AfStatEntry> = emptyList())
    private data class AfFixtureNode(
        val fixture: AfFixtureInfo? = null,
        val teams: AfTeamsNode? = null,
        val events: List<AfEventNode>? = null,
        val lineups: List<AfLineupNode>? = null,
        val statistics: List<AfStatNode>? = null
    )
}
