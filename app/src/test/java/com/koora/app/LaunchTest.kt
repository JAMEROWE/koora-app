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
 * v1.6.0 IA: the app opens on LIVE (Today + Tomorrow league sections,
 * poll per match); MATCHES is the Results screen (finished matches).
 * Demo mode stamps the sample matches with competitions, so the sections
 * render: Botola Pro (Raja-Wydad finished, FAR-MCO finished), Ligue 1
 * (PSG-OM live, OL-Lille) and Champions League (Real-City).
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
        rule.onNodeWithText(rule.activity.getString(R.string.tab_settings)).assertExists()

        // The window loads in a coroutine — waiting is mandatory (CI can
        // reach "idle" while the skeleton is still showing, which hides the
        // day titles). Botola Pro is the FIRST section, so Raja appears
        // near the top once loaded.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Raja Club Athletic").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Raja Club Athletic")[0].assertExists()

        // v1.6.0 Live layout: Today block first, Tomorrow block after it.
        // (Asserted AFTER the content load — the skeleton has no titles.)
        rule.onAllNodesWithText(rule.activity.getString(R.string.day_today))[0].assertExists()
        rule.onAllNodesWithText(rule.activity.getString(R.string.day_tomorrow))[0].assertExists()

        // Deeper sections sit below the fold on Robolectric's small screen —
        // swipe up, then wait for each row (PSG in Ligue 1, Real in CL).
        rule.onAllNodesWithText("Raja Club Athletic")[0].performTouchInput { swipeUp() }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Paris Saint-Germain").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Paris Saint-Germain")[0].assertExists()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Real Madrid").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Real Madrid")[0].assertExists()
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
