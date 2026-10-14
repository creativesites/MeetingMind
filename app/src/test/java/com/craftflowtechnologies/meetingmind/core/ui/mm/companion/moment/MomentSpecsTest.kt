package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.moment

import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlan
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentPlanner
import com.craftflowtechnologies.meetingmind.core.companion.moment.MomentSpace
import com.craftflowtechnologies.meetingmind.core.companion.moment.PlainReason
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilRotation
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilVariant
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Eyes
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.management.ManagementFactory

/** The moment table, the evaluator, the time scale and the clock (Z-27). */
class MomentSpecsTest {

    private val eps = 1e-3f

    private fun allVariants(spec: MomentSpec): List<MomentSpec> =
        CompanionForm.entries.flatMap { f -> listOf(spec.resolve(f, false), spec.resolve(f, true)) } + spec

    @Test fun `every track, beat and haptic lies within the base length, keys in order`() {
        for (base in MomentSpecs.All) for (s in allVariants(base)) {
            val name = "${s.kind} ${s.variant}"
            for (t in s.pose) {
                assertTrue("$name ${t.channel} ends ≤ base", t.keys.last().ms <= s.baseMs)
                assertEquals("$name ${t.channel} ordered", t.keys.map { it.ms }.sorted(), t.keys.map { it.ms })
            }
            for (t in s.stage) {
                assertTrue("$name ${t.target}.${t.prop} ends ≤ base", t.keys.last().ms <= s.baseMs)
                assertEquals("$name ${t.target}.${t.prop} ordered", t.keys.map { it.ms }.sorted(), t.keys.map { it.ms })
            }
            for (b in s.beats) assertTrue("$name beat ${b.name}", b.startMs in 0..b.endMs && b.endMs <= s.baseMs)
            assertEquals("$name beats ordered by start", s.beats.map { it.startMs }.sorted(), s.beats.sortedBy { it.startMs }.map { it.startMs })
            for (h in s.haptics) assertTrue(h.ms in 0..s.baseMs)
            assertEquals("$name states ordered", s.states.map { it.ms }.sorted(), s.states.map { it.ms })
            // One track per channel and per target-prop after resolution.
            assertEquals(s.pose.size, s.pose.map { it.channel }.distinct().size)
            assertEquals(s.stage.size, s.stage.map { it.target to it.prop }.distinct().size)
        }
    }

    @Test fun `form takes only drive channels their form draws`() {
        for (s in MomentSpecs.All) {
            for ((form, take) in s.formTakes + s.quietTakes) for (t in take.pose) {
                assertTrue("${s.kind} ${s.variant} $form ${t.channel}", form in t.channel.formsUsing)
            }
            // The base pose tracks are body channels or Zuri's (the base take is Zuri).
            for (t in s.pose) assertTrue("${s.kind} ${t.channel}", CompanionForm.ZURI in t.channel.formsUsing)
        }
    }

    @Test fun `base lengths match the spec`() {
        assertEquals(3600, MomentSpecs.FirstKeep.baseMs)
        assertEquals(listOf(1500, 1400, 1500, 1500, 1600), UnveilVariant.entries.map { MomentSpecs.unveil(it).baseMs })
        assertEquals(UnveilVariant.entries.map { it.baseMs }, UnveilVariant.entries.map { MomentSpecs.unveil(it).baseMs })
        assertEquals(600, MomentSpecs.Nod.baseMs)
        assertEquals(1500, MomentSpecs.CardReveal.baseMs)
    }

    @Test fun `haptics - at most two in the First Keep, none in Faith, one tick per Unveil, none in Dawn and the nod`() {
        for (f in CompanionForm.entries) {
            val fk = MomentSpecs.FirstKeep.resolve(f, false)
            assertEquals(listOf(HapticBeat(700, HapticKind.TICK), HapticBeat(1500, HapticKind.CONFIRM)), fk.haptics)
            assertTrue(MomentSpecs.FirstKeep.resolve(f, true).haptics.isEmpty())
            for (v in UnveilVariant.entries) {
                val n = MomentSpecs.unveil(v).resolve(f, false).haptics.size
                assertEquals("$v", if (v == UnveilVariant.DAWN) 0 else 1, n)
            }
            assertTrue(MomentSpecs.Nod.resolve(f, false).haptics.isEmpty())
        }
        assertEquals(400, MomentSpecs.Plant.haptics.single().ms)
        assertEquals(1000, MomentSpecs.Inkline.haptics.single().ms)
    }

