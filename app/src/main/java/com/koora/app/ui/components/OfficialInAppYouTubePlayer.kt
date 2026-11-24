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
import androidx.compose.runtime.mutableIntStateOf
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
 * OFFICIAL IN-APP YOUTUBE PLAYER (v1.10.5) - honest app identity.
 *
 * YouTube's Required Minimum Functionality requires API Clients using the
 * embedded player to identify themselves via the HTTP Referer header, and
 * documents the format: "https://" + the app's store-registered app ID.
 * For a WebView-loaded bundled HTML page, the documented technique is
 * loadDataWithBaseURL: the baseUrl becomes the document origin and the
 * Referer. This build therefore identifies as https://com.koora.app -
 * Koora's applicationId - via three aligned signals:
 *   1. loadDataWithBaseURL base URL = https://com.koora.app
 *   2. iframe referrerpolicy="strict-origin-when-cross-origin" (sends the
 *      app identity cross-origin, matching the browser default YouTube
 *      recommends)
 *   3. the IFrame player's origin= parameter matches the same identity
 * The WebView user agent is left untouched: the "; wv" token and the
 * Android WebView Media Integrity attestation let YouTube verify the
 * embedding app honestly.
 *
 * REPLACED (v1.10.0-v1.10.4): the previous versions identified the embed
 * as YouTube itself - base URL and origin= set to youtube.com, a
 * shouldInterceptRequest proxy that re-sent the embed document with an
 * explicit "Referer: https://www.youtube.com/" header, and a user agent
 * with the WebView token stripped. Misidentifying the embedder contradicts
 * the Required Minimum Functionality page and is the credible code-level
 * cause of the error 153 / 152-4 rejections that family of builds kept
 * hitting. The same-origin error-card poller is removed with them: it
 * depended on the document and the embed iframe sharing the youtube.com
 * origin, which honest identification deliberately ends; IFrame onError
 * plus the stall/no-play guards below remain the failure detection.
 *
 * Policy kept intact (spec-compliant behavior):
 *  - the IFrame API script is the only script; playback stays 100% in-app;
 *  - main-frame navigation is refused for every host outside
 *    youtube.com / youtube-nocookie.com / ytimg.com / googlevideo.com
 *    (VideoPolicy, host-level form);
 *  - the JS bridge reports IFrame onError (100/101/150/153...) and the
 *    playback state to Compose: a player that never reports ready in 6s,
 *    a renderer crash (after one silent rebuild), or a player that never
 *    actually PLAYS within 12s hands the video to the caller (GONE);
 *  - mixed content is never allowed.
 */

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OfficialInAppYouTubePlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    onEnterFullscreen: ((View, WebChromeClient.CustomViewCallback) -> Unit)? = null,
    onExitFullscreen: (() -> Unit)? = null,
    // Carries the FAILED videoId so the caller can hide that candidate
    // and auto-play the next one.
    onEmbedBlockedOrFailed: (String) -> Unit
) {
    val context = LocalContext.current

    // v1.10.4: renderer-crash resilience — attempt 0 is the first build of
    // this candidate; attempt 1 is the ONE silent rebuild after a renderer
    // crash (same video, fresh WebView and fresh guards). A second crash
    // reports the candidate as failed. Remember/key scopes include `attempt`
    // so every rebuild starts with clean state.
    var attempt by remember(videoId) { mutableIntStateOf(0) }

    // v1.9.1 (spec §6.1): false until the IFrame player reports onReady.
    // A spinner shows meanwhile; if nothing reports within 6s (stalled
    // black box) the next candidate takes over.
    var isPlayerReady by remember(videoId, attempt) { mutableStateOf(false) }

    // v1.10.0: onReady alone is NOT proof of picture — the notorious
    // black-box-with-audio case reports ready and never actually plays.
    // PLAYING state (1) is the only honest "you are watching" signal.
    var sawPlaying by remember(videoId, attempt) { mutableStateOf(false) }

    // A refusal screen was detected (IFrame onError or one of the
    // guards). The WebView leaves the composition the moment this flips —
    // YouTube's error card and its "Watch video on YouTube" tap-out are
    // never left on screen.
    var embedErrored by remember(videoId, attempt) { mutableStateOf(false) }

    val latestOnBlocked by rememberUpdatedState(onEmbedBlockedOrFailed)

    val webView = remember(videoId, attempt) {
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

                    // Hide the WebView BEFORE reporting — the caller
                    // needs a moment to resolve the next candidate, and during
                    // that gap YouTube's error card (with its tap-out to
                    // YouTube's site) must not stay on screen.
                    @JavascriptInterface
                    fun onPlayerError(errorCode: Int) {
                        post {
                            embedErrored = true
                            onEmbedBlockedOrFailed(videoId)
                        }
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

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail
                ): Boolean {
                    // Absorb renderer kills (return true keeps Koora alive):
                    // they are usually transient memory pressure, so the
                    // FIRST crash silently rebuilds the same video; only a
                    // second crash gives up on the candidate.
                    view.post {
                        if (attempt == 0) {
                            attempt = 1
                        } else {
                            embedErrored = true
                            onEmbedBlockedOrFailed(videoId)
                        }
                    }
                    return true
                }
            }

            val safeId = videoId.replace(Regex("[^A-Za-z0-9_-]"), "")
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <!-- Browser-grade referrer policy for every request this
                         document tree makes — full URL same-origin, origin
                         cross-origin, never nothing. -->
                    <meta name="referrer" content="strict-origin-when-cross-origin">
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        html, body { width: 100%; height: 100%; background-color: #000000; overflow: hidden; }
                        #player { width: 100%; height: 100%; border: 0; }
                    </style>
                </head>
                <body>
                    <!-- The iframe is hand-written so its referrerpolicy
                         applies: cross-origin it sends the document origin,
                         https://com.koora.app — Koora's app identity per
                         YouTube's Required Minimum Functionality. The origin=
                         parameter matches the same identity. enablejsapi=1
                         lets the IFrame API attach below for
                         onReady/state/error. -->
                    <iframe
                        id="player"
                        src="https://www.youtube.com/embed/$safeId?autoplay=1&rel=0&playsinline=1&enablejsapi=1&origin=https%3A%2F%2Fcom.koora.app"
                        referrerpolicy="strict-origin-when-cross-origin"
                        allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
                        allowfullscreen
                        frameborder="0"></iframe>
                    <script src="https://www.youtube.com/iframe_api"></script>
                    <script>
                        var player;
                        // The IFrame API attaches to the hand-written iframe
                        // above (enablejsapi=1) and reports through the
                        // KooraPlayerBridge: onReady, playback state, and
                        // onError (100/101/150/153...). The 6s stall guard
                        // and 12s no-play guard on the Compose side remain
                        // the backstops for failures that fire no event.
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
                "https://com.koora.app",
                html,
                "text/html",
                "utf-8",
                null
            )
        }
    }

    // The black-box stall guard. If the IFrame player neither reports
    // ready nor errors within 6 seconds, hand THIS video to the next
    // candidate — playback continues, never a lingering black box. The
    // stalled WebView also hides the moment the guard fires — during the
    // candidate-swap gap nothing stale stays on screen.
    LaunchedEffect(videoId, attempt) {
        delay(6_000L)
        if (!isPlayerReady) {
            embedErrored = true
            latestOnBlocked(videoId)
        }
    }

    // v1.10.0: the no-playback guard. onReady alone is NOT proof of picture
    // — the notorious black-box-with-audio case reports ready and never
    // actually plays. PLAYING state (1) is the only honest "you are
    // watching" signal — if it is never seen within 12s, the next
    // candidate takes over. The user watches, always.
    LaunchedEffect(videoId, attempt) {
        delay(12_000L)
        if (!sawPlaying) {
            embedErrored = true
            latestOnBlocked(videoId)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    // key(videoId, attempt): AndroidView invokes its factory once per node
    // — without the key, picking another video in "More highlights" would
    // keep the old (already destroyed) WebView on screen. The key swaps in
    // a freshly built player for every candidate AND for the post-crash
    // rebuild. embedErrored removes the WebView from composition
    // the instant a refusal screen is detected — YouTube's error card and
    // its "Watch video on YouTube" tap-out are never left visible; the
    // spinner holds the frame until the next candidate is ready.
    key(videoId, attempt) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(androidx.compose.ui.graphics.Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (!embedErrored) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (!isPlayerReady) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
