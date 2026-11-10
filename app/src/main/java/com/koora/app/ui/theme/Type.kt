@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.koora.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.koora.app.R

/*
 * Koora typography — "Night Stadium" (v1.5.3).
 *
 * Inter (variable font) carries Latin; Noto Sans Arabic (variable font)
 * is bundled right next to it so Arabic headlines and body text render
 * with the same weight rhythm. Both are OFL-licensed (see
 * assets/licenses/) and bundled as FILES — no runtime downloads, the
 * app works fully offline.
 *
 * Tabular figures ("tnum") on the score/time styles so digits never
 * jitter while a live score updates.
 */

private fun inter(weight: FontWeight, axis: Int) = Font(
    resId = R.font.inter,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axis))
)

private fun noto(weight: FontWeight, axis: Int) = Font(
    resId = R.font.notosansarabic,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axis))
)

val KooraFontFamily = FontFamily(
    inter(FontWeight.Normal, 400),
    inter(FontWeight.Medium, 500),
    inter(FontWeight.SemiBold, 600),
    inter(FontWeight.Bold, 700),
    noto(FontWeight.Normal, 400),
    noto(FontWeight.Bold, 700)
)

private const val TABULAR = "tnum"

val KooraTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(
            fontFamily = KooraFontFamily,
            fontFeatureSettings = TABULAR
        ),
        headlineSmall = headlineSmall.copy(fontFamily = KooraFontFamily),
        titleLarge = titleLarge.copy(
            fontFamily = KooraFontFamily,
            fontFeatureSettings = TABULAR
        ),
        titleMedium = titleMedium.copy(
            fontFamily = KooraFontFamily,
            fontFeatureSettings = TABULAR
        ),
        bodyLarge = bodyLarge.copy(fontFamily = KooraFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = KooraFontFamily),
        bodySmall = bodySmall.copy(fontFamily = KooraFontFamily),
        labelLarge = labelLarge.copy(fontFamily = KooraFontFamily),
        labelMedium = labelMedium.copy(fontFamily = KooraFontFamily),
        labelSmall = labelSmall.copy(fontFamily = KooraFontFamily)
    )
}
