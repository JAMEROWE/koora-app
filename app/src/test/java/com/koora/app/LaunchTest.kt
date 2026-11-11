package com.koora.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
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
 * v1.6.0 IA: the app opens on LIVE (Today + Tomorrow league sections,
 * poll per match); MATCHES is the Results screen (finished matches).
 * Demo mode stamps the sample matches with competitions, so the Live
 * screen renders: Botola Pro (Raja-Wydad finished, FAR-MCO finished),
 * Ligue 1 (PSG-OM live, OL-Lille) and Champions League (Real-City).
 *
 * Robolectric quirk handled here: the framework focuses the first
 * focusable node (the first match row) when the window gains focus, and
 * the LazyColumn auto-scrolls to reveal it — on the small test screen
 * that hides the day titles. The test scrolls the LIST back up (swipe
 * down ON a row) before asserting anything above the fold. Real devices
 * do not auto-focus anything at launch, so the screen opens at the top.
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
    fun appLaunches_liveTabShowsLeagueSections() {
        rule.waitForIdle()

        // The bottom navigation rendered (activity + Scaffold + tabs all alive)
        rule.onNodeWithText(rule.activity.getString(R.string.tab_matches)).assertExists()
        // v1.9.0: Settings LEFT the bottom bar (master spec §1.1) — it is the
        // gear icon in the top-right now, labeled with its content description.
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.tab_settings)).assertExists()

        // The window loads in a coroutine — waiting is mandatory (CI can
        // reach "idle" while the skeleton is still showing). Botola Pro is
        // the FIRST section, so Raja is the top match row once loaded.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Raja Club Athletic").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Raja Club Athletic")[0].assertExists()

        // Scroll the LIST back to the top (swipe down on the row itself),
        // then the day title and the league header must be there. (The
        // Tomorrow block sits below the fold of Robolectric's 470px screen —
        // its logic is covered by MatchRulesTest and verified on-device.)
        rule.onAllNodesWithText("Raja Club Athletic")[0].performTouchInput { swipeDown() }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(rule.activity.getString(R.string.day_today))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText(rule.activity.getString(R.string.day_today))[0].assertExists()
        rule.onAllNodesWithText("Botola Pro")[0].assertExists()
    }

    @Test
    fun matchesTab_showsResultsUnderLeague() {
        rule.waitForIdle()

        rule.onNodeWithText(rule.activity.getString(R.string.tab_matches)).performClick()

        // Results screen: finished demo matches (Raja-Wydad, FAR-MCO) under
        // their Botola Pro section.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Raja Club Athletic").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Raja Club Athletic")[0].assertExists()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("AS FAR Rabat").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("AS FAR Rabat")[0].assertExists()
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
