package com.example.ai.pipeline

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.asr.SpeechRecognizer
import com.example.ai.asr.TranscriptionOptions
import com.example.ai.common.AiResult
import com.example.ai.diarization.SpeakerDiarizer
import com.example.ai.embeddings.LocalEmbeddingEngine
import com.example.ai.llm.MeetingIntelligenceEngine
import com.example.ai.modelmanagement.LocalModelStorage
import com.example.ai.vad.SpeechInterval
import com.example.ai.vad.VoiceActivityDetector
import com.example.core.database.MeetMindDatabase
import com.example.core.database.MeetingEntity
import com.example.core.model.ActionItem
import com.example.core.model.ChatMessage
import com.example.core.model.Decision
import com.example.core.model.DecisionType
import com.example.core.model.MeetingStatus
import com.example.core.model.MeetingSummary
import com.example.core.model.ProcessingStage
import com.example.core.model.Question
import com.example.core.model.Speaker
import com.example.core.model.Transcript
import com.example.core.model.TranscriptSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Exercises the full [MeetingProcessingPipeline] orchestration logic — stage sequencing, Room
 * persistence, cancellation handling — using fake VAD/ASR/diarizer/intelligence implementations
 * instead of real sherpa-onnx/MediaPipe ones. Every stage of this pipeline is defined behind an
 * interface specifically so this is possible: none of what's tested here touches a native
 * library, so it genuinely runs on the JVM. Real model *accuracy* is a separate, device-only
 * concern — see docs/AI_ARCHITECTURE.md "Known Limitations".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeetingProcessingPipelineIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: MeetMindDatabase
    private lateinit var audioFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MeetMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        audioFile = File(context.cacheDir, "fake_recording.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    @After
    fun tearDown() {
        database.close()
        audioFile.delete()
    }

    private suspend fun insertRecordingMeeting(meetingId: String) {
        database.meetingDao().insertMeeting(
            MeetingEntity(
                id = meetingId,
                title = "Untitled Meeting",
                createdAt = System.currentTimeMillis(),
                durationMs = 0L,
                source = "LOCAL_RECORDING",
                audioFilePath = audioFile.absolutePath,
                status = MeetingStatus.RECORDING.name,
                participantCount = 1,
                language = "en",
                summaryText = null
            )
        )
    }

    private val fakeVad = object : VoiceActivityDetector {
        override suspend fun detectSpeechIntervals(audioFile: File, totalDurationMs: Long) =
            AiResult.Success(listOf(SpeechInterval(startMs = 0L, endMs = totalDurationMs)))
    }

    private val fakeAsr = object : SpeechRecognizer {
        override suspend fun transcribe(
            audioFile: File,
            totalDurationMs: Long,
            meetingId: String,
            speechIntervals: List<SpeechInterval>,
            options: TranscriptionOptions,
            onProgress: (progress: Float, statusText: String) -> Unit
        ): AiResult<List<TranscriptSegment>> {
            onProgress(1f, "done")
            return AiResult.Success(
                listOf(
                    TranscriptSegment(id = "seg1", meetingId = meetingId, startMs = 0L, endMs = 2000L, text = "We will ship on Friday."),
                    TranscriptSegment(id = "seg2", meetingId = meetingId, startMs = 2000L, endMs = 4000L, text = "Sounds good to me.")
                )
            )
        }
    }

    private val fakeDiarizer = object : SpeakerDiarizer {
        override suspend fun diarize(
            audioFile: File,
            totalDurationMs: Long,
            segments: List<TranscriptSegment>,
            knownSpeakers: List<Speaker>,
            expectedSpeakerCount: Int?
        ): AiResult<List<TranscriptSegment>> = AiResult.Success(
            segments.mapIndexed { index, seg ->
                seg.copy(speakerId = "spk_${seg.meetingId}_$index", speakerName = "Speaker ${index + 1}")
            }
        )
    }

    private val fakeIntelligenceEngine = object : MeetingIntelligenceEngine {
        override suspend fun processMeeting(
            transcript: Transcript,
            meetingTitle: String,
            recordingType: com.example.core.model.RecordingType,
            customContext: String?
        ) = AiResult.Success(
            // Title generation is folded into this one structured-JSON call — this fake mirrors
            // that by returning a real, specific title in the same MeetingSummary rather than
            // via a separate generateTitle() call.
            MeetingSummary(
                title = "Friday Ship Decision",
                summary = "The team confirmed the Friday ship date.",
                topics = listOf("Ship date"),
                decisions = listOf(
                    Decision(id = UUID.randomUUID().toString(), meetingId = transcript.meetingId, text = "Ship on Friday", type = DecisionType.DECISION, sourceSegmentIds = listOf("seg1"))
                ),
                actionItems = listOf(
                    ActionItem(id = UUID.randomUUID().toString(), meetingId = transcript.meetingId, task = "Notify customers", assigneeName = "Speaker 1", sourceSegmentIds = listOf("seg1"))
                ),
                questions = emptyList(),
                followUps = emptyList()
            )
        )

        override suspend fun askMeeting(question: String, transcript: Transcript, relevantSegments: List<TranscriptSegment>) =
            AiResult.Success(ChatMessage(id = UUID.randomUUID().toString(), meetingId = transcript.meetingId, isUser = false, content = "n/a"))
    }

    private fun buildPipeline(
        vad: VoiceActivityDetector = fakeVad,
        asr: SpeechRecognizer = fakeAsr
    ) = MeetingProcessingPipeline(
        context = context,
        database = database,
        modelStorage = LocalModelStorage(context),
        vad = vad,
        speechRecognizer = asr,
        diarizer = fakeDiarizer,
        intelligenceEngine = fakeIntelligenceEngine,
        embeddingEngine = LocalEmbeddingEngine()
    )

    @Test
    fun `full pipeline with fakes progresses through every real stage in order`() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        insertRecordingMeeting(meetingId)
        val pipeline = buildPipeline()
        val observedStages = mutableListOf<ProcessingStage>()

        pipeline.processMeeting(meetingId, audioFile, 4000L) { _, _, stage -> observedStages.add(stage) }

        val expectedOrder = listOf(
            ProcessingStage.PREPARING_AUDIO,
            ProcessingStage.DETECTING_SPEECH,
            ProcessingStage.TRANSCRIBING,
            ProcessingStage.DIARIZING,
            ProcessingStage.CLEANING_TRANSCRIPT,
            ProcessingStage.ANALYZING,
            ProcessingStage.SAVING_RESULTS,
            ProcessingStage.COMPLETED
        )
        assertEquals(expectedOrder, observedStages.distinct())
    }

    @Test
    fun `full pipeline with fakes persists real transcript, speakers, decisions, and action items`() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        insertRecordingMeeting(meetingId)
        val pipeline = buildPipeline()

        val result = pipeline.processMeeting(meetingId, audioFile, 4000L) { _, _, _ -> }

        assertEquals(MeetingStatus.READY.name, result.status)
        assertEquals("Friday Ship Decision", result.title)

        val segments = database.transcriptDao().getSegmentsForMeetingDirect(meetingId)
        assertEquals(2, segments.size)
        assertEquals("spk_${meetingId}_0", segments[0].speakerId)

        val speakers = database.speakerDao().getSpeakersForMeetingDirect(meetingId)
        assertEquals(2, speakers.size)

        val decisions = database.decisionDao().getDecisionsForMeetingDirect(meetingId)
        assertEquals(1, decisions.size)
        assertEquals("DECISION", decisions[0].type)
        assertEquals("[\"seg1\"]", decisions[0].sourceSegmentIdsJson)

        val actionItems = database.actionItemDao().getActionItemsForMeetingDirect(meetingId)
        assertEquals(1, actionItems.size)
        assertEquals("Speaker 1", actionItems[0].assigneeName)
    }

    @Test
    fun `full pipeline runs the cleanup stage and persists a validated cleanedText per segment`() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        insertRecordingMeeting(meetingId)
        val pipeline = buildPipeline()

        pipeline.processMeeting(meetingId, audioFile, 4000L) { _, _, _ -> }

        val segments = database.transcriptDao().getSegmentsForMeetingDirect(meetingId)
        // Neither fake ASR segment contains filler words, so the rule-based cleanup engine's
        // candidate is identical to the raw text — it must still be validated and persisted as
        // cleanedText, not left null, proving the stage actually ran rather than being skipped.
        assertEquals("We will ship on Friday.", segments.first { it.id == "seg1" }.cleanedText)
        assertEquals("Sounds good to me.", segments.first { it.id == "seg2" }.cleanedText)
    }

    @Test
    fun `re-running the pipeline for the same meeting replaces prior results instead of duplicating them`() = runBlocking {
        // Simulates WorkManager restarting MeetingProcessingWorker from scratch after the app
        // process was killed mid-run: every entity this pipeline writes uses a freshly-generated
        // id, so without an idempotency guard a second full run would double the transcript,
        // decisions, and action items instead of replacing the first (killed) run's data.
        val meetingId = UUID.randomUUID().toString()
        insertRecordingMeeting(meetingId)
        val pipeline = buildPipeline()

        pipeline.processMeeting(meetingId, audioFile, 4000L) { _, _, _ -> }
        pipeline.processMeeting(meetingId, audioFile, 4000L) { _, _, _ -> }

        val segments = database.transcriptDao().getSegmentsForMeetingDirect(meetingId)
        assertEquals(2, segments.size)

        val speakers = database.speakerDao().getSpeakersForMeetingDirect(meetingId)
        assertEquals(2, speakers.size)

        val decisions = database.decisionDao().getDecisionsForMeetingDirect(meetingId)
        assertEquals(1, decisions.size)

        val actionItems = database.actionItemDao().getActionItemsForMeetingDirect(meetingId)
        assertEquals(1, actionItems.size)

        val embeddings = database.embeddingDao().getEmbeddingsForMeeting(meetingId)
        // 2 transcript-segment embeddings + 1 summary embedding, not 6.
        assertEquals(3, embeddings.size)
    }

    @Test
    fun `cancellation stops the pipeline honestly and marks the meeting as errored, not fabricated`() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        insertRecordingMeeting(meetingId)
        val reachedVadStage = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slowVad = object : VoiceActivityDetector {
            override suspend fun detectSpeechIntervals(audioFile: File, totalDurationMs: Long): AiResult<List<SpeechInterval>> {
                reachedVadStage.complete(Unit)
                delay(30_000)
                return AiResult.Success(emptyList())
            }
        }
        val pipeline = buildPipeline(vad = slowVad)

        var caughtCancellation = false
        val job: Job = launch(Dispatchers.Default) {
            try {
                pipeline.processMeeting(meetingId, audioFile, 4000L) { _, _, _ -> }
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            }
        }
        // Wait until the pipeline has genuinely entered the (fake) VAD stage — not just been
        // scheduled — before cancelling, so this test exercises real mid-flight cancellation
        // rather than racing the coroutine's own startup.
        kotlinx.coroutines.withTimeout(5000) { reachedVadStage.await() }
        job.cancelAndJoin()

        assertTrue("Expected the pipeline to propagate CancellationException", caughtCancellation)
        val stored = database.meetingDao().getMeetingById(meetingId)
        assertEquals(MeetingStatus.ERROR.name, stored?.status)
        val segments = database.transcriptDao().getSegmentsForMeetingDirect(meetingId)
        assertTrue("A cancelled run must never persist a transcript", segments.isEmpty())
    }

    @Test
    fun `falls back to a deterministic, non-fabricated title when meeting intelligence is unavailable`() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        insertRecordingMeeting(meetingId)
        val unavailableIntelligence = object : MeetingIntelligenceEngine {
            override suspend fun processMeeting(
                transcript: Transcript,
                meetingTitle: String,
                recordingType: com.example.core.model.RecordingType,
                customContext: String?
            ) = AiResult.ModelUnavailable(modelId = "local-llm", message = "No local meeting intelligence model is installed.")

            override suspend fun askMeeting(question: String, transcript: Transcript, relevantSegments: List<TranscriptSegment>) =
                AiResult.ModelUnavailable(modelId = "local-llm", message = "No local meeting intelligence model is installed.")
        }
        val pipeline = MeetingProcessingPipeline(
            context = context,
            database = database,
            modelStorage = LocalModelStorage(context),
            vad = fakeVad,
            speechRecognizer = fakeAsr,
            diarizer = fakeDiarizer,
            intelligenceEngine = unavailableIntelligence,
            embeddingEngine = LocalEmbeddingEngine()
        )

        val result = pipeline.processMeeting(meetingId, audioFile, 4000L) { _, _, _ -> }

        // "General" is insertRecordingMeeting's default recordingType — never a guessed name.
        val expectedFallback = com.example.core.common.MeetingTitleGenerator.deterministicFallbackTitle(
            com.example.core.model.RecordingType.GENERAL,
            result.createdAt
        )
        assertEquals(expectedFallback, result.title)
        assertEquals(MeetingStatus.READY.name, result.status)
    }

    @Test
    fun `falls back to a deterministic title when the model's own title candidate is generic and unhelpful`() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        insertRecordingMeeting(meetingId)
        val genericTitleIntelligence = object : MeetingIntelligenceEngine {
            override suspend fun processMeeting(
                transcript: Transcript,
                meetingTitle: String,
                recordingType: com.example.core.model.RecordingType,
                customContext: String?
            ) = AiResult.Success(
                MeetingSummary(
                    title = "Meeting Summary",
                    summary = "",
                    topics = emptyList(),
                    decisions = emptyList(),
                    actionItems = emptyList(),
                    questions = emptyList()
                )
            )

            override suspend fun askMeeting(question: String, transcript: Transcript, relevantSegments: List<TranscriptSegment>) =
                AiResult.Success(ChatMessage(id = UUID.randomUUID().toString(), meetingId = transcript.meetingId, isUser = false, content = "n/a"))
        }
        val pipeline = MeetingProcessingPipeline(
            context = context,
            database = database,
            modelStorage = LocalModelStorage(context),
            vad = fakeVad,
            speechRecognizer = fakeAsr,
            diarizer = fakeDiarizer,
            intelligenceEngine = genericTitleIntelligence,
            embeddingEngine = LocalEmbeddingEngine()
        )

        val result = pipeline.processMeeting(meetingId, audioFile, 4000L) { _, _, _ -> }

        val expectedFallback = com.example.core.common.MeetingTitleGenerator.deterministicFallbackTitle(
            com.example.core.model.RecordingType.GENERAL,
            result.createdAt
        )
        assertEquals(expectedFallback, result.title)
    }

    // --- Single-speaker fast path: a confirmed solo recording must never run diarization ---

    @Test
    fun `a confirmed single speaker skips diarization entirely - the model is never even invoked`() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        insertRecordingMeeting(meetingId)
        var diarizerWasInvoked = false
        val explodingDiarizer = object : SpeakerDiarizer {
            override suspend fun diarize(
                audioFile: File,
                totalDurationMs: Long,
                segments: List<TranscriptSegment>,
                knownSpeakers: List<Speaker>,
                expectedSpeakerCount: Int?
            ): AiResult<List<TranscriptSegment>> {
                diarizerWasInvoked = true
                error("Diarization must never run for a confirmed single speaker")
            }
        }
        val pipeline = MeetingProcessingPipeline(
            context = context,
            database = database,
            modelStorage = LocalModelStorage(context),
            vad = fakeVad,
            speechRecognizer = fakeAsr,
            diarizer = explodingDiarizer,
            intelligenceEngine = fakeIntelligenceEngine,
            embeddingEngine = LocalEmbeddingEngine()
        )

        val result = pipeline.processMeeting(meetingId, audioFile, 4000L, expectedSpeakerCount = 1) { _, _, _ -> }

        assertTrue("Pipeline must complete without ever invoking the diarizer", !diarizerWasInvoked)
        assertEquals(MeetingStatus.READY.name, result.status)
        val segments = database.transcriptDao().getSegmentsForMeetingDirect(meetingId)
        // The two ASR fragments share the same (now real, assigned) speakerId and sit
        // back-to-back, so TranscriptStructureEngine correctly merges them into one paragraph.
        assertEquals(1, segments.size)
        // Phase 15 §2: a confirmed single speaker still gets a real, persisted speaker identity —
        // leaving speakerId null (the old behavior) meant the SpeakerEntity insertion step
        // silently dropped the segment, so the UI fell back to "Unlabeled speaker". "You" because
        // this meeting's source is LOCAL_RECORDING (the user's own recording).
        assertEquals("speaker_0", segments[0].speakerId)
        assertEquals("You", segments[0].speakerName)
        assertTrue(segments[0].text.contains("ship on Friday"))
        assertTrue(segments[0].text.contains("Sounds good"))

        val speakers = database.speakerDao().getSpeakersForMeetingDirect(meetingId)
        assertEquals(1, speakers.size)
        assertEquals("You", speakers[0].customName)
        assertEquals("#6366F1", speakers[0].colorHex)
    }

    @Test
    fun `a confirmed single speaker on an imported recording is named Speaker 1, not You`() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        database.meetingDao().insertMeeting(
            MeetingEntity(
                id = meetingId,
                title = "Untitled Meeting",
                createdAt = System.currentTimeMillis(),
                durationMs = 0L,
                source = "IMPORTED_AUDIO",
                audioFilePath = audioFile.absolutePath,
                status = MeetingStatus.RECORDING.name,
                participantCount = 1,
                language = "en",
                summaryText = null
            )
        )
        val pipeline = MeetingProcessingPipeline(
            context = context,
            database = database,
            modelStorage = LocalModelStorage(context),
            vad = fakeVad,
            speechRecognizer = fakeAsr,
            diarizer = object : SpeakerDiarizer {
                override suspend fun diarize(
                    audioFile: File,
                    totalDurationMs: Long,
                    segments: List<TranscriptSegment>,
                    knownSpeakers: List<Speaker>,
                    expectedSpeakerCount: Int?
                ): AiResult<List<TranscriptSegment>> = error("must not run")
            },
            intelligenceEngine = fakeIntelligenceEngine,
            embeddingEngine = LocalEmbeddingEngine()
        )

        pipeline.processMeeting(meetingId, audioFile, 4000L, expectedSpeakerCount = 1) { _, _, _ -> }

        val segments = database.transcriptDao().getSegmentsForMeetingDirect(meetingId)
        // An imported file isn't necessarily the user's own voice, so the identity must not
        // assume "You" the way a LOCAL_RECORDING does.
        assertEquals("Speaker 1", segments[0].speakerName)
        val speakers = database.speakerDao().getSpeakersForMeetingDirect(meetingId)
        assertEquals("Speaker 1", speakers[0].customName)
    }

    @Test
    fun `an unspecified speaker count still runs diarization normally`() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        insertRecordingMeeting(meetingId)
        var diarizerWasInvoked = false
        val trackingDiarizer = object : SpeakerDiarizer {
            override suspend fun diarize(
                audioFile: File,
                totalDurationMs: Long,
                segments: List<TranscriptSegment>,
                knownSpeakers: List<Speaker>,
                expectedSpeakerCount: Int?
            ): AiResult<List<TranscriptSegment>> {
                diarizerWasInvoked = true
                return AiResult.Success(segments)
            }
        }
        val pipeline = MeetingProcessingPipeline(
            context = context,
            database = database,
            modelStorage = LocalModelStorage(context),
            vad = fakeVad,
            speechRecognizer = fakeAsr,
            diarizer = trackingDiarizer,
            intelligenceEngine = fakeIntelligenceEngine,
            embeddingEngine = LocalEmbeddingEngine()
        )

        pipeline.processMeeting(meetingId, audioFile, 4000L, expectedSpeakerCount = null) { _, _, _ -> }

        assertTrue("Diarization must still run when the speaker count is unspecified", diarizerWasInvoked)
    }

    @Test
    fun `a confirmed multi-person count still runs diarization normally`() = runBlocking {
        val meetingId = UUID.randomUUID().toString()
        insertRecordingMeeting(meetingId)
        var receivedExpectedCount: Int? = -999
        val trackingDiarizer = object : SpeakerDiarizer {
            override suspend fun diarize(
                audioFile: File,
                totalDurationMs: Long,
                segments: List<TranscriptSegment>,
                knownSpeakers: List<Speaker>,
                expectedSpeakerCount: Int?
            ): AiResult<List<TranscriptSegment>> {
                receivedExpectedCount = expectedSpeakerCount
                return AiResult.Success(segments)
            }
        }
        val pipeline = MeetingProcessingPipeline(
            context = context,
            database = database,
            modelStorage = LocalModelStorage(context),
            vad = fakeVad,
            speechRecognizer = fakeAsr,
            diarizer = trackingDiarizer,
            intelligenceEngine = fakeIntelligenceEngine,
            embeddingEngine = LocalEmbeddingEngine()
        )

        pipeline.processMeeting(meetingId, audioFile, 4000L, expectedSpeakerCount = 3) { _, _, _ -> }

        assertEquals(3, receivedExpectedCount)
    }
}
