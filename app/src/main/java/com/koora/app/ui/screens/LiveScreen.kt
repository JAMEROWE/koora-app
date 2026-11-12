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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
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
import com.koora.app.ui.components.DayEmptyLine
import com.koora.app.ui.components.DayTitle
import com.koora.app.ui.components.ExpandCollapseRow
import com.koora.app.ui.components.LeagueHeaderCard
import com.koora.app.ui.components.MatchDivider
import com.koora.app.ui.components.MatchRow
import com.koora.app.ui.components.SkeletonList
import com.koora.app.ui.components.StadiumHero
import com.koora.app.ui.LeagueSection
import com.koora.app.ui.competitionLabels
import com.koora.app.ui.daySections
import com.koora.app.ui.isLive
import com.koora.app.ui.localDayKeyAt
import com.koora.app.ui.matchesOnDay
import com.koora.app.ui.sectionExpandedDefault
import com.koora.app.ui.sortMixed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
                // ONE shared window (6 days back..day after tomorrow, v1.9.0
                // spec §2.1) — identical keys in Results/Highlights, zero
                // extra calls between tabs.
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
fun LiveScreen(
    onMatchClick: (Long) -> Unit = {},
    onOpenSettings: () -> Unit = {},
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
    // v1.9.0 (spec §2.1): date strip — 0 all, 1 today, 2 tomorrow, 3 day after.
    // v1.10.0: 4 = a day picked from the calendar (still inside the honest
    // -6..+2 window — the picker physically refuses days we cannot fill).
    var dayFilter by remember { mutableIntStateOf(0) }
    var customDayKey by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    // v1.10.0 (spec §2.2): accordion state per section key ("t-sec-PL").
    // Unset keys fall back to the smart default (live/favorite/small blocks
    // expanded). In-memory per visit — a fresh open re-applies the default.
    val sectionExpanded = remember { mutableStateMapOf<String, Boolean>() }
    val onToggleFavorite: (Match) -> Unit = { m ->
        FavoritesStore.toggle(context, m)
        vm.bumpFavorites()
    }

    Column(Modifier.fillMaxSize()) {
        // Spec §1.1: Settings gear floats over the stadium hero, top-right.
        Box {
            StadiumHero()
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.tab_settings),
                    tint = MaterialTheme.colorScheme.onSurface
                )
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

        // v1.9.0 (spec §2.1): quick date selector strip — instant switching
        // between today, tomorrow and the day after. No calendar picker:
        // the honest window is -6..+2 days, and a calendar would promise
        // days we cannot fill.
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
                label = { Text(stringResource(R.string.chip_all), maxLines = 1) }
            )
            FilterChip(
                selected = dayFilter == 1,
                onClick = { dayFilter = 1 },
                label = { Text(stringResource(R.string.day_today), maxLines = 1) }
            )
            FilterChip(
                selected = dayFilter == 2,
                onClick = { dayFilter = 2 },
                label = { Text(stringResource(R.string.day_tomorrow), maxLines = 1) }
            )
            FilterChip(
                selected = dayFilter == 3,
                onClick = { dayFilter = 3 },
                label = { Text(stringResource(R.string.day_after), maxLines = 1) }
            )
            // Spec §2.1 calendar picker — constrained to the fetched window
            // (-6..+2 days). Days outside it are simply not selectable, so
            // the calendar can never promise a day we cannot fill.
            IconButton(onClick = { showDatePicker = true }) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = stringResource(R.string.calendar_pick),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                            dayFilter = 4
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
                val dayAfterTitle = stringResource(R.string.day_after)
                val tomorrowCount = if (vm.demo) 0
                else matchesOnDay(vm.window, vm.tomorrowKey).size
                val todayEmpty = stringResource(R.string.empty_today, tomorrowCount)
                val tomorrowEmpty = stringResource(R.string.empty_tomorrow)
                val dayAfterEmpty = stringResource(R.string.empty_day_after)
                val calendarEmpty = stringResource(R.string.league_results_empty)

                LazyColumn(Modifier.fillMaxSize()) {
                    // v1.10.0 (spec §2.2): the top Expand All / Collapse All
                    // toggle operates on exactly the sections the current
                    // filter shows.
                    item(key = "exp-toggle") {
                        val visibleKeys = visibleSectionKeys(vm, dayFilter, customDayKey)
                        ExpandCollapseRow(
                            onExpandAll = { visibleKeys.forEach { sectionExpanded[it] = true } },
                            onCollapseAll = { visibleKeys.forEach { sectionExpanded[it] = false } }
                        )
                    }
                    favoritesBlock(
                        vm, context, favTitle, onMatchClick,
                        { voteFor = it }, { teamFor = it }, onToggleFavorite
                    )
                    if (dayFilter == 0 || dayFilter == 1) {
                        dayBlock(
                            tag = "t",
                            title = todayTitle,
                            emptyText = todayEmpty,
                            sections = daySections(todayMatches(vm)),
                            titles = titles,
                            favIds = favIds,
                            sectionExpanded = sectionExpanded,
                            onMatchClick = onMatchClick,
                            onVote = { voteFor = it },
                            onTeam = { teamFor = it },
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                    // v1.8.0 roadmap P0: native ad card in the fixture feed
                    // (FotMob-style in-feed placement, matches the Results tab).
                    item(key = "ad-native-live") { NativeAdCard() }
                    if (dayFilter == 0 || dayFilter == 2) {
                        dayBlock(
                            tag = "w",
                            title = tomorrowTitle,
                            emptyText = tomorrowEmpty,
                            sections = daySections(tomorrowMatches(vm)),
                            titles = titles,
                            favIds = favIds,
                            sectionExpanded = sectionExpanded,
                            onMatchClick = onMatchClick,
                            onVote = { voteFor = it },
                            onTeam = { teamFor = it },
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                    if (dayFilter == 0 || dayFilter == 3) {
                        dayBlock(
                            tag = "da",
                            title = dayAfterTitle,
                            emptyText = dayAfterEmpty,
                            sections = daySections(dayAfterMatches(vm)),
                            titles = titles,
                            favIds = favIds,
                            sectionExpanded = sectionExpanded,
                            onMatchClick = onMatchClick,
                            onVote = { voteFor = it },
                            onTeam = { teamFor = it },
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                    // v1.10.0 (spec §2.1): a day chosen from the calendar —
                    // one titled block with that day's matches (empty line
                    // when the honest window holds nothing there).
                    if (dayFilter == 4 && customDayKey != null) {
                        dayBlock(
                            tag = "cd",
                            title = prettyDayKey(customDayKey!!),
                            emptyText = calendarEmpty,
                            sections = daySections(matchesOnDay(vm.window, customDayKey!!)),
                            titles = titles,
                            favIds = favIds,
                            sectionExpanded = sectionExpanded,
                            onMatchClick = onMatchClick,
                            onVote = { voteFor = it },
                            onTeam = { teamFor = it },
                            onToggleFavorite = onToggleFavorite
                        )
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

/** Day-After filter honestly reports its empty demo block. */
private fun dayAfterMatches(vm: LiveViewModel): List<Match> =
    if (vm.demo) emptyList()
    else sortMixed(matchesOnDay(vm.window, vm.dayAfterKey))

/** Section keys the current filter shows — drives Expand/Collapse All. */
private fun visibleSectionKeys(vm: LiveViewModel, dayFilter: Int, customDayKey: String?): List<String> {
    val blocks: List<Pair<String, List<Match>>> = when {
        dayFilter == 1 -> listOf("t" to todayMatches(vm))
        dayFilter == 2 -> listOf("w" to tomorrowMatches(vm))
        dayFilter == 3 -> listOf("da" to dayAfterMatches(vm))
        dayFilter == 4 && customDayKey != null ->
            listOf("cd" to matchesOnDay(vm.window, customDayKey))
        else -> listOf(
            "t" to todayMatches(vm),
            "w" to tomorrowMatches(vm),
            "da" to dayAfterMatches(vm)
        )
    }
    return blocks.flatMap { (tag, ms) -> daySections(ms).map { "$tag-sec-${it.code}" } }
}

/** "2026-11-09" -> "Sun, 9 Nov" for the calendar-picked block title. */
private fun prettyDayKey(key: String): String = runCatching {
    val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(key)!!
    SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(d)
}.getOrDefault(key)

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
    else matchesOnDay(all, vm.todayKey) +
        matchesOnDay(all, vm.tomorrowKey) +
        matchesOnDay(all, vm.dayAfterKey)
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

/** One day = title, then a collapsible league section per competition. */
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
    item(key = "$tag-title") { DayTitle(title) }
    if (sections.isEmpty()) {
        item(key = "$tag-empty") { DayEmptyLine(emptyText) }
        return
    }
    sections.forEach { sec ->
        val key = "$tag-sec-${sec.code}"
        val live = sec.matches.any { isLive(it) }
        // Spec §2.2 smart default: live/favorited/small blocks open; the
        // rest stay collapsed until the user expands them.
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
