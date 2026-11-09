package com.koora.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koora.app.data.MatchRepository
import com.koora.app.ui.screens.ArticleReader
import com.koora.app.ui.screens.HighlightsScreen
import com.koora.app.ui.screens.MatchDetailScreen
import com.koora.app.ui.theme.KooraTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * v1.5.0 SCREEN TESTS — boots the new surfaces headlessly (Robolectric):
 *  - the in-app article reader (news tap must stay INSIDE the app),
 *  - the in-app highlights player,
 *  - the match detail deep-stats cards (events / bars / lineups).
 * If any of these ever throws on entry, the build goes red here first.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = ScreensTest.NoAdsApp::class)
class ScreensTest {

    companion object {
        init {
            // Class-load time (before any coroutine): never touch the network.
            MatchRepository.forceDemoForTest = true
        }
    }

    class NoAdsApp : android.app.Application()

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun setUp() {
        MatchRepository.forceDemoForTest = true
    }

    @Test
    fun articleReaderRenders_inApp() {
        rule.setContent {
            KooraTheme {
                ArticleReader(
                    title = "Test headline from the feed",
                    url = "https://example.com/article",
                    onClose = {}
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Test headline from the feed").assertExists()
    }

    @Test
    fun highlightsScreenRenders_inApp() {
        rule.setContent {
            KooraTheme {
                HighlightsScreen(home = "Raja", away = "Wydad", onBack = {})
            }
        }
        rule.waitForIdle()
        // Top bar subtitle shows the fixture
        rule.onNodeWithText("Raja vs Wydad").assertExists()
    }

    @Test
    fun matchDetailShowsDeepStats_demo() {
        // Fill the repository cache with the demo list (no network)
        runBlocking { MatchRepository.matches("FL1") }
        val match = MatchRepository.cache.first { it.id == 9001L }

        rule.setContent {
            KooraTheme {
                MatchDetailScreen(
                    matchId = match.id,
                    onBack = {},
                    onWatchHighlights = { _, _ -> }
                )
            }
        }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(activityString(R.string.stats_events))
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Demo events timeline + lineups + bars all rendered.
        // (A player can appear in BOTH the events timeline and the lineup
        // list, so assert presence via onAllNodesWithText, not uniqueness.)
        rule.onAllNodesWithText("A. Sbai")[0].assertExists()
        rule.onAllNodesWithText("4-3-3")[0].assertExists()
        rule.onAllNodesWithText("54%")[0].assertExists()
    }

    private fun activityString(resId: Int): String {
        val ctx = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.app.Application>()
        return ctx.getString(resId)
    }
}
