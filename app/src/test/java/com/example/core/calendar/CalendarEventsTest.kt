package com.example.core.calendar

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.model.RecordingType
import com.example.core.repository.NoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarEventsTest {

    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    /** The phone's calendar provider, as a stand-in with three events. */
    class FakeCalendar : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
            val path = uri.pathSegments
            return if (path.firstOrNull() == "instances") {
                MatrixCursor(projection!!).apply {
                    fun row(id: Long, begin: Long, end: Long, title: String, self: Int, status: Int?) = addRow(projection.map { col ->
                        when (col) {
                            CalendarContract.Instances.EVENT_ID -> id
                            CalendarContract.Instances.BEGIN -> begin
                            CalendarContract.Instances.END -> end
                            CalendarContract.Instances.TITLE -> title
                            CalendarContract.Instances.EVENT_LOCATION -> "Room 4"
                            CalendarContract.Instances.ALL_DAY -> 0
                            CalendarContract.Instances.CALENDAR_DISPLAY_NAME -> "Work"
                            CalendarContract.Instances.DISPLAY_COLOR -> 0xFF3366FF.toInt()
                            CalendarContract.Instances.SELF_ATTENDEE_STATUS -> self
                            CalendarContract.Instances.STATUS -> status
                            CalendarContract.Instances.OWNER_ACCOUNT -> "me@example.com"
                            else -> null
                        }
                    }.toTypedArray())
                    val base = 1_800_000_000_000L
                    row(1, base + 3_600_000L, base + 7_200_000L, "Design review", CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED, CalendarContract.Events.STATUS_CONFIRMED)
                    row(2, base + 1_800_000L, base + 3_600_000L, "Declined thing", CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED, null)
                    row(3, base + 900_000L, base + 1_800_000L, "Cancelled sync", CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED, CalendarContract.Events.STATUS_CANCELED)
                }
            } else {
                MatrixCursor(projection!!).apply {
                    if (args?.firstOrNull() == "1") {
                        fun row(name: String, email: String, relationship: Int, type: Int) = addRow(projection.map { col ->
                            when (col) {
                                CalendarContract.Attendees.ATTENDEE_NAME -> name
                                CalendarContract.Attendees.ATTENDEE_EMAIL -> email
                                CalendarContract.Attendees.ATTENDEE_RELATIONSHIP -> relationship
                                CalendarContract.Attendees.ATTENDEE_TYPE -> type
                                else -> null
                            }
                        }.toTypedArray())
                        row("Me", "me@example.com", CalendarContract.Attendees.RELATIONSHIP_ORGANIZER, CalendarContract.Attendees.TYPE_REQUIRED)
                        row("Sarah Lee", "sarah@example.com", CalendarContract.Attendees.RELATIONSHIP_ATTENDEE, CalendarContract.Attendees.TYPE_REQUIRED)
                        row("", "tom@example.com", CalendarContract.Attendees.RELATIONSHIP_ATTENDEE, CalendarContract.Attendees.TYPE_OPTIONAL)
                        row("Board Room", "room@resource.example.com", CalendarContract.Attendees.RELATIONSHIP_ATTENDEE, CalendarContract.Attendees.TYPE_RESOURCE)
                    }
                }
            }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }

    private lateinit var app: Application

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        Robolectric.setupContentProvider(FakeCalendar::class.java, CalendarContract.AUTHORITY)
    }

    @Test
    fun `nothing is read without permission`() = runBlocking {
        shadowOf(app).denyPermissions(Manifest.permission.READ_CALENDAR)
        assertTrue(CalendarEvents(app).between(0, Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun `events come back without declined or cancelled ones, with guests but not rooms or yourself`() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
        val events = CalendarEvents(app).upNext(now)
        val review = events.single()
        assertEquals("Design review", review.title)
        assertEquals("Room 4", review.location)
        assertEquals("1@${now + hour}", review.key)
        assertEquals(listOf("Sarah Lee", "tom@example.com"), review.otherPeople)
        assertEquals(3, review.attendees.size)
        assertEquals(3, UpNext.speakerCount(review))
    }

    // ---------------------------------------------------------------- pure rules

    private fun event(id: Long, begin: Long, end: Long, title: String = "E$id", allDay: Boolean = false, people: Int = 0) = CalendarEvent(
        id, begin, end, title, null, allDay, null, null,
        attendees = (1..people).map { Attendee("P$it", "p$it@x.com", isSelf = it == 1, isOrganizer = false) }
    )

    @Test
    fun `up next keeps what's on now and coming, timed events first`() {
        val picked = UpNext.pick(listOf(
            event(1, now - 2 * hour, now - hour),          // over
            event(2, now - 10 * 60_000, now + 20 * 60_000), // on now
            event(3, now + 2 * hour, now + 3 * hour),
            event(4, now, now + 24 * hour, allDay = true),
            event(5, now + 5 * hour, now + 6 * hour),
            event(6, now + 7 * hour, now + 8 * hour, title = " ")
        ), now)
        assertEquals(listOf(2L, 3L, 5L), picked.map { it.eventId })
    }

    @Test
    fun `the type follows the title and the speaker count follows the guest list`() {
        assertEquals(RecordingType.SERMON, UpNext.suggestedType("Sunday Service"))
        assertEquals(RecordingType.BIBLE_STUDY, UpNext.suggestedType("Wednesday Bible Study"))
        assertEquals(RecordingType.INTERVIEW, UpNext.suggestedType("Interview: backend engineer"))
        assertEquals(RecordingType.LECTURE, UpNext.suggestedType("Stats class"))
        assertEquals(RecordingType.CONVERSATION, UpNext.suggestedType("Coffee with Ana"))
        assertEquals(RecordingType.MEETING, UpNext.suggestedType("Q3 planning"))
        assertEquals(RecordingType.MEETING, UpNext.suggestedType("Glassware order")) // "class" only as a word

        assertNull(UpNext.speakerCount(event(1, 0, 1)))
        assertEquals(2, UpNext.speakerCount(event(1, 0, 1, people = 1)))
        assertEquals(4, UpNext.speakerCount(event(1, 0, 1, people = 4)))
        assertNull(UpNext.speakerCount(event(1, 0, 1, people = 12))) // big invites: let diarization decide
    }

    @Test
    fun `labels say when in plain words`() {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try { labels() } finally { TimeZone.setDefault(previous) }
    }

    private fun labels() {
        val fmt = java.text.SimpleDateFormat("HH:mm")
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        val base = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2027, 0, 15, 9, 0) }.timeInMillis
        assertEquals("Now", UpNext.whenLabel(event(1, base - 60_000, base + hour), base, fmt))
        assertEquals("In 25 min", UpNext.whenLabel(event(1, base + 25 * 60_000, base + hour), base, fmt))
        assertEquals("At 14:30", UpNext.whenLabel(event(1, base + 5 * hour + 30 * 60_000, base + 6 * hour), base, fmt))
        assertEquals("Tomorrow 09:00", UpNext.whenLabel(event(1, base + 24 * hour, base + 25 * hour), base, fmt))
        assertEquals("All day", UpNext.whenLabel(event(1, base, base + 24 * hour, allDay = true), base, fmt))
    }

    @Test
    fun `an event gets one note, found again the next time`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        val notes = NoteRepository(app, db)
        val ev = CalendarEvent(7, now, now + hour, "Design review", "Room 4", false, "Work", null,
            listOf(Attendee("Sarah", "s@x.com", false, false), Attendee("Me", "me@x.com", true, true)))
        val first = notes.noteForCalendarEvent(ev, RecordingType.MEETING)
        val again = notes.noteForCalendarEvent(ev, RecordingType.MEETING)
        assertEquals(first.id, again.id)
        assertEquals("Design review", first.title)
        assertEquals(now, first.eventDate)
        assertEquals("Sarah", first.metadata["participants"])
        assertEquals("Room 4", first.metadata["location"])
        // The same meeting next week is a different occurrence, so a different note.
        val nextWeek = notes.noteForCalendarEvent(ev.copy(begin = now + 7 * 24 * hour, end = now + 7 * 24 * hour + hour), RecordingType.MEETING)
        assertTrue(nextWeek.id != first.id)
        db.close()
    }
}
