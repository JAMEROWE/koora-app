package com.koora.app.ui.screens

import android.content.Context
import com.google.gson.Gson
import com.koora.app.BuildConfig
import com.koora.app.data.Match
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/*
 * OFFICIAL-HIGHLIGHT RESOLUTION — v1.10.3.
 *
 * Extracted from HighlightsScreen so the MATCH DETAIL screen can gate the
 * "Watch highlights" button on VERIFIED availability (roadmap P0):
 * the button is rendered only when a video has been confirmed, and the
 * whole section stays out of the UI tree otherwise — never a disabled
 * state, placeholder box or "not published yet" notice.
 *
 * THE v1.10.3 DISCOVERY REBUILD (owner report, 5th occurrence: "no
 * highlights are published yet" for matches whose videos are demonstrably
 * on YouTube). ROOT CAUSE, verified live 2026-11-13: YouTube has
 * DISCONTINUED the RSS feeds endpoint —
 * https://www.youtube.com/feeds/videos.xml?channel_id=... returns HTTP 404
 * for EVERY channel (verified: all six allowlisted league channels AND a
 * control channel), while the channel pages themselves still serve. Every
 * earlier fix downstream of discovery (parser, matching, oEmbed, player)
 * could never help, because upstream there was never a single video to
 * process. The discovery tier now reads each OFFICIAL channel's own public
 * /videos page and extracts the video grid metadata (videoId + title) that
 * YouTube embeds in that page's ytInitialData payload. NOTE: this payload
 * is only present on the DESKTOP web shell — a mobile user-agent receives
 * a shell without the video list, so the fetcher must send a desktop UA.
 *
 * ALLOWLIST COMPLIANCE (roadmap §3.2). A video is surfaced ONLY when ALL
 * of these pass — enforced by [verifyDataApiVideo] for the Data API tier:
 *   1. the channel is in OFFICIAL_CHANNELS or CLUB_CHANNELS (verified
 *      rights-holders — league channels and official club channels,
 *      each resolved live from its own public page on 2026-11-13)
 *   2. status.uploadStatus == "processed"
 *   3. status.privacyStatus == "public"
 *   4. status.embeddable == true
 *   5. contentDetails.regionRestriction does not block the device country
 * The channel-page tier is compliant by construction: metadata comes from
 * the allowlisted channels' own public pages, every candidate is again
 * verified against YouTube's public oEmbed endpoint before playback, and
 * the playback itself is YouTube's official embed. NOTHING here touches
 * search results, third-party re-uploads, video streams, or media caching.
 *
 * NEVER: open YouTube search results without verification, third-party
 * re-upload channels, scraping video streams, or caching media. Only
 * metadata (title / videoId) is cached, in VideoCache.
 */

/** Official competition channels (ids re-verified live 2026-11-13). */
internal val OFFICIAL_CHANNELS: List<Pair<String, String>> = listOf(
    "UCG5qGWdu8nIRZqJ_GgDwQ-w" to "Premier League",
    "UCTv-XvfzLX3i4IGWAm4sbmA" to "LaLiga",
    "UCQsH5XtIc9hONE1BQjucM0g" to "Ligue 1",
    "UCyGa1YEx9ST66rYrJTGIKOw" to "UEFA",
    "UC6UL29enLNe4mqwTfAyeNuw" to "Bundesliga",
    "UCBJeMCIeLQos7wacox4hmLQ" to "Serie A"
)

/**
 * v1.10.3 — official CLUB channels, every one resolved live from its own
 * public page and verified (title contains the club, videos page serves
 * the video grid) on 2026-11-13. Clubs not verified live are NOT listed
 * here — their fixtures still resolve through the league channel above;
 * we never guess a channel.
 *
 * [tokens] are the ASCII, diacritic-free fragments that identify the club
 * inside provider team names ("ACF Fiorentina", "Atlético Madrid",
 * "1. FC Köln"). [clubChannelsFor] requires EVERY token to appear in the
 * normalized team name, so "Manchester City FC" can never select
 * Manchester United's channel and vice versa.
 */
data class ClubChannel(val club: String, val tokens: List<String>, val channelId: String)

