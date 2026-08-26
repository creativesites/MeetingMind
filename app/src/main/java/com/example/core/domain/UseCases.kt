package com.example.core.domain

import android.net.Uri
import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.ai.pipeline.MeetingProcessingPipeline
import com.example.core.audio.AudioExtractor
import com.example.core.audio.AudioRecorder
import com.example.core.model.ActionItem
import com.example.core.model.ChatMessage
import com.example.core.model.Meeting
import com.example.core.model.MeetingSource
import com.example.core.repository.ActionItemRepository
import com.example.core.repository.MeetingRepository
import com.example.core.repository.ModelRepository
import com.example.core.repository.SearchResultItem
import com.example.core.repository.SearchRepository
import com.example.core.repository.TranscriptRepository
import java.io.File
import java.util.UUID

class StartRecordingUseCase(
    private val audioRecorder: AudioRecorder,
    private val meetingRepository: MeetingRepository
) {
    suspend operator fun invoke(
        meetingTitle: String = "In-Person Meeting",
        recordingType: com.example.core.model.RecordingType = com.example.core.model.RecordingType.GENERAL,
        customContext: String? = null
    ): Pair<Meeting, File> {
        val meetingId = UUID.randomUUID().toString()
        val audioFile = audioRecorder.startRecording(meetingId)
        val meeting = meetingRepository.createInitialMeeting(
            id = meetingId,
            title = meetingTitle,
            source = MeetingSource.LOCAL_RECORDING,
            audioFilePath = audioFile.absolutePath,
            recordingType = recordingType,
            customContext = customContext
        )
        return Pair(meeting, audioFile)
    }
}

class StopRecordingUseCase(
    private val audioRecorder: AudioRecorder
) {
    operator fun invoke(): Pair<File?, Long> {
        val durationMs = audioRecorder.durationMs.value
        val file = audioRecorder.stopRecording()
        return Pair(file, durationMs)
    }
}

class ImportRecordingUseCase(
    private val audioExtractor: AudioExtractor,
    private val meetingRepository: MeetingRepository
) {
    suspend operator fun invoke(uri: Uri): Pair<Meeting, File> {
        val meetingId = UUID.randomUUID().toString()
        val imported = audioExtractor.importAndExtract(uri, meetingId)
        val source = if (imported.isVideo) MeetingSource.IMPORTED_VIDEO else MeetingSource.IMPORTED_AUDIO
        val cleanTitle = imported.fileName.substringBeforeLast(".")

        val meeting = meetingRepository.createInitialMeeting(
            id = meetingId,
            title = cleanTitle,
            source = source,
            audioFilePath = imported.outputFile.absolutePath
        )
        return Pair(meeting, imported.outputFile)
    }
}

class TranscribeMeetingUseCase(
    private val pipeline: MeetingProcessingPipeline
) {
    suspend operator fun invoke(
        meetingId: String,
        audioFile: File,
        totalDurationMs: Long,
        modelId: String,
        expectedSpeakerCount: Int? = null,
        onProgress: (step: String, percent: Int, stage: com.example.core.model.ProcessingStage) -> Unit
    ) {
        pipeline.processMeeting(
            meetingId = meetingId,
            audioFile = audioFile,
            totalDurationMs = totalDurationMs,
            modelId = modelId,
            expectedSpeakerCount = expectedSpeakerCount,
            onProgress = onProgress
        )
    }
}

/**
 * Lets a user re-clean an already-transcribed meeting's transcript with a different
 * [com.example.core.model.TranscriptCleanupMode] — without re-recording, re-transcribing, or
 * re-diarizing. Delegates to [MeetingProcessingPipeline.cleanTranscript], the exact same code
 * path a fresh recording's own cleanup stage uses — this is not a second, special-cased pipeline,
 * just a different entry point into the one real implementation of "how cleanup runs."
 *
 * Only [com.example.core.model.TranscriptSegment.cleanedText] is ever touched here — the real ASR
 * text, speaker assignments, timestamps, and source-segment provenance are all read from what's
 * already persisted and never modified. [TranscriptRepository.updateCleanedText] already refuses
 * to touch a user-edited segment at the SQL level, so a hand-correction survives a re-clean
 * automatically, with no extra logic needed here.
 */
class ReprocessTranscriptCleanupUseCase(
    private val pipeline: MeetingProcessingPipeline,
    private val meetingRepository: MeetingRepository,
    private val transcriptRepository: TranscriptRepository
) {
    suspend operator fun invoke(
        meetingId: String,
        cleanupMode: com.example.core.model.TranscriptCleanupMode,
        onStatus: suspend (String) -> Unit = {}
    ) {
        val meeting = meetingRepository.getMeetingByIdDirect(meetingId) ?: return
        val transcript = transcriptRepository.getTranscriptDirect(meetingId)
        if (transcript.segments.isEmpty()) return

        val cleaned = pipeline.cleanTranscript(
            structuredSegments = transcript.segments,
            recordingType = meeting.recordingType,
            cleanupMode = cleanupMode,
            singleSpeakerMode = meeting.speakerCountPreference == 1,
            onStatus = onStatus
        )
        cleaned.forEach { seg -> transcriptRepository.updateCleanedText(seg.id, seg.cleanedText) }
    }
}

