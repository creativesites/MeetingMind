package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Immutable
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVisual
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** How a visual uses time (§10.5). */
sealed interface Timing {
    /** Repeats every [periodMs]; stops (holds still) after [maxCycles] when set. */
    data class Loop(val periodMs: Int, val maxCycles: Int? = null) : Timing
    /** Plays once over [durationMs] and holds the last frame. */
    data class OneShot(val durationMs: Int) : Timing
    /** Follows a live level (mic or TTS); runs while visible. */
    data object Driven : Timing
}

/** The pose channels a keyframe track can drive. */
enum class PoseChannel { DY, SX, SY, TILT, HOP, EYE_SCALE }

@Immutable
data class Keyframe(val at: Float, val value: Float, val easing: Easing = FastOutSlowInEasing)

/** Keyframes over normalised time 0..1. The easing on a key shapes the segment that ends at it. */
@Immutable
data class Track(val channel: PoseChannel, val keys: List<Keyframe>) {
    fun valueAt(p: Float): Float {
        if (p <= keys.first().at) return keys.first().value
        for (i in 1 until keys.size) {
            val b = keys[i]
            if (p <= b.at) {
                val a = keys[i - 1]
                val span = b.at - a.at
                val f = if (span <= 0f) 1f else b.easing.transform((p - a.at) / span)
                return a.value + (b.value - a.value) * f
            }
        }
        return keys.last().value
    }
}

/** A sine breath: scaleY 1 ± amp, scaleX 1 ∓ 0.55·amp, pivot at the foot. */
@Immutable
data class Breath(val periodMs: Int, val amp: Float)

/** A sine sway of the whole body: tilt = base ± amp. */
@Immutable
data class Sway(val periodMs: Int, val amp: Float, val base: Float = 0f)

/**
 * Motion for one visual, as data (§10.5). The numbers are the §3.1 values and the visual
 * reference's; Companion Lab exposes them for tuning.
 */
@Immutable
data class MotionSpec(
    val visual: CompanionVisual,
    val timing: Timing,
    val tracks: List<Track> = emptyList(),
    val breath: Breath? = null,
    val sway: Sway? = null,
    val blinkEveryMs: Int? = null,
    val enterMs: Int = 250,
    val exitMs: Int = 250,
    val fps: Int = 60
) {
    fun track(c: PoseChannel): Track? = tracks.firstOrNull { it.channel == c }

    /** How long the clock must run, or null for as long as visible. */
    val runMs: Long?
        get() = when (val t = timing) {
            is Timing.Loop -> t.maxCycles?.let { it.toLong() * t.periodMs }
            is Timing.OneShot -> t.durationMs.toLong()
            Timing.Driven -> null
        }
}

object ZuriMotion {

    /** Celebrating (§3.1): anticipation 0–120 ms, hop 120–620, land 620–800, settle to 1.6 s. */
    private val CelebrateTracks = run {
        fun at(ms: Int) = ms / 1600f
        listOf(
            Track(PoseChannel.DY, listOf(Keyframe(0f, 0f), Keyframe(at(120), 0f), Keyframe(at(370), -9f, EaseOut), Keyframe(at(620), 0f, EaseIn), Keyframe(1f, 0f))),
            Track(PoseChannel.HOP, listOf(Keyframe(0f, 0f), Keyframe(at(120), 0f), Keyframe(at(370), 1f, EaseOut), Keyframe(at(620), 0f, EaseIn), Keyframe(1f, 0f))),
            Track(PoseChannel.SY, listOf(
                Keyframe(0f, 1f), Keyframe(at(120), 0.94f), Keyframe(at(370), 1.05f, EaseOut), Keyframe(at(620), 1f, EaseIn),
                Keyframe(at(710), 0.92f), Keyframe(at(800), 1f), Keyframe(at(1000), 1.02f), Keyframe(at(1200), 0.995f), Keyframe(1f, 1f)
            )),
            Track(PoseChannel.SX, listOf(
                Keyframe(0f, 1f), Keyframe(at(120), 1.03f), Keyframe(at(370), 0.97f, EaseOut), Keyframe(at(620), 1f, EaseIn),
                Keyframe(at(710), 1.08f), Keyframe(at(800), 1f), Keyframe(at(1000), 0.99f), Keyframe(1f, 1f)
            ))
        )
    }

