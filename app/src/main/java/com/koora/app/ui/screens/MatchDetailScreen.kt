package com.koora.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.koora.app.data.TeamColors
import com.koora.app.ui.components.LivePulseDot
import com.koora.app.ui.components.TeamBadge
import com.koora.app.ui.dateLabel
import com.koora.app.ui.formationRows
import com.koora.app.ui.isLive
import com.koora.app.ui.minuteValue
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
 *
 * v1.8.0 roadmap release:
 *  - Highlights button exists ONLY when a verified official video is
 *    confirmed available (roadmap P0) — otherwise the section is fully
 *    hidden, never a placeholder or disabled state.
 *  - Team-colored stadium hero (home left / away right, subtle lerp).
 *  - 90-minute event timeline above the event list.
 *  - Dual-sided stat bars in each team's kit color.
 *  - 2D formation pitch for lineups (falls back to the text list when
 *    formation or XI data is incomplete — positions are never invented).
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
                // v1.8.0 (roadmap P0): the section exists ONLY when a verified
                // official video is confirmed available — checked once per
                // fixture (VideoCache serves it), hidden from the UI tree
                // otherwise. No placeholder, no disabled state, no fallback.
                var videoReady by remember(matchId) { mutableStateOf(false) }
                LaunchedEffect(matchId) {
                    if (match.status == "FINISHED") {
                        videoReady = HighlightsSource.hasVideo(
                            context, match.homeTeam.name, match.awayTeam.name
                        )
                    }
                }
                if (videoReady) {
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
            Spacer(Modifier.height(12.dp))
            // v1.8.0 roadmap: horizontal 90-minute track — goals, cards and
            // subs positioned by real minute (home above, away below).
            // Events with an unparseable minute are skipped (never guessed).
            MatchTimeline(match, ds.events)
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(8.dp))
            ds.events.forEach { e -> EventRow(match, e) }
        }
    }
}

