package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilVariant
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Eyes
import kotlin.math.cos
import kotlin.math.exp

/*
 * Moments as data (docs/mvp/ZURI_SIGNATURE_MOMENTS.md §3.7). Keys are absolute milliseconds on the
 * base (Everyday) timeline; the time scale is applied at evaluation ([ResolvedMoment]).
 */

/** Which moment. [riveId] is the Rive `moment` input value (append-only). */
enum class MomentKind(val riveId: Int) { FIRST_KEEP(1), UNVEIL(2), NOD(3), CARD_REVEAL(4) }

/** The easings of the prototype (`EASE`), named as in the spec and mapped to `MM.motion`. */
object MomentEasing {
    val Lin: Easing = Easing { it }
    /** Emphasized (0.2, 0, 0, 1). */
    val Std: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Out: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val In: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val Io: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    /** Overshoot (about 1.04 peak for a 0 → 1 segment). */
    val Back: Easing = Easing { u ->
        if (u >= 1f) 1f else { val v = u - 1f; 1f + 2.2f * v * v * v + 1.2f * v * v }
    }
    /** A damped spring that settles by the segment's end. */
    val Spring: Easing = Easing { u -> if (u >= 1f) 1f else 1f - exp(-5.5f * u) * cos(10.5f * u) }
}

/** One key: at [ms] on the base timeline the value is [value]; [easing] shapes the segment that ends here. */
@Immutable
data class MsKey(val ms: Int, val value: Float, val easing: Easing = MomentEasing.Std)

/** A named span, for the Lab, the tests and the docs. */
@Immutable
data class Beat(val name: String, val startMs: Int, val endMs: Int)

/** Where a stage track draws (§3.7). Layers the host tags with `Modifier.momentLayer(target)` are marked (host). */
enum class StageTarget {
    COMPANION, CARD, HEADER, TITLE_WORDS, BODY, BLOOM, HALO, SPARKLES, CURTAIN, GLOW, RIPPLE, UNDERLINE, CARET,
    SPROUTS, HEADLINE, SUBLINE, ACTIONS, MINI_COMPANION,
    /** First Keep only: the processing ring closing (beat 0) and its caption. */
    RING, CAPTION,
    /** First Keep: the card's key points (skeleton bars → verbatim text). */
    KEY_POINTS,
    /** First Keep: the headline, sub line and actions block (moves up when Wren perches). */
    WORDS,
    /** The single gold glint (quiet First Keep, Dawn). */
    GLINT
}

/** The property a stage track drives. Offsets are dp; [ROTATION] is degrees; [PROGRESS] is 0..1. */
enum class StageProp { ALPHA, X_DP, Y_DP, SCALE, SCALE_Y, ROTATION, PROGRESS, BLUR_DP }

@Immutable
data class StageTrack(val target: StageTarget, val prop: StageProp, val keys: List<MsKey>)

/**
 * Character channels a moment overrides on top of the pose of its [MomentVisual] (the prototype's
 * `mpose`). Body channels apply to every form; the rest only to the forms in [formsUsing].
 */
enum class MomentChannel(val formsUsing: Set<CompanionForm>) {
    DY(All), SX(All), SY(All), TILT(All), HOP(All), LOOK_X(All), LOOK_Y(All), BLINK(All),
    /** Zuri: the sprout bars × this (10/16/10 at 1.33). */
    SPROUT_K(setOf(CompanionForm.ZURI)),
    /** Zuri: the sprout lean, degrees. */
    LEAN(setOf(CompanionForm.ZURI)),
    /** Nas: ear lift, degrees. */
    EAR(setOf(CompanionForm.NAS)),
    /** Nas and Wren: head tilt, degrees. */
    HEAD_TILT(setOf(CompanionForm.NAS, CompanionForm.WREN)),
    /** Nas: wag amplitude 0..1 (7 Hz). */
    WAG(setOf(CompanionForm.NAS)),
    /** Nas and Wren: tail, degrees. */
    TAIL(setOf(CompanionForm.NAS, CompanionForm.WREN)),
    /** Wren: wing, degrees. */
    WING(setOf(CompanionForm.WREN)),
    /** Page: fold size, units. */
    FOLD(setOf(CompanionForm.PAGE)),
    /** Page's mini (First Keep): fold and blink. */
    MINI_FOLD(setOf(CompanionForm.PAGE)),
    MINI_BLINK(setOf(CompanionForm.PAGE));
}

