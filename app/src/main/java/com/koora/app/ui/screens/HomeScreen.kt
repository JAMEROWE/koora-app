package com.koora.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
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
import com.koora.app.ui.competitionLabels
import com.koora.app.ui.daySections
import com.koora.app.ui.localDayKeyAt
import com.koora.app.ui.matchesOnDay
import com.koora.app.ui.searchMatches
import com.koora.app.ui.sectionExpandedDefault
import com.koora.app.ui.sortMixed
import com.koora.app.ui.components.DayEmptyLine
import com.koora.app.ui.components.ExpandCollapseRow
import com.koora.app.ui.components.LeagueHeaderCard
import com.koora.app.ui.components.MatchDivider
import com.koora.app.ui.components.MatchRow
import com.koora.app.ui.components.SkeletonList
import com.koora.app.ui.components.StadiumHero
import com.koora.app.ui.isLive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * HOME — the matches-first screen (post-review direction, approved by the
 * owner on 24 Nov):
 *   - ONE Matches home. There is no separate Live tab and no separate
 *     Live filter chip: TODAY already contains today's live matches,
 *     sorted first with their LIVE badge.
 *   - Day chips (Today / Tomorrow / Day after) plus the calendar: the
 *     selected day shows ONLY that day — picking Today never renders a
 *     Tomorrow section underneath it. The calendar still covers the
 *     honest -6..+2 window, so recent results stay reachable by day.
 *   - Favorite matches of the SELECTED day pin to the top; favorites of
 *     other days stay on their own day (same rule as the sections).
 *   - League sections keep the smart accordion default (live/favorite/
 *     small blocks open), full two-line team names, one kickoff time per
 *     row.
 *   - Team & league search over the loaded window (magnifier, top bar).
 *   - The StadiumHero stays as the identity front, slimmed so the first
 *     matches sit closer to the top.
 * Data: the SAME shared window fetch as before (-6..+2, per league,
 * free-quota aware), auto-refresh every 60s in the foreground.
 */
