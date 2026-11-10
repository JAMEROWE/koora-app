package com.koora.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.koora.app.BuildConfig

/*
 * Ad strategy (v1.5 "moderate at peak moments", policy-safe placement v1.5.3):
 *  - Banners and native cards are always-on and passive (see AdViews.kt).
 *  - One interstitial, ONLY at natural breaks — opening a LIVE match
 *    detail, and LEAVING the video highlights screen (end of a content
 *    segment, never at its start — Google Play interstitial guidance).
 *    Capped hard so it never becomes noise:
 *      * never in the first app session,
 *      * never more than once per 4 minutes (the cap is SHARED).
 *  - No ad gates content. Rewarded gating was removed in v1.4.
 *
 * All unit IDs come from BuildConfig and default to Google's PUBLIC TEST
 * units — safe during development, swapped via local.properties / GitHub
 * secrets for release with zero code changes.
 */
object AdsManager {

    private const val PREFS = "koora_ads"
    private const val KEY_SESSIONS = "sessions"
    private const val KEY_LAST_INTERSTITIAL = "last_interstitial"
    private const val CAP_MS = 4 * 60 * 1000L

    private var interstitial: InterstitialAd? = null

    /** Preload the interstitial. Call again after each show/dismiss. */
    fun load(context: Context) {
        try {
            InterstitialAd.load(
            context,
            BuildConfig.AD_UNIT_INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitial = null
                            load(context)
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            interstitial = null
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                }
            }
        )
        } catch (_: Exception) {
            // Devices without Google Play services (some Huawei/Xiaomi builds):
            // no ads, never a crash.
            interstitial = null
        }
    }

    /** Count one app session — used to skip the interstitial in the first session. */
    fun bumpSession(context: Context) {
        prefs(context).edit().putInt(KEY_SESSIONS, prefs(context).getInt(KEY_SESSIONS, 0) + 1).apply()
    }

    /**
     * Show the capped interstitial if every condition passes. Never blocks
     * content. [onDone] runs when the ad is dismissed, fails to show, or is
     * skipped entirely (cap/first-session/not loaded) — so callers can chain
     * navigation after the ad moment.
     */
    fun maybeShowInterstitial(activity: Activity, onDone: (() -> Unit)? = null) {
        var shown = false
        try {
            val p = prefs(activity)
            val allowed = p.getInt(KEY_SESSIONS, 0) >= 2 &&          // not the first session
                System.currentTimeMillis() - p.getLong(KEY_LAST_INTERSTITIAL, 0L) >= CAP_MS
            val ad = if (allowed) interstitial else null
            if (ad != null) {
                p.edit().putLong(KEY_LAST_INTERSTITIAL, System.currentTimeMillis()).apply()
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        interstitial = null
                        load(activity)
                        onDone?.invoke()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        interstitial = null
                        onDone?.invoke()
                    }
                }
                shown = true
                ad.show(activity)
            }
        } catch (_: Exception) {
            // ads must never crash the app
        }
        if (!shown) onDone?.invoke()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
