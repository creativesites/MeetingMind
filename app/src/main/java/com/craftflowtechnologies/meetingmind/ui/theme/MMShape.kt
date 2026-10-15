package com.craftflowtechnologies.meetingmind.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ───────────────────────────── Space ─────────────────────────────

/** Spacing scale (DESIGN_SYSTEM §4). Screen gutter is [l]; section gap is [xl]; card padding is [l]. */
@Immutable
data class MMSpace(val xs: Dp, val s: Dp, val m: Dp, val l: Dp, val xl: Dp, val xxl: Dp)

/** Comfortable is the default; Compact moves m, l and xl down one step. */
enum class MMDensity(val label: String) {
    Comfortable("Comfortable"), Compact("Compact");

    val space: MMSpace
        get() = when (this) {
            Comfortable -> MMSpace(xs = 4.dp, s = 8.dp, m = 12.dp, l = 16.dp, xl = 24.dp, xxl = 32.dp)
            Compact -> MMSpace(xs = 4.dp, s = 8.dp, m = 8.dp, l = 12.dp, xl = 16.dp, xxl = 32.dp)
        }
}

val LocalMMSpace = staticCompositionLocalOf { MMDensity.Comfortable.space }

/** Fixed sizes that are not spacing: touch target, icon sizes, hairline. */
object MMSize {
    /** The 48 dp rule: every tappable element has at least this target. */
    val minTouch = 48.dp
    val icon = 24.dp
    val iconSmall = 20.dp
    val hairline = 1.dp
    /** The space-colour marker on a note row. */
    val marker = 4.dp
}

// ───────────────────────────── Radius ─────────────────────────────

/** Corner radii (DESIGN_SYSTEM §4): small 8 (chips, inputs), card 16 (cards, sheets), pill 50%. */
@Immutable
class MMRadius internal constructor() {
    val small: Shape = RoundedCornerShape(8.dp)
    val card: Shape = RoundedCornerShape(16.dp)
    val pill: Shape = CircleShape
    /** Bottom sheets and menus: top corners only. */
    val sheet: Shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
}

val LocalMMRadius = staticCompositionLocalOf { MMRadius() }

// ───────────────────────────── Elevation ─────────────────────────────

/** Flat by default; only floating chrome (bottom bar, sheets, mini-player, snackbars) gets one shadow. */
object MMElevation {
    val flat = 0.dp
    val floating = 8.dp
    const val floatingAlpha = 0.08f

    /** The one shared floating shadow. */
    fun Modifier.floatingShadow(shape: Shape): Modifier = shadow(
        elevation = floating, shape = shape,
        ambientColor = Color.Black.copy(alpha = floatingAlpha),
        spotColor = Color.Black.copy(alpha = floatingAlpha)
    )
}

// ───────────────────────────── Motion ─────────────────────────────

/**
 * Motion tokens (DESIGN_SYSTEM §6) resolved against the system animator duration scale.
 * When the scale is 0 ("Remove animations") every spec is a [snap] to the end state.
 */
@Immutable
class MMMotion(val durationScale: Float = 1f) {
    val reduced: Boolean get() = durationScale <= 0f

    private fun ms(base: Int) = (base * durationScale).toInt()

    /** Press and toggle: 150 ms. */
    fun <T> quick(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(ms(QuickMs), easing = Emphasized)
    /** Content changes, expand/collapse: 250 ms, emphasized easing. */
    fun <T> standard(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(ms(StandardMs), easing = Emphasized)
    /** Sheets and screens: 300 ms. */
    fun <T> enter(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(ms(EnterMs), easing = Emphasized)
    /** Bar indicator, Mimi: damping 0.8, medium-low stiffness. */
    fun <T> spring(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

    companion object {
        const val QuickMs = 150
        const val StandardMs = 250
        const val EnterMs = 300
        val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    }
}

val LocalMMMotion = staticCompositionLocalOf { MMMotion() }

/** The system "Animator duration scale" (Developer options / Remove animations), 1 if unreadable. */
@Composable
fun rememberSystemAnimationScale(): Float {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        runCatching {
            android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
    }
}
