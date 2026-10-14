package com.craftflowtechnologies.meetingmind.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Companion colours (docs/mvp/ZURI_EXPERIENCE.md §2.2). The companion wears the user's accent:
 * every body colour is derived from [MMColors.accent], already resolved for light or dark. Semantic
 * colours never apply, so the companion never turns red when something fails.
 *
 * Mixing is a straight per-channel sRGB lerp, like the visual reference's `pal()`, so the
 * companion matches `zuri-options.html` exactly (Compose's `lerp` mixes in Oklab, which shifts hues).
 */

/** The eyes, mouth and brows: one fixed slate ink in both themes. */
val companionInk = Color(0xFF151827)

/** Cheek blush: rose at 34%. */
val companionBlush = Color(0x57F4728A)

/** The catch-light in the eyes and the shine on the body. */
val companionLight = Color(0xFFFFFFFF)

/** Tongue and the Grateful heart. */
val companionTongue = Color(0xFFF47A8A)

private val PaperRuleGrey = Color(0xFFD4D4D8)
private val PageDarkPaper = Color(0xFFEEF0F5)
private val ShadowDark = Color(0x66000000)

@Immutable
data class CompanionPalette(
    val isDark: Boolean,
    val accent: Color,
    /** Body gradient start (top-left). */
    val bodyTop: Color,
    /** Body gradient end. */
    val bodyBottom: Color,
    /** Sprout, ears, wing, tail, paws' outline. */
    val deep: Color,
    /** Belly, snout, paws, the page fold. */
    val light: Color,
    /** T1 rim light. */
    val rim: Color,
    /** Page only: the paper stays light in both themes. */
    val paper: Color,
    val paperLine: Color,
    /** Page only: its text lines and waveform. */
    val lines: Color,
    val eye: Color,
    val blush: Color,
    val sparkle: Color,
    val gold: Color,
    /** The sleepy "z"s. */
    val mute: Color,
    val shadow: Color,
    /** The accent halo behind the body at 64 dp and up: 20% light, 32% dark. */
    val halo: Color,
    val catchLight: Color,
    /** The soft shine on the body: 42% light, 28% dark. */
    val shine: Color,
    val tongue: Color
)

/** Per-channel sRGB mix, `a` at t = 0 and `b` at t = 1. */
internal fun mixSrgb(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f
)

/** The companion palette for an accent in light ([dark] false) or dark. */
fun companionPalette(accent: Color, dark: Boolean): CompanionPalette {
    val white = companionLight
    val ink = PaperColors.ink
    val night = GraphiteColors.background
    return CompanionPalette(
        isDark = dark,
        accent = accent,
        bodyTop = if (dark) mixSrgb(accent, white, 0.22f) else mixSrgb(accent, white, 0.50f),
        bodyBottom = if (dark) mixSrgb(accent, night, 0.10f) else mixSrgb(accent, white, 0.12f),
        deep = if (dark) mixSrgb(accent, night, 0.42f) else mixSrgb(accent, ink, 0.25f),
        light = if (dark) mixSrgb(accent, white, 0.62f) else mixSrgb(accent, white, 0.80f),
        rim = if (dark) mixSrgb(accent, white, 0.60f) else mixSrgb(accent, white, 0.55f),
        paper = if (dark) mixSrgb(accent, PageDarkPaper, 0.90f) else mixSrgb(accent, white, 0.95f),
        paperLine = if (dark) mixSrgb(accent, night, 0.30f) else mixSrgb(accent, PaperRuleGrey, 0.72f),
        lines = if (dark) mixSrgb(accent, white, 0.10f) else mixSrgb(accent, white, 0.25f),
        eye = companionInk,
        blush = companionBlush,
        sparkle = if (dark) mixSrgb(accent, white, 0.25f) else accent,
        gold = if (dark) GraphiteColors.gold else PaperColors.gold,
        mute = if (dark) GraphiteColors.inkMuted else PaperColors.inkMuted,
        shadow = if (dark) ShadowDark else ink.copy(alpha = 0.09f),
        halo = accent.copy(alpha = if (dark) 0.32f else 0.20f),
        catchLight = white,
        shine = white.copy(alpha = if (dark) 0.28f else 0.42f),
        tongue = companionTongue
    )
}

/** The companion palette for the active theme colours (accent already applied). */
fun companionPalette(colors: MMColors): CompanionPalette = companionPalette(colors.accent, colors.isDark)

/** The companion palette for a design-system accent. */
fun companionPalette(accent: MMAccent, dark: Boolean): CompanionPalette =
    companionPalette(if (dark) accent.dark else accent.light, dark)
