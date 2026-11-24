package com.koora.app.data

import android.content.Context
import java.text.Normalizer

/**
 * First-launch personalization: the user's favorite LEAGUES and TEAMS,
 * picked in the onboarding dialog and editable from Settings. Stored in
 * SharedPreferences — no backend. Favorite leagues pin to the top of a
 * match day and open by default; sections holding a favorite team's
 * match open too. This is the teams/leagues personalization from the
 * app-identity review; the older per-match star (FavoritesStore) is a
 * separate feature and stays untouched.
 */
object FavoriteTeamsStore {

    /** Test-only escape hatch, same pattern as MatchRepository.forceDemoForTest. */
    @Volatile var skipOnboardingForTest: Boolean = false

    private const val PREFS = "koora_favorite_teams"
    private const val KEY_TEAMS = "teams"
    private const val KEY_LEAGUES = "leagues"
    private const val KEY_ONBOARDED = "onboarded"

    fun teams(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_TEAMS, emptySet()) ?: emptySet()

    fun leagues(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_LEAGUES, emptySet()) ?: emptySet()

    fun setAll(context: Context, teams: Set<String>, leagues: Set<String>) {
        prefs(context).edit()
            .putStringSet(KEY_TEAMS, teams)
            .putStringSet(KEY_LEAGUES, leagues)
            .apply()
    }

    fun onboardingDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDED, false)

    fun markOnboardingDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    /** Lowercase, diacritics stripped, punctuation collapsed — pure. */
    internal fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /**
     * Two names match when equal after normalization or one fully
     * contains the other ("Arsenal" vs "Arsenal FC"). Stored favorites
     * are full team names picked from a list, so containment cannot
     * bridge two different clubs. Pure.
     */
    internal fun normalizedEquals(stored: String, normalizedName: String): Boolean {
        val s = normalize(stored)
        if (s.isEmpty() || normalizedName.isEmpty()) return false
        return s == normalizedName ||
            normalizedName.contains(s) ||
            s.contains(normalizedName)
    }

    fun isFavoriteTeam(context: Context, teamName: String): Boolean {
        val n = normalize(teamName)
        return teams(context).any { normalizedEquals(it, n) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
