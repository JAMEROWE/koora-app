package com.koora.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.koora.app.ui.components.LeagueHeaderCard
import com.koora.app.ui.theme.KooraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Isolated fixtures for the league-header competition icon: England's flag
 * on platforms with subdivision-flag support, the readable "ENG" label on
 * older platforms, and the neutral trophy for international competitions.
 * These verify OUR rendering fallback only - no live provider coverage is
 * claimed (demo data carries no English league).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CompetitionIconScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "w360dp-night")
    fun englandFlag_supportedPlatform_rendersFlag() {
        rule.setContent {
            KooraTheme {
                Surface {
                    LeagueHeaderCard(
                        title = "Premier League",
                        count = 3,
                        live = false,
                        expanded = true,
                        onToggle = {},
                        code = "PL"
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/header_england_flag.png")
    }

    @Test
    @Config(sdk = [32], qualifiers = "w360dp-night")
    fun englandFlag_olderPlatform_rendersEngLabel() {
        rule.setContent {
            KooraTheme {
                Surface {
                    LeagueHeaderCard(
                        title = "Premier League",
                        count = 3,
                        live = false,
                        expanded = true,
                        onToggle = {},
                        code = "PL"
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/header_england_label.png")
    }

    @Test
    @Config(qualifiers = "w360dp-night")
    fun internationalCompetition_rendersTrophy() {
        rule.setContent {
            KooraTheme {
                Surface {
                    LeagueHeaderCard(
                        title = "Champions League",
                        count = 1,
                        live = false,
                        expanded = true,
                        onToggle = {},
                        code = "CL"
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/header_international_trophy.png")
    }
}