    @Test fun `easings`() {
        for (e in listOf(MomentEasing.Lin, MomentEasing.Std, MomentEasing.Out, MomentEasing.In, MomentEasing.Io, MomentEasing.Back, MomentEasing.Spring)) {
            assertEquals(0f, e.transform(0f), eps)
            assertEquals(1f, e.transform(1f), eps)
        }
        assertEquals(0.5f, MomentEasing.Lin.transform(0.5f), eps)
        assertTrue("std is front-loaded", MomentEasing.Std.transform(0.3f) > 0.6f)
        assertTrue("in is back-loaded", MomentEasing.In.transform(0.3f) < 0.2f)
        val peak = (1..99).maxOf { MomentEasing.Back.transform(it / 100f) }
        assertTrue("back overshoots a little: $peak", peak in 1.03f..1.06f)
        val springPeak = (1..99).maxOf { MomentEasing.Spring.transform(it / 100f) }
        assertTrue("spring overshoots: $springPeak", springPeak > 1.05f)
    }

    @Test fun `interpolation holds outside the keys and eases into each`() {
        val t = KeyTrack(listOf(MsKey(100, 0f), MsKey(300, 10f, MomentEasing.Lin), MsKey(500, 20f, MomentEasing.In)))
        assertEquals(0f, t.valueAt(0f), eps)
        assertEquals(5f, t.valueAt(200f), eps)
        assertEquals(10f, t.valueAt(300f), eps)
        assertTrue(t.valueAt(400f) < 15f)
        assertEquals(20f, t.valueAt(900f), eps)
        assertEquals(7f, KeyTrack(listOf(MsKey(0, 7f))).valueAt(50f), eps)
    }

    @Test fun `key frames of the First Keep match the prototype`() {
        val m = ResolvedMoment.firstKeep(CompanionForm.ZURI, MomentSpace.EVERYDAY, quiet = false)
        assertEquals(3600, m.playedMs)
        assertEquals(118f, m.stage(StageTarget.CARD, StageProp.Y_DP, 900f, 0f), eps)
        assertEquals(0f, m.stage(StageTarget.CARD, StageProp.Y_DP, 1600f, 9f), eps)
        assertEquals(1f, m.stage(StageTarget.CARD, StageProp.SCALE, 1600f, 0f), eps)
        assertEquals(.72f, m.stage(StageTarget.COMPANION, StageProp.SCALE, 3600f, 0f), eps)
        assertEquals(.3f, m.stage(StageTarget.BLOOM, StageProp.ALPHA, 3600f, 0f), eps)
        assertEquals(6, m.sparkles)
        val p = MomentPose.poseInto(Pose(), m, 700f)
        assertEquals(-12f, p.dy, eps)
        assertEquals(1f, p.hop, eps)
        assertEquals("sprout 10/16/10", 16f, p.sprout1, 0.1f)
        assertEquals(Eyes.HAPPY, p.eyes)
        assertEquals(MomentVisual.THINKING, m.visual(100f))
        assertEquals(MomentVisual.IDLE, m.visual(3000f))
        assertEquals(1f, MomentPose.poseInto(Pose(), m, 3030f).blink, eps)
    }

    @Test fun `the Nas take - half hop, wag, head tilt, card from the right`() {
        val m = ResolvedMoment.firstKeep(CompanionForm.NAS, MomentSpace.EVERYDAY, quiet = false)
        val p = MomentPose.poseInto(Pose(), m, 700f)
        assertEquals(-6f, p.dy, eps)
        assertEquals(24f, p.ear, eps)
        assertEquals(-8f, MomentPose.poseInto(Pose(), m, 2000f).headTilt, eps)
        assertEquals(40f, m.stage(StageTarget.CARD, StageProp.X_DP, 0f, 0f), eps)
        // The wag decays to nothing by 1.65 s.
        assertEquals(0f, m.pose(MomentChannel.WAG, 1650f, 1f), eps)
    }