private val All: Set<CompanionForm> get() = CompanionForm.entries.toSet()

@Immutable
data class PoseTrack(val channel: MomentChannel, val keys: List<MsKey>)

/** The character's base state while a moment plays (the prototype's `p.state`). */
enum class MomentVisual { IDLE, THINKING, CELEBRATING, PEACEFUL }

@Immutable
data class StateKey(val ms: Int, val visual: MomentVisual)

@Immutable
data class EyesKey(val ms: Int, val eyes: Eyes)

enum class HapticKind { TICK, CONFIRM }

@Immutable
data class HapticBeat(val ms: Int, val kind: HapticKind)

/**
 * A patch over a spec: a per-form take, the quiet (Faith) version, or both. Tracks replace the spec's
 * track for the same channel or target and prop; non-null lists replace the spec's list.
 */
@Immutable
data class MomentPatch(
    val pose: List<PoseTrack> = emptyList(),
    val stage: List<StageTrack> = emptyList(),
    val states: List<StateKey>? = null,
    val eyes: List<EyesKey>? = null,
    val haptics: List<HapticBeat>? = null,
    val beats: List<Beat>? = null
)

typealias FormTake = MomentPatch

/**
 * One moment's timeline (§3.7). [sparkles] is the First Keep burst count (Work 4, Faith none).
 * [tickPerWord] keeps the Signature in time with the title words (Inkline).
 */
@Immutable
data class MomentSpec(
    val kind: MomentKind,
    val variant: UnveilVariant? = null,
    val baseMs: Int,
    val beats: List<Beat>,
    val pose: List<PoseTrack>,
    val stage: List<StageTrack>,
    val states: List<StateKey> = listOf(StateKey(0, MomentVisual.IDLE)),
    val eyes: List<EyesKey> = emptyList(),
    val formTakes: Map<CompanionForm, FormTake> = emptyMap(),
    val haptics: List<HapticBeat> = emptyList(),
    val sparkles: Int = 0,
    val sound: Boolean = false,
    val quiet: MomentPatch? = null,
    val quietTakes: Map<CompanionForm, MomentPatch> = emptyMap(),
    val tickPerWord: Boolean = false
) {
    /** This spec with [patch] applied (the prototype's `build`). */
    fun patched(patch: MomentPatch?): MomentSpec {
        if (patch == null) return this
        val poseByChannel = patch.pose.associateBy { it.channel }
        val stageByKey = patch.stage.associateBy { it.target to it.prop }
        return copy(
            pose = pose.filter { it.channel !in poseByChannel } + patch.pose,
            stage = stage.filter { (it.target to it.prop) !in stageByKey } + patch.stage,
            states = patch.states ?: states,
            eyes = patch.eyes ?: eyes,
            haptics = patch.haptics ?: haptics,
            beats = patch.beats ?: beats
        )
    }

    /** The timeline for one form, quiet or not: base + form take (+ quiet + quiet form take). */
    fun resolve(form: CompanionForm, quiet: Boolean): MomentSpec {
        var s = patched(formTakes[form])
        if (quiet) s = s.patched(this.quiet).patched(quietTakes[form])
        return s.copy(formTakes = emptyMap(), quiet = null, quietTakes = emptyMap())
    }
}

/** A keyframe track packed into arrays, so evaluation allocates nothing. */
class KeyTrack(keys: List<MsKey>) {
    private val ms = IntArray(keys.size) { keys[it].ms }
    private val values = FloatArray(keys.size) { keys[it].value }
    private val easings = Array(keys.size) { keys[it].easing }
    val lastMs: Int get() = ms.last()

    /** The prototype's `tv`: hold before the first key and after the last; ease into each key. */
    fun valueAt(tMs: Float): Float {
        if (tMs <= ms[0]) return values[0]
        for (j in 1 until ms.size) {
            if (tMs <= ms[j]) {
                val span = ms[j] - ms[j - 1]
                val u = if (span > 0) (tMs - ms[j - 1]) / span else 1f
                return values[j - 1] + (values[j] - values[j - 1]) * easings[j].transform(u.coerceIn(0f, 1f))
            }
        }
        return values[values.size - 1]
    }
}
