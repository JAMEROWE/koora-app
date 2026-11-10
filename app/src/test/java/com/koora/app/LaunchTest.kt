package com.koora.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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

    companion object {
        init {
            // Runs at CLASS LOAD — before any JUnit rule, @Before, or activity
            // launch. Critical: viewModelScope uses Dispatchers.Main.immediate,
            // so the first data load starts synchronously inside onCreate —
            // later @Before would be TOO LATE on CI (which has a real API
            // token baked in) and the test would hit the live API.
            MatchRepository.forceDemoForTest = true
        }
    }

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

        // v1.5.6: the Today | Tomorrow day switch rendered. "Today" also
        // titles the mixed block, so assert via ALL nodes, not uniqueness.
        rule.onAllNodesWithText(rule.activity.getString(R.string.day_today))[0].assertExists()
        rule.onAllNodesWithText(rule.activity.getString(R.string.day_tomorrow))[0].assertExists()

        // The match list loads in a coroutine — waiting is mandatory here:
        // CI machines are fast and can reach "idle" while the list is still
        // Loading, which made this test flaky (run 42600042530).
        // Demo mode shows the whole sample list regardless of the wall
        // clock; the LIVE match sorts to the top of the mixed day block.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Paris Saint-Germain").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Paris Saint-Germain")[0].assertExists()

        // Deeper rows of the mixed list can sit below the fold on
        // Robolectric's small screen — swipe up, then wait for each row.
        rule.onAllNodesWithText("Paris Saint-Germain")[0].performTouchInput { swipeUp() }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Raja Club Athletic").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Raja Club Athletic")[0].assertExists()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Real Madrid").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Real Madrid")[0].assertExists()
    }

    @Test
    fun standingsTabRenders_table() {
        rule.waitForIdle()

        rule.onNodeWithText(rule.activity.getString(R.string.tab_standings)).performClick()

        // Same anti-flake rule: wait for the table coroutine, don't guess.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Paris Saint-Germain").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Paris Saint-Germain")[0].assertExists()
    }
}
