package com.craftflowtechnologies.meetingmind.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * # MeetingMind design system: how to read tokens
 *
 * One access pattern: the [MM] object. Its members are `@Composable` getters backed by
 * CompositionLocals provided by `MeetMindTheme`, so they follow the active palette, accent, text
 * scale, density and system animation scale.
 *
 * ```
 * Text("Hi", style = MM.type.body, color = MM.colors.ink)
 * Modifier.padding(MM.space.l).clip(MM.radius.card).background(MM.colors.surface)
 * animateColorAsState(target, animationSpec = MM.motion.quick())
 * ```
 *
 * | Access          | Type            | Source                                       |
 * |-----------------|-----------------|----------------------------------------------|
 * | `MM.type`       | [MMTypography]  | [mmTypography] x [LocalMMTextScale]          |
 * | `MM.space`      | [MMSpace]       | [MMDensity] (Comfortable / Compact)          |
 * | `MM.radius`     | [MMRadius]      | small 8, card 16, pill, sheet                |
 * | `MM.elevation`  | [MMElevation]   | flat, one floating shadow                    |
 * | `MM.motion`     | [MMMotion]      | quick/standard/enter/spring, system-scaled   |
 * | `MM.colors`     | [MMColors]      | palette with the chosen [MMAccent] applied   |
 *
 * [MMSize] (touch target 48 dp, icon sizes, hairline) is a plain object, not themed.
 *
 * Rules: no `fontSize = N.sp`, colour literals or `RoundedCornerShape(N.dp)` outside `ui/theme`
 * (enforced by `DesignSystemGuardTest`). If a token is missing, add it here first.
 *
 * Shared components live in `core/ui/mm`. The older top-level colour names (`Ink`, `Accent`,
 * `SurfaceBase`...) keep working and read the same [MMColors].
 */
object MM {
    val type: MMTypography
        @Composable @ReadOnlyComposable get() = LocalMMTypography.current
    val space: MMSpace
        @Composable @ReadOnlyComposable get() = LocalMMSpace.current
    val radius: MMRadius
        @Composable @ReadOnlyComposable get() = LocalMMRadius.current
    val elevation: MMElevation
        @Composable @ReadOnlyComposable get() = MMElevation
    val motion: MMMotion
        @Composable @ReadOnlyComposable get() = LocalMMMotion.current
    val colors: MMColors
        @Composable @ReadOnlyComposable get() = LocalMMColors.current
}

val LocalMMTypography = staticCompositionLocalOf { mmTypography() }
