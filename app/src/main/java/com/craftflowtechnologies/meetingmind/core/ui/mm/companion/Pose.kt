package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import androidx.compose.runtime.Immutable

/** Eye shapes shared by every form (§2.1). */
enum class Eyes { OPEN, SOFT, HAPPY, CLOSED }

/** Mouth shapes shared by every form (§2.1). */
enum class Mouth { SMILE, OPEN, O, WOBBLE, NONE }

/** The one effect layer a pose may carry. */
enum class Fx { NONE, RINGS, DOTS, SPARKLES, SPARKLES_TWO, HEART, ZZZ, GLINT }

/**
 * One frame of the companion, in the 100-unit box (§10.4). Body channels apply to every form;
 * form channels are ignored by forms that don't have them.
 */
@Immutable
data class Pose(
    /** Body offset (the prototype's `by`), positive is down. */
    val dy: Float = 0f,
    val sx: Float = 1f,
    val sy: Float = 1f,
    /** Whole-body tilt around the foot, degrees. */
    val tilt: Float = 0f,
    val eyes: Eyes = Eyes.OPEN,
    val eyeScale: Float = 1f,
    val lookX: Float = 0f,
    val lookY: Float = 0f,
    /** 0 open … 1 shut. */
    val blink: Float = 0f,
    val mouth: Mouth = Mouth.SMILE,
    val brows: Boolean = false,
    /** Folded hands or paws (Prayerful only). */
    val hands: Boolean = false,
    /** 0 on the ground … 1 at the top of a hop (shrinks the shadow). */
    val hop: Float = 0f,
    // Zuri: sound-sprout bar heights and lean.
    val sprout0: Float = 5f,
    val sprout1: Float = 8f,
    val sprout2: Float = 5f,
    val sproutLean: Float = 0f,
    // Nas: ear lift, head tilt and drop, tail.
    val ear: Float = 12f,
    val headTilt: Float = 0f,
    val headDy: Float = 0f,
    val tail: Float = 0f,
    // Wren: wing (and tail, head above).
    val wing: Float = 0f,
    // Page: fold size and lean.
    val fold: Float = 12f,
    val pageLean: Float = 0f,
    val fx: Fx = Fx.NONE,
    /** Sermon quiet: no level reaction, no rings. */
    val calm: Boolean = false
)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Interpolates two poses. Numbers mix; enums and flags switch at t = 0.5. */
fun lerp(a: Pose, b: Pose, t: Float): Pose {
    val late = t >= 0.5f
    return Pose(
        dy = mix(a.dy, b.dy, t), sx = mix(a.sx, b.sx, t), sy = mix(a.sy, b.sy, t), tilt = mix(a.tilt, b.tilt, t),
        eyes = if (late) b.eyes else a.eyes, eyeScale = mix(a.eyeScale, b.eyeScale, t),
        lookX = mix(a.lookX, b.lookX, t), lookY = mix(a.lookY, b.lookY, t), blink = mix(a.blink, b.blink, t),
        mouth = if (late) b.mouth else a.mouth, brows = if (late) b.brows else a.brows,
        hands = if (late) b.hands else a.hands, hop = mix(a.hop, b.hop, t),
        sprout0 = mix(a.sprout0, b.sprout0, t), sprout1 = mix(a.sprout1, b.sprout1, t), sprout2 = mix(a.sprout2, b.sprout2, t),
        sproutLean = mix(a.sproutLean, b.sproutLean, t),
        ear = mix(a.ear, b.ear, t), headTilt = mix(a.headTilt, b.headTilt, t), headDy = mix(a.headDy, b.headDy, t),
        tail = mix(a.tail, b.tail, t), wing = mix(a.wing, b.wing, t),
        fold = mix(a.fold, b.fold, t), pageLean = mix(a.pageLean, b.pageLean, t),
        fx = if (late) b.fx else a.fx, calm = if (late) b.calm else a.calm
    )
}
