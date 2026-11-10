package com.koora.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Koora brand colors — "Night Stadium" (v1.5.3).
 *
 * Dark-first: near-black like a stadium under floodlights, one pitch-green
 * accent. Light theme stays available via Settings.
 *
 * Status colors (used directly by match components):
 */
val LiveRed = Color(0xFFEF4444)
val CardAmber = Color(0xFFF59E0B)
val PitchGreen = Color(0xFF15803D)      // light-theme accent
val PitchAccent = Color(0xFF22C55E)     // dark-theme accent
val PitchGreenDark = Color(0xFF14532D)
val NightSurface = Color(0xFF0E1116)

// Light — clean paper look for daytime reading
private val LightColors = lightColorScheme(
    primary = PitchGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF052E16),
    secondary = Color(0xFF334155),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = CardAmber,
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF0E1116),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0E1116),
    surfaceVariant = Color(0xFFE9EEF4),
    onSurfaceVariant = Color(0xFF5A6472),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F8),
    surfaceContainer = Color(0xFFE9EEF4),
    surfaceContainerHigh = Color(0xFFDFE5EC),
    surfaceContainerHighest = Color(0xFFD9E0E8),
    outline = Color(0xFFC9D2DC),
    outlineVariant = Color(0xFFD5DCE4),
    error = Color(0xFFDC2626),
    onError = Color.White
)

// Dark — the stadium at night
private val DarkColors = darkColorScheme(
    primary = PitchAccent,
    onPrimary = Color(0xFF052E16),
    primaryContainer = PitchGreenDark,
    onPrimaryContainer = Color(0xFFBBF7D0),
    secondary = Color(0xFF9AA6B8),
    onSecondary = Color(0xFF0E1116),
    secondaryContainer = Color(0xFF1D242F),
    onSecondaryContainer = Color(0xFFC9D2DC),
    tertiary = CardAmber,
    background = NightSurface,
    onBackground = Color(0xFFF2F5F9),
    surface = NightSurface,
    onSurface = Color(0xFFF2F5F9),
    surfaceVariant = Color(0xFF1D242F),
    onSurfaceVariant = Color(0xFF8A94A6),
    surfaceContainerLowest = Color(0xFF0A0D11),
    surfaceContainerLow = Color(0xFF141922),
    surfaceContainer = Color(0xFF171C24),
    surfaceContainerHigh = Color(0xFF1D242F),
    surfaceContainerHighest = Color(0xFF232B37),
    outline = Color(0xFF2A3340),
    outlineVariant = Color(0xFF232B37),
    error = LiveRed,
    onError = Color(0xFF2B0505)
)

@Composable
fun KooraTheme(
    themeMode: String = ThemeController.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeController.DARK -> true
        ThemeController.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = KooraTypography,
        content = content
    )
}
