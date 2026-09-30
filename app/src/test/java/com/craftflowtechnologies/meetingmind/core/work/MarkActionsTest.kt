package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.core.audio.MeetingRecordingService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A tap on the recording notification or the lock screen writes a mark, at the recording's own clock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkActionsTest {
    @Before fun setup() = RecordingMarks.clear()
    @After fun tearDown() = RecordingMarks.clear()

    @Test fun eachActionMapsToItsMark() {
        MarkKind.entries.forEach { kind ->
            assertEquals(kind, MarkActions.kindOf(MarkActions.actionOf(kind)))
        }
        assertEquals(MarkKind.KEY, MarkActions.kindOf(MarkActions.KEY_ACTION))
        assertEquals(MarkKind.ACTION, MarkActions.kindOf(MarkActions.ACTION_ACTION))
        assertEquals(MarkKind.QUESTION, MarkActions.kindOf(MarkActions.QUESTION_ACTION))
        assertNull(MarkActions.kindOf(MeetingRecordingService.ACTION_STOP))
        assertNull(MarkActions.kindOf(null))
    }

    @Test fun anActionWritesAMarkAtTheElapsedTime() {
        val m = MarkActions.handle(MarkActions.ACTION_ACTION, 754_000)!!
        assertEquals(Mark(MarkKind.ACTION, 754_000), m)
        assertEquals(listOf(m), RecordingMarks.marks.value)
        assertNull(MarkActions.handle("something.else", 1000))
        assertEquals(1, RecordingMarks.marks.value.size)
    }

    @Test fun theServiceTurnsANotificationIntentIntoAMark() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val service = Robolectric.setupService(MeetingRecordingService::class.java)
        service.onStartCommand(Intent(ctx, MeetingRecordingService::class.java).setAction(MarkActions.actionOf(MarkKind.QUESTION)), 0, 1)
        service.onStartCommand(Intent(ctx, MeetingRecordingService::class.java).setAction(MarkActions.actionOf(MarkKind.KEY)), 0, 2)
        assertEquals(listOf(MarkKind.QUESTION, MarkKind.KEY), RecordingMarks.marks.value.map { it.kind })
    }

    @Test fun theMarksAreKeptOnTheRecordingsNoteWhenItIsSaved() {
        val f = PulseFixture()
        try {
            MarkActions.handle(MarkActions.KEY_ACTION, 61_000)
            MarkActions.handle(MarkActions.QUESTION_ACTION, 120_000)
            runBlocking { Marks.save(f.db, "m1", RecordingMarks.take()) }
            val note = runBlocking { f.db.noteDao().getById("n1")!! }
            assertEquals(listOf(Mark(MarkKind.KEY, 61_000), Mark(MarkKind.QUESTION, 120_000)), Marks.read(note.metadataJson))
            assertEquals(emptyList<Mark>(), RecordingMarks.marks.value) // taken: the next recording starts clean
        } finally { f.db.close() }
    }
}
