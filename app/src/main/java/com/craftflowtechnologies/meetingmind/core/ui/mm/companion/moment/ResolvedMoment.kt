package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment

import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentSpace
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilRotation
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Eyes

/**
 * One moment ready to play: the spec resolved for a form (and quiet), its played length, and packed
 * tracks for evaluation. Evaluation takes **base-timeline** milliseconds ([baseMsAt]) and allocates nothing.
 *
 * The time scale lives here and only here: `timeScale = playedMs / spec.baseMs`. For an Unveil the planner
 * already clamped [playedMs] to 1.2–1.8 s ([UnveilRotation.scaledMs]); the system animator scale is
 * applied by the clock on top.
 */
class ResolvedMoment(
    /** The resolved spec (form take and quiet patch applied). */
    val spec: MomentSpec,
    val form: CompanionForm,
    val space: MomentSpace,
    val quiet: Boolean,
    /** The length this moment plays for, before the system animator scale. */
    val playedMs: Int,
    /** First Keep sparkle count: 6, Work 4, quiet 0. */
    val sparkles: Int = spec.sparkles,
    /** Study: the sparkles land as tiny sprout-bar shapes. */
    val sparkleBars: Boolean = false,
    /** The First Keep chime may play (the once-ever plays only); still gated by Sounds, ringer and DND. */
    val chime: Boolean = false
) {
    val timeScale: Float = playedMs.toFloat() / spec.baseMs
    /** Faith ×0.7 on body motion. */
    val amplitude: Float = space.amplitude

    private val poseTracks = arrayOfNulls<KeyTrack>(MomentChannel.entries.size)
    private val stageTracks = arrayOfNulls<KeyTrack>(StageTarget.entries.size * StageProp.entries.size)
    private val stateMs = IntArray(spec.states.size) { spec.states[it].ms }
    private val stateValues = Array(spec.states.size) { spec.states[it].visual }
    private val eyesMs = IntArray(spec.eyes.size) { spec.eyes[it].ms }
    private val eyesValues = Array(spec.eyes.size) { spec.eyes[it].eyes }
    /** Haptic times on the played timeline (before the system scale). */
    val hapticMs: IntArray = IntArray(spec.haptics.size) { Math.round(spec.haptics[it].ms * timeScale) }

    init {
        for (t in spec.pose) poseTracks[t.channel.ordinal] = KeyTrack(t.keys)
        for (t in spec.stage) stageTracks[index(t.target, t.prop)] = KeyTrack(t.keys)
    }

    /** Played milliseconds → base-timeline milliseconds. */
    fun baseMsAt(playedMs: Float): Float = playedMs / timeScale

    fun has(target: StageTarget, prop: StageProp): Boolean = stageTracks[index(target, prop)] != null
    fun has(channel: MomentChannel): Boolean = poseTracks[channel.ordinal] != null

    /** A stage value at base time [tMs], or [default] when the spec has no such track (the end-state value). */
    fun stage(target: StageTarget, prop: StageProp, tMs: Float, default: Float): Float {
        val track = stageTracks[index(target, prop)] ?: return default
        return track.valueAt(tMs)
    }

    /** A pose channel at base time [tMs], or [default] when the spec doesn't drive it. */
    fun pose(channel: MomentChannel, tMs: Float, default: Float): Float {
        val track = poseTracks[channel.ordinal] ?: return default
        return track.valueAt(tMs)
    }

    fun visual(tMs: Float): MomentVisual {
        var v = if (stateValues.isEmpty()) MomentVisual.IDLE else stateValues[0]
        for (i in stateMs.indices) if (tMs >= stateMs[i]) v = stateValues[i]
        return v
    }

    /** The eyes override at [tMs], or null. */
    fun eyes(tMs: Float): Eyes? {
        var v: Eyes? = null
        for (i in eyesMs.indices) if (tMs >= eyesMs[i]) v = eyesValues[i]
        return v
    }

    private fun index(target: StageTarget, prop: StageProp) = target.ordinal * StageProp.entries.size + prop.ordinal

    companion object {
        /**
         * The moment for a plan, or null for [MomentPlan.Plain]. This is the one place the scale is chosen:
         * First Keep × the space's time scale; Unveil at the planner's (already clamped) duration; the nod
         * at its fixed 0.6 s.
         */
        fun forPlan(plan: MomentPlan, form: CompanionForm): ResolvedMoment? = when (plan) {
            is MomentPlan.FirstKeep -> firstKeep(form, plan.space, plan.quiet, plan.chime)
            is MomentPlan.Unveil -> ResolvedMoment(
                MomentSpecs.unveil(plan.variant).resolve(form, plan.quiet), form, plan.space, plan.quiet,
                playedMs = plan.durationMs
            )
            is MomentPlan.Nod -> ResolvedMoment(MomentSpecs.Nod.resolve(form, plan.quiet), form, plan.space, plan.quiet, MomentSpecs.Nod.baseMs)
            is MomentPlan.Plain -> null
        }

        fun firstKeep(form: CompanionForm, space: MomentSpace, quiet: Boolean, chime: Boolean = false): ResolvedMoment {
            val spec = MomentSpecs.FirstKeep.resolve(form, quiet)
            return ResolvedMoment(
                spec, form, space, quiet,
                playedMs = Math.round(spec.baseMs * space.timeScale),
                sparkles = when {
                    quiet -> 0
                    space == MomentSpace.WORK -> 4
                    else -> spec.sparkles
                },
                sparkleBars = space == MomentSpace.STUDY,
                chime = chime && !quiet
            )
        }

        /** An Unveil at its rotation length for [plays] (Companion Lab). */
        fun unveil(variant: com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilVariant, form: CompanionForm, space: MomentSpace, plays: Int = 1, quiet: Boolean = false) =
            ResolvedMoment(
                MomentSpecs.unveil(variant).resolve(form, quiet), form, space, quiet,
                playedMs = UnveilRotation.durationMs(variant, if (quiet) MomentSpace.FAITH else space, plays)
            )
    }
}
