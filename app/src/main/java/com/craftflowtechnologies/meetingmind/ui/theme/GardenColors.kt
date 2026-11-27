package com.craftflowtechnologies.meetingmind.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The time of day the Personal garden is drawn at. */
enum class GardenPhase { DAWN, DAY, DUSK, NIGHT }

/**
 * One painted garden: the sky, the hills, the soil and the glass and ink that sit on top of them.
 *
 * The scene is an illustration, so its colours are fixed per time of day rather than taken from
 * the theme. The theme still decides how bright it may get: in the dark theme, dawn and day are
 * painted as deep, moonlit versions so the garden never flashes a light screen at someone who
 * asked for dark.
 */
@Immutable
data class GardenPalette(
    val skyTop: Color,
    val skyMid: Color,
    val skyLow: Color,
    /** The sun or the moon. */
    val sun: Color,
    val sunGlow: Color,
    val hillFar: Color,
    val hillNear: Color,
    val ground: Color,
    /** Below the garden: the panel that holds the seed jar and the bar. */
    val soil: Color,
    /** Text and icons painted on the scene. */
    val ink: Color,
    val inkSoft: Color,
    /** Frosted cards over the scene, and their hairline. */
    val glass: Color,
    val glassLine: Color,
    /** 0 to 1: how much the stars show. */
    val stars: Float,
    /** 0 to 1: how much the fireflies glow. */
    val fireflies: Float,
    /** Sun or moon position as fractions of the scene's width and height. */
    val sunX: Float,
    val sunY: Float,
    /** True when [ink] is light: the scene is dark behind it. */
    val darkScene: Boolean
)

/** The lantern screen's colours for one theme. */
@Immutable
data class LanternPalette(
    /** The corners of the room, the middle, and the warm light around the lantern. */
    val edge: Color,
    val middle: Color,
    val glow: Color,
    val ink: Color,
    val inkSoft: Color,
    /** Quiet buttons' fill and hairline. */
    val quiet: Color,
    val line: Color
)

/** Fixed colours for the garden and its plants. */
object GardenColors {
    // The lantern gold, shared with the Bento circle tile, and the ink that reads on it.
    val Gold = Color(0xFFF5C86B)
    val GoldSoft = Color(0xFFF7D488)
    val GoldDeep = Color(0xFFF2BC55)
    val OnGold = Color(0xFF2A1F0E)
    // The soil panel under the garden is dark at every hour, so it has its own light ink and glass.
    val SoilInk = Color(0xFFFBF6EE)
    val SoilInkSoft = Color(0xFFD9D1E2)
    val SoilGlass = Color(0x17FFFFFF)
    val SoilLine = Color(0x2EFFFFFF)
    // Seed packets: paper packets with a printed band, the same in both themes (they are objects).
    val Packet = Color(0xFFFBF6EC)
    val PacketBand = Color(0xFFF1DDBA)
    val PacketInk = Color(0xFF2A1F14)
    val PacketInkSoft = Color(0xFF6B5A44)
    val PacketButton = Color(0xFF2A1F14)
    val PacketButtonInk = Color(0xFFFBF6EC)
    /** Gold text on a light scene's glass (4.5:1 or better). */
    val GoldInk = Color(0xFF7A4F12)
    val Ember = Color(0xFFFFE9AE)
    val EmberGlow = Color(0xFFFFD478)

    // Plants. Four leaf greens from oldest (darkest) to newest (lightest), the stem, the flower.
    val Stem = Color(0xFF5FA97C)
    val Leaf1 = Color(0xFF6DBA88)
    val Leaf2 = Color(0xFF7CC796)
    val Leaf3 = Color(0xFF93D6A8)
    val Leaf4 = Color(0xFFA9E2BA)
    /** A resting plant's leaves: the same shapes, drier. */
    val LeafResting = Color(0xFFA9B58A)
    val StemResting = Color(0xFF8C9A6E)
    val BloomCore = Color(0xFFC7792E)
    val Seed = Color(0xFFB47A3C)
    val Mound = Color(0x33FFFFFF)
    val Star = Color(0xFFFFFFFF)

