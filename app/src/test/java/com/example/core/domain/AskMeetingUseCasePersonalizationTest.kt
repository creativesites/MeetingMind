package com.example.core.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.common.AiResult
import com.example.ai.llm.MeetingIntelligenceEngine
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.database.TranscriptSegmentEntity
import com.example.core.model.AskPersonalizationContext
import com.example.core.model.ChatMessage
import com.example.core.model.RecordingType
import com.example.core.model.Transcript
import com.example.core.model.TranscriptSegment
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
import java.util.UUID

/**
 * [AskMeetingUseCase] is where Ask AI's personalization (Phase 15 §8) actually gets assembled —
 * these tests pin down that [AskPersonalizationContext] carries the right, narrow data through to
 * [MeetingIntelligenceEngine.askMeeting]: the caller-supplied name, and only vocabulary entries
 * [VocabularyRepository.findRelevantTerms] judged relevant to the actual question asked, never the
 * whole learned-vocabulary table.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AskMeetingUseCasePersonalizationTest {

    private lateinit var database: MeetMindDatabase
    private lateinit var transcriptRepository: TranscriptRepository
    private lateinit var vocabularyRepository: VocabularyRepository
    private var capturedPersonalization: AskPersonalizationContext? = null

    private val capturingEngine = object : MeetingIntelligenceEngine {
        override suspend fun processMeeting(
            transcript: Transcript,
            meetingTitle: String,
            recordingType: RecordingType,
            customContext: String?
        ) = throw NotImplementedError("not exercised by this test")

        override suspend fun askMeeting(
            question: String,
            transcript: Transcript,
            relevantSegments: List<TranscriptSegment>,
            personalization: AskPersonalizationContext
        ): AiResult<ChatMessage> {
            capturedPersonalization = personalization
            return AiResult.Success(ChatMessage(id = UUID.randomUUID().toString(), meetingId = transcript.meetingId, isUser = false, content = "answer"))
        }
    }

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transcriptRepository = TranscriptRepository(database)
        vocabularyRepository = VocabularyRepository(database)
        runBlocking {
            database.meetingDao().insertMeeting(
                MeetingEntity(
                    id = "m1", title = "Test Meeting", createdAt = System.currentTimeMillis(), durationMs = 0L,
                    source = "LOCAL_RECORDING", audioFilePath = null, status = "READY", participantCount = 1, language = "en", summaryText = null
                )
            )
            database.transcriptDao().insertSegments(
                listOf(
                    TranscriptSegmentEntity(
                        id = "s1", meetingId = "m1", speakerId = "spk_0", speakerName = "You",
                        startMs = 0L, endMs = 1000L, text = "We use Sherpa Onix for ASR.", confidence = 0.9f
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
    fun `passes the caller-supplied user name through to the engine`() = runBlocking {
        val useCase = AskMeetingUseCase(transcriptRepository, capturingEngine, vocabularyRepository = vocabularyRepository)

        useCase("m1", "What ASR engine do we use?", userName = "Winston")

        assertEquals("Winston", capturedPersonalization?.userName)
    }

    @Test
    fun `a null user name is passed through as null, never guessed`() = runBlocking {
        val useCase = AskMeetingUseCase(transcriptRepository, capturingEngine, vocabularyRepository = vocabularyRepository)

        useCase("m1", "What ASR engine do we use?")

        assertEquals(null, capturedPersonalization?.userName)
    }

    @Test
    fun `only vocabulary relevant to this question is included, not the whole table`() = runBlocking {
        vocabularyRepository.recordCorrection("Sherpa Onix", "Sherpa-ONNX", VocabularySource.REPLACE_ALL)
        vocabularyRepository.recordCorrection("Kubernetes", "Kubernetes (K8s)", VocabularySource.REPLACE_ALL)
        val useCase = AskMeetingUseCase(transcriptRepository, capturingEngine, vocabularyRepository = vocabularyRepository)

        useCase("m1", "What did we say about Sherpa Onix?")

        val vocab = capturedPersonalization?.relevantVocabulary.orEmpty()
        assertEquals(1, vocab.size)
        assertEquals("Sherpa-ONNX", vocab[0].canonicalForm)
    }

    @Test
    fun `with no vocabularyRepository wired, personalization has empty vocabulary rather than crashing`() = runBlocking {
        val useCase = AskMeetingUseCase(transcriptRepository, capturingEngine, vocabularyRepository = null)

        useCase("m1", "What ASR engine do we use?", userName = "Winston")

        assertEquals("Winston", capturedPersonalization?.userName)
        assertTrue(capturedPersonalization?.relevantVocabulary.orEmpty().isEmpty())
    }
}
