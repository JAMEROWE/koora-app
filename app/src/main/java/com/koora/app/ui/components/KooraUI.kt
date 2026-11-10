package com.koora.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koora.app.data.TeamColors
import com.koora.app.ui.theme.LiveRed

/*
 * Shared "Night Stadium" components (v1.5.3):
 *  - TeamBadge: colored initials circle, generated locally (no crest
 *    assets — trademark-safe).
 *  - LivePulseDot: breathing dot for live moments.
 *  - SkeletonList / ShimmerBox: placeholder shapes while data loads.
 */

@Composable
fun TeamBadge(
    teamName: String,
    tla: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val colors = TeamColors.colorsFor(teamName)
    val label = tla?.takeIf { it.isNotBlank() && it.length <= 4 }
        ?: TeamColors.initialsFor(teamName)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(colors.bg))
            .border(0.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color(colors.fg),
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
fun LivePulseDot(
    color: Color = LiveRed,
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp
) {
    val transition = rememberInfiniteTransition(label = "livePulse")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePulseAlpha"
    )
    Box(
        modifier
            .size(dotSize)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
    )
}

/** Match-row-shaped skeleton placeholder shown instead of a spinner. */
@Composable
fun SkeletonList(rows: Int = 8, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        repeat(rows) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                ShimmerBox(
                    Modifier
                        .weight(1f)
                        .size(width = 80.dp, height = 16.dp)
                )
                Spacer(Modifier.width(10.dp))
                ShimmerBox(Modifier.size(width = 52.dp, height = 16.dp))
                Spacer(Modifier.width(10.dp))
                ShimmerBox(
                    Modifier
                        .weight(1f)
                        .size(width = 80.dp, height = 16.dp)
                )
                Spacer(Modifier.width(10.dp))
                ShimmerBox(Modifier.size(22.dp))
            }
        }
    }
}
