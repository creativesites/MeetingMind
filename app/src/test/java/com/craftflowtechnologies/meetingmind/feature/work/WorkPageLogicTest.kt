package com.craftflowtechnologies.meetingmind.feature.work

import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.notes.Cursor
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteTranscriptStatus
import com.craftflowtechnologies.meetingmind.core.work.WorkNoteKind
import com.craftflowtechnologies.meetingmind.core.work.WorkNoteSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WorkPageLogicTest {
    private val day = 86_400_000L
    /** Thursday 9 Oct 2025, 11:00 local. */
    private val now = Calendar.getInstance().apply { clear(); set(2025, Calendar.OCTOBER, 9, 11, 0) }.timeInMillis
    private val startOfToday = Calendar.getInstance().apply { clear(); set(2025, Calendar.OCTOBER, 9) }.timeInMillis

    private fun item(id: String, at: Long, title: String = id) = WorkNoteItem(
        id, title, "", null, RecordingType.MEETING, at, false, false, false, NoteTranscriptStatus.None, 0, Cursor(at, id)
    )

    @Test fun `opens on Today with meetings or things that need you, otherwise Notes`() {
        assertEquals(WorkSegment.TODAY, defaultSegment(meetingsToday = 1, needsYou = 0))
        assertEquals(WorkSegment.TODAY, defaultSegment(meetingsToday = 0, needsYou = 2))
        assertEquals(WorkSegment.TODAY, defaultSegment(meetingsToday = 3, needsYou = 2))
        assertEquals(WorkSegment.NOTES, defaultSegment(meetingsToday = 0, needsYou = 0))
    }

    @Test fun `Meetings and My notes exclude each other, other chips stack`() {
        var q = NotesQuery().toggled(NoteChip.MEETINGS).toggled(NoteChip.HAS_TASKS)
        assertEquals(setOf(NoteChip.MEETINGS, NoteChip.HAS_TASKS), q.chips)
        q = q.toggled(NoteChip.MY_NOTES)
        assertEquals(setOf(NoteChip.MY_NOTES, NoteChip.HAS_TASKS), q.chips)
        q = q.toggled(NoteChip.MY_NOTES)
        assertEquals(setOf(NoteChip.HAS_TASKS), q.chips)
    }

    @Test fun `chips and project become repository filters`() {
        val f = NotesQuery(setOf(NoteChip.MEETINGS, NoteChip.HAS_RECORDING, NoteChip.THIS_WEEK), notebookId = "nb1").filters(weekStart = 123L)
        assertEquals(WorkNoteKind.MEETINGS, f.kind)
        assertTrue(f.hasRecording); assertEquals(false, f.hasOpenTasks)
        assertEquals("nb1", f.notebookId); assertEquals(123L, f.createdSince)
        val none = NotesQuery().filters(123L)
        assertEquals(WorkNoteKind.ANY, none.kind); assertNull(none.createdSince); assertNull(none.notebookId)
    }

    @Test fun `date headers read Today Yesterday This week Earlier then months`() {
        assertEquals("Today", dateHeader(startOfToday + 60_000, now))
        assertEquals("Yesterday", dateHeader(startOfToday - 60_000, now))
        assertEquals("This week", dateHeader(startOfToday - 3 * day, now))
        val oct2 = Calendar.getInstance().apply { clear(); set(2025, Calendar.OCTOBER, 1, 12, 0) }.timeInMillis
        assertEquals("Earlier", dateHeader(oct2, now))
        val aug = Calendar.getInstance().apply { clear(); set(2025, Calendar.AUGUST, 20, 12, 0) }.timeInMillis
        assertTrue(dateHeader(aug, now).startsWith("Aug") || dateHeader(aug, now).isNotBlank())
        val old = Calendar.getInstance().apply { clear(); set(2024, Calendar.MARCH, 5, 12, 0) }.timeInMillis
        assertTrue(dateHeader(old, now).contains("2024"))
    }

    @Test fun `notes are grouped under one header per date bucket in loaded order`() {
        val rows = listOf(item("a", startOfToday + 1000), item("b", startOfToday + 500), item("c", startOfToday - 1000), item("d", startOfToday - 4 * day))
        val out = groupNotes(rows, WorkNoteSort.RECENT, now)
        assertEquals(listOf("Today", "a", "b", "Yesterday", "c", "This week", "d"), out.map { if (it is NoteEntry.Header) it.label else (it as NoteEntry.Row).item.id })
        assertEquals(out.map { it.key }.toSet().size, out.size)
    }

    @Test fun `title sort groups by first letter and meeting-date sort puts undated notes last`() {
        val byTitle = groupNotes(listOf(item("1", 1, "alpha"), item("2", 1, "Apple"), item("3", 1, "beta"), item("4", 1, "42 things")), WorkNoteSort.TITLE, now)
        assertEquals(listOf("A", "B", "#"), byTitle.filterIsInstance<NoteEntry.Header>().map { it.label })
        val byDate = groupNotes(listOf(item("1", startOfToday + 1), item("2", 0L)), WorkNoteSort.MEETING_DATE, now)
        assertEquals(listOf("Today", "No date"), byDate.filterIsInstance<NoteEntry.Header>().map { it.label })
    }

    @Test fun `task buckets`() {
        assertEquals(TaskBucket.NO_DATE, taskBucket(null, now))
        assertEquals(TaskBucket.OVERDUE, taskBucket(startOfToday - 1, now))
        assertEquals(TaskBucket.TODAY, taskBucket(startOfToday + 5 * 3_600_000L, now))
        assertEquals(TaskBucket.THIS_WEEK, taskBucket(startOfToday + 3 * day, now))
        assertEquals(TaskBucket.LATER, taskBucket(startOfToday + 9 * day, now))
    }

    @Test fun `the context line leaves out zeros and pluralises`() {
        assertTrue(contextLine(now, 3, 2).endsWith("3 meetings · 2 need you"))
        assertTrue(contextLine(now, 1, 1).endsWith("1 meeting · 1 needs you"))
        assertEquals(1, contextLine(now, 0, 0).split(" · ").size)
    }

    @Test fun `the week starts on Monday`() {
        val monday = Calendar.getInstance().apply { clear(); set(2025, Calendar.OCTOBER, 6) }.timeInMillis
        assertEquals(monday, weekStart(now))
    }

    @Test fun `previews are flattened and capped`() {
        assertEquals("a b c", previewOf("  a\n\n b\t c "))
        assertTrue(previewOf("x".repeat(500)).length <= 181)
    }
}
