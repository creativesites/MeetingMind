package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment

import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVisual
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.CompanionPoses
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Eyes
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Fx
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Mouth
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Pose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The character's pose during a moment: the prototype's `mpose`. The pose of the moment's
 * [MomentVisual] is the basis, and every channel the spec drives overrides it. Written in place, so the
 * per-frame path allocates nothing. This is the moment pose-override hook; it wraps [CompanionPoses]
 * without changing it.
 */
object MomentPose {

    private fun basisOf(v: MomentVisual): CompanionVisual = when (v) {
        MomentVisual.IDLE, MomentVisual.CELEBRATING -> CompanionState.IDLE
        MomentVisual.THINKING -> CompanionState.THINKING
        MomentVisual.PEACEFUL -> CreateMode.PEACEFUL
    }

    /** The visual the drawer is told it draws (cheeks, beak shape…). */
    fun drawnVisual(v: MomentVisual): CompanionVisual = when (v) {
        MomentVisual.CELEBRATING -> CompanionState.CELEBRATING
        else -> basisOf(v)
    }

    /**
     * Fills [out] for base time [tMs]. [titleWords] is the note title's word count, for the Inkline
     * per-word tick.
     */
    fun poseInto(out: Pose, m: ResolvedMoment, tMs: Float, titleWords: Int = 5): Pose {
        val form = m.form
        val tSec = tMs / 1000f
        val v = m.visual(tMs)
        CompanionPoses.poseInto(out, form, basisOf(v), tSec, 0.5f, reduced = false)
        if (v == MomentVisual.CELEBRATING) {
            out.eyes = Eyes.HAPPY; out.mouth = Mouth.OPEN; out.sx = 1f; out.sy = 1f; out.dy = 0f; out.fx = Fx.NONE
            when (form) {
                CompanionForm.ZURI -> { out.sprout0 = 8f; out.sprout1 = 12f; out.sprout2 = 8f; out.sproutLean = 0f }
                CompanionForm.NAS -> { out.ear = 24f + sin(tSec * 9f) * 6f; out.tail = sin(tSec * 14f) * 28f }
                CompanionForm.WREN -> { out.tail = 12f }
                CompanionForm.PAGE -> { out.fold = 12f + sin(tSec * 12f) * 3f }
            }
        }
        val a = m.amplitude
        out.dy = m.pose(MomentChannel.DY, tMs, out.dy) * a
        out.hop = m.pose(MomentChannel.HOP, tMs, out.hop) * a
        out.sx = 1f + (m.pose(MomentChannel.SX, tMs, out.sx) - 1f) * a
        out.sy = 1f + (m.pose(MomentChannel.SY, tMs, out.sy) - 1f) * a
        out.tilt = m.pose(MomentChannel.TILT, tMs, out.tilt) * a
        out.lookX = m.pose(MomentChannel.LOOK_X, tMs, out.lookX)
        out.lookY = m.pose(MomentChannel.LOOK_Y, tMs, out.lookY)
        out.blink = m.pose(MomentChannel.BLINK, tMs, out.blink)
        when (form) {
            CompanionForm.ZURI -> {
                var k = m.pose(MomentChannel.SPROUT_K, tMs, 1f)
                val kk = if (m.spec.tickPerWord) tick(m, tMs, titleWords) else -1f
                if (kk >= 0f) k = 1f + 0.35f * kk
                out.sprout0 *= k; out.sprout1 *= k; out.sprout2 *= k
                out.sproutLean = m.pose(MomentChannel.LEAN, tMs, out.sproutLean)
            }
            CompanionForm.NAS -> {
                out.ear = m.pose(MomentChannel.EAR, tMs, out.ear)
                out.headTilt = m.pose(MomentChannel.HEAD_TILT, tMs, out.headTilt)
                if (m.has(MomentChannel.WAG)) out.tail = sin(tSec * 44f) * 26f * m.pose(MomentChannel.WAG, tMs, 0f) * a
                out.tail = m.pose(MomentChannel.TAIL, tMs, out.tail)
                val kk = if (m.spec.tickPerWord) tick(m, tMs, titleWords) else -1f
                if (kk >= 0f) out.ear = 12f + 9f * kk
            }
            CompanionForm.WREN -> {
                out.headTilt = m.pose(MomentChannel.HEAD_TILT, tMs, out.headTilt)
                out.tail = m.pose(MomentChannel.TAIL, tMs, out.tail)
                out.wing = if (m.has(MomentChannel.WING)) m.pose(MomentChannel.WING, tMs, 0f)
                else if (v == MomentVisual.CELEBRATING) -30f - abs(sin(tSec * 11f)) * 25f * (0.3f + out.hop) else out.wing
                val kk = if (m.spec.tickPerWord) tick(m, tMs, titleWords) else -1f
                if (kk >= 0f) out.headTilt = -6f + 10f * kk
            }
            CompanionForm.PAGE -> out.fold = m.pose(MomentChannel.FOLD, tMs, out.fold)
        }
        val e = m.eyes(tMs)
        if (e != null) out.eyes = e
        return out
    }

    /** The Inkline metronome, 0..1 per word while the title writes, or −1 outside it (no boxing). */
    private fun tick(m: ResolvedMoment, tMs: Float, words: Int): Float {
        val tp = m.stage(StageTarget.TITLE_WORDS, StageProp.PROGRESS, tMs, 1f)
        if (tp <= 0f || tp >= 1f) return -1f
        val q = words.coerceIn(1, 8)
        return abs(sin(tp * q * PI.toFloat()))
    }

    /** The Page mini (First Keep end): idle, with its own fold and blink. */
    fun miniInto(out: Pose, m: ResolvedMoment, tMs: Float, reduced: Boolean): Pose {
        CompanionPoses.poseInto(out, CompanionForm.PAGE, CompanionState.IDLE, tMs / 1000f, 0.5f, reduced)
        out.fold = m.pose(MomentChannel.MINI_FOLD, tMs, 12f)
        out.blink = if (reduced) 0f else m.pose(MomentChannel.MINI_BLINK, tMs, 0f)
        return out
    }

    /**
     * The First Keep's static end pose (§7.7, reduced motion): the static Celebrating pose, or Peaceful with
     * glad eyes when quiet. Nas keeps his head tilted at the card.
     */
    fun firstKeepStaticInto(out: Pose, form: CompanionForm, quiet: Boolean): Pose {
        if (quiet) {
            CompanionPoses.poseInto(out, form, CreateMode.PEACEFUL, 0f, 0.5f, reduced = true)
            out.eyes = Eyes.HAPPY
        } else {
            CompanionPoses.poseInto(out, form, CompanionState.CELEBRATING, 0f, 0.5f, reduced = true)
        }
        if (form == CompanionForm.NAS) out.headTilt = -8f
        return out
    }
}
