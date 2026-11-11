package com.koora.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.koora.app.R
import com.koora.app.data.Match
import com.koora.app.data.MatchRepository
import com.koora.app.data.Team
import com.koora.app.data.VotesStore
import com.koora.app.ui.components.TeamBadge
import com.koora.app.ui.dateLabel
import com.koora.app.ui.next5Last5
import com.koora.app.ui.scoreLabel
import com.koora.app.ui.statusLabel
import com.koora.app.ui.theme.LiveRed
import com.koora.app.ui.voteLocked
import com.koora.app.ui.VotePick

/*
 * BOTTOM PANELS (v1.5.6, extended v1.6.0).
 *
 * VotePanel  — tap an upcoming (or live) match: pick Home / Draw / Away.
 *              Stage 1 keeps the pick on the phone; the community
 *              placeholder is honest ("when available"). Locked at
 *              kick-off. Closes on swipe-down or the Close button; system
 *              back always closes one step (ModalBottomSheet default).
 *
 * TeamPanel  — tap a team circle: next 5 matches (any competition, each
 *              row labeled with ITS league) and last 5 results. Rows are
 *              TAPPABLE: an upcoming fixture opens the prematch poll, a
 *              finished one opens the full match page with statistics.
 *              Data from v4/teams/{id}/matches (6h cache); if the
 *              endpoint is not on the current plan the panel says so
 *              instead of showing invented data.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VotePanel(
    match: Match,
    onDismiss: () -> Unit,
    onFullPage: (Long) -> Unit
) {
    val context = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    var pick by remember(match.id) { mutableStateOf(VotesStore.pick(context, match.id)) }
    val locked = voteLocked(match, now)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        ) {
            // Fixture header
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(match.homeTeam.name, match.homeTeam.tla, 30.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    match.homeTeam.name,
                    Modifier.weight(1.2f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    statusLabel(match),
                    Modifier.padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    match.awayTeam.name,
                    Modifier.weight(1.2f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                TeamBadge(match.awayTeam.name, match.awayTeam.tla, 30.dp)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.vote_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))

            if (locked) {
                Text(
                    stringResource(R.string.vote_locked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VotePick.entries.forEach { option ->
                        val selected = pick == option.key
                        val label = when (option) {
                            VotePick.HOME -> stringResource(R.string.vote_home)
                            VotePick.DRAW -> stringResource(R.string.vote_draw)
                            VotePick.AWAY -> stringResource(R.string.vote_away)
                        }
                        if (selected) {
                            Button(
                                onClick = { },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors()
                            ) { Text(label, maxLines = 1) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    pick = option.key
                                    VotesStore.setPick(context, match.id, option.key)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                }
            }

            pick?.let { p ->
                Spacer(Modifier.height(10.dp))
                val label = when (p) {
                    VotePick.HOME.key -> stringResource(R.string.vote_home)
                    VotePick.DRAW.key -> stringResource(R.string.vote_draw)
                    else -> stringResource(R.string.vote_away)
                }
                Text(
                    stringResource(R.string.vote_your_pick, label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.vote_community),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = {
                    onDismiss()
                    onFullPage(match.id)
                }) { Text(stringResource(R.string.full_match_page)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.panel_close)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamPanel(
    team: Team,
    onDismiss: () -> Unit,
    onMatch: (Long) -> Unit = {},
    onVote: (Match) -> Unit = {}
) {
    var fixtures by remember { mutableStateOf<List<Match>?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(team.id) {
        failed = false
        fixtures = null
        try {
            fixtures = MatchRepository.teamFixtures(team.id)
        } catch (_: Exception) {
            failed = true
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(team.name, team.tla, 34.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    team.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when {
                fixtures == null && !failed -> Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }

                failed -> Text(
                    stringResource(R.string.team_fixtures_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )

                else -> {
                    val (next, recent) = next5Last5(fixtures ?: emptyList(), team.id, System.currentTimeMillis())
                    TeamSection(
                        stringResource(R.string.team_next), next,
                        onRow = { m ->
                            if (m.status.equals("FINISHED", true)) onMatch(m.id) else onVote(m)
                        }
                    )
                    TeamSection(
                        stringResource(R.string.team_recent), recent,
                        onRow = { m -> onMatch(m.id) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.panel_close)) }
            }
        }
    }
}

@Composable
private fun TeamSection(title: String, list: List<Match>, onRow: (Match) -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(4.dp))
    if (list.isEmpty()) {
        Text(
            stringResource(R.string.team_fixtures_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Column {
            list.forEach { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = { onRow(m) })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        dateLabel(m),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(96.dp)
                    )
                    Text(
                        "${m.homeTeam.tla ?: m.homeTeam.name.take(3)} - ${m.awayTeam.tla ?: m.awayTeam.name.take(3)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        scoreLabel(m),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (m.status.equals("LIVE", true) || m.status == "IN_PLAY") LiveRed
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        m.competition?.name ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
