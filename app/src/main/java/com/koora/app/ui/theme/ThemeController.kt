package com.koora.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * Theme preference (v1.5.3 "Night Stadium").
 *
 * Three modes — "system" (default), "dark", "light" — persisted in
 * SharedPreferences and exposed as Compose state so switching the theme
 * in Settings repaints the whole app instantly.
 */
object ThemeController {

    private const val PREFS = "koora_theme"
    private const val KEY_MODE = "mode"

    const val SYSTEM = "system"
    const val DARK = "dark"
    const val LIGHT = "light"

    var mode by mutableStateOf(SYSTEM)
        private set

    fun init(context: Context) {
        mode = runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODE, SYSTEM)
        }.getOrNull() ?: SYSTEM
    }

    fun set(context: Context, newMode: String) {
        mode = if (newMode in listOf(SYSTEM, DARK, LIGHT)) newMode else SYSTEM
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_MODE, mode).apply()
        }
    }
}
