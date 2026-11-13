package com.koora.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koora.app.ui.screens.clubChannelsFor
import com.koora.app.ui.screens.extractChannelVideos
import com.koora.app.ui.screens.titleMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * v1.10.3 — THE HIGHLIGHTS DISCOVERY REBUILD, locked by tests.
 *
 * Root cause of the owner's recurring "no highlights are published yet"
 * (reported five times across releases): YouTube DISCONTINUED the RSS
 * feeds endpoint — https://www.youtube.com/feeds/videos.xml returns HTTP
 * 404 for every channel — so the old tier silently produced ZERO videos
 * no matter what the downstream parser/matcher/player did. Discovery now
 * reads each official channel's own public /videos page and extracts the
 * video grid from its ytInitialData payload. These tests lock the pure
 * extraction and club-channel lookup logic against the exact shapes
 * observed live on 2026-11-13.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ChannelVideosTest {

    // Multi-line raw strings: JSON tolerates whitespace between tokens, so
    // every value stays on one line and no concatenation gymnastics are
    // needed — the shapes mirror the live lockupViewModel layout.
    private fun lockup(id: String, title: String) = """
        {"lockupViewModel":
            {"contentId":"$id","contentType":"VIDEO",
            "contentImage":{"thumbnailViewModel":{"image":{"sources":[
                {"url":"https://i.ytimg.com/vi/$id/hq720.jpg"}]}}},
            "metadata":{"lockupMetadataViewModel":
                {"title":{"content":"$title"}}}}}
        """

    private fun playlistLockup(listId: String, title: String) = """
        {"lockupViewModel":
            {"contentId":"$listId","contentType":"PLAYLIST",
            "metadata":{"lockupMetadataViewModel":
                {"title":{"content":"$title"}}}}}
        """

    private fun page(items: String) =
        """<html><script>var ytInitialData = {"contents":{"grid":{"items":[$items]}}};</script></html>"""

    @Test
    fun extractsIdsAndTitles_inPageOrder() {
        val html = page(
            lockup("AbCdEf12345", "TEAM A 2-1 TEAM B | HIGHLIGHTS") + "," +
                lockup("Zyx987Wvu12", "EXTENDED HIGHLIGHTS | Team C vs Team D")
        )
        val entries = extractChannelVideos(html)
        assertEquals(2, entries.size)
        assertEquals("AbCdEf12345", entries[0].videoId)
        assertEquals("TEAM A 2-1 TEAM B | HIGHLIGHTS", entries[0].title)
        assertEquals("Zyx987Wvu12", entries[1].videoId)
    }

    @Test
    fun skipsNonVideoLockups_andTitleless() {
        val html = page(
            playlistLockup("PLAAAAAAAAAAAAAAAAAA", "Season playlist") + "," +
                lockup("QpwoeiRuty1", "") + "," +
                lockup("QpwoeiRuty2", "Real video")
        )
        val entries = extractChannelVideos(html)
        assertEquals(1, entries.size)
        assertEquals("QpwoeiRuty2", entries[0].videoId)
    }

    @Test
    fun deduplicatesRepeatedVideoIds() {
        val html = page(
            lockup("AbCdEf12345", "First") + "," + lockup("AbCdEf12345", "Second")
        )
        val entries = extractChannelVideos(html)
        assertEquals(1, entries.size)
        assertEquals("First", entries[0].title)
    }

    @Test
    fun pageWithoutYtInitialData_yieldsNothing() {
        assertTrue(extractChannelVideos("<html><body>error 404</body></html>").isEmpty())
        assertTrue(extractChannelVideos("").isEmpty())
    }

    @Test
    fun titleMatches_stillWorksOnExtractedTitles() {
        // The Fiorentina-Juventus owner case: league-channel titles are
        // uppercase with dashes; provider names carry ACF/FC prefixes.
        val html = page(lockup("15hGtRJ3aFk", "FIORENTINA-JUVENTUS 2-2 | EXTENDED HIGHLIGHTS | SERIE A 2026/27"))
        val entries = extractChannelVideos(html)
        val e = entries.single()
        assertTrue(titleMatches(e.title, "ACF Fiorentina"))
        assertTrue(titleMatches(e.title, "Juventus FC"))
    }

    // ---- club channel lookup ------------------------------------------------

    @Test
    fun clubLookup_fiorentinaAndJuventus() {
        val home = clubChannelsFor("ACF Fiorentina")
        assertEquals(1, home.size)
        assertEquals("UC9nwpWq73kl4e2a51d1aCog", home[0].channelId)
        val away = clubChannelsFor("Juventus FC")
        assertEquals(1, away.size)
        assertEquals("UCLzKhsxrExAC6yAdtZ-BOWw", away[0].channelId)
    }

    @Test
    fun clubLookup_manchesterDisambiguation() {
        val city = clubChannelsFor("Manchester City FC")
        val united = clubChannelsFor("Manchester United FC")
        assertEquals(listOf("UCkzCjdRMrW2vXLx8mvPVLdQ"), city.map { it.channelId })
        assertEquals(listOf("UC6yW44UGJJBvYTlfC7CRg2Q"), united.map { it.channelId })
    }

    @Test
    fun clubLookup_realMadrid_notBetis() {
        val madrid = clubChannelsFor("Real Madrid CF")
        assertEquals(1, madrid.size)
        assertFalse(madrid[0].tokens.contains("betis"))
        // "Real Betis Balompié" must NOT select Real Madrid's channel.
        val betis = clubChannelsFor("Real Betis Balompié")
        assertTrue(betis.none { it.channelId == "UCWV3obpZVGgJ3j9FVhEjF2Q" })
    }

    @Test
    fun clubLookup_stripsDiacritics() {
        val atleti = clubChannelsFor("Atlético Madrid")
        assertEquals(1, atleti.size)
        assertEquals("UCuzKFwdh7z2GHcIOX_tXgxA", atleti[0].channelId)
    }

    @Test
    fun clubLookup_unknownTeamYieldsNothing() {
        assertTrue(clubChannelsFor("Some Unlisted FC").isEmpty())
        assertTrue(clubChannelsFor("").isEmpty())
    }
}
