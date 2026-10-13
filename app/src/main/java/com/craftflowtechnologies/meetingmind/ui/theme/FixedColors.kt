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

/**
 * The Today home's illustrated tiles. Their artwork is light in both themes, so the ink drawn on it stays dark in
 * both too, the same reasoning as [PrayRoomColors].
 */
object BentoColors {
    val Ink = Color(0xFF1C1F2A)
    val InkSoft = Color(0xFF4A4560)
    val Paper = Color(0xFFFFFFFF)
    val Violet = Color(0xFF6D5FD0)
    val GoldInk = Color(0xFF7A4F24)
    val GoldText = Color(0xFF6B5A44)
    val GoldPill = Color(0xFF5B3F1F)
    val ClayInk = Color(0xFF7A4F3A)
    val ClayText = Color(0xFF5D5249)
    val TealInk = Color(0xFF2F6F6A)
    val TealText = Color(0xFF355E5A)
    val CircleGold = Color(0xFFF5C86B)
    val CircleLilac = Color(0xFFC9BDE6)
    val CircleMint = Color(0xFF9FD1C9)
    val IndigoInk = Color(0xFF3F3A74)
    val BrownInk = Color(0xFF6B4A3A)
    val BrownText = Color(0xFF6B5A4E)
    val PrayerScrim = Color(0xFF1C1A44)
}

/** The artwork behind the Today home's illustrated tiles (drawn in code, see feature/today/home/BentoScenes.kt). */
object BentoArtColors {
    // Faith: sunrise over lavender hills.
    val SunriseTop = Color(0xFFF9E6D8)
    val SunriseMid = Color(0xFFEFDCEA)
    val SunriseLow = Color(0xFFDCD3F2)
    val SunGlow = Color(0xFFFFF6D8)
    val SunDisc = Color(0xFFFFF3CF)
    val HillLight = Color(0xFFC9BDE6)
    val HillMid = Color(0xFFA99BD6)
    val HillDeep = Color(0xFF8A7CC6)

    // Devotional: warm paper, a beam of light, an open book.
    val PaperCenter = Color(0xFFFFFAF0)
    val PaperEdge = Color(0xFFEEDCC0)
    val Beam = Color(0xFFFFF8EA)
    val BookEdge = Color(0xFFA7784A)

    // Prayer: night sky, crescent moon, stars.
    val NightTop = Color(0xFF241F52)
    val NightLow = Color(0xFF5A4F9A)
    val MoonGlow = Color(0xFFF6E7B8)
    val Moon = Color(0xFFF6EED8)
    val MoonCut = Color(0xFF2E2A62)
    val NightHill = Color(0xFF1C1A44)

    // Create: watercolour blooms.
    val Watercolor = Color(0xFFF4EFE9)
    val BloomClay = Color(0xFFF2B8A2)
    val BloomSage = Color(0xFFB9D8CF)
    val BloomGold = Color(0xFFF6DC9C)

    // Circles: rings and a member.
    val CirclePaper = Color(0xFFEEF5F3)
    val Ring = Color(0xFF5FA7A0)

    // Work: dot grid and trend line.
    val Dot = Color(0xFFB9B3D6)

    // Personal: a window and a plant.
    val WallTop = Color(0xFFFBF1E6)
    val WallBottom = Color(0xFFEEDCCB)
    val Arch = Color(0xFFFFFAF2)
    val ArchEdge = Color(0xFFE0CDB9)
    val Mullion = Color(0xFFE8D8C6)
    val LeafSoft = Color(0xFF9FC7B0)
    val LeafGreen = Color(0xFF7FB59B)

    // Journal: ruled paper.
    val RuledPaper = Color(0xFFF8F4EA)
    val Rule = Color(0xFFD9D2C4)
    val CornerFold = Color(0xFFE9DCC2)
}
