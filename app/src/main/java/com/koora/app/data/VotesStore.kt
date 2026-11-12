package com.koora.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/*
 * STAGE-1 VOTES (v1.5.6) — one pick per user per match, saved on the phone.
 * No backend, always free, works offline. The panel shows the pick plus
 * the honest placeholder line ("community percentages … when available");
 * stage 2 (a free backend) will read these same keys later, nothing here
 * changes. Locking at kick-off is enforced by ui.voteLocked (Rules layer).
 */
object VotesStore {

    private const val PREFS = "koora_votes"
    private const val KEY = "picks"
    private const val REACTIONS_KEY = "reactions"

    /** "HOME" / "DRAW" / "AWAY" (VotePick.key) or null when no pick yet. */
    fun pick(context: Context, matchId: Long): String? {
        val map = all(context)
        return map[matchId.toString()]
    }

    /**
     * v1.10.0 — every pick ever made, matchId -> pick. Read by the
     * personal-accuracy line (accuracyOf counts only resolved matches).
     */
    fun picks(context: Context): Map<Long, String> =
        all(context).mapKeys { (k, _) -> k.toLongOrNull() ?: 0L }
            .filterKeys { it != 0L }

    /** Saves/changes the pick — only allowed before kick-off (UI checks voteLocked). */
    fun setPick(context: Context, matchId: Long, pick: String) {
        val map = all(context).toMutableMap()
        map[matchId.toString()] = pick
        save(context, map)
    }

    /**
     * v1.10.0 (spec §2.3) — the user's quick reactions ("Clean sheet",
     * "Goal fest", "Upset alert"), stored locally like the pick. Multi-select:
     * a match can be both a goal fest and an upset. The set is empty when
     * the user reacted to nothing.
     */
    fun reactions(context: Context, matchId: Long): Set<String> {
        val raw = allReactions(context)[matchId.toString()] ?: return emptySet()
        return raw.filter { it.isNotBlank() }.toSet()
    }

    /** Adds the reaction when missing, removes it when present. */
    fun toggleReaction(context: Context, matchId: Long, key: String) {
        val all = allReactions(context).toMutableMap()
        val current = all[matchId.toString()]?.toMutableSet() ?: mutableSetOf()
        if (!current.add(key)) {
            current.remove(key)
        }
        if (current.isEmpty()) all.remove(matchId.toString()) else all[matchId.toString()] = current.toList()
        val o = JSONObject()
        all.forEach { (id, list) -> o.put(id, JSONArray(list)) }
        prefs(context).edit().putString(REACTIONS_KEY, o.toString()).apply()
    }

    private fun allReactions(context: Context): Map<String, List<String>> {
        val raw = prefs(context).getString(REACTIONS_KEY, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            val out = mutableMapOf<String, List<String>>()
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                val arr = o.getJSONArray(k)
                val list = (0 until arr.length()).map { arr.getString(it) }
                out[k] = list
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun all(context: Context): Map<String, String> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            val out = mutableMapOf<String, String>()
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                out[k] = o.getString(k)
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun save(context: Context, map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs(context).edit().putString(KEY, o.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
