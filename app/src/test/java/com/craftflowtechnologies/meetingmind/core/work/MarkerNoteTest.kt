package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.MeetingSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.MeetingRepository
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** R-1: markers kept with the recording, and merged into its note once, however often it is processed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkerNoteTest {
    private lateinit var context: Context
    private lateinit var db: MeetMindDatabase
    private lateinit var notes: NoteRepository

    private val segments = listOf(
        Marks.Segment("s1", 55_000, 62_000, null, "Grace is a gift, not a wage."),
        Marks.Segment("s2", 62_000, 70_000, null, "You cannot earn it."),
        Marks.Segment("s3", 300_000, 310_000, null, "Let us pray.")
    )
    private val marks = listOf(
        Mark(MarkKind.KEY, 65_000),
        Mark(MarkKind.SCRIPTURE, 70_000, "Ephesians 2:8"),
        Mark(MarkKind.NOTE, 80_000, "Ask Sam about the small group"),
        Mark(MarkKind.PRAYER, 305_000, "For the Kim family"),
        Mark(MarkKind.DECISION, 90_000)
    )

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        notes = NoteRepository(context, db)
    }
    @After fun tearDown() = db.close()

    private fun sermon(): Pair<String, String> = runBlocking {
        val meeting = MeetingRepository(context, db).createInitialMeeting(title = "Sunday", source = MeetingSource.LOCAL_RECORDING, recordingType = RecordingType.SERMON)
        meeting.id to db.meetingDao().getMeetingById(meeting.id)!!.noteId!!
    }

    @Test fun markersKeepTheirTextWhenSavedWithTheRecording() = runBlocking {
        val (meetingId, noteId) = sermon()
        Marks.save(db, meetingId, marks)
        assertEquals(marks, Marks.read(db.noteDao().getById(noteId)!!.metadataJson))
    }

    @Test fun anOldMarkWithoutTextStillReads() {
        assertEquals(listOf(Mark(MarkKind.KEY, 5)), Marks.read("""{"marks":"[{\"kind\":\"KEY\",\"at\":5}]"}"""))
    }

    @Test fun undoTakesBackOnlyTheLastMark() {
        RecordingMarks.clear()
        RecordingMarks.add(MarkKind.KEY, 1_000)
        RecordingMarks.add(MarkKind.NOTE, 2_000, "  hello ")
        assertEquals(Mark(MarkKind.NOTE, 2_000, "hello"), RecordingMarks.undoLast())
        assertEquals(listOf(Mark(MarkKind.KEY, 1_000)), RecordingMarks.marks.value)
        RecordingMarks.clear()
        assertEquals(null, RecordingMarks.undoLast())
    }

    @Test fun highlightsQuoteTheWordsAroundThem() {
        val g = MarkerNote.build("n", "m", marks, segments)
        val key = g.blocks.first { it.sectionKey == MarkerNote.KEY_MOMENTS && it.type == NoteBlockType.TRANSCRIPT_EXCERPT }
        assertEquals("Grace is a gift, not a wage. You cannot earn it.", key.content.text)
        assertEquals("55000", key.payload["startMs"])
        assertEquals("m", key.payload["meetingId"])
        assertEquals(listOf("s1", "s2"), key.sourceSegmentIds)
    }

    @Test fun aHighlightWithNoTranscriptStillGetsATimestamp() {
        val g = MarkerNote.build("n", "m", listOf(Mark(MarkKind.KEY, 754_000)), emptyList())
        assertEquals("Marked at 12:34", g.blocks.last().content.text)
        assertEquals("754000", g.blocks.last().payload["startMs"])
    }

    @Test fun scriptureNotesAndPrayerPointsGoUnderTheirHeadings() {
        val g = MarkerNote.build("n", "m", marks, segments)
        val scripture = g.blocks.single { it.type == NoteBlockType.SCRIPTURE }
        assertEquals("Ephesians 2:8", scripture.content.text)
        assertEquals(scripture.id, g.refs.single().blockId)
        assertEquals("Ask Sam about the small group", g.blocks.single { it.sectionKey == MarkerNote.KEY_NOTES && it.type == NoteBlockType.PARAGRAPH }.content.text)
        assertEquals("For the Kim family", g.blocks.single { it.sectionKey == MarkerNote.KEY_PRAYER && it.type == NoteBlockType.BULLET }.content.text)
        // Decisions belong to the findings, not to a section of their own.
        assertFalse(g.blocks.any { it.content.text.contains("Decision") })
    }

    @Test fun nothingMarkedWritesNothing() {
        assertTrue(MarkerNote.build("n", "m", listOf(Mark(MarkKind.ACTION, 1)), segments).blocks.isEmpty())
    }

    @Test fun mergingTwiceGivesTheSameNote() = runBlocking {
        val (meetingId, noteId) = sermon()
        Marks.save(db, meetingId, marks)
        MarkerNote.apply(context, db, meetingId, segments)
        val first = notes.getDocument(noteId)!!.blocks.map { it.sectionKey to it.content.text }
        assertTrue(first.any { it.first == MarkerNote.KEY_MOMENTS })
        MarkerNote.apply(context, db, meetingId, segments)
        MarkerNote.apply(context, db, meetingId, segments)
        assertEquals(first, notes.getDocument(noteId)!!.blocks.map { it.sectionKey to it.content.text })
        assertEquals(1, db.scriptureDao().getForNote(noteId).size)
    }

    @Test fun aDecisionMarkBecomesOneUserCapturedDecisionEvenWhenReprocessed() = runBlocking {
        val (meetingId, _) = sermon()
        Marks.save(db, meetingId, marks)
        repeat(2) { Marks.reconcile(db, meetingId, segments) }
        val decisions = db.decisionDao().getDecisionsForMeetingDirect(meetingId)
        assertEquals(1, decisions.size)
        assertTrue(decisions.single().id.startsWith("mark_"))
        assertEquals("You cannot earn it.", decisions.single().text)
    }

    @Test fun aTypedActionKeepsTheUsersWords() = runBlocking {
        val (meetingId, _) = sermon()
        Marks.save(db, meetingId, listOf(Mark(MarkKind.ACTION, 300_000, "Send Sam the notes")))
        Marks.reconcile(db, meetingId, segments)
        assertEquals(listOf("Send Sam the notes"), db.actionItemDao().getActionItemsForMeetingDirect(meetingId).map { it.task })
    }
}
