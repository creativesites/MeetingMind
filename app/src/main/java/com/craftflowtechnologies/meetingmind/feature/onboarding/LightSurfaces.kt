package com.craftflowtechnologies.meetingmind.feature.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Companion
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionForceCanvas
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionReducedMotion
import com.craftflowtechnologies.meetingmind.ui.theme.Brand
import com.craftflowtechnologies.meetingmind.ui.theme.LocalAppearance
import com.craftflowtechnologies.meetingmind.ui.theme.LocalMMSpace
import com.craftflowtechnologies.meetingmind.ui.theme.LocalMMTextScale
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMColors
import com.craftflowtechnologies.meetingmind.ui.theme.MMDensity
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme
import com.craftflowtechnologies.meetingmind.ui.theme.TextSize

/*
 * The light, premium surfaces shared by onboarding and the paywall. Both screens always draw in the
 * Paper (light) palette, whatever the app's dark or light setting, with the person's accent, text size
 * and density kept. Everything here reads MM tokens: no colour, size or corner literals.
 */

/**
 * Draws [content] in the light palette and turns the status and navigation bar icons dark while it shows.
 * The bars themselves are transparent (edge to edge), so the screen's own background shows behind them.
 */
@Composable
fun AlwaysLight(content: @Composable () -> Unit) {
    val outerDark = MM.colors.isDark
    val appearance = LocalAppearance.current
    val textScale = LocalMMTextScale.current
    val density = if (LocalMMSpace.current == MMDensity.Compact.space) MMDensity.Compact else MMDensity.Comfortable
    LightSystemBars(restoreLightIcons = !outerDark)
    // The outer theme has already applied the person's text size to the font scale: don't apply it twice.
    MeetMindTheme(
        darkTheme = false,
        appearance = appearance.copy(textSize = TextSize.DEFAULT),
        spaceDensity = density,
        textScale = textScale
    ) {
        CompositionLocalProvider(LocalAppearance provides appearance, content = content)
    }
}

/** Dark bar icons while shown; on leaving, the icons the app's own theme uses ([restoreLightIcons]). */
@Composable
private fun LightSystemBars(restoreLightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val restore by rememberUpdatedState(restoreLightIcons)
    // The activity restyles its bars when the app theme changes; run after it so the light screen keeps dark icons.
    LaunchedEffect(restoreLightIcons) {
        withFrameNanos { }
        view.barController()?.useDarkIcons(true)
    }
    DisposableEffect(view) {
        view.barController()?.useDarkIcons(true)
        onDispose { view.barController()?.useDarkIcons(!restore) }
    }
}

private fun WindowInsetsControllerCompat.useDarkIcons(dark: Boolean) {
    isAppearanceLightStatusBars = dark
    isAppearanceLightNavigationBars = dark
}

private fun View.barController(): WindowInsetsControllerCompat? {
    val window = context.hostActivity()?.window ?: return null
    return runCatching { WindowCompat.getInsetsController(window, this) }.getOrNull()
}

private fun Context.hostActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** The light page: the paper background with a soft wash of the accent and the brand's light at the top. */
@Composable
fun LightBackdrop(modifier: Modifier = Modifier) {
    val c = MM.colors
    Canvas(modifier.fillMaxSize()) {
        drawRect(c.background)
        drawRect(Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.09f), Color.Transparent), startY = 0f, endY = size.height * 0.55f))
        val violet = Offset(size.width * 0.92f, size.height * 0.06f)
        drawCircle(Brush.radialGradient(listOf(Brand.Violet.copy(alpha = 0.16f), Color.Transparent), center = violet, radius = size.width * 0.75f), radius = size.width * 0.75f, center = violet)
        val cyan = Offset(size.width * 0.04f, size.height * 0.30f)
        drawCircle(Brush.radialGradient(listOf(Brand.Cyan.copy(alpha = 0.13f), Color.Transparent), center = cyan, radius = size.width * 0.62f), radius = size.width * 0.62f, center = cyan)
    }
}

/**
 * The companion on a lifted paper disc, inside a soft glow of the accent. The glow breathes and the disc floats
 * gently (both still when animations are off); the companion itself holds a still, Canvas-drawn pose, because
 * these are launch-time screens and its frame clock would otherwise run for as long as they show.
 * No companion: the app's own tile.
 */
