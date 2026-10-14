package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import androidx.compose.animation.core.LinearEasing
import com.craftflowtechnologies.meetingmind.core.companion.CompanionForm
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionTier
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVisual
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms.glint

class CompanionMotionTest {
    private val eps = 1e-4f
    private val allVisuals: List<CompanionVisual> = CompanionState.entries + CreateMode.entries

    @Test fun `lerp mixes numbers and switches enums at half`() {
        val a = Pose(dy = 0f, sx = 1f, eyes = Eyes.OPEN, ear = 10f, hands = false)
        val b = Pose(dy = 10f, sx = 2f, eyes = Eyes.CLOSED, ear = 20f, hands = true)
        val q = lerp(a, b, 0.25f)
        assertEquals(2.5f, q.dy, eps); assertEquals(1.25f, q.sx, eps); assertEquals(12.5f, q.ear, eps)
        assertEquals(Eyes.OPEN, q.eyes); assertFalse(q.hands)
        val h = lerp(a, b, 0.5f)
        assertEquals(Eyes.CLOSED, h.eyes); assertTrue(h.hands)
        assertEquals(a, lerp(a, b, 0f).copy(eyes = a.eyes))
        assertEquals(b, lerp(a, b, 1f))
    }

    @Test fun `keyframe tracks interpolate with easing and clamp`() {
        val tr = Track(PoseChannel.DY, listOf(Keyframe(0f, 0f), Keyframe(0.5f, 10f, LinearEasing), Keyframe(1f, 0f, LinearEasing)))
        assertEquals(0f, tr.valueAt(-1f), eps)
        assertEquals(5f, tr.valueAt(0.25f), eps)
        assertEquals(10f, tr.valueAt(0.5f), eps)
        assertEquals(5f, tr.valueAt(0.75f), eps)
        assertEquals(0f, tr.valueAt(2f), eps)
    }

    @Test fun `celebrating follows the spec beats`() {
        val spec = ZuriMotion.specs.getValue(CompanionState.CELEBRATING)
        assertEquals(Timing.OneShot(1600), spec.timing)
        val dy = spec.track(PoseChannel.DY)!!
        assertEquals(-9f, dy.valueAt(370 / 1600f), eps)
        assertEquals(0f, dy.valueAt(1f), eps)
        assertEquals(0.94f, spec.track(PoseChannel.SY)!!.valueAt(120 / 1600f), eps)
        assertEquals(0.92f, spec.track(PoseChannel.SY)!!.valueAt(710 / 1600f), eps)
    }

    @Test fun `every visual has a spec and a static pose for every form`() {
        for (v in allVisuals) {
            assertTrue("$v", ZuriMotion.specs.containsKey(v))
            for (f in CompanionForm.entries) CompanionPoses.pose(f, v, 0f, 0.55f, reduced = true)
        }
    }

    @Test fun `static poses do not depend on time`() {
        for (v in allVisuals) for (f in CompanionForm.entries) {
            assertEquals("$f $v", CompanionPoses.pose(f, v, 0f, 0.55f, true), CompanionPoses.pose(f, v, 7.3f, 0.55f, true))
        }
    }

    @Test fun `static poses match the reference rm branches`() {
        val c = CompanionPoses.pose(CompanionForm.ZURI, CompanionState.CELEBRATING, 0f, 0f, true)
        assertEquals(-5f, c.dy, eps); assertEquals(1.04f, c.sy, eps); assertEquals(Fx.SPARKLES, c.fx)
        val l = CompanionPoses.pose(CompanionForm.ZURI, CompanionState.LISTENING, 0f, 0.55f, true)
        assertEquals(-5f, l.tilt, eps); assertEquals(Eyes.SOFT, l.eyes); assertEquals(11f * 1.05f, l.sprout1, eps)
        val w = CompanionPoses.pose(CompanionForm.ZURI, CompanionState.WORRIED, 0f, 0f, true)
        assertTrue(w.brows); assertEquals(14f, w.sproutLean, eps); assertEquals(0.88f, w.eyeScale, eps)
        val s = CompanionPoses.pose(CompanionForm.NAS, CompanionState.SLEEPY, 0f, 0f, true)
        assertEquals(Eyes.CLOSED, s.eyes); assertEquals(4f, s.ear, eps); assertEquals(8f, s.headTilt, eps)
        val p = CompanionPoses.pose(CompanionForm.WREN, CreateMode.PRAYERFUL, 0f, 0f, true)
        assertTrue(p.hands); assertEquals(14f, p.headTilt, eps)
        assertEquals(17f, CompanionPoses.pose(CompanionForm.PAGE, CompanionState.WORRIED, 0f, 0f, true).fold, eps)
    }

