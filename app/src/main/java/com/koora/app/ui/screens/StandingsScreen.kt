package com.koora.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koora.app.R
import com.koora.app.data.MatchRepository
import com.koora.app.data.TableRow
import kotlinx.coroutines.launch

class StandingsViewModel : ViewModel() {
    val competitions = listOf(
        Competition("FL1", "Ligue 1"),
        Competition("CL", "Champions League")
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

            table == null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            else -> LazyColumn(Modifier.fillMaxSize()) {
                item { TableHeader() }
                items(table, key = { it.position }) { row ->
                    TableLine(row)
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

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
private fun TableLine(row: TableRow) {
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