internal val CLUB_CHANNELS: List<ClubChannel> = listOf(
    // Premier League
    ClubChannel("Arsenal", listOf("arsenal"), "UCpryVRk_VDudG8SHXgWcG0w"),
    ClubChannel("Aston Villa", listOf("villa"), "UCICNP0mvtr0prFwGUQIABfQ"),
    ClubChannel("AFC Bournemouth", listOf("bournemouth"), "UCeOCuVSSweaEj6oVtJZEKQw"),
    ClubChannel("Brentford", listOf("brentford"), "UCAalMUm3LIf504ItA3rqfug"),
    ClubChannel("Brighton", listOf("brighton"), "UCf-cpC9WAdOsas19JHipukA"),
    ClubChannel("Burnley", listOf("burnley"), "UChvUXuSDeEFSQZS8GcPMtkg"),
    ClubChannel("Chelsea", listOf("chelsea"), "UCU2PacFf99vhb3hNiYDmxww"),
    ClubChannel("Crystal Palace", listOf("palace"), "UCWB9N0012fG6bGyj486Qxmg"),
    ClubChannel("Everton", listOf("everton"), "UCtK4QAczAN2mt2ow_jlGinQ"),
    ClubChannel("Fulham", listOf("fulham"), "UC2VLfz92cTT8jHIFOecC-LA"),
    ClubChannel("Leeds United", listOf("leeds"), "UCyQcJHDN4uYfPa1DHzKVSnw"),
    ClubChannel("Liverpool", listOf("liverpool"), "UC9LQwHZoucFT94I2h6JOcjw"),
    ClubChannel("Manchester City", listOf("manchester", "city"), "UCkzCjdRMrW2vXLx8mvPVLdQ"),
    ClubChannel("Manchester United", listOf("manchester", "united"), "UC6yW44UGJJBvYTlfC7CRg2Q"),
    ClubChannel("Newcastle United", listOf("newcastle"), "UCywGl_BPp9QhD0uAcP2HsJw"),
    ClubChannel("Nottingham Forest", listOf("nottingham", "forest"), "UCyAxjuAr8f_BFDGCO3Htbxw"),
    ClubChannel("Tottenham Hotspur", listOf("tottenham", "hotspur"), "UCEg25rdRZXg32iwai6N6l0w"),
    // LaLiga
    ClubChannel("Atlético de Madrid", listOf("atletico"), "UCuzKFwdh7z2GHcIOX_tXgxA"),
    ClubChannel("FC Barcelona", listOf("barcelona"), "UC14UlmYlSNiQCBe9Eookf_A"),
    ClubChannel("CA Osasuna", listOf("osasuna"), "UC1iCMq31GZ6Sv3qIIRhQ3Tg"),
    ClubChannel("RCD Mallorca", listOf("mallorca"), "UCpBP0bYboEZVhHvQNIw-0XA"),
    ClubChannel("Real Betis", listOf("betis"), "UCeB7JZwcar2fVoK2w2f9OwA"),
    ClubChannel("RC Celta", listOf("celta"), "UCCJLVZYqRb_85b2Flpg04cg"),
    ClubChannel("RCD Espanyol", listOf("espanyol"), "UClywhnD01yUU5kO6OgAeHUQ"),
    ClubChannel("Girona FC", listOf("girona"), "UC6x5gKUZpXuKDujmaHc3Xhg"),
    ClubChannel("Levante UD", listOf("levante"), "UCvOegN2N1FGPPv4xBNN2F9A"),
    ClubChannel("Real Madrid", listOf("real", "madrid"), "UCWV3obpZVGgJ3j9FVhEjF2Q"),
    ClubChannel("Sevilla FC", listOf("sevilla"), "UCLy9lmj_0cqffXUzbGHNmYA"),
    ClubChannel("Valencia CF", listOf("valencia"), "UCgvyo5x49J8ht5H9imKfxMQ"),
    ClubChannel("Villarreal CF", listOf("villarreal"), "UC0MLWyQ0L7uEZY8wbkDSTkw"),
    // Serie A
    ClubChannel("Atalanta", listOf("atalanta"), "UC0R-isVeRhMDe3vFTWP5Spg"),
    ClubChannel("ACF Fiorentina", listOf("fiorentina"), "UC9nwpWq73kl4e2a51d1aCog"),
    ClubChannel("Hellas Verona", listOf("verona"), "UCquN7Rmr2Hw-b3UkhtAMp_w"),
    ClubChannel("Inter", listOf("inter"), "UCvXzEblUa0cfny4HAJ_ZOWw"),
    ClubChannel("Juventus", listOf("juventus"), "UCLzKhsxrExAC6yAdtZ-BOWw"),
    ClubChannel("SS Lazio", listOf("lazio"), "UCVtDCsB0UlIkDn2kjsva3WA"),
    ClubChannel("AC Milan", listOf("milan"), "UCKcx1uK38H4AOkmfv4ywlrg"),
    ClubChannel("SSC Napoli", listOf("napoli"), "UCTnCzHi0P6MH83er5OfZbzQ"),
    ClubChannel("Parma Calcio", listOf("parma"), "UCXKXzx4PG8dcqExNJ5npUkg"),
    ClubChannel("AS Roma", listOf("roma"), "UCLttSYJ6kPtlcurY96kXkQw"),
    ClubChannel("US Sassuolo", listOf("sassuolo"), "UCok6j2Pw2VsM-QYP_bmwU6g"),
    ClubChannel("Torino FC", listOf("torino"), "UCD-Q9QNPEmhrxyZb2yn3juA"),
    ClubChannel("US Lecce", listOf("lecce"), "UCzc84VvVxLbeCQ2wYXV0VOg"),
    ClubChannel("Pisa", listOf("pisa"), "UCsx5PLguyc68a_PoIwWJCEg"),
    ClubChannel("AC Monza", listOf("monza"), "UCgFNZmFK4tLKPpXpvJGS9dQ"),
    ClubChannel("Udinese", listOf("udinese"), "UCbz89vmhZ0uerNuVKxur9lA"),
    // Bundesliga
    ClubChannel("FC Augsburg", listOf("augsburg"), "UC5SqmvgOsmHJ8mV2mpO69PQ"),
    ClubChannel("SV Werder Bremen", listOf("bremen", "werder"), "UCdjedrfgyFQEkqbkq1DJf3w"),
    ClubChannel("Borussia Dortmund", listOf("dortmund"), "UCK8rTVgp3-MebXkmeJcQb1Q"),
    ClubChannel("SC Freiburg", listOf("freiburg"), "UC_atVJpGbIdjIa9OC6E3yJA"),
    ClubChannel("1. FC Köln", listOf("koln"), "UCQZhvmm636X6I5rrnLFBp1w"),
    ClubChannel("RB Leipzig", listOf("leipzig"), "UCkZwB4IGoNBvRmVT2gaO4XA"),
    ClubChannel("FC St. Pauli", listOf("pauli"), "UCmjYwKDykQSA9VtqaT-OVdA"),
    ClubChannel("1. FSV Mainz 05", listOf("mainz"), "UCMT1hwsDgOEMhS2dIfD3e6A"),
    ClubChannel("VfB Stuttgart", listOf("stuttgart"), "UCNjHTx_URHNiZvjW-uzIf4Q"),
    ClubChannel("Hamburger SV", listOf("hamburger", "hsv"), "UCUegZz1N_lW_5mKORkYykCw"),
    ClubChannel("Eintracht Frankfurt", listOf("frankfurt"), "UCDFp4bscTFm8hYxCBx-tIXg"),
    ClubChannel("1. FC Union Berlin", listOf("union"), "UCexHeD0RsxZFZoKLhhR_IUQ"),
    // Ligue 1
    ClubChannel("LOSC Lille", listOf("lille", "losc"), "UCae9u1pNGzaklyZC8OKkeCQ"),
    ClubChannel("Stade Brestois", listOf("brest"), "UC0IUjVIiL24hZx73DahzITA"),
    ClubChannel("FC Lorient", listOf("lorient"), "UC1LeTxFgKYwMeQjRjLyGRTQ"),
    ClubChannel("Olympique Lyonnais", listOf("lyon"), "UCzHCZXmqIdjqRnpdp0l_T6g"),
    ClubChannel("AS Monaco", listOf("monaco"), "UCHy548EHHX9f-ETJlm18Jiw"),
    ClubChannel("FC Nantes", listOf("nantes"), "UC4sn_Eq3_g9Al4JhdYysj0g"),
    ClubChannel("OGC Nice", listOf("nice"), "UCAvm8jHWe-8K2kZK-7ynIHA"),
    ClubChannel("Paris Saint-Germain", listOf("paris", "saint"), "UCt9a_qP9CqHCNwilf-iULag"),
    ClubChannel("FC Metz", listOf("metz"), "UCRt4L6aPg6hOQBoxdE-bycw"),
    ClubChannel("Toulouse FC", listOf("toulouse"), "UCYXs6c0q6Fdlltd5SyrQnjw")
)

