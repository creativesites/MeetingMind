package com.craftflowtechnologies.meetingmind.core.companion

import com.craftflowtechnologies.meetingmind.core.model.RecordingType

/**
 * What the companion does on the recording screen (docs/mvp/ZURI_EXPERIENCE.md §3.4, §5.4). Pure,
 * so it is unit-tested on the JVM.
 */
object RecordingCompanionPolicy {

    /** Only the Sermon type is a sermon recording; every other type (Faith or not) is General. */
    fun kindOf(type: RecordingType): RecordingKind =
        if (type == RecordingType.SERMON) RecordingKind.SERMON else RecordingKind.GENERAL

    /** Sermons start Quiet unless the global "Quiet during sermons" setting is off. */
    fun startsQuiet(type: RecordingType, settings: CompanionSettings): Boolean =
        kindOf(type) == RecordingKind.SERMON && settings.quietSermons

    /** The "Quiet during sermon · tap to change" chip exists only where a recording started Quiet. */
    fun showsQuietChip(type: RecordingType, settings: CompanionSettings): Boolean = startsQuiet(type, settings)

    /** What to draw and how big (Listening 96 dp, Quiet 56 dp, §5.4). */
    data class Pose(val state: CompanionState, val variant: CompanionVariant, val sizeDp: Int, val levelDriven: Boolean)

    /**
     * Quiet keeps its calm pose through a pause. Otherwise recording is Listening, driven by the
     * mic, and a pause is Idle (the companion is not listening). Short screens use a smaller slot
     * so the timer and controls keep their room.
     */
    fun pose(recording: Boolean, quiet: Boolean, screenHeightDp: Int): Pose = when {
        quiet -> Pose(CompanionState.LISTENING, CompanionVariant.Quiet, QuietDp, levelDriven = false)
        recording -> Pose(CompanionState.LISTENING, CompanionVariant.Normal, listeningDp(screenHeightDp), levelDriven = true)
        else -> Pose(CompanionState.IDLE, CompanionVariant.Normal, listeningDp(screenHeightDp), levelDriven = false)
    }

    private fun listeningDp(screenHeightDp: Int) = if (screenHeightDp >= TallScreenDp) ListeningDp else CompactDp

    const val ListeningDp = 96
    const val QuietDp = 56
    private const val CompactDp = 64
    private const val TallScreenDp = 700
}
