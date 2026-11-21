package com.craftflowtechnologies.meetingmind.feature.work

import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** The founder recorded a work meeting and Work showed nothing: every state now has a row, and each row opens the right place. */
class WorkRecentTest {
    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val day = 86_400_000L

    private fun meeting(id: String, status: String, at: Long = now - minute, noteId: String? = "n-$id", reviewedAt: Long? = null) = MeetingEntity(
        id = id, title = "Meeting $id", createdAt = at, durationMs = 38 * minute, source = "LOCAL_RECORDING", audioFilePath = null,
        status = status, participantCount = 2, language = "en", summaryText = null, noteId = noteId, reviewedAt = reviewedAt
    )

    @Test fun aMeetingJustRecordedShowsInEveryState() {
        assertEquals(WorkRecent.Kind.RECORDING, WorkRecent.rowOf(meeting("a", "RECORDING"), null, false, now).kind)
        assertEquals(WorkRecent.Kind.WRITING, WorkRecent.rowOf(meeting("b", "PROCESSING"), 42, false, now).kind)
        assertEquals(WorkRecent.Kind.WRITING, WorkRecent.rowOf(meeting("c", "QUEUED"), null, false, now).kind)
        assertEquals(WorkRecent.Kind.SAVED, WorkRecent.rowOf(meeting("d", "SAVED"), null, false, now).kind)
        assertEquals(WorkRecent.Kind.NEEDS_YOU, WorkRecent.rowOf(meeting("e", "ERROR"), null, false, now).kind)
        assertEquals(WorkRecent.Kind.NEEDS_YOU, WorkRecent.rowOf(meeting("f", "MODEL_REQUIRED"), null, false, now).kind)
        assertEquals(WorkRecent.Kind.READY, WorkRecent.rowOf(meeting("g", "READY"), null, false, now).kind)
    }

    @Test fun progressIsShownOnlyWhileItMeansSomething() {
        assertEquals("Writing your notes · 42%", WorkRecent.rowOf(meeting("b", "PROCESSING"), 42, false, now).line)
        assertEquals("Writing your notes", WorkRecent.rowOf(meeting("b", "PROCESSING"), 0, false, now).line)
        assertEquals("Writing your notes", WorkRecent.rowOf(meeting("b", "PROCESSING"), 100, false, now).line)
    }

    @Test fun aFinishedMeetingWithFindingsIsToReviewUntilReviewed() {
        assertEquals(WorkRecent.Kind.TO_REVIEW, WorkRecent.rowOf(meeting("r", "READY"), null, true, now).kind)
        assertEquals(WorkRecent.Kind.READY, WorkRecent.rowOf(meeting("r", "READY", reviewedAt = now), null, true, now).kind)
    }

    @Test fun rowsAreNewestFirstCappedAndRecent() {
        val list = (1..6).map { meeting("m$it", "READY", at = now - it * minute) } + meeting("old", "READY", at = now - 30 * day)
        val rows = WorkRecent.rows(list.shuffled(), emptyMap(), emptySet(), now)
        assertEquals(listOf("m1", "m2", "m3", "m4"), rows.map { it.meetingId })
    }

    @Test fun eachRowOpensTheRightPlace() {
        // A ready recording opens through the recording route, which lands on its notes, the same from both homes.
        assertEquals(WorkRecent.Target.PROCESSING, WorkRecent.targetOf(WorkRecent.rowOf(meeting("a", "READY"), null, false, now)))
        assertEquals(WorkRecent.Target.PROCESSING, WorkRecent.targetOf(WorkRecent.rowOf(meeting("a", "READY", noteId = null), null, false, now)))
        assertEquals(WorkRecent.Target.WRAP_UP, WorkRecent.targetOf(WorkRecent.rowOf(meeting("a", "READY"), null, true, now)))
        assertEquals(WorkRecent.Target.PROCESSING, WorkRecent.targetOf(WorkRecent.rowOf(meeting("a", "PROCESSING"), 10, false, now)))
        assertEquals(WorkRecent.Target.PROCESSING, WorkRecent.targetOf(WorkRecent.rowOf(meeting("a", "SAVED"), null, false, now)))
    }

    @Test fun anUntitledRecordingStillReadsAsSomething() {
        assertEquals("Untitled recording", WorkRecent.rowOf(meeting("u", "RECORDING").copy(title = " "), null, false, now).title)
    }
}
