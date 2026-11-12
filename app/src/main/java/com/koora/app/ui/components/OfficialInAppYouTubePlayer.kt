package com.koora.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/*
 * OFFICIAL IN-APP YOUTUBE PLAYER (v1.9.0, master spec §6.4) — the Error
 * 153 fix, replacing the raw embed-URL WebView.
 *
 * WHY THIS EXISTS: loading https://www.youtube.com/embed/<id> directly in
 * a WebView sends NO Referer/Origin for the player's own XHR calls, and
 * YouTube answers with error 153 ("video cannot be played in this
 * player") — a silent BLACK BOX, often without any JS error callback.
 *
 * THE v1.10.0 BLACK-BOX FIX (owner report: the box persisted on real
 * devices): the IFrame API script creates the player iframe itself, and
 * the created iframe carries NO referrerpolicy — on WebView versions that
 * send no referrer header for data-loaded documents, YouTube rejects the
 * player BEFORE the API handshake, so neither onReady nor onError ever
 * fires. Per the Feb-2026 advisory on error 153, the fix is to hand-write
 * the <iframe> with referrerpolicy="strict-origin" (the parent document
 * IS https://www.youtube.com via loadDataWithBaseURL, so the origin is
 * sent as the referrer) and attach the IFrame API to it afterwards for
 * onReady / onStateChange / onError.
 *
 * Policy baked in (spec §6.1):
 *  - the IFrame API script is the only script; playback stays 100% in-app;
 *  - main-frame navigation is refused for every host outside
 *    youtube.com / youtube-nocookie.com / ytimg.com / googlevideo.com,
 *    so logo / share / redirect taps can never turn the screen into
 *    YouTube's website (VideoPolicy's promise, host-level form);
 *  - the JS bridge reports IFrame onError (100/101/150/153...) AND the
 *    playback state to Compose: a player that never reports ready in 4s,
 *    a renderer crash, or a player that never actually PLAYS within 12s
 *    hands the video to the caller (GONE) — never a lingering black box;
 *  - mixed content is never allowed; the WebView UA is the normal
 *    browser UA (the "; wv" token removed) so YouTube serves the full
 *    embed experience.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OfficialInAppYouTubePlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    onEnterFullscreen: ((View, WebChromeClient.CustomViewCallback) -> Unit)? = null,
    onExitFullscreen: (() -> Unit)? = null,
    // v1.9.1: carries the FAILED videoId so the caller can hide that
    // candidate and hand the same video to the in-app Custom Tab.
    onEmbedBlockedOrFailed: (String) -> Unit
) {
    val context = LocalContext.current

    // v1.9.1 (spec §6.1): false until the IFrame player reports onReady.
    // A spinner shows meanwhile; if nothing reports within 4s (stalled
    // black box) the in-app Custom Tab fallback takes over.
    var isPlayerReady by remember(videoId) { mutableStateOf(false) }

    // v1.10.0: onReady alone is NOT proof of picture — the notorious
    // black-box-with-audio case reports ready and never actually plays.
    // PLAYING state (1) is the only honest "you are watching" signal.
    var sawPlaying by remember(videoId) { mutableStateOf(false) }

    val latestOnBlocked by rememberUpdatedState(onEmbedBlockedOrFailed)

    val webView = remember(videoId) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                userAgentString = settings.userAgentString.replace("; wv", "")
            }

            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onPlayerReady() {
                        post { isPlayerReady = true }
                    }

                    @JavascriptInterface
                    fun onPlayerState(state: Int) {
                        post { if (state == 1) sawPlaying = true }
                    }

                    @JavascriptInterface
                    fun onPlayerError(errorCode: Int) {
                        post { onEmbedBlockedOrFailed(videoId) }
                    }
                },
                "KooraPlayerBridge"
            )

            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view != null && callback != null) {
                        onEnterFullscreen?.invoke(view, callback)
                    }
                }

                override fun onHideCustomView() {
                    onExitFullscreen?.invoke()
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val host = request?.url?.host ?: return true
                    val isAllowedEmbed = host.endsWith("youtube.com") ||
                        host.endsWith("youtube-nocookie.com") ||
                        host.endsWith("ytimg.com") ||
                        host.endsWith("googlevideo.com")
                    return !isAllowedEmbed
                }

                // v1.10.0: a crashed WebView renderer is the ultimate black
                // box — absorb it (return true keeps Koora alive) and hand
                // the video to the in-app Custom Tab fallback.
                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail
                ): Boolean {
                    view.post { onEmbedBlockedOrFailed(videoId) }
                    return true
                }
            }

            val safeId = videoId.replace(Regex("[^A-Za-z0-9_-]"), "")
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        html, body { width: 100%; height: 100%; background-color: #000000; overflow: hidden; }
                        #player { width: 100%; height: 100%; border: 0; }
                    </style>
                </head>
                <body>
                    <!-- v1.10.0 Error-153 fix: the iframe is hand-written with
                         referrerpolicy="strict-origin" — the parent document's
                         base IS https://www.youtube.com, so the origin reaches
                         YouTube as the referrer even on WebViews that send no
                         referrer for data-loaded pages. enablejsapi=1 lets the
                         IFrame API attach below for onReady/state/error. -->
                    <iframe
                        id="player"
                        src="https://www.youtube.com/embed/$safeId?autoplay=1&rel=0&playsinline=1&enablejsapi=1&origin=https%3A%2F%2Fwww.youtube.com"
                        referrerpolicy="strict-origin"
                        allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
                        allowfullscreen
                        frameborder="0"></iframe>
                    <script src="https://www.youtube.com/iframe_api"></script>
                    <script>
                        var player;
                        function onYouTubeIframeAPIReady() {
                            player = new YT.Player('player', {
                                events: {
                                    'onReady': function() {
                                        if (window.KooraPlayerBridge) {
                                            window.KooraPlayerBridge.onPlayerReady();
                                        }
                                    },
                                    'onStateChange': function(event) {
                                        if (window.KooraPlayerBridge) {
                                            window.KooraPlayerBridge.onPlayerState(event.data);
                                        }
                                    },
                                    'onError': function(event) {
                                        if (window.KooraPlayerBridge) {
                                            window.KooraPlayerBridge.onPlayerError(event.data);
                                        }
                                    }
                                }
                            });
                        }
                    </script>
                </body>
            </html>
            """.trimIndent()

            loadDataWithBaseURL(
                "https://www.youtube.com",
                html,
                "text/html",
                "utf-8",
                null
            )
        }
    }

    // v1.9.1 (spec §6.1, §6.4): the black-box stall guard. If the IFrame
    // player neither reports ready nor errors within 4 seconds, hand THIS
    // video to the in-app Custom Tab — playback guaranteed without ever
    // leaving Koora's task.
    LaunchedEffect(videoId) {
        delay(4_000L)
        if (!isPlayerReady) latestOnBlocked(videoId)
    }

    // v1.10.0: the no-playback guard. onReady can fire while the video
    // never actually renders (black box with audio, autoplay blocked by
    // an OEM WebView, silent 153) — if PLAYING is never seen within 12s,
    // the in-app Custom Tab takes over. The user watches, always.
    LaunchedEffect(videoId) {
        delay(12_000L)
        if (!sawPlaying) latestOnBlocked(videoId)
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    // key(videoId): AndroidView invokes its factory once per node —
    // without the key, picking another video in "More highlights" would
    // keep the old (already destroyed) WebView on screen. The key swaps
    // in the freshly built player for every candidate.
    key(videoId) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(androidx.compose.ui.graphics.Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
            )
            if (!isPlayerReady) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
