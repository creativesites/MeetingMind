package com.craftflowtechnologies.meetingmind.core.calendar

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Bridges Circle events and meeting sessions to the device's native calendar and RFC 5545 .ics format.
 */
object CalendarExporter {

    private val ICS_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    /**
     * Builds an [Intent.ACTION_INSERT] intent that opens the user's preferred calendar app
     * with event fields pre-populated.
     */
    fun buildInsertEventIntent(
        title: String,
        description: String? = null,
        startMs: Long,
        endMs: Long? = null,
        location: String? = null,
        allDay: Boolean = false
    ): Intent {
        val durationMs = (endMs ?: (startMs + 60 * 60 * 1000L))
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, durationMs)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
            if (!description.isNullOrBlank()) {
                putExtra(CalendarContract.Events.DESCRIPTION, description)
            }
            if (!location.isNullOrBlank()) {
                putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            }
        }
    }

    /**
     * Generates a standard RFC 5545 .ics string suitable for sharing or saving as an iCalendar file.
     */
    fun generateIcs(
        title: String,
        description: String? = null,
        startMs: Long,
        endMs: Long? = null,
        location: String? = null,
        url: String? = null,
        uid: String = UUID.randomUUID().toString()
    ): String = buildString {
        val now = Instant.now()
        val start = Instant.ofEpochMilli(startMs)
        val end = Instant.ofEpochMilli(endMs ?: (startMs + 60 * 60 * 1000L))

        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//MeetingMind//MeetingMind Events//EN")
        appendLine("CALSCALE:GREGORIAN")
        appendLine("METHOD:PUBLISH")
        appendLine("BEGIN:VEVENT")
        appendLine("UID:$uid@meetingmind.app")
        appendLine("DTSTAMP:${ICS_DATE_TIME.format(now)}")
        appendLine("DTSTART:${ICS_DATE_TIME.format(start)}")
        appendLine("DTEND:${ICS_DATE_TIME.format(end)}")
        appendLine("SUMMARY:${escapeIcs(title)}")
        if (!description.isNullOrBlank()) {
            appendLine("DESCRIPTION:${escapeIcs(description)}")
        }
        if (!location.isNullOrBlank()) {
            appendLine("LOCATION:${escapeIcs(location)}")
        }
        if (!url.isNullOrBlank()) {
            appendLine("URL:$url")
        }
        appendLine("STATUS:CONFIRMED")
        appendLine("END:VEVENT")
        appendLine("END:VCALENDAR")
    }

    private fun escapeIcs(value: String): String {
        return value.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
    }
}
