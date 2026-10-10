package com.craftflowtechnologies.meetingmind.core.companion

import com.craftflowtechnologies.meetingmind.BuildConfig

/** Who gets Ask {companion}, and where it may run (§8, §12.3). */
enum class AskGate {
    /** Everyone gets on-device and online Ask. The setting for the whole testing period. */
    NONE,
    /** Free gets on-device Ask; online Ask is Pro. */
    FREE_LOCAL_PRO_ONLINE,
    PRO_ONLY,
    ALL_ONLINE
}

/** The founder's switches for the companion (§10.7). */
object CompanionFlags {
    /** No tiers while testing (founder decision, 2026-10-09). */
    val askGate: AskGate = AskGate.NONE

    /** Live whisper (Z-25) is an experiment: on in debug/dev builds, off in release. */
    val liveWhisper: Boolean = BuildConfig.COMPANION_LIVE_WHISPER

    /** The T1 volume layer (Z-14). Off until the perf check lands. */
    const val volumeLayer: Boolean = false

    /**
     * Kill switch for the Rive renderer (Z-19). When true, a form whose `.riv` is bundled in
     * `assets/companion/` renders through Rive; otherwise everything renders on Canvas.
     */
    const val rive: Boolean = true
}
