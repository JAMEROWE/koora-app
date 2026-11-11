package com.koora.app.ui.screens

import android.content.Context
import com.google.gson.Gson
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
import java.net.URLEncoder

/*
 * OFFICIAL-HIGHLIGHT RESOLUTION — v1.8.0.
 *
 * Extracted from HighlightsScreen so the MATCH DETAIL screen can gate the
 * "Watch highlights" button on VERIFIED availability (roadmap P0):
 * the button is rendered only when a video has been confirmed, and the
 * whole section stays out of the UI tree otherwise — never a disabled
 * state, placeholder box or "not published yet" notice.
 *
 * ALLOWLIST COMPLIANCE (roadmap §3.2). A video is surfaced ONLY when ALL
 * of these pass — enforced by [verifyDataApiVideo] for the Data API tier:
 *   1. snippet.channelId is in OFFICIAL_CHANNELS (verified rights-holders)
 *   2. status.uploadStatus == "processed"
 *   3. status.privacyStatus == "public"
 *   4. status.embeddable == true
 *   5. contentDetails.regionRestriction does not block the device country
 * The channel-RSS tier is inherently compliant: entries come from the
 * allowlisted channels' own feeds, so their channel provenance is exact.
 *
 * NEVER: open YouTube search results without verification, third-party
 * re-upload channels, scraping video streams, or caching media. Only
 * metadata (title / videoId / published) is cached, in VideoCache.
 */

/** Official competition channels (verified live 2026-11-09), id -> name. */
internal val OFFICIAL_CHANNELS: List<Pair<String, String>> = listOf(
    "UCG5qGWdu8nIRZqJ_GgDwQ-w" to "Premier League",
    "UCTv-XvfzLX3i4IGWAm4sbmA" to "LaLiga",
    "UCQsH5XtIc9hONE1BQjucM0g" to "Ligue 1",
    "UCyGa1YEx9ST66rYrJTGIKOw" to "UEFA",
    "UC6UL29enLNe4mqwTfAyeNuw" to "Bundesliga",
    "UCBJeMCIeLQos7wacox4hmLQ" to "Serie A"
)

internal data class YtEntry(
    val title: String,
    val videoId: String,
    val published: String,
    val channel: String = ""
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
// Tier 1 — YouTube Data API v3 (optional, needs the free YOUTUBE_API_KEY)
// ---------------------------------------------------------------------------

/**
 * Search, then VERIFY every candidate against the roadmap checklist
 * before surfacing it. Returns null when nothing passes (the caller
 * falls back to the inherently-compliant RSS tier).
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
    data class Snip(val channelId: String? = null, val channelTitle: String? = null)
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
                published = "",
                channel = v.snippet?.channelTitle ?: ""
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
    if (OFFICIAL_CHANNELS.none { it.first == channelId }) return false   // allowlist only
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
// Tier 2 — allowlisted channels' own RSS feeds (no key, provenance exact)
// ---------------------------------------------------------------------------

private suspend fun viaOfficialChannels(home: String, away: String): List<YtEntry> =
    coroutineScope {
        OFFICIAL_CHANNELS.map { (id, name) ->
            async { runCatching { fetchChannelFeed(id, name) }.getOrDefault(emptyList()) }
        }
            .flatMap { it.await() }
            .filter { titleMatches(it.title, home) && titleMatches(it.title, away) }
            .sortedByDescending { it.published }  // newest highlights first
    }

private fun fetchChannelFeed(channelId: String, channelName: String): List<YtEntry> {
    val text = httpGet("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
        ?: return emptyList()
    return parseChannelFeed(text).map { it.copy(channel = channelName) }
}

/**
 * Parses a YouTube channel Atom feed. THE FIX (v1.5.6): with namespace
 * processing disabled (Android default) the tag of <yt:videoId> arrives
 * as "yt:videoId" — the old code compared it to "videoid" and NEVER
 * matched, so official-channel highlights silently resolved to nothing.
 * We now strip the prefix before matching. A saved real-shaped feed is
 * unit-tested in HighlightsParserTest.
 */
internal fun parseChannelFeed(text: String): List<YtEntry> {
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), null)

    val entries = mutableListOf<YtEntry>()
    var inEntry = false
    var tag: String? = null
    var title = ""
    var videoId = ""
    var published = ""

    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
            XmlPullParser.START_TAG -> {
                val name = parser.name ?: ""
                if (name.equals("entry", true)) {
                    inEntry = true
                    title = ""; videoId = ""; published = ""
                }
                tag = name
            }
            XmlPullParser.TEXT -> if (inEntry) when (tag?.substringAfterLast(':')?.lowercase()) {
                // Guard: <media:title> repeats <title> — capture only the first.
                "title" -> if (title.isEmpty()) title += parser.text
                "videoid" -> videoId += parser.text
                "published" -> published += parser.text
            }
            XmlPullParser.END_TAG -> {
                val name = parser.name ?: ""
                if (name.equals("entry", true)) {
                    inEntry = false
                    if (videoId.isNotBlank()) {
                        entries += YtEntry(title.trim(), videoId.trim(), published.trim())
                    }
                }
            }
        }
        parser.next()
    }
    return entries
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

internal fun httpGet(url: String): String? = runCatching {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 8_000
    conn.readTimeout = 12_000
    conn.instanceFollowRedirects = true
    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; KooraApp/1.5)")
    try {
        conn.inputStream.use { it.readBytes().decodeToString() }
    } finally {
        conn.disconnect()
    }
}.getOrNull()
