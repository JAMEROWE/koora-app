package com.koora.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.koora.app.BuildConfig
import com.koora.app.R
import com.koora.app.data.FavoritesStore
import com.koora.app.ui.theme.ThemeController

/*
 * Minimal Settings screen:
 *  - Language switcher (per-app locales, persists automatically) — makes
 *    the EN / FR / AR promise real on any device.
 *  - Favorites management.
 *  - About (version + data credits).
 */
@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val locales = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (locales.isEmpty) "" else locales.toLanguageTags()
    var favCount by remember { mutableIntStateOf(FavoritesStore.count(context)) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // v1.9.0: Settings left the bottom bar (spec §1.1) — it is a page
        // now, so it needs its own way back.
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.nav_back)
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.tab_settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(16.dp))

        // ---- Language ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                LanguageRow("English", "en", currentTag) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                }
                LanguageRow("Français", "fr", currentTag) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr"))
                }
                LanguageRow("العربية", "ar", currentTag) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
                }
                Text(
                    stringResource(R.string.language_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Theme (Night Stadium) ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                LanguageRow(stringResource(R.string.theme_system), ThemeController.SYSTEM, ThemeController.mode) {
                    ThemeController.set(context, ThemeController.SYSTEM)
                }
                LanguageRow(stringResource(R.string.theme_dark), ThemeController.DARK, ThemeController.mode) {
                    ThemeController.set(context, ThemeController.DARK)
                }
                LanguageRow(stringResource(R.string.theme_light), ThemeController.LIGHT, ThemeController.mode) {
                    ThemeController.set(context, ThemeController.LIGHT)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Favorites ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_favorites),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.fav_count, favCount),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (favCount > 0) {
                    TextButton(onClick = {
                        FavoritesStore.clear(context)
                        favCount = 0
                    }) {
                        Text(stringResource(R.string.fav_clear))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- About ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_about),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString()),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.about_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                // v1.8.0 roadmap §3.4: permanent legal notice — always visible
                // in Settings, never conditional.
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    stringResource(R.string.legal_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LanguageRow(label: String, tag: String, currentTag: String, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = currentTag.startsWith(tag), onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
