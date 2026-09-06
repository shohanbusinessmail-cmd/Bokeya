package com.shohan.bokeya.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.shohan.bokeya.R

/**
 * Typography built around **Hind Siliguri**, an open-source Bangla/Latin
 * typeface designed for UI. It is bundled rather than relying on the system
 * font because stock Bangla rendering varies wildly across OEM devices —
 * conjuncts (যুক্তাক্ষর) like ক্ত, স্ত্র and র‍্য break or clip on many of them.
 *
 * Bangla needs more vertical room than Latin: matras sit above the headline
 * and descenders below, so line heights here are generous, and
 * [LineHeightStyle.Trim.None] keeps the first line's matra from being clipped.
 */
val HindSiliguri = FontFamily(
    Font(R.font.hind_siliguri_light, FontWeight.Light),
    Font(R.font.hind_siliguri_regular, FontWeight.Normal),
    Font(R.font.hind_siliguri_medium, FontWeight.Medium),
    Font(R.font.hind_siliguri_semibold, FontWeight.SemiBold),
    Font(R.font.hind_siliguri_bold, FontWeight.Bold),
)

private val BanglaLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun bangla(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = HindSiliguri,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = BanglaLineHeight,
)

val BokeyaTypography = Typography(
    displayLarge = bangla(52, 64, FontWeight.Bold, -0.5),
    displayMedium = bangla(42, 52, FontWeight.Bold, -0.4),
    displaySmall = bangla(34, 44, FontWeight.SemiBold, -0.3),

    headlineLarge = bangla(30, 40, FontWeight.SemiBold, -0.2),
    headlineMedium = bangla(26, 36, FontWeight.SemiBold),
    headlineSmall = bangla(22, 32, FontWeight.SemiBold),

    titleLarge = bangla(20, 30, FontWeight.SemiBold),
    titleMedium = bangla(17, 26, FontWeight.Medium),
    titleSmall = bangla(15, 22, FontWeight.Medium),

    bodyLarge = bangla(16, 26),
    bodyMedium = bangla(14, 23),
    bodySmall = bangla(13, 20),

    labelLarge = bangla(14, 20, FontWeight.Medium),
    labelMedium = bangla(12, 17, FontWeight.Medium),
    labelSmall = bangla(11, 16, FontWeight.Medium),
)

/**
 * Dedicated styles for large money figures.
 *
 * Financial amounts are the thing users scan first, so they get tabular-feeling
 * weight and tight tracking. These are exposed separately from [Typography]
 * because Material's scale has no slot for "the one huge number on the screen".
 */
object AmountTypography {
    val hero = TextStyle(
        fontFamily = HindSiliguri,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.8).sp,
        lineHeightStyle = BanglaLineHeight,
    )
    val large = TextStyle(
        fontFamily = HindSiliguri,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
        lineHeightStyle = BanglaLineHeight,
    )
    val medium = TextStyle(
        fontFamily = HindSiliguri,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
        lineHeightStyle = BanglaLineHeight,
    )
    val small = TextStyle(
        fontFamily = HindSiliguri,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        lineHeightStyle = BanglaLineHeight,
    )
}
