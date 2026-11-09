package com.koora.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Settings
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.koora.app.ui.screens.LiveScreen
import com.koora.app.ui.screens.MatchDetailScreen
import com.koora.app.ui.screens.MatchesScreen
import com.koora.app.ui.screens.NewsScreen
import com.koora.app.ui.screens.SettingsScreen
import com.koora.app.ui.screens.StandingsScreen
import com.koora.app.ui.theme.KooraTheme

/*
 * AppCompatActivity (not ComponentActivity) so that the per-app language
 * switcher (AppCompatDelegate.setApplicationLocales) works on Android 12
 * and below too — the EN / FR / AR promise.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // GDPR consent (Google UMP) — required before showing ads to EU users (France)
        requestGdprConsent()
        setContent {
            KooraTheme {
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
    val tabs = listOf(
        TabItem("matches", Icons.Filled.SportsSoccer, R.string.tab_matches),
        TabItem("live", Icons.Filled.FiberManualRecord, R.string.tab_live),
        TabItem("standings", Icons.Filled.EmojiEvents, R.string.tab_standings),
        TabItem("news", Icons.Filled.Article, R.string.tab_news),
        TabItem("settings", Icons.Filled.Settings, R.string.tab_settings)
    )

    Scaffold(
        bottomBar = {
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
                        label = { Text(stringResource(tab.labelRes)) }
                    )
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
                MatchesScreen(onMatchClick = { id -> nav.navigate("match/$id") })
            }
            composable("live") { LiveScreen() }
            composable("standings") { StandingsScreen() }
            composable("news") { NewsScreen() }
            composable("settings") { SettingsScreen() }
            composable("match/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                MatchDetailScreen(matchId = id, onBack = { nav.popBackStack() })
            }
        }
    }
}
