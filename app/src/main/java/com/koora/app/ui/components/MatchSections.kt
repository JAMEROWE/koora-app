package com.koora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.koora.app.R
import com.koora.app.data.Match
import com.koora.app.data.Team
import com.koora.app.ui.isLive
import com.koora.app.ui.scoreLabel
import com.koora.app.ui.statusLabel
import com.koora.app.ui.theme.LiveRed

/*
 * SHARED MATCH LIST PIECES (v1.6.0) — the Live tab and the Results tab
 * render the exact same section anatomy, so the pieces live here once:
 *
 *   DayTitle      — big label between day blocks (Today / Tomorrow).
 *   LeagueHeader  — green brand bar + real league name + match count.
 *   MatchRow      — one fixture: badges, score, status, favorite star.
 */

/** Big day label between day blocks (Today / Tomorrow). */
@Composable
internal fun DayTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

/** Small caption under a day title (e.g. the honest empty-state line). */
@Composable
internal fun DayEmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        textAlign = TextAlign.Center
    )
}

/**
 * Section header: green brand bar + real league name + match count.
 * This is what makes fixtures and results clearly grouped per league —
 * never a flat, unlabeled list.
 */
@Composable
internal fun LeagueHeader(title: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 18.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Thin divider between match rows. */
@Composable
internal fun MatchDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

/**
 * One fixture row, shared by Live + Results: tap -> poll or full page,
 * team circle -> team panel, star -> favorites. All features, always.
 */
@Composable
internal fun MatchRow(
    m: Match,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onMatch: () -> Unit,
    onTeam: (Team) -> Unit,
    showStar: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onMatch)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TeamBadge(
            m.homeTeam.name,
            m.homeTeam.tla,
            26.dp,
            modifier = Modifier.clickable(onClick = { onTeam(m.homeTeam) })
        )
        Spacer(Modifier.width(8.dp))
        Text(
            m.homeTeam.name,
            Modifier.weight(1.1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(88.dp)
        ) {
            Text(
                scoreLabel(m),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLive(m)) {
                    LivePulseDot()
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    statusLabel(m),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLive(m)) LiveRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            m.awayTeam.name,
            Modifier.weight(1.1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.width(8.dp))
        TeamBadge(
            m.awayTeam.name,
            m.awayTeam.tla,
            26.dp,
            modifier = Modifier.clickable(onClick = { onTeam(m.awayTeam) })
        )
        if (showStar) {
            IconToggleButton(
                checked = isFavorite,
                onCheckedChange = { onToggleFavorite() }
            ) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(R.string.fav_tab),
                    tint = if (isFavorite) Color(0xFFF5B301) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