    @Test fun `quiet is the calm peaceful pose and ignores the level`() {
        val q = CompanionPoses.pose(CompanionForm.ZURI, CompanionState.LISTENING, 1f, 1f, false, CompanionVariant.Quiet)
        assertTrue(q.calm); assertEquals(Eyes.CLOSED, q.eyes); assertEquals(Fx.NONE, q.fx)
        assertEquals(q, CompanionPoses.pose(CompanionForm.ZURI, CompanionState.LISTENING, 1f, 0f, false, CompanionVariant.Quiet))
    }

    @Test fun `listening follows the level`() {
        val quiet = CompanionPoses.pose(CompanionForm.NAS, CompanionState.LISTENING, 0f, 0f, true)
        val loud = CompanionPoses.pose(CompanionForm.NAS, CompanionState.LISTENING, 0f, 1f, true)
        assertTrue(loud.ear > quiet.ear)
        assertTrue(loud.sy > quiet.sy)
    }

    @Test fun `animated poses move over time`() {
        assertNotEquals(
            CompanionPoses.pose(CompanionForm.ZURI, CompanionState.IDLE, 0.5f, 0f, false),
            CompanionPoses.pose(CompanionForm.ZURI, CompanionState.IDLE, 1.3f, 0f, false)
        )
    }

    @Test fun `nas celebrates with half the hop`() {
        val z = CompanionPoses.pose(CompanionForm.ZURI, CompanionState.CELEBRATING, 0.37f, 0f, false)
        val n = CompanionPoses.pose(CompanionForm.NAS, CompanionState.CELEBRATING, 0.37f, 0f, false)
        assertEquals(z.dy * 0.5f, n.dy, eps)
    }

    @Test fun `clock never runs with animations off, at T0, off screen, or when finished`() {
        val idle = ZuriMotion.specs.getValue(CompanionState.IDLE)
        assertTrue(CompanionClockGate.shouldRun(idle, CompanionTier.T1, 1f, visible = true, finished = false))
        assertFalse(CompanionClockGate.shouldRun(idle, CompanionTier.T1, 0f, visible = true, finished = false))
        assertFalse(CompanionClockGate.shouldRun(idle, CompanionTier.T0, 1f, visible = true, finished = false))
        assertFalse(CompanionClockGate.shouldRun(idle, CompanionTier.T1, 1f, visible = false, finished = false))
        assertFalse(CompanionClockGate.shouldRun(idle, CompanionTier.T2, 1f, visible = true, finished = true))
    }

    @Test fun `thinking draws at 30 fps`() {
        assertEquals(30, ZuriMotion.specs.getValue(CompanionState.THINKING).fps)
        assertFalse(CompanionClockGate.frameDue(16.7f, 30))
        assertTrue(CompanionClockGate.frameDue(33.3f, 30))
        assertTrue(CompanionClockGate.frameDue(16.7f, 60))
    }

    @Test fun `sleepy z's stop after three cycles, one-shots after their duration`() {
        assertEquals(3L * 2600, ZuriMotion.specs.getValue(CompanionState.SLEEPY).runMs)
        assertEquals(1600L, ZuriMotion.specs.getValue(CompanionState.CELEBRATING).runMs)
        assertEquals(600L, ZuriMotion.Nod.runMs)
        assertEquals(null, ZuriMotion.specs.getValue(CompanionState.LISTENING).runMs)
    }

    @Test fun `smoother attacks fast and releases slowly`() {
        val s = LevelSmoother()
        s.update(1f, 60f)
        assertEquals(1f - kotlin.math.exp(-1f), s.value, 1e-3f)
        val up = s.value
        s.update(0f, 60f)
        val dropped = up - s.value
        assertTrue("release is slower than attack", dropped < up)
        assertEquals(up * kotlin.math.exp(-60f / 250f), s.value, 1e-3f)
        repeat(100) { s.update(1f, 16f) }
        assertEquals(1f, s.value, 1e-3f)
        s.update(5f, 16f); assertTrue(s.value <= 1f)
        val before = s.value; s.update(0f, 0f); assertEquals(before, s.value, 0f)
    }