/** Lowercased, diacritic-stripped form used for all club-name matching. */
internal fun normalizedClubName(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()

/**
 * The club channels whose EVERY identifying token appears in [team]'s
 * normalized name. Pure, tested.
 */
internal fun clubChannelsFor(team: String): List<ClubChannel> {
    val n = normalizedClubName(team)
    return CLUB_CHANNELS.filter { c -> c.tokens.all { n.contains(it) } }
}

/* Public: returned by the public HighlightsSource.resolve(). */
data class YtEntry(
    val title: String,
    val videoId: String,
    val published: String,
    val channel: String = "",
    /**
     * Latest plausible publish instant (epoch ms), 0 = unknown. Channel
     * grid entries carry a fuzzy relative text ("3 days ago"), so this is
     * the LATEST time the text still allows (now minus the minimum age);
     * Data API entries carry the exact ISO publish stamp. Pairing treats
     * 0 as unverifiable and refuses the match.
     */
    val publishedAtMs: Long = 0L
)

/**
 * Single entry point. Resolves at most once per fixture — VideoCache
 * serves found videos for 24h and a fresh "nothing yet" for 45 minutes.
 */
object HighlightsSource {

    /** All matching official videos, newest first. Empty = none available. */
    suspend fun resolve(context: Context?, home: String, away: String): List<YtEntry> =
        withContext(Dispatchers.IO) {
            if (context != null) {
                VideoCache.get(context, home, away)?.let { return@withContext it }
            }
            val found = runCatching {
                if (BuildConfig.YOUTUBE_API_KEY.isNotBlank()) {
                    viaDataApi(home, away) ?: viaOfficialChannels(home, away)
                } else {
                    viaOfficialChannels(home, away)
                }
            }.getOrDefault(emptyList())
            if (context != null) {
                if (found.isNotEmpty()) VideoCache.putFound(context, home, away, found)
                else VideoCache.putMiss(context, home, away)
            }
            found
        }

    /** Roadmap P0 gate: is a VERIFIED official video available right now? */
    suspend fun hasVideo(context: Context?, home: String, away: String): Boolean =
        resolve(context, home, away).isNotEmpty()
}

// ---------------------------------------------------------------------------
// Tier 3 — HIGHLIGHTS HUB (v1.8.0): released videos for known fixtures
// ---------------------------------------------------------------------------

/**
 * The independent Highlights tab. Scans the OFFICIAL CHANNEL video pages
 * once per load and pairs every released video with a finished fixture the
 * app knows, by team-name matching.
 *
 * Compliance is identical to the per-fixture tier: metadata comes from the
 * allowlisted channels' own public pages, so provenance is exact — no
 * search results are ever opened. The Data API is deliberately NOT used
 * here (one search per fixture would burn quota; pages are 6 cached GETs).
 *
 * The no-fake rules cut BOTH ways:
 *  - a released video that cannot be paired with a KNOWN fixture is NOT
 *    shown (the tab contains only released matches, never a link dump);
 *  - a known fixture without a released video is NOT shown either (no
 *    placeholders, no "coming soon" rows).
 */
object HighlightsHub {

    data class Row(val match: Match, val video: YtEntry)

    /** Page scan is cheap but not free — serve the list for 15 minutes. */
    private const val TTL_MS = 15L * 60 * 1000

    @Volatile private var cachedAt: Long = 0L
    @Volatile private var cache: List<Row> = emptyList()

    /**
     * Pairs released official videos with [fixtures] (already FINISHED).
     * Also seeds VideoCache so tapping a row opens the player instantly.
     */
    suspend fun resolve(context: Context?, fixtures: List<Match>): List<Row> {
        val now = System.currentTimeMillis()
        cache.takeIf { now - cachedAt <= TTL_MS }?.let { return it }
        val rows = withContext(Dispatchers.IO) {
            val finished = fixtures
                .filter { it.status == "FINISHED" }
                .sortedByDescending { it.utcDate }   // newest fixture wins a tie
            if (finished.isEmpty()) return@withContext emptyList<Row>()
            coroutineScope {
                OFFICIAL_CHANNELS.map { (id, name) ->
                    async { runCatching { ChannelVideosStore.videos(id, name) }.getOrDefault(emptyList()) }
                }.awaitAll()
            }
                .flatten()
                // page order on the videos tab is newest-release-first
                .mapNotNull { e -> pairWithFixture(e, finished)?.let { Row(it.second, e) } }
                // v1.10.2: hub rows are oembed-verified too — a video whose
                // embedding YouTube refuses never seeds the player, so the
                // detail screen's cascade starts on playable ground.
                .let { rows ->
                    val ok = rows.map { async { oembedAllows(it.video.videoId) } }
                        .map { it.await() }
                    rows.filterIndexed { i, _ -> ok[i] }
                }
                .let { oneRowPerFixture(it) }
        }
        if (context != null) {
            rows.forEach { r ->
                runCatching {
                    // Seed only when the per-fixture cache has nothing or a
                    // fresh miss — never clobber a richer resolved list.
                    val existing = VideoCache.get(context, r.match.homeTeam.name, r.match.awayTeam.name)
                    if (existing.isNullOrEmpty()) {
                        VideoCache.putFound(context, r.match.homeTeam.name, r.match.awayTeam.name, listOf(r.video))
                    }
                }
            }
        }
        cache = rows
        cachedAt = now
        return rows
    }

    /**
     * One hub row per FIXTURE — fixes the user-reported duplicate where
     * the same final surfaced twice because an official channel released
     * more than one video of it. Careful by design:
     *  - fixture identity is the provider match id, so a rematch or any
     *    different fixture between the same clubs keeps its own row;
     *    when the id is unset (0, demo/odd feeds) the fallback key is
     *    kick-off + both team names — two different days never merge;
     *  - every incoming row is already oembed-verified playable and the
     *    list is ordered official-channels-first, newest-release-first,
     *    so the surviving row stays a supported official source;
     *  - one video still surfaces at most once.
     * Pure, unit-tested.
     */
    internal fun oneRowPerFixture(rows: List<Row>): List<Row> {
        val seenVideos = mutableSetOf<String>()
        val seenFixtures = mutableSetOf<Any>()
        val out = mutableListOf<Row>()
        for (r in rows) {
            if (!seenVideos.add(r.video.videoId)) continue
            val key: Any = r.match.id.takeIf { it != 0L }
                ?: "${r.match.utcDate}|${r.match.homeTeam.name.lowercase()}|${r.match.awayTeam.name.lowercase()}"
            if (!seenFixtures.add(key)) continue
            out += r
        }
        return out
    }

    /**
     * Pure pairing rule: the feed entry must name BOTH teams of one
     * fixture (token match, same as the per-fixture tier) AND be grounded
     * in time and score (owner report 2026-11-24: a Liverpool 4-0 row
     * surfaced with the thumbnail of an OLDER 1-2 meeting — a recently
     * re-released classic paired by name alone):
     *
     *  - PUBLISH WINDOW: an entry pairs only when its publish instant is
     *    demonstrably compatible with the fixture's kick-off — not before
     *    the meeting existed (throwbacks, previews) and not long after
     *    (post-match shows). Unknown publish time is NOT a verified
     *    match and never pairs: the no-fake rule cuts both ways;
     *  - SCORE DISAGREEMENT: when the title itself carries a scoreline
     *    that contradicts the fixture's recorded score, the entry is
     *    another meeting's video and is rejected. A title without a
     *    scoreline proves nothing either way, so it falls back to the
     *    publish window alone.
     *
     * [fixtures] are sorted newest-first, so between two compatible
     * meetings the latest still wins — but a video released before the
     * REMATCH existed now pairs with the meeting it actually documents.
     */
    internal fun pairWithFixture(entry: YtEntry, fixtures: List<Match>): Pair<YtEntry, Match>? {
        if (entry.videoId.isBlank()) return null
        val m = fixtures.firstOrNull { f ->
            titleMatches(entry.title, f.homeTeam.name) &&
                titleMatches(entry.title, f.awayTeam.name) &&
                publishWindowAllows(entry.publishedAtMs, f.utcDate) &&
                !seasonDeclaresOtherMeeting(entry.title, f.utcDate) &&
                !titleScoreDisagrees(
                    entry.title, f.homeTeam.name, f.awayTeam.name,
                    f.score.fullTime?.home, f.score.fullTime?.away
                )
        } ?: return null
        return entry to m
    }

    /** Test hook + manual refresh: forget the in-memory list. */
    fun invalidate() {
        cachedAt = 0L
        cache = emptyList()
    }
}

// ---------------------------------------------------------------------------
// Tier 1 — YouTube Data API v3 (optional, needs the free YOUTUBE_API_KEY)
// ---------------------------------------------------------------------------

/**
 * Search, then VERIFY every candidate against the roadmap checklist
 * before surfacing it. Returns null when nothing passes (the caller
 * falls back to the inherently-compliant channel-page tier).
 */
private fun viaDataApi(home: String, away: String): List<YtEntry>? {
    val q = URLEncoder.encode("$home vs $away highlights", "UTF-8")
    val searchUrl = "https://www.googleapis.com/youtube/v3/search" +
        "?part=snippet&type=video&maxResults=5&q=$q&key=${BuildConfig.YOUTUBE_API_KEY}"
    val text = httpGet(searchUrl) ?: return null
    data class Id(val videoId: String? = null)
    data class Item(val id: Id? = null)
    data class SearchResult(val items: List<Item> = emptyList())
    val ids = Gson().fromJson(text, SearchResult::class.java)
        .items.mapNotNull { it.id?.videoId }
        .filter { it.isNotBlank() }
    if (ids.isEmpty()) return null

    val detailUrl = "https://www.googleapis.com/youtube/v3/videos" +
        "?part=snippet,status,contentDetails&id=${ids.joinToString(",")}" +
        "&key=${BuildConfig.YOUTUBE_API_KEY}"
    val detailText = httpGet(detailUrl) ?: return null
    data class Snip(
        val channelId: String? = null,
        val channelTitle: String? = null,
        val publishedAt: String? = null
    )
    data class St(
        val uploadStatus: String? = null,
        val privacyStatus: String? = null,
        val embeddable: Boolean? = null
    )
    data class RegionRestriction(val type: String? = null, val regionCodes: List<String> = emptyList())
    data class ContentDet(val regionRestriction: RegionRestriction? = null)
    data class Video(
        val id: String? = null,
        val snippet: Snip? = null,
        val status: St? = null,
        val contentDetails: ContentDet? = null
    )
    data class VideoList(val items: List<Video> = emptyList())
    val country = deviceCountry()
    val videos = Gson().fromJson(detailText, VideoList::class.java).items
    return videos
        .filter { v ->
            verifyDataApiVideo(
                channelId = v.snippet?.channelId ?: "",
                uploadStatus = v.status?.uploadStatus,
                privacyStatus = v.status?.privacyStatus,
                embeddable = v.status?.embeddable,
                blocked = v.contentDetails?.regionRestriction
                    ?.takeIf { it.type.equals("block", true) }?.regionCodes ?: emptyList(),
                allowed = v.contentDetails?.regionRestriction
                    ?.takeIf { it.type.equals("allow", true) }?.regionCodes ?: emptyList(),
                deviceCountry = country
            )
        }
        .takeIf { it.isNotEmpty() }
        ?.map { v ->
            YtEntry(
                title = "$home vs $away — highlights",
                videoId = v.id ?: "",
                published = v.snippet?.publishedAt ?: "",
                channel = v.snippet?.channelTitle ?: "",
                publishedAtMs = isoUtcMs(v.snippet?.publishedAt) ?: 0L
            )
        }
        ?.filter { it.videoId.isNotBlank() && it.title.isNotBlank() }
}

/**
 * Pure roadmap §3.2 checklist. Unknown device country cannot be checked
 * against region lists — YouTube's own embed player still enforces its
 * regional blocks at playback, so we err on surfacing (step 5 needs a
 * country to have meaning).
 */
internal fun verifyDataApiVideo(
    channelId: String,
    uploadStatus: String?,
    privacyStatus: String?,
    embeddable: Boolean?,
    blocked: List<String>,
    allowed: List<String>,
    deviceCountry: String
): Boolean {
    if (OFFICIAL_CHANNELS.none { it.first == channelId } &&
        CLUB_CHANNELS.none { it.channelId == channelId }
    ) return false   // allowlist only
    if (!uploadStatus.equals("processed", true)) return false
    if (!privacyStatus.equals("public", true)) return false
    if (embeddable != true) return false
    return regionAllows(blocked, allowed, deviceCountry)
}

/** Region rule: a block list excludes; an allow list admits; unknown country passes. */
internal fun regionAllows(blocked: List<String>, allowed: List<String>, deviceCountry: String): Boolean {
    val c = deviceCountry.trim().uppercase()
    if (c.isEmpty()) return true
    if (blocked.any { it.trim().uppercase() == c }) return false
    if (allowed.isNotEmpty() && allowed.none { it.trim().uppercase() == c }) return false
    return true
}

/** ISO-ish device country ("MA", "FR", ...); empty when undeterminable. */
internal fun deviceCountry(): String = runCatching {
    java.util.Locale.getDefault().country ?: ""
}.getOrDefault("")

// ---------------------------------------------------------------------------
// Tier 2 — allowlisted channels' own video pages (no key, provenance exact)
// ---------------------------------------------------------------------------

private suspend fun viaOfficialChannels(home: String, away: String): List<YtEntry> =
    coroutineScope {
        val league = OFFICIAL_CHANNELS.map { (id, name) ->
            async { ChannelVideosStore.videos(id, name) }
        }.awaitAll().flatten()
        val clubs = (clubChannelsFor(home) + clubChannelsFor(away))
            .distinctBy { it.channelId }
        val clubVideos = clubs.map { c ->
            async { ChannelVideosStore.videos(c.channelId, c.club) }
        }.awaitAll().flatten()
        // League releases first (the canonical extended-highlights product),
        // then the two clubs' own channels; every source stays in page order,
        // which on YouTube's videos tab is newest-release-first.
        (league + clubVideos)
            .filter { titleMatches(it.title, home) && titleMatches(it.title, away) }
            .distinctBy { it.videoId }
            // v1.10.2 (owner report: error 152-4 at playback): every
            // candidate is verified against YouTube's public oEmbed
            // endpoint BEFORE it reaches the player — a video whose
            // embedding YouTube refuses never becomes a candidate at all.
            // Zero quota, no key, fail-open on network errors.
            .take(EMBED_CHECK_CAP)
            .let { candidates ->
                val ok = candidates.map { e -> async { oembedAllows(e.videoId) } }
                    .map { it.await() }
                candidates.filterIndexed { i, _ -> ok[i] }
            }
    }

/** Bound on oembed checks per resolve — title matches are few, but never
 *  more than this many network probes per fixture. */
private const val EMBED_CHECK_CAP = 8

/**
 * v1.10.2 — YouTube's public oEmbed endpoint as the free embeddability
 * oracle: it serves metadata (HTTP 200) for videos that may be embedded
 * and refuses (HTTP 401/403/404) exactly when the owner disabled
 * embedding or the video is gone. Catching those candidates here means
 * the player only ever sees videos YouTube is WILLING to embed.
 *
 * Network failure returns TRUE (fail-open): a temporary oembed outage
 * must not hide playable highlights — and if the video really is blocked,
 * the player's own error bridge + candidate cascade handle it honestly.
 * Pure decision logic: [oembedStatusAllows]. Tested.
 */
internal fun oembedStatusAllows(httpCode: Int?): Boolean =
    httpCode == null || httpCode == 200

private fun oembedAllows(videoId: String): Boolean {
    val id = videoId.replace(Regex("[^A-Za-z0-9_-]"), "")
    if (id.isBlank()) return false
    val watch = URLEncoder.encode("https://www.youtube.com/watch?v=$id", "UTF-8")
    val code = runCatching {
        val conn = URL("https://www.youtube.com/oembed?url=$watch&format=json")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 6_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; KooraApp/1.5)")
        try {
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
    return oembedStatusAllows(code)
}

/**
 * v1.10.3 — the channel-page cache. A channel's /videos page is
 * fixture-INDEPENDENT, so every fetch is memoized app-wide for 15 minutes:
 * the Hub scan, a match-detail resolve and a player retry all share one
 * copy. Only non-empty lists are cached — an empty result means the fetch
 * or extraction failed this round, and the next resolve must retry rather
 * than inherit a 15-minute hole.
 */
object ChannelVideosStore {

    private const val TTL_MS = 15L * 60 * 1000

    private class Slot(val at: Long, val entries: List<YtEntry>)

    private val slots = ConcurrentHashMap<String, Slot>()

    suspend fun videos(channelId: String, channelName: String): List<YtEntry> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            slots[channelId]?.takeIf { now - it.at <= TTL_MS }?.let { return@withContext it.entries }
            val entries = runCatching { fetchChannelVideos(channelId, channelName) }
                .getOrDefault(emptyList())
            if (entries.isNotEmpty()) slots[channelId] = Slot(now, entries)
            entries
        }

    /** Test hook + manual refresh. */
    fun invalidate() {
        slots.clear()
    }
}

private fun fetchChannelVideos(channelId: String, channelName: String): List<YtEntry> {
    val html = httpGet("https://www.youtube.com/channel/$channelId/videos") ?: return emptyList()
    return extractChannelVideos(html).map { it.copy(channel = channelName) }
}

private val ytInitialDataPatterns = listOf(
    Regex("""var ytInitialData = (\{.*?\});</script>""", setOf(RegexOption.DOT_MATCHES_ALL)),
    Regex("""window\["ytInitialData"\]\s*=\s*(\{.*?\});""", setOf(RegexOption.DOT_MATCHES_ALL))
)

private val VIDEO_ID_SHAPE = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * Extracts the video list from a channel /videos page: parses the
 * ytInitialData payload the page embeds, walks it for lockupViewModel
 * entries (the current YouTube grid renderer) and returns videoId + title
 * pairs IN PAGE ORDER (newest release first). Non-video lockups (walls,
 * playlists) carry contentIds that do not match the 11-char video shape
 * and are skipped. Pure, tested.
 */
internal fun extractChannelVideos(html: String, nowMs: Long = System.currentTimeMillis()): List<YtEntry> {
    val json = ytInitialDataPatterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) }
        ?: return emptyList()
    val root = runCatching { Gson().fromJson(json, Map::class.java) }.getOrNull()
        ?: return emptyList()
    val lockups = mutableListOf<Any>()
    collectLockups(root, lockups)
    val seen = mutableSetOf<String>()
    val out = mutableListOf<YtEntry>()
    for (raw in lockups) {
        val lk = raw as? Map<*, *> ?: continue
        val id = (lk["contentId"] as? String)?.trim() ?: continue
        if (!VIDEO_ID_SHAPE.matches(id) || !seen.add(id)) continue
        val title = (((lk["metadata"] as? Map<*, *>)
            ?.get("lockupMetadataViewModel") as? Map<*, *>)
            ?.get("title") as? Map<*, *>)
            ?.get("content") as? String
        if (title.isNullOrBlank()) continue
        val (agoText, ageMs) = lockupPublishAge(lk)
        out += YtEntry(
            title.trim(), id, agoText ?: "",
            publishedAtMs = ageMs?.let { nowMs - it } ?: 0L
        )
    }
    return out
}

