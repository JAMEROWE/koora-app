package com.koora.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koora.app.R
import com.koora.app.data.Match
import com.koora.app.data.Team
import com.koora.app.ui.isLive
import com.koora.app.ui.scoreLabel
import com.koora.app.ui.statusLabel
import com.koora.app.ui.theme.LiveRed

/** Compact row label: API short name when available (e.g. "Man City"), else full name. */
private fun Team.rowName(): String = shortName?.takeIf { it.isNotBlank() } ?: name

/**
 * Team name in a match row. Wraps whole words over two lines; if the longest
 * single word is wider than the column (narrow phones, large font scales),
 * the font steps down until the word fits, so names never break mid-word.
 */
@Composable
private fun TeamNameText(name: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val base = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxWidthPx = constraints.maxWidth
        val longest = name.split(' ', '-').maxByOrNull { it.length } ?: name
        var fontSize = base.fontSize
        while (fontSize > 9.sp &&
            measurer.measure(longest, style = base.copy(fontSize = fontSize)).size.width > maxWidthPx
        ) {
            fontSize = (fontSize.value - 0.5f).sp
        }
        Text(
            name,
            Modifier.fillMaxWidth(),
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = base.copy(fontSize = fontSize)
        )
    }
}

/*
 * SHARED MATCH LIST PIECES (v1.6.0) — the Live tab and the Results tab
 * render the exact same section anatomy, so the pieces live here once:
 *
 *   DayTitle          — big label between day blocks (Today / Tomorrow).
 *   LeagueHeaderCard  — accordion header: green brand bar + league name
 *                       + LIVE pill + match count + chevron (spec §2.2).
 *   ExpandCollapseRow — the top Expand All / Collapse All toggle.
 *   MatchRow          — one fixture: badges, score, status, favorite star.
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
 * Section header (spec §2.2, v1.10.0): green brand bar + league name +
 * pulsing LIVE pill when the section holds live matches + match count
 * badge + expand/collapse chevron. Tapping the header toggles the
 * accordion; the chevron rotation is animated. League crest images are
 * NOT rendered — the free providers give no emblem URLs, and a drawn
 * fake crest would break the no-fake rule.
 */
@Composable
internal fun LeagueHeaderCard(
    title: String,
    count: Int,
    live: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else 180f,
        label = "chevron"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
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
        if (live) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = LiveRed.copy(alpha = 0.16f)
            ) {
                Row(
                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LivePulseDot()
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = LiveRed
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                count.toString(),
                Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { rotationZ = rotation }
        )
    }
}

/**
 * Spec §2.2: the top Expand All / Collapse All toggle for the whole
 * feed (Live and Results). Two small stateless buttons — the screen
 * owns the collapsed-state map.
 */
@Composable
internal fun ExpandCollapseRow(onExpandAll: () -> Unit, onCollapseAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
    ) {
        TextButton(onClick = onExpandAll) {
            Text(stringResource(R.string.expand_all), style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = onCollapseAll) {
            Text(stringResource(R.string.collapse_all), style = MaterialTheme.typography.labelMedium)
        }
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
        // 48dp touch target around the 26dp badge (Android accessibility
        // minimum) — the visible badge size is unchanged.
        Box(
            Modifier
                .size(48.dp)
                .clickable(onClick = { onTeam(m.homeTeam) }),
            contentAlignment = Alignment.Center
        ) {
            TeamBadge(m.homeTeam.name, m.homeTeam.tla, 26.dp)
        }
        Spacer(Modifier.width(4.dp))
        TeamNameText(
            m.homeTeam.rowName(),
            Modifier.weight(1.1f),
            textAlign = TextAlign.End
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(68.dp)
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
        TeamNameText(
            m.awayTeam.rowName(),
            Modifier.weight(1.1f)
        )
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .size(48.dp)
                .clickable(onClick = { onTeam(m.awayTeam) }),
            contentAlignment = Alignment.Center
        ) {
            TeamBadge(m.awayTeam.name, m.awayTeam.tla, 26.dp)
        }
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
