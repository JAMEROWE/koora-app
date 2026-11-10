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
     * v1.5.6 Today/Tomorrow window: ONE call for every competition between
     * two local days ("yyyy-MM-dd"). Falls back to the last cached window
     * on any failure (stale flag -> "saved data" caption); demo mode serves
     * the sample list with no network. Widget pools are refreshed per
     * competition code so the home-screen widget benefits too.
     */
    suspend fun matchesRange(fromDay: String, toDay: String, force: Boolean = false): Pair<List<Match>, Boolean> {
        val key = "range:$fromDay:$toDay"
        val type = object : TypeToken<List<Match>>() {}.type
        if (!force) fresh<List<Match>>(key)?.let { return it to false }
        val list: List<Match> = try {
            if (hasKey) api.matchesRange(fromDay, toDay).matches ?: emptyList()
            else DemoData.matches("ALL").distinctBy { it.id }
        } catch (e: Exception) {
            val stale = anyCached<List<Match>>(key) ?: loadDisk<List<Match>>(key, type)
            if (stale != null) { markStale(key); return stale to true } else throw e
        }
        remember(key, list, type)
        cache = list
        cacheCode = "RANGE"
        notifyWidgetFeed()
        val ctx = appContext
        if (ctx != null) {
            list.groupBy { it.competition?.code ?: "" }.forEach { (code, ms) ->
                if (code.isNotBlank()) runCatching { WidgetFeed.refresh(ctx, code, ms) }
            }
        }
        return list to false
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
            if (stale != null) { markStale(key); stale } else throw e
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
