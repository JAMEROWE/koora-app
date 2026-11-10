package com.koora.app.ui.screens

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/*
 * VIDEO-RESOLUTION CACHE (v1.5.6) — highlights are looked up once per
 * fixture, then served from the phone:
 *   - FOUND  -> cached 24h  (a published highlight does not disappear)
 *   - NOT FOUND -> cached only 45 minutes — official channels usually
 *     publish 1-6h after full time, so a fresh miss must retry soon or
 *     the user would stare at "no highlights published yet" all day.
 * Pure TTL decision lives in [isFresh] (unit-tested). No network here.
 */
internal object VideoCache {

    internal const val FOUND_TTL_MS = 24L * 60 * 60 * 1000
    internal const val MISS_TTL_MS = 45L * 60 * 1000

    private const val PREFS = "koora_video_cache"
    private val gson = Gson()
    private val listType = object : TypeToken<List<SlotVideo>>() {}.type

    internal data class SlotVideo(
        val title: String = "",
        val videoId: String = "",
        val published: String = ""
    )

    private data class Slot(val found: Boolean, val at: Long, val videos: List<SlotVideo> = emptyList())

    /** Pure TTL rule: found = fresh for 24h, miss = fresh for 45 min. */
    internal fun isFresh(found: Boolean, at: Long, now: Long): Boolean {
        if (at > now) return false
        val age = now - at
        return age <= (if (found) FOUND_TTL_MS else MISS_TTL_MS)
    }

    private fun key(home: String, away: String) = "$home|$away"

    /**
     * Cached resolution for this fixture, or null when nothing usable:
     * null = "go look on the network"; an empty list (fresh miss) means
     * "we already looked recently — there is genuinely nothing yet".
     */
    internal fun get(context: Context, home: String, away: String): List<YtEntry>? = runCatching {
        val raw = prefs(context).getString(key(home, away), null) ?: return null
        val slot = gson.fromJson(raw, Slot::class.java) ?: return null
        if (!isFresh(slot.found, slot.at, System.currentTimeMillis())) return null
        if (!slot.found) return emptyList()
        slot.videos.map { YtEntry(it.title, it.videoId, it.published) }
    }.getOrNull()

    internal fun putFound(context: Context, home: String, away: String, videos: List<YtEntry>) {
        put(context, home, away, Slot(true, System.currentTimeMillis(),
            videos.map { SlotVideo(it.title, it.videoId, it.published) }))
    }

    internal fun putMiss(context: Context, home: String, away: String) {
        put(context, home, away, Slot(false, System.currentTimeMillis(), emptyList()))
    }

    private fun put(context: Context, home: String, away: String, slot: Slot) {
        runCatching {
            prefs(context).edit()
                .putString(key(home, away), gson.toJson(slot))
                .apply()
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
