package com.koora.app.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koora.app.BuildConfig
import com.koora.app.R
import com.koora.app.ads.NativeAdCard
import com.koora.app.data.FavoritesStore
import com.koora.app.data.Match
import com.koora.app.data.MatchRepository
import com.koora.app.data.Team
import com.koora.app.ui.components.LivePulseDot
import com.koora.app.ui.components.SkeletonList
import com.koora.app.ui.components.TeamBadge
import com.koora.app.ui.isLive
import com.koora.app.ui.localDayKeyAt
import com.koora.app.ui.matchesOnDay
import com.koora.app.ui.scoreLabel
import com.koora.app.ui.sortMixed
import com.koora.app.ui.statusLabel
import com.koora.app.ui.theme.LiveRed
import kotlinx.coroutines.launch

data class Competition(val code: String, val label: String)

/** The 13 real competitions (shared by Matches + Live screens). */
val ALL_COMPETITIONS = listOf(
    Competition("CL", "Champions League"),
    Competition("PL", "Premier League"),
    Competition("PD", "La Liga"),
    Competition("SA", "Serie A"),
    Competition("BL1", "Bundesliga"),
    Competition("FL1", "Ligue 1"),
    Competition("ELC", "Championship"),
    Competition("DED", "Eredivisie"),
    Competition("PPL", "Primeira Liga"),
    Competition("BSA", "Brasileirão"),
    Competition("CLI", "Copa Libertadores"),
    Competition("WC", "World Cup"),
    Competition("EC", "Euro")
    // Botola Pro joins here once we upgrade to API-Football
)

private val competitionLabels = ALL_COMPETITIONS.associate { it.code to it.label }

/*
 * v1.5.6 "COMFORTABLE MATCHES" — final IA (owner feedback: league
 * sections must BE the screen, not buried below a flat list):
 *   - Top: Today | Tomorrow switch (client-side filter, zero extra calls).
 *   - The selected day grouped into LEAGUE SECTIONS with the real league
 *     names from the data; inside a section: live first, then upcoming
 *     by kickoff, then newest results — results always sit under their
 *     league header.
 *   - Favorites block on top when non-empty; one native ad after the
 *     first section.
 *   - Taps: finished -> full match page; upcoming/live -> vote panel;
 *     team circle -> team panel. Data: v4/matches?dateFrom&dateTo (ONE
 *     call covers both days); offline -> saved copy + "updated X min ago".
 * Demo mode shows all sample matches in one day section (never empty
 * for CI).
 */
class MatchesViewModel(app: Application) : AndroidViewModel(app) {

    var tomorrow by mutableStateOf(false)
        private set
    var loading by mutableStateOf(true)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var stale by mutableStateOf(false)
        private set
    var staleMinutes by mutableIntStateOf(0)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var matches by mutableStateOf<List<Match>>(emptyList())
        private set
    var todayKey by mutableStateOf("")
        private set
    var tomorrowKey by mutableStateOf("")
        private set
    var favsTick by mutableIntStateOf(0)
        private set

    val demo: Boolean get() = !MatchRepository.hasKey

    init {
        load()
    }

    fun setDay(toTomorrow: Boolean) {
        tomorrow = toTomorrow
    }

    fun bumpFavorites() {
        favsTick++
    }

    fun refresh() = load(viaPull = true)

