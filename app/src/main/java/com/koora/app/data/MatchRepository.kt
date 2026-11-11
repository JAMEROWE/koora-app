package com.koora.app.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.koora.app.BuildConfig
import com.koora.app.widget.WidgetFeed
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.delay
import java.io.File
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

/*
 * Single source of truth for football data.
 * With a key in local.properties  -> real data from football-data.org.
 * Without a key                   -> demo data, so the app runs out of the box.
 *
 * Resilience (v1.5.3 "never a broken screen"):
 *  - every successful response is cached in MEMORY (60s TTL) and written
 *    to a DISK file in cacheDir;
 *  - on ANY network failure (429 rate limit, timeout, no connection...),
 *    the last cached value — memory first, then disk — is served instead
 *    of throwing, and the key is flagged stale so the UI can show
 *    "saved data, updated X min ago".
 */
object MatchRepository {

    // v1.5.6: 5-minute TTL ("comfortable" doc) — pull-to-refresh bypasses it.
    private const val TTL_MS = 300_000L

    /*
     * WINDOW LOADING (v1.6.0) — WHY PER-COMPETITION CALLS:
     * the free tier proved NOT to serve the all-competitions /v4/matches
     * endpoint (real installs got an empty screen), so the window is
     * assembled from the resource that always worked:
     * /v4/competitions/{code}/matches?dateFrom&dateTo.
     *
     * Free plan = 10 calls/minute, so the loader is rate-limit aware:
     *  - one cache entry PER LEAGUE (5-min TTL + disk copy): a failing or
     *    rate-limited league can never blank out the other leagues;
     *  - forced refreshes (pull-to-refresh / 60s live auto-refresh) fetch
     *    at most FETCHES_PER_ROUND leagues, and only leagues not updated
     *    in the last 60s — successive rounds naturally rotate through all
     *    leagues while never exceeding the quota;
     *  - 429 -> one retry after RETRY_429_DELAY_MS;
     *  - 403/404 (plan/season) -> remembered as empty so we never hammer
     *    a rejected endpoint again this window;
     *  - any other failure -> last saved copy for that league, else skip.
     * Botola Pro (BOT) has no football-data.org resource yet — it joins
     * with the API-Football upgrade, demo mode stamps it locally.
     */
    private val WINDOW_LEAGUES = listOf(
        "CL", "PL", "PD", "SA", "BL1", "FL1", "ELC", "DED", "PPL", "BSA", "CLI", "WC", "EC"
    )
    private const val FETCHES_PER_ROUND = 6
    private const val FORCE_MIN_INTERVAL_MS = 60_000L
    private const val RETRY_429_DELAY_MS = 6_000L

    /** Last successfully loaded match list, used by the detail screen. */
    var cache: List<Match> = emptyList()
        private set

    /** Competition code of the list currently in [cache]. */
    var cacheCode: String = ""
        private set

    /** Test hook: force demo mode so internal launch tests never touch the network. */
    @VisibleForTesting
    internal var forceDemoForTest: Boolean = false

    val hasKey: Boolean get() = !forceDemoForTest && BuildConfig.FOOTBALL_DATA_TOKEN.isNotBlank()

    private data class Entry(val at: Long, val value: Any?)

    private val store = mutableMapOf<String, Entry>()
    private val updated = mutableMapOf<String, Long>()
    private val staleKeys = mutableSetOf<String>()
    private val gson = Gson()

    /** Timestamp (epoch ms) of the last successful fetch for a cache key. */
    fun lastUpdated(key: String): Long = updated[key] ?: 0L

    /** True when the current value for [key] was served from cache after a failure. */
    fun isStale(key: String): Boolean = key in staleKeys

    // ---- Disk cache -------------------------------------------------------

    @Volatile
    private var appContext: Context? = null

    /** Call once from Application.onCreate. Safe to skip (tests). */
    fun initDiskCache(context: Context) {
        appContext = context.applicationContext
    }

    private fun diskFile(key: String): File? =
        appContext?.let { File(it.cacheDir, "koora_${key.replace(':', '_')}.json") }

    private class DiskWrap(val at: Long = 0, val data: Any? = null)

