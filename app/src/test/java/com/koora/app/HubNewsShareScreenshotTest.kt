package com.koora.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.koora.app.data.FavoriteTeamsStore
import com.koora.app.data.MatchRepository
import com.koora.app.data.NewsItem
import com.koora.app.ui.screens.HighlightsHub
import com.koora.app.ui.screens.HubRow
import com.koora.app.ui.screens.MatchDetailScreen
import com.koora.app.ui.screens.NewsRow
import com.koora.app.ui.screens.YtEntry
import com.koora.app.ui.theme.KooraTheme
import com.koora.app.ui.theme.ThemeController
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RENDERED UI EVIDENCE — the highlights hub thumbnail card, the news
 * story cards, and the match detail share action. Synthetic fixtures
 * only. Headlessly the YouTube thumbnail area may show its loading
 * surface (no media fetch in tests); layout, text and badges are real.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HubNewsShareScreenshotTest.NoAdsApp::class)
class HubNewsShareScreenshotTest {

    companion object {
        init {
            MatchRepository.forceDemoForTest = true
            FavoriteTeamsStore.skipOnboardingForTest = true
        }
    }

    class NoAdsApp : android.app.Application()

    @get:Rule
    val rule = createComposeRule()

    private fun appString(resId: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(resId)

    @Test
    @Config(qualifiers = "w360dp-night")
    fun hubRows_renderThumbnailCards() {
        runBlocking { MatchRepository.matches("FL1") }
        val matches = MatchRepository.cache
        assert(matches.size >= 2) { "demo fixtures missing" }
        val rows = matches.take(2).mapIndexed { i, m ->
            HighlightsHub.Row(
                m,
                YtEntry(
                    title = "${m.homeTeam.name} vs ${m.awayTeam.name} highlights",
                    videoId = "testVideo$i",
                    published = "2026-11-23T20:00:00Z",
                    channel = if (i == 0) "beIN SPORTS" else ""
                )
            )
        }
        rule.setContent {
            KooraTheme(themeMode = ThemeController.DARK) {
                // Surface supplies the dark background + content color, as
                // the real screen's Scaffold does; a bare Column leaves
                // LocalContentColor black and the capture background light.
                Surface {
                    Column {
                        rows.forEach { HubRow(it) { _, _ -> } }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/hub_rows_360.png")
    }

    @Test
    @Config(qualifiers = "w360dp-night")
    fun newsRows_renderStoryCards() {
        val now = System.currentTimeMillis()
        val items = listOf(
            NewsItem(
                "Raja edge the Casablanca derby with a stoppage-time winner",
                "https://example.com/story-1", "BBC Sport", "Africa", now - 45 * 60_000L
            ),
            NewsItem(
                "Champions League playoff draw: every tie explained",
                "https://example.com/story-2", "ESPN", "Europe", now - 5 * 3_600_000L
            )
        )
        rule.setContent {
            KooraTheme(themeMode = ThemeController.DARK) {
                Surface {
                    Column {
                        items.forEach { NewsRow(it) {} }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/news_cards_360.png")
    }

    @Test
    @Config(qualifiers = "w360dp-night")
    fun matchDetail_rendersShareAction() {
        // Fill the repository cache with the demo list (no network).
        runBlocking { MatchRepository.matches("FL1") }
        val match = MatchRepository.cache.first { it.id == 9001L }
        rule.setContent {
            KooraTheme(themeMode = ThemeController.DARK) {
                MatchDetailScreen(
                    matchId = match.id,
                    onBack = {},
                    onWatchHighlights = { _, _ -> }
                )
            }
        }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithContentDescription(appString(R.string.share_match))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/match_detail_share_360.png")
    }
}
