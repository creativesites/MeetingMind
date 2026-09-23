package com.example.core.timeline

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.calendar.CalendarEvent
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.identity.AppIdentity
import com.example.core.identity.LookAndFeel
import com.example.core.model.Note
import com.example.core.model.NoteStatus
import com.example.core.model.NotebookSpace
import com.example.core.model.RecordingType
import com.example.core.repository.NoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimelineTest {

    private val hour = 3_600_000L
    private val base = Calendar.getInstance().apply { clear(); set(2027, Calendar.MARCH, 14, 9, 0) }.timeInMillis // a Sunday

    private fun note(id: String, type: RecordingType = RecordingType.GENERAL, at: Long = base, meta: Map<String, String> = emptyMap(), answeredAt: Long? = null) =
        Note(id, "T-$id", type, null, at, at, at, false, false, if (answeredAt != null) NoteStatus.ANSWERED else NoteStatus.OPEN, answeredAt, meta)

    private fun event(id: Long, begin: Long) = CalendarEvent(id, begin, begin + hour, "Event $id", "Room", false, "Work", 0xFF22AA55.toInt())

    // ---------------------------------------------------------------- assembling

    @Test
    fun `a recording and its note are one card, carrying length, status and open tasks`() {
        val items = TimelineAssembler.assemble(
            notes = listOf(note("rec"), note("plain", at = base + hour)),
            meetingsByNote = mapOf("rec" to listOf(TimelineAssembler.MeetingInfo("m1", 45 * 60_000L, "READY", base))),
            openTasksByMeeting = mapOf("m1" to 2), coverByNote = mapOf("plain" to "/pic.jpg"),
            events = emptyList(), answered = emptyList(), layers = TimelineLayer.entries.toSet()
        )
        assertEquals(listOf("note:rec", "note:plain"), items.map { it.id })
        val rec = items[0]
        assertEquals(TimelineLayer.RECORDINGS, rec.layer)
        assertEquals(ItemKind.RECORDING, rec.kind)
        assertEquals("2 open tasks", rec.badge)
        assertEquals(base + 45 * 60_000L, rec.end)
        assertTrue(rec.subtitle!!.contains("45 min"))
        assertEquals(TimelineLayer.NOTES, items[1].layer)
        assertEquals("/pic.jpg", items[1].coverPath)
    }

    @Test
    fun `an event with its note shows once, as the event, opening the note`() {
        val ev = event(7, base + 2 * hour)
        val eventNote = note("evnote", at = ev.begin, meta = mapOf(NoteRepository.CALENDAR_EVENT_KEY to ev.key))
        val items = TimelineAssembler.assemble(
            listOf(eventNote), mapOf("evnote" to listOf(TimelineAssembler.MeetingInfo("m", 60_000, "READY", ev.begin))), emptyMap(), emptyMap(),
            listOf(ev, event(8, base + 5 * hour)), emptyList(), TimelineLayer.entries.toSet()
        )
        assertEquals(2, items.size)
        val first = items.first()
        assertEquals(ItemKind.EVENT, first.kind)
        assertEquals("Recorded", first.badge)
        assertEquals(DeepTarget.Note("evnote"), first.target)
        assertTrue(items[1].target is DeepTarget.Event)
        assertEquals(0xFF22AA55L, first.accent)

        // With the calendar layer off, the note still shows by itself.
        val noEvents = TimelineAssembler.assemble(listOf(eventNote), emptyMap(), emptyMap(), emptyMap(), listOf(ev), emptyList(), setOf(TimelineLayer.NOTES))
        assertEquals(listOf("note:evnote"), noEvents.map { it.id })
    }

    @Test
    fun `faith work goes to the faith layer, answered prayers get a milestone, and layers filter`() {
        val sermon = note("s", RecordingType.SERMON)
        val request = note("r", RecordingType.PRAYER_REQUEST, at = base - 30 * 24 * hour, answeredAt = base + hour)
        val all = TimelineAssembler.assemble(listOf(sermon), emptyMap(), emptyMap(), emptyMap(), emptyList(), listOf(request), TimelineLayer.entries.toSet())
        assertEquals(TimelineLayer.FAITH, all.first { it.id == "note:s" }.layer)
        val answered = all.first { it.kind == ItemKind.ANSWERED_PRAYER }
        assertEquals("Answered: T-r", answered.title)
        assertEquals(base + hour, answered.start)

        val work = TimelineAssembler.assemble(listOf(sermon, note("n")), emptyMap(), emptyMap(), emptyMap(), emptyList(), listOf(request), setOf(TimelineLayer.NOTES))
        assertEquals(listOf("note:n"), work.map { it.id })
    }

    @Test
    fun `professional identity never gets the faith layer`() {
        assertFalse(TimelineLayer.FAITH in TimelineLayer.defaultsFor(AppIdentity(spaces = setOf(NotebookSpace.WORK))))
        assertTrue(TimelineLayer.FAITH in TimelineLayer.defaultsFor(AppIdentity()))
    }

    // ---------------------------------------------------------------- the repository, on a real database

    @Test
    fun `the repository reads a range with covers, recordings and memories, and skips drafts`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        val notes = NoteRepository(context, db)
        val inRange = notes.createNote(title = "Standup", eventDate = base)
        notes.createNote(title = "", eventDate = base, draft = true)
        notes.createNote(title = "Next week", eventDate = base + 8 * 24 * hour)
        val lastYear = notes.createNote(title = "Last year", eventDate = base - 365 * 24 * hour)
        db.meetingDao().insertMeeting(MeetingEntity("m1", "Standup", base, 20 * 60_000L, "LOCAL_RECORDING", "/a.wav", "PROCESSING", 2, "en", null, noteId = inRange.id))

        val repo = TimelineRepository(context, db)
        val items = repo.between(TimelineDays.startOfDay(base), TimelineDays.addDays(TimelineDays.startOfDay(base), 1), TimelineLayer.entries.toSet(), includeCalendar = false)
        assertEquals(listOf("Standup"), items.map { it.title })
        assertEquals("Processing", items.single().badge)
        assertEquals(ItemKind.RECORDING, items.single().kind)

        val memories = repo.onThisDay(base)
        assertEquals(listOf(lastYear.id), memories.map { it.noteId })
        assertEquals("A year ago today", memories.single().subtitle)
        db.close()
    }

    // ---------------------------------------------------------------- the sky and the greeting

    @Test
    fun `the sky follows the day - a sun that rises, peaks and sets, a moon and stars at night`() {
        val dawn = TimeOfDaySky.at(6, 30)
        val noon = TimeOfDaySky.at(12, 15)
        val dusk = TimeOfDaySky.at(18, 0)
        val night = TimeOfDaySky.at(23, 30)
        assertEquals(SkyPhase.DAWN, dawn.phase)
        assertFalse(dawn.isMoon)
        assertTrue(dawn.arc < 0.1f && dawn.elevation < 0.3f)
        assertTrue(noon.elevation > 0.95f)
        assertTrue(dusk.arc > 0.9f)
        assertTrue(night.isMoon)
        assertEquals(SkyPhase.NIGHT, night.phase)
        assertEquals(1f, night.stars)
        assertEquals(0f, noon.stars)
        // Colours drift minute by minute rather than jumping.
        assertTrue(TimeOfDaySky.at(12, 0).skyTop != TimeOfDaySky.at(15, 0).skyTop)
        assertEquals(0xFF7F7F7FL, TimeOfDaySky.lerp(0xFF000000L, 0xFFFFFFFFL, 0.5f))
    }

    @Test
    fun `greetings are playful, stable for the day, and faith-first on Sunday morning`() {
        val morning = Calendar.getInstance().apply { timeInMillis = base } // Sunday 9:00
        val ana = AppIdentity(displayName = "Ana Banda")
        val g1 = Greetings.pick(ana, morning)
        assertEquals(g1, Greetings.pick(ana, morning.clone() as Calendar))
        assertTrue(g1.contains("Ana"))
        val faith = AppIdentity(spaces = setOf(NotebookSpace.FAITH), look = LookAndFeel.SANCTUARY, displayName = "Ana")
        assertEquals("Ready for church, Ana?", Greetings.pick(faith, morning))
        assertTrue(Greetings.pick(AppIdentity(), morning).contains("friend"))
        assertEquals("2 events today · 1 recording processing", Greetings.contextLine(2, 1, 0))
        assertNull(Greetings.contextLine(0, 0, 0))
    }

    // ---------------------------------------------------------------- rhythms, prep, the week

    @Test
    fun `three Sunday sermons at the same hour become a rhythm that's due on Sunday morning`() {
        val sundays = (1..3).map { w -> RecordingType.SERMON to (base + 90 * 60_000L - w * 7 * 24 * hour) } // 10:30
        val rhythms = Rhythms.learn(sundays + (RecordingType.MEETING to base))
        assertEquals(1, rhythms.size)
        assertEquals(10 * 60 + 30, rhythms.single().minuteOfDay)
        val sundayTen = Calendar.getInstance().apply { timeInMillis = base + hour }
        assertNotNull(Rhythms.dueNow(rhythms, sundayTen))
        val sundayEvening = Calendar.getInstance().apply { timeInMillis = base + 9 * hour }
        assertNull(Rhythms.dueNow(rhythms, sundayEvening))
        assertTrue(Rhythms.describe(rhythms.single()).contains("sermon"))
    }

    @Test
    fun `prep finds the last note with the most of the same people`() {
        val older = note("old", at = base - 20 * 24 * hour, meta = mapOf("participants" to "Sarah Lee, Tom"))
        val newer = note("new", at = base - 2 * 24 * hour, meta = mapOf("participants" to "Sarah Lee"))
        val prep = MeetingPrep.find(listOf("Sarah Lee", "Tom"), listOf(older, newer), before = base)!!
        assertEquals("old", prep.lastNoteId) // two shared people beat one
        assertEquals(listOf("Sarah Lee", "Tom"), prep.sharedPeople)
        assertNull(MeetingPrep.find(listOf("Nobody"), listOf(older), base))
    }

    @Test
    fun `the week in review counts only what happened`() {
        val items = TimelineAssembler.assemble(
            listOf(note("a"), note("b", RecordingType.DEVOTIONAL)),
            mapOf("a" to listOf(TimelineAssembler.MeetingInfo("m", 30 * 60_000L, "READY", base))), emptyMap(), emptyMap(), emptyList(),
            listOf(note("r", RecordingType.PRAYER_REQUEST, answeredAt = base)), TimelineLayer.entries.toSet()
        )
        val w = WeekReview.of(items)
        assertEquals(WeekReview(recordings = 1, notes = 0, faith = 1, answered = 1, minutesRecorded = 30), w)
    }
}
