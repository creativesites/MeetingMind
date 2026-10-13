package com.craftflowtechnologies.meetingmind.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.work.Mark
import com.craftflowtechnologies.meetingmind.core.work.MarkKind
import com.craftflowtechnologies.meetingmind.core.work.Marks
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The source-owned marker table (schema 25): ordering, isolation between recordings of one note,
 * and what a delete may and may not reach.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingMarkerDaoTest {
    private lateinit var context: Context
    private lateinit var db: MeetMindDatabase

    private val part1 = "m9a"
    private val part2 = "m9b"

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        db.noteDao().upsert(NoteEntity("n9", "Sermon series", "SERMON", null, 1, 1, 1, false, false, "OPEN", null, "{}", null, ""))
        db.meetingDao().insertMeeting(MeetingEntity(part1, "Part 1", 1, 60_000, "LOCAL_RECORDING", null, "READY", 2, "en", null, recordingType = "SERMON", noteId = "n9"))
        db.meetingDao().insertMeeting(MeetingEntity(part2, "Part 2", 2, 60_000, "LOCAL_RECORDING", null, "READY", 2, "en", null, recordingType = "SERMON", noteId = "n9"))
    }

    @After
    fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
        db.close()
    }

    private fun marker(id: String, meetingId: String, sequence: Long, timestampMs: Long, kind: String = "KEY", text: String? = null) =
        RecordingMarkerEntity(id, meetingId, sequence, timestampMs, kind, text, createdAt = 1, updatedAt = 1)

    @Test
    fun marksAreReadInTimestampOrderThenInsertionOrder() = runBlocking {
        db.recordingMarkerDao().insertAll(listOf(marker("a", part1, 0, 300), marker("b", part1, 1, 100), marker("c", part1, 2, 100)))

        assertEquals(listOf("b", "c", "a"), db.recordingMarkerDao().forMeeting(part1).map { it.id })
    }

    @Test
    fun aRecordingOnlyEverSeesItsOwnMarks() = runBlocking {
        // Both parts have a mark at the same local timestamp: they must never be confused.
        db.recordingMarkerDao().insertAll(listOf(marker("a", part1, 0, 5_000), marker("b", part1, 1, 9_000), marker("c", part2, 0, 5_000)))

        assertEquals(listOf("a", "b"), db.recordingMarkerDao().forMeeting(part1).map { it.id })
        assertEquals(listOf("c"), db.recordingMarkerDao().forMeeting(part2).map { it.id })
    }

    @Test
    fun nextSequenceIsOnePastTheHighestStoredMark() = runBlocking {
        assertEquals(0L, db.recordingMarkerDao().nextSequence(part1))
        db.recordingMarkerDao().insertAll(listOf(marker("a", part1, 0, 1), marker("b", part1, 4, 2)))

        assertEquals(5L, db.recordingMarkerDao().nextSequence(part1))
        assertEquals(0L, db.recordingMarkerDao().nextSequence(part2))
    }

    @Test
    fun deletingARecordingRemovesOnlyItsOwnMarks() = runBlocking {
        db.recordingMarkerDao().insertAll(listOf(marker("a", part1, 0, 1), marker("c", part2, 0, 1)))

        db.meetingDao().deleteMeetingById(part1)

        assertEquals(emptyList<RecordingMarkerEntity>(), db.recordingMarkerDao().forMeeting(part1))
        assertEquals(listOf("c"), db.recordingMarkerDao().forMeeting(part2).map { it.id })
        assertNotNull("the other recording survives", db.meetingDao().getMeetingById(part2))
        assertNotNull("the parent note survives", db.noteDao().getById("n9"))
    }

    @Test
    fun deletingANoteKeepsItsRecordingsAndTheirMarks() = runBlocking {
        db.recordingMarkerDao().insertAll(listOf(marker("a", part1, 0, 1)))

        db.noteDao().delete("n9")

        // A note does not own its recordings: the audio and the marks outlive it.
        assertNull("the note is gone", db.noteDao().getById("n9"))
        assertNotNull("the recording is kept", db.meetingDao().getMeetingById(part1))
        assertEquals(listOf("a"), db.recordingMarkerDao().forMeeting(part1).map { it.id })
    }

    @Test
    fun aKindThisBuildDoesNotKnowIsKeptButNotShownAsAMark() = runBlocking {
        db.recordingMarkerDao().insertAll(listOf(marker("future", part1, 0, 1, kind = "HOLOGRAM")))

        assertEquals("HOLOGRAM", db.recordingMarkerDao().forMeeting(part1).single().kind)
        assertEquals(emptyList<Mark>(), Marks.forMeeting(db, part1))
    }

    @Test
    fun savingMarksAddsThemToTheRecordingAndKeepsTheSequence() = runBlocking {
        Marks.save(db, part1, listOf(Mark(MarkKind.KEY, 1_000)))
        Marks.save(db, part1, listOf(Mark(MarkKind.QUESTION, 2_000, "Who owns it?")))

        assertEquals(listOf(0L, 1L), db.recordingMarkerDao().forMeeting(part1).map { it.sequence }.sorted())
        assertEquals(
            listOf(Mark(MarkKind.KEY, 1_000), Mark(MarkKind.QUESTION, 2_000, "Who owns it?")),
            Marks.forMeeting(db, part1)
        )
        assertEquals(emptyList<Mark>(), Marks.forMeeting(db, part2))
    }

    /** Stored by name: the seven kinds that exist keep their names, and IDEA is appended after them. */
    @Test
    fun theStoredKindNamesAreUnchangedAndIdeaIsAppended() {
        assertEquals(
            listOf("KEY", "ACTION", "QUESTION", "DECISION", "SCRIPTURE", "NOTE", "PRAYER", "IDEA"),
            MarkKind.entries.map { it.name }
        )
    }

    @Test
    fun savingForAMeetingThatDoesNotExistWritesNothing() = runBlocking {
        Marks.save(db, "missing", listOf(Mark(MarkKind.KEY, 1)))

        assertEquals(emptyList<RecordingMarkerEntity>(), db.recordingMarkerDao().forMeeting("missing"))
    }
}
