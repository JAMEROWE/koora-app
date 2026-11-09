package com.koora.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.koora.app.R
import com.koora.app.ads.AdBanner

/*
 * IN-APP ARTICLE READER (v1.4.2) — tapping a headline no longer throws the
 * user out of Koora. A WebView keeps them INSIDE the app while they read,
 * which does two things:
 *   1. session time stays in the app (retention), and
 *   2. the always-on banner ad keeps earning during reading time —
 *      the whole monetization reason reading is in-app.
 *
 * Back first walks the page history, then closes the reader. If the page
 * fails to load, the user can still open it in the browser — reading must
 * never become a dead end. Ads must never crash the reader either, so the
 * banner comes from the crash-proof AdBanner wrapper.
 */
@Composable
fun ArticleReader(title: String, url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

    Column(Modifier.fillMaxSize()) {
        // Slim top bar: close | article title | reload
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.reader_close))
            }
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = { webView?.reload(); failed = false }) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.news_refresh))
            }
        }

        if (!failed && progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (failed) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Text(
                        stringResource(R.string.reader_error),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(stringResource(R.string.reader_open))
                    }
                }
            } else {
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
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            // Some article pages embed http images — render them
                            settings.mixedContentMode =
                                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): Boolean {
                                    val u = request.url
                                    return if (u.scheme == "http" || u.scheme == "https") {
                                        false // keep every web page inside the app
                                    } else {
                                        // mailto:, tel:, intent: ... hand to the system
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, u))
                                        }
                                        true
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError
                                ) {
                                    if (request.isForMainFrame) failed = true
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(v: WebView?, p: Int) {
                                    progress = p
                                }
                            }
                            loadUrl(url)
                            webView = this
                        }
                    }
                )
            }
        }

        // Ads while reading — in-app reading exists precisely for this surface
        AdBanner()
    }
}
