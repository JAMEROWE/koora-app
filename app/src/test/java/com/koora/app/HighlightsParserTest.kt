package com.koora.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koora.app.ui.screens.parseChannelFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * v1.5.6 — THE HIGHLIGHTS FIX, locked by a saved real-shaped feed.
 *
 * Root cause of "highlights never resolve": the feed parser compared tag
 * names to "videoid" but YouTube's Atom feed writes <yt:videoId>; with
 * Android's default namespace handling the parser sees the prefixed name
 * and the comparison never matched. The fix strips the prefix; this test
 * proves the parser reads IDs, titles and published dates from the exact
 * saved shape of a real channel feed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HighlightsParserTest {

    private fun feed(): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("yt_feed_sample.xml")
        return stream!!.readBytes().decodeToString()
    }

    @Test
    fun parsesYtVideoId_withNamespacePrefix() {
        val entries = parseChannelFeed(feed())
        assertEquals(3, entries.size) // the id-less live placeholder is skipped
        assertEquals("AbCdEf12345", entries[0].videoId)
        assertEquals("Zyx987Wvu", entries[1].videoId)
        assertEquals("QpwoeiRuty1", entries[2].videoId)
    }

    @Test
    fun titlesAreNotDuplicated_byMediaTitle() {
        val entries = parseChannelFeed(feed())
        // <title> AND <media:title> carry the same text — must appear once.
        assertEquals("Match Highlights | Team A 2-1 Team B", entries[0].title)
        assertEquals("Goal of the month | November", entries[2].title)
    }

    @Test
    fun publishedDatesParsed() {
        val entries = parseChannelFeed(feed())
        assertTrue(entries[0].published.startsWith("2026-11-09"))
        assertTrue(entries[1].published.startsWith("2026-11-08"))
    }

    @Test
    fun emptyFeedYieldsNoEntries() {
        assertTrue(parseChannelFeed("<feed xmlns=\"http://www.w3.org/2005/Atom\"></feed>").isEmpty())
    }
}
