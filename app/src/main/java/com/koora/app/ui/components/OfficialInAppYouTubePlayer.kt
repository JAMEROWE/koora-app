package com.koora.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.HttpURLConnection
import java.net.URL
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
 * THE v1.10.2 ERROR-152-4 FIX (owner report: "This video is unavailable,
 * Error code: 152 - 4" on the referrer-fixed build): 152-4 is the SAME
 * identity family as 153 — YouTube could not verify the EMBEDDER because
 * the HTTP Referer it received was missing or not credible (2026 reports:
 * capacitor-youtube-player shipped exactly this fix as v8.2.13; the
 * ionic-team tracker calls 153 "embedder identity missing referrer"). A
 * declarative referrerpolicy turned out to be insufficient on WebViews
 * rooted at a data:-loaded document. So the identity is now repaired at
 * the HTTP level: shouldInterceptRequest re-issues the embed DOCUMENT
 * request itself with an explicit "Referer: https://www.youtube.com/"
 * header (plus the WebView's own UA and cookies) and streams YouTube's
 * byte-identical response back. Everything else — player JS, media
 * segments, API XHR — flows untouched. This is still the official embed
 * player served by YouTube's own endpoint; no stream is fetched, scraped
 * or cached, so the embed-only promise (spec §6.1) is intact.
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

                // v1.10.2 — the error-152-4 identity repair (see the file
                // header). ONLY the embed document is proxied; every other
                // request passes through untouched. Any failure here is
                // fail-open (null = the WebView loads the URL normally),
                // so the repair can never make things worse.
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val url = request.url
                    if (url.host != "www.youtube.com") return null
                    if (url.path?.startsWith("/embed/") != true) return null
                    return runCatching {
                        proxiedEmbedDocument(url.toString(), view.settings.userAgentString)
                    }.getOrNull()
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
                    <!-- v1.10.2: browser-grade referrer policy for every request
                         this document tree makes — full URL same-origin, origin
                         cross-origin, never nothing. -->
                    <meta name="referrer" content="strict-origin-when-cross-origin">
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        html, body { width: 100%; height: 100%; background-color: #000000; overflow: hidden; }
                        #player { width: 100%; height: 100%; border: 0; }
                    </style>
                </head>
                <body>
                    <!-- v1.10.0 Error-153 fix + v1.10.2 Error-152-4 upgrade:
                         the iframe is hand-written; its referrerpolicy now
                         matches a real browser (full URL same-origin, origin
                         cross-origin) so the embedder identity YouTube sees
                         is complete — and the HTTP-level Referer repair in
                         shouldInterceptRequest covers WebViews that strip
                         referrers for data-loaded documents entirely.
                         enablejsapi=1 lets the IFrame API attach below for
                         onReady/state/error. -->
                    <iframe
                        id="player"
                        src="https://www.youtube.com/embed/$safeId?autoplay=1&rel=0&playsinline=1&enablejsapi=1&origin=https%3A%2F%2Fwww.youtube.com"
                        referrerpolicy="strict-origin-when-cross-origin"
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
    // player neither reports ready nor errors within 6 seconds, hand THIS
    // video to the next candidate — playback continues, never a lingering
    // black box. (6s: v1.10.2 raises it from 4s because the embed document
    // now takes the intercept+proxy hop, and a slow network must not skip
    // a video that is about to play.)
    LaunchedEffect(videoId) {
        delay(6_000L)
        if (!isPlayerReady) latestOnBlocked(videoId)
    }

    // v1.10.0: the no-playback guard. onReady alone is NOT proof of picture
    // — the notorious black-box-with-audio case reports ready and never
    // actually plays. PLAYING state (1) is the only honest "you are
    // watching" signal — if it is never seen within 12s, the next
    // candidate takes over. The user watches, always.
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

/**
 * v1.10.2 — the error-152-4 embedder-identity repair (pure HTTP mechanics,
 * no playback logic). Re-issues the embed document request with an explicit
 * "Referer: https://www.youtube.com/" — the identity YouTube could not
 * verify on data:-rooted WebViews — plus the WebView's own UA and cookies,
 * and streams YouTube's byte-identical response back. Redirects are
 * followed manually so the identity headers survive every hop. Runs on a
 * WebView IO thread; ANY failure returns null (fail-open: the WebView
 * simply loads the URL itself, exactly like before the repair existed).
 * This serves YouTube's own embed player unchanged — nothing is scraped,
 * cached or faked (VideoPolicy's embed-only promise holds).
 */
private fun proxiedEmbedDocument(target: String, ua: String): WebResourceResponse? {
    var url = target
    repeat(5) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("Referer", "https://www.youtube.com/")
            setRequestProperty("User-Agent", ua)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            // identity: no gzip on this hop, so the body can be handed to
            // the WebView without any decompression layer.
            setRequestProperty("Accept-Encoding", "identity")
            CookieManager.getInstance().getCookie(url)?.let { setRequestProperty("Cookie", it) }
        }
        val code = conn.responseCode
        when {
            code in 300..399 -> {
                val location = conn.getHeaderField("Location")
                runCatching { conn.inputStream.close() }
                conn.disconnect()
                if (location.isNullOrBlank()) return null
                url = URL(URL(url), location).toString()
            }
            code in 200..299 -> {
                val fullType = conn.contentType ?: "text/html"
                val mime = fullType.substringBefore(';').trim().ifBlank { "text/html" }
                val charset = fullType.substringAfter("charset=", "")
                    .substringBefore(';').trim().takeIf { it.isNotBlank() }
                val headers = conn.headerFields
                    .filterKeys { key ->
                        key != null && !key.equals("Content-Encoding", true) &&
                            !key.equals("Transfer-Encoding", true) &&
                            !key.equals("Content-Length", true)
                    }
                    .mapKeys { it.key ?: "" }
                    .mapValues { (_, v) -> v.joinToString(", ") }
                val body = conn.inputStream
                return WebResourceResponse(
                    mime, charset, code,
                    conn.responseMessage ?: "OK", headers, body
                )
            }
            else -> {
                runCatching { conn.errorStream?.close() }
                conn.disconnect()
                return null
            }
        }
    }
    return null
}
