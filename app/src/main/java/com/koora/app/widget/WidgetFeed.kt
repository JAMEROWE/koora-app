package com.koora.app.widget

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.koora.app.data.Match
import com.koora.app.ui.fameScore
import com.koora.app.ui.isLive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/*
 * Data bridge between the app and the home-screen widget (v1.5.5).
 *
 * MatchRepository calls [refresh] after EVERY successful matches() fetch.
 * We keep a rolling pool of the competitions the user has loaded, pick the
 * most relevant fixtures with [pickWidgetMatches] (pure, unit-tested) and
 * persist the picks to filesDir — the widget provider may run when the app
 * itself was never started, so it reads only from disk.
 *
 * Never fake data: if nothing was picked, the widget shows a hint instead
 * of invented scores.
 */
object WidgetFeed {

    private const val POOL_FILE = "koora_widget_pool.json"
    private const val PICKS_FILE = "koora_widget_picks.json"
    private const val MAX_PER_COMPETITION = 100

    private val gson = Gson()
    private val poolType = object : TypeToken<Map<String, List<Match>>>() {}.type

    /** Called by MatchRepository after every successful matches() fetch. */
    fun refresh(context: Context, competition: String, list: List<Match>) {
        runCatching {
            if (competition.isBlank() || list.isEmpty()) return
            val pool = readPool(context).toMutableMap()
            pool[competition] = list.takeLast(MAX_PER_COMPETITION)
            writePool(context, pool)

            val picks = pickWidgetMatches(pool.values.flatten(), System.currentTimeMillis(), 4)
            if (picks.isEmpty()) return
            File(context.filesDir, PICKS_FILE).writeText(gson.toJson(picks))
            KooraWidgetProvider.pushUpdate(context)
        }
    }

    /** Picks for the widget provider; empty when the app has no data yet. */
    fun loadPicks(context: Context): List<Match> = runCatching {
        val f = File(context.filesDir, PICKS_FILE)
        if (!f.exists()) emptyList()
        else gson.fromJson(f.readText(), object : TypeToken<List<Match>>() {}.type)
            ?: emptyList()
    }.getOrDefault(emptyList())

    private fun readPool(context: Context): Map<String, List<Match>> = runCatching {
        val f = File(context.filesDir, POOL_FILE)
        if (!f.exists()) emptyMap()
        else gson.fromJson(f.readText(), poolType) ?: emptyMap()
    }.getOrDefault(emptyMap())

    private fun writePool(context: Context, pool: Map<String, List<Match>>) {
        File(context.filesDir, POOL_FILE).writeText(gson.toJson(pool))
    }
}

private val utcParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private const val FINISHED_WINDOW_MS = 36L * 60 * 60 * 1000  // keep FT for 36h

private fun finishedRecently(m: Match, nowMs: Long): Boolean {
    val t = runCatching { utcParser.parse(m.utcDate)?.time }.getOrNull() ?: return false
    return nowMs - t in 0..FINISHED_WINDOW_MS
}

/**
 * What the widget shows, most relevant first (pure function):
 *   1. LIVE matches — famous clubs first, earliest kickoff as tie-break;
 *   2. upcoming fixtures — same ordering;
 *   3. recent final scores (finished within the last 36h), newest first.
 * Duplicates removed, capped at [max]. Never invents matches.
 */
fun pickWidgetMatches(matches: List<Match>, nowMs: Long, max: Int = 4): List<Match> {
    val all = matches.distinctBy { it.id }
    val relevance = compareByDescending<Match> { fameScore(it) }.thenBy { it.utcDate }

    val live = all.filter { isLive(it) }.sortedWith(relevance)
    val upcoming = all
        .filter { !isLive(it) && !it.status.equals("FINISHED", true) }
        .sortedWith(relevance)
    val recent = all
        .filter { it.status.equals("FINISHED", true) && finishedRecently(it, nowMs) }
        .sortedByDescending { it.utcDate }

    return (live + upcoming + recent).distinctBy { it.id }.take(max)
}
