package com.craftflowtechnologies.meetingmind.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The app's colours by role, one set per theme (docs/PRD_M0.md §5).
 *
 * Screens read these through the named tokens in Color.kt (`Ink`, `Line`, `SurfaceBase`…), which
 * follow the active theme. A colour that is content rather than UI — a notebook's own colour, a
 * highlight, a photo overlay — may stay literal; everything else comes from here.
 *
 * The palettes are deliberately quiet: neutral greys with no blue cast, one accent, hierarchy
 * carried by type and spacing rather than by colour.
 */
@Immutable
data class MMColors(
    val isDark: Boolean,
    /** Behind everything. */
    val background: Color,
    /** Cards, sheets, the editor page: the "paper". */
    val surface: Color,
    /** A surface lifted above another: menus, floating bars. */
    val surfaceRaised: Color,
    /** Inset panels inside a surface: search fields, word editor, cleanup panels. */
    val surfaceSunk: Color,
    /** A tinted canvas behind grouped cards. */
    val canvas: Color,
    /** Tracks: segmented controls, meters, pills. */
    val track: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    /** Text and icons on an [ink]-filled button. */
    val onInk: Color,
    val line: Color,
    val lineSoft: Color,
    val lineFaint: Color,
    val accent: Color,
    val accentWash: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val dangerWash: Color,
    /** The Faith space's warm gold, and a wash of it. */
    val gold: Color,
    val goldWash: Color,
    /** Gold text that stays readable on [goldWash]. */
    val goldInk: Color,
    val speaker2: Color,
    val speaker3: Color,
    val speaker4: Color,
    val recording: Color,
    val scrim: Color
)

/** Paper: warm, neutral light. */
val PaperColors = MMColors(
    isDark = false,
    background = Color(0xFFFAFAF9),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceSunk = Color(0xFFF5F5F4),
    canvas = Color(0xFFF4F4F2),
    track = Color(0xFFEDEDEB),
    ink = Color(0xFF18181B),
    inkSecondary = Color(0xFF52525B),
    inkMuted = Color(0xFF8B8B93),
    inkFaint = Color(0xFFC8C8CD),
    onInk = Color(0xFFFFFFFF),
    line = Color(0xFFE5E5E3),
    lineSoft = Color(0xFFEDEDEB),
    lineFaint = Color(0xFFF3F3F1),
    accent = Color(0xFF5B5BD6),
    accentWash = Color(0x1A5B5BD6),
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF16A34A),
    warning = Color(0xFFD97706),
    danger = Color(0xFFDC2626),
    dangerWash = Color(0x14DC2626),
    gold = Color(0xFFB7791F),
    goldWash = Color(0x1FB7791F),
    goldInk = Color(0xFF7A4E0F),
    speaker2 = Color(0xFF9D5BD2),
    speaker3 = Color(0xFF0F9D76),
    speaker4 = Color(0xFFD08A10),
    recording = Color(0xFFE5484D),
    scrim = Color(0x66000000)
)

/** Graphite: near-black with a whisper of warmth, never navy. The default. */
val GraphiteColors = MMColors(
    isDark = true,
    background = Color(0xFF111113),
    surface = Color(0xFF18181B),
    surfaceRaised = Color(0xFF212124),
    surfaceSunk = Color(0xFF0C0C0E),
    canvas = Color(0xFF111113),
    track = Color(0xFF26262A),
    ink = Color(0xFFEDEDEF),
    inkSecondary = Color(0xFFB4B4BB),
    inkMuted = Color(0xFF85858D),
    inkFaint = Color(0xFF52525A),
    onInk = Color(0xFF111113),
    line = Color(0xFF2C2C31),
    lineSoft = Color(0xFF232327),
    lineFaint = Color(0xFF1D1D20),
    accent = Color(0xFF9B9CF6),
    accentWash = Color(0x269B9CF6),
    onAccent = Color(0xFF111113),
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFF87171),
    dangerWash = Color(0x1FF87171),
    gold = Color(0xFFE0B25A),
    goldWash = Color(0x24E0B25A),
    goldInk = Color(0xFFF0CD87),
    speaker2 = Color(0xFFC39AF0),
    speaker3 = Color(0xFF4FD1A5),
    speaker4 = Color(0xFFF2B447),
    recording = Color(0xFFFF6369),
    scrim = Color(0x99000000)
)

val LocalMMColors = staticCompositionLocalOf { PaperColors }

/** How the app picks light or dark. */
enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("Match system") }

/**
 * Carries a colour picked for the light theme over to the current one. Light: unchanged. Dark: a
 * pale fill (a pastel card, a wash) becomes the dark surface with a hint of its hue; a dark
 * fill (a hero, a card) stays as it is: it already reads as a dark surface. Mid tones —
 * accents, golds — are kept. Literal dark text colours are replaced by the Ink tokens instead.
 * For literal colours that predate the theme system; new code uses the role tokens.
 */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun Color.forTheme(): Color {
    val c = LocalMMColors.current
    return adaptColor(this, c)
}

internal fun adaptColor(color: Color, c: MMColors): Color {
    if (!c.isDark || color == Color.Transparent || color == Color.Unspecified) return color
    val lum = color.copy(alpha = 1f).luminance()
    return when {
        lum > 0.72f -> androidx.compose.ui.graphics.lerp(c.surface, color.copy(alpha = 1f), 0.10f).copy(alpha = color.alpha)
        else -> color
    }
}
