package com.koora.app.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

/*
 * IN-APP CHROME CUSTOM TAB FALLBACK (v1.9.1, master spec v5 §6.1) — the
 * "100% playback, zero black box" guarantee.
 *
 * WHEN IT IS USED: only when YouTube itself refuses the inline embed —
 * status.embeddable=false on the rights-holder's side, a region block,
 * or a player that stalls without reporting ready (the
 * OfficialInAppYouTubePlayer stall guard hands over here). Embedding is
 * the FIRST choice; this tab is the fallback, exactly as the master
 * spec prescribes.
 *
 * WHY IT STAYS BAN-SAFE AND COMPLIANT:
 *  - it renders YouTube's OWN web player (all branding, controls and
 *    ads intact — spec §6.5; nothing is downloaded or re-streamed);
 *  - it opens INSIDE Koora's own task window: a single X button returns
 *    straight to Koora, the external YouTube app is never involved;
 *  - the toolbar is Koora-themed (#0F172A dark slate) so the moment
 *    still feels in-app;
 *  - only the official watch URL, with a sanitized video id — never a
 *    search page, never a channel page.
 */
fun openHighlightInAppCustomTab(context: Context, videoId: String) {
    val safeId = videoId.replace(Regex("[^A-Za-z0-9_-]"), "")
    if (safeId.isBlank()) return
    val uri = Uri.parse("https://www.youtube.com/watch?v=$safeId")
    val colorScheme = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(Color.parseColor("#0F172A"))
        .setNavigationBarColor(Color.parseColor("#0F172A"))
        .build()

    val customTabsIntent = CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(colorScheme)
        .setShowTitle(true)
        .setUrlBarHidingEnabled(true)
        .build()

    // v1.10.0 hardening: a device without any Custom-Tabs-capable browser
    // must never dead-end (or crash) — the plain browser view of the SAME
    // official watch URL is the last resort.
    try {
        customTabsIntent.launchUrl(context, uri)
    } catch (_: Exception) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}
