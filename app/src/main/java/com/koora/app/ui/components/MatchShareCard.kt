package com.koora.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.koora.app.R
import com.koora.app.data.Match
import com.koora.app.ui.dateLabel
import com.koora.app.ui.scoreLabel
import com.koora.app.ui.statusLabel
import com.koora.app.ui.theme.KooraTheme
import com.koora.app.ui.theme.ThemeController
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/*
 * BRANDED SHARE CARD (approved design phase, 24 Nov): the match-detail share
 * action generates this card as an actual PNG and hands it to the Android
 * share sheet. The user picks the target app; the app itself never sends
 * anything. Night-stadium identity (the same drawn StadiumHero as the home
 * header), initials-based team badges (no club or league logos - IP-safe),
 * and the result line as text for apps that prefer text.
 */

private val CardBg = Color(0xFF0B1220)
private val CardAccent = Color(0xFF22C55E)
private val CardWarm = Color(0xFFFFE082)

@Composable
fun MatchShareCard(match: Match, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 360.dp, height = 450.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CardBg)
            .border(1.dp, Color(0xFF26334A), RoundedCornerShape(24.dp))
    ) {
        // Night-stadium identity art across the top (pure Canvas, no assets).
        StadiumHero(Modifier.align(Alignment.TopCenter))
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Brand wordmark first: whoever receives the PNG sees where it
            // came from before any result.
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = CardAccent
            )
            match.competition?.name?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFAAB4C8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            // Score block: badge over full team name on each side, score in
            // the middle — same side-by-side rule as the match lists.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TeamBadge(match.homeTeam.name, match.homeTeam.tla, 52.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        match.homeTeam.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    scoreLabel(match),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = CardWarm
                )
                Spacer(Modifier.width(8.dp))
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TeamBadge(match.awayTeam.name, match.awayTeam.tla, 52.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        match.awayTeam.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${statusLabel(match)} • ${dateLabel(match)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAB4C8)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.share_card_brand),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF7C8AA5)
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Renders [MatchShareCard] off-screen to a PNG in the app cache and opens
 * the system share sheet with the image plus the result line as text. Runs
 * the render a frame after the off-screen view attaches so composition has
 * run before the bitmap is drawn.
 */
fun shareMatchCard(context: Context, match: Match) {
    val activity = context.findActivity() ?: return
    val density = activity.resources.displayMetrics.density
    val widthPx = (360 * density).roundToInt()
    val heightPx = (450 * density).roundToInt()
    val host = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
    val cardView = ComposeView(activity).apply {
        visibility = View.INVISIBLE
        setContent {
            KooraTheme(themeMode = ThemeController.DARK) {
                MatchShareCard(match)
            }
        }
    }
    host.addView(cardView, ViewGroup.LayoutParams(widthPx, heightPx))
    // Two posts: the first frame runs composition, the second draws it.
    cardView.post {
        cardView.post {
            val w = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
            val h = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
            cardView.measure(w, h)
            cardView.layout(0, 0, widthPx, heightPx)
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            cardView.draw(Canvas(bitmap))
            host.removeView(cardView)
            cardView.disposeComposition()

            val dir = File(activity.cacheDir, "shared")
            dir.mkdirs()
            val out = File(dir, "match_${match.id}.png")
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(
                activity, activity.packageName + ".fileprovider", out
            )

            val ft = match.score.fullTime
            val score = if (match.status == "FINISHED" && ft?.home != null && ft.away != null)
                "${ft.home}-${ft.away}" else "vs"
            val text = buildString {
                append("${match.homeTeam.name} $score ${match.awayTeam.name}")
                match.competition?.name?.takeIf { it.isNotBlank() }?.let { append(" - $it") }
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(
                Intent.createChooser(send, activity.getString(R.string.share_chooser_title))
            )
        }
    }
}
