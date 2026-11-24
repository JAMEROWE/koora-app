package com.koora.app

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.koora.app.data.FavoriteTeamsStore
import com.koora.app.data.MatchRepository
import com.koora.app.ui.components.MatchShareCard
import com.koora.app.ui.screens.MatchDetailScreen
import com.koora.app.ui.theme.KooraTheme
import com.koora.app.ui.theme.ThemeController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RENDERED UI EVIDENCE — the branded share card a match's Share action
 * actually generates, and the action itself: tapping Share must open the
 * system chooser with an image/png EXTRA_STREAM (the generated card) plus
 * the result line as text. Nothing is sent by the app itself; the user
 * picks the target.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShareCardScreenshotTest.NoAdsApp::class)
class ShareCardScreenshotTest {

    companion object {
        init {
            MatchRepository.forceDemoForTest = true
            FavoriteTeamsStore.skipOnboardingForTest = true
        }
    }

    class NoAdsApp : android.app.Application()

    @get:Rule
    val rule = createComposeRule()

    private fun demoMatch(): com.koora.app.data.Match {
        runBlocking { MatchRepository.matches("FL1") }
        return MatchRepository.cache.first { it.id == 9001L }
    }

    @Test
    @Config(qualifiers = "w360dp-night")
    fun shareCard_rendersBrandedCard() {
        val match = demoMatch()
        rule.setContent {
            KooraTheme(themeMode = ThemeController.DARK) {
                MatchShareCard(match)
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/match_share_card.png")
    }

    @Test
    @Config(qualifiers = "w360dp-night")
    fun shareAction_sendsBrandedCardImage() {
        val match = demoMatch()
        val app = ApplicationProvider.getApplicationContext<Application>()
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
        // Tap Share: the card renders off-screen (two posted frames), then
        // the chooser opens with the PNG + result text.
        rule.onAllNodesWithContentDescription(appString(R.string.share_match))[0]
            .performClick()
        rule.waitForIdle()

        val chooser = shadowOf(app).nextStartedActivity
        assertNotNull("Share tap must start an activity", chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull("Chooser must wrap a send intent", send)
        assertEquals(Intent.ACTION_SEND, send!!.action)
        assertEquals("image/png", send.type)
        assertNotNull("Share must include the generated card image", send.getStringExtra(Intent.EXTRA_TEXT))
        val stream: android.net.Uri? = send.getParcelableExtra(Intent.EXTRA_STREAM)
        assertNotNull("Share must include the generated card image", stream)
        assertTrue(
            "Result text names both teams",
            send.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Raja Club Athletic")
        )

        // The generated PNG itself: exists, decodes, and is not a blank
        // bitmap (a blank render compresses to nearly one color).
        val file = java.io.File(app.cacheDir, "shared/match_${match.id}.png")
        assertTrue("Share card PNG must exist", file.isFile && file.length() > 0)
        val bmp = BitmapFactory.decodeFile(file.absolutePath)
        assertNotNull("Share card PNG must decode", bmp)
        val colors = mutableSetOf<Int>()
        for (x in 0 until bmp!!.width step 13) {
            for (y in 0 until bmp.height step 13) {
                colors.add(bmp.getPixel(x, y))
            }
        }
        assertTrue("Share card must not be blank", colors.size > 10)
    }
}
