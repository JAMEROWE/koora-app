package com.koora.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/*
 * STADIUM HERO (v1.6.0) — the app's "front". A floodlit night stadium
 * drawn in pure Canvas (no image assets: crisp on every density, adds
 * almost nothing to the APK):
 *   - night-sky gradient with two corner floodlight glows,
 *   - a lamp rig along the top rim of the stands,
 *   - the bowl (two stand arcs) with a subtle crowd shimmer,
 *   - the pitch in perspective with mown stripes, touchlines, halfway
 *     line and center circle.
 * Colors follow the Night Stadium identity: near-black blue sky, warm
 * floodlights, deep pitch green.
 */
@Composable
fun StadiumHero(modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        drawSky()
        drawFloodlightGlows()
        drawStands()
        drawLightRig()
        drawPitch()
    }
}

private val SkyTop = Color(0xFF070A10)
private val SkyBottom = Color(0xFF111827)
private val StandDark = Color(0xFF1A2332)
private val StandLight = Color(0xFF263349)
private val Crowd = Color(0xFF3B4A63)
private val LightWarm = Color(0xFFFFE082)
private val PitchBase = Color(0xFF14532D)
private val PitchStripe = Color(0xFF166534)
private val PitchLine = Color(0xFFB7F7C4)

private fun DrawScope.drawSky() {
    drawRect(Brush.verticalGradient(listOf(SkyTop, SkyBottom)))
}

/** Two big soft glows from the corner floodlight towers. */
private fun DrawScope.drawFloodlightGlows() {
    val w = size.width
    listOf(
        Offset(w * 0.14f, size.height * 0.18f),
        Offset(w * 0.86f, size.height * 0.18f)
    ).forEach { c ->
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(LightWarm.copy(alpha = 0.16f), Color.Transparent),
                center = c,
                radius = size.height * 0.75f
            ),
            radius = size.height * 0.75f,
            center = c
        )
    }
}

/** Y of the rim arc at x: apex (center height) -> base (edge height). */
private fun rimY(x: Float, w: Float, apex: Float, base: Float): Float {
    val t = ((x / w) - 0.5f) * 2f           // -1 .. 1
    val curve = 1f - t * t                  // 1 at center, 0 at edges
    return base - (base - apex) * curve
}

/** The bowl: two stand arcs with a dotted crowd shimmer. */
private fun DrawScope.drawStands() {
    val w = size.width
    val h = size.height
    val upperApex = h * 0.16f
    val upperBase = h * 0.52f
    val lowerApex = h * 0.36f
    val lowerBase = h * 0.64f

    val upper = Path().apply {
        moveTo(w * -0.05f, upperBase)
        quadraticBezierTo(w * 0.5f, upperApex, w * 1.05f, upperBase)
        lineTo(w * 1.05f, h * 0.66f)
        quadraticBezierTo(w * 0.5f, h * 0.34f, w * -0.05f, h * 0.66f)
        close()
    }
    drawPath(upper, StandDark)

    val lower = Path().apply {
        moveTo(w * -0.05f, lowerBase)
        quadraticBezierTo(w * 0.5f, lowerApex, w * 1.05f, lowerBase)
        lineTo(w * 1.05f, h * 0.74f)
        quadraticBezierTo(w * 0.5f, h * 0.48f, w * -0.05f, h * 0.74f)
        close()
    }
    drawPath(lower, StandLight)

    // Crowd shimmer: dotted rows hugging each tier
    val step = w / 34f
    var x = 0f
    while (x <= w) {
        drawCircle(Crowd, 2.2f, Offset(x, rimY(x, w, upperApex, upperBase) + 16f))
        drawCircle(
            Crowd.copy(alpha = 0.75f), 2.2f,
            Offset(x + step / 2f, rimY(x, w, upperApex, upperBase) + 38f)
        )
        drawCircle(
            Crowd.copy(alpha = 0.9f), 2.2f,
            Offset(x, rimY(x, w, lowerApex, lowerBase) + 14f)
        )
        x += step
    }
}

/** Floodlight rig: two masts + a lamp row with soft halos on the rim. */
private fun DrawScope.drawLightRig() {
    val w = size.width
    val h = size.height
    val rigY = h * 0.20f
    listOf(w * 0.14f, w * 0.86f).forEach { mx ->
        drawLine(
            Color(0xFF39465E),
            Offset(mx, rigY + 8f),
            Offset(mx, h * 0.55f),
            strokeWidth = 4f
        )
    }
    val lamps = 16
    for (i in 0 until lamps) {
        val lx = w * ((i + 0.5f) / lamps)
        val ly = rimY(lx, w, h * 0.115f, rigY)
        val alpha = if (i % 3 == 0) 0.95f else 0.8f
        drawRoundRect(
            color = LightWarm.copy(alpha = alpha),
            topLeft = Offset(lx - 7f, ly - 4f),
            size = Size(14f, 7f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(LightWarm.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(lx, ly),
                radius = 26f
            ),
            radius = 26f,
            center = Offset(lx, ly)
        )
    }
}

/** The pitch in perspective: stripes, touchlines, halfway line, circle. */
private fun DrawScope.drawPitch() {
    val w = size.width
    val h = size.height
    val topY = h * 0.74f
    val botY = h * 1.02f
    val topHalf = w * 0.34f
    val botHalf = w * 0.62f

    // Mown stripes first (each its own wedge, inside pitch bounds)
    val stripes = 8
    for (i in 0 until stripes) {
        if (i % 2 == 1) continue
        val t1 = i / stripes.toFloat()
        val t2 = (i + 1) / stripes.toFloat()
        val halfTop = topHalf * 2f
        val halfBot = botHalf * 2f
        val xTop1 = w / 2f - topHalf + halfTop * t1
        val xTop2 = w / 2f - topHalf + halfTop * t2
        val xBot1 = w / 2f - botHalf + halfBot * t1
        val xBot2 = w / 2f - botHalf + halfBot * t2
        val stripe = Path().apply {
            moveTo(xTop1, topY)
            lineTo(xTop2, topY)
            lineTo(xBot2, botY)
            lineTo(xBot1, botY)
            close()
        }
        drawPath(stripe, PitchStripe)
    }

    // Base fill with a soft light falloff (semi-transparent on top)
    val pitch = Path().apply {
        moveTo(w / 2f - topHalf, topY)
        lineTo(w / 2f + topHalf, topY)
        lineTo(w / 2f + botHalf, botY)
        lineTo(w / 2f - botHalf, botY)
        close()
    }
    drawPath(pitch, Brush.verticalGradient(listOf(PitchBase.copy(alpha = 0.55f), PitchBase)))

    // White work
    val line = PitchLine.copy(alpha = 0.55f)
    drawLine(line, Offset(w / 2f - topHalf, topY), Offset(w / 2f + topHalf, topY), 3f)
    drawLine(line, Offset(w / 2f - topHalf, topY), Offset(w / 2f - botHalf, botY), 3f)
    drawLine(line, Offset(w / 2f + topHalf, topY), Offset(w / 2f + botHalf, botY), 3f)
    drawLine(line, Offset(w / 2f - botHalf, botY), Offset(w / 2f + botHalf, botY), 3f)
    val midY = (topY + botY) / 2f
    val midHalf = (topHalf + botHalf) / 2f
    drawLine(line, Offset(w / 2f - midHalf, midY), Offset(w / 2f + midHalf, midY), 2.5f)
    drawCircle(
        color = line,
        radius = midHalf * 0.34f,
        center = Offset(w / 2f, midY + (botY - topY) * 0.08f),
        style = Stroke(width = 2.5f)
    )
}
