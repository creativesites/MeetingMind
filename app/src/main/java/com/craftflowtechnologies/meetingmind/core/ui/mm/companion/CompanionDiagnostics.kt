package com.craftflowtechnologies.meetingmind.core.ui.mm.companion

import java.util.concurrent.atomic.AtomicInteger

/**
 * Debug counters for Companion Lab: live Rive views and the last Rive failure. Always on (they cost
 * an increment); the per-frame counters are added by the perf work and stay off unless enabled.
 */
object CompanionDiagnostics {
    /** Rive views currently created (each holds native memory and, while playing, a frame callback). */
    val liveRive = AtomicInteger(0)

    /** "<FORM>: <why>" for the last Rive file that failed its contract or load, or null. */
    @Volatile var lastRiveFailure: String? = null
}
