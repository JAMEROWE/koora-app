package com.koora.app.ui.components

import android.os.Build
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.koora.app.R
import com.koora.app.data.Match
import com.koora.app.data.Team
import com.koora.app.ui.isLive
import com.koora.app.ui.scoreLabel
import com.koora.app.ui.statusLabel
import com.koora.app.ui.theme.LiveRed

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
/**
 * Flag emoji for a domestic league's country, or a trophy for international
 * competitions (no national flag is correct for those). Unknown codes show
 * no icon. On devices without flag-emoji support the regional indicator
 * degrades to the country letters (e.g. "MA"), which stays meaningful.
 */
private fun flagFor(code: String?): String? = when (code) {
    "BOT" -> "\uD83C\uDDF2\uD83C\uDDE6" // Morocco
    "FL1" -> "\uD83C\uDDEB\uD83C\uDDF7" // France
    "PD" -> "\uD83C\uDDEA\uD83C\uDDF8"  // Spain
    "SA" -> "\uD83C\uDDEE\uD83C\uDDF9"  // Italy
    "BL1" -> "\uD83C\uDDE9\uD83C\uDDEA" // Germany
    "DED" -> "\uD83C\uDDF3\uD83C\uDDF1" // Netherlands
    "PPL" -> "\uD83C\uDDF5\uD83C\uDDF9" // Portugal
    "BSA" -> "\uD83C\uDDE7\uD83C\uDDF7" // Brazil
    // England's own flag (tag-sequence emoji) where the platform emoji font
    // supports subdivision flags (Android 13+); a readable "ENG" label
    // elsewhere - never a black flag posing as an identifier.
    "PL" -> if (Build.VERSION.SDK_INT >= 33) {
        "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F"
    } else {
        "ENG"
    }
    "CL", "EC", "EL", "WC", "CLI" -> "\uD83C\uDFC6" // international competition
    else -> null
}

@Composable
internal fun LeagueHeaderCard(
    title: String,
    count: Int,
    live: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    code: String? = null
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
        flagFor(code)?.let { flag ->
            Text(flag, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(6.dp))
        }
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
    // Side-by-side "home vs away" at every width and font scale: immediate
    // matchup recognition is the user's chosen arrangement. Names never
    // shrink and never break mid-word; each side picks the most complete
    // grounded label that fits whole (full name, API shortName, or TLA -
    // the badge shows the same TLA), and the full name stays one tap away
    // via the badge or match detail.
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onMatch)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VersusContent(m, Modifier.weight(1f), onTeam)
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

@Composable
private fun VersusContent(m: Match, modifier: Modifier = Modifier, onTeam: (Team) -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // 48dp touch target around the 26dp badge (Android accessibility
        // minimum) - the visible badge size is unchanged.
        Box(
            Modifier
                .size(48.dp)
                .clickable(onClick = { onTeam(m.homeTeam) }),
            contentAlignment = Alignment.Center
        ) {
            TeamBadge(m.homeTeam.name, m.homeTeam.tla, 26.dp)
        }
        Spacer(Modifier.width(2.dp))
        AdaptiveTeamName(
            m.homeTeam,
            Modifier.weight(1f),
            textAlign = TextAlign.End
        )
        Column(
            Modifier.width(60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val score = scoreLabel(m)
            Text(
                score,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            // The kickoff time is never shown twice: when status equals the
            // score slot (upcoming matches), only the score slot renders.
            val status = statusLabel(m)
            if (status != score) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLive(m)) {
                        LivePulseDot()
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLive(m)) LiveRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        AdaptiveTeamName(
            m.awayTeam,
            Modifier.weight(1f)
        )
        Spacer(Modifier.width(2.dp))
        Box(
            Modifier
                .size(48.dp)
                .clickable(onClick = { onTeam(m.awayTeam) }),
            contentAlignment = Alignment.Center
        ) {
            TeamBadge(m.awayTeam.name, m.awayTeam.tla, 26.dp)
        }
    }
}

/**
 * Conventional shortening built only from the club's own words: drops the
 * generic connector words ("Club", "de", "FC") and keeps the identity words
 * - "Raja Club Athletic" -> "Raja Athletic", "Olympique de Marseille" ->
 * "Olympique Marseille". Identity-bearing tokens (acronyms like FAR, suffixes
 * like AC/United/City) are never dropped, and nothing is invented or
 * appended. Returns null when there is nothing safe to drop, so the caller
 * falls back to the TLA instead of a mangled name.
 */
internal fun conventionalShort(name: String): String? {
    val filler = setOf("Club", "de", "del", "la", "FC", "CF")
    val words = name.split(' ').filter { it.isNotBlank() }
    if (words.size < 3) return null
    val kept = listOf(words.first()) + words.drop(1).filter { it !in filler }
    if (kept.size < 2 || kept.size == words.size) return null
    // The result must keep a real identity word, not just connectors.
    return if (kept.any { it.length >= 3 && it !in filler }) kept.joinToString(" ") else null
}

/**
 * Team label in a versus row, always at the standard body size. Picks the
 * most complete grounded label that fits the column whole (full name, then
 * API shortName, then TLA - which the badge also shows). A label is only
 * chosen when its longest word fits the column AND the whole label fits two
 * lines, so names never break mid-word and never shrink; the full name is
 * one tap away via the badge or the match detail.
 */
@Composable
private fun AdaptiveTeamName(team: Team, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val style = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxWidthPx = constraints.maxWidth
        // Most complete first: full name, API shortName, the club's own
        // words minus filler, then the TLA (which the badge also shows).
        val candidates = remember(team) {
            listOfNotNull(
                team.name,
                team.shortName?.takeIf { it.isNotBlank() },
                conventionalShort(team.name),
                team.tla?.takeIf { it.isNotBlank() }
            ).distinct()
        }
        val label = candidates.firstOrNull { candidate ->
            val longest = candidate.split(' ', '-').maxByOrNull { it.length } ?: candidate
            if (measurer.measure(longest, style = style).size.width > maxWidthPx) {
                return@firstOrNull false
            }
            !measurer.measure(
                candidate,
                style = style,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                constraints = Constraints(maxWidth = maxWidthPx)
            ).hasVisualOverflow
        } ?: candidates.last()
        Text(
            label,
            Modifier.fillMaxWidth(),
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = style
        )
    }
}
