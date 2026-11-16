package com.craftflowtechnologies.meetingmind.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Learning space's additions to the design system (design/learning-vertical-design-spec.md §7).
 *
 * Learning uses the shared [MM] tokens for everything else. These are the few roles the shared
 * palette does not have: a warm "paper" surface for study material, and "clay" for "needs your
 * attention" (never "error": Learning does not use danger for a wrong answer).
 */
@Immutable
data class LearningPalette(
    /** Flashcards, definition panels, "ready to test yourself". */
    val paper: Color,
    /** Needs review, not quite. 4.8:1 on Paper. */
    val clay: Color,
    val clayWash: Color,
    val successWash: Color
)

val PaperLearningPalette = LearningPalette(
    paper = Color(0xFFFFFDF8),
    clay = Color(0xFFB45309),
    clayWash = Color(0x14B45309),
    successWash = Color(0x1415803D)
)

val GraphiteLearningPalette = LearningPalette(
    paper = Color(0xFF141D2B),
    clay = Color(0xFFF0A35E),
    clayWash = Color(0x1FF0A35E),
    successWash = Color(0x1F4ADE80)
)

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
        @Composable @ReadOnlyComposable get() = if (MM.colors.isDark) GraphiteLearningPalette else PaperLearningPalette
    val type: LearningTypography
        @Composable @ReadOnlyComposable get() = learningTypography(LocalMMTextScale.current)

    /** Reading screens use a 20dp gutter (decision D6); everything else keeps MM.space.l. */
    val readingGutter = 20.dp
}

/**
 * Wraps the Learning space in its Study accent (Ocean, DESIGN_SYSTEM §2). The semantic colours do
 * not change. Use once, at the space's root.
 */
@Composable
fun LearningAccent(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMMColors provides MMAccent.Ocean.on(MM.colors), content = content)
}