/**
 * The grid's metadata row carries view counts and a relative publish
 * text; the accessibility label ("3 days ago") disambiguates the compact
 * content ("3d ago"), so it wins when both exist. Verified against the
 * live lockupViewModel shape on 2026-11-24.
 */
private fun lockupPublishAge(lk: Map<*, *>): Pair<String?, Long?> {
    val metadata = (((lk["metadata"] as? Map<*, *>)
        ?.get("lockupMetadataViewModel") as? Map<*, *>)
        ?.get("metadata") as? Map<*, *>)
        ?.get("contentMetadataViewModel") as? Map<*, *> ?: return null to null
    val metadataRows = metadata["metadataRows"] as? List<*> ?: return null to null
    var label: String? = null
    var content: String? = null
    for (row in metadataRows) {
        val parts = (row as? Map<*, *>)?.get("metadataParts") as? List<*> ?: continue
        for (part in parts) {
            val p = part as? Map<*, *> ?: continue
            val c = (p["text"] as? Map<*, *>)?.get("content") as? String
            val a = p["accessibilityLabel"] as? String
            if (label == null && a != null && relativeAgeMs(a) != null) label = a
            if (content == null && c != null && relativeAgeMs(c) != null) content = c
        }
    }
    val raw = label ?: content ?: return null to null
    return raw to relativeAgeMs(raw)
}