@Composable
fun CompanionHalo(
    form: CompanionForm?,
    size: Dp,
    modifier: Modifier = Modifier,
    state: CompanionState = CompanionState.IDLE,
    variant: CompanionVariant = CompanionVariant.Normal,
    contentDescription: String? = null
) {
    val c = MM.colors
    val reduced = MM.motion.reduced
    val t = rememberInfiniteTransition(label = "halo")
    val breathe by t.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(2800), RepeatMode.Reverse), label = "breathe")
    val drift by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(3400), RepeatMode.Reverse), label = "drift")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { val s = if (reduced) 1f else breathe; scaleX = s; scaleY = s }
                .background(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.22f), Brand.Violet.copy(alpha = 0.08f), Color.Transparent)), CircleShape)
        )
        Box(Modifier.size(size * 0.8f).border(MMSize.hairline, Brush.sweepGradient(Brand.ring.map { it.copy(alpha = 0.5f) }), CircleShape))
        Box(
            Modifier.size(size * 0.66f)
                .graphicsLayer { if (!reduced) translationY = drift * this.size.height * 0.04f }
                .shadow(MM.elevation.floating, CircleShape, ambientColor = c.accent, spotColor = c.accent)
                .clip(CircleShape).background(c.surface)
                .border(MMSize.hairline, c.lineSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (form != null) {
                // Launch-time screens draw on Canvas; Rive comes later, once the app has settled.
                CompositionLocalProvider(LocalCompanionForceCanvas provides true, LocalCompanionReducedMotion provides true) {
                    Companion(form = form, state = state, size = size * 0.48f, variant = variant, contentDescription = contentDescription)
                }
            } else {
                com.craftflowtechnologies.meetingmind.core.ui.AppIconTile(size = size * 0.4f)
            }
        }
    }
}

/**
 * The one main action: a full-width accent pill with a soft coloured shadow, 56 dp tall. Disabled, it
 * falls back to the track colour.
 */
@Composable
fun GlowButton(
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showArrow: Boolean = true,
    onClick: () -> Unit
) {
    val c = MM.colors
    val shape = MM.radius.pill
    val lift = if (enabled) Modifier.shadow(MM.elevation.floating, shape, ambientColor = c.accent, spotColor = c.accent) else Modifier
    Surface(
        onClick = onClick, enabled = enabled, shape = shape, color = Color.Transparent,
        modifier = modifier.heightIn(min = MMSize.minTouch + MM.space.s).then(lift).testTag(tag)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(if (enabled) Brush.horizontalGradient(listOf(c.accent, lerp(c.accent, Brand.Indigo, 0.35f))) else SolidColor(c.track))
                .padding(horizontal = MM.space.xl, vertical = MM.space.l),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
        ) {
            Text(label, style = MM.type.heading, color = if (enabled) c.onAccent else c.inkMuted, textAlign = TextAlign.Center)
            if (showArrow && enabled) {
                Spacer(Modifier.width(MM.space.s))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(MMSize.iconSmall))
            }
        }
    }
}

/**
 * A choice card on the light page. Chosen: a lifted surface with the accent's wash, an accent border and a soft
 * shadow. Not chosen: a quiet surface with a hairline.
 */
internal fun Modifier.choiceCard(c: MMColors, shape: Shape, selected: Boolean, elevation: Dp): Modifier =
    this.then(if (selected) Modifier.shadow(elevation, shape, ambientColor = c.accent, spotColor = c.accent) else Modifier)
        .clip(shape)
        .background(if (selected) c.surfaceRaised else c.surface)
        .background(if (selected) c.accentWash else Color.Transparent)
        .border(if (selected) 1.5.dp else MMSize.hairline, if (selected) c.accent else c.line, shape)

/** The round mark at the end of a choice: an accent ring with a dot when chosen, a hairline ring when not. */
@Composable
internal fun ChoiceMark(selected: Boolean, modifier: Modifier = Modifier) {
    val c = MM.colors
    Box(
        modifier.size(MMSize.icon).clip(CircleShape)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) c.accent else c.inkFaint, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Box(Modifier.size(MMSize.icon / 2).clip(CircleShape).background(c.accent))
    }
}

/**
 * Step progress: one segment per step after the welcome, filled up to [done], easing in with the theme's
 * motion. Read out as "Step n of m".
 */
@Composable
internal fun StepProgress(done: Int, total: Int, description: String, modifier: Modifier = Modifier) {
    val c = MM.colors
    val motion = MM.motion
    Row(
        modifier.semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(done.toFloat().coerceIn(0f, total.toFloat()), 0f..total.toFloat().coerceAtLeast(1f), (total - 1).coerceAtLeast(0))
        },
        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        repeat(total.coerceAtLeast(1)) { i ->
            val fill by animateFloatAsState(if (i < done) 1f else 0f, motion.standard(), label = "segment")
            Box(Modifier.weight(1f).height(MM.space.xs + 1.dp).clip(MM.radius.pill).background(c.track)) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(fill).clip(MM.radius.pill)
                        .background(Brush.horizontalGradient(listOf(c.accent, lerp(c.accent, Brand.Violet, 0.4f))))
                )
            }
        }
    }
}

/** A rounded tile holding a choice's icon: the accent on its wash when on, muted on the sunk surface when off. */
@Composable
internal fun IconTile(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, modifier: Modifier = Modifier) {
    val c = MM.colors
    Box(
        modifier.size(MMSize.minTouch - MM.space.s).clip(MM.radius.small).background(if (on) c.accentWash else c.surfaceSunk),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (on) c.accent else c.inkMuted, modifier = Modifier.size(MMSize.iconSmall))
    }
}
