package com.koora.app.ads

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.koora.app.BuildConfig
import androidx.compose.ui.graphics.toArgb

/*
 * Compose wrappers for the always-on, passive ad surfaces:
 *  - AdBanner: classic 50dp banner (Standings + News bottoms).
 *  - NativeAdCard: a single native ad card placed between match rows
 *    (FotMob-style in-feed placement, ~2x banner CPM).
 *  Both hide themselves when there is no fill — never an empty gray box.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        factory = { ctx ->
            runCatching {
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    setAdUnitId(BuildConfig.AD_UNIT_BANNER)
                    loadAd(AdRequest.Builder().build())
                }
            }.getOrElse {
                // Ads must never crash the app (no Play services, test JVM, ...)
                android.view.View(ctx).apply { visibility = android.view.View.GONE }
            }
        }
    )
}

@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    var adFilled by remember { mutableStateOf(false) }
    var heldAd by remember { mutableStateOf<NativeAd?>(null) }

    // Theme-aware colors resolved in Compose, applied to the Android views
    val headlineColor = MaterialTheme.colorScheme.onSurface
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            runCatching<android.view.View> {
            val root = NativeAdView(ctx)
            val box = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 32, 40, 32)
            }

            val badge = TextView(ctx).apply {
                text = "Ad"
                textSize = 11f
                setTextColor(metaColor.toArgb())
                setPadding(0, 0, 0, 10)
            }
            val headline = TextView(ctx).apply {
                textSize = 15f
                setTextColor(headlineColor.toArgb())
                paint.isFakeBoldText = true
                maxLines = 2
            }
            val body = TextView(ctx).apply {
                textSize = 13f
                setTextColor(metaColor.toArgb())
                maxLines = 2
                setPadding(0, 8, 0, 0)
            }
            val cta = TextView(ctx).apply {
                textSize = 14f
                setTextColor(accentColor.toArgb())
                paint.isFakeBoldText = true
                setPadding(0, 12, 0, 0)
            }

            box.addView(badge)
            box.addView(headline)
            box.addView(body)
            box.addView(cta)
            root.addView(box)

            root.headlineView = headline
            root.bodyView = body
            root.callToActionView = cta

            val loader = AdLoader.Builder(ctx, BuildConfig.AD_UNIT_NATIVE)
                .forNativeAd { ad ->
                    heldAd = ad
                    headline.text = ad.headline ?: ""
                    body.text = ad.body ?: ""
                    body.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
                    cta.text = ad.callToAction ?: ""
                    cta.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
                    root.setNativeAd(ad)
                    adFilled = true
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        // No fill -> collapse the slot entirely (no-blank rule)
                        adFilled = false
                        root.visibility = View.GONE
                    }
                })
                .withNativeAdOptions(NativeAdOptions.Builder().build())
                .build()
            loader.loadAd(AdRequest.Builder().build())
            root
            }.getOrElse {
                // Ads must never crash the app (no Play services, test JVM, ...)
                android.view.View(ctx).apply { visibility = android.view.View.GONE }
            }
        },
        update = { view ->
            view.visibility = if (adFilled) View.VISIBLE else View.GONE
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            heldAd?.destroy()
            heldAd = null
        }
    }
}
