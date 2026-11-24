package com.koora.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.koora.app.ui.screens.ArticleReader
import com.koora.app.ui.screens.HighlightsHubScreen
import com.koora.app.ui.screens.HighlightsScreen
import com.koora.app.ui.screens.HomeScreen
import com.koora.app.ui.screens.MatchDetailScreen
import com.koora.app.ui.screens.NewsScreen
import com.koora.app.ui.screens.SettingsScreen
import com.koora.app.ui.screens.StandingsScreen
import com.koora.app.ui.theme.KooraTheme
import com.koora.app.ui.theme.ThemeController

/*
 * AppCompatActivity (not ComponentActivity) so that the per-app language
 * switcher (AppCompatDelegate.setApplicationLocales) works on Android 12
 * and below too — the EN / FR / AR promise.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Theme preference (system / dark / light) — read before setContent
        ThemeController.init(applicationContext)
        // GDPR consent (Google UMP) — required before showing ads to EU users (France)
        requestGdprConsent()
        setContent {
            KooraTheme(themeMode = ThemeController.mode) {
                KooraRoot()
            }
        }
    }

    private fun requestGdprConsent() {
        try {
            val info = UserMessagingPlatform.getConsentInformation(this)
            val alreadyObtained =
                info.consentStatus == ConsentInformation.ConsentStatus.OBTAINED
            if (alreadyObtained && !info.isConsentFormAvailable) return
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { _ ->
                // form errors are ignored — consent must never crash the app
            }
        } catch (_: Exception) {
        }
    }
}

private data class TabItem(val route: String, val icon: ImageVector, val labelRes: Int)

@Composable
private fun KooraRoot() {
    val nav = rememberNavController()
    // v1.9.0 (master spec §1): Settings LEFT the bottom bar — it is now the
    // gear icon in every top-level screen's app bar. Five single-line tabs:
    // Matches-first home (owner-approved 24 Nov): ONE Matches tab merges the
    // old Live and Results tabs — Today carries the live matches, past days
    // stay reachable through the calendar. Four single-line tabs:
    val tabs = listOf(
        TabItem("matches", Icons.Filled.SportsSoccer, R.string.tab_matches),
        TabItem("standings", Icons.Filled.EmojiEvents, R.string.tab_standings),
        TabItem("highlights", Icons.Filled.OndemandVideo, R.string.tab_highlights),
        TabItem("news", Icons.Filled.Article, R.string.tab_news)
    )
    val openSettings = { nav.navigate("settings") }

    Scaffold(
        bottomBar = {
            // Fullscreen surfaces (article reader, video highlights) hide the
            // bottom bar so reading/watching gets the whole screen.
            val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
            val fullscreen = currentRoute != null &&
                (currentRoute.startsWith("article") || currentRoute.startsWith("video"))
            if (!fullscreen) {
                NavigationBar {
                    val backStack by nav.currentBackStackEntryAsState()
                    val currentRoute = backStack?.destination?.route
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = {
                                // Spec §1.2: single-line labels — "Standings"
                                // never wraps its last letter onto a second
                                // line on compact screens or in FR/AR.
                                Text(
                                    stringResource(tab.labelRes),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 10.sp
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "matches",
            modifier = Modifier.padding(padding)
        ) {
            composable("matches") {
                HomeScreen(
                    onMatchClick = { id -> nav.navigate("match/$id") },
                    onOpenSettings = openSettings
                )
            }
            composable("standings") { StandingsScreen(onOpenSettings = openSettings) }
            composable("highlights") {
                HighlightsHubScreen(
                    onWatch = { row ->
                        val m = row.match
                        nav.navigate(
                            "video?home=${Uri.encode(m.homeTeam.name)}&away=${Uri.encode(m.awayTeam.name)}" +
                                "&utc=${Uri.encode(m.utcDate)}" +
                                "&hg=${m.score.fullTime?.home ?: -1}&ag=${m.score.fullTime?.away ?: -1}"
                        )
                    },
                    onOpenSettings = openSettings
                )
            }
            composable("news") { NewsScreen(
                onArticleClick = { item ->
                    nav.navigate("article?url=${Uri.encode(item.link)}&title=${Uri.encode(item.title)}")
                },
                onOpenSettings = openSettings
            ) }
            composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable("match/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                MatchDetailScreen(
                    matchId = id,
                    onBack = { nav.popBackStack() },
                    onWatchHighlights = { m ->
                        nav.navigate(
                            "video?home=${Uri.encode(m.homeTeam.name)}&away=${Uri.encode(m.awayTeam.name)}" +
                                "&utc=${Uri.encode(m.utcDate)}" +
                                "&hg=${m.score.fullTime?.home ?: -1}&ag=${m.score.fullTime?.away ?: -1}"
                        )
                    }
                )
            }
            composable("article?url={url}&title={title}") { entry ->
                ArticleReader(
                    title = entry.arguments?.getString("title").orEmpty(),
                    url = entry.arguments?.getString("url").orEmpty(),
                    onClose = { nav.popBackStack() }
                )
            }
            composable("video?home={home}&away={away}&utc={utc}&hg={hg}&ag={ag}") { entry ->
                HighlightsScreen(
                    home = entry.arguments?.getString("home").orEmpty(),
                    away = entry.arguments?.getString("away").orEmpty(),
                    utcDate = entry.arguments?.getString("utc").orEmpty(),
                    homeGoals = entry.arguments?.getString("hg")?.toIntOrNull() ?: -1,
                    awayGoals = entry.arguments?.getString("ag")?.toIntOrNull() ?: -1,
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}