    private fun <T> persist(key: String, value: T, type: Type) {
        val f = diskFile(key) ?: return
        runCatching {
            val wrap = gson.toJsonTree(DiskWrap(System.currentTimeMillis(), value))
            f.writeText(wrap.toString())
        }
    }

    private fun <T> loadDisk(key: String, type: Type): T? {
        val f = diskFile(key) ?: return null
        return runCatching {
            if (!f.exists()) return null
            val obj = gson.fromJson(f.readText(), JsonObject::class.java)
            val at = obj.get("at")?.asLong ?: 0L
            val dataEl = obj.get("data") ?: return null
            if (dataEl.isJsonNull) return null
            val data: Any? = gson.fromJson(dataEl, type)
            if (updated[key] == null || updated[key]!! < at) updated[key] = at
            @Suppress("UNCHECKED_CAST")
            data as? T
        }.getOrNull()
    }

    private fun <T> remember(key: String, value: T, type: Type) {
        store[key] = Entry(System.currentTimeMillis(), value)
        updated[key] = System.currentTimeMillis()
        staleKeys.remove(key)
        persist(key, value, type)
    }

    private val api: FootballDataApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("X-Auth-Token", BuildConfig.FOOTBALL_DATA_TOKEN)
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.football-data.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FootballDataApi::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> fresh(key: String): T? {
        val e = store[key] ?: return null
        return if (System.currentTimeMillis() - e.at <= TTL_MS) e.value as? T else null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyCached(key: String): T? = store[key]?.value as? T

    private fun markStale(key: String) {
        staleKeys.add(key)
    }

    suspend fun matches(competition: String, force: Boolean = false): List<Match> {
        val key = "matches:$competition"
        if (!force) fresh<List<Match>>(key)?.let {
            cache = it; cacheCode = competition; return it
        }
        val type = object : TypeToken<List<Match>>() {}.type
        val list = try {
            if (hasKey) api.matches(competition).matches ?: emptyList()
            else DemoData.matches(competition)
        } catch (e: Exception) {
            val stale = anyCached<List<Match>>(key) ?: loadDisk<List<Match>>(key, type)
            if (stale != null) { markStale(key); stale } else throw e
        }
        remember(key, list, type)
        cache = list
        cacheCode = competition
        notifyWidgetFeed()
        return list
    }

    /** v1.5.5: after every successful matches() fetch, refresh the widget. */
    private fun notifyWidgetFeed() {
        val ctx = appContext ?: return
        runCatching { WidgetFeed.refresh(ctx, cacheCode, cache) }
    }

    /**
     * v1.6.0 THE WINDOW: every competition merged over a local date range
     * ("yyyy-MM-dd".."yyyy-MM-dd"). ONE shared fetch feeds the Live tab
     * (today + tomorrow sections) AND the Results tab (finished matches) —
     * zero duplicate calls between screens thanks to the per-league cache.
     */
    suspend fun matchesWindow(fromDay: String, toDay: String, force: Boolean = false): List<Match> {
        if (!hasKey) return DemoData.matches("ALL").distinctBy { it.id }
        val type = object : TypeToken<List<Match>>() {}.type
        val now = System.currentTimeMillis()
        // Refreshes are budget-capped to protect the 10/min free quota; only
        // a truly first load (no league cached at all) may fetch everything.
        val haveAnyWindowCache = updated.keys.any { it.startsWith("leaguewin:") }
        var fetchBudget = if (!haveAnyWindowCache) Int.MAX_VALUE else FETCHES_PER_ROUND
        // Live auto-refresh wants 60s freshness; a plain screen entry only
        // refetches leagues past the 5-minute TTL.
        val minAgeMs = if (force) FORCE_MIN_INTERVAL_MS else TTL_MS
        val merged = LinkedHashMap<Long, Match>()
        for (code in WINDOW_LEAGUES) {
            val leagueKey = "leaguewin:$code:$fromDay:$toDay"
            val cached = anyCached<List<Match>>(leagueKey)
            val age = now - (updated[leagueKey] ?: 0L)
            val allowNetwork = cached == null || (age >= minAgeMs && fetchBudget > 0)
            val list: List<Match> = if (allowNetwork) {
                if (cached != null) fetchBudget--
                fetchLeagueWindow(code, leagueKey, fromDay, toDay, type)
            } else {
                cached ?: loadDisk<List<Match>>(leagueKey, type) ?: emptyList()
            }
            list.forEach { m -> merged.putIfAbsent(m.id, m) }
        }
        // v1.6.1: cups + Botola Pro via API-Football (free plan, ONE request
        // per window day). Each date is cached on its own TTL (past: until
        // local midnight — results never change; today: 10 min; future: 30
        // min). Fully asleep without the API_FOOTBALL_KEY build value.
        if (AfFixtures.available) {
            for (day in daysBetween(fromDay, toDay)) {
                val dayMatches = runCatching { afDay(day, type) }.getOrNull() ?: continue
                dayMatches.forEach { m -> merged.putIfAbsent(m.id, m) }
            }
        }
        val result = merged.values.toList()
        cache = result
        cacheCode = "WINDOW"
        notifyWidgetFeed()
        val ctx = appContext
        if (ctx != null) {
            result.groupBy { it.competition?.code ?: "" }.forEach { (c, ms) ->
                if (c.isNotBlank()) runCatching { WidgetFeed.refresh(ctx, c, ms) }
            }
        }
        return result
    }

    /** One league of the window — never throws; failures degrade to cache/skip. */
    private suspend fun fetchLeagueWindow(
        code: String,
        leagueKey: String,
        fromDay: String,
        toDay: String,
        type: Type
    ): List<Match> {
        var attempt = runCatching {
            api.competitionMatchesRange(code, fromDay, toDay).matches ?: emptyList()
        }
        if (attempt.isFailure && attempt.exceptionOrNull() is HttpException &&
            (attempt.exceptionOrNull() as HttpException).code() == 429
        ) {
            delay(RETRY_429_DELAY_MS)
            attempt = runCatching {
                api.competitionMatchesRange(code, fromDay, toDay).matches ?: emptyList()
            }
        }
        attempt.getOrNull()?.let {
            remember(leagueKey, it, type)
            return it
        }
        val e = attempt.exceptionOrNull()
        if (e is HttpException && (e.code() == 403 || e.code() == 404)) {
            // Endpoint not on this plan / league not in season: remember the
            // empty result so this window never re-asks.
            remember(leagueKey, emptyList<Match>(), type)
            return emptyList()
        }
        val stale = anyCached<List<Match>>(leagueKey) ?: loadDisk<List<Match>>(leagueKey, type)
        if (stale != null) markStale(leagueKey)
        return stale ?: emptyList()
    }

    /**
     * One API-Football date with its own cache TTL. Never throws upward:
     * any failure degrades to the last cached copy ("stale" flag) or to an
     * empty list — a cups outage can never blank the other leagues.
     */
    private suspend fun afDay(day: String, type: Type): List<Match> {
        val key = "af:$day"
        val now = System.currentTimeMillis()
        val ttl = afTtlMs(day, now)
        anyCached<List<Match>>(key)?.let { cached ->
            if (now - (updated[key] ?: 0L) <= ttl) return cached
        }
        return try {
            val fetched = AfFixtures.fetchDay(day)
            remember(key, fetched, type)
            fetched
        } catch (e: Exception) {
            val stale = anyCached<List<Match>>(key) ?: loadDisk<List<Match>>(key, type)
            if (stale != null) { markStale(key); stale } else emptyList()
        }
    }

    /** Cache lifetime for one API-Football date key ("yyyy-MM-dd"). */
    private fun afTtlMs(day: String, now: Long): Long = when {
        day == todayKeyLocal() -> 10 * 60_000L            // today: live-ish
        day < todayKeyLocal() -> nextLocalMidnight(now) - now // past: final
        else -> 30 * 60_000L                              // future: slow drift
    }

    private val dayFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)