@Composable
private fun MatchTimeline(match: Match, events: List<StatsEvent>) {
    val homeKit = Color(TeamColors.colorsFor(match.homeTeam.name).bg)
    val awayKit = Color(TeamColors.colorsFor(match.awayTeam.name).bg)
    val track = MaterialTheme.colorScheme.outlineVariant

    // Events positioned by real minute; unparseable minutes are skipped
    // (never guessed). Same-minute collisions on one side stack one row
    // up/down instead of overlapping; rows are capped — 3+ events in one
    // minute for one team are vanishingly rare and may slightly overlap.
    data class Plot(val min: Double, val e: StatsEvent, val row: Int)
    val homeSide = mutableListOf<Plot>()
    val awaySide = mutableListOf<Plot>()
    events.forEach { e ->
        val m = minuteValue(e.minute) ?: return@forEach
        val side = if (e.side == 0) homeSide else awaySide
        val row = (side.count { it.e.minute == e.minute }).coerceAtMost(1)
        side.add(Plot(m, e, row))
    }

    BoxWithConstraints(Modifier.fillMaxWidth().height(60.dp)) {
        val w = maxWidth
        // 0'..95' span so 90+5 fits inside the track; 6dp padding each side
        fun x(min: Double): Float = 6f + (min.coerceIn(0.0, 95.0) / 95.0).toFloat() * (w.value - 12f)
        // the track itself
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .height(2.dp)
                .background(track, RoundedCornerShape(1.dp))
        )
        // minute ticks 45 / 90
        listOf(45.0 to "45", 90.0 to "90").forEach { (min, label) ->
            Text(
                label,
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (x(min) - 4f).dp, y = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // home events just ABOVE the track, away events just BELOW it
        homeSide.forEach { p ->
            TimelineDot(
                e = p.e, color = homeKit,
                modifier = Modifier
                    .offset(x = (x(p.min) - 5f).dp, y = (20 - p.row * 9).dp)
            )
        }
        awaySide.forEach { p ->
            TimelineDot(
                e = p.e, color = awayKit,
                modifier = Modifier
                    .offset(x = (x(p.min) - 5f).dp, y = (34 + p.row * 9).dp)
            )
        }
    }
}

@Composable
private fun TimelineDot(e: StatsEvent, color: Color, modifier: Modifier = Modifier) {
    when (e.kind) {
        "yellow" -> Surface(
            modifier = modifier.size(7.dp),
            shape = RoundedCornerShape(1.dp),
            color = Color(0xFFF2C94C)
        ) {}
        "red" -> Surface(
            modifier = modifier.size(7.dp),
            shape = RoundedCornerShape(1.dp),
            color = Color(0xFFEB5757)
        ) {}
        "sub" -> Surface(
            modifier = modifier.size(7.dp),
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, color)
        ) {}
        else -> Surface(
            modifier = modifier.size(10.dp),
            shape = CircleShape,
            color = color,
            border = BorderStroke(1.dp, Color.White)
        ) {}
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
    // v1.8.0 roadmap: bars colored by each team's primary kit color.
    val homeKit = Color(TeamColors.colorsFor(match.homeTeam.name).bg)
    val awayKit = Color(TeamColors.colorsFor(match.awayTeam.name).bg)
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
                            color = homeKit,
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
                            color = awayKit
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    SplitBar(b.homeFraction, homeKit, awayKit)
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

@Composable
private fun LineupsSection(match: Match, ds: MatchStats) {
    // v1.8.0 roadmap P1: 2D tactical pitch. Rendered ONLY when both XIs
    // are complete (11 starters each, formation rows sum to 10 outfield
    // players) — the provider lists starters GK-first in formation order,
    // verified live 2026-11-11 on real ESPN summaries. Anything less
    // falls back to the v1.5.0 text list; positions are NEVER invented.
    val pitchable = ds.lineups.size == 2 && ds.lineups.all { lu ->
        lu.starters.size == 11 && formationRows(lu.formation)?.sum() == 11
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stats_lineups),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            if (pitchable) {
                Row(Modifier.fillMaxWidth()) {
                    LineupsPitch(
                        ds.lineups[0],
                        Color(TeamColors.colorsFor(match.homeTeam.name).bg),
                        Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(14.dp))
                    LineupsPitch(
                        ds.lineups[1],
                        Color(TeamColors.colorsFor(match.awayTeam.name).bg),
                        Modifier.weight(1f)
                    )
                }
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
}

/**
 * One team's half of the pitch, drawn vertically (own goal at the
 * bottom). Node positions come from the formation rows mapped onto the
 * provider's GK-first starter order.
 */
@Composable
private fun LineupsPitch(lu: StatsLineup, kitColor: Color, modifier: Modifier = Modifier) {
    val rows = formationRows(lu.formation) ?: return
    val pitchLine = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Text(
            lu.formation ?: "",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        lu.coach?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.62f)
                .clip(RoundedCornerShape(10.dp))
                .background(PitchGreen.copy(alpha = 0.10f))
                .border(1.dp, pitchLine.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
        ) {
            val pitchW = maxWidth
            val pitchH = maxHeight
            // halfway line above the GK (bottom of the half)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .offset(y = (-14).dp)
                    .height(0.5.dp)
                    .background(pitchLine.copy(alpha = 0.5f))
            )
            var playerIndex = 0
            rows.forEachIndexed { rowIdx, count ->
                val f = rowIdx.toFloat() / (rows.size - 1).coerceAtLeast(1)
                val yFrac = 0.93f - f * 0.78f          // GK bottom -> attackers top
                repeat(count) {
                    val p = lu.starters.getOrNull(playerIndex) ?: return@repeat
                    playerIndex++
                    val xFrac = (it + 1f) / (count + 1f)
                    PlayerNode(
                        p = p,
                        color = kitColor,
                        modifier = Modifier.offset(
                            x = pitchW * (xFrac - 0.5f * (22.dp.value / pitchW.value)),
                            y = pitchH * yFrac
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerNode(p: StatsPlayer, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
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
