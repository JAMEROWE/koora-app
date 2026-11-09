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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koora.app.R
import com.koora.app.ads.AdBanner
import com.koora.app.data.Match
import com.koora.app.data.MatchRepository
import com.koora.app.ui.isLive
import com.koora.app.ui.scoreLabel
import com.koora.app.ui.statusLabel
import com.koora.app.ui.theme.LiveRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Live tab: matches in progress across competitions.
 *  - League chips select which competition to poll.
 *  - Auto-refresh every 60 seconds, ONLY while this screen is visible and
 *    the app is in the foreground (lifecycle-aware, protects the shared
 *    free-tier rate limit).
 *  - "Updated HH:MM" keeps users trusting what they see (free-tier data
 *    can lag a few minutes behind real life until the API-Football upgrade).
 */
class LiveViewModel : ViewModel() {
    val leagues = ALL_COMPETITIONS

    var selected by mutableStateOf(leagues.first())
        private set
    var live by mutableStateOf<List<Match>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun select(c: Competition) {
        selected = c
        load(force = false)
    }

    fun load(force: Boolean) {
        loading = live.isEmpty()
        viewModelScope.launch {
            try {
                val all = MatchRepository.matches(selected.code, force = force)
                live = all.filter { isLive(it) }
                error = null
            } catch (e: Exception) {
                if (live.isEmpty()) error = e.message ?: "Network error"
            }
            loading = false
        }
    }
}

@Composable
fun LiveScreen(vm: LiveViewModel = viewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            foreground = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-refresh loop — runs only while this screen is composed; each tick
    // fires ONLY when the app is in the foreground.
    LaunchedEffect(vm.selected) {
        vm.load(force = false)
        while (true) {
            delay(60_000)
            if (foreground) vm.load(force = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = vm.leagues.indexOf(vm.selected)) {
            vm.leagues.forEach { c ->
                Tab(
                    selected = c.code == vm.selected.code,
                    onClick = { vm.select(c) },
                    text = { Text(c.label) }
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "LIVE",
                color = LiveRed,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            val ts = MatchRepository.lastUpdated("matches:${vm.selected.code}")
            Text(
                if (ts > 0L) stringResource(R.string.updated_at, hhmm(ts)) else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                vm.loading && vm.live.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                vm.error != null && vm.live.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.error_generic),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }

                vm.live.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.no_live),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.live, key = { it.id }) { m ->
                        LiveMatchRow(m)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        AdBanner()
    }
}

@Composable
private fun LiveMatchRow(m: Match) {
    Row(
        Modifier
            .fillMaxWidth()
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
                fontWeight = FontWeight.Bold,
                color = LiveRed
            )
            Text(
                statusLabel(m),
                style = MaterialTheme.typography.labelSmall,
                color = LiveRed
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

private fun hhmm(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
