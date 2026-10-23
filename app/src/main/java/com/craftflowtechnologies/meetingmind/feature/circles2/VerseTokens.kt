package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * Colours for a scripture surface in a circle, one set for both themes. Dark mode is a deep warm espresso with gold
 * lettering and warm off-white scripture (not the washed-out grey the sunk surface gave), light mode the warm parchment.
 */
data class VerseTokens(val background: Color, val border: Color, val title: Color, val meta: Color, val scripture: Color, val muted: Color) {
    companion object {
        @Composable
        fun current(): VerseTokens =
            if (MM.colors.isDark) VerseTokens(
                background = Color(0xFF231F1A), border = Color(0xFF7A6432), title = Color(0xFFFFD580),
                meta = Color(0xFFE6C77A), scripture = Color(0xFFF5EFE6), muted = Color(0xFFB8AD9C)
            ) else VerseTokens(
                background = CirclesPalette.WarmBg, border = CirclesPalette.WarmBorder, title = CirclesPalette.WarmInk,
                meta = CirclesPalette.WarmInk.copy(alpha = 0.8f), scripture = MM.colors.ink, muted = MM.colors.inkMuted
            )
    }
}