// ---------------------------------------------------------------------------
// Publish-time grounding + title score cross-check (pairing gates)
// ---------------------------------------------------------------------------

/** One shared UTC parser for ISO kick-offs/publish stamps (minSdk 24). */
private val isoUtcParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

internal fun isoUtcMs(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching { synchronized(isoUtcParser) { isoUtcParser.parse(iso)?.time } }.getOrNull()
}

private val AGO_WORD_MS = mapOf(
    "second" to 1_000L,
    "minute" to 60_000L,
    "hour" to 3_600_000L,
    "day" to 86_400_000L,
    "week" to 7 * 86_400_000L,
    "month" to 30 * 86_400_000L,
    "year" to 365 * 86_400_000L
)
private val AGO_FULL = Regex("""(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago""")
private val AGO_COMPACT = Regex("""^(\d+)\s*(mo|s|m|h|d|w|y)\s+ago$""")
private val AGO_COMPACT_MS = mapOf(
    "s" to 1_000L,
    "m" to 60_000L,
    "h" to 3_600_000L,
    "d" to 86_400_000L,
    "w" to 7 * 86_400_000L,
    "mo" to 30 * 86_400_000L,
    "y" to 365 * 86_400_000L
)

/**
 * Relative "ago" texts from YouTube's channel grid, e.g. the
 * accessibility label "3 days ago", a "Streamed 5 hours ago" variant,
 * or the compact content "3d ago". Returns the MINIMUM age the text
 * guarantees ("3 days ago" means at least 3 full days), so
 * now-minus-age is the LATEST publish instant the text still allows —
 * the conservative bound for rejecting stale videos. Months/years use
 * their shortest real length, keeping the bound permissive rather than
 * falsely rejecting.
 */
