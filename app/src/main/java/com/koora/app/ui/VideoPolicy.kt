package com.koora.app.ui

/*
 * Video navigation policy (v1.5.4) — the "Koora player" promise.
 *
 * The highlights screen loads EXACTLY ONE page: the official YouTube
 * embed (https://www.youtube.com/embed/<id>). Every other main-frame
 * navigation is refused, so tapping the YouTube logo, the share button
 * or any redirect inside the player can never turn our screen into the
 * YouTube website. Media segments (googlevideo), thumbnails (ytimg) and
 * the player's own iframe subresources are NOT main-frame navigations —
 * they keep flowing, playback is unaffected.
 *
 * Kept as pure logic so VideoPolicyTest can enforce it: this is a
 * compliance promise (we embed, we never deep-link into YouTube's site)
 * as much as a UX choice. The small YouTube logo ON the player itself
 * stays — YouTube's terms require it, and that is what keeps embedding
 * legal.
 *
 * v1.10.1 — the v1.9.1 Chrome Custom Tab exception was REMOVED (owner
 * report: youtube.com inside a tab still IS YouTube — cookie walls,
 * sign-in prompts, and on devices without a Custom Tabs provider it
 * leaked to the real browser). The embed IS the player: a candidate
 * that refuses inline playback is hidden (GONE) and the next candidate
 * auto-plays; when none plays, the honest branded state. Koora never
 * opens YouTube's website, app or browser. This policy still governs
 * the embedded WebView exactly as before.
 */
object VideoPolicy {

    private val ALLOWED_PREFIXES = listOf(
        "https://www.youtube.com/embed/",
        "https://www.youtube-nocookie.com/embed/"
    )

    /** May this URL be loaded as the WebView's MAIN frame? */
    fun isAllowedMainframeUrl(url: String): Boolean {
        val u = url.trim()
        if (u.startsWith("about:") || u.startsWith("data:") || u.startsWith("blob:")) return true
        return ALLOWED_PREFIXES.any { u.startsWith(it) }
    }

    /** The one and only page this screen loads, with the in-app params. */
    fun embedUrl(videoId: String): String =
        "https://www.youtube.com/embed/$videoId?autoplay=1&rel=0&playsinline=1&iv_load_policy=3"
}
