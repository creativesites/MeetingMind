package com.craftflowtechnologies.meetingmind.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colours for content drawn on a fixed dark backdrop (the prayer room, the testimony cards). They do not follow the
 * light or dark theme on purpose: the backdrop is dark in both. Values are the exact ones those screens always used.
 */
val FixedWhite: Color = Color.White

/** The live prayer room's aurora and accents. */
object PrayRoomColors {
    val Ink = Color(0xFF07060F)
    val Gold = Color(0xFFF6D365)
    val Amber = Color(0xFFE8A33A)
    val Cyan = Color(0xFF5EE7FF)
    val Indigo = Color(0xFF6366F1)
    val Violet = Color(0xFFA78BFA)
    val Rose = Color(0xFFF472B6)
    val Night = Color(0xFF1E1B4B)
    val Sky = Color(0xFF0EA5E9)
    val Slate = Color(0xFF64748B)
    val SlateDeep = Color(0xFF334155)
    val Red = Color(0xFFEF4444)
    val Blush = Color(0xFFFCA5A5)
}
