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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koora.app.BuildConfig
import com.koora.app.R
import com.koora.app.ads.AdBanner
import com.koora.app.ads.NativeAdCard
import com.koora.app.data.FavoritesStore
import com.koora.app.data.Match
import com.koora.app.data.MatchRepository
import com.koora.app.data.Team
import com.koora.app.ui.LeagueSection
import com.koora.app.ui.components.DayEmptyLine
import com.koora.app.ui.components.DayTitle
import com.koora.app.ui.components.LeagueHeader
import com.koora.app.ui.components.MatchDivider
import com.koora.app.ui.components.MatchRow
import com.koora.app.ui.components.SkeletonList
import com.koora.app.ui.components.StadiumHero
import com.koora.app.ui.competitionLabels
import com.koora.app.ui.daySections
import com.koora.app.ui.localDayKeyAt
import com.koora.app.ui.matchesOnDay
import com.koora.app.ui.sortMixed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * LIVE TAB (v1.6.0) — what the owner defined "live" to mean:
 *   - TODAY and TOMORROW, one after the other, no switch needed;
 *   - every match shown UNDER THE LEAGUE IT BELONGS TO (green-barred
 *     section headers with the real league name and a match count —
 *     never a flat, unlabeled list);
 *   - every match carries the POLL (tap -> vote panel, locked at
 *     kick-off) and the full feature set (finished -> full match page
 *     with statistics; team circle -> team panel with next 5 fixtures
 *     and last 5 results, each labeled with its league);
 *   - ONE shared window fetch (yesterday..tomorrow, per league, free
 *     tier rate-limit aware) also feeds the Results tab;
 *   - auto-refresh every 60s while visible and in the foreground;
 *   - the floodlit StadiumHero is the app's new professional front.
 */
class LiveViewModel : ViewModel() {
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
    var tomorrowKey by mutableStateOf("")
        private set
    var favsTick by mutableIntStateOf(0)
        private set
    var updatedAt by mutableStateOf(0L)
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
            tomorrowKey = localDayKeyAt(now, 1)
            try {
                // ONE shared window (6 days back..tomorrow) — identical keys in
                // the Results tab, zero extra calls between the two tabs; the
                // 6-day depth is what fills Results with the last matchday.
                window = MatchRepository.matchesWindow(
                    localDayKeyAt(now, -6), tomorrowKey, force = force
                )
                errorMessage = null
                updatedAt = System.currentTimeMillis()
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
fun LiveScreen(
    onMatchClick: (Long) -> Unit = {},
    vm: LiveViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            foreground = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-refresh: 60s tick, fires only in the foreground. The repository
    // caps each round to 6 league fetches, so the free quota holds.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            if (foreground) vm.load(force = true)
        }
    }

    var voteFor by remember { mutableStateOf<Match?>(null) }
    var teamFor by remember { mutableStateOf<Team?>(null) }
    val onToggleFavorite: (Match) -> Unit = { m ->
        FavoritesStore.toggle(context, m)
        vm.bumpFavorites()
    }

    Column(Modifier.fillMaxSize()) {
        StadiumHero()

        if (vm.demo) {
            Text(
                stringResource(R.string.demo_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Trust caption: when the window was last refreshed.
        if (vm.updatedAt > 0L) {
            Text(
                stringResource(R.string.updated_at, hhmm(vm.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

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
                val favTitle = stringResource(R.string.fav_tab)
                val todayTitle = stringResource(R.string.day_today)
                val tomorrowTitle = stringResource(R.string.day_tomorrow)
                val tomorrowCount = if (vm.demo) 0
                else matchesOnDay(vm.window, vm.tomorrowKey).size
                val todayEmpty = stringResource(R.string.empty_today, tomorrowCount)
                val tomorrowEmpty = stringResource(R.string.empty_tomorrow)

                LazyColumn(Modifier.fillMaxSize()) {
                    favoritesBlock(
                        vm, context, favTitle, onMatchClick,
                        { voteFor = it }, { teamFor = it }, onToggleFavorite
                    )
                    dayBlock(
                        tag = "t",
                        title = todayTitle,
                        emptyText = todayEmpty,
                        sections = daySections(todayMatches(vm)),
                        titles = titles,
                        favIds = favIds,
                        onMatchClick = onMatchClick,
                        onVote = { voteFor = it },
                        onTeam = { teamFor = it },
                        onToggleFavorite = onToggleFavorite
                    )
                    // v1.8.0 roadmap P0: native ad card in the fixture feed
                    // (FotMob-style in-feed placement, matches the Results tab).
                    item(key = "ad-native-live") { NativeAdCard() }
                    dayBlock(
                        tag = "w",
                        title = tomorrowTitle,
                        emptyText = tomorrowEmpty,
                        sections = daySections(tomorrowMatches(vm)),
                        titles = titles,
                        favIds = favIds,
                        onMatchClick = onMatchClick,
                        onVote = { voteFor = it },
                        onTeam = { teamFor = it },
                        onToggleFavorite = onToggleFavorite
                    )
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

        AdBanner()
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

/** Demo keeps every sample match on "Today" (CI + keyless never empty). */
private fun todayMatches(vm: LiveViewModel): List<Match> =
    if (vm.demo) sortMixed(vm.window)
    else sortMixed(matchesOnDay(vm.window, vm.todayKey))

private fun tomorrowMatches(vm: LiveViewModel): List<Match> =
    if (vm.demo) emptyList()
    else sortMixed(matchesOnDay(vm.window, vm.tomorrowKey))

// ---- LazyColumn blocks ------------------------------------------------------

private fun LazyListScope.favoritesBlock(
    vm: LiveViewModel,
    context: android.content.Context,
    title: String,
    onMatchClick: (Long) -> Unit,
    onVote: (Match) -> Unit,
    onTeam: (Team) -> Unit,
    onToggleFavorite: (Match) -> Unit
) {
    val all = FavoritesStore.all(context).map { FavoritesStore.toMatch(it) }
    val favs = if (vm.demo) all
    else matchesOnDay(all, vm.todayKey) + matchesOnDay(all, vm.tomorrowKey)
    if (favs.isEmpty()) return
    item(key = "fav-header") { DayTitle(title) }
    items(favs, key = { "fav-${it.id}" }) { m ->
        MatchRow(
            m = m,
            isFavorite = true,
            onToggleFavorite = { onToggleFavorite(m) },
            onMatch = { rowTap(m, onMatchClick, onVote) },
            onTeam = onTeam
        )
        MatchDivider()
    }
}

/** One day = title, then a league section per competition present. */
private fun LazyListScope.dayBlock(
    tag: String,
    title: String,
    emptyText: String,
    sections: List<LeagueSection>,
    titles: Map<String, String>,
    favIds: Set<Long>,
    onMatchClick: (Long) -> Unit,
    onVote: (Match) -> Unit,
    onTeam: (Team) -> Unit,
    onToggleFavorite: (Match) -> Unit
) {
    item(key = "$tag-title") { DayTitle(title) }
    if (sections.isEmpty()) {
        item(key = "$tag-empty") { DayEmptyLine(emptyText) }
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
                onMatch = { rowTap(m, onMatchClick, onVote) },
                onTeam = onTeam
            )
            MatchDivider()
        }
    }
}

/** Finished -> full match page with statistics; otherwise -> the poll. */
private fun rowTap(m: Match, onMatchClick: (Long) -> Unit, onVote: (Match) -> Unit) {
    if (m.status.equals("FINISHED", true)) onMatchClick(m.id) else onVote(m)
}

private fun hhmm(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
