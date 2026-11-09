package com.koora.app.data

import com.koora.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/*
 * Single source of truth for football data.
 * With a key in local.properties  -> real data from football-data.org.
 * Without a key                   -> demo data, so the app runs out of the box.
 */
object MatchRepository {

    /** Last successfully loaded match list, used by the detail screen. */
    var cache: List<Match> = emptyList()
        private set

    val hasKey: Boolean get() = BuildConfig.FOOTBALL_DATA_TOKEN.isNotBlank()

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

    suspend fun matches(competition: String): List<Match> {
        val list = if (hasKey) {
            api.matches(competition).matches ?: emptyList()
        } else {
            DemoData.matches(competition)
        }
        cache = list
        return list
    }

    suspend fun standings(competition: String): List<TableRow> {
        return if (hasKey) {
            api.standings(competition)
                .standings
                .firstOrNull { it.type.equals("TOTAL", ignoreCase = true) }
                ?.table
                ?: emptyList()
        } else {
            DemoData.standings()
        }
    }

    /** Previous meetings between the two teams of a match (most recent last from API). */
    suspend fun head2head(matchId: Long): List<Match> {
        return if (hasKey) {
            api.head2head(matchId).matches ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * Recent results for a team, as 'W'/'D'/'L' (most recent first, up to [count]).
     * Computed locally from the competition matches already in [cache] — no extra API call.
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