    // Your sky: one tint per constellation, cycled in order (gold, blue, rose, mint). Fixed like the rest of the garden.
    val SkyBlue = Color(0xFF9FC7FF)
    val SkyRose = Color(0xFFF2A7C9)
    val SkyMint = Color(0xFF9FE3C2)
    val SkyTints: List<Color> = listOf(Gold, SkyBlue, SkyRose, SkyMint)

    fun palette(phase: GardenPhase, darkTheme: Boolean): GardenPalette = if (darkTheme) dark(phase) else light(phase)

    /** The lantern: a warm, lamplit room in the light theme and a night garden in the dark. */
    fun lantern(darkTheme: Boolean): LanternPalette = if (darkTheme) LanternPalette(
        edge = Color(0xFF0A0918), middle = Color(0xFF1A1533), glow = Color(0xFF3A2448),
        ink = Color(0xFFF7F2EA), inkSoft = Color(0xFFBFB5D6), quiet = Color(0x14FFFFFF), line = Color(0x33FFFFFF)
    ) else LanternPalette(
        edge = Color(0xFFF3E7D6), middle = Color(0xFFF8EEDF), glow = Color(0xFFF6D9A8),
        ink = Color(0xFF2A1F14), inkSoft = Color(0xFF5E4C36), quiet = Color(0x0F2A1F14), line = Color(0x332A1F14)
    )

    private fun light(phase: GardenPhase): GardenPalette = when (phase) {
        GardenPhase.DAWN -> GardenPalette(
            skyTop = Color(0xFFF4D4C6), skyMid = Color(0xFFF1B7A4), skyLow = Color(0xFFFBE6C4),
            sun = Color(0xFFFFF4D8), sunGlow = Color(0xA6FFE2AA),
            hillFar = Color(0xFFC7A2B6), hillNear = Color(0xFF9A7895), ground = Color(0xFF6C5776), soil = Color(0xFF4B3D56),
            ink = Color(0xFF2A1F33), inkSoft = Color(0xFF4E3F57), glass = Color(0x7AFFFFFF), glassLine = Color(0xB3FFFFFF),
            stars = 0f, fireflies = 0f, sunX = 0.64f, sunY = 0.5f, darkScene = false
        )
        GardenPhase.DAY -> GardenPalette(
            skyTop = Color(0xFF93CCE4), skyMid = Color(0xFFC8E7EE), skyLow = Color(0xFFF3F0E4),
            sun = Color(0xFFFFF7DC), sunGlow = Color(0xB3FFF5C8),
            hillFar = Color(0xFFA2CBAE), hillNear = Color(0xFF74AC89), ground = Color(0xFF4E8967), soil = Color(0xFF365F49),
            ink = Color(0xFF15241C), inkSoft = Color(0xFF2E4639), glass = Color(0x94FFFFFF), glassLine = Color(0xCCFFFFFF),
            stars = 0f, fireflies = 0f, sunX = 0.8f, sunY = 0.075f, darkScene = false
        )
        GardenPhase.DUSK -> GardenPalette(
            skyTop = Color(0xFF1B1838), skyMid = Color(0xFF4B2E5F), skyLow = Color(0xFFE8957A),
            sun = Color(0xFFFFD7A0), sunGlow = Color(0x8CFFAA6E),
            hillFar = Color(0xFF3B2F5D), hillNear = Color(0xFF2A2347), ground = Color(0xFF1F1A34), soil = Color(0xFF16122A),
            ink = Color(0xFFFBF6EE), inkSoft = Color(0xFFDCD3EA), glass = Color(0x6B14102C), glassLine = Color(0x33FFFFFF),
            stars = 0.75f, fireflies = 1f, sunX = 0.6f, sunY = 0.53f, darkScene = true
        )
        GardenPhase.NIGHT -> GardenPalette(
            skyTop = Color(0xFF090819), skyMid = Color(0xFF17153C), skyLow = Color(0xFF2C265E),
            sun = Color(0xFFF3EEDD), sunGlow = Color(0x47DCDCFF),
            hillFar = Color(0xFF1D1A3E), hillNear = Color(0xFF15122F), ground = Color(0xFF100D23), soil = Color(0xFF0B0918),
            ink = Color(0xFFF4F1FA), inkSoft = Color(0xFFC9C3DD), glass = Color(0x80141232), glassLine = Color(0x29FFFFFF),
            stars = 1f, fireflies = 1f, sunX = 0.8f, sunY = 0.075f, darkScene = true
        )
    }