    /**
     * Curious (§3.1, Phase B): a 600 ms lean-in on each new question, then hold. Head tilt 8°, the
     * body leans in 4% (the "4 units" of the spec, about the foot) and the eyes widen to 1.1×.
     */
    private val CuriousTracks = listOf(
        Track(PoseChannel.TILT, listOf(Keyframe(0f, 0f), Keyframe(0.55f, 9.5f, EaseOut), Keyframe(1f, 8f))),
        Track(PoseChannel.SX, listOf(Keyframe(0f, 1f), Keyframe(0.5f, 1.05f, EaseOut), Keyframe(1f, 1.04f))),
        Track(PoseChannel.SY, listOf(Keyframe(0f, 1f), Keyframe(0.5f, 1.05f, EaseOut), Keyframe(1f, 1.04f))),
        Track(PoseChannel.DY, listOf(Keyframe(0f, 0f), Keyframe(0.5f, -1.4f, EaseOut), Keyframe(1f, -1f))),
        Track(PoseChannel.EYE_SCALE, listOf(Keyframe(0f, 1f), Keyframe(0.5f, 1.12f, EaseOut), Keyframe(1f, 1.1f)))
    )

    /**
     * Proud (§3.1, Phase B): 1.2 s, chest up and one gold glint, then Idle. Warm and small on
     * purpose: a tiny breath in, the chest lifts, no hop and no sparkle burst.
     */
    private val ProudTracks = run {
        fun at(ms: Int) = ms / 1200f
        listOf(
            Track(PoseChannel.SY, listOf(Keyframe(0f, 1f), Keyframe(at(140), 0.985f), Keyframe(at(520), 1.046f, EaseOut), Keyframe(at(800), 1.038f), Keyframe(1f, 1.04f))),
            Track(PoseChannel.SX, listOf(Keyframe(0f, 1f), Keyframe(at(140), 1.008f), Keyframe(at(520), 0.976f, EaseOut), Keyframe(at(800), 0.982f), Keyframe(1f, 0.98f))),
            Track(PoseChannel.DY, listOf(Keyframe(0f, 0f), Keyframe(at(140), 0.4f), Keyframe(at(520), -1.2f, EaseOut), Keyframe(at(800), -0.9f), Keyframe(1f, -1f)))
        )
    }

    /** The nod (§3.1): 600 ms, a single 3-unit dip, no sparkles. */
    val Nod = MotionSpec(
        CompanionState.CELEBRATING, Timing.OneShot(600),
        tracks = listOf(
            Track(PoseChannel.DY, listOf(Keyframe(0f, 0f), Keyframe(0.4f, 3f), Keyframe(1f, 0f))),
            Track(PoseChannel.SY, listOf(Keyframe(0f, 1f), Keyframe(0.4f, 0.97f), Keyframe(1f, 1f)))
        )
    )

    val specs: Map<CompanionVisual, MotionSpec> = listOf(
        MotionSpec(CompanionState.IDLE, Timing.Loop(3400), breath = Breath(3400, 0.022f), blinkEveryMs = 4600),
        MotionSpec(CompanionState.LISTENING, Timing.Driven, breath = Breath(2200, 0.01f), sway = Sway(4830, 2f, -5f)),
        MotionSpec(CompanionState.THINKING, Timing.Loop(1500), sway = Sway(4190, 3f), blinkEveryMs = 3100, fps = 30),
        MotionSpec(CompanionState.CELEBRATING, Timing.OneShot(1600), tracks = CelebrateTracks),
        MotionSpec(CompanionState.WORRIED, Timing.Loop(6980), sway = Sway(6980, 1.5f), blinkEveryMs = 3800, fps = 30),
        MotionSpec(CompanionState.SLEEPY, Timing.Loop(2600, maxCycles = 3), breath = Breath(5500, 0.025f), fps = 30),
        MotionSpec(CompanionState.CURIOUS, Timing.OneShot(600), tracks = CuriousTracks),
        MotionSpec(CompanionState.PROUD, Timing.OneShot(1200), tracks = ProudTracks),
        MotionSpec(CompanionState.READING, Timing.Driven, blinkEveryMs = 2400, fps = 30),
        MotionSpec(CreateMode.PRAYERFUL, Timing.Loop(6000), breath = Breath(6000, 0.008f), fps = 30),
        MotionSpec(CreateMode.PEACEFUL, Timing.Loop(6000), breath = Breath(6000, 0.015f), fps = 30),
        MotionSpec(CreateMode.GRATEFUL, Timing.Loop(4500), breath = Breath(4500, 0.012f), sway = Sway(7850, 1f, 4f), fps = 30),
        MotionSpec(CreateMode.JOYFUL, Timing.Loop(1208)),
        MotionSpec(CreateMode.REFLECTIVE, Timing.Loop(4500), breath = Breath(4500, 0.012f), blinkEveryMs = 4200, fps = 30),
        MotionSpec(CreateMode.CELEBRATORY, Timing.OneShot(1600), tracks = CelebrateTracks)
    ).associateBy { it.visual }