class HomeViewModel : ViewModel() {
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
    var dayAfterKey by mutableStateOf("")
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
            dayAfterKey = localDayKeyAt(now, 2)
            try {
                window = MatchRepository.matchesWindow(
                    localDayKeyAt(now, -6), dayAfterKey, force = force
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
fun HomeScreen(
    onMatchClick: (Long) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    vm: HomeViewModel = viewModel()
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
    // 0 today (default), 1 tomorrow, 2 day after, 3 a calendar-picked day.
    var dayFilter by remember { mutableIntStateOf(0) }
    var customDayKey by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val sectionExpanded = remember { mutableStateMapOf<String, Boolean>() }
    val onToggleFavorite: (Match) -> Unit = { m ->
        FavoritesStore.toggle(context, m)
        vm.bumpFavorites()
    }

    Column(Modifier.fillMaxSize()) {
        // Slim identity banner; Settings gear + Search top-right.
        Box {
            StadiumHero(modifier = Modifier.height(110.dp))
            Row(Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    },
                    modifier = Modifier.testTag("home_search_toggle")
                ) {
                    Icon(
                        if (searching) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = stringResource(R.string.search_label),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.tab_settings),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

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

        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("home_search_field")
            )
        } else {
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

            // Day chips: exactly one day at a time. Today already carries
            // the live matches — there is deliberately no Live chip.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = dayFilter == 0,
                    onClick = { dayFilter = 0 },
                    label = { Text(stringResource(R.string.day_today), maxLines = 1) }
                )
                FilterChip(
                    selected = dayFilter == 1,
                    onClick = { dayFilter = 1 },
                    label = { Text(stringResource(R.string.day_tomorrow), maxLines = 1) }
                )
                FilterChip(
                    selected = dayFilter == 2,
                    onClick = { dayFilter = 2 },
                    label = { Text(stringResource(R.string.day_after), maxLines = 1) }
                )
                // Calendar: constrained to the fetched window (-6..+2 days).
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = stringResource(R.string.calendar_pick),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showDatePicker) {
            val windowKeys = remember {
                val now = System.currentTimeMillis()
                (-6..2).map { localDayKeyAt(now, it) }.toSet()
            }
            val pickerState = rememberDatePickerState(
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcMillis: Long): Boolean {
                        val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }.format(Date(utcMillis))
                        return key in windowKeys
                    }
                }
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val ms = pickerState.selectedDateMillis
                        if (ms != null) {
                            customDayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }.format(Date(ms))
                            dayFilter = 3
                        }
                        showDatePicker = false
                    }) { Text(stringResource(R.string.date_pick_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(stringResource(R.string.panel_close))
                    }
                }
            ) {
                DatePicker(state = pickerState)
            }
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

            searching -> SearchResults(
                vm = vm,
                query = query,
                onMatchClick = onMatchClick,
                onVote = { voteFor = it },
                onTeam = { teamFor = it },
                onToggleFavorite = onToggleFavorite
            )

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
                val (selectedKey, selectedTitle, selectedEmpty) = selectedDay(vm, dayFilter, customDayKey)
                val dayMatches = remember(vm.window, vm.favsTick, selectedKey, vm.demo) {
                    dayMatchesFor(vm, selectedKey)
                }

                // testTag: LaunchTest scrolls THIS list before asserting
                // below-the-fold items (Robolectric's tiny window).
                LazyColumn(Modifier.fillMaxSize().testTag("home_feed")) {
                    item(key = "exp-toggle") {
                        val visibleKeys = dayMatchesFor(vm, selectedKey)
                            .let { daySections(it).map { sec -> "h-sec-${sec.code}" } }
                        ExpandCollapseRow(
                            onExpandAll = { visibleKeys.forEach { sectionExpanded[it] = true } },
                            onCollapseAll = { visibleKeys.forEach { sectionExpanded[it] = false } }
                        )
                    }
                    favoritesBlock(
                        vm = vm,
                        context = context,
                        dayKey = selectedKey,
                        title = favTitle,
                        onMatchClick = onMatchClick,
                        onVote = { voteFor = it },
                        onTeam = { teamFor = it },
                        onToggleFavorite = onToggleFavorite
                    )
                    // Native ad card stays in-feed (placement unchanged).
                    item(key = "ad-native-home") { NativeAdCard() }
                    dayBlock(
                        tag = "h",
                        title = selectedTitle,
                        emptyText = selectedEmpty,
                        sections = daySections(dayMatches),
                        titles = titles,
                        favIds = favIds,
                        sectionExpanded = sectionExpanded,
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

// ---- Search ---------------------------------------------------------------

@Composable
private fun SearchResults(
    vm: HomeViewModel,
    query: String,
    onMatchClick: (Long) -> Unit,
    onVote: (Match) -> Unit,
    onTeam: (Team) -> Unit,
    onToggleFavorite: (Match) -> Unit
) {
    val context = LocalContext.current
    val favIds = remember(vm.favsTick) { FavoritesStore.all(context).map { it.id }.toSet() }
    val results = remember(vm.window, query) { searchMatches(vm.window, query) }
    LazyColumn(Modifier.fillMaxSize().testTag("home_search_results")) {
        if (query.trim().length >= 2 && results.isEmpty()) {
            item(key = "search-empty") {
                DayEmptyLine(stringResource(R.string.search_none, query.trim()))
            }
        }
        items(results, key = { "s-${it.id}" }) { m ->
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

// ---- Data shaping ----------------------------------------------------------

/** Demo keeps every sample match on "Today" (CI + keyless never empty). */
private fun dayMatchesFor(vm: HomeViewModel, dayKey: String): List<Match> =
    if (vm.demo && dayKey == vm.todayKey) sortMixed(vm.window)
    else sortMixed(matchesOnDay(vm.window, dayKey))

private data class SelectedDay(val key: String, val title: String, val empty: String)

@Composable
private fun selectedDay(vm: HomeViewModel, dayFilter: Int, customDayKey: String?): SelectedDay {
    val tomorrowCount = if (vm.demo) 0 else matchesOnDay(vm.window, vm.tomorrowKey).size
    return when (dayFilter) {
        1 -> SelectedDay(
            vm.tomorrowKey,
            stringResource(R.string.day_tomorrow),
            stringResource(R.string.empty_tomorrow)
        )
        2 -> SelectedDay(
            vm.dayAfterKey,
            stringResource(R.string.day_after),
            stringResource(R.string.empty_day_after)
        )
        3 -> SelectedDay(
            customDayKey ?: vm.todayKey,
            prettyDayKey(customDayKey ?: vm.todayKey),
            stringResource(R.string.league_results_empty)
        )
        else -> SelectedDay(
            vm.todayKey,
            stringResource(R.string.day_today),
            stringResource(R.string.empty_today, tomorrowCount)
        )
    }
}

/** "2026-11-09" -> "Sun, 9 Nov" for the calendar-picked block title. */
private fun prettyDayKey(key: String): String = runCatching {
    val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(key)!!
    SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(d)
}.getOrDefault(key)

// ---- LazyColumn blocks ------------------------------------------------------

/** Favorites of the SELECTED day only — other days stay on their own day. */
private fun LazyListScope.favoritesBlock(
    vm: HomeViewModel,
    context: android.content.Context,
    dayKey: String,
    title: String,
    onMatchClick: (Long) -> Unit,
    onVote: (Match) -> Unit,
    onTeam: (Team) -> Unit,
    onToggleFavorite: (Match) -> Unit
) {
    val all = FavoritesStore.all(context).map { FavoritesStore.toMatch(it) }
    val favs = if (vm.demo && dayKey == vm.todayKey) all else matchesOnDay(all, dayKey)
    if (favs.isEmpty()) return
    item(key = "fav-header") {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
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

/** The selected day = title, then a collapsible league section per competition. */
private fun LazyListScope.dayBlock(
    tag: String,
    title: String,
    emptyText: String,
    sections: List<LeagueSection>,
    titles: Map<String, String>,
    favIds: Set<Long>,
    sectionExpanded: SnapshotStateMap<String, Boolean>,
    onMatchClick: (Long) -> Unit,
    onVote: (Match) -> Unit,
    onTeam: (Team) -> Unit,
    onToggleFavorite: (Match) -> Unit
) {
    item(key = "$tag-title") {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
    if (sections.isEmpty()) {
        item(key = "$tag-empty") { DayEmptyLine(emptyText) }
        return
    }
    sections.forEach { sec ->
        val key = "$tag-sec-${sec.code}"
        val live = sec.matches.any { isLive(it) }
        val expanded = sectionExpanded[key] ?: sectionExpandedDefault(
            hasLive = live,
            hasFavorite = sec.matches.any { it.id in favIds },
            sectionsInBlock = sections.size
        )
        item(key = "$key-header") {
            LeagueHeaderCard(
                title = titles[sec.code] ?: competitionLabels[sec.code] ?: sec.code,
                count = sec.matches.size,
                live = live,
                expanded = expanded,
                onToggle = { sectionExpanded[key] = !expanded }
            )
        }
        if (expanded) {
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
}

/** Finished -> full match page with statistics; otherwise -> the poll. */
private fun rowTap(m: Match, onMatchClick: (Long) -> Unit, onVote: (Match) -> Unit) {
    if (m.status.equals("FINISHED", true)) onMatchClick(m.id) else onVote(m)
}

private fun hhmm(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