/**
 * Applies every learned correction (Phase 15 §4/§6) to one meeting's transcript in one pass —
 * the "Fix terminology" AI tool. Deliberately not a new LLM prompt contract: the underlying
 * operation is the same exact-match, case-insensitive replace [TranscriptRepository.replaceAllInTranscript]
 * already does for a manual Replace All, just driven by [VocabularyRepository]'s learned
 * surfaceForm -> canonicalForm mappings instead of one user-typed pair. [VocabularyRepository.findRelevantTerms]
 * narrows the vocabulary down to entries actually worth checking against this transcript (real
 * word-level fuzzy matching) before the exact-match replace runs — fuzzy matching only decides
 * what's worth checking, it can never itself turn into a wrong replacement, since the replace step
 * is always an exact substring match.
 */
class FixTerminologyUseCase(
    private val transcriptRepository: com.example.core.repository.TranscriptRepository,
    private val vocabularyRepository: com.example.core.repository.VocabularyRepository
) {
    suspend operator fun invoke(meetingId: String): List<com.example.core.repository.TranscriptRepository.ReplaceAllChange> {
        val transcript = transcriptRepository.getTranscriptDirect(meetingId)
        if (transcript.segments.isEmpty()) return emptyList()

        val fullText = transcript.segments.joinToString(" ") { it.text }
        val relevantTerms = vocabularyRepository.findRelevantTerms(fullText, limit = 50)

        val allChanges = mutableListOf<com.example.core.repository.TranscriptRepository.ReplaceAllChange>()
        for (term in relevantTerms) {
            allChanges += transcriptRepository.replaceAllInTranscript(meetingId, term.surfaceForm, term.canonicalForm)
        }
        return allChanges
    }
}

class AskMeetingUseCase(
    private val transcriptRepository: TranscriptRepository,
    private val pipelineIntelligence: com.example.ai.llm.MeetingIntelligenceEngine =
        com.example.ai.llm.UnavailableMeetingIntelligenceEngine(),
    private val embeddingEngine: com.example.ai.embeddings.EmbeddingEngine = com.example.ai.embeddings.LocalEmbeddingEngine(),
    private val retrievalTopK: Int = 12
) {
    suspend operator fun invoke(
        meetingId: String,
        question: String
    ): ChatMessage {
        val transcript = transcriptRepository.getTranscriptDirect(meetingId)
        // Record user message
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            meetingId = meetingId,
            isUser = true,
            content = question,
            timestamp = System.currentTimeMillis()
        )
        transcriptRepository.saveChatMessage(userMsg)

        // Real retrieval: cosine-similarity top-K over the question against every segment, so a
        // long recording's answer isn't silently limited to whatever fits the model's context
        // budget starting from the beginning of the transcript (the previously known gap — see
        // docs/AI_ARCHITECTURE.md "Ask Meeting limitation"). A short transcript that already fits
        // in [retrievalTopK] segments skips the ranking step entirely — nothing to gain from it.
        val relevantSegments = retrieveRelevantSegments(transcript.segments, question)

        // Process with local intelligence — never fabricate an answer. If no local LLM is
        // installed, or nothing has been transcribed yet, say so explicitly instead of
        // inventing a grounded-sounding response.
        val aiResponse = when (val result = pipelineIntelligence.askMeeting(question, transcript, relevantSegments)) {
            is AiResult.Success -> result.value
            else -> ChatMessage(
                id = UUID.randomUUID().toString(),
                meetingId = meetingId,
                isUser = false,
                content = result.describeFailure()
                    ?: "Ask Meeting is unavailable: no local meeting intelligence model is installed.",
                timestamp = System.currentTimeMillis()
            )
        }
        transcriptRepository.saveChatMessage(aiResponse)
        return aiResponse
    }

    private suspend fun retrieveRelevantSegments(
        segments: List<com.example.core.model.TranscriptSegment>,
        question: String
    ): List<com.example.core.model.TranscriptSegment> {
        if (segments.size <= retrievalTopK) return segments
        val queryVector = embeddingEngine.embed(question)
        return segments
            .map { seg -> seg to embeddingEngine.cosineSimilarity(queryVector, embeddingEngine.embed(seg.text)) }
            .sortedByDescending { it.second }
            .take(retrievalTopK)
            .map { it.first }
            .sortedBy { it.startMs }
    }
}

class SearchMeetingsUseCase(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(query: String): List<SearchResultItem> {
        return searchRepository.searchHybrid(query)
    }
}

class DeleteMeetingUseCase(
    private val meetingRepository: MeetingRepository
) {
    suspend operator fun invoke(meetingId: String) {
        meetingRepository.deleteMeeting(meetingId)
    }
}

class DownloadModelUseCase(
    private val modelRepository: ModelRepository
) {
    suspend operator fun invoke(modelId: String): AiResult<Unit> {
        return modelRepository.installModel(modelId)
    }
}