internal fun relativeAgeMs(text: String): Long? {
    AGO_FULL.find(text)?.let { m ->
        val n = m.groupValues[1].toLongOrNull() ?: return null
        return AGO_WORD_MS[m.groupValues[2]]?.let { it * n }
    }
    AGO_COMPACT.find(text.trim())?.let { m ->
        val n = m.groupValues[1].toLongOrNull() ?: return null
        return AGO_COMPACT_MS[m.groupValues[2].lowercase()]?.let { it * n }
    }
    return null
}

/**
 * Official highlights appear within hours of the final whistle — never
 * before the meeting exists (throwbacks, previews), and not days later
 * (magazine clips that merely NAME the teams). Unknown publish time (0)
 * or an unreadable kick-off is NOT a verified pairing and is refused.
 */
internal const val PUBLISH_EARLIEST_BEFORE_KICKOFF_MS = 12L * 3_600_000L
internal const val PUBLISH_LATEST_AFTER_KICKOFF_MS = 5L * 86_400_000L

internal fun publishWindowAllows(publishedAtMs: Long, fixtureUtc: String): Boolean {
    if (publishedAtMs <= 0L) return false
    val ko = isoUtcMs(fixtureUtc) ?: return false
    return publishedAtMs >= ko - PUBLISH_EARLIEST_BEFORE_KICKOFF_MS &&
        publishedAtMs <= ko + PUBLISH_LATEST_AFTER_KICKOFF_MS
}

