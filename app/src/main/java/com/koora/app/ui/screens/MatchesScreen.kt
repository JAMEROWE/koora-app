package com.koora.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koora.app.BuildConfig
import com.koora.app.R
import com.koora.app.ads.NativeAdCard
import com.koora.app.data.FavoritesStore
import com.koora.app.data.Match
import com.koora.app.data.MatchRepository
import com.koora.app.data.Team
import com.koora.app.ui.LeagueSection
import com.koora.app.ui.components.LeagueHeader
import com.koora.app.ui.components.MatchDivider
import com.koora.app.ui.components.MatchRow
import com.koora.app.ui.components.SkeletonList
import com.koora.app.ui.competitionLabels
import com.koora.app.ui.daySections
import com.koora.app.ui.localDayKey
import com.koora.app.ui.localDayKeyAt
import com.koora.app.ui.sortResultsNewestFirst
import kotlinx.coroutines.launch

/*
 * MATCHES TAB (v1.6.0) — now the RESULTS screen, as the owner defined it:
 *   - finished matches from yesterday + today, newest first, grouped
 *     UNDER THEIR LEAGUE (green-barred headers, real league names);
 *   - tapping a result opens the full match page with the complete
 *     statistics (events, lineups, bars, h2h, highlights);
 *   - tapping a team circle opens the team panel: next 5 fixtures (each
 *     labeled with its competition) and last 5 results; tapping one of
 *     those upcoming fixtures opens the prematch poll / its match page;
 *   - data comes from the SAME shared window fetch as the Live tab —
 *     no extra API calls.
 */
class MatchesViewModel : ViewModel() {
    var loading by mutableStateOf(true)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var window by mutableStateOf<List<Match>>(emptyList())
        private set
    var todayKey by mutableStateOf("")
        private set
    var yesterdayKey by mutableStateOf("")
        private set
    var favsTick by mutableIntStateOf(0)
        private set

    val demo: Boolean get() = !MatchRepository.hasKey

    init {
        load()
    }

    fun bumpFavorites() {
        favsTick++
    }

    fun refresh() = load(force = true)

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            if (force) refreshing = true
            val now = System.currentTimeMillis()
            todayKey = localDayKeyAt(now, 0)
            yesterdayKey = localDayKeyAt(now, -1)
            try {
                // v1.6.1: EXACTLY the Live tab's window (-6 days..tomorrow) so
                // both tabs share one per-league cache (identical keys = zero
                // duplicate calls). The 6-day depth is what makes finished
                // results from the last matchday actually appear.
                window = MatchRepository.matchesWindow(
                    localDayKeyAt(now, -6), localDayKeyAt(now, 1), force = force
                )
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message ?: "Network error"
            }
            loading = false
            refreshing = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(onMatchClick: (Long) -> Unit, vm: MatchesViewModel = viewModel()) {
    val context = LocalContext.current

    var voteFor by remember { mutableStateOf<Match?>(null) }
    var teamFor by remember { mutableStateOf<Team?>(null) }
    val onToggleFavorite: (Match) -> Unit = { m ->
        FavoritesStore.toggle(context, m)
        vm.bumpFavorites()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.tab_results)) })

        when {
            vm.loading && vm.window.isEmpty() ->
                SkeletonList(rows = 9, modifier = Modifier.fillMaxSize())

            vm.errorMessage != null && vm.window.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.error_generic))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        vm.errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.load() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            else -> PullToRefreshBox(
                isRefreshing = vm.refreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                val favIds = remember(vm.favsTick, vm.window.size) {
                    FavoritesStore.all(context).map { it.id }.toSet()
                }
                val titles = remember(vm.window) {
                    vm.window.mapNotNull { m ->
                        val c = m.competition ?: return@mapNotNull null
                        if (c.code.isBlank() || c.name.isBlank()) null else c.code to c.name
                    }.toMap()
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    resultsBlock(
                        tag = "r",
                        sections = daySections(finishedMatches(vm)),
                        titles = titles,
                        favIds = favIds,
                        onMatchClick = onMatchClick,
                        onTeam = { teamFor = it },
                        onToggleFavorite = onToggleFavorite
                    )
                    item(key = "ad") {
                        // Moderate native ad placement (policy: in-list)
                        NativeAdCard()
                        MatchDivider()
                    }
                    item(key = "version") {
                        Text(
                            stringResource(R.string.app_version_line, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    voteFor?.let { m ->
        VotePanel(
            match = m,
            onDismiss = { voteFor = null },
            onFullPage = { id -> onMatchClick(id) }
        )
    }
    teamFor?.let { t ->
        TeamPanel(
            team = t,
            onDismiss = { teamFor = null },
            onMatch = { id ->
                teamFor = null
                onMatchClick(id)
            },
            onVote = { m ->
                teamFor = null
                voteFor = m
            }
        )
    }
}

// ---- Data shaping ----------------------------------------------------------

/** Finished matches of yesterday + today, newest first. */
private fun finishedMatches(vm: MatchesViewModel): List<Match> =
    sortResultsNewestFirst(
        vm.window.filter { m ->
            m.status.equals("FINISHED", true) &&
                (localDayKey(m) == vm.todayKey || localDayKey(m) == vm.yesterdayKey)
        }
    )

// ---- LazyColumn block --------------------------------------------------------

private fun LazyListScope.resultsBlock(
    tag: String,
    sections: List<LeagueSection>,
    titles: Map<String, String>,
    favIds: Set<Long>,
    onMatchClick: (Long) -> Unit,
    onTeam: (Team) -> Unit,
    onToggleFavorite: (Match) -> Unit
) {
    if (sections.isEmpty()) {
        item(key = "$tag-empty") {
            Text(
                stringResource(R.string.league_results_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            )
        }
        return
    }
    sections.forEach { sec ->
        item(key = "$tag-sec-${sec.code}") {
            LeagueHeader(
                title = titles[sec.code] ?: competitionLabels[sec.code] ?: sec.code,
                count = sec.matches.size
            )
        }
        items(sec.matches, key = { "$tag-m-${sec.code}-${it.id}" }) { m ->
            MatchRow(
                m = m,
                isFavorite = m.id in favIds,
                onToggleFavorite = { onToggleFavorite(m) },
                onMatch = { onMatchClick(m.id) },
                onTeam = onTeam
            )
            MatchDivider()
        }
    }
}