    /** Per-space timing scale (§2.6): Faith slower and gentler, Work crisper. */
    val spaceScale: Map<NotebookSpace, Float> = mapOf(NotebookSpace.FAITH to 1.3f, NotebookSpace.WORK to 0.8f)

    fun spec(visual: CompanionVisual, variant: CompanionVariant = CompanionVariant.Normal): MotionSpec = when {
        variant == CompanionVariant.Nod -> Nod
        variant == CompanionVariant.Quiet -> specs.getValue(CreateMode.PEACEFUL)
        else -> specs.getValue(visual)
    }
}

/**
 * The pose for a visual at time [t] seconds with level [level] (0..1). A faithful port of the
 * visual reference's `pose()` plus each form's channels (`DRAW.orb/nas/wren/folio`). With
 * [reduced] true it returns the static pose (the reference's `rm` branches), used for reduced
 * motion, golden tests and card export.
 */
object CompanionPoses {
    private const val TAU = (2 * PI).toFloat()

    fun pose(
        form: CompanionForm,
        visual: CompanionVisual,
        t: Float,
        level: Float,
        reduced: Boolean,
        variant: CompanionVariant = CompanionVariant.Normal
    ): Pose {
        if (variant == CompanionVariant.Quiet) {
            return pose(form, CreateMode.PEACEFUL, t, 0f, reduced).copy(calm = true, fx = Fx.NONE)
        }
        val spec = ZuriMotion.spec(visual, variant)
        val rm = reduced
        // Reading's static pose has the mouth closed (§3.1), whatever level the caller passes.
        val L = if (rm && visual == CompanionState.READING) 0f else level.coerceIn(0f, 1f)
        fun blinkAt(periodMs: Int?): Float {
            if (rm || periodMs == null) return 0f
            val q = t % (periodMs / 1000f)
            return if (q < 0.16f) sin(q / 0.16f * PI.toFloat()) else 0f
        }
        fun breath(): Pair<Float, Float> {
            val b = spec.breath ?: return 1f to 1f
            val v = if (rm) 0f else sin(t * TAU / (b.periodMs / 1000f))
            return (1f - b.amp * 0.55f * v) to (1f + b.amp * v)
        }
        fun sway(): Float {
            val s = spec.sway ?: return 0f
            return s.base + if (rm) 0f else sin(t * TAU / (s.periodMs / 1000f)) * s.amp
        }
        val (bx, by) = breath()

        var p = when (if (variant == CompanionVariant.Nod) null else visual) {
            null -> {
                // The nod: happy face, one dip.
                val prog = if (rm) 0.4f else (t * 1000f / 600f).coerceIn(0f, 1f)
                Pose(eyes = Eyes.HAPPY, mouth = Mouth.SMILE,
                    dy = spec.track(PoseChannel.DY)!!.valueAt(prog), sy = spec.track(PoseChannel.SY)!!.valueAt(prog))
            }
            CompanionState.IDLE -> Pose(sx = bx, sy = by, blink = blinkAt(spec.blinkEveryMs))
            CompanionState.LISTENING -> {
                val b = if (rm) 0f else sin(t * TAU / 2.2f)
                Pose(sy = 1f + 0.035f * L + 0.01f * b, sx = 1f + 0.014f * L, tilt = sway(), eyes = Eyes.SOFT, lookX = 0.6f, fx = Fx.RINGS)
            }
            CompanionState.THINKING -> Pose(
                tilt = if (rm) 3f else sway(), lookX = 1.1f, lookY = -1.5f, mouth = Mouth.O,
                blink = blinkAt(spec.blinkEveryMs), fx = Fx.DOTS
            )
            CompanionState.CELEBRATING, CreateMode.CELEBRATORY -> celebrate(spec, t, rm)
            CompanionState.WORRIED -> Pose(
                sx = 1.03f, sy = 0.95f, dy = 1.5f, tilt = if (rm) 0f else sway(), brows = true, mouth = Mouth.WOBBLE,
                lookY = 0.7f, eyeScale = 0.88f, blink = blinkAt(spec.blinkEveryMs)
            )
            CompanionState.SLEEPY -> Pose(sx = bx, sy = by - 0.03f, dy = 2f, tilt = 6f, eyes = Eyes.CLOSED, mouth = Mouth.NONE, fx = Fx.ZZZ)
            CompanionState.CURIOUS -> {
                val prog = oneShotProgress(t, 600, rm)
                val e = FastOutSlowInEasing.transform(prog)
                Pose(
                    tilt = spec.track(PoseChannel.TILT)!!.valueAt(prog), dy = spec.track(PoseChannel.DY)!!.valueAt(prog),
                    sx = spec.track(PoseChannel.SX)!!.valueAt(prog), sy = spec.track(PoseChannel.SY)!!.valueAt(prog),
                    eyeScale = spec.track(PoseChannel.EYE_SCALE)!!.valueAt(prog), lookX = 0.8f * e, lookY = -0.4f * e
                )
            }
            CompanionState.PROUD -> {
                val prog = oneShotProgress(t, 1200, rm)
                Pose(
                    sy = spec.track(PoseChannel.SY)!!.valueAt(prog), sx = spec.track(PoseChannel.SX)!!.valueAt(prog),
                    dy = spec.track(PoseChannel.DY)!!.valueAt(prog), eyes = Eyes.HAPPY, mouth = Mouth.SMILE, fx = Fx.GLINT
                )
            }
            CompanionState.READING -> Pose(
                // Eyes follow the line (§3.1): a sweep left to right per sentence, a quick return.
                lookX = if (rm) 0f else readingGlance(t), lookY = 0.6f, mouth = Mouth.SMILE,
                sy = 1f + 0.015f * L, sx = 1f + 0.006f * L, blink = blinkAt(spec.blinkEveryMs), talk = L
            )
            CreateMode.PRAYERFUL -> Pose(sx = bx, sy = by, dy = 1f, eyes = Eyes.CLOSED, mouth = Mouth.NONE, hands = true)
            CreateMode.PEACEFUL -> Pose(sx = bx, sy = by, eyes = Eyes.CLOSED, mouth = Mouth.SMILE)
            CreateMode.GRATEFUL -> Pose(sx = bx, sy = by, eyes = Eyes.HAPPY, mouth = Mouth.SMILE, tilt = if (rm) 4f else sway(), fx = Fx.HEART)
            CreateMode.JOYFUL -> Pose(eyes = Eyes.HAPPY, mouth = Mouth.OPEN, dy = if (rm) -2f else -abs(sin(t * 2.6f)) * 3f, fx = Fx.SPARKLES_TWO)
            CreateMode.REFLECTIVE -> Pose(sx = bx, sy = by, lookX = -1f, lookY = -1.5f, tilt = -3f, blink = blinkAt(spec.blinkEveryMs))
            else -> Pose()
        }
        p = formChannels(form, visual, variant, p, t, L, rm)
        return p
    }

