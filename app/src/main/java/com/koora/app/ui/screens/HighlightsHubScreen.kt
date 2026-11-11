package com.koora.app.ui.screens

import android.text.format.DateUtils
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.koora.app.ads.AdBanner
import com.koora.app.data.MatchRepository
import com.koora.app.ui.components.SkeletonList
import com.koora.app.ui.localDayKeyAt
import com.koora.app.ui.scoreLabel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/*
 * HIGHLIGHTS TAB (v1.8.0) — the independent entry point the owner asked
 * for: official released highlight videos, discoverable without opening
 * any match page. The list contains ONLY matches whose official video is
 * actually out (paired from the verified league channels' own feeds by
 * HighlightsHub) — a fixture without a release never appears, and a
 * release without a known fixture never appears either. Empty tab =
 * nothing released yet, with a retry — never a placeholder.
 *
 * Data cost: the fixture list reuses the SAME shared window cache as the
 * Live and Results tabs (identical keys = zero extra football-data.org
 * calls); the video scan is 6 cheap RSS GETs, served for 15 minutes.
 */
class HighlightsHubViewModel : ViewModel() {
    var rows by mutableStateOf<List<HighlightsHub.Row>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set

    fun load(context: android.content.Context?, force: Boolean = false) {
        if (!force && rows.isNotEmpty()) return
        loading = true
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                // Exactly the Live/Results window — shared per-league cache.
                val fixtures = MatchRepository.matchesWindow(
                    localDayKeyAt(now, -6), localDayKeyAt(now, 1)
                )
                rows = HighlightsHub.resolve(context, fixtures)
            } catch (_: Exception) {
                // Offline/failure: keep the previous rows (they are already
                // verified real data); an empty list stays empty — never
                // fake rows, per the no-fake rule.
            }
            loading = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsHubScreen(
    onWatch: (String, String) -> Unit,
    vm: HighlightsHubViewModel = viewModel()
) {
    val context = LocalContext.current
    var reloadTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadTick) {
        vm.load(context, force = reloadTick > 0)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_highlights)) },
            actions = {
                IconButton(onClick = { reloadTick++ }) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.hub_refresh)
                    )
                }
            }
        )

        // Permanent attribution under the title (roadmap §3.4, EN/FR/AR).
        Text(
            stringResource(R.string.highlights_attribution),
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(Modifier.weight(1f)) {
            when {
                vm.loading && vm.rows.isEmpty() ->
                    SkeletonList(rows = 7, modifier = Modifier.fillMaxSize())

                vm.rows.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.hub_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(vm.rows, key = { it.video.videoId }) { row ->
                        HubRow(row, onWatch)
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
private fun HubRow(row: HighlightsHub.Row, onWatch: (String, String) -> Unit) {
    val m = row.match
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onWatch(m.homeTeam.name, m.awayTeam.name) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            "${m.homeTeam.name}  ${scoreLabel(m)}  ${m.awayTeam.name}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            m.competition?.name?.takeIf { it.isNotBlank() }?.let {
                HubBadge(it)
                Spacer(Modifier.width(6.dp))
            }
            row.video.channel.takeIf { it.isNotBlank() }?.let {
                HubBadge(it, soft = true)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                relativeReleaseTime(row.video.published),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HubBadge(text: String, soft: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (soft) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            color = if (soft) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

private val releaseParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

/** "2 hours ago" from the feed's ISO stamp; empty when unparseable. */
internal fun relativeReleaseTime(iso: String): String {
    val at = runCatching { releaseParser.parse(iso)?.time }.getOrNull() ?: return ""
    if (at <= 0L || at > System.currentTimeMillis() + 60_000L) return ""
    return DateUtils.getRelativeTimeSpanString(
        at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
    ).toString()
}
