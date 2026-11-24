package com.koora.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.getOrNull
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
 * Matches-first IA (owner-approved 24 Nov): the app opens on the MATCHES
 * home — Today by default, live matches inside Today, one day at a time,
 * favorites of the selected day pinned on top, team/league search in the
 * top bar. Demo mode stamps the sample matches on Today: Botola Pro
 * (Raja-Wydad, FAR-MCO finished), Ligue 1 (PSG-OM live, OL-Lille) and
 * Champions League (Real-City).
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
    fun appLaunches_homeShowsTodayByDefault() {
        rule.waitForIdle()

        // The bottom navigation rendered (activity + Scaffold + tabs alive)
        rule.onNodeWithText(rule.activity.getString(R.string.tab_standings)).assertExists()
        // Settings is the gear icon in the top-right, labeled by description.
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.tab_settings)).assertExists()
        // No "All days" chip anymore: exactly one day at a time.
        rule.onNodeWithText(rule.activity.getString(R.string.chip_all)).assertDoesNotExist()

        // Default = Today: the word appears TWICE once the feed composed —
        // the chip AND the list's day title.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(rule.activity.getString(R.string.day_today))
                .fetchSemanticsNodes().size >= 2
        }

        // Day-after chip narrows the feed to the day-after block, which
        // honestly reports empty in demo mode (never a fake fixture).
        rule.onNodeWithText(rule.activity.getString(R.string.day_after)).performClick()
        rule.onNodeWithTag("home_feed")
            .performScrollToNode(hasText(rule.activity.getString(R.string.empty_day_after)))
        try {
            rule.waitUntil(timeoutMillis = 10_000) {
                rule.onAllNodesWithText(rule.activity.getString(R.string.empty_day_after))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            // On timeout, dump every text node the screen holds so the CI
            // log shows what the day-after view ACTUALLY rendered.
            val dump = runCatching {
                val sb = StringBuilder("KOORA-DEBUG texts: ")
                rule.onAllNodesWithText("", substring = true)
                    .fetchSemanticsNodes()
                    .forEach { n ->
                        val t = n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
                            ?.joinToString("|")
                        if (!t.isNullOrBlank()) sb.append('[').append(t).append("] ")
                    }
                sb.toString()
            }.getOrDefault("KOORA-DEBUG: semantics dump failed")
            throw AssertionError(dump, e)
        }
    }

    @Test
    fun home_selectedDayShowsOnlyThatDay() {
        rule.waitForIdle()
        // Wait for the feed: Today chip + Today day title.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(rule.activity.getString(R.string.day_today))
                .fetchSemanticsNodes().size >= 2
        }

        // Owner's rule (24 Nov): picking Tomorrow must not render a Today
        // or other-day section — "Today" drops back to just its chip.
        rule.onNodeWithText(rule.activity.getString(R.string.day_tomorrow)).performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(rule.activity.getString(R.string.day_tomorrow))
                .fetchSemanticsNodes().size >= 2
        }
        rule.waitForIdle()
        val todayNodes = rule.onAllNodesWithText(rule.activity.getString(R.string.day_today))
            .fetchSemanticsNodes()
        assert(todayNodes.size == 1) {
            "Expected only the Today chip while Tomorrow is selected, found ${todayNodes.size} Today nodes"
        }
    }

    @Test
    fun home_searchFindsTeamAndLeague() {
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText(rule.activity.getString(R.string.day_today))
                .fetchSemanticsNodes().size >= 2
        }

        // Team search: "Raja" finds the demo Botola Pro fixture.
        rule.onNodeWithTag("home_search_toggle").performClick()
        rule.onNodeWithTag("home_search_field").performTextInput("Raja")
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Raja", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // League search: "champions" finds the Champions League fixture.
        rule.onNodeWithTag("home_search_toggle").performClick()
        rule.onNodeWithTag("home_search_toggle").performClick()
        rule.onNodeWithTag("home_search_field").performTextInput("champions")
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Real", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
