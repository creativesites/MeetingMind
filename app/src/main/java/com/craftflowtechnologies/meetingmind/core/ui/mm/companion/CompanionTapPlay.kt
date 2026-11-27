package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVariant
import com.craftflowtechnologies.meetingmind.core.companion.CompanionVisual
import com.craftflowtechnologies.meetingmind.core.companion.CreateMode

/**
 * What a tap on the companion plays. Every tap gets a reaction, never the same one twice running:
 * single taps walk a cycle (nod, curious lean, a heart, a proud glint, a little hop), and quick taps
 * in a row build up, from a hop at the third to a happy bounce with sparkles from the fifth.
 * Pure, so the sequence is tested; [ZuriSlot] draws it.
 */
internal object CompanionTapPlay {

    /** One reaction: what to draw and for how long before the companion goes back to what it was doing. */
    enum class Reaction(val durationMs: Long, val visual: CompanionVisual?, val mode: CreateMode?, val variant: CompanionVariant) {
        NOD(600, null, null, CompanionVariant.Nod),
        CURIOUS(1100, CompanionState.CURIOUS, null, CompanionVariant.Normal),
        HEART(1600, null, CreateMode.GRATEFUL, CompanionVariant.Normal),
        PROUD(1300, CompanionState.PROUD, null, CompanionVariant.Normal),
        HOP(1600, CompanionState.CELEBRATING, null, CompanionVariant.Normal),
        JOY(2200, null, CreateMode.JOYFUL, CompanionVariant.Normal)
    }

    /** Taps closer together than this count as one streak. */
    const val STREAK_GAP_MS = 700L

    private val cycle = listOf(Reaction.NOD, Reaction.CURIOUS, Reaction.HEART, Reaction.PROUD, Reaction.HOP)

    /** The streak length after a tap at [now], given the previous tap at [lastTapAt] and the streak then. */
    fun streakAfter(lastTapAt: Long, streak: Int, now: Long): Int =
        if (lastTapAt > 0L && now - lastTapAt <= STREAK_GAP_MS) streak + 1 else 1

    /** The reaction for the tap numbered [count] (0 for the first) in a streak of [streak]. */
    fun reactionFor(count: Int, streak: Int): Reaction = when {
        streak >= 5 -> Reaction.JOY
        streak >= 3 -> Reaction.HOP
        else -> cycle[Math.floorMod(count, cycle.size)]
    }
}
