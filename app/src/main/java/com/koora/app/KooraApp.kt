package com.koora.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.koora.app.ads.AdsManager

class KooraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ads must NEVER crash the app — on devices without Google Play
        // services every step below would throw. Each one fails silently
        // to a "no ads" state instead.
        runCatching { MobileAds.initialize(this) { } }
        runCatching { AdsManager.load(this) }          // preload interstitial
        runCatching { AdsManager.bumpSession(this) }   // first session -> no interstitial yet
    }
}
