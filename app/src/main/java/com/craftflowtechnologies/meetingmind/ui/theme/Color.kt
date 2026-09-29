package com.craftflowtechnologies.meetingmind.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// CleanMyMac Vibrant Accent Palette
val CleanMacIndigo = Color(0xFF6366F1) // Electric Indigo / Royal Violet
val CleanMacPurple = Color(0xFFA855F7) // Vibrant Purple
val CleanMacMagenta = Color(0xFFEC4899) // Clean Radiant Pink/Magenta
val CleanMacBlue = Color(0xFF2563EB) // macOS System Blue
val CleanMacCyan = Color(0xFF06B6D4) // Aquatic Mint
val CleanMacEmerald = Color(0xFF10B981) // Clean Emerald Green
val CleanMacCoral = Color(0xFFF43F5E) // Crisp Coral / Recording
val CleanMacAmber = Color(0xFFF59E0B) // Amber Gold

// CleanMyMac Modern Light Theme Surfaces (Pristine, Airy & Crisp)
val LightCanvasBackground = Color(0xFFF4F6FB) // Crisp soft light canvas
val LightCardSurface = Color(0xFFFFFFFF) // Pure elevated card surface
val LightPillSurface = Color(0xFFEBF1F9) // Frosted pill / container
val LightCardElevated = Color(0xFFF8FAFC) // Top layer / inset surface
val LightBorderColor = Color(0xFFE2E8F0) // Subtle 1dp border outline
val LightBorderSubtle = Color(0xFFEEF2F6) // Inner separator outline

// Light Theme High-Contrast Typography
val LightTextPrimary = Color(0xFF0F172A) // Deep Slate Navy
val LightTextSecondary = Color(0xFF475569) // Mid Slate
val LightTextMuted = Color(0xFF94A3B8) // Light Muted Slate

// Compatibility aliases for existing references
val IndigoPrimary = CleanMacIndigo
val IndigoPrimaryLight = Color(0xFF818CF8)
val IndigoOnPrimary = Color(0xFFFFFFFF)
val IndigoPrimaryContainer = Color(0xFFEEF2FF)
val IndigoOnPrimaryContainer = Color(0xFF3730A3)

val VioletSecondary = CleanMacPurple
val VioletOnSecondary = Color(0xFFFFFFFF)
val VioletSecondaryContainer = Color(0xFFFAF5FF)
val VioletOnSecondaryContainer = Color(0xFF6B21A8)

val CyanTertiary = CleanMacCyan
val CyanOnTertiary = Color(0xFFFFFFFF)
val CyanTertiaryContainer = Color(0xFFECFEFF)
val CyanOnTertiaryContainer = Color(0xFF155E75)

// Dark Theme Surfaces (Retained for dark mode preference)
val DarkBackground = Color(0xFF0F1420)
val DarkSurface = Color(0xFF161E2E)
val DarkSurfaceVariant = Color(0xFF1E293B)
val DarkSurfaceElevated = Color(0xFF28354D)
val DarkOnBackground = Color(0xFFF8FAFC)
val DarkOnSurface = Color(0xFFF1F5F9)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)
val DarkOutline = Color(0xFF334155)
val DarkOutlineVariant = Color(0xFF1E293B)
val DarkCardBorder = Color(0x33FFFFFF)

// Semantic Accents
val SuccessGreen = CleanMacEmerald
val SuccessGreenContainer = Color(0xFFECFDF5)
val WarningAmber = CleanMacAmber
val WarningAmberContainer = Color(0xFFFFFBEB)
val ErrorRed = Color(0xFFEF4444)
val RecordingRed = CleanMacCoral
val InfoSky = CleanMacBlue

// Modern Speaker Colors (Vibrant, high-contrast palette)
val SpeakerColor1 = CleanMacIndigo
val SpeakerColor2 = CleanMacPurple
val SpeakerColor3 = CleanMacEmerald
val SpeakerColor4 = CleanMacAmber
val SpeakerColor5 = CleanMacCyan
val SpeakerColor6 = CleanMacMagenta

// CleanMyMac Iconic Gradient Brushes
val CleanMacCosmicBrush = Brush.linearGradient(
    colors = listOf(CleanMacIndigo, CleanMacPurple, CleanMacMagenta)
)

val CleanMacAquaBrush = Brush.linearGradient(
    colors = listOf(CleanMacBlue, CleanMacCyan)
)

val CleanMacEmeraldBrush = Brush.linearGradient(
    colors = listOf(CleanMacEmerald, CleanMacCyan)
)

