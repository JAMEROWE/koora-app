package com.koora.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.koora.app.data.MatchRepository
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * RENDERED UI EVIDENCE — Roborazzi screenshots of the REAL app (MainActivity,
 * demo data) at phone widths, large text and Arabic RTL. The PNGs land in
 * app/build/outputs/roborazzi and are uploaded as CI artifacts, so every
 * reviewer sees exactly what a phone shows — not a hand-drawn mockup.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = HomeScreenshotTest.NoAdsApp::class)
class HomeScreenshotTest {

    companion object {
        init {
            MatchRepository.forceDemoForTest = true
        }
    }

    class NoAdsApp : android.app.Application()

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        MatchRepository.forceDemoForTest = true
    }

    private fun awaitFeed() {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(rule.activity.getString(R.string.day_today))
                .fetchSemanticsNodes().size >= 2
        }
        rule.waitForIdle()
    }

    /** Today (default), 360dp wide, dark identity. */
    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun shot01_homeToday_360_dark() {
        awaitFeed()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/home_today_360_dark.png")
    }

    /** Same screen at 320dp: names must still wrap whole-word, no overlap. */
    @Test
    @Config(qualifiers = "w320dp-h640dp-night")
    fun shot02_homeToday_320_dark() {
        awaitFeed()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/home_today_320_dark.png")
    }

    /** Large text (font scale 1.3): rows must grow, not crop. */
    @Test
    @Config(qualifiers = "w360dp-h800dp-night", fontScale = 1.3f)
    fun shot03_homeToday_bigText() {
        awaitFeed()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/home_today_bigtext.png")
    }

    /** Arabic RTL: layout mirrors, content stays readable. */
    @Test
    @Config(qualifiers = "ar-w360dp-h800dp-night")
    fun shot04_homeToday_arabic_rtl() {
        awaitFeed()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/home_today_ar_rtl.png")
    }

    /** Owner's rule: Tomorrow selected shows ONLY tomorrow — no Today block. */
    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun shot05_tomorrowOnly_dark() {
        awaitFeed()
        rule.onNodeWithText(rule.activity.getString(R.string.day_tomorrow)).performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/home_tomorrow_only.png")
    }

    /** Team/league search over the loaded window. */
    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun shot06_search_dark() {
        awaitFeed()
        rule.onNodeWithTag("home_search_toggle").performClick()
        rule.onNodeWithTag("home_search_field").performTextInput("champions")
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/home_search.png")
    }
}
