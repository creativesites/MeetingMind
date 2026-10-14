package com.craftflowtechnologies.meetingmind.core.companion.moment

import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilVariant.CURTAIN
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilVariant.DAWN
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilVariant.INKLINE
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilVariant.LIFT
import com.craftflowtechnologies.meetingmind.core.companion.moment.UnveilVariant.PLANT
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class UnveilRotationTest {

    private fun picks(space: MomentSpace, n: Int, from: RotationState = RotationState()): List<UnveilVariant> =
        UnveilRotation.preview(from, space, n)

    @Test fun `variant ids are the append-only rive values`() {
        assertEquals(listOf(1, 2, 3, 4, 5), UnveilVariant.entries.map { it.id })
        assertEquals(listOf(LIFT, INKLINE, CURTAIN, DAWN, PLANT), UnveilVariant.entries)
        assertEquals(listOf(1500, 1400, 1500, 1500, 1600), UnveilVariant.entries.map { it.baseMs })
    }

    @Test fun `weights match the spec table`() {
        assertEquals(listOf(3, 2, 2, 2, 2), UnveilRotation.weights.getValue(MomentSpace.EVERYDAY).toList())
        assertEquals(listOf(0, 2, 1, 4, 0), UnveilRotation.weights.getValue(MomentSpace.FAITH).toList())
        assertEquals(listOf(1, 4, 3, 1, 0), UnveilRotation.weights.getValue(MomentSpace.WORK).toList())
        assertEquals(listOf(3, 2, 1, 1, 4), UnveilRotation.weights.getValue(MomentSpace.STUDY).toList())
    }

    @Test fun `spaces map from the app's notebook spaces`() {
        assertEquals(MomentSpace.EVERYDAY, MomentSpace.of(NotebookSpace.PERSONAL))
        assertEquals(MomentSpace.EVERYDAY, MomentSpace.of(null))
        assertEquals(MomentSpace.STUDY, MomentSpace.of(NotebookSpace.LEARNING))
        assertEquals(MomentSpace.FAITH, MomentSpace.of(NotebookSpace.FAITH))
        assertEquals(MomentSpace.WORK, MomentSpace.of(NotebookSpace.WORK))
    }

    @Test fun `everyday from zero follows the smooth weighted round-robin`() {
        // The prototype's nextVariant() from zero (the spec's worked example swaps picks 9 and 10).
        assertEquals(
            listOf(LIFT, INKLINE, CURTAIN, DAWN, PLANT, LIFT, INKLINE, CURTAIN, DAWN, PLANT, LIFT),
            picks(MomentSpace.EVERYDAY, 11)
        )
    }

    @Test fun `deterministic for a given state`() {
        val mid = (0 until 7).fold(RotationState()) { s, _ -> UnveilRotation.next(s, MomentSpace.STUDY).state }
        assertEquals(picks(MomentSpace.STUDY, 50, mid), picks(MomentSpace.STUDY, 50, mid))
    }

    @Test fun `no repeats in 1000 picks for each space`() {
        for (space in MomentSpace.entries) {
            val p = picks(space, 1000)
            for (i in 1 until p.size) assertNotEquals("$space at $i", p[i - 1], p[i])
        }
    }

    @Test fun `no repeats across space switches`() {
        var s = RotationState()
        var last: UnveilVariant? = null
        val order = MomentSpace.entries
        for (i in 0 until 1000) {
            val space = order[(i * 7 + i / 3) % order.size]
            val pick = UnveilRotation.next(s, space)
            assertNotEquals("pick $i in $space", last, pick.variant)
            assertTrue(UnveilRotation.weight(space, pick.variant) > 0)
            last = pick.variant
            s = pick.state
        }
    }

    @Test fun `faith never yields lift or plant, and work never plant`() {
        val faith = picks(MomentSpace.FAITH, 1000)
        assertFalse(LIFT in faith)
        assertFalse(PLANT in faith)
        assertFalse(PLANT in picks(MomentSpace.WORK, 1000))
    }

    @Test fun `proportions stay within one of the weights over every complete cycle`() {
        // Faith is excluded: the no-repeat guard caps Dawn at half by design (§3.6).
        for (space in listOf(MomentSpace.EVERYDAY, MomentSpace.WORK, MomentSpace.STUDY)) {
            val w = UnveilRotation.weights.getValue(space)
            val cycle = w.sum()
            val p = picks(space, cycle * 40)
            for (c in 0 until 40) {
                val chunk = p.subList(c * cycle, (c + 1) * cycle)
                for (v in UnveilVariant.entries) {
                    val n = chunk.count { it == v }
                    assertTrue("$space cycle $c $v: $n vs ${w[v.ordinal]}", abs(n - w[v.ordinal]) <= 1)
                }
            }
            // And exactly proportional over the whole run.
            for (v in UnveilVariant.entries) assertTrue("$space $v", abs(w[v.ordinal] * 40 - p.count { it == v }) <= 2)
        }
    }

    @Test fun `faith dawn is at most half, never twice in a row`() {
        val p = picks(MomentSpace.FAITH, 700)
        val dawn = p.count { it == DAWN }
        assertTrue("$dawn", dawn <= 350)
        assertTrue("dawn is still faith's accent: $dawn", dawn >= 300)
    }

    @Test fun `seen counts every play`() {
        var s = RotationState()
        repeat(22) { s = UnveilRotation.next(s, MomentSpace.EVERYDAY).state }
        assertEquals(6, s.seen(LIFT))
        assertEquals(22, UnveilVariant.entries.sumOf { s.seen(it) })
    }

    @Test fun `familiarity speeds up at 8 and 25 plays`() {
        assertEquals(1f, UnveilRotation.familiarity(7))
        assertEquals(0.9f, UnveilRotation.familiarity(8))
        assertEquals(0.9f, UnveilRotation.familiarity(24))
        assertEquals(0.8f, UnveilRotation.familiarity(25))
    }

    @Test fun `durations scale by space and familiarity and clamp to the window`() {
        assertEquals(1500, UnveilRotation.durationMs(LIFT, MomentSpace.EVERYDAY, 1))
        assertEquals(1725, UnveilRotation.durationMs(DAWN, MomentSpace.FAITH, 1))
        assertEquals(1275, UnveilRotation.durationMs(LIFT, MomentSpace.WORK, 1))
        assertEquals(1350, UnveilRotation.durationMs(CURTAIN, MomentSpace.EVERYDAY, 8))
        // The 50th Lift in Work: 1500 × 0.85 × 0.8 = 1020, clamped up to 1200.
        assertEquals(1200, UnveilRotation.durationMs(LIFT, MomentSpace.WORK, 50))
        assertEquals(1800, UnveilRotation.durationMs(PLANT, MomentSpace.FAITH, 1))
        for (v in UnveilVariant.entries) for (s in MomentSpace.entries) for (n in listOf(1, 8, 25, 99)) {
            assertTrue(UnveilRotation.durationMs(v, s, n) in 1200..1800)
        }
    }

    @Test fun `state survives encoding and a broken value restarts`() {
        var s = RotationState()
        for (i in 0 until 37) s = UnveilRotation.next(s, MomentSpace.entries[i % 4]).state
        assertEquals(s, RotationState.decode(s.encode()))
        assertEquals(picks(MomentSpace.WORK, 20, s), picks(MomentSpace.WORK, 20, RotationState.decode(s.encode())))
        assertEquals(RotationState(), RotationState.decode("garbage"))
        assertEquals(RotationState(), RotationState.decode(null))
        assertTrue("small: ${s.encode()}", s.encode().length < 120)
    }
}
