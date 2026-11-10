package com.koora.app.ui.screens

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.koora.app.BuildConfig
import com.koora.app.R
import com.koora.app.ads.AdBanner
import com.koora.app.ads.AdsManager
import com.koora.app.ui.VideoPolicy
import com.koora.app.ui.components.LivePulseDot
import com.koora.app.ui.theme.PitchAccent
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
 * IN-APP VIDEO HIGHLIGHTS — "Watch highlights" plays INSIDE Koora.
 *
 * v1.5.4 — the screen is now 100% Koora's own player experience:
 *  - The ONLY page ever loaded here is the official YouTube embed
 *    (VideoPolicy enforces it): taps on the YouTube logo / share /
 *    redirects can no longer show YouTube's website inside the app.
 *  - The old YouTube-search fallback is GONE. When no official video is
 *    found we show a branded "not published yet" state (never fake data,
 *    never YouTube's site).
 *  - A 16:9 player sits at the top; BELOW it is OUR panel — the other
 *    official videos found for this fixture ("More highlights"), so the
 *    space around the player is Koora content, not YouTube's.
 *  - Playback starts instantly on entry (autoplay) with our title bar;
 *    the small YouTube logo on the player itself stays — that is
 *    required by YouTube's terms and is what keeps embedding legal
 *    (we never host or re-stream anything).
 *
 * Resolution order:
 *   1. YouTube Data API search (if YOUTUBE_API_KEY is set) — exact match.
 *   2. Official league channels' RSS feeds (no key needed): Premier
 *      League, LaLiga, Ligue 1, UEFA, Bundesliga, Serie A publish their
 *      highlights; we match the fixture's team names against video
 *      titles and keep ALL matches, newest first.
 *
 * Monetization at the peak moment, still moderate:
 *  - the ONE capped interstitial fires when the user LEAVES the video
 *    (natural break — max 1 per 4 minutes, never in the first session,
 *    the cap is SHARED with live matches);
 *  - the banner keeps running at the bottom for the whole watch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsScreen(home: String, away: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // v1.5.4: resolve ALL matching official videos, newest first.
    // The first plays immediately; the rest fill OUR panel under the player.
    var videos by remember { mutableStateOf<List<YtEntry>>(emptyList()) }
    var currentId by remember { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf(true) }
    var retryTick by remember { mutableIntStateOf(0) }

    // Policy-safe ad moment (v1.5.3): the ONE capped interstitial fires when
    // the user LEAVES the video — end of the content segment, never at its
    // start (Google Play interstitial guidance). Same 4-min shared cap,
    // never in the first session. Navigation continues after the ad.
    fun exitAfterAd() {
        val activity = context as? Activity
        if (activity != null) {
            AdsManager.maybeShowInterstitial(activity) { onBack() }
        } else {
            onBack()
        }
    }

    LaunchedEffect(home, away, retryTick) {
        resolving = true
        currentId = null
        val found = resolveVideos(context, home, away)
        videos = found
        currentId = found.firstOrNull()?.videoId
        resolving = false
    }

    fun closeFullscreen() {
        runCatching { customViewCallback?.onCustomViewHidden() }
        customViewCallback = null
        customView = null
    }

    fun pick(entry: YtEntry) {
        if (entry.videoId == currentId) return
        closeFullscreen()
        currentId = entry.videoId
        webView?.loadUrl(VideoPolicy.embedUrl(entry.videoId))
    }

    // With the navigation policy blocking every escape there is nothing
    // useful behind the player — system back always leaves the screen.
    BackHandler {
        if (customView != null) closeFullscreen() else exitAfterAd()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.video_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { exitAfterAd() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
            Text(
                "$home vs $away",
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (progress in 1..99 && currentId != null) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when {
                // Resolving the exact video — branded, calm, no spinner wall
                resolving -> ResolvingState()

                // PLAYER-ONLY VIEW: the official embed in a 16:9 frame, with
                // Koora's own "More highlights" panel below — never YouTube's.
                currentId != null -> Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView,
                                            request: WebResourceRequest
                                        ): Boolean {
                                            if (!request.isForMainFrame) return false
                                            // v1.5.4 policy: the ONLY page this
                                            // screen loads is the official embed.
                                            // Logo / share / redirect taps can no
                                            // longer show YouTube's site.
                                            return !VideoPolicy.isAllowedMainframeUrl(
                                                request.url.toString()
                                            )
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(v: WebView?, p: Int) {
                                            progress = p
                                        }

                                        override fun onShowCustomView(
                                            view: View,
                                            callback: CustomViewCallback
                                        ) {
                                            if (customView != null) {
                                                callback.onCustomViewHidden()
                                                return
                                            }
                                            customView = view
                                            customViewCallback = callback
                                        }

                                        override fun onHideCustomView() {
                                            customView = null
                                            customViewCallback = null
                                        }
                                    }
                                    loadUrl(VideoPolicy.embedUrl(currentId ?: ""))
                                    webView = this
                                }
                            }
                        )
                    }

                    // OUR content under OUR player
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val more = videos.drop(1)
                        if (more.isNotEmpty()) {
                            Text(
                                stringResource(R.string.video_more),
                                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            more.forEach { entry ->
                                val selected = entry.videoId == currentId
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { pick(entry) }
                                        .background(
                                            if (selected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.PlayCircle,
                                        contentDescription = null,
                                        tint = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        entry.title,
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // No official video found — branded state, NEVER YouTube's site
                else -> NoHighlightsState(onRetry = { retryTick++ })
            }

            // Ads while watching — the other reason watching stays in-app
            AdBanner()
        }

        // Fullscreen video overlay, drawn over everything
        customView?.let { fv ->
            AndroidView(factory = { fv }, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ResolvingState() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LivePulseDot(color = PitchAccent, dotSize = 14.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.video_finding),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NoHighlightsState(onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.SportsSoccer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.video_none_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.video_none_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

// ---------------------------------------------------------------------------
// Video resolution
// ---------------------------------------------------------------------------

/** Official competition channels (verified live 2026-11-09). */
private val OFFICIAL_CHANNELS = listOf(
    "UCG5qGWdu8nIRZqJ_GgDwQ-w", // Premier League
    "UCTv-XvfzLX3i4IGWAm4sbmA", // LaLiga
    "UCQsH5XtIc9hONE1BQjucM0g", // Ligue 1
    "UCyGa1YEx9ST66rYrJTGIKOw", // UEFA (Champions League)
    "UC6UL29enLNe4mqwTfAyeNuw", // Bundesliga
    "UCBJeMCIeLQos7wacox4hmLQ"  // Serie A
)

internal data class YtEntry(val title: String, val videoId: String, val published: String)

/** All matching official videos, newest first (v1.5.4 feeds our panel).
 *  v1.5.6: resolved at most once per fixture — VideoCache serves found
 *  videos for 24h and a fresh "nothing yet" for 45 minutes. */
private suspend fun resolveVideos(context: android.content.Context?, home: String, away: String): List<YtEntry> =
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

/** Tier 1 (optional): exact search across ALL of YouTube, needs a free API key. */
private fun viaDataApi(home: String, away: String): List<YtEntry>? {
    val q = URLEncoder.encode("$home vs $away highlights", "UTF-8")
    val url = "https://www.googleapis.com/youtube/v3/search" +
        "?part=snippet&type=video&maxResults=1&q=$q&key=${BuildConfig.YOUTUBE_API_KEY}"
    val text = httpGet(url) ?: return null
    data class Id(val videoId: String? = null)
    data class Item(val id: Id? = null)
    data class SearchResult(val items: List<Item> = emptyList())
    val id = Gson().fromJson(text, SearchResult::class.java).items.firstOrNull()?.id?.videoId
    return id?.let { listOf(YtEntry("$home vs $away — highlights", it, "")) }
}

/** Tier 2 (no key): match team names against official league channels' feeds. */
private suspend fun viaOfficialChannels(home: String, away: String): List<YtEntry> =
    coroutineScope {
        OFFICIAL_CHANNELS.map { ch -> async { runCatching { fetchChannelFeed(ch) }.getOrDefault(emptyList()) } }
            .flatMap { it.await() }
            .filter { titleMatches(it.title, home) && titleMatches(it.title, away) }
            .sortedByDescending { it.published }  // newest highlights first
    }

private fun fetchChannelFeed(channelId: String): List<YtEntry> {
    val text = httpGet("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
        ?: return emptyList()
    return parseChannelFeed(text)
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

private fun httpGet(url: String): String? = runCatching {
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
