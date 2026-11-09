package com.koora.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koora.app.data.MatchRepository
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * INTERNAL LAUNCH TEST — the sandbox "phone".
 *
 * Boots the real MainActivity through the real Android framework
 * (Robolectric — no device, no emulator needed) and fails the build if
 * startup ever crashes.
 *
 * Regression locked here: v1.4.0 crashed instantly on launch because
 * AppCompatActivity was given a framework Material theme. Any future
 * change that reintroduces that bug (or any other startup crash) turns
 * this test red before the APK ever ships.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = LaunchTest.NoAdsApp::class)
class LaunchTest {

    /** Application stub: no AdMob init (no Google Play services on the JVM). */
    class NoAdsApp : android.app.Application()

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        // Deterministic run: demo data, zero network.
        MatchRepository.forceDemoForTest = true
    }

    @Test
    fun appLaunches_andMatchesTabRenders() {
        rule.waitForIdle()

        // The bottom navigation rendered (activity + Scaffold + tabs all alive)
        rule.onNodeWithText(rule.activity.getString(R.string.tab_matches)).assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.tab_settings)).assertExists()

        // The match list actually composed with demo fixtures
        rule.onNodeWithText("Raja Club Athletic").assertExists()
        rule.onNodeWithText("Real Madrid").assertExists()
    }

    @Test
    fun standingsTabRenders_table() {
        rule.waitForIdle()

        rule.onNodeWithText(rule.activity.getString(R.string.tab_standings)).performClick()
        rule.waitForIdle()

        // Demo standings table composed end-to-end
        rule.onNodeWithText("Paris Saint-Germain").assertExists()
    }
}
