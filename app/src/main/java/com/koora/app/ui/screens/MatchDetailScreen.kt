package com.koora.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koora.app.R
import com.koora.app.ads.AdsManager
import com.koora.app.data.Match
import com.koora.app.data.MatchRepository
import com.koora.app.data.MatchStats
import com.koora.app.data.MatchStatsRepository
import com.koora.app.data.StatsBar
import com.koora.app.data.StatsEvent
import com.koora.app.ui.components.LivePulseDot
import com.koora.app.ui.components.TeamBadge
import com.koora.app.ui.dateLabel
import com.koora.app.ui.isLive
import com.koora.app.ui.scoreLabel
import com.koora.app.ui.statusLabel
import com.koora.app.ui.theme.LiveRed
import com.koora.app.ui.theme.PitchGreen
import kotlinx.coroutines.launch

/*
 * Match detail — v1.4: stats are FREE for everyone (no ad gate).
 * Monetization rides along passively: one capped interstitial fires on
 * entry to a LIVE match (see AdsManager.maybeShowInterstitial).
 * Finished matches offer official video highlights watched IN-APP
 * (HighlightsScreen) since v1.4.2.
 */
sealed interface StatsUiState {
    data object Idle : StatsUiState
    data object Loading : StatsUiState
    data class Ready(val meetings: List<Match>) : StatsUiState
    data object Empty : StatsUiState
}

class MatchDetailViewModel : ViewModel() {
    var stats by mutableStateOf<StatsUiState>(StatsUiState.Idle)
        private set
    var deepStats by mutableStateOf<MatchStats?>(null)
        private set

    fun loadStats(matchId: Long) {
        if (stats !is StatsUiState.Idle) return
        stats = StatsUiState.Loading
        viewModelScope.launch {
            stats = try {
                // h2h also contains the current fixture -> exclude it
                val meetings = MatchRepository.head2head(matchId).filter { it.id != matchId }
                if (meetings.isNotEmpty()) StatsUiState.Ready(meetings) else StatsUiState.Empty
            } catch (e: Exception) {
                StatsUiState.Empty
            }
        }
    }

