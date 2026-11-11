package com.koora.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/*
 * OFFICIAL IN-APP YOUTUBE PLAYER (v1.9.0, master spec §7.2) — the Error
 * 153 fix, replacing the raw embed-URL WebView.
 *
 * WHY THIS EXISTS: loading https://www.youtube.com/embed/<id> directly in
 * a WebView sends NO Referer/Origin for the player's own XHR calls, and
 * YouTube answers with error 153 ("video cannot be played in this
 * player"). The fix is to bootstrap the OFFICIAL IFrame API from an HTML
 * document whose base URL IS https://www.youtube.com, and to pass
 * 'origin' in playerVars — exactly how YouTube's own docs prescribe
 * embedding outside a real browser tab.
 *
 * Policy baked in (spec §7.1):
 *  - the IFrame API script is the only script; playback stays 100% in-app;
 *  - main-frame navigation is refused for every host outside
 *    youtube.com / youtube-nocookie.com / ytimg.com / googlevideo.com,
 *    so logo / share / redirect taps can never turn the screen into
 *    YouTube's website (VideoPolicy's promise, host-level form);
 *  - the JS bridge reports IFrame onError (100/101/150/153...) to
 *    Compose: the caller hides the player (GONE) and moves on — never an
 *    error box, never an external app;
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
    onEmbedBlockedOrFailed: () -> Unit
) {
    val context = LocalContext.current

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
                    fun onPlayerError(errorCode: Int) {
                        post { onEmbedBlockedOrFailed() }
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
                        #player { width: 100%; height: 100%; }
                    </style>
                </head>
                <body>
                    <div id="player"></div>
                    <script src="https://www.youtube.com/iframe_api"></script>
                    <script>
                        var player;
                        function onYouTubeIframeAPIReady() {
                            player = new YT.Player('player', {
                                videoId: '$safeId',
                                host: 'https://www.youtube.com',
                                playerVars: {
                                    'autoplay': 1,
                                    'playsinline': 1,
                                    'rel': 0,
                                    'modestbranding': 1,
                                    'fs': 1,
                                    'origin': 'https://www.youtube.com'
                                },
                                events: {
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

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(androidx.compose.ui.graphics.Color.Black)
    )
}
