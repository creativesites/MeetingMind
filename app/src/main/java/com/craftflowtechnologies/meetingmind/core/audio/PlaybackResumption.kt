package com.craftflowtechnologies.meetingmind.core.audio

import androidx.media3.common.MediaItem

/** What to load when the system asks the player to resume after the app was killed (notification, Bluetooth or headset play button). */
data class ResumeData(val items: List<MediaItem>, val startIndex: Int, val startPositionMs: Long)

fun interface ResumptionSource {
    /** The last queue and where it was left, or null when there's nothing to resume. */
    suspend fun last(): ResumeData?
}

/**
 * Registered once at app start (see MeetingMindApp), because after the process dies the service can
 * be recreated on its own and has to find the last reading without any screen being open.
 */
object PlaybackResumption {
    @Volatile var source: ResumptionSource? = null
}
