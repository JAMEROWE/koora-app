package com.koora.app.ui.screens

import androidx.compose.foundation.clickable
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
import com.koora.app.data.Match
import com.koora.app.data.MatchRepository
import com.koora.app.ui.isLive
import com.koora.app.ui.scoreLabel
import com.koora.app.ui.statusLabel
import com.koora.app.ui.theme.LiveRed
import kotlinx.coroutines.launch

data class Competition(val code: String, val label: String)

sealed interface MatchesUiState {
    data object Loading : MatchesUiState
    data class Error(val message: String) : MatchesUiState
    data class Ready(val competition: Competition, val matches: List<Match>) : MatchesUiState
}

class MatchesViewModel : ViewModel() {
    val competitions = listOf(
        Competition("CL", "Champions League"),
        Competition("PL", "Premier League"),
        Competition("PD", "La Liga"),
        Competition("SA", "Serie A"),
        Competition("BL1", "Bundesliga"),
        Competition("FL1", "Ligue 1")
        // Botola Pro joins here once we upgrade to API-Football
    )

    var selected by mutableStateOf(competitions.first())
        private set
    var state by mutableStateOf<MatchesUiState>(MatchesUiState.Loading)
        private set

    init {
        load(competitions.first())
    }

    fun load(c: Competition) {
        selected = c
        state = MatchesUiState.Loading
        viewModelScope.launch {
            state = try {
                MatchesUiState.Ready(c, MatchRepository.matches(c.code))
            } catch (e: Exception) {
                MatchesUiState.Error(e.message ?: "Network error")
            }
        }
    }
}

@Composable
fun MatchesScreen(onMatchClick: (Long) -> Unit, vm: MatchesViewModel = viewModel()) {
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
                    text = { Text(c.label) }
                )
            }
        }

        when (val s = vm.state) {
            is MatchesUiState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

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

            is MatchesUiState.Ready -> LazyColumn(Modifier.fillMaxSize()) {
                items(s.matches, key = { it.id }) { m ->
                    MatchRow(m) { onMatchClick(m.id) }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchRow(m: Match, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            m.homeTeam.name,
            Modifier.weight(1.2f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(96.dp)
        ) {
            Text(
                scoreLabel(m),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                statusLabel(m),
                style = MaterialTheme.typography.labelSmall,
                color = if (isLive(m)) LiveRed else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            m.awayTeam.name,
            Modifier.weight(1.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
