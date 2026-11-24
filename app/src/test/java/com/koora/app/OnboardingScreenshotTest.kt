package com.koora.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.koora.app.data.FavoriteTeamsStore
import com.koora.app.data.MatchRepository
import com.koora.app.ui.screens.HomeScreen
import com.koora.app.ui.theme.KooraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RENDERED UI EVIDENCE â the first-launch favorites picker and its
 * effect on the home screen (favorite league pinned and opened on top).
 * Real HomeScreen composable with demo data. PNGs land in
 * app/build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = OnboardingScreenshotTest.NoAdsApp::class)
class OnboardingScreenshotTest {

    companion object {
        init {
            MatchRepository.forceDemoForTest = true
            // This class EXERCISES the picker â every test class sets the
            // flag explicitly because it is JVM-static across classes.
            FavoriteTeamsStore.skipOnboardingForTest = false
        }
    }

    class NoAdsApp : android.app.Application()

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "w360dp-night")
    fun onboarding_firstLaunch_rendersPicker() {
        // Fresh prefs: nothing stored, onboarding not done â the picker
        // must appear by itself once the first load lands.
        rule.setContent {
            KooraTheme {
                Surface { HomeScreen() }
            }
        }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("onboarding_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/onboarding_first_launch.png")
    }

    @Test
    @Config(qualifiers = "w360dp-night")
    fun favorites_chosen_leaguePinsOnTop() {
        // The user picked Ligue 1 (normally below Botola Pro in the
        // competition order) and finished onboarding: home must pin the
        // FL1 section on top. Prefs are written BEFORE first composition.
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        FavoriteTeamsStore.setAll(ctx, setOf("Paris Saint-Germain"), setOf("FL1"))
        FavoriteTeamsStore.markOnboardingDone(ctx)
        rule.setContent {
            KooraTheme {
                Surface { HomeScreen() }
            }
        }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithTag("home_feed").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/home_favorites_pinned.png")
    }
}
