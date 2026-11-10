package com.koora.app.data

import android.content.Context
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

    /** "HOME" / "DRAW" / "AWAY" (VotePick.key) or null when no pick yet. */
    fun pick(context: Context, matchId: Long): String? {
        val map = all(context)
        return map[matchId.toString()]
    }

    /** Saves/changes the pick — only allowed before kick-off (UI checks voteLocked). */
    fun setPick(context: Context, matchId: Long, pick: String) {
        val map = all(context).toMutableMap()
        map[matchId.toString()] = pick
        save(context, map)
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
