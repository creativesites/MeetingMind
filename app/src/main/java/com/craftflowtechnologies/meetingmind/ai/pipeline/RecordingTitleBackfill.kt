package com.craftflowtechnologies.meetingmind.ai.pipeline

import android.content.Context
import android.util.Log
import com.craftflowtechnologies.meetingmind.ai.cloud.CloudAi
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiIntelligenceEngine
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.FallbackMeetingIntelligenceEngine
import com.craftflowtechnologies.meetingmind.ai.llm.MeetingIntelligenceEngine
import com.craftflowtechnologies.meetingmind.ai.llm.MediaPipeLanguageModel
import com.craftflowtechnologies.meetingmind.ai.llm.RealMeetingIntelligenceEngine
import com.craftflowtechnologies.meetingmind.ai.llm.RecordingTitles
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmModelResolver
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.MeetingStatus
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.TitleSource
import com.craftflowtechnologies.meetingmind.core.model.Transcript
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Retitles recordings made before a capable engine was available.
 *
 * A recording processed with no model installed still has real speech, so its title came from
 * [TitleSource.CONTENT] or the date-only [TitleSource.DATE] fallback. When an offline model is
 * later downloaded (or a managed cloud route is configured), those are the only rows worth
 * revisiting: a [TitleSource.USER] name or an imported file's own [TitleSource.IMPORT] name is
 * never touched.
 *
 * Runs on [Dispatchers.IO] from the existing app-startup hook, skips entirely when no engine is
 * available or a recording is still in progress, and is bounded to [MAX_PER_RUN] rows per launch.
 * It is idempotent: a title that is not usable, or is identical to the current one, writes nothing.
 */
object RecordingTitleBackfill {
    private const val TAG = "RecordingTitleBackfill"

    /** Deliberately small: a launch should not spend minutes of inference retitling a library. */
    private const val MAX_PER_RUN = 3

    suspend fun run(context: Context): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val database = MeetMindDatabase.getInstance(context)
            val meetingDao = database.meetingDao()
            // Never compete with a recording or an active pipeline for the one LLM allocation.
            if (meetingDao.getMeetingsWithStatus(MeetingStatus.PROCESSING.name).isNotEmpty() ||
                meetingDao.getMeetingsWithStatus(MeetingStatus.RECORDING.name).isNotEmpty()
            ) {
                return@runCatching
            }

            val modelStorage = LocalModelStorage(context)
            val installedModel = LlmModelResolver.resolveSmallestInstalled(modelStorage, ModelCapability.SUMMARIZATION)
            val cloud = CloudAi.transport(context)
            val cloudReady = runCatching { cloud.refreshConfigured() }.getOrDefault(false)
            if (installedModel == null && !cloudReady) return@runCatching

            val localEngine: MeetingIntelligenceEngine? = installedModel?.let { modelId ->
                RealMeetingIntelligenceEngine(
                    languageModel = MediaPipeLanguageModel(context, modelStorage, modelId = modelId),
                    contextLengthTokens = ModelCatalog.entries.find { it.id == modelId }?.contextLengthTokens
                        ?: DEFAULT_CONTEXT_TOKENS
                )
            }
            val engine: MeetingIntelligenceEngine = when {
                cloudReady && localEngine != null ->
                    FallbackMeetingIntelligenceEngine(primary = GeminiIntelligenceEngine(cloud), fallback = localEngine)
                cloudReady -> GeminiIntelligenceEngine(cloud)
                localEngine != null -> localEngine
                else -> return@runCatching
            }

            val candidates = meetingDao.getMeetingsForTitleBackfill(MAX_PER_RUN)
            for (meeting in candidates) {
                try {
                    val segments = database.transcriptDao().getSegmentsForMeetingDirect(meeting.id)
                    val texts = segments.map { it.cleanedText ?: it.text }.filter { it.isNotBlank() }
                    if (!RecordingTitles.hasUsableWords(texts)) continue
                    val recordingType = runCatching { RecordingType.valueOf(meeting.recordingType) }
                        .getOrDefault(RecordingType.GENERAL)
                    val transcript = Transcript(
                        meetingId = meeting.id,
                        segments = segments.map {
                            TranscriptSegment(
                                id = it.id,
                                meetingId = it.meetingId,
                                speakerId = it.speakerId,
                                speakerName = it.speakerName,
                                startMs = it.startMs,
                                endMs = it.endMs,
                                text = it.text,
                                confidence = it.confidence,
                                cleanedText = it.cleanedText
                            )
                        },
                        language = meeting.language,
                        createdAt = meeting.createdAt
                    )
                    val result = engine.suggestTitle(transcript, recordingType)
                    val title = (result as? AiResult.Success)?.value?.let(RecordingTitles::parseTitleCandidate)
                    if (title.isNullOrBlank() || title == meeting.title) continue
                    meetingDao.updateMeeting(
                        meeting.copy(
                            title = title,
                            titleSource = TitleSource.AI.name,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    runCatching { NoteRepository(context, database).syncFromRecording(meeting.id) }
                } catch (e: Exception) {
                    Log.w(TAG, "Title backfill skipped for ${meeting.id}: ${e.message}")
                }
            }
        }.onFailure { Log.w(TAG, "Title backfill will retry next launch: ${it.message}") }
            .also { runCatching { LlmEngineManager.release() } }
        Unit
    }

    private const val DEFAULT_CONTEXT_TOKENS = 4096
}
