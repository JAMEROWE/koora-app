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
            api.matches(competition).matches
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
}