/**
 * First plausible scoreline in a video title ("4-0", "1 - 2"). Guards:
 * scores above 15 are not football scores ("2025-26" season labels), and
 * a run glued to more digits or dashes is a date fragment, not a score.
 */
private val SCORE_IN_TITLE =
    Regex("""(?<![\d-])(\d{1,2})\s*[-–]\s*(\d{1,2})(?![\d-])""")

internal fun titleScoreline(title: String): Pair<Int, Int>? =
    SCORE_IN_TITLE.findAll(title)
        .firstOrNull {
            val a = it.groupValues[1].toIntOrNull() ?: 99
            val b = it.groupValues[2].toIntOrNull() ?: 99
            a <= 15 && b <= 15
        }
        ?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

/**
 * Season labels in a title ("2025/26") are fixture grounding too: a
 * video that DECLARES a different season documents another meeting —
 * throwback re-releases of classics, the exact owner-reported genre.
 * Only complete "20XX/YY" or "20XX-YY" spans count (date fragments like
 * "2026-11" are rejected by the span check); a fixture's season starts
 * in July. No label, no opinion. Closes the window gap the publish
 * check cannot: a classic re-released AFTER the new kick-off.
 */
private val SEASON_LABEL = Regex("""\b(20\d{2})[/\-](\d{2})\b""")