    fun load(viaPull: Boolean = false) {
        viewModelScope.launch {
            if (viaPull) refreshing = true
            val now = System.currentTimeMillis()
            todayKey = localDayKeyAt(now, 0)
            tomorrowKey = localDayKeyAt(now, 1)
            try {
                val (list, wasStale) =
                    MatchRepository.matchesRange(todayKey, tomorrowKey, force = viaPull)
                matches = list
                stale = wasStale
                staleMinutes = if (wasStale) {
                    ((now - MatchRepository.lastUpdated("range:$todayKey:$tomorrowKey")) / 60000L)
                        .toInt().coerceAtLeast(1)
                } else 0
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

    // Panels live here so any row can open them.
    var voteFor by remember { mutableStateOf<Match?>(null) }
    var teamFor by remember { mutableStateOf<Team?>(null) }

    Column(Modifier.fillMaxSize()) {
        if (!MatchRepository.hasKey) {
            Text(
                stringResource(R.string.demo_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // 1) Day switch — Today | Tomorrow, one tap changes the list below.
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = !vm.tomorrow,
                onClick = { vm.setDay(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text(stringResource(R.string.day_today)) }
            SegmentedButton(
                selected = vm.tomorrow,
                onClick = { vm.setDay(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text(stringResource(R.string.day_tomorrow)) }
        }

        // Resilience caption: data came from cache after a network failure.
        if (vm.stale) {
            Text(
                stringResource(R.string.stale_updated, vm.staleMinutes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        when {
            vm.loading -> SkeletonList(rows = 9, modifier = Modifier.fillMaxSize())

            vm.errorMessage != null && vm.matches.isEmpty() -> Box(
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
                DayMatchesContent(
                    vm = vm,
                    onMatchClick = onMatchClick,
                    onVote = { voteFor = it },
                    onTeam = { teamFor = it },
                    onToggleFavorite = { m ->
                        FavoritesStore.toggle(context, m)
                        vm.bumpFavorites()
                    }
                )
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
        TeamPanel(team = t, onDismiss = { teamFor = null })
    }
}

@Composable
private fun DayMatchesContent(
    vm: MatchesViewModel,
    onMatchClick: (Long) -> Unit,
    onVote: (Match) -> Unit,
    onTeam: (Team) -> Unit,
    onToggleFavorite: (Match) -> Unit
) {
    val context = LocalContext.current

    val selectedKey = if (vm.tomorrow) vm.tomorrowKey else vm.todayKey
    // Demo mode always shows the full sample list (CI + keyless installs
    // must never hit an empty "today" just because of the wall clock).
    val dayList = if (vm.demo) sortMixed(vm.matches) else sortMixed(matchesOnDay(vm.matches, selectedKey))

    // Real league names straight from the window data — section titles are
    // the provider's own names, even for a competition that is not in
    // ALL_COMPETITIONS (never a raw code like "FL1").
    val leagueTitles = remember(vm.matches) {
        vm.matches.mapNotNull { m ->
            val c = m.competition ?: return@mapNotNull null
            if (c.code.isBlank() || c.name.isBlank()) null else c.code to c.name
        }.toMap()
    }

    // THE SECTIONS: the selected day grouped by league, ALL_COMPETITIONS
    // order first (CL, PL, PD, ...), unknown leagues after. Demo data
    // carries no competition and lands in one section titled with the day.
    val sections = remember(dayList, leagueTitles) {
        dayList.groupBy { it.competition?.code ?: "" }
            .map { (code, ms) ->
                Triple(
                    code,
                    leagueTitles[code] ?: competitionLabels[code] ?: "",
                    sortMixed(ms)
                )
            }
            .sortedBy { (code, _, _) ->
                when {
                    code.isEmpty() -> -1
                    else -> ALL_COMPETITIONS.indexOfFirst { it.code == code }
                        .let { if (it >= 0) it else 999 }
                }
            }
    }

    // Favorites (starred matches) for the selected day — no API cost.
    val favs = remember(vm.favsTick, vm.matches.size, selectedKey) {
        val all = FavoritesStore.all(context).map { FavoritesStore.toMatch(it) }
        (if (vm.demo) all else matchesOnDay(all, selectedKey))
    }

    LazyColumn(Modifier.fillMaxSize()) {
        // ---- Favorites on top -------------------------------------------
        if (favs.isNotEmpty()) {
            item(key = "fav-header") {
                BlockTitle(stringResource(R.string.fav_tab))
            }
            items(favs, key = { "fav-${it.id}" }) { m ->
                MatchRow(
                    m = m,
                    isFavorite = true,
                    onToggleFavorite = { onToggleFavorite(m) },
                    onMatch = {
                        if (m.status.equals("FINISHED", true)) onMatchClick(m.id) else onVote(m)
                    },
                    onTeam = onTeam
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        }

        // ---- League sections for the selected day ------------------------
        if (dayList.isEmpty()) {
            item(key = "day-empty") {
                val tomorrowCount = matchesOnDay(vm.matches, vm.tomorrowKey).size
                Text(
                    if (vm.tomorrow) stringResource(R.string.empty_tomorrow)
                    else stringResource(R.string.empty_today, tomorrowCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            sections.forEachIndexed { sIdx, (code, title, rows) ->
                item(key = "sec-$code") {
                    LeagueHeader(
                        title = title.ifEmpty {
                            stringResource(if (vm.tomorrow) R.string.day_tomorrow else R.string.day_today)
                        },
                        count = rows.size
                    )
                }
                items(rows, key = { "m-$code-${it.id}" }) { m ->
                    MatchRow(
                        m = m,
                        isFavorite = FavoritesStore.isFavorite(context, m.id),
                        onToggleFavorite = { onToggleFavorite(m) },
                        onMatch = {
                            if (m.status.equals("FINISHED", true)) onMatchClick(m.id) else onVote(m)
                        },
                        onTeam = onTeam
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
                if (sIdx == 0) {
                    // Moderate native ad placement (policy: in-list, never blocking)
                    item(key = "native-ad") {
                        NativeAdCard()
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
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

@Composable
private fun BlockTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

/**
 * Section header: green brand bar + real league name + match count.
 * This is what makes results and fixtures clearly grouped per league.
 */
@Composable
private fun LeagueHeader(title: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 18.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MatchRow(
    m: Match,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onMatch: () -> Unit,
    onTeam: (Team) -> Unit,
    showStar: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onMatch)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TeamBadge(
            m.homeTeam.name,
            m.homeTeam.tla,
            26.dp,
            modifier = Modifier.clickable(onClick = { onTeam(m.homeTeam) })
        )
        Spacer(Modifier.width(8.dp))
        Text(
            m.homeTeam.name,
            Modifier.weight(1.1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(88.dp)
        ) {
            Text(
                scoreLabel(m),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLive(m)) {
                    LivePulseDot()
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    statusLabel(m),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLive(m)) LiveRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            m.awayTeam.name,
            Modifier.weight(1.1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.width(8.dp))
        TeamBadge(
            m.awayTeam.name,
            m.awayTeam.tla,
            26.dp,
            modifier = Modifier.clickable(onClick = { onTeam(m.awayTeam) })
        )
        if (showStar) {
            IconToggleButton(
                checked = isFavorite,
                onCheckedChange = { onToggleFavorite() }
            ) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = if (isFavorite) Color(0xFFF5B301) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
