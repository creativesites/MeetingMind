package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.CompanionTapPlay.Reaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CompanionTapPlayTest {

    @Test
    fun `slow taps walk the cycle and never repeat back to back`() {
        val played = (0 until 10).map { CompanionTapPlay.reactionFor(it, streak = 1) }
        assertEquals(listOf(Reaction.NOD, Reaction.CURIOUS, Reaction.HEART, Reaction.PROUD, Reaction.HOP), played.take(5))
        played.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
    }

    @Test
    fun `quick taps build up to a hop and then a happy bounce`() {
        var streak = 0
        var last = 0L
        val played = (0 until 6).map { i ->
            val now = 1_000L + i * 300L
            streak = CompanionTapPlay.streakAfter(last, streak, now)
            last = now
            CompanionTapPlay.reactionFor(i, streak)
        }
        assertEquals(Reaction.NOD, played[0])
        assertEquals(Reaction.CURIOUS, played[1])
        assertEquals(Reaction.HOP, played[2])
        assertEquals(Reaction.HOP, played[3])
        assertEquals(Reaction.JOY, played[4])
        assertEquals(Reaction.JOY, played[5])
    }

    @Test
    fun `a pause ends the streak`() {
        assertEquals(1, CompanionTapPlay.streakAfter(lastTapAt = 0L, streak = 0, now = 5_000L))
        assertEquals(4, CompanionTapPlay.streakAfter(lastTapAt = 5_000L, streak = 3, now = 5_500L))
        assertEquals(1, CompanionTapPlay.streakAfter(lastTapAt = 5_000L, streak = 3, now = 6_000L))
    }

    @Test
    fun `every reaction draws something and ends`() {
        Reaction.entries.forEach { r ->
            assert(r.durationMs in 500L..2500L)
            assert(r.visual != null || r.mode != null || r.variant != com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant.Normal)
        }
    }
}