    /** 0..1 through a one-shot of [ms]; the static pose is the held end (1). */
    internal fun oneShotProgress(t: Float, ms: Int, rm: Boolean): Float = if (rm) 1f else (t * 1000f / ms).coerceIn(0f, 1f)

    /** The Reading glance: −1.2 → +1.2 over 85% of a 2.4 s sentence (eased), then back in the rest. */
    internal fun readingGlance(t: Float): Float {
        val ph = (t % ReadingSentenceS) / ReadingSentenceS
        return if (ph < 0.85f) -1.2f + 2.4f * FastOutSlowInEasing.transform(ph / 0.85f)
        else 1.2f - 2.4f * ((ph - 0.85f) / 0.15f)
    }

    /** The default sentence length while reading aloud (§3.1). */
    const val ReadingSentenceS = 2.4f

    private fun celebrate(spec: MotionSpec, t: Float, rm: Boolean): Pose {
        val base = Pose(eyes = Eyes.HAPPY, mouth = Mouth.OPEN, fx = Fx.SPARKLES)
        if (rm) return base.copy(dy = -5f, sy = 1.04f, sx = 0.97f, hop = 0.6f)
        val prog = (t * 1000f / 1600f).coerceIn(0f, 1f)
        return base.copy(
            dy = spec.track(PoseChannel.DY)?.valueAt(prog) ?: 0f,
            sx = spec.track(PoseChannel.SX)?.valueAt(prog) ?: 1f,
            sy = spec.track(PoseChannel.SY)?.valueAt(prog) ?: 1f,
            hop = spec.track(PoseChannel.HOP)?.valueAt(prog) ?: 0f
        )
    }

