package com.koora.app

import com.koora.app.ui.VideoPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.5.4 — the video navigation policy is a legal promise: inside the
 * highlights screen the ONLY main-frame page is the official YouTube
 * embed. Anything that would show YouTube's website (watch, search,
 * shorts, redirect exits, accounts) must be refused.
 */
class VideoPolicyTest {

    @Test
    fun officialEmbed_isAllowed() {
        assertTrue(
            VideoPolicy.isAllowedMainframeUrl(
                "https://www.youtube.com/embed/abc123?autoplay=1&rel=0&playsinline=1"
            )
        )
    }

    @Test
    fun nocookieEmbed_isAllowed() {
        assertTrue(
            VideoPolicy.isAllowedMainframeUrl("https://www.youtube-nocookie.com/embed/abc123")
        )
    }

    @Test
    fun internalFrames_areAllowed() {
        // The embed page and player internals create these frames.
        assertTrue(VideoPolicy.isAllowedMainframeUrl("about:blank"))
        assertTrue(VideoPolicy.isAllowedMainframeUrl("data:text/html,<b>x</b>"))
    }

    @Test
    fun youtubeWatchPage_isBlocked() {
        assertFalse(VideoPolicy.isAllowedMainframeUrl("https://www.youtube.com/watch?v=abc123"))
    }

    @Test
    fun youtubeSearchPage_isBlocked() {
        assertFalse(
            VideoPolicy.isAllowedMainframeUrl(
                "https://www.youtube.com/results?search_query=raja+vs+wydad"
            )
        )
    }

    @Test
    fun youtubeShorts_isBlocked() {
        assertFalse(VideoPolicy.isAllowedMainframeUrl("https://www.youtube.com/shorts/abc123"))
    }

    @Test
    fun redirectExits_areBlocked() {
        assertFalse(
            VideoPolicy.isAllowedMainframeUrl(
                "https://www.youtube.com/redirect?q=https://example.com"
            )
        )
    }

    @Test
    fun nonYoutubeSites_areBlocked() {
        assertFalse(VideoPolicy.isAllowedMainframeUrl("https://example.com/video"))
    }

    @Test
    fun embedUrl_carriesInAppParams() {
        val u = VideoPolicy.embedUrl("abc123")
        assertTrue(u.contains("https://www.youtube.com/embed/abc123"))
        assertTrue(u.contains("autoplay=1"))     // instant start, our own screen
        assertTrue(u.contains("rel=0"))          // related videos limited to same channel
        assertTrue(u.contains("playsinline=1"))  // no auto-fullscreen takeover
    }
}
