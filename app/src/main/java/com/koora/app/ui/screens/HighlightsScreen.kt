package com.koora.app.ui.screens

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.koora.app.R
import com.koora.app.ads.AdBanner
import com.koora.app.ads.AdsManager
import java.net.URLEncoder

/*
 * IN-APP VIDEO HIGHLIGHTS (v1.4.2) — "Watch highlights" no longer throws
 * the user out to YouTube. A WebView loads YouTube's own search results
 * for this fixture, and every video plays through YouTube's OFFICIAL
 * player inside Koora — legal by design (we never host or re-stream
 * anything; we only ever show YouTube's own player).
 *
 * Monetization at the peak moment, still moderate:
 *  - the ONE capped interstitial fires on entry (max 1 per 4 minutes,
 *    never in the first session — the cap is SHARED with live-match
 *    entry, so a user can never see two ads back to back);
 *  - the banner keeps running under the player for the whole watch.
 *
 * Fullscreen: tapping the player's fullscreen button rotates into a
 * YouTube-provided fullscreen view (WebChromeClient custom view).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsScreen(home: String, away: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Capped interstitial on entry — "click to watch, an ad pops" (shared cap)
    LaunchedEffect(Unit) {
        (context as? Activity)?.let { AdsManager.maybeShowInterstitial(it) }
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
            else -> onBack()
        }
    }

    val query = remember(home, away) {
        runCatching { URLEncoder.encode("$home vs $away highlights", "UTF-8") }
            .getOrDefault("$home+vs+$away+highlights")
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
                    IconButton(onClick = onBack) {
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

            Box(Modifier.fillMaxWidth().weight(1f)) {
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
                            webViewClient = android.webkit.WebViewClient()
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(v: WebView?, p: Int) {
                                    progress = p
                                }

                                override fun onShowCustomView(
                                    view: View,
                                    callback: CustomViewCallback
                                ) {
                                    // Fullscreen video (YouTube-provided view)
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
                                "https://www.youtube.com/results?search_query=$query"
                            )
                            webView = this
                        }
                    }
                )
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
