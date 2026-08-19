package com.example.core.future

import com.example.core.model.MeetingSource

/**
 * Future meeting scheduler for remote platforms (Zoom, Google Meet, Microsoft Teams).
 * This MVP operates strictly local-first. These interfaces define the contract for Phase 2+
 * cloud meeting bot synchronization without requiring changes to the core presentation layer.
 */
data class RemoteMeeting(
    val id: String,
    val platform: String, // "ZOOM", "GOOGLE_MEET", "TEAMS"
    val joinUrl: String,
    val scheduledStartTime: Long,
    val scheduledEndTime: Long,
    val title: String,
    val hostEmail: String? = null
)

data class BotSession(
    val botId: String,
    val meetingId: String,
    val status: String, // "SCHEDULED", "JOINED", "RECORDING", "TRANSCRIPTION_READY", "DISCONNECTED"
    val audioStreamUrl: String? = null
)

data class CalendarMeeting(
    val eventId: String,
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val videoLink: String?,
    val attendees: List<String>
)

interface MeetingProvider {
    /**
     * Future integration point: schedules an automated bot to record a remote conference.
     */
    suspend fun scheduleBot(meeting: RemoteMeeting): BotSession
}

interface CalendarProvider {
    /**
     * Future integration point: retrieves upcoming meetings from Google Calendar / Microsoft 365.
     */
    suspend fun getUpcomingMeetings(): List<CalendarMeeting>
}
