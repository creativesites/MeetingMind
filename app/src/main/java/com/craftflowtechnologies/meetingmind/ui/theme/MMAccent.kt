package com.craftflowtechnologies.meetingmind.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The personalization accents (DESIGN_SYSTEM §2). Each has a value for Paper (light) and one for
 * Graphite (dark). Indigo is the default and equals the palette's built-in accent.
 *
 * Gold's light value is 0xFF996515, not the spec's 0xFFB7791F: the spec value is 3.5:1 on Paper,
 * below the 4.5:1 required for accent text.
 */
enum class MMAccent(val label: String, val light: Color, val dark: Color) {
    Indigo("Indigo", Color(0xFF5B5BD6), Color(0xFF9B9CF6)),
    Ocean("Ocean", Color(0xFF0E7490), Color(0xFF5CC8DD)),
    Forest("Forest", Color(0xFF15803D), Color(0xFF5FD08A)),
    Gold("Gold", Color(0xFF996515), Color(0xFFE0B25A)),
    Rose("Rose", Color(0xFFBE185D), Color(0xFFF27AAE)),
    Graphite("Graphite", Color(0xFF3F3F46), Color(0xFFC9CDD6));

    /** Overrides [base]'s accent, accentWash (10% light / 15% dark) and onAccent (by contrast). */
    fun on(base: MMColors): MMColors {
        val c = if (base.isDark) dark else light
        return base.copy(
            accent = c,
            accentWash = c.copy(alpha = if (base.isDark) 0.15f else 0.10f),
            onAccent = onAccentFor(c)
        )
    }

    companion object {
        private val White = Color(0xFFFFFFFF)
        private val Near = Color(0xFF111113)

        /** White or near-black, whichever reads better on [fill]. */
        fun onAccentFor(fill: Color): Color =
            if (contrast(White, fill) >= contrast(Near, fill)) White else Near

        /** WCAG 2 contrast ratio between two opaque colours. */
        fun contrast(a: Color, b: Color): Double {
            val la = a.luminance().toDouble()
            val lb = b.luminance().toDouble()
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }
    }
}
