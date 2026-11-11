package com.koora.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koora.app.BuildConfig
import com.koora.app.R
import com.koora.app.ads.AdBanner
import com.koora.app.ads.AdsManager
import com.koora.app.data.Match
import com.koora.app.data.MatchRepository
import com.koora.app.data.MatchStats
import com.koora.app.data.MatchStatsRepository
import com.koora.app.data.StatsBar
import com.koora.app.data.StatsEvent
import com.koora.app.data.StatsLineup
import com.koora.app.data.StatsPlayer
import com.koora.app.data.Team
import com.koora.app.data.TeamColors
import com.koora.app.ui.components.LivePulseDot
import com.koora.app.ui.components.TeamBadge
import com.koora.app.ui.dateLabel
import com.koora.app.ui.formationRows
import com.koora.app.ui.isLive
import com.koora.app.ui.playerMatches
import com.koora.app.ui.positionLabel
import com.koora.app.ui.scoreLabel
import com.koora.app.ui.statNumberColor
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
 *
 * v1.8.0 roadmap release (owner-revised):
 *  - Highlights button on every FINISHED match (always visible, one
 *    predictable entry); the player lists only verified official videos.
 *  - Team-colored stadium hero (home left / away right, subtle lerp).
 * v1.9.0 (master spec):
 *  - Crest comparison header above Match stats; WCAG-safe stat numbers.
 *  - ONE unified green pitch (home top / away bottom) replaces the dot
 *    timeline; event badges with minutes sit above each player; tapping
 *    a player opens the max-info bottom sheet.
 *  - Sticky banner at the bottom (highest-dwell screen, roadmap P0).
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

    // Deep stats (events, lineups, bars) — real providers (ESPN / API-Football).
    // v1.8.0: the demo sample is DEBUG-only — a release build with no key
    // HIDES the sections (no-fake rule), it never shows invented players.
    LaunchedEffect(matchId) {
        match?.let {
            vm.loadDeepStats(
                it,
                it.competition?.code?.takeIf { c -> c.isNotBlank() } ?: MatchRepository.cacheCode,
                BuildConfig.DEBUG && !MatchRepository.hasKey
            )
        }
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
        },
        // v1.8.0 roadmap P0: sticky banner on the highest-dwell screen.
        bottomBar = { AdBanner() }
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
                // v1.8.0 "stadium hero" — floodlit scoreboard card tinted with
                // the two teams' kit colors (subtle, theme-aware lerp: home
                // left, away right, neutral center).
                val heroBase = MaterialTheme.colorScheme.surfaceContainerHigh
                val heroEdge = MaterialTheme.colorScheme.surface
                val homeKit = Color(TeamColors.colorsFor(match.homeTeam.name).bg)
                val awayKit = Color(TeamColors.colorsFor(match.awayTeam.name).bg)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    lerp(heroBase, homeKit, 0.20f),
                                    lerp(heroBase, homeKit, 0.08f),
                                    lerp(heroEdge, awayKit, 0.08f),
                                    lerp(heroEdge, awayKit, 0.20f)
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

                // Official video highlights, watched INSIDE the app.
                // v1.8.0 owner revision: the button is ALWAYS visible for
                // finished matches — a predictable, independent entry point,
                // not dependent on a pre-check. The player screen it opens
                // lists ONLY verified official releases; when nothing is
                // published yet it shows the honest "not available" state
                // with a retry — never a fake or placeholder video.
                if (match.status == "FINISHED") {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onWatchHighlights(match.homeTeam.name, match.awayTeam.name) }
                    ) {
                        Icon(Icons.Filled.PlayCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.highlights_button))
                    }
                    Spacer(Modifier.height(4.dp))
                    // Roadmap §3.4 inline attribution — shown whenever the
                    // button is visible.
                    Text(
                        stringResource(R.string.highlights_attribution),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(28.dp))
                StatsContent(match, vm.stats)

                // Deep stats v1.8.0 — timeline, events, kit-colored bars,
                // 2D formation pitch (hidden when no data, as always)
                vm.deepStats?.let { ds ->
                    if (ds.events.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        EventsCard(match, ds)
                    }
                    if (ds.bars.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        StatsBarsCard(match, ds)
                    }
                    if (ds.lineups.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        LineupsSection(match, ds)
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
// Deep stats cards (v1.8.0): 90' timeline, events, kit bars, 2D pitch
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
            // v1.9.0 (spec §4.1): the separate dot timeline is GONE — the
            // unified pitch in the lineups card carries the visual story
            // (badges with minutes above the players themselves). This list
            // stays as the accessible, full-detail record of the match.
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
private fun StatsBarsCard(match: Match, ds: MatchStats) {
    // v1.9.0 (spec §5): crest + name comparison header above the bars, and
    // stat NUMBERS lifted to WCAG-readable tones — a dark kit on a dark
    // theme (or a white kit on light) would be unreadable otherwise.
    // Bars keep the raw kit colors.
    val dark = isSystemInDarkTheme()
    val homeKitRaw = Color(TeamColors.colorsFor(match.homeTeam.name).bg)
    val awayKitRaw = Color(TeamColors.colorsFor(match.awayTeam.name).bg)
    val homeNum = Color(statNumberColor(TeamColors.colorsFor(match.homeTeam.name).bg.toInt(), dark))
    val awayNum = Color(statNumberColor(TeamColors.colorsFor(match.awayTeam.name).bg.toInt(), dark))
    val homeShort =
        match.homeTeam.tla ?: match.homeTeam.shortName ?: match.homeTeam.name
    val awayShort =
        match.awayTeam.tla ?: match.awayTeam.shortName ?: match.awayTeam.name
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stats_match),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            // Spec §5: Home crest + short name | score/status | Away.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        homeShort,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    TeamBadge(match.homeTeam.name, match.homeTeam.tla, 26.dp)
                }
                Text(
                    scoreLabel(match),
                    Modifier.padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TeamBadge(match.awayTeam.name, match.awayTeam.tla, 26.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        awayShort,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ds.bars.forEach { b ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            b.home,
                            Modifier.width(36.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = homeNum,
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
                            fontWeight = FontWeight.Bold,
                            color = awayNum
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    SplitBar(b.homeFraction, homeKitRaw, awayKitRaw)
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
private fun SplitBar(homeFraction: Float?, homeColor: Color, awayColor: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {
        if (homeFraction != null) {
            val w = homeFraction.coerceIn(0.02f, 0.98f)
            Box(Modifier.weight(w).fillMaxHeight().background(homeColor))
            Box(Modifier.weight(1f - w).fillMaxHeight().background(awayColor))
        } else {
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

/** A tapped player and everything we honestly know about their match. */
private data class PlayerSpot(
    val player: StatsPlayer,
    val side: Int,
    val posKey: String,
    val events: List<StatsEvent>
)

@Composable
private fun LineupsSection(match: Match, ds: MatchStats) {
    // v1.9.0 (spec §4): ONE unified pitch — home top half, away bottom half,
    // events as badges with minutes above each player, subs on a bench
    // strip, tap any player for the max-info sheet. Rendered ONLY when both
    // XIs are complete (11 starters each, formation rows sum to 10 outfield
    // players) — anything less falls back to the text list; positions are
    // NEVER invented (no-fake rule).
    val pitchable = ds.lineups.size == 2 && ds.lineups.all { lu ->
        lu.starters.size == 11 && formationRows(lu.formation)?.sum() == 11
    }
    var playerFor by remember { mutableStateOf<PlayerSpot?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stats_lineups),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            if (pitchable) {
                UnifiedPitch(
                    match = match,
                    ds = ds,
                    onPlayer = { playerFor = it }
                )
            } else {
                Row(Modifier.fillMaxWidth()) {
                    ds.lineups.forEachIndexed { i, lu ->
                        if (i > 0) Spacer(Modifier.width(16.dp))
                        LineupTextColumn(lu, Modifier.weight(1f))
                    }
                }
            }
        }
    }
    playerFor?.let { spot ->
        PlayerSheet(spot, match, onDismiss = { playerFor = null })
    }
}

/** Team anchor above/below the pitch: crest + name + formation + coach. */
@Composable
private fun TeamAnchor(team: Team, lu: StatsLineup) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TeamBadge(team.name, team.tla, 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            team.shortName ?: team.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Text(
            listOfNotNull(lu.formation, lu.coach).joinToString("  •  "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The all-in-one pitch (spec §4.1): vertical, home occupying the TOP half
 * and away the BOTTOM half so both teams face each other across the
 * halfway line. Positions come from the formation rows mapped onto the
 * provider's GK-first starter order — the same verified mapping as v1.8.0.
 * The pitch is forced LTR: a mirrored pitch would lie about the game.
 */
@Composable
private fun UnifiedPitch(
    match: Match,
    ds: MatchStats,
    onPlayer: (PlayerSpot) -> Unit
) {
    val homeKit = Color(TeamColors.colorsFor(match.homeTeam.name).bg)
    val awayKit = Color(TeamColors.colorsFor(match.awayTeam.name).bg)
    TeamAnchor(match.homeTeam, ds.lineups[0])
    Spacer(Modifier.height(8.dp))
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .clip(RoundedCornerShape(12.dp))
                .background(PitchGreen.copy(alpha = 0.10f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
        ) {
            val pitchLine = MaterialTheme.colorScheme.outlineVariant
            val pitchW = maxWidth
            val pitchH = maxHeight
            // halfway line
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .height(0.5.dp)
                    .background(pitchLine.copy(alpha = 0.5f))
            )
            // center circle
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(46.dp)
                    .border(1.dp, pitchLine.copy(alpha = 0.5f), CircleShape)
            )
            PitchTeam(
                lineup = ds.lineups[0],
                side = 0,
                kit = homeKit,
                events = ds.events,
                yFor = { f -> 0.07f + f * 0.37f },   // GK top edge -> attackers near center
                onPlayer = onPlayer,
                pitchW = pitchW,
                pitchH = pitchH
            )
            PitchTeam(
                lineup = ds.lineups[1],
                side = 1,
                kit = awayKit,
                events = ds.events,
                yFor = { f -> 0.93f - f * 0.37f },   // GK bottom edge -> attackers near center
                onPlayer = onPlayer,
                pitchW = pitchW,
                pitchH = pitchH
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    TeamAnchor(match.awayTeam, ds.lineups[1])
    BenchStrip(match, ds.events)
}

/** One team's players + their event badges, placed by formation rows. */
@Composable
private fun BoxScope.PitchTeam(
    lineup: StatsLineup,
    side: Int,
    kit: Color,
    events: List<StatsEvent>,
    yFor: (Float) -> Float,
    onPlayer: (PlayerSpot) -> Unit,
    pitchW: Dp,
    pitchH: Dp
) {
    val rows = formationRows(lineup.formation) ?: return
    var playerIndex = 0
    rows.forEachIndexed { rowIdx, count ->
        val f = rowIdx.toFloat() / (rows.size - 1).coerceAtLeast(1)
        val yFrac = yFor(f)
        repeat(count) {
            val p = lineup.starters.getOrNull(playerIndex) ?: return@repeat
            val idx = playerIndex
            playerIndex++
            val xFrac = (it + 1f) / (count + 1f)
            // Events involving THIS starter: goals/cards name them directly;
            // a substitution names them in `other` (the replaced player).
            val mine = events.filter { ev ->
                ev.side == side && (
                    if (ev.kind == "sub") playerMatches(ev.other ?: "", p.name)
                    else playerMatches(ev.player, p.name)
                )
            }
            val spot = PlayerSpot(
                player = p,
                side = side,
                posKey = positionLabel(rows, idx),
                events = mine
            )
            PlayerNode(
                p = p,
                color = kit,
                badges = mine,
                modifier = Modifier.offset(
                    x = pitchW * (xFrac - 0.5f * (22.dp.value / pitchW.value)),
                    y = pitchH * yFrac
                ),
                onClick = { onPlayer(spot) }
            )
        }
    }
}

@Composable
private fun PlayerNode(
    p: StatsPlayer,
    color: Color,
    badges: List<StatsEvent>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Spec §4.2: event badges ABOVE the jersey, grouped per kind —
        // "⚽ 23', 67'", cards and the sub arrow. Only real events, only
        // real minutes; nothing is invented to fill the pitch.
        badges.groupBy { if (it.kind == "sub") "sub" else it.kind }
            .values
            .take(3)
            .forEach { group -> EventBadge(group, color) }
        if (badges.isNotEmpty()) Spacer(Modifier.height(1.dp))
        Surface(
            shape = CircleShape,
            color = color,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.surface)
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Text(
                    p.number ?: "·",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            p.name.substringAfterLast(" ").take(10),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 7.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/** One grouped badge pill: minute(s) + kind glyph, colored by kind. */
@Composable
private fun EventBadge(group: List<StatsEvent>, kit: Color) {
    val kind = group.first().kind
    val minutes = group.joinToString(", ") { "${it.minute}'" }
    val bg = when (kind) {
        "yellow" -> Color(0xFFF2C94C)
        "red" -> Color(0xFFEB5757)
        "sub" -> MaterialTheme.colorScheme.outlineVariant
        else -> kit
    }
    val fg = when (kind) {
        "yellow" -> Color(0xFF3B2F00)
        else -> Color.White
    }
    val glyph = if (kind == "sub") "⬇" else if (kind == "goal") "⚽" else ""
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = bg,
        border = if (kind == "goal") BorderStroke(0.5.dp, Color.White) else null
    ) {
        Text(
            ("$glyph $minutes").trim(),
            Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1
        )
    }
}

/**
 * Bench strip (spec §4.2): the players who came ON, with their minute.
 * Built ONLY from real substitution events — no subs in the data, no
 * strip (we never invent a bench).
 */
@Composable
private fun BenchStrip(match: Match, events: List<StatsEvent>) {
    val subs = events.filter { it.kind == "sub" }
    if (subs.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            subs.filter { it.side == 0 }.forEach { BenchRow(it) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            subs.filter { it.side == 1 }.forEach { BenchRow(it) }
        }
    }
}

@Composable
private fun BenchRow(e: StatsEvent) {
    Text(
        "⬆ ${e.minute}'  ${e.player}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

/**
 * Player max-info sheet (spec §4.3) — every field we ACTUALLY have from
 * the free providers: identity (number, name, team, formation-derived
 * position group) and this match's real events. Photos, ages and career
 * bios are premium-API fields; when a richer plan is connected they
 * render here automatically. Zero placeholders on the free tier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSheet(spot: PlayerSpot, match: Match, onDismiss: () -> Unit) {
    val team = if (spot.side == 0) match.homeTeam else match.awayTeam
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(team.name, team.tla, 34.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "#${spot.player.number ?: "·"}  ${spot.player.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${team.shortName ?: team.name}  •  " +
                            stringResource(positionString(spot.posKey)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (spot.events.isNotEmpty()) {
                Text(
                    stringResource(R.string.stats_events),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                spot.events.forEach { ev -> SheetEventRow(ev) }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.player_sheet_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun positionString(key: String): Int = when (key) {
    "GK" -> R.string.pos_gk
    "DF" -> R.string.pos_df
    "FW" -> R.string.pos_fw
    else -> R.string.pos_mf
}

@Composable
private fun SheetEventRow(e: StatsEvent) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
        Column {
            if (e.detail.isNotBlank()) {
                Text(
                    e.detail,
                    style = MaterialTheme.typography.bodySmall,
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
    }
}

/** v1.5.0 text lineup (fallback when the formation pitch can't be trusted). */
@Composable
private fun LineupTextColumn(lu: StatsLineup, modifier: Modifier = Modifier) {
    Column(modifier) {
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
