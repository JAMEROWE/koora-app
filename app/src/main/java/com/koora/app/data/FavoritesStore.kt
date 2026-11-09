package com.koora.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Locally stored favorite matches (the "bell/star" feature).
 * Stored as JSON in SharedPreferences — no backend needed yet.
 * When the push-notification backend arrives (API-Football upgrade),
 * these saved matches become the notification targets.
 */
data class FavoriteMatch(
    val id: Long,
    val home: String,
    val away: String,
    val utcDate: String,
    val competition: String,
    val status: String = "SCHEDULED",
    val homeGoals: Int? = null,
    val awayGoals: Int? = null
)

object FavoritesStore {

    private const val PREFS = "koora_favorites"
    private const val KEY = "matches"

    fun all(context: Context): List<FavoriteMatch> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                FavoriteMatch(
                    id = o.getLong("id"),
                    home = o.getString("home"),
                    away = o.getString("away"),
                    utcDate = o.getString("utcDate"),
                    competition = o.getString("competition"),
                    status = o.optString("status", "SCHEDULED"),
                    homeGoals = if (o.has("hg")) o.getInt("hg") else null,
                    awayGoals = if (o.has("ag")) o.getInt("ag") else null
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isFavorite(context: Context, id: Long): Boolean =
        all(context).any { it.id == id }

    /** Add (or refresh the stored score/status of) a match, or remove if already saved. */
    fun toggle(context: Context, match: Match) {
        val list = all(context)
        val listNoId = list.filter { it.id != match.id }
        val code = MatchRepository.cacheCode
        val ft = match.score.fullTime
        val entry = FavoriteMatch(
            id = match.id,
            home = match.homeTeam.name,
            away = match.awayTeam.name,
            utcDate = match.utcDate,
            competition = code,
            status = match.status,
            homeGoals = ft?.home,
            awayGoals = ft?.away
        )
        val removed = list.size == listNoId.size
        val next = if (removed) listNoId else listNoId + entry
        save(context, next)
    }

    fun remove(context: Context, id: Long) {
        save(context, all(context).filter { it.id != id })
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    fun count(context: Context): Int = all(context).size

    /** Convert a stored favorite back into a Match for UI rendering. */
    fun toMatch(f: FavoriteMatch): Match = Match(
        id = f.id,
        utcDate = f.utcDate,
        status = f.status,
        homeTeam = Team(id = -1, name = f.home),
        awayTeam = Team(id = -2, name = f.away),
        score = Score(fullTime = ScoreDetail(f.homeGoals, f.awayGoals))
    )

    private fun save(context: Context, list: List<FavoriteMatch>) {
        val arr = JSONArray()
        list.forEach { f ->
            val o = JSONObject()
            o.put("id", f.id)
            o.put("home", f.home)
            o.put("away", f.away)
            o.put("utcDate", f.utcDate)
            o.put("competition", f.competition)
            o.put("status", f.status)
            f.homeGoals?.let { o.put("hg", it) }
            f.awayGoals?.let { o.put("ag", it) }
            arr.put(o)
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