    // ───────────── Z-20: Curious, Proud, Reading ─────────────

    @Test fun `curious leans in over 600 ms and holds its static pose`() {
        val spec = ZuriMotion.specs.getValue(CompanionState.CURIOUS)
        assertEquals(Timing.OneShot(600), spec.timing)
        for (f in CompanionForm.entries) {
            val start = CompanionPoses.pose(f, CompanionState.CURIOUS, 0f, 0f, false)
            val end = CompanionPoses.pose(f, CompanionState.CURIOUS, 0.6f, 0f, false)
            val still = CompanionPoses.pose(f, CompanionState.CURIOUS, 0f, 0f, true)
            assertEquals("$f starts upright", 0f, start.tilt, eps)
            assertEquals("$f ends where the static pose is", still, end)
        }
        val z = CompanionPoses.pose(CompanionForm.ZURI, CompanionState.CURIOUS, 0f, 0f, true)
        assertEquals(8f, z.tilt, eps); assertEquals(1.1f, z.eyeScale, eps); assertEquals(1.04f, z.sy, eps)
        assertEquals(-12f, CompanionPoses.pose(CompanionForm.NAS, CompanionState.CURIOUS, 0f, 0f, true).headTilt, eps)
    }

    @Test fun `proud is chest up with one glint, small and warm, and settles to its static pose`() {
        val spec = ZuriMotion.specs.getValue(CompanionState.PROUD)
        assertEquals(Timing.OneShot(1200), spec.timing)
        for (f in CompanionForm.entries) {
            val still = CompanionPoses.pose(f, CompanionState.PROUD, 0f, 0f, true)
            assertEquals("$f", still, CompanionPoses.pose(f, CompanionState.PROUD, 1.2f, 0f, false))
            assertEquals(1.04f, still.sy, eps); assertEquals(Fx.GLINT, still.fx); assertEquals(Eyes.HAPPY, still.eyes)
            // Never a hop: the body rises at most a unit and a bit, unlike Celebrating's 9.
            for (i in 0..24) assertTrue(CompanionPoses.pose(f, CompanionState.PROUD, i * 0.05f, 0f, false).dy > -1.5f)
        }
        assertEquals(0f, glint(0.1f), eps)
        assertEquals(1f, glint(1.2f), eps)
        assertEquals(1f, glint(5f), eps)
    }

    @Test fun `reading follows the voice level and its static pose has the mouth closed`() {
        assertEquals(Timing.Driven, ZuriMotion.specs.getValue(CompanionState.READING).timing)
        assertEquals(30, ZuriMotion.specs.getValue(CompanionState.READING).fps)
        for (f in CompanionForm.entries) {
            val still = CompanionPoses.pose(f, CompanionState.READING, 0f, 0.55f, true)
            assertEquals("$f static mouth closed", 0f, still.talk, 0f)
            assertEquals("$f eyes centred", 0f, still.lookX, 0f)
        }
        val quiet = CompanionPoses.pose(CompanionForm.ZURI, CompanionState.READING, 1f, 0f, false)
        val loud = CompanionPoses.pose(CompanionForm.ZURI, CompanionState.READING, 1f, 1f, false)
        assertTrue("the sprout follows the voice", loud.sprout1 > quiet.sprout1)
        assertEquals(1f, loud.talk, eps)
        val nasQuiet = CompanionPoses.pose(CompanionForm.NAS, CompanionState.READING, 1f, 0f, false)
        val nasLoud = CompanionPoses.pose(CompanionForm.NAS, CompanionState.READING, 1f, 1f, false)
        assertEquals("Nas's ears move, subtly", 4f, nasLoud.ear - nasQuiet.ear, eps)
    }

    @Test fun `reading glance sweeps left to right per sentence, then returns`() {
        assertEquals(-1.2f, CompanionPoses.readingGlance(0f), eps)
        assertEquals(1.2f, CompanionPoses.readingGlance(CompanionPoses.ReadingSentenceS * 0.85f), 1e-3f)
        assertTrue(CompanionPoses.readingGlance(CompanionPoses.ReadingSentenceS * 0.4f) in -1.2f..1.2f)
        assertEquals(CompanionPoses.readingGlance(0.3f), CompanionPoses.readingGlance(0.3f + CompanionPoses.ReadingSentenceS), 1e-3f)
    }
}
