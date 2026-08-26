package com.example.core.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.database.SpeakerEntity
import com.example.core.database.TranscriptSegmentEntity
import kotlinx.coroutines.flow.first
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
 * Renaming a speaker must only ever touch the user-facing display name — never the underlying
 * diarization identity ([SpeakerEntity.id], [SpeakerEntity.speakerIndex],
 * [SpeakerEntity.originalLabel]). If a rename silently reset those, a second processing pass or
 * a UI refresh could re-derive a different identity for the same real speaker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranscriptRepositorySpeakerTest {

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
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `renaming a speaker preserves speakerIndex, originalLabel and confidence`() = runBlocking {
        val meetingId = "m1"
        database.speakerDao().insertSpeakers(
            listOf(
                SpeakerEntity(
                    id = "spk_m1_0",
                    meetingId = meetingId,
                    speakerIndex = 0,
                    originalLabel = "Speaker 1",
                    customName = "Speaker 1",
                    colorHex = "#3B82F6",
                    confidence = null
                )
            )
        )

        repository.renameSpeaker(meetingId, "spk_m1_0", "Winston")

        val speakers = repository.getSpeakers(meetingId).first()
        assertEquals(1, speakers.size)
        assertEquals("Winston", speakers[0].customName)
        // Identity fields must be untouched by the rename.
        assertEquals("spk_m1_0", speakers[0].id)
        assertEquals(0, speakers[0].speakerIndex)
        assertEquals("Speaker 1", speakers[0].originalLabel)
    }

    @Test
    fun `renaming an unknown speaker id is a safe no-op`() = runBlocking {
        val meetingId = "m1"
        // No speakers seeded at all.
        repository.renameSpeaker(meetingId, "spk_m1_does_not_exist", "Winston")

        val speakers = repository.getSpeakers(meetingId).first()
        assertEquals(0, speakers.size)
    }

    // --- mergeSpeakers (Phase 15 §2): collapsing two diarization-split identities into one ---

    private fun segment(id: String, meetingId: String, speakerId: String?, speakerName: String?, startMs: Long) =
        TranscriptSegmentEntity(
            id = id,
            meetingId = meetingId,
            speakerId = speakerId,
            speakerName = speakerName,
            startMs = startMs,
            endMs = startMs + 1000L,
            text = "hello",
            confidence = 0.9f
        )

    @Test
    fun `merging moves every source segment to the target and removes the source speaker row`() = runBlocking {
        val meetingId = "m1"
        database.speakerDao().insertSpeakers(
            listOf(
                SpeakerEntity(id = "spk_a", meetingId = meetingId, speakerIndex = 0, originalLabel = "Speaker 1", customName = "Alex", colorHex = "#6366F1"),
                SpeakerEntity(id = "spk_b", meetingId = meetingId, speakerIndex = 1, originalLabel = "Speaker 2", customName = "Alex (2)", colorHex = "#A855F7")
            )
        )
        database.transcriptDao().insertSegments(
            listOf(
                segment("s1", meetingId, "spk_a", "Alex", 0L),
                segment("s2", meetingId, "spk_b", "Alex (2)", 1000L),
                segment("s3", meetingId, "spk_b", "Alex (2)", 2000L)
            )
        )

        repository.mergeSpeakers(meetingId, sourceSpeakerId = "spk_b", targetSpeakerId = "spk_a")

        val speakers = repository.getSpeakers(meetingId).first()
        assertEquals(1, speakers.size)
        assertEquals("spk_a", speakers[0].id)
        assertEquals("Alex", speakers[0].customName)

        val segments = database.transcriptDao().getSegmentsForMeetingDirect(meetingId).sortedBy { it.startMs }
        assertTrue(segments.all { it.speakerId == "spk_a" })
        assertTrue(segments.all { it.speakerName == "Alex" })
    }

    @Test
    fun `merging a speaker into itself is a safe no-op`() = runBlocking {
        val meetingId = "m1"
        database.speakerDao().insertSpeakers(
            listOf(SpeakerEntity(id = "spk_a", meetingId = meetingId, speakerIndex = 0, originalLabel = "Speaker 1", customName = "Alex", colorHex = "#6366F1"))
        )

        repository.mergeSpeakers(meetingId, sourceSpeakerId = "spk_a", targetSpeakerId = "spk_a")

        val speakers = repository.getSpeakers(meetingId).first()
        assertEquals(1, speakers.size)
    }

    @Test
    fun `merging into an unknown target speaker is a safe no-op`() = runBlocking {
        val meetingId = "m1"
        database.speakerDao().insertSpeakers(
            listOf(SpeakerEntity(id = "spk_a", meetingId = meetingId, speakerIndex = 0, originalLabel = "Speaker 1", customName = "Alex", colorHex = "#6366F1"))
        )
        database.transcriptDao().insertSegments(listOf(segment("s1", meetingId, "spk_a", "Alex", 0L)))

        repository.mergeSpeakers(meetingId, sourceSpeakerId = "spk_a", targetSpeakerId = "spk_does_not_exist")

        // Nothing moved and the source speaker is still there — a merge into a target that
        // doesn't resolve must never delete data.
        val speakers = repository.getSpeakers(meetingId).first()
        assertEquals(1, speakers.size)
        assertEquals("spk_a", speakers[0].id)
        val segments = database.transcriptDao().getSegmentsForMeetingDirect(meetingId)
        assertEquals("spk_a", segments[0].speakerId)
    }
}
