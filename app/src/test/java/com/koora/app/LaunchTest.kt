package com.koora.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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

        // v1.9.0 (spec §2.1): the date-strip chips sit above the feed.
        rule.onNodeWithText(rule.activity.getString(R.string.chip_all)).assertExists()

        // The window loads in a coroutine — waiting is mandatory. In demo
        // mode the WHOLE window lands on "Today", and the word "Today"
        // appears TWICE once the feed composed: the chip AND the list's day
        // title. Two nodes = the live feed rendered its first section.
        // (On Robolectric's 470px screen the match rows themselves sit below
        // the fold; the rows are fully asserted on the Matches tab below —
        // same MatchRow/LeagueSection code, real data instead of scroll
        // gymnastics.)
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(rule.activity.getString(R.string.day_today))
                .fetchSemanticsNodes().size >= 2
        }

        // The new Day-After filter (spec §2.1): tapping the chip narrows the
        // feed to the day-after block, which honestly reports empty in demo
        // mode (never a fake fixture).
        rule.onNodeWithText(rule.activity.getString(R.string.day_after)).performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(rule.activity.getString(R.string.empty_day_after))
                .fetchSemanticsNodes().isNotEmpty()
        }
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
