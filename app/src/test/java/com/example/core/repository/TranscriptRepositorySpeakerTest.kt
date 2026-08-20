package com.example.core.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.database.SpeakerEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
}
