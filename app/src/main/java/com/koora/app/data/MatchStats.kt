package com.koora.app.data

import androidx.annotation.VisibleForTesting
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
 * Providers, in priority order:
 *
 * 1. API-Football (api-sports.io) — used ONLY when a key is present
 *    (local.properties / GitHub secret API_FOOTBALL_KEY). Free plan =
 *    100 requests/day; $19/mo removes the cap with zero code change.
 *
 * 2. ESPN open match data (v1.5.1, default, NO KEY NEEDED):
 *    site.api.espn.com/apis/site/v2/sports/soccer/{slug}/scoreboard
 *    + /summary?event={id} give the full boxscore: keyEvents (goals,
 *    yellow & red cards, substitutions), rosters (formation + starters
 *    + bench) and statistics (possession, shots, corners, fouls,
 *    cards, saves). Verified live 2026-11-09 for all competitions the
 *    app covers. Responses arrive gzipped; we sniff the gzip magic and
 *    inflate when present.
 *
 * football-data.org's FREE tier does NOT include events or lineups
 * (verified live: the /v4/matches/{id} response returns both null),
 * which is why this repository exists at all.
 *
 * Mapping: football-data and the provider use different match ids, so
 * we resolve the fixture by date + team-name fuzzy match, then read
 * the full summary (events + lineups + statistics in ONE response).
 *
 * Caching: finished matches are cached in memory forever (repeat
 * views cost zero requests). Live matches refresh every 60 s.
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
    // v1.10.2 (owner report: "no substitutes for the upper team"): the
    // ANNOUNCED substitutes with their real names — ESPN roster entries
    // with starter != true, API-Football's substitutes array. The old
    // subsCount was a bare number nobody could read; the bench card now
    // lists these names per team. Empty = provider announced no bench
    // (the section then hides — we never invent one).
    val bench: List<StatsPlayer>
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

    // football-data competition code -> ESPN soccer slug (no key needed)
    // v1.8.0: extended to the v1.7.0 "max attractive leagues" — every slug
    // verified live 2026-11-11 (scoreboard returns the league object).
    // KOR (K League 1) is deliberately ABSENT: ESPN has no coverage for it
    // (league registry contains no kor.* entry), so those match pages show
    // H2H/form only — sections stay hidden, never faked.
    internal val espnLeagueMap = mapOf(
        "CL" to "uefa.champions",
        "PL" to "eng.1",
        "PD" to "esp.1",
        "SA" to "ita.1",
        "BL1" to "ger.1",
        "FL1" to "fra.1",
        "ELC" to "eng.2",
        "DED" to "ned.1",
        "PPL" to "por.1",
        "BSA" to "bra.1",
        "CLI" to "conmebol.libertadores",
        "WC" to "fifa.world",
        "EC" to "uefa.euro",
        "UEL" to "uefa.europa",
        "UECL" to "uefa.europa.conf",
        "SPL" to "ksa.1",
        "TUR" to "tur.1",
        "ARG" to "arg.1",
        "MEX" to "mex.1",
        "MLS" to "usa.1",
        "SCO" to "sco.1",
        "BEL" to "bel.1",
        "JPN" to "jpn.1"
    )

    private val hasKey: Boolean get() = BuildConfig.API_FOOTBALL_KEY.isNotBlank()
    private val gson = Gson()

    // match id -> (stats, fetched-at epoch ms)
    private val mem = mutableMapOf<Long, Pair<MatchStats?, Long>>()

    private const val LIVE_TTL_MS = 60_000L

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
                listOf(
                    StatsPlayer("16", "A. Tagnaouti"), StatsPlayer("12", "S. Khalfi"), StatsPlayer("14", "O. Belrhazi"), StatsPlayer("15", "Y. Alaoui"), StatsPlayer("17", "M. Nahri"), StatsPlayer("18", "A. Mardaci"), StatsPlayer("20", "H. Moudden")
                )
            ),
            StatsLineup(
                match.awayTeam.shortName ?: match.awayTeam.name, "4-2-3-1", "R. El Amrani",
                listOf(
                    StatsPlayer("1", "A. Lekrati"), StatsPlayer("26", "Y. Atouch"), StatsPlayer("18", "J. Nkoma"), StatsPlayer("4", "B. Martaud"), StatsPlayer("5", "V. Betoukoumi"), StatsPlayer("6", "M. El Moussaoui"), StatsPlayer("8", "A. Abahaf"), StatsPlayer("10", "C. Bangre"), StatsPlayer("7", "G. Kanga"), StatsPlayer("11", "S. Tiaib"), StatsPlayer("9", "E. Aguerd")
                ),
                listOf(
                    StatsPlayer("22", "M. Hanaf"), StatsPlayer("13", "R. Ben Ali"), StatsPlayer("14", "O. Jouida"), StatsPlayer("16", "A. Naji"), StatsPlayer("19", "Y. Chibi")
                )
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

    /**
     * Entry point. demo=true -> sample data (CI only).
     * Real data: API-Football when a key exists, else ESPN. Null -> sections hidden.
     */
    suspend fun statsFor(match: Match, compCode: String, demo: Boolean): MatchStats? =
        withContext(Dispatchers.IO) {
            if (demo) return@withContext demo(match)
            synchronized(mem) {
                mem[match.id]?.let { (v, at) ->
                    val live = match.status == "IN_PLAY" || match.status == "PAUSED"
                    if (!live || System.currentTimeMillis() - at <= LIVE_TTL_MS) {
                        return@withContext v
                    }
                }
            }
            val resolved = runCatching { resolve(match, compCode) }.getOrNull()
            synchronized(mem) { mem[match.id] = Pair(resolved, System.currentTimeMillis()) }
            resolved
        }

    // ------------------------------------------------------------------
    // Provider dispatch
    // ------------------------------------------------------------------

    private fun resolve(match: Match, compCode: String): MatchStats? {
        if (hasKey) {
            runCatching { afResolve(match, compCode) }.getOrNull()?.let { return it }
        }
        return espnResolve(match, compCode)
    }

    // ------------------------------------------------------------------
    // API-Football (api-sports.io v3) — only when a key is configured
    // ------------------------------------------------------------------

    private fun afResolve(match: Match, compCode: String): MatchStats? {
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
                bench = l.subs.mapNotNull { x ->
                    x.player?.name?.let { StatsPlayer(x.player?.number, it) }
                }
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

    // ------------------------------------------------------------------
    // ESPN open match data (v1.5.1) — default provider, no key needed
    // ------------------------------------------------------------------

    private fun espnResolve(match: Match, compCode: String): MatchStats? {
        val slug = espnLeagueMap[compCode.trim().uppercase()] ?: return null
        val date = match.utcDate.take(10).replace("-", "")

        val sb = espnCall("sports/soccer/$slug/scoreboard?dates=$date", EspnScoreboard::class.java)
        val event = sb.events.firstOrNull { e ->
            val comps = e.competitions.firstOrNull()?.competitors ?: emptyList()
            val h = comps.firstOrNull { it.homeAway == "home" }?.team
            val a = comps.firstOrNull { it.homeAway == "away" }?.team
            h != null && a != null &&
                nameMatches(h.displayName ?: "", match.homeTeam.name) &&
                nameMatches(a.displayName ?: "", match.awayTeam.name)
        } ?: return null

        val sum = espnCall("sports/soccer/$slug/summary?event=${event.id}", EspnSummary::class.java)
        return espnToStats(sum, match)
    }

    /** ESPN replies gzipped even without asking; sniff the gzip magic and inflate. */
    private fun <T> espnCall(path: String, clazz: Class<T>): T {
        val conn = URL("https://site.api.espn.com/apis/site/v2/$path").openConnection()
            as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept-Encoding", "gzip")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)")
        try {
            val raw = conn.inputStream.use { it.readBytes() }
            val gz = raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()
            val text = if (gz)
                java.util.zip.GZIPInputStream(raw.inputStream()).use { it.readBytes() }
                    .decodeToString()
            else raw.decodeToString()
            return gson.fromJson(text, clazz)
                ?: throw IllegalStateException("ESPN returned an empty body")
        } finally {
            conn.disconnect()
        }
    }

    @VisibleForTesting
    internal fun espnToStats(s: EspnSummary, match: Match): MatchStats {
        val comps = s.header?.competitions?.firstOrNull()?.competitors ?: emptyList()
        val homeId = comps.firstOrNull { it.homeAway == "home" }?.team?.id ?: ""

        val events = s.keyEvents.mapNotNull { e ->
            val text = e.type?.text ?: return@mapNotNull null
            val type = (e.type.type ?: "").lowercase()
            val tn = text.lowercase()
            // Primary: ESPN's machine type ("goal", "own-goal", "penalty-scored",
            // "yellow-card", "red-card", "substitution"). Fallback: event text.
            val kind = when {
                type.contains("sub") -> "sub"
                type.contains("second") || type.contains("red") -> "red"
                type.contains("yellow") -> "yellow"
                type.contains("goal") ||
                    (type.contains("penalty") && type.contains("scored")) -> "goal"
                tn.contains("second yellow") || tn.contains("red card") ||
                    tn.contains("sent off") -> "red"
                tn.contains("yellow") -> "yellow"
                tn.contains("goal") ||
                    (tn.contains("penalty") &&
                        (tn.contains("converted") || tn.contains("scored"))) -> "goal"
                tn.contains("substitut") || tn.contains(" replaces ") -> "sub"
                else -> return@mapNotNull null
            }
            if ("missed" in tn || "saved" in tn) return@mapNotNull null
            val minute = (e.clock?.displayValue ?: "").replace("'", "")
            if (minute.isBlank()) return@mapNotNull null
            val detail = when {
                "own goal" in tn || type == "own-goal" -> "Own goal"
                "penalty" in tn -> "Penalty"
                else -> ""
            }
            val (player, other) = espnEventPeople(kind, e)
            if (kind == "sub" && player.isBlank()) return@mapNotNull null
            StatsEvent(
                minute = minute,
                side = if (e.team?.id == homeId) 0 else 1,
                kind = kind,
                detail = detail,
                player = player,
                other = other
            )
        }

        val lineups = s.rosters
            .sortedByDescending { it.homeAway == "home" }
            .mapNotNull { r ->
                val starters = r.roster.filter { it.starter == true }
                    .mapNotNull { p ->
                        p.athlete?.displayName?.let {
                            StatsPlayer(p.jersey?.takeUnless { it.isBlank() }, it)
                        }
                    }
                if (starters.size < 7) return@mapNotNull null   // XI not announced yet
                StatsLineup(
                    team = r.team?.displayName ?: "",
                    formation = r.formation?.takeUnless { it.isBlank() },
                    coach = r.coach?.fullName,
                    starters = starters.take(11),
                    bench = r.roster
                        .filter { it.starter != true }
                        .mapNotNull { p ->
                            p.athlete?.displayName?.let {
                                StatsPlayer(p.jersey?.takeUnless { it.isBlank() }, it)
                            }
                        }
                )
            }
            .filter { it.team.isNotBlank() }

        val boxHome = s.boxscore?.teams?.firstOrNull { it.homeAway == "home" }
        val boxAway = s.boxscore?.teams?.firstOrNull { it.homeAway == "away" }
        val bars = mutableListOf<StatsBar>()
        if (boxHome != null && boxAway != null) {
            val hs = boxHome.statistics.associate { s -> Pair(s.name ?: "", s.displayValue ?: "") }
            val aws = boxAway.statistics.associate { s -> Pair(s.name ?: "", s.displayValue ?: "") }
            espnBarNames.forEach { (espnName, label) ->
                val hv = hs[espnName] ?: return@forEach
                val av = aws[espnName] ?: return@forEach
                if (hv.isBlank() || av.isBlank()) return@forEach
                val (hd, ad) = if (label == "possession") possessionPair(hv, av) else Pair(hv, av)
                bars.add(StatsBar(label, hd, ad, fraction(hv, av)))
            }
        }

        return MatchStats(
            homeShort = match.homeTeam.tla ?: match.homeTeam.shortName ?: match.homeTeam.name,
            awayShort = match.awayTeam.tla ?: match.awayTeam.shortName ?: match.awayTeam.name,
            events = events,
            lineups = lineups,
            bars = bars
        )
    }

    /** (in, out) for subs from "X replaces Y", scorer/card player from the event text. */
    @VisibleForTesting
    internal fun espnEventPeople(kind: String, e: EspnKey): Pair<String, String?> {
        val involved = e.athletesInvolved?.mapNotNull { it.displayName } ?: emptyList()
        val text = e.text ?: ""
        return when (kind) {
            "sub" -> {
                val seg = text.substringAfter(". ").trim().removeSuffix(".")
                if (seg.contains(" replaces ")) {
                    Pair(
                        seg.substringBefore(" replaces ").trim(),
                        seg.substringAfter(" replaces ").trim()
                    )
                } else Pair(involved.firstOrNull() ?: "", involved.getOrNull(1))
            }
            else -> {
                val name = nameFromChunk(text)
                if (name.isBlank()) Pair(involved.firstOrNull() ?: "", involved.getOrNull(1))
                else Pair(name, involved.getOrNull(1))
            }
        }
    }

    /**
     * Pulls the player name out of an ESPN event text:
     * "Calvin Verdonk (Lille) is shown the yellow card." -> "Calvin Verdonk"
     * "Goal! Rennes 0, Lille 1. S. Diaoune (Lille) left footed..." -> "S. Diaoune"
     * "Own Goal by H. Mouaddib (Rennes)." / "Penalty by M. Perez (Lille)" too.
     * The leading score line ("Rennes 0, Lille 1. ") is stripped by regex so
     * player initials like "S." never get confused with sentence breaks.
     */
    @VisibleForTesting
    internal fun nameFromChunk(text: String): String {
        val body = text.trim()
            .removePrefix("Goal! ")
            .removePrefix("Own Goal by ")
            .removePrefix("Penalty by ")
        // score line "Rennes 0, Lille 1. " -> digits, comma, TEAM NAME, digits, period
        val name = body.replace(Regex(".*\\d+,\\s*\\D*?\\d+\\.\\s*"), "")
            .substringBefore(" (")
            .trim()
        if (!body.contains(" (")) return ""
        if (Regex("\\d").containsMatchIn(name)) {
            // leftover score fragment -> useless, let callers fall back to the roster
            return name.substringAfterLast(". ", "")
        }
        return name
    }

    /** possessionPct has no % sign; make the two sides sum to 100. */
    private fun possessionPair(hv: String, av: String): Pair<String, String> {
        val h = hv.toDoubleOrNull() ?: return Pair("$hv%", "$av%")
        val a = av.toDoubleOrNull() ?: return Pair("$hv%", "$av%")
        val hi = Math.round(h).toInt()
        val ai0 = Math.round(a).toInt()
        val ai = if (hi + ai0 == 100) ai0 else 100 - hi
        return Pair("$hi%", "$ai%")
    }

    // ESPN stat name -> app label key (insertion order = display order)
    private val espnBarNames = linkedMapOf(
        "possessionPct" to "possession",
        "totalShots" to "shots",
        "shotsOnTarget" to "shots_on",
        "wonCorners" to "corners",
        "foulsCommitted" to "fouls",
        "yellowCards" to "yellow",
        "redCards" to "red",
        "offsides" to "offsides",
        "saves" to "saves"
    )

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

    // ---------------- ESPN JSON (Gson, internal for unit tests) ----------------

    internal data class EspnScoreboard(val events: List<EspnEvent> = emptyList())
    internal data class EspnEvent(
        val id: String? = null,
        val competitions: List<EspnComp> = emptyList()
    )
    internal data class EspnComp(val competitors: List<EspnCompetitor> = emptyList())
    internal data class EspnCompetitor(
        val homeAway: String? = null,
        val team: EspnTeamRef? = null
    )
    internal data class EspnTeamRef(val id: String? = null, val displayName: String? = null)
    internal data class EspnSummary(
        val header: EspnHeader? = null,
        val keyEvents: List<EspnKey> = emptyList(),
        val rosters: List<EspnRoster> = emptyList(),
        val boxscore: EspnBox? = null
    )
    internal data class EspnHeader(val competitions: List<EspnComp> = emptyList())
    internal data class EspnType(val text: String? = null, val type: String? = null)
    internal data class EspnClock(val displayValue: String? = null)
    internal data class EspnAthleteRef(val displayName: String? = null)
    internal data class EspnKey(
        val type: EspnType? = null,
        val clock: EspnClock? = null,
        val team: EspnTeamRef? = null,
        val athletesInvolved: List<EspnAthleteRef>? = null,
        val text: String? = null
    )
    internal data class EspnCoachRef(val fullName: String? = null)
    internal data class EspnPlayerRef(
        val jersey: String? = null,
        val starter: Boolean? = null,
        val athlete: EspnAthleteRef? = null
    )
    internal data class EspnRoster(
        val homeAway: String? = null,
        val team: EspnTeamRef? = null,
        val formation: String? = null,
        val coach: EspnCoachRef? = null,
        val roster: List<EspnPlayerRef> = emptyList()
    )
    internal data class EspnStat(val name: String? = null, val displayValue: String? = null)
    internal data class EspnBoxTeam(
        val homeAway: String? = null,
        val team: EspnTeamRef? = null,
        val statistics: List<EspnStat> = emptyList()
    )
    internal data class EspnBox(val teams: List<EspnBoxTeam> = emptyList())
}