    private fun todayKeyLocal(): String = dayFormat.format(java.util.Date())

    private fun nextLocalMidnight(now: Long): Long {
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = now
        c.add(java.util.Calendar.DAY_OF_MONTH, 1)
        c.set(java.util.Calendar.HOUR_OF_DAY, 0)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** Local days "yyyy-MM-dd" from fromDay to toDay inclusive (defensive). */
    private fun daysBetween(fromDay: String, toDay: String): List<String> {
        val out = mutableListOf<String>()
        try {
            val from = dayFormat.parse(fromDay) ?: return listOf(fromDay)
            val end = dayFormat.parse(toDay) ?: return listOf(fromDay)
            val c = java.util.Calendar.getInstance()
            c.time = from
            while (!c.time.after(end) && out.size < 15) {
                out.add(dayFormat.format(c.time))
                c.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
        } catch (_: Exception) {
        }
        if (out.isEmpty()) out.add(fromDay)
        return out
    }

    /**
     * A team's fixtures across all competitions for the Teams panel.
     * 6h cache (doc). Free-plan/403/429 failures fall back to the cache;
     * with nothing cached the exception reaches the panel, which shows the
     * honest "team fixtures not available" state. Demo mode derives from
     * the sample data locally — no network.
     */
    suspend fun teamFixtures(teamId: Long): List<Match> {
        if (!hasKey) {
            return DemoData.matches("ALL")
                .filter { it.homeTeam.id == teamId || it.awayTeam.id == teamId }
                .distinctBy { it.id }
        }
        val key = "team:$teamId"
        val type = object : TypeToken<List<Match>>() {}.type
        val sixHours = 6L * 3_600_000
        anyCached<List<Match>>(key)?.let {
            if (System.currentTimeMillis() - (updated[key] ?: 0L) <= sixHours) return it
        }
        val list: List<Match> = try {
            api.teamMatches(teamId).matches ?: emptyList()
        } catch (e: Exception) {
            val stale = anyCached<List<Match>>(key) ?: loadDisk<List<Match>>(key, type)
            // v1.6.0 fallback: derive what we can from the already-loaded
            // window (real data, just limited to the loaded days) instead of
            // showing "unavailable" for an endpoint hiccup.
            if (stale != null) { markStale(key); stale }
            else {
                val derived = cache.filter { it.homeTeam.id == teamId || it.awayTeam.id == teamId }
                if (derived.isNotEmpty()) derived else throw e
            }
        }
        remember(key, list, type)
        return list
    }

    suspend fun standings(competition: String, force: Boolean = false): List<TableRow> {
        val key = "standings:$competition"
        if (!force) fresh<List<TableRow>>(key)?.let { return it }
        val type = object : TypeToken<List<TableRow>>() {}.type
        val table = try {
            if (hasKey) api.standings(competition)
                .standings
                .firstOrNull { it.type.equals("TOTAL", ignoreCase = true) }
                ?.table
                ?: emptyList()
            else DemoData.standings()
        } catch (e: Exception) {
            val stale = anyCached<List<TableRow>>(key) ?: loadDisk<List<TableRow>>(key, type)
            if (stale != null) { markStale(key); stale } else throw e
        }
        remember(key, table, type)
        return table
    }

    /** Previous meetings between the two teams of a match. 429 -> empty (graceful). */
    suspend fun head2head(matchId: Long): List<Match> {
        if (!hasKey) return emptyList()
        return try {
            api.head2head(matchId).matches ?: emptyList()
        } catch (e: HttpException) {
            if (e.code() == 429) emptyList() else throw e
        }
    }

    /**
     * Recent results for a team, as 'W'/'D'/'L' (most recent first, up to [count]).
     * Computed locally from the competition matches already in [matches] — no extra API call.
     */
    fun teamForm(matches: List<Match>, teamId: Long, count: Int = 5): List<Char> {
        return matches
            .filter { it.status == "FINISHED" && (it.homeTeam.id == teamId || it.awayTeam.id == teamId) }
            .sortedByDescending { it.utcDate }
            .take(count)
            .mapNotNull { m ->
                val home = m.score.fullTime?.home
                val away = m.score.fullTime?.away
                if (home == null || away == null) return@mapNotNull null
                val forTeam = if (m.homeTeam.id == teamId) home else away
                val against = if (m.homeTeam.id == teamId) away else home
                when {
                    forTeam > against -> 'W'
                    forTeam < against -> 'L'
                    else -> 'D'
                }
            }
    }
}
