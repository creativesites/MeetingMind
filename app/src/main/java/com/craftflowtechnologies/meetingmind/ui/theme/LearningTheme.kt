package com.craftflowtechnologies.meetingmind.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Learning space's study roles, read from the app's own [MM] colours (founder, 2026-11-21:
 * Learning has no colours of its own). Learning uses the shared tokens for everything; these names
 * stay only so study screens can say what a colour is for:
 * - [paper] is the shared `surface` (flashcards, definition panels).
 * - [clay] is the shared `warning`: "needs your attention", never "error". Learning does not use
 *   danger for a wrong answer.
 * - [clayWash] and [successWash] are light washes of `warning` and `success`.
 */
@Immutable
data class LearningPalette(
    /** Flashcards, definition panels, "ready to test yourself". */
    val paper: Color,
    /** Needs review, not quite. */
    val clay: Color,
    val clayWash: Color,
    val successWash: Color
)

/** The study roles for [colors]: no hue of their own, only the app's surface, warning and success. */
fun learningPaletteOf(colors: MMColors): LearningPalette {
    val wash = if (colors.isDark) 0.12f else 0.08f
    return LearningPalette(
        paper = colors.surface,
        clay = colors.warning,
        clayWash = colors.warning.copy(alpha = wash),
        successWash = colors.success.copy(alpha = wash)
    )
}

/** Reading and prompt faces for study content. */
@Immutable
data class LearningTypography(
    /** Study-guide prose, definitions, answers, Ask answers. */
    val reading: TextStyle,
    /** Question stems and flashcard fronts. */
    val prompt: TextStyle,
    /** Timestamps only. */
    val mono: TextStyle
)

/**
 * The reading face. Decision D4 is to bundle Literata (one family, two weights). Until the font
 * files are added to res/font, the system serif stands in, as the scripture token already does.
 */
val LearningReadingFamily: FontFamily = FontFamily.Serif

fun learningTypography(scale: Float = MMTextScale.Default) = LearningTypography(
    reading = TextStyle(
        fontFamily = LearningReadingFamily, fontWeight = FontWeight.Normal,
        fontSize = 17.sp * scale, lineHeight = 28.sp * scale
    ),
    prompt = TextStyle(
        fontFamily = LearningReadingFamily, fontWeight = FontWeight.Medium,
        fontSize = 22.sp * scale, lineHeight = 30.sp * scale
    ),
    mono = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp * scale, lineHeight = 16.sp * scale
    )
)

object LearningTheme {
    val colors: LearningPalette
        @Composable @ReadOnlyComposable get() = learningPaletteOf(MM.colors)
    val type: LearningTypography
        @Composable @ReadOnlyComposable get() = learningTypography(LocalMMTextScale.current)

    /** Reading screens use a 20dp gutter (decision D6); everything else keeps MM.space.l. */
    val readingGutter = 20.dp
}

/**
 * Learning used to wrap its screens in its own Ocean accent. It now uses the app's accent and theme
 * like every other space, so this only draws [content]. Kept so existing callers still compile.
 */
@Composable
fun LearningAccent(content: @Composable () -> Unit) {
    content()
}