internal fun titleSeasonStart(title: String): Int? =
    SEASON_LABEL.find(title)?.let { m ->
        val start = m.groupValues[1].toIntOrNull() ?: return null
        val end = m.groupValues[2].toIntOrNull() ?: return null
        if ((start + 1) % 100 != end) return null
        start
    }

/** Season start year of a fixture from its ISO kick-off (no clock). */
internal fun fixtureSeasonStart(fixtureUtc: String): Int? {
    val year = fixtureUtc.take(4).toIntOrNull() ?: return null
    val month = fixtureUtc.substring(5, 7).toIntOrNull() ?: return null
    if (month !in 1..12) return null
    return if (month >= 7) year else year - 1
}

internal fun seasonDeclaresOtherMeeting(title: String, fixtureUtc: String): Boolean {
    val label = titleSeasonStart(title) ?: return false
    val seasonStart = fixtureSeasonStart(fixtureUtc) ?: return false
    return label != seasonStart
}

/**
 * Reliable REJECTION only: true when the title carries a scoreline that
 * contradicts the fixture's recorded score. The teams' order in the
 * title sets the score's orientation when both are found; when it cannot
 * be read, only the score SET can disagree. Unknown fixture scores and
 * scoreless titles never disagree.
 */
internal fun titleScoreDisagrees(
    title: String,
    homeTeam: String,
    awayTeam: String,
    homeGoals: Int?,
    awayGoals: Int?
): Boolean {
    if (homeGoals == null || awayGoals == null) return false
    val (a, b) = titleScoreline(title) ?: return false
    val lower = title.lowercase()
    val homeIdx = tokens(homeTeam)
        .mapNotNull { lower.indexOf(it).takeIf { i -> i >= 0 } }
        .minOrNull() ?: -1
    val awayIdx = tokens(awayTeam)
        .mapNotNull { lower.indexOf(it).takeIf { i -> i >= 0 } }
        .minOrNull() ?: -1
    return if (homeIdx >= 0 && awayIdx >= 0 && homeIdx != awayIdx) {
        val (th, ta) = if (homeIdx < awayIdx) a to b else b to a
        th != homeGoals || ta != awayGoals
    } else {
        !((a == homeGoals && b == awayGoals) || (a == awayGoals && b == homeGoals))
    }
}

private fun collectLockups(node: Any?, out: MutableList<Any>) {
    when (node) {
        is Map<*, *> -> {
            node["lockupViewModel"]?.let { out += it }
            node.values.forEach { collectLockups(it, out) }
        }
        is List<*> -> node.forEach { collectLockups(it, out) }
    }
}

/** Token-based title matching tolerant of provider naming differences. */
private val stopTokens = setOf("fc", "cf", "club", "athletic", "ac", "sc", "afc", "as", "ca", "cd", "if", "de", "the")

// Tokens too generic to match alone ("Real Betis" vs "Real Madrid", "Man City")
private val ambiguousTokens = setOf("real", "city", "inter", "united", "sporting", "racing", "union", "atletico")

private fun tokens(name: String): List<String> =
    name.lowercase()
        .replace(Regex("[^\\p{L}\\p{Nd} ]"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() && it !in stopTokens }

internal fun titleMatches(title: String, team: String): Boolean {
    val t = title.lowercase()
    val toks = tokens(team)
    // A distinctive token (>=4 chars, not generic) decides the match
    if (toks.any { it !in ambiguousTokens && it.length >= 4 && t.contains(it) }) return true
    // Otherwise only the full normalized name counts
    val full = toks.joinToString(" ")
    return full.isNotEmpty() && t.contains(full)
}

/**
 * Desktop user-agent REQUIRED (v1.10.3): YouTube serves the mobile web
 * shell without the ytInitialData video list to mobile UAs — the page
 * arrives ~500 KB with zero video metadata. A desktop UA receives the
 * full payload (~1.2 MB with the grid). googleapis endpoints ignore the
 * UA; oembedAllows keeps its own identity header.
 */
internal fun httpGet(url: String): String? = runCatching {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 8_000
    conn.readTimeout = 12_000
    conn.instanceFollowRedirects = true
    conn.setRequestProperty(
        "User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    )
    conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
    try {
        conn.inputStream.use { it.readBytes().decodeToString() }
    } finally {
        conn.disconnect()
    }
}.getOrNull()
