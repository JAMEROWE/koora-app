package com.koora.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.URLEncoder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
 * Finished matches offer the official video highlights on YouTube.
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(
    matchId: Long,
    onBack: () -> Unit,
    vm: MatchDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val match = remember(matchId) {
        MatchRepository.cache.firstOrNull { it.id == matchId }
    }

    // Stats load automatically — free, no ad gate
    LaunchedEffect(matchId) { vm.loadStats(matchId) }

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
                Text(
                    match.homeTeam.name,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    scoreLabel(match),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    match.awayTeam.name,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "${statusLabel(match)} • ${dateLabel(match)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                match.score.halfTime?.home?.let { htHome ->
                    match.score.halfTime?.away?.let { htAway ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.ht_label, htHome, htAway),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Official video highlights (YouTube) for finished matches
                if (match.status == "FINISHED") {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { openHighlights(context, match) }) {
                        Icon(Icons.Filled.PlayCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.highlights_button))
                    }
                }

                Spacer(Modifier.height(28.dp))
                StatsContent(match, vm.stats)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun openHighlights(context: android.content.Context, match: Match) {
    try {
        val query = URLEncoder.encode(
            "${match.homeTeam.name} vs ${match.awayTeam.name} highlights",
            "UTF-8"
        )
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query")))
    } catch (_: Exception) {
        // no browser / bad URL -> ignore
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
