package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import android.os.Debug
import java.util.concurrent.atomic.AtomicInteger

/**
 * Debug counters for Companion Lab (§10.8): Canvas frame draw time and allocations, live Rive views,
 * the last Rive failure. The per-frame counters cost one boolean read unless [enabled]; the Lab
 * turns them on while it is open. Main thread only.
 */
object CompanionDiagnostics {
    /** Rive views currently created (each holds native memory and, while playing, a frame callback). */
    val liveRive = AtomicInteger(0)

    /** "<FORM>: <why>" for the last Rive file that failed its contract or load, or null. */
    @Volatile var lastRiveFailure: String? = null

    /** Per-frame counters on (Companion Lab). Turning it on starts the runtime's allocation counting. */
    @Volatile var enabled: Boolean = false
        private set

    /** Canvas frames measured since [enable]. */
    var frames: Long = 0
        private set
    /** Mean Canvas draw time per frame (pose + drawing), nanoseconds, over the last ~30 frames. */
    var drawNsAvg: Float = 0f
        private set
    /** Mean objects allocated per Canvas frame, over the last ~30 frames; −1 if the runtime can't count. */
    var allocsAvg: Float = -1f
        private set

    private var countAllocs = false
    private var startAllocs = 0

    fun enable(on: Boolean) {
        if (on == enabled) return
        enabled = on
        frames = 0; drawNsAvg = 0f; allocsAvg = -1f
        // Debug.startAllocCounting is deprecated but still counts on ART in debuggable apps.
        @Suppress("DEPRECATION")
        countAllocs = on && runCatching { Debug.resetThreadAllocCount(); Debug.startAllocCounting(); true }.getOrDefault(false)
        @Suppress("DEPRECATION")
        if (!on) runCatching { Debug.stopAllocCounting() }
    }

    /** Call at the start of a Canvas frame; pass the result to [frameEnd]. 0 when off. */
    fun frameStart(): Long {
        if (!enabled) return 0L
        @Suppress("DEPRECATION")
        if (countAllocs) startAllocs = Debug.getThreadAllocCount()
        return System.nanoTime()
    }

    fun frameEnd(start: Long) {
        if (start == 0L) return
        val ns = (System.nanoTime() - start).toFloat()
        val k = if (frames < 30) 1f / (frames + 1) else 1f / 30f
        drawNsAvg += (ns - drawNsAvg) * k
        if (countAllocs) {
            @Suppress("DEPRECATION")
            val n = (Debug.getThreadAllocCount() - startAllocs).toFloat()
            allocsAvg = if (allocsAvg < 0f) n else allocsAvg + (n - allocsAvg) * k
        }
        frames++
    }
}
