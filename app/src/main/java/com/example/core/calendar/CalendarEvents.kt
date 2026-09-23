package com.example.core.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.core.model.RecordingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Someone invited to an event. */
data class Attendee(val name: String, val email: String?, val isSelf: Boolean, val isOrganizer: Boolean)

/** One occurrence of a calendar event (a repeating meeting has one per day it happens). */
data class CalendarEvent(
    val eventId: Long,
    val begin: Long,
    val end: Long,
    val title: String,
    val location: String?,
    val allDay: Boolean,
    val calendarName: String?,
    val color: Int?,
    val attendees: List<Attendee> = emptyList(),
    /** The calendar's own account (the phone's owner), used to tell them apart from guests. */
    val ownerAccount: String? = null
) {
    /** Identifies this occurrence, so the note made for it is found again rather than duplicated. */
    val key: String get() = "$eventId@$begin"

    /** Everyone except the phone's owner, by name (or email when that's all the calendar has). */
    val otherPeople: List<String> get() = attendees.filter { !it.isSelf }.map { it.name.ifBlank { it.email.orEmpty() } }.filter { it.isNotBlank() }.distinct()
}

/**
 * Today's and upcoming events from every calendar synced to the phone — Google, Outlook, Samsung,
 * whatever the person already uses — through Android's own calendar provider (docs/PLAN_V1.md M8).
 *
 * No accounts, no OAuth, nothing leaves the phone. It reads only, and only after the person turns
 * it on and grants READ_CALENDAR.
 */
class CalendarEvents(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events overlapping [from]..[to], soonest first, without declined or cancelled ones. */
    suspend fun between(from: Long, to: Long): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.OWNER_ACCOUNT
        )
        val events = runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        if (c.getInt(8) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                        if (!c.isNull(9) && c.getInt(9) == CalendarContract.Events.STATUS_CANCELED) continue
                        add(
                            CalendarEvent(
                                eventId = c.getLong(0), begin = c.getLong(1), end = c.getLong(2),
                                title = c.getString(3)?.trim().orEmpty(), location = c.getString(4)?.trim()?.ifEmpty { null },
                                allDay = c.getInt(5) == 1, calendarName = c.getString(6),
                                color = if (c.isNull(7)) null else c.getInt(7),
                                ownerAccount = c.getString(10)?.trim()?.ifEmpty { null }
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
        events
    }

    /** Who is invited to an event; [ownerAccount] marks the phone's owner among them. */
    suspend fun attendees(eventId: Long, ownerAccount: String?): List<Attendee> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        runCatching {
            CalendarContract.Attendees.query(
                context.contentResolver, eventId,
                arrayOf(
                    CalendarContract.Attendees.ATTENDEE_NAME, CalendarContract.Attendees.ATTENDEE_EMAIL,
                    CalendarContract.Attendees.ATTENDEE_RELATIONSHIP, CalendarContract.Attendees.ATTENDEE_TYPE
                )
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        if (c.getInt(3) == CalendarContract.Attendees.TYPE_RESOURCE) continue // rooms, projectors
                        val email = c.getString(1)?.trim()?.ifEmpty { null }
                        add(
                            Attendee(
                                name = c.getString(0)?.trim().orEmpty(),
                                email = email,
                                isSelf = email != null && ownerAccount != null && email.equals(ownerAccount, ignoreCase = true),
                                isOrganizer = c.getInt(2) == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** What to show on Home: what's on now and what's next, today and tomorrow morning. */
    suspend fun upNext(now: Long = System.currentTimeMillis()): List<CalendarEvent> {
        val events = between(now - 3 * HOUR, now + 30 * HOUR)
        return UpNext.pick(events, now).map { it.copy(attendees = attendees(it.eventId, it.ownerAccount)) }
    }

    companion object { private const val HOUR = 3_600_000L }
}

/** The pure decisions about events, kept apart from the provider so they can be tested. */
object UpNext {

    /**
     * Events still worth recording: in progress (started, not ended) or starting within the next
     * day, timed events before all-day ones, at most [limit].
     */
    fun pick(events: List<CalendarEvent>, now: Long, limit: Int = 3): List<CalendarEvent> =
        events.asSequence()
            .filter { it.end > now && it.title.isNotBlank() }
            .filter { !it.allDay || it.begin <= now + 12 * 3_600_000L }
            .sortedWith(compareBy<CalendarEvent> { it.allDay }.thenBy { it.begin })
            .distinctBy { it.key }
            .take(limit)
            .toList()

    /** A likely recording type from the event title; the picker still shows so it can be changed. */
    fun suggestedType(title: String): RecordingType {
        val t = title.lowercase()
        fun has(vararg words: String) = words.any { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(t) }
        return when {
            has("bible study") -> RecordingType.BIBLE_STUDY
            has("sermon", "service", "worship", "church", "mass", "sunday service") -> RecordingType.SERMON
            has("interview", "screening") -> RecordingType.INTERVIEW
            has("lecture", "class", "seminar", "course", "lesson", "tutorial") -> RecordingType.LECTURE
            has("brainstorm", "workshop", "ideation") -> RecordingType.BRAINSTORM
            has("1:1", "1-1", "one on one", "one-on-one", "catch up", "catch-up", "coffee") -> RecordingType.CONVERSATION
            else -> RecordingType.MEETING
        }
    }

    /**
     * Speaker count for diarization from who's invited: the other people plus the phone's owner.
     * Unknown (no guest list) stays unknown; large invites are left to automatic detection, since
     * many invitees don't speak.
     */
    fun speakerCount(event: CalendarEvent): Int? {
        val people = event.attendees.map { it.email ?: it.name }.filter { it.isNotBlank() }.distinct().size
        if (people == 0) return null
        val count = maxOf(people, 2)
        return count.takeIf { it <= 6 }
    }

    /** "Now", "In 25 min", "At 14:30", "Tomorrow 09:00". */
    fun whenLabel(event: CalendarEvent, now: Long, timeFormat: java.text.DateFormat): String {
        if (event.allDay) return "All day"
        if (event.begin <= now) return "Now"
        val minutes = ((event.begin - now) / 60_000L).toInt()
        if (minutes < 60) return "In ${maxOf(minutes, 1)} min"
        val today = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val day = java.util.Calendar.getInstance().apply { timeInMillis = event.begin }
        val sameDay = today.get(java.util.Calendar.DAY_OF_YEAR) == day.get(java.util.Calendar.DAY_OF_YEAR) &&
            today.get(java.util.Calendar.YEAR) == day.get(java.util.Calendar.YEAR)
        return (if (sameDay) "At " else "Tomorrow ") + timeFormat.format(java.util.Date(event.begin))
    }
}
