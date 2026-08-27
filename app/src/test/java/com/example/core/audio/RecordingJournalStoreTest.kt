package com.example.core.audio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingJournalStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `read returns null when no journal has ever been written`() {
        assertNull(RecordingJournalStore(context).read())
    }

    @Test
    fun `write then read round-trips every field`() {
        val store = RecordingJournalStore(context)
        val entry = RecordingJournalEntry(
            meetingId = "m1",
            title = "Team Sync",
            recordingType = "MEETING",
            audioFilePath = "/data/meetings/m1/audio.m4a",
            lastKnownDurationMs = 123_456L,
            markerCount = 0,
            noteCount = 0,
            lastHeartbeatAtMs = 987_654_321L,
            state = RecordingState.RECORDING.name
        )

        store.write(entry)
        val readBack = store.read()

        assertEquals(entry, readBack)
    }

    @Test
    fun `clear removes the journal so a later read is null again`() {
        val store = RecordingJournalStore(context)
        store.write(
            RecordingJournalEntry(
                meetingId = "m2",
                title = "Standup",
                recordingType = "GENERAL",
                audioFilePath = "/data/meetings/m2/audio.m4a",
                lastKnownDurationMs = 0L,
                markerCount = 0,
                noteCount = 0,
                lastHeartbeatAtMs = 1L,
                state = RecordingState.PAUSED.name
            )
        )

        store.clear()

        assertNull(store.read())
    }

    @Test
    fun `a later write overwrites the previous entry rather than appending`() {
        val store = RecordingJournalStore(context)
        store.write(
            RecordingJournalEntry(
                meetingId = "m3", title = "First", recordingType = "GENERAL",
                audioFilePath = "/a.m4a", lastKnownDurationMs = 1000L,
                markerCount = 0, noteCount = 0, lastHeartbeatAtMs = 1L, state = RecordingState.RECORDING.name
            )
        )
        store.write(
            RecordingJournalEntry(
                meetingId = "m3", title = "First", recordingType = "GENERAL",
                audioFilePath = "/a.m4a", lastKnownDurationMs = 5000L,
                markerCount = 0, noteCount = 0, lastHeartbeatAtMs = 2L, state = RecordingState.PAUSED.name
            )
        )

        val readBack = store.read()
        assertEquals(5000L, readBack?.lastKnownDurationMs)
        assertEquals(RecordingState.PAUSED.name, readBack?.state)
    }
}