    @Test fun `the quiet First Keep - no hop, no sparkles, no halo, eyes close then open glad, slower`() {
        val m = ResolvedMoment.firstKeep(CompanionForm.ZURI, MomentSpace.FAITH, quiet = true, chime = true)
        assertEquals(4140, m.playedMs)
        assertEquals(0, m.sparkles)
        assertFalse(m.chime)
        assertEquals(0f, m.stage(StageTarget.SPARKLES, StageProp.PROGRESS, 1200f, 1f), eps)
        assertEquals(0f, m.stage(StageTarget.HALO, StageProp.ALPHA, 900f, 1f), eps)
        assertEquals(0f, MomentPose.poseInto(Pose(), m, 700f).dy, eps)
        assertEquals(Eyes.CLOSED, MomentPose.poseInto(Pose(), m, 500f).eyes)
        assertEquals(Eyes.HAPPY, MomentPose.poseInto(Pose(), m, 1500f).eyes)
        assertEquals(1f, m.stage(StageTarget.GLINT, StageProp.ALPHA, 1800f, 0f), eps)
        assertEquals(4, ResolvedMoment.firstKeep(CompanionForm.ZURI, MomentSpace.WORK, false).sparkles)
        assertEquals(3060, ResolvedMoment.firstKeep(CompanionForm.ZURI, MomentSpace.WORK, false).playedMs)
        assertTrue(ResolvedMoment.firstKeep(CompanionForm.ZURI, MomentSpace.STUDY, false).sparkleBars)
    }

    @Test fun `scaling - plans carry the clamped length, the evaluator maps it back to the base timeline`() {
        val work = ResolvedMoment.forPlan(MomentPlan.Unveil(UnveilVariant.LIFT, 1275, false, MomentSpace.WORK), CompanionForm.ZURI)!!
        assertEquals(0.85f, work.timeScale, eps)
        assertEquals(700f, work.baseMsAt(595f), 0.01f)
        assertEquals(595, work.hapticMs.single())
        // The 50th Lift in Work: clamped to 1.2 s.
        val fam = ResolvedMoment.unveil(UnveilVariant.LIFT, CompanionForm.ZURI, MomentSpace.WORK, plays = 50)
        assertEquals(1200, fam.playedMs)
        assertEquals(0.8f, fam.timeScale, eps)
        for (v in UnveilVariant.entries) for (s in MomentSpace.entries) for (n in listOf(1, 8, 25, 60)) {
            assertTrue(ResolvedMoment.unveil(v, CompanionForm.NAS, s, n).playedMs in UnveilRotation.MinMs..UnveilRotation.MaxMs)
        }
        val sermon = ResolvedMoment.forPlan(MomentPlan.Unveil(UnveilVariant.DAWN, MomentPlanner.SermonDawnMs, true, MomentSpace.FAITH), CompanionForm.ZURI)!!
        assertEquals(1725, sermon.playedMs)
        assertEquals(0f, sermon.stage(StageTarget.GLINT, StageProp.ALPHA, 1050f, 1f), eps)
        assertEquals(Eyes.CLOSED, MomentPose.poseInto(Pose(), sermon, 1200f).eyes)
        assertNull(ResolvedMoment.forPlan(MomentPlan.Plain(PlainReason.SENSITIVE), CompanionForm.ZURI))
        assertEquals(600, ResolvedMoment.forPlan(MomentPlan.Nod(), CompanionForm.ZURI)!!.playedMs)
    }

    @Test fun `faith is gentle - amplitudes times 0_7`() {
        val faith = ResolvedMoment(MomentSpecs.Lift.resolve(CompanionForm.ZURI, false), CompanionForm.ZURI, MomentSpace.FAITH, false, 1725)
        val everyday = ResolvedMoment(MomentSpecs.Lift.resolve(CompanionForm.ZURI, false), CompanionForm.ZURI, MomentSpace.EVERYDAY, false, 1500)
        assertEquals(-10f, MomentPose.poseInto(Pose(), everyday, 500f).dy, eps)
        assertEquals(-7f, MomentPose.poseInto(Pose(), faith, 500f).dy, eps)
    }