    /** Deep stats (events / lineups / bars). demo=true -> sample data. */
    fun loadDeepStats(match: Match, compCode: String, demo: Boolean) {
        if (deepStats != null) return
        viewModelScope.launch {
            deepStats = runCatching {
                MatchStatsRepository.statsFor(match, compCode, demo)
            }.getOrNull()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(
    matchId: Long,
    onBack: () -> Unit,
    onWatchHighlights: (home: String, away: String) -> Unit,
    vm: MatchDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val match = remember(matchId) {
        MatchRepository.cache.firstOrNull { it.id == matchId }
    }

    // Stats load automatically — free, no ad gate
    LaunchedEffect(matchId) { vm.loadStats(matchId) }

    // Deep stats (events, lineups, bars) — demo sample or API-Football
    LaunchedEffect(matchId) {
        match?.let { vm.loadDeepStats(it, MatchRepository.cacheCode, !MatchRepository.hasKey) }
    }

    // The ONE capped interstitial: only on live matches, only per AdsManager rules
    LaunchedEffect(matchId) {
        val live = match != null && isLive(match)
        val activity = context as? Activity
        if (live && activity != null) AdsManager.maybeShowInterstitial(activity)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (match == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.match_not_found))
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))
                // v1.5.4 "stadium hero" — floodlit gradient scoreboard card
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .border(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLive(match)) {
                                LivePulseDot()
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                "${statusLabel(match)} • ${dateLabel(match)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TeamBadge(match.homeTeam.name, match.homeTeam.tla, 44.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                match.homeTeam.name,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            scoreLabel(match),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                match.awayTeam.name,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.width(10.dp))
                            TeamBadge(match.awayTeam.name, match.awayTeam.tla, 44.dp)
                        }
                        match.score.halfTime?.home?.let { htHome ->
                            match.score.halfTime?.away?.let { htAway ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.ht_label, htHome, htAway),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Official video highlights, watched INSIDE the app
                if (match.status == "FINISHED") {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onWatchHighlights(match.homeTeam.name, match.awayTeam.name) }
                    ) {
                        Icon(Icons.Filled.PlayCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.highlights_button))
                    }
                }

                Spacer(Modifier.height(28.dp))
                StatsContent(match, vm.stats)

                // Deep stats v1.5.0 — events, bars, lineups (hidden when no data)
                vm.deepStats?.let { ds ->
                    if (ds.events.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        EventsCard(match, ds)
                    }
                    if (ds.bars.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        StatsBarsCard(ds)
                    }
                    if (ds.lineups.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        LineupsCard(ds)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatsContent(match: Match, state: StatsUiState) {
    when (state) {
        is StatsUiState.Loading -> Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        is StatsUiState.Empty -> Text(
            stringResource(R.string.no_stats),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        )

        is StatsUiState.Ready -> {
            HeadToHeadCard(match, state.meetings)
            Spacer(Modifier.height(16.dp))
            LastMeetingsCard(state.meetings)
            Spacer(Modifier.height(16.dp))
            FormCard(match)
        }

        StatsUiState.Idle -> {}
    }
}

@Composable
private fun HeadToHeadCard(match: Match, meetings: List<Match>) {
    val homeId = match.homeTeam.id
    var hw = 0
    var draws = 0
    var aw = 0
    meetings.forEach { m ->
        val h = m.score.fullTime?.home ?: return@forEach
        val a = m.score.fullTime?.away ?: return@forEach
        when {
            h == a -> draws++
            h > a -> if (m.homeTeam.id == homeId) hw++ else aw++
            else -> if (m.homeTeam.id == homeId) aw++ else hw++
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.h2h_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.h2h_meetings, meetings.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatCell(
                    (match.homeTeam.shortName ?: match.homeTeam.name),
                    "${hw}",
                    Modifier.weight(1f)
                )
                StatCell("—", "${draws}", Modifier.weight(1f))
                StatCell(
                    (match.awayTeam.shortName ?: match.awayTeam.name),
                    "${aw}",
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LastMeetingsCard(meetings: List<Match>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.last_meetings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            meetings.reversed().take(5).forEachIndexed { i, m ->
                if (i > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.width(72.dp)) {
                        Text(
                            dateLabel(m),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Text(
                        m.homeTeam.shortName ?: m.homeTeam.name,
                        Modifier.weight(1.2f),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        scoreLabel(m),
                        Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        m.awayTeam.shortName ?: m.awayTeam.name,
                        Modifier.weight(1.2f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun FormCard(match: Match) {
    val homeForm = MatchRepository.teamForm(MatchRepository.cache, match.homeTeam.id)
    val awayForm = MatchRepository.teamForm(MatchRepository.cache, match.awayTeam.id)
    if (homeForm.isEmpty() && awayForm.isEmpty()) return

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.form_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            FormRow(match.homeTeam.shortName ?: match.homeTeam.name, homeForm)
            Spacer(Modifier.height(10.dp))
            FormRow(match.awayTeam.shortName ?: match.awayTeam.name, awayForm)
        }
    }
}

@Composable
private fun FormRow(teamName: String, form: List<Char>) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            teamName,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            form.forEach { c ->
                val bg = when (c) {
                    'W' -> PitchGreen
                    'L' -> LiveRed
                    else -> Color(0xFF8A9A93)
                }
                Surface(shape = CircleShape, color = bg) {
                    Text(
                        c.toString(),
                        Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (form.isEmpty()) {
                Text("–", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Deep stats cards (v1.5.0): events timeline, stat bars, lineups
// ---------------------------------------------------------------------------

@Composable
private fun EventsCard(match: Match, ds: MatchStats) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stats_events),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            ds.events.forEach { e -> EventRow(match, e) }
        }
    }
}

@Composable
private fun EventRow(match: Match, e: StatsEvent) {
    val teamLabel = if (e.side == 0)
        (match.homeTeam.tla ?: match.homeTeam.shortName ?: match.homeTeam.name)
    else
        (match.awayTeam.tla ?: match.awayTeam.shortName ?: match.awayTeam.name)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            e.minute,
            Modifier.width(44.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (e.kind) {
            "yellow" -> CardChip(Color(0xFFF2C94C))
            "red" -> CardChip(Color(0xFFEB5757))
            "sub" -> Icon(
                Icons.Filled.SwapVert, contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> Icon(
                Icons.Filled.SportsSoccer, contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.player,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (e.detail.isNotBlank()) {
                Text(
                    e.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            e.other?.let {
                Text(
                    if (e.kind == "sub") "→ $it" else "($it)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                teamLabel,
                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CardChip(color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color,
        modifier = Modifier.size(14.dp)
    ) {}
}

@Composable
private fun StatsBarsCard(ds: MatchStats) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stats_match),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            ds.bars.forEach { b ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            b.home,
                            Modifier.width(36.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                        Text(
                            localStatLabel(b.label),
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        Text(
                            b.away,
                            Modifier.width(36.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    SplitBar(b.homeFraction)
                }
            }
        }
    }
}

@Composable
private fun localStatLabel(label: String): String = when (label.lowercase()) {
    "ball possession", "possession" -> stringResource(R.string.stats_possession)
    "total shots", "shots" -> stringResource(R.string.stats_shots)
    "shots on goal", "shots on target", "shots_on" -> stringResource(R.string.stats_shots_on)
    "corner kicks", "corners" -> stringResource(R.string.stats_corners)
    "fouls" -> stringResource(R.string.stats_fouls)
    "yellow cards", "yellow" -> stringResource(R.string.stats_yellow)
    "red cards", "red" -> stringResource(R.string.stats_red)
    "offsides" -> stringResource(R.string.stats_offsides)
    "goalkeeper saves", "saves" -> stringResource(R.string.stats_saves)
    else -> label
}

@Composable
private fun SplitBar(homeFraction: Float?) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {
        if (homeFraction != null) {
            val w = homeFraction.coerceIn(0.02f, 0.98f)
            Box(Modifier.weight(w).fillMaxHeight().background(PitchGreen))
            Box(Modifier.weight(1f - w).fillMaxHeight().background(Color(0xFF8A9A93)))
        } else {
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

@Composable
private fun LineupsCard(ds: MatchStats) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stats_lineups),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                ds.lineups.forEachIndexed { i, lu ->
                    if (i > 0) Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            lu.team,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        lu.formation?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        lu.coach?.let {
                            Text(
                                "${stringResource(R.string.stats_coach)}: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        lu.starters.forEach { p ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    p.number ?: "·",
                                    Modifier.width(20.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    p.name,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.stats_subs, lu.subsCount),
                            Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
