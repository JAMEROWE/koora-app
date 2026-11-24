package com.koora.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.koora.app.data.CompetitionRef
import com.koora.app.data.FavoriteTeamsStore
import com.koora.app.data.Match
import com.koora.app.data.MatchRepository
import com.koora.app.data.Score
import com.koora.app.data.ScoreDetail
import com.koora.app.data.Team
import com.koora.app.ui.screens.HomeScreen
import com.koora.app.ui.theme.KooraTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * RENDERED UI EVIDENCE — the REAL-data rendering path (window override,
 * no demo flag), shaped like the mix on the owner's phone: CL (9),
 * MLS (1), Scottish Premiership (2), Championship (8), Brasileiro (1).
 * Regression guard for "expanded sections show no match rows" and for
 * the league-flag coverage. Also renders the new Results mode.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = RealDataScreenshotTest.NoAdsApp::class)
class RealDataScreenshotTest {

    companion object {
        init {
            MatchRepository.forceDemoForTest = false
            FavoriteTeamsStore.skipOnboardingForTest = true
        }

        private val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private fun iso(daysAgo: Int, hourUtc: Int): String {
            val now = System.currentTimeMillis()
            val dayStart = now - (now % 86_400_000L)
            return utc.format(java.util.Date(dayStart - daysAgo * 86_400_000L + hourUtc * 3_600_000L))
        }

        private fun m(id: Long, home: String, away: String, code: String, comp: String,
                      status: String, whenIso: String, hg: Int? = null, ag: Int? = null) = Match(
            id = id,
            utcDate = whenIso,
            status = status,
            homeTeam = Team(id = id * 10, name = home, shortName = null, tla = null),
            awayTeam = Team(id = id * 10 + 1, name = away, shortName = null, tla = null),
            score = Score(fullTime = if (hg != null) ScoreDetail(hg, ag) else null),
            competition = CompetitionRef(0, code, comp)
        )

        fun phoneMix(): List<Match> {
            val out = mutableListOf<Match>()
            var id = 1L
            repeat(9) { i ->
                out += m(id++, "Real Madrid", "Manchester City", "CL", "UEFA Champions League",
                    "TIMED", iso(0, 20))
            }
            out += m(id++, "Inter Miami CF", "LA Galaxy", "MLS", "MLS", "TIMED", iso(0, 23))
            out += m(id++, "Celtic", "Rangers", "SCO", "Premiership", "TIMED", iso(0, 19))
            out += m(id++, "Heart of Midlothian", "Hibernian", "SCO", "Premiership", "TIMED", iso(0, 19))
            repeat(8) {
                out += m(id++, "Leeds United", "Norwich City", "ELC", "Championship",
                    "TIMED", iso(0, 18))
            }
            out += m(id++, "Flamengo", "Palmeiras", "BSA", "Campeonato Brasileiro Série A",
                "TIMED", iso(0, 21))
            // yesterday's finished matches for Results mode
            out += m(id++, "Arsenal", "Chelsea", "CL", "UEFA Champions League",
                "FINISHED", iso(1, 20), 2, 1)
            out += m(id++, "Paris Saint-Germain", "AS Monaco", "FL1", "Ligue 1",
                "FINISHED", iso(1, 19), 3, 0)
            return out
        }
    }

    class NoAdsApp : android.app.Application()

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        MatchRepository.windowOverrideForTest = null
        MatchRepository.forceDemoForTest = true
    }

    private fun showHome() {
        MatchRepository.windowOverrideForTest = phoneMix()
        rule.setContent {
            KooraTheme {
                Surface { HomeScreen() }
            }
        }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("home_feed").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w360dp-night")
    fun realData_today_sectionsShowRows() {
        showHome()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/realdata_today_360.png")
    }

    @Test
    @Config(qualifiers = "w360dp-night")
    fun realData_resultsMode_listsFinishedMatches() {
        showHome()
        rule.onNodeWithTag("results_chip").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/realdata_results_360.png")
    }
}
