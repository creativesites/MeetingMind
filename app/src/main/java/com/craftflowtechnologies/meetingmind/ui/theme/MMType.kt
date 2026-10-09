package com.craftflowtechnologies.meetingmind.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.R

/** Inter for all UI text. */
val InterFamily = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold)
)

/** Outfit 600, used by the Display token only. */
val OutfitFamily = FontFamily(Font(R.font.outfit_600, FontWeight.SemiBold))

/** The text-size personalization steps (DESIGN_SYSTEM §3). */
object MMTextScale {
    const val Small = 0.9f
    const val Default = 1.0f
    const val Large = 1.15f
    const val XLarge = 1.3f
    val steps = listOf(Small, Default, Large, XLarge)
}

/** Multiplier applied to every [MMTypography] token. Default 1.0. */
val LocalMMTextScale = staticCompositionLocalOf { MMTextScale.Default }

/** The nine type tokens (DESIGN_SYSTEM §3). Build with [mmTypography]. */
@Immutable
data class MMTypography(
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val secondary: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    val scripture: TextStyle
)

private fun style(
    family: FontFamily, weight: FontWeight, size: Float, line: Float, scale: Float, tracking: Float = 0f
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp * scale,
    lineHeight = line.sp * scale,
    letterSpacing = if (tracking == 0f) TextUnit.Unspecified else tracking.sp * scale
)

fun mmTypography(scale: Float = MMTextScale.Default) = MMTypography(
    display = style(OutfitFamily, FontWeight.SemiBold, 30f, 36f, scale),
    title = style(InterFamily, FontWeight.SemiBold, 22f, 28f, scale),
    heading = style(InterFamily, FontWeight.SemiBold, 17f, 24f, scale),
    body = style(InterFamily, FontWeight.Normal, 15f, 22f, scale),
    bodyStrong = style(InterFamily, FontWeight.Medium, 15f, 22f, scale),
    secondary = style(InterFamily, FontWeight.Normal, 13f, 18f, scale),
    caption = style(InterFamily, FontWeight.Medium, 12f, 16f, scale),
    overline = style(InterFamily, FontWeight.SemiBold, 11f, 14f, scale, tracking = 0.6f),
    scripture = style(FontFamily.Serif, FontWeight.Normal, 17f, 28f, scale)
)

/** Material's typography, mapped onto the tokens so Material components inherit them. */
fun materialTypographyFor(t: MMTypography) = Typography(
    displayLarge = t.display, displayMedium = t.display, displaySmall = t.display,
    headlineLarge = t.title, headlineMedium = t.title, headlineSmall = t.heading,
    titleLarge = t.title, titleMedium = t.heading, titleSmall = t.bodyStrong,
    bodyLarge = t.body, bodyMedium = t.body, bodySmall = t.secondary,
    labelLarge = t.bodyStrong, labelMedium = t.caption, labelSmall = t.overline
)
