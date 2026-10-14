package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.database.TranscriptSegmentEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage 0 regression: a note with two recordings (Sermon Part 1 and Part 2). A mark tapped in
 * Part 1 must never be reconciled against Part 2's transcript, even though both sit at the same
 * local timestamp. Before the fix, [Marks.reconcile] read every mark on the shared note, so
 * Part 2's processing turned Part 1's mark into an action item on Part 2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingSourceIsolationTest {
    private lateinit var context: Context
    private lateinit var db: MeetMindDatabase

    private val part1 = "m9a"
    private val part2 = "m9b"

    private val part1Segments = listOf(
        Marks.Segment("p1s1", 0, 20_000, null, "Part one: remember to send the notes.")
    )
    private val part2Segments = listOf(
        Marks.Segment("p2s1", 0, 20_000, null, "Part two: we pray for the Kim family.")
    )

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java).allowMainThreadQueries().build()
        MeetMindDatabase.setInstanceForTest(db)
        db.noteDao().upsert(NoteEntity("n9", "Sermon series", "SERMON", null, 1, 1, 1, false, false, "OPEN", null, "{}", null, ""))
        db.meetingDao().insertMeeting(MeetingEntity(part1, "Part 1", 1, 60_000, "LOCAL_RECORDING", null, "READY", 2, "en", null, recordingType = "SERMON", noteId = "n9"))
        db.meetingDao().insertMeeting(MeetingEntity(part2, "Part 2", 2, 60_000, "LOCAL_RECORDING", null, "READY", 2, "en", null, recordingType = "SERMON", noteId = "n9"))
        db.transcriptDao().insertSegments(
            part1Segments.map { TranscriptSegmentEntity(it.id, part1, null, null, it.startMs, it.endMs, it.text, null) } +
                part2Segments.map { TranscriptSegmentEntity(it.id, part2, null, null, it.startMs, it.endMs, it.text, null) }
        )
    }

    @After
    fun tearDown() {
        MeetMindDatabase.setInstanceForTest(null)
        db.close()
    }

    @Test
    fun partOneMarkIsNotReconciledAgainstPartTwoSegments() = runBlocking {
        Marks.save(db, part1, listOf(Mark(MarkKind.ACTION, 0, "Send the notes")))

        Marks.reconcile(db, part2, part2Segments)

        assertTrue(
            "Part 1's mark must not create an action on Part 2",
            db.actionItemDao().getActionItemsForMeetingDirect(part2).isEmpty()
        )
    }

    @Test
    fun partOneMarkStillReconcilesWithinPartOne() = runBlocking {
        Marks.save(db, part1, listOf(Mark(MarkKind.ACTION, 0, "Send the notes")))

        Marks.reconcile(db, part1, part1Segments)

        val items = db.actionItemDao().getActionItemsForMeetingDirect(part1)
        assertEquals(listOf("Send the notes"), items.map { it.task })
    }

    @Test
    fun unassignedLegacyMarkIsNotReconciledAgainstEitherPart() = runBlocking {
        // A legacy mark on a two-part note has no reliable owner, so the migration keeps it unassigned.
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO legacy_note_marks (id, noteId, ordinal, status, rawKind, atMs, text, rawJson, createdAt) " +
                "VALUES ('legacy:n9:0', 'n9', 0, 'UNASSIGNED', 'ACTION', 0, 'Old', '{}', 1)"
        )

        Marks.reconcile(db, part1, part1Segments)
        Marks.reconcile(db, part2, part2Segments)

        assertTrue(db.actionItemDao().getActionItemsForMeetingDirect(part1).isEmpty())
        assertTrue(db.actionItemDao().getActionItemsForMeetingDirect(part2).isEmpty())
    }
}