    private fun formChannels(form: CompanionForm, visual: CompanionVisual, variant: CompanionVariant, p: Pose, t: Float, L: Float, rm: Boolean): Pose {
        val v: CompanionVisual = if (variant == CompanionVariant.Nod) CompanionState.IDLE else visual
        val celebrating = v == CompanionState.CELEBRATING || v == CreateMode.CELEBRATORY
        // Curious and Proud ease their form channels in with the one-shot; static poses hold the end.
        val e = when (v) {
            CompanionState.CURIOUS -> FastOutSlowInEasing.transform(oneShotProgress(t, 600, rm))
            CompanionState.PROUD -> FastOutSlowInEasing.transform(oneShotProgress(t, 1200, rm))
            else -> 1f
        }
        return when (form) {
            CompanionForm.ZURI -> {
                var lean = 0f
                val h0: Float; val h1: Float; val h2: Float
                when {
                    v == CompanionState.LISTENING -> {
                        if (rm) { h0 = 6f * (0.5f + L); h1 = 11f * (0.5f + L); h2 = 7f * (0.5f + L) } else {
                            h0 = 3.5f + L * (7f + 5f * sin(t * 9f))
                            h1 = 3.5f + L * (7f + 5f * sin(t * 9f + 1.9f)) + 2f
                            h2 = 3.5f + L * (7f + 5f * sin(t * 9f + 3.8f))
                        }
                    }
                    v == CompanionState.THINKING -> {
                        if (rm) { h0 = 4f; h1 = 7f; h2 = 4f } else {
                            h0 = 4f + 2.5f * max(0f, sin(t * 4.2f))
                            h1 = 4f + 2.5f * max(0f, sin(t * 4.2f - 0.7f))
                            h2 = 4f + 2.5f * max(0f, sin(t * 4.2f - 1.4f))
                        }
                    }
                    v == CompanionState.READING -> {
                        // The sprout follows the voice while reading aloud.
                        h0 = 4f + L * 5f; h1 = 6f + L * 7f; h2 = 4f + L * 5f
                    }
                    celebrating -> { h0 = 8f; h1 = 12f; h2 = 8f }
                    v == CompanionState.PROUD -> { h0 = 5f + 3f * e; h1 = 8f + 4f * e; h2 = 5f + 3f * e }
                    v == CompanionState.CURIOUS -> { h0 = 5f + 1f * e; h1 = 8f + 2.5f * e; h2 = 5f + 1f * e; lean = -6f * e }
                    v == CreateMode.JOYFUL -> {
                        if (rm) { h0 = 6f; h1 = 10f; h2 = 6f } else { h0 = 6f + 2f * sin(t * 5f); h1 = 6f + 2f * sin(t * 5f + 1f); h2 = 6f + 2f * sin(t * 5f + 2f) }
                    }
                    v == CompanionState.WORRIED || v == CompanionState.SLEEPY -> { h0 = 3f; h1 = 4f; h2 = 3f; lean = 14f }
                    v == CreateMode.PRAYERFUL -> { h0 = 3f; h1 = 5f; h2 = 3f; lean = 10f }
                    v == CreateMode.PEACEFUL -> { h0 = 4f; h1 = 6f; h2 = 4f; lean = 6f }
                    else -> { h0 = 5f; h1 = 8f; h2 = 5f }
                }
                if (p.calm) lean = 6f
                p.copy(sprout0 = h0, sprout1 = h1, sprout2 = h2, sproutLean = lean)
            }
            CompanionForm.NAS -> {
                var headR = 0f; var headDy = 0f; var ear = 12f; var tail = 0f; var dy = p.dy
                when {
                    v == CompanionState.IDLE -> { tail = if (rm) 0f else sin(t * 1.1f) * 5f; ear = 12f + if (rm) 0f else sin(t * TAU / 3.4f) * 1.5f }
                    v == CompanionState.LISTENING -> { headR = if (rm) -10f else -10f + sin(t * 1.3f) * 2f; ear = 12f + L * (if (rm) 16f else 16f + sin(t * 10f) * 4f) }
                    v == CompanionState.THINKING -> { headR = 8f; ear = 16f }
                    v == CreateMode.REFLECTIVE -> { headR = -6f; ear = 14f }
                    celebrating -> {
                        // Nas: hop ×0.5, plus a 7 Hz wag that decays over 1.2 s.
                        dy = p.dy * 0.5f
                        ear = 24f + if (rm) 0f else sin(t * 9f) * 6f
                        tail = if (rm) 24f else sin(t * TAU * 7f) * 28f * (1f - (t / 1.2f)).coerceIn(0f, 1f)
                    }
                    v == CreateMode.JOYFUL -> { ear = 18f; tail = if (rm) 14f else sin(t * 8f) * 16f }
                    v == CompanionState.WORRIED -> { ear = 2f; headDy = 2f; tail = 35f }
                    v == CompanionState.SLEEPY -> { ear = 4f; headDy = 4f; headR = 8f; tail = 20f }
                    v == CreateMode.PRAYERFUL -> { ear = 5f; headDy = 2f }
                    v == CreateMode.PEACEFUL -> { ear = 8f; headR = 4f }
                    v == CreateMode.GRATEFUL -> { ear = 10f; headR = 6f; headDy = 1f; tail = if (rm) 6f else sin(t * 3f) * 6f }
                    v == CompanionState.CURIOUS -> { headR = -12f * e; ear = 12f + 6f * e }
                    v == CompanionState.PROUD -> {
                        // A small, warm wag that settles: never the celebration wag.
                        ear = 12f + 8f * e
                        tail = 18f * e + if (rm) 0f else sin(t * TAU * 2.5f) * 7f * (1f - (t / 1.2f)).coerceIn(0f, 1f)
                    }
                    v == CompanionState.READING -> { ear = 12f + L * 4f; headR = -4f }
                }
                if (p.calm) { ear = 8f; headR = 4f }
                p.copy(dy = dy, headTilt = headR, headDy = headDy, ear = ear, tail = tail)
            }
            CompanionForm.WREN -> {
                var tail = 0f; var wing = 0f; var headDy = 0f; var headR = 0f
                when {
                    v == CompanionState.IDLE -> tail = if (rm) 0f else sin(t * TAU / 3.4f) * 2f
                    v == CompanionState.LISTENING -> { tail = if (rm) 6f else 6f + sin(t * 7f) * L * 10f; headR = -6f }
                    v == CompanionState.THINKING -> { tail = 4f; headR = -4f }
                    v == CreateMode.REFLECTIVE -> { tail = 2f; headR = -8f }
                    celebrating -> { tail = 12f; wing = if (rm) -40f else -30f - abs(sin(t * 11f)) * 25f * (0.3f + p.hop) }
                    v == CreateMode.JOYFUL -> { tail = if (rm) 10f else 8f + sin(t * 6f) * 5f; wing = -12f }
                    v == CompanionState.WORRIED -> { tail = -26f; wing = 6f; headDy = 2f }
                    v == CompanionState.SLEEPY -> { tail = -18f; headDy = 5f; headR = 10f }
                    v == CreateMode.PRAYERFUL -> { tail = -8f; headDy = 3f; headR = 14f; wing = -16f }
                    v == CreateMode.PEACEFUL -> { tail = -4f; headR = 6f }
                    v == CreateMode.GRATEFUL -> { tail = 4f; headR = 10f; headDy = 1f }
                    v == CompanionState.CURIOUS -> { tail = 6f * e; headR = -12f * e }
                    v == CompanionState.PROUD -> { tail = 14f * e; wing = -10f * e }
                    v == CompanionState.READING -> { tail = 2f; headR = -4f }
                }
                if (p.calm) { tail = -4f; headR = 6f }
                p.copy(tail = tail, wing = wing, headDy = headDy, headTilt = headR)
            }
            CompanionForm.PAGE -> {
                var fold = 12f; var lean = 0f
                when {
                    celebrating -> fold = if (rm) 15f else 12f + sin(t * 12f) * 3f
                    v == CompanionState.WORRIED -> { fold = 17f; lean = -4f }
                    v == CompanionState.SLEEPY -> lean = 4f
                    v == CreateMode.PRAYERFUL || v == CreateMode.GRATEFUL -> lean = 3f
                    // Proud: the corner lifts a little; Curious leans toward the question.
                    v == CompanionState.PROUD -> fold = 12f + 1.5f * e
                    v == CompanionState.CURIOUS -> lean = -2f * e
                }
                p.copy(fold = fold, pageLean = lean)
            }
        }
    }
}
