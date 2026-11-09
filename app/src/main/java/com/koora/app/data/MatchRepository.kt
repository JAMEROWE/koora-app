package com.koora.app.data

import com.koora.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/*
 * Single source of truth for football data.
 * With a key in local.properties  -> real data from football-data.org.
 * Without a key                   -> demo data, so the app runs out of the box.
 *
 * Free-tier safety net: every response is cached for TTL_MS. Repeated calls
 * inside the TTL are served from memory (zero API calls). If the shared
 * rate limit is hit (HTTP 429) during a forced refresh, we serve the last
 * cached value instead of an error, so users never see a broken screen.
 */
object MatchRepository {

    private const val TTL_MS = 60_000L

    /** Last successfully loaded match list, used by the detail screen. */
    var cache: List<Match> = emptyList()
        private set

    /** Competition code of the list currently in [cache]. */
    var cacheCode: String = ""
        private set

    val hasKey: Boolean get() = BuildConfig.FOOTBALL_DATA_TOKEN.isNotBlank()

    private data class Entry(val at: Long, val value: Any?)

    private val store = mutableMapOf<String, Entry>()
    private val updated = mutableMapOf<String, Long>()

    /** Timestamp (epoch ms) of the last successful fetch for a cache key. */
    fun lastUpdated(key: String): Long = updated[key] ?: 0L

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

    private fun <T> remember(key: String, value: T) {
        store[key] = Entry(System.currentTimeMillis(), value)
        updated[key] = System.currentTimeMillis()
    }

    suspend fun matches(competition: String, force: Boolean = false): List<Match> {
        val key = "matches:$competition"
        if (!force) fresh<List<Match>>(key)?.let {
            cache = it; cacheCode = competition; return it
        }
        val list = try {
            if (hasKey) api.matches(competition).matches ?: emptyList()
            else DemoData.matches(competition)
        } catch (e: HttpException) {
            val stale = anyCached<List<Match>>(key)
            if (e.code() == 429 && stale != null) stale else throw e
        }
        remember(key, list)
        cache = list
        cacheCode = competition
        return list
    }

    suspend fun standings(competition: String, force: Boolean = false): List<TableRow> {
        val key = "standings:$competition"
        if (!force) fresh<List<TableRow>>(key)?.let { return it }
        val table = try {
            if (hasKey) api.standings(competition)
                .standings
                .firstOrNull { it.type.equals("TOTAL", ignoreCase = true) }
                ?.table
                ?: emptyList()
            else DemoData.standings()
        } catch (e: HttpException) {
            val stale = anyCached<List<TableRow>>(key)
            if (e.code() == 429 && stale != null) stale else throw e
        }
        remember(key, table)
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
