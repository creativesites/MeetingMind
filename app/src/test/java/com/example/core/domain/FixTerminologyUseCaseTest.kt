package com.example.core.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.database.TranscriptSegmentEntity
import com.example.core.model.VocabularySource
import com.example.core.repository.TranscriptRepository
import com.example.core.repository.VocabularyRepository
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
 * [FixTerminologyUseCase] is the "Fix terminology" AI tool (Phase 15 §6) — deliberately not a new
 * LLM prompt contract, just [VocabularyRepository]'s learned corrections applied via the same
 * exact-match replace Replace All itself uses. These tests pin down that it only ever changes what
 * a learned correction actually says to change, never anything else in the transcript.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FixTerminologyUseCaseTest {

    private lateinit var database: MeetMindDatabase
    private lateinit var transcriptRepository: TranscriptRepository
    private lateinit var vocabularyRepository: VocabularyRepository
    private lateinit var useCase: FixTerminologyUseCase

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transcriptRepository = TranscriptRepository(database)
        vocabularyRepository = VocabularyRepository(database)
        useCase = FixTerminologyUseCase(transcriptRepository, vocabularyRepository)
        runBlocking {
            database.meetingDao().insertMeeting(
                MeetingEntity(
                    id = "m1", title = "Test Meeting", createdAt = System.currentTimeMillis(), durationMs = 0L,
                    source = "LOCAL_RECORDING", audioFilePath = null, status = "READY", participantCount = 1, language = "en", summaryText = null
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun segment(id: String, text: String, startMs: Long) = TranscriptSegmentEntity(
        id = id, meetingId = "m1", speakerId = "spk_0", speakerName = "You", startMs = startMs, endMs = startMs + 1000L, text = text, confidence = 0.9f
    )

    @Test
    fun `applies a learned correction wherever it appears in the transcript`() = runBlocking {
        vocabularyRepository.recordCorrection("Sherpa Onix", "Sherpa-ONNX", VocabularySource.REPLACE_ALL)
        database.transcriptDao().insertSegments(
            listOf(
                segment("s1", "We're using Sherpa Onix for speech recognition.", 0L),
                segment("s2", "Sherpa Onix handles diarization too.", 1000L),
                segment("s3", "Totally unrelated sentence.", 2000L)
            )
        )

        val changes = useCase("m1")

        assertEquals(2, changes.size)
        val transcript = transcriptRepository.getTranscriptDirect("m1")
        assertTrue(transcript.segments.first { it.id == "s1" }.text.contains("Sherpa-ONNX"))
        assertTrue(transcript.segments.first { it.id == "s2" }.text.contains("Sherpa-ONNX"))
        assertEquals("Totally unrelated sentence.", transcript.segments.first { it.id == "s3" }.text)
    }

    @Test
    fun `with no learned vocabulary, makes no changes at all`() = runBlocking {
        database.transcriptDao().insertSegments(listOf(segment("s1", "Nothing to correct here.", 0L)))

        val changes = useCase("m1")

        assertTrue(changes.isEmpty())
        assertEquals("Nothing to correct here.", transcriptRepository.getTranscriptDirect("m1").segments[0].text)
    }

    @Test
    fun `a learned term that does not appear in this transcript changes nothing`() = runBlocking {
        vocabularyRepository.recordCorrection("Kubernetes", "Kubernetes (K8s)", VocabularySource.REPLACE_ALL)
        database.transcriptDao().insertSegments(listOf(segment("s1", "We talked about the budget.", 0L)))

        val changes = useCase("m1")

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `an empty transcript is a safe no-op`() = runBlocking {
        vocabularyRepository.recordCorrection("Sherpa Onix", "Sherpa-ONNX", VocabularySource.REPLACE_ALL)

        val changes = useCase("m1")

        assertTrue(changes.isEmpty())
    }
}
