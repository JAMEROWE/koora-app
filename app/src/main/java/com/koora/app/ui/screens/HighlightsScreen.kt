package com.koora.app.ui.screens

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.koora.app.BuildConfig
import com.koora.app.R
import com.koora.app.ads.AdBanner
import com.koora.app.ads.AdsManager
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
 * v1.5.0: the user no longer lands on a YouTube search page. We RESOLVE
 * the actual highlight video first, then show only YouTube's OFFICIAL
 * embed player — no YouTube header, no recommendations, no "open app"
 * banners. The small YouTube logo on the player itself stays: that is
 * required by YouTube's terms and is the reason this stays legal (we
 * never host or re-stream anything).
 *
 * Resolution order:
 *   1. YouTube Data API search (if YOUTUBE_API_KEY is set) — exact match.
 *   2. Official league channels' RSS feeds (no key needed): Premier
 *      League, LaLiga, Ligue 1, UEFA, Bundesliga, Serie A publish their
 *      highlights; we match the fixture's team names against video
 *      titles and pick the newest.
 *   3. Fallback: in-app YouTube search page (rare — when no video is
 *      found, e.g. competitions without an official feed).
 *
 * Monetization at the peak moment, still moderate:
 *  - the ONE capped interstitial fires when the user LEAVES the video
 *    (natural break — max 1 per 4 minutes, never in the first session,
 *    the cap is SHARED with live matches);
 *  - the banner keeps running under the player for the whole watch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsScreen(home: String, away: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Resolved video id; null while resolving; SEARCH_PAGE marks the fallback.
    var videoId by remember { mutableStateOf<String?>(null) }
    var searchPageFallback by remember { mutableStateOf(false) }

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

    LaunchedEffect(home, away) {
        val resolved = resolveVideoId(home, away)
        if (resolved != null) videoId = resolved else searchPageFallback = true
    }

    fun closeFullscreen() {
        runCatching { customViewCallback?.onCustomViewHidden() }
        customViewCallback = null
        customView = null
    }

    BackHandler {
        val w = webView
        when {
            customView != null -> closeFullscreen()
            w != null && w.canGoBack() -> w.goBack()
            else -> exitAfterAd()
        }
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
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                when {
                    // Resolving the exact video — brief spinner
                    videoId == null && !searchPageFallback -> CircularProgressIndicator()

                    // PLAYER-ONLY VIEW: the official YouTube embed. Nothing
                    // else from YouTube is visible — it feels like Koora's own player.
                    videoId != null -> AndroidView(
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
                                webViewClient = WebViewClient()
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
                                loadUrl(
                                    "https://www.youtube.com/embed/$videoId" +
                                        "?autoplay=1&rel=0&playsinline=1"
                                )
                                webView = this
                            }
                        }
                    )

                    // Rare fallback: search page stays INSIDE the app too
                    else -> AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = WebViewClient()
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(v: WebView?, p: Int) {
                                        progress = p
                                    }
                                }
                                val q = runCatching {
                                    URLEncoder.encode("$home vs $away highlights", "UTF-8")
                                }.getOrDefault("$home+vs+$away+highlights")
                                loadUrl("https://www.youtube.com/results?search_query=$q")
                                webView = this
                            }
                        }
                    )
                }
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

private data class YtEntry(val title: String, val videoId: String, val published: String)

private suspend fun resolveVideoId(home: String, away: String): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            if (BuildConfig.YOUTUBE_API_KEY.isNotBlank()) {
                viaDataApi(home, away) ?: viaOfficialChannels(home, away)
            } else {
                viaOfficialChannels(home, away)
            }
        }.getOrNull()
    }

/** Tier 1 (optional): exact search across ALL of YouTube, needs a free API key. */
private fun viaDataApi(home: String, away: String): String? {
    val q = URLEncoder.encode("$home vs $away highlights", "UTF-8")
    val url = "https://www.googleapis.com/youtube/v3/search" +
        "?part=snippet&type=video&maxResults=1&q=$q&key=${BuildConfig.YOUTUBE_API_KEY}"
    val text = httpGet(url) ?: return null
    data class Id(val videoId: String? = null)
    data class Item(val id: Id? = null)
    data class SearchResult(val items: List<Item> = emptyList())
    return Gson().fromJson(text, SearchResult::class.java).items.firstOrNull()?.id?.videoId
}

/** Tier 2 (no key): match team names against official league channels' feeds. */
private suspend fun viaOfficialChannels(home: String, away: String): String? =
    coroutineScope {
        OFFICIAL_CHANNELS.map { ch -> async { runCatching { fetchChannelFeed(ch) }.getOrDefault(emptyList()) } }
            .flatMap { it.await() }
            .filter { titleMatches(it.title, home) && titleMatches(it.title, away) }
            .maxByOrNull { it.published }  // newest highlight wins
            ?.videoId
    }

private fun fetchChannelFeed(channelId: String): List<YtEntry> {
    val text = httpGet("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
        ?: return emptyList()
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
            XmlPullParser.TEXT -> if (inEntry) when (tag?.lowercase()) {
                "title" -> title += parser.text
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
