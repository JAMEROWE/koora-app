package com.koora.app.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/*
 * football-data.org free tier v4 API models (parsed with Gson).
 * Free tier covers 12 competitions incl. Champions League (CL) and Ligue 1 (FL1).
 * Botola Pro is NOT in the free tier -> added later via API-Football upgrade.
 */

data class Team(
    val id: Long,
    val name: String,
    val shortName: String? = null,
    val tla: String? = null
)

data class ScoreDetail(val home: Int? = null, val away: Int? = null)
data class Score(val fullTime: ScoreDetail? = null, val halfTime: ScoreDetail? = null)

/** Competition reference embedded in every match object (v1.5.6 league blocks). */
data class CompetitionRef(
    val id: Long = 0,
    val code: String = "",
    val name: String = ""
)

data class Match(
    val id: Long,
    val utcDate: String,
    val status: String,
    val homeTeam: Team,
    val awayTeam: Team,
    val score: Score,
    val competition: CompetitionRef? = null
)

data class MatchesResponse(val matches: List<Match> = emptyList())

data class Standing(
    val stage: String? = null,
    val type: String,
    val table: List<TableRow> = emptyList()
)

data class TableRow(
    val position: Int,
    val team: Team,
    val playedGames: Int,
    val won: Int,
    val draw: Int,
    val lost: Int,
    val points: Int,
    val goalsFor: Int,
    val goalsAgainst: Int
)

data class StandingsResponse(val standings: List<Standing> = emptyList())

data class H2HResultSet(val count: Int = 0)
data class Head2HeadResponse(
    val resultSet: H2HResultSet = H2HResultSet(),
    val matches: List<Match> = emptyList()
)

interface FootballDataApi {

    @GET("v4/competitions/{code}/matches")
    suspend fun matches(
        @Path("code") competition: String,
        @Query("status") status: String = "SCHEDULED,TIMED,LIVE,IN_PLAY,FINISHED"
    ): MatchesResponse

    /** v1.5.6: ONE call covers every competition for a date window (Today+Tomorrow). */
    @GET("v4/matches")
    suspend fun matchesRange(
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String,
        @Query("status") status: String = "SCHEDULED,TIMED,LIVE,IN_PLAY,FINISHED"
    ): MatchesResponse

    /** v1.5.6: a team's fixtures across all competitions (Teams panel). */
    @GET("v4/teams/{id}/matches")
    suspend fun teamMatches(
        @Path("id") teamId: Long,
        @Query("status") status: String = "SCHEDULED,TIMED,LIVE,IN_PLAY,FINISHED"
    ): MatchesResponse

    @GET("v4/competitions/{code}/standings")
    suspend fun standings(@Path("code") competition: String): StandingsResponse

    @GET("v4/matches/{id}/head2head")
    suspend fun head2head(
        @Path("id") matchId: Long,
        @Query("limit") limit: Int = 10
    ): Head2HeadResponse
}