val CleanMacSunsetBrush = Brush.linearGradient(
    colors = listOf(CleanMacCoral, CleanMacAmber)
)

val CleanMacSubtleCardBrush = Brush.linearGradient(
    colors = listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC))
)

// Legacy aliases
val HeroGradientBrush = CleanMacCosmicBrush
val WaveformGradientBrush = CleanMacAquaBrush
val BentoAccentBrush = CleanMacCosmicBrush

// ─────────────────────────────────────────────────────────────
// Recording page redesign tokens (see docs/recording-page-implementation.md §1.1)
// Purple is confined to: the dial ring, speaker 1 identity, active timestamps,
// citation chips, selection wash, and the "Show original"/scope text buttons.
// Every primary button/surface elsewhere uses the Ink scale, not purple.
// Aliased onto the existing CleanMyMac tokens above where the hex already matches,
// so this is additive — nothing above is renamed or removed.
// ─────────────────────────────────────────────────────────────

// Ink / text
val Ink: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.ink // 0xFF0F172A — primary text, primary buttons, active tab
val InkSecondary: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.inkSecondary // 0xFF475569 — secondary text, inactive controls
val InkMuted: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.inkMuted // 0xFF94A3B8 — metadata, captions, inactive tabs
val InkFaint: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.inkFaint // chevrons, gutter timestamps, disabled

// Lines / surfaces
val Line: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.line // 0xFFE2E8F0 — borders on interactive surfaces
val LineSoft: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.lineSoft // 0xFFEEF2F6 — section dividers, timeline spine
val LineFaint: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.lineFaint // list-row dividers
val SurfaceSunk: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.surfaceSunk // inline panels (word editor, notes, cleanup)
val SurfaceCanvas: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.canvas // 0xFFF4F6FB — only where a non-white canvas is used
val SurfaceTrack: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.track // 0xFFEBF1F9 — segmented-control track, meter track

// Accent — player, identity, citations
val Accent: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.accent // 0xFF6366F1 — progress ring, speaker 1, timestamps, links
val AccentWash: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.accentWash // 10% — citation chips, selection highlight

// Speaker colours — cycle Accent -> Speaker2 -> Speaker3 -> Speaker4 by first appearance,
// then persist per speaker id (never reshuffle). Speaker3 doubles as the diff "kept/changed"
// underline; Speaker4 doubles as the low-confidence flag colour.
val Speaker2: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.speaker2 // 0xFFA855F7
val Speaker3: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.speaker3 // 0xFF10B981
val Speaker4: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.speaker4 // 0xFFF59E0B

/**
 * The brand, taken from the app icon: a cyan-to-violet "M" on deep navy. One gradient for
 * everything that says "this is MeetingMind" — story rings, onboarding, the setup card, the tour.
 */
object Brand {
    val Navy = Color(0xFF060D2E)
    val NavyLift = Color(0xFF111A45)
    val Cyan = Color(0xFF3DDCFB)
    val Blue = Color(0xFF2F6BFF)
    val Indigo = Color(0xFF5B4BF5)
    val Violet = Color(0xFF9B6BFF)
    val Lilac = Color(0xFFD29BFF)
    /** Left to right, as on the icon. */
    val sweep = listOf(Cyan, Blue, Indigo, Violet, Lilac)
    /** A closed loop for rings (ends where it starts, so there's no seam). */
    val ring = listOf(Cyan, Blue, Indigo, Violet, Lilac, Cyan)
}

// ─────────────────────────────────────────────────────────────
// Role tokens added with the theme system (docs/PRD_M0.md §5). They follow the active theme.
// ─────────────────────────────────────────────────────────────

/** Behind everything. */
val Background: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.background
/** Cards, sheets and pages — what used to be plain white. */
val SurfaceBase: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.surface
/** Menus and floating bars, lifted above a surface. */
val SurfaceRaised: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.surfaceRaised
/** Text and icons on an [Ink]-filled button or chip. */
val OnInk: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.onInk
val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.onAccent
val Danger: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.danger
val DangerWash: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.dangerWash
val Success: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.success
val Warning: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.warning
val FaithGold: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.gold
val FaithGoldWash: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.goldWash
val FaithGoldInk: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.goldInk
val Recording: Color @Composable @ReadOnlyComposable get() = LocalMMColors.current.recording
val IsDarkTheme: Boolean @Composable @ReadOnlyComposable get() = LocalMMColors.current.isDark
