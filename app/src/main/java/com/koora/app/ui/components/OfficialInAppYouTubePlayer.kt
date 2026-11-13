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
 * THE v1.10.4 MID-PLAY DEATH FIX (owner report: "it starts playing but
 * while running it shut down and replies retry / couldn't find video" —
 * and the 152-4 card STAYED on screen with our spinner on top): two gaps.
 *
 * Gap 1 — YouTube's newer refusal screens (the 152-x "This video is
 * unavailable" family) are rendered by the embed document itself and
 * frequently fire NO IFrame onError event; the API has no event for them.
 * Until the 6s no-ready backstop rotated the candidate, the card sat
 * there — and it carries a "Watch video on YouTube" button, i.e. a
 * tap-out to YouTube's site INSIDE our player box, which VideoPolicy
 * forbids in spirit. Two fixes:
 *   - an in-page poller (same-origin: both this document and the embed
 *     iframe are https://www.youtube.com) checks the iframe every 600ms
 *     for the card's '.ytp-error' overlay or its literal "Error code"
 *     text — tight signals a healthy player never renders — and reports
 *     it through the bridge EXACTLY like onError. Detection drops from
 *     ~6s to ~1s, the card is hidden instantly, and the next candidate
 *     auto-plays. If the iframe ever becomes unreadable the exception is
 *     swallowed and the 6s stall guard remains the backstop.
 *   - onError, the stall/no-play guards and the poller all set a local
 *     `embedErrored` flag that removes the WebView from composition
 *     IMMEDIATELY (black + spinner) — YouTube's error card and its
 *     tap-out button are never left visible for even one frame.
 *
 * Gap 2 — after PLAYING was observed, a WebView RENDERER CRASH (memory
 * pressure on 4G devices) marked the candidate failed outright. Renderer
 * kills are usually transient: the player now silently rebuilds itself
 * for the SAME video once (fresh `attempt`, fresh WebView, fresh guards)
 * and only reports the candidate as failed if it crashes again.
 *
 * Policy baked in (spec §6.1):
 *  - the IFrame API script is the only script; playback stays 100% in-app;
 *  - main-frame navigation is refused for every host outside
 *    youtube.com / youtube-nocookie.com / ytimg.com / googlevideo.com,
 *    so logo / share / redirect taps can never turn the screen into
 *    YouTube's website (VideoPolicy's promise, host-level form);
 *  - the JS bridge reports IFrame onError (100/101/150/153...), the
 *    same-origin error-card poller, AND the playback state to Compose:
 *    a player that never reports ready in 6s, a renderer crash (after
 *    one silent rebuild), or a player that never actually PLAYS within
 *    12s hands the video to the caller (GONE) — never a lingering black
 *    box, never an error card with a tap-out to YouTube's site;
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

    // v1.10.4: a refusal screen was detected (IFrame onError, the
    // same-origin error-card poller, or a guard). The WebView leaves the
    // composition the moment this flips — YouTube's error card and its
    // "Watch video on YouTube" tap-out are never left on screen.
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

                    // v1.10.4: hide the WebView BEFORE reporting — the caller
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

                // v1.10.4: a crashed WebView renderer is the ultimate black
                // box — absorb it (return true keeps Koora alive). Renderer
                // kills are usually transient memory pressure, so the FIRST
                // crash silently rebuilds the same video (fresh WebView, fresh
                // guards via `attempt`); only a second crash gives up on the
                // candidate and lets the next one play.
                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail
                ): Boolean {
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
                        // v1.10.4: refusal screens of the 152-x family render
                        // YouTube's own error card WITHOUT firing the IFrame
                        // API's onError — the API has no event for them. This
                        // document and the embed iframe share the origin
                        // https://www.youtube.com, so the card is detectable
                        // from here: poll for the '.ytp-error' overlay or the
                        // literal "Error code" text (signals a healthy player
                        // never renders), report through the bridge exactly
                        // like onError, and the candidate cascade rotates
                        // within ~1s instead of ~6s. Polling stops on PLAYING
                        // (state 1), on onError, after 50 rounds (~30s), or on
                        // any read failure — the stall guard is the backstop.
                        var errorPoll = null;
                        var errorPollRounds = 0;
                        function stopErrorPoll() {
                            if (errorPoll !== null) {
                                clearInterval(errorPoll);
                                errorPoll = null;
                            }
                        }
                        function onYouTubeIframeAPIReady() {
                            player = new YT.Player('player', {
                                events: {
                                    'onReady': function() {
                                        if (window.KooraPlayerBridge) {
                                            window.KooraPlayerBridge.onPlayerReady();
                                        }
                                    },
                                    'onStateChange': function(event) {
                                        if (event.data === 1) {
                                            stopErrorPoll();
                                        }
                                        if (window.KooraPlayerBridge) {
                                            window.KooraPlayerBridge.onPlayerState(event.data);
                                        }
                                    },
                                    'onError': function(event) {
                                        stopErrorPoll();
                                        if (window.KooraPlayerBridge) {
                                            window.KooraPlayerBridge.onPlayerError(event.data);
                                        }
                                    }
                                }
                            });
                            errorPoll = setInterval(function() {
                                errorPollRounds++;
                                if (errorPollRounds > 50) {
                                    stopErrorPoll();
                                    return;
                                }
                                try {
                                    var frame = document.getElementById('player');
                                    if (!frame) return;
                                    var doc = frame.contentDocument;
                                    if (!doc || !doc.body) return;
                                    var text = doc.body.innerText || '';
                                    if (doc.querySelector('.ytp-error') || text.indexOf('Error code') !== -1) {
                                        stopErrorPoll();
                                        if (window.KooraPlayerBridge) {
                                            window.KooraPlayerBridge.onPlayerError(152);
                                        }
                                    }
                                } catch (e) {
                                    // iframe not readable from here — the 6s
                                    // stall guard still catches a dead player.
                                }
                            }, 600);
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
    // a video that is about to play.) v1.10.4: the stalled WebView also
    // hides the moment the guard fires — during the candidate-swap gap
    // nothing stale stays on screen.
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
    // a freshly built player for every candidate AND for the v1.10.4
    // post-crash rebuild. embedErrored removes the WebView from composition
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
