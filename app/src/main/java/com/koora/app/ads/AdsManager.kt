package com.koora.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/*
 * Rewarded video ads — the main revenue source of the app.
 *
 * Uses Google's PUBLIC TEST ad unit until you create real AdMob units.
 * Test IDs never generate revenue and never cause policy problems
 * during development. Replace TEST_REWARDED_UNIT before release.
 */
object AdsManager {

    // Google's official test rewarded ad unit — replace with your own before release
    private const val TEST_REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917"

    private var rewarded: RewardedAd? = null

    fun load(context: Context) {
        RewardedAd.load(
            context,
            TEST_REWARDED_UNIT,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            rewarded = null
                            load(context) // preload the next one
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            rewarded = null
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewarded = null
                }
            }
        )
    }

    fun show(activity: Activity, onRewarded: () -> Unit) {
        val ad = rewarded
        if (ad == null) {
            // Ad not ready yet — in the skeleton we still reward the user
            // so the flow is testable end-to-end.
            onRewarded()
            return
        }
        ad.show(activity) { _ -> onRewarded() }
    }
}
