package com.koora.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Koora brand colors — pitch green, works for MENA + France audiences
val PitchGreen = Color(0xFF0B6E4F)
val PitchGreenDark = Color(0xFF0A5A42)
val PitchAccent = Color(0xFF2BD48F)
val NightSurface = Color(0xFF0E1512)
val LiveRed = Color(0xFFE53935)

private val LightColors = lightColorScheme(
    primary = PitchGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8EFD4),
    onPrimaryContainer = Color(0xFF06231A),
    secondary = PitchGreenDark,
    background = Color(0xFFF7FAF8),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = PitchAccent,
    onPrimary = Color(0xFF06231A),
    primaryContainer = PitchGreenDark,
    onPrimaryContainer = Color(0xFFB8EFD4),
    secondary = PitchAccent,
    background = NightSurface,
    surface = Color(0xFF14201B)
)

@Composable
fun KooraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