    /** The dark theme: every phase is a night scene, tinted by the hour, so nothing glares. */
    private fun dark(phase: GardenPhase): GardenPalette = when (phase) {
        GardenPhase.DAWN -> GardenPalette(
            skyTop = Color(0xFF15142C), skyMid = Color(0xFF3A2A4E), skyLow = Color(0xFF8A5A64),
            sun = Color(0xFFF6D9B8), sunGlow = Color(0x59E8A27A),
            hillFar = Color(0xFF332A4A), hillNear = Color(0xFF241F3A), ground = Color(0xFF1A162C), soil = Color(0xFF121022),
            ink = Color(0xFFF7F1EA), inkSoft = Color(0xFFD6CBDC), glass = Color(0x6B14102C), glassLine = Color(0x2EFFFFFF),
            stars = 0.45f, fireflies = 0.4f, sunX = 0.64f, sunY = 0.5f, darkScene = true
        )
        GardenPhase.DAY -> GardenPalette(
            skyTop = Color(0xFF0E1C2A), skyMid = Color(0xFF1C3442), skyLow = Color(0xFF345A5C),
            sun = Color(0xFFE8F0E6), sunGlow = Color(0x40B8E0D0),
            hillFar = Color(0xFF203A38), hillNear = Color(0xFF172C2B), ground = Color(0xFF11201F), soil = Color(0xFF0C1716),
            ink = Color(0xFFEFF5F1), inkSoft = Color(0xFFC3D3CB), glass = Color(0x70081A1A), glassLine = Color(0x29FFFFFF),
            stars = 0.2f, fireflies = 0f, sunX = 0.8f, sunY = 0.075f, darkScene = true
        )
        GardenPhase.DUSK -> GardenPalette(
            skyTop = Color(0xFF120F28), skyMid = Color(0xFF34224A), skyLow = Color(0xFFA2655A),
            sun = Color(0xFFF2C894), sunGlow = Color(0x66E88A5E),
            hillFar = Color(0xFF2C2348), hillNear = Color(0xFF1F1A37), ground = Color(0xFF17132A), soil = Color(0xFF100D20),
            ink = Color(0xFFFBF6EE), inkSoft = Color(0xFFD6CDE4), glass = Color(0x73100C24), glassLine = Color(0x29FFFFFF),
            stars = 0.85f, fireflies = 1f, sunX = 0.6f, sunY = 0.53f, darkScene = true
        )
        GardenPhase.NIGHT -> GardenPalette(
            skyTop = Color(0xFF050410), skyMid = Color(0xFF100E2C), skyLow = Color(0xFF1F1A48),
            sun = Color(0xFFE6E1D2), sunGlow = Color(0x33C8C8F0),
            hillFar = Color(0xFF16132F), hillNear = Color(0xFF0F0D24), ground = Color(0xFF0B091A), soil = Color(0xFF070612),
            ink = Color(0xFFF1EEF8), inkSoft = Color(0xFFC2BCD8), glass = Color(0x80100E28), glassLine = Color(0x24FFFFFF),
            stars = 1f, fireflies = 0.9f, sunX = 0.8f, sunY = 0.075f, darkScene = true
        )
    }
}
