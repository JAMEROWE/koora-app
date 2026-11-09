package com.koora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
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
import com.koora.app.ui.screens.MatchDetailScreen
import com.koora.app.ui.screens.MatchesScreen
import com.koora.app.ui.screens.StandingsScreen
import com.koora.app.ui.theme.KooraTheme

class MainActivity : ComponentActivity() {
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
                // form errors are ignored in the skeleton
            }
        } catch (_: Exception) {
            // consent must never crash the app
        }
    }
}

private data class TabItem(val route: String, val icon: ImageVector, val labelRes: Int)

@Composable
private fun KooraRoot() {
    val nav = rememberNavController()
    val tabs = listOf(
        TabItem("matches", Icons.Filled.SportsSoccer, R.string.tab_matches),
        TabItem("standings", Icons.Filled.EmojiEvents, R.string.tab_standings)
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
            composable("standings") { StandingsScreen() }
            composable("match/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                MatchDetailScreen(matchId = id, onBack = { nav.popBackStack() })
            }
        }
    }
}
