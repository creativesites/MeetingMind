package com.example.core.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.database.TranscriptSegmentEntity
import kotlinx.coroutines.flow.first
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

/**
 * The transcript is user-owned data, not immutable AI output: a hand-corrected segment must
 * persist to Room, be marked [com.example.core.database.TranscriptSegmentEntity.isUserEdited],
 * and — since every other consumer (Ask AI, export, search) reads the same table via
 * [TranscriptRepository.getTranscriptDirect] / [TranscriptRepository.getTranscript] — be visible
 * to them immediately with no separate "edited copy" to keep in sync.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranscriptRepositoryEditTest {

    private lateinit var database: MeetMindDatabase
    private lateinit var repository: TranscriptRepository

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TranscriptRepository(database)
        runBlocking {
            database.meetingDao().insertMeeting(
                MeetingEntity(
                    id = "m1",
                    title = "Test Meeting",
                    createdAt = System.currentTimeMillis(),
                    durationMs = 0L,
                    source = "LOCAL_RECORDING",
                    audioFilePath = null,
                    status = "READY",
                    participantCount = 1,
                    language = "en",
                    summaryText = null
                )
            )
            database.transcriptDao().insertSegments(
                listOf(
                    TranscriptSegmentEntity(
                        id = "s1",
                        meetingId = "m1",
                        speakerId = "spk_m1_0",
                        speakerName = "Speaker 1",
                        startMs = 0L,
                        endMs = 2000L,
                        text = "The deadline is Thursday.",
                        confidence = 0.8f
                    ),
                    TranscriptSegmentEntity(
                        id = "s2",
                        meetingId = "m1",
                        speakerId = "spk_m1_1",
                        speakerName = "Speaker 2",
                        startMs = 2000L,
                        endMs = 4000L,
                        text = "Sounds good to me.",
                        confidence = 0.9f
                    )
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `editing a segment's text persists and marks it user-edited`() = runBlocking {
        repository.updateSegmentText("s1", "The deadline is Friday.")

        val transcript = repository.getTranscriptDirect("m1")
        val edited = transcript.segments.first { it.id == "s1" }
        assertEquals("The deadline is Friday.", edited.text)
        assertTrue(edited.isUserEdited)
    }

    @Test
    fun `editing one segment leaves other segments and their metadata untouched`() = runBlocking {
        repository.updateSegmentText("s1", "The deadline is Friday.")

        val transcript = repository.getTranscriptDirect("m1")
        val untouched = transcript.segments.first { it.id == "s2" }
        assertEquals("Sounds good to me.", untouched.text)
        assertFalse(untouched.isUserEdited)
        // Speaker/timestamp metadata on the edited segment itself must also be untouched.
        val edited = transcript.segments.first { it.id == "s1" }
        assertEquals("spk_m1_0", edited.speakerId)
        assertEquals("Speaker 1", edited.speakerName)
        assertEquals(0L, edited.startMs)
        assertEquals(2000L, edited.endMs)
    }

    @Test
    fun `a fresh unedited transcript has isUserEdited false on every segment`() = runBlocking {
        val transcript = repository.getTranscriptDirect("m1")
        assertTrue(transcript.segments.all { !it.isUserEdited })
    }

    @Test
    fun `edits are visible through the Flow-based getTranscript, not just the direct read`() = runBlocking {
        repository.updateSegmentText("s2", "Sounds good — let's confirm by email.")

        val transcript = repository.getTranscript("m1").first()
        val edited = transcript.segments.first { it.id == "s2" }
        assertEquals("Sounds good — let's confirm by email.", edited.text)
        assertTrue(edited.isUserEdited)
    }
}
