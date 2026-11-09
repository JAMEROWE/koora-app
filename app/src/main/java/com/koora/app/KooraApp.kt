package com.koora.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.koora.app.ads.AdsManager

class KooraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) { }
        AdsManager.load(this)
    }
}