    @Test fun `unveil key frames - each variant's signature`() {
        fun m(v: UnveilVariant, f: CompanionForm = CompanionForm.ZURI) = ResolvedMoment.unveil(v, f, MomentSpace.EVERYDAY)
        assertEquals(1.33f, m(UnveilVariant.LIFT).pose(MomentChannel.SPROUT_K, 500f, 0f), eps)
        assertEquals(1f, m(UnveilVariant.LIFT).stage(StageTarget.HEADER, StageProp.ALPHA, 500f, 0f), eps)
        assertEquals(.5f, m(UnveilVariant.INKLINE).stage(StageTarget.TITLE_WORDS, StageProp.PROGRESS, 550f, 0f), eps)
        assertEquals(1f, m(UnveilVariant.CURTAIN).stage(StageTarget.CURTAIN, StageProp.PROGRESS, 1000f, 0f), eps)
        assertEquals(Eyes.OPEN, MomentPose.poseInto(Pose(), m(UnveilVariant.DAWN), 800f).eyes)
        assertEquals(Eyes.CLOSED, MomentPose.poseInto(Pose(), m(UnveilVariant.DAWN), 600f).eyes)
        assertEquals(.6f, m(UnveilVariant.PLANT).stage(StageTarget.HEADER, StageProp.SCALE_Y, 400f, 0f), eps)
        assertEquals(-10f, MomentPose.poseInto(Pose(), m(UnveilVariant.LIFT, CompanionForm.NAS), 500f).headTilt, eps)
        assertEquals(-5f, MomentPose.poseInto(Pose(), m(UnveilVariant.LIFT, CompanionForm.NAS), 500f).dy, eps)
        // Companion sizes from the spec: Lift 64, Inkline 48, Curtain 56, Dawn 48, Plant 56, Nod 40 dp.
        val sizes = listOf(UnveilVariant.LIFT to 64, UnveilVariant.INKLINE to 48, UnveilVariant.CURTAIN to 56, UnveilVariant.DAWN to 48, UnveilVariant.PLANT to 56)
        for ((v, dp) in sizes) {
            val sc = m(v).stage(StageTarget.COMPANION, StageProp.SCALE, 600f, 0f)
            assertEquals("$v", dp.toFloat(), sc * MomentSpecs.UnveilCompanionDp, 1f)
        }
        val nod = ResolvedMoment.forPlan(MomentPlan.Nod(), CompanionForm.ZURI)!!
        assertEquals(40f, nod.stage(StageTarget.COMPANION, StageProp.SCALE, 300f, 0f) * MomentSpecs.UnveilCompanionDp, 0.5f)
        assertEquals(3f, MomentPose.poseInto(Pose(), nod, 300f).dy, eps)
    }

    @Test fun `the inkline signature keeps time with the words`() {
        val m = ResolvedMoment.unveil(UnveilVariant.INKLINE, CompanionForm.NAS, MomentSpace.EVERYDAY)
        val ears = (150..950 step 10).map { MomentPose.poseInto(Pose(), m, it.toFloat(), titleWords = 4).ear }
        assertTrue(ears.max() > 20f && ears.min() < 13f)
    }

    @Test fun `the clock - animator scale, clamping, animations off`() {
        val ms = 1_000_000L
        assertEquals(500f, MomentTimeline.playedMs(0, 500 * ms, 1f, 1500), eps)
        assertEquals(250f, MomentTimeline.playedMs(0, 500 * ms, 2f, 1500), eps)
        assertEquals(1500f, MomentTimeline.playedMs(0, 9_000 * ms, 1f, 1500), eps)
        assertEquals(1500f, MomentTimeline.playedMs(0, 0, 0f, 1500), eps)
        assertEquals(0f, MomentTimeline.playedMs(100, 50, 1f, 1500), eps)
    }

    @Test fun `the clock object - skip goes to the end once`() {
        val c = MomentClock(1500)
        assertTrue(c.playing)
        c.skip()
        assertFalse(c.playing); assertTrue(c.skipped); assertEquals(1500f, c.timeMs, eps)
    }

    @Test fun `evaluating a frame allocates nothing`() {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val moments = arrayOf(
            ResolvedMoment.firstKeep(CompanionForm.ZURI, MomentSpace.EVERYDAY, false),
            ResolvedMoment.firstKeep(CompanionForm.NAS, MomentSpace.FAITH, true),
            ResolvedMoment.unveil(UnveilVariant.INKLINE, CompanionForm.NAS, MomentSpace.WORK),
            ResolvedMoment.unveil(UnveilVariant.PLANT, CompanionForm.ZURI, MomentSpace.STUDY),
            ResolvedMoment.unveil(UnveilVariant.CURTAIN, CompanionForm.WREN, MomentSpace.EVERYDAY)
        )
        val pose = Pose()
        val targets = StageTarget.entries.toTypedArray()
        fun frame(m: ResolvedMoment, t: Float): Float {
            MomentPose.poseInto(pose, m, t)
            var acc = 0f
            for (i in targets.indices) acc += m.stage(targets[i], StageProp.ALPHA, t, 1f) + m.stage(targets[i], StageProp.Y_DP, t, 0f)
            return acc + m.visual(t).ordinal
        }
        repeat(500) { i -> for (j in moments.indices) frame(moments[j], (i * 7 % 3600).toFloat()) }
        val id = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(id)
        var sink = 0f
        val frames = 2000
        for (i in 0 until frames) for (j in moments.indices) sink += frame(moments[j], (i * 3 % 3600).toFloat())
        val per = (bean.getThreadAllocatedBytes(id) - before).toDouble() / (frames * moments.size)
        assertTrue("allocates $per bytes per frame ($sink)", per < 8.0)
    }
}
