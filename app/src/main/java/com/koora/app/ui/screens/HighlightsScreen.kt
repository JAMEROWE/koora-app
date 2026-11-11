package com.koora.app.ui.screens

import android.app.Activity
import android.view.View
import android.webkit.WebChromeClient
import androidx.activity.compose.BackHandler
// v1.8.0: video resolution moved to HighlightsSource.kt. v1.9.0: playback
// moved to OfficialInAppYouTubePlayer (Error 153 fix, master spec §7.2).
// This file keeps only resolution + the Koora panel around the player.
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.koora.app.R
import com.koora.app.ads.AdBanner
import com.koora.app.ads.AdsManager
import com.koora.app.ui.components.LivePulseDot
import com.koora.app.ui.components.OfficialInAppYouTubePlayer
import com.koora.app.ui.theme.PitchAccent

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
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // v1.5.4: resolve ALL matching official videos, newest first.
    // The first plays immediately; the rest fill OUR panel under the player.
    var videos by remember { mutableStateOf<List<YtEntry>>(emptyList()) }
    var pickedId by remember { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf(true) }
    var retryTick by remember { mutableIntStateOf(0) }

    // v1.9.0 (spec §7.1.4): videos that fail IN the player (100/101/150/153)
    // are hidden (GONE) and the next candidate auto-plays. Never an error
    // box, never an external app. When every candidate failed we show the
    // honest branded state — never a placeholder video.
    var blockedIds by remember { mutableStateOf(setOf<String>()) }
    val currentId: String? =
        videos.firstOrNull { it.videoId == pickedId && it.videoId !in blockedIds }?.videoId
            ?: videos.firstOrNull { it.videoId !in blockedIds }?.videoId

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
        pickedId = null
        blockedIds = emptySet()
        val found = HighlightsSource.resolve(context, home, away)
        videos = found
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
        pickedId = entry.videoId
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

            when {
                // Resolving the exact video — branded, calm, no spinner wall
                resolving -> ResolvingState()

                // PLAYER-ONLY VIEW: the official embed in a 16:9 frame, with
                // Koora's own "More highlights" panel below — never YouTube's.
                currentId != null -> Column(Modifier.fillMaxSize()) {
                    // v1.9.0 (spec §7.2): official IFrame API bootstrapped from
                    // an https://www.youtube.com base document + origin var —
                    // the Error 153 fix. Any embed error hides the player (GONE)
                    // and auto-plays the next candidate.
                    OfficialInAppYouTubePlayer(
                        videoId = currentId,
                        onEnterFullscreen = { view, callback ->
                            if (customView != null) {
                                callback.onCustomViewHidden()
                                return@OfficialInAppYouTubePlayer
                            }
                            customView = view
                            customViewCallback = callback
                        },
                        onExitFullscreen = {
                            customView = null
                            customViewCallback = null
                        },
                        onEmbedBlockedOrFailed = {
                            currentId?.let { blockedIds = blockedIds + it }
                        }
                    )

                    // OUR content under OUR player
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // v1.8.0 attribution (roadmap §3.4) — shown whenever the
                        // player is visible: official video, streamed by YouTube.
                        val currentChannel = videos
                            .firstOrNull { it.videoId == currentId }?.channel
                        Text(
                            stringResource(
                                R.string.video_attribution,
                                currentChannel?.takeIf { it.isNotBlank() } ?: "YouTube"
                            ),
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
