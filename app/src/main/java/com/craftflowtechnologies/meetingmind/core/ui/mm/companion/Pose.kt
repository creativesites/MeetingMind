package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

/** Eye shapes shared by every form (§2.1). */
enum class Eyes { OPEN, SOFT, HAPPY, CLOSED }

/** Mouth shapes shared by every form (§2.1). */
enum class Mouth { SMILE, OPEN, O, WOBBLE, NONE }

/** The one effect layer a pose may carry. */
enum class Fx { NONE, RINGS, DOTS, SPARKLES, SPARKLES_TWO, HEART, ZZZ, GLINT }

/**
 * One frame of the companion, in the 100-unit box (§10.4). Body channels apply to every form;
 * form channels are ignored by forms that don't have them.
 *
 * Mutable on purpose: the Canvas renderer keeps a few instances per slot and refills them every
 * frame ([CompanionPoses.poseInto], [lerpInto], [set]), so drawing allocates nothing (§10.8).
 * Code outside the draw path treats it as a value: [CompanionPoses.pose] returns a fresh one.
 */
data class Pose(
    /** Body offset (the prototype's `by`), positive is down. */
    var dy: Float = 0f,
    var sx: Float = 1f,
    var sy: Float = 1f,
    /** Whole-body tilt around the foot, degrees. */
    var tilt: Float = 0f,
    var eyes: Eyes = Eyes.OPEN,
    var eyeScale: Float = 1f,
    var lookX: Float = 0f,
    var lookY: Float = 0f,
    /** 0 open … 1 shut. */
    var blink: Float = 0f,
    var mouth: Mouth = Mouth.SMILE,
    var brows: Boolean = false,
    /** Folded hands or paws (Prayerful only). */
    var hands: Boolean = false,
    /** 0 on the ground … 1 at the top of a hop (shrinks the shadow). */
    var hop: Float = 0f,
    // Zuri: sound-sprout bar heights and lean.
    var sprout0: Float = 5f,
    var sprout1: Float = 8f,
    var sprout2: Float = 5f,
    var sproutLean: Float = 0f,
    // Nas: ear lift, head tilt and drop, tail.
    var ear: Float = 12f,
    var headTilt: Float = 0f,
    var headDy: Float = 0f,
    var tail: Float = 0f,
    // Wren: wing (and tail, head above).
    var wing: Float = 0f,
    // Page: fold size and lean.
    var fold: Float = 12f,
    var pageLean: Float = 0f,
    var fx: Fx = Fx.NONE,
    /** Sermon quiet: no level reaction, no rings. */
    var calm: Boolean = false,
    /** Reading aloud: how far the mouth (Page) or beak (Wren) opens with the voice, 0..1. */
    var talk: Float = 0f
) {
    /** Back to the defaults above, in place. */
    fun reset(): Pose {
        dy = 0f; sx = 1f; sy = 1f; tilt = 0f; eyes = Eyes.OPEN; eyeScale = 1f; lookX = 0f; lookY = 0f; blink = 0f
        mouth = Mouth.SMILE; brows = false; hands = false; hop = 0f
        sprout0 = 5f; sprout1 = 8f; sprout2 = 5f; sproutLean = 0f
        ear = 12f; headTilt = 0f; headDy = 0f; tail = 0f; wing = 0f; fold = 12f; pageLean = 0f
        fx = Fx.NONE; calm = false; talk = 0f
        return this
    }

    /** Copies every channel of [o] into this pose, in place. */
    fun set(o: Pose): Pose {
        dy = o.dy; sx = o.sx; sy = o.sy; tilt = o.tilt; eyes = o.eyes; eyeScale = o.eyeScale; lookX = o.lookX; lookY = o.lookY
        blink = o.blink; mouth = o.mouth; brows = o.brows; hands = o.hands; hop = o.hop
        sprout0 = o.sprout0; sprout1 = o.sprout1; sprout2 = o.sprout2; sproutLean = o.sproutLean
        ear = o.ear; headTilt = o.headTilt; headDy = o.headDy; tail = o.tail; wing = o.wing; fold = o.fold; pageLean = o.pageLean
        fx = o.fx; calm = o.calm; talk = o.talk
        return this
    }
}

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Interpolates two poses. Numbers mix; enums and flags switch at t = 0.5. */
fun lerp(a: Pose, b: Pose, t: Float): Pose = lerpInto(Pose(), a, b, t)

/** [lerp] into [out] without allocating; [out] may be [a] or [b]. */
fun lerpInto(out: Pose, a: Pose, b: Pose, t: Float): Pose {
    val late = t >= 0.5f
    out.dy = mix(a.dy, b.dy, t); out.sx = mix(a.sx, b.sx, t); out.sy = mix(a.sy, b.sy, t); out.tilt = mix(a.tilt, b.tilt, t)
    out.eyes = if (late) b.eyes else a.eyes; out.eyeScale = mix(a.eyeScale, b.eyeScale, t)
    out.lookX = mix(a.lookX, b.lookX, t); out.lookY = mix(a.lookY, b.lookY, t); out.blink = mix(a.blink, b.blink, t)
    out.mouth = if (late) b.mouth else a.mouth; out.brows = if (late) b.brows else a.brows
    out.hands = if (late) b.hands else a.hands; out.hop = mix(a.hop, b.hop, t)
    out.sprout0 = mix(a.sprout0, b.sprout0, t); out.sprout1 = mix(a.sprout1, b.sprout1, t); out.sprout2 = mix(a.sprout2, b.sprout2, t)
    out.sproutLean = mix(a.sproutLean, b.sproutLean, t)
    out.ear = mix(a.ear, b.ear, t); out.headTilt = mix(a.headTilt, b.headTilt, t); out.headDy = mix(a.headDy, b.headDy, t)
    out.tail = mix(a.tail, b.tail, t); out.wing = mix(a.wing, b.wing, t)
    out.fold = mix(a.fold, b.fold, t); out.pageLean = mix(a.pageLean, b.pageLean, t)
    out.fx = if (late) b.fx else a.fx; out.calm = if (late) b.calm else a.calm
    out.talk = mix(a.talk, b.talk, t)
    return out
}
