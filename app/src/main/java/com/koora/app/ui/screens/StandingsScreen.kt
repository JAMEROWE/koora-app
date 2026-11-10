package com.koora.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koora.app.R
import com.koora.app.ads.AdBanner
import com.koora.app.data.MatchRepository
import com.koora.app.data.TableRow
import com.koora.app.ui.components.SkeletonList
import com.koora.app.ui.components.TeamBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/*
 * v1.5.4 table zones (2025-26 season formats): the first [Pair.first]
 * positions qualify for continental competition (green), the last
 * [Pair.second] are relegation places (red). The Champions League
 * "league phase" only marks the top 8 (direct knockout spots).
 * Numbers are indicative, not official competition rules.
 */
private val ZONES: Map<String, Pair<Int, Int>> = mapOf(
    "PL" to (4 to 3),
    "PD" to (4 to 3),
    "SA" to (4 to 3),
    "BL1" to (4 to 2),
    "FL1" to (3 to 2),
    "ELC" to (2 to 3),
    "DED" to (2 to 2),
    "PPL" to (2 to 2),
    "BSA" to (4 to 4),
    "CL" to (8 to 0)
)

class StandingsViewModel : ViewModel() {
    // Only competitions that expose a league table (tournaments like
    // World Cup / Euro / Libertadores return 404 for standings).
    val competitions = listOf(
        Competition("FL1", "Ligue 1"),
        Competition("PL", "Premier League"),
        Competition("PD", "La Liga"),
        Competition("SA", "Serie A"),
        Competition("BL1", "Bundesliga"),
        Competition("CL", "Champions League"),
        Competition("ELC", "Championship"),
        Competition("DED", "Eredivisie"),
        Competition("PPL", "Primeira Liga"),
        Competition("BSA", "Brasileirão")
    )

    var selected by mutableStateOf(competitions.first())
        private set
    var rows by mutableStateOf<List<TableRow>?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        load(competitions.first())
    }

    fun load(c: Competition) {
        selected = c
        rows = null
        error = null
        viewModelScope.launch {
            try {
                rows = MatchRepository.standings(c.code)
            } catch (e: Exception) {
                error = e.message ?: "Network error"
            }
        }
    }
}

@Composable
fun StandingsScreen(vm: StandingsViewModel = viewModel()) {
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = vm.competitions.indexOf(vm.selected)) {
            vm.competitions.forEach { c ->
                Tab(
                    selected = c.code == vm.selected.code,
                    onClick = { vm.load(c) },
                    text = { Text(c.label) }
                )
            }
        }

        val ts = MatchRepository.lastUpdated("standings:${vm.selected.code}")
        if (ts > 0L) {
            Text(
                stringResource(R.string.updated_at, hhmm(ts)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // v1.5.4 zone legend — the green/red bars on the table rows
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.standings_legend_europe),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if ((ZONES[vm.selected.code]?.second ?: 0) > 0) {
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.standings_legend_relegation),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(Modifier.weight(1f)) {
            val table = vm.rows
            val err = vm.error
            when {
                err != null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.error_generic))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.load(vm.selected) }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                table == null -> SkeletonList(rows = 10, modifier = Modifier.fillMaxSize())

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item { TableHeader() }
                    val zone = ZONES[vm.selected.code]
                    val relegationFrom = zone
                        ?.takeIf { it.second > 0 }
                        ?.let { table.size - it.second + 1 }
                        ?: Int.MAX_VALUE
                    items(table, key = { it.position }) { row ->
                        TableLine(
                            row,
                            zoneColor = when {
                                zone != null && row.position <= zone.first -> {
                                    MaterialTheme.colorScheme.primary
                                }
                                row.position >= relegationFrom -> {
                                    MaterialTheme.colorScheme.error
                                }
                                else -> null
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }

        AdBanner()
    }
}

private fun hhmm(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))

@Composable
private fun tableCell(text: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    Text(
        text,
        modifier,
        textAlign = TextAlign.Center,
        maxLines = 1,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else null
    )
}

@Composable
private fun TableHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            "#",
            Modifier.width(28.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(43.dp)) // align with the name column (bar + badge offsets)
        Text(
            stringResource(R.string.col_team),
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        tableCell(stringResource(R.string.col_played), Modifier.width(30.dp))
        tableCell(stringResource(R.string.col_won), Modifier.width(30.dp))
        tableCell(stringResource(R.string.col_draw), Modifier.width(30.dp))
        tableCell(stringResource(R.string.col_lost), Modifier.width(30.dp))
        tableCell(stringResource(R.string.col_points), Modifier.width(36.dp))
    }
}

@Composable
private fun TableLine(row: TableRow, zoneColor: Color?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${row.position}",
            Modifier.width(28.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        // v1.5.4 zone bar — always occupies space so columns stay aligned
        Box(
            Modifier
                .width(3.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(zoneColor ?: Color.Transparent)
        )
        Spacer(Modifier.width(6.dp))
        TeamBadge(row.team.name, row.team.tla, 22.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            row.team.name,
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
        tableCell("${row.playedGames}", Modifier.width(30.dp))
        tableCell("${row.won}", Modifier.width(30.dp))
        tableCell("${row.draw}", Modifier.width(30.dp))
        tableCell("${row.lost}", Modifier.width(30.dp))
        tableCell("${row.points}", Modifier.width(36.dp), bold = true)
    }
}
