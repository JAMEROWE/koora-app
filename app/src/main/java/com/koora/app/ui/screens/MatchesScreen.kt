package com.koora.app.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koora.app.BuildConfig
import com.koora.app.R
import com.koora.app.ads.NativeAdCard
import com.koora.app.data.FavoritesStore
import com.koora.app.data.Match
import com.koora.app.data.MatchRepository
import com.koora.app.ui.components.LivePulseDot
import com.koora.app.ui.components.SkeletonList
import com.koora.app.ui.components.TeamBadge
import com.koora.app.ui.isLive
import com.koora.app.ui.pickMatchOfTheDay
import com.koora.app.ui.scoreLabel
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

sealed interface MatchesUiState {
    data object Loading : MatchesUiState
    data class Error(val message: String) : MatchesUiState
    data class Ready(val competition: Competition, val matches: List<Match>) : MatchesUiState
}

class MatchesViewModel(app: Application) : AndroidViewModel(app) {
    val competitions = listOf(Competition("FAV", "★")) + ALL_COMPETITIONS

    var selected by mutableStateOf(competitions[1])
        private set
    var state by mutableStateOf<MatchesUiState>(MatchesUiState.Loading)
        private set
    var refreshing by mutableStateOf(false)
        private set

    init {
        load(competitions[1])
    }

    fun load(c: Competition, viaPull: Boolean = false) {
        selected = c
        if (c.code == "FAV") {
            refreshing = false
            val favs = FavoritesStore.all(getApplication())
                .map { FavoritesStore.toMatch(it) }
            state = MatchesUiState.Ready(c, favs)
            return
        }
        // Pull-to-refresh keeps the visible list; tab switches show skeletons.
        if (!viaPull) state = MatchesUiState.Loading
        viewModelScope.launch {
            if (viaPull) refreshing = true
            state = try {
                MatchesUiState.Ready(c, MatchRepository.matches(c.code, force = viaPull))
            } catch (e: Exception) {
                val prev = state
                if (viaPull && prev is MatchesUiState.Ready) prev
                else MatchesUiState.Error(e.message ?: "Network error")
            }
            refreshing = false
        }
    }

    /** Re-read favorites after a star toggle while the Favorites tab is open. */
    fun refreshFavorites() {
        if (selected.code == "FAV") {
            val favs = FavoritesStore.all(getApplication())
                .map { FavoritesStore.toMatch(it) }
            state = MatchesUiState.Ready(selected, favs)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(onMatchClick: (Long) -> Unit, vm: MatchesViewModel = viewModel()) {
    val context = LocalContext.current
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

        ScrollableTabRow(selectedTabIndex = vm.competitions.indexOf(vm.selected)) {
            vm.competitions.forEach { c ->
                Tab(
                    selected = c.code == vm.selected.code,
                    onClick = { vm.load(c) },
                    text = {
                        Text(
                            if (c.code == "FAV") stringResource(R.string.fav_tab) else c.label
                        )
                    }
                )
            }
        }

        // Resilience caption: data came from cache after a network failure.
        val staleKey = "matches:${vm.selected.code}"
        if (MatchRepository.isStale(staleKey) && MatchRepository.lastUpdated(staleKey) > 0L) {
            val minutes = ((System.currentTimeMillis() -
                MatchRepository.lastUpdated(staleKey)) / 60000L).toInt().coerceAtLeast(1)
            Text(
                stringResource(R.string.stale_updated, minutes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        when (val s = vm.state) {
            is MatchesUiState.Loading -> SkeletonList(rows = 9, modifier = Modifier.fillMaxSize())

            is MatchesUiState.Error -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.error_generic))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.load(vm.selected) }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            is MatchesUiState.Ready -> if (s.matches.isEmpty()) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(
                            if (s.competition.code == "FAV") R.string.fav_empty else R.string.no_matches
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else if (s.competition.code == "FAV") {
                MatchList(
                    matches = s.matches,
                    onToggleFavorite = { m ->
                        FavoritesStore.toggle(context, m)
                        vm.refreshFavorites()
                    },
                    onMatchClick = onMatchClick,
                    versionLine = false
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = vm.refreshing,
                    onRefresh = { vm.load(vm.selected, viaPull = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    MatchList(
                        matches = s.matches,
                        onToggleFavorite = { m ->
                            FavoritesStore.toggle(context, m)
                            vm.refreshFavorites()
                        },
                        onMatchClick = onMatchClick,
                        versionLine = true,
                        hero = pickMatchOfTheDay(s.matches)
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchList(
    matches: List<Match>,
    onToggleFavorite: (Match) -> Unit,
    onMatchClick: (Long) -> Unit,
    versionLine: Boolean,
    hero: Match? = null
) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
        hero?.let { h ->
            item(key = "hero") {
                MatchOfDayCard(match = h, onClick = { onMatchClick(h.id) })
            }
        }
        itemsIndexed(matches) { index, m ->
            // FotMob-style native ad card after the first few rows
            if (index == 5) {
                NativeAdCard()
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
            MatchRow(
                m = m,
                isFavorite = FavoritesStore.isFavorite(context, m.id),
                onToggleFavorite = { onToggleFavorite(m) },
                onClick = { onMatchClick(m.id) }
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
        if (versionLine) {
            item {
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

@Composable
private fun MatchRow(
    m: Match,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TeamBadge(m.homeTeam.name, m.homeTeam.tla, 26.dp)
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
        TeamBadge(m.awayTeam.name, m.awayTeam.tla, 26.dp)
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

/** "Match of the Day" hero card — the headline fixture, Night Stadium style. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchOfDayCard(match: Match, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    RoundedCornerShape(16.dp)
                )
                .padding(14.dp)
        ) {
            Text(
                stringResource(R.string.match_of_the_day).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                TeamBadge(match.homeTeam.name, match.homeTeam.tla, 34.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    match.homeTeam.name,
                    Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(
                        scoreLabel(match),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLive(match)) {
                            LivePulseDot()
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            statusLabel(match),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLive(match)) LiveRed
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    match.awayTeam.name,
                    Modifier.weight(1f),
                    maxLines = 2,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                TeamBadge(match.awayTeam.name, match.awayTeam.tla, 34.dp)
            }
        }
    }
}
