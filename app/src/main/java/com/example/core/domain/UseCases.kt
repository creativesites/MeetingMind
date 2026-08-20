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
        pipeline.processMeeting(meetingId, audioFile, totalDurationMs, modelId, expectedSpeakerCount, onProgress)
    }
}

class AskMeetingUseCase(
    private val transcriptRepository: TranscriptRepository,
    private val pipelineIntelligence: com.example.ai.llm.MeetingIntelligenceEngine =
        com.example.ai.llm.UnavailableMeetingIntelligenceEngine()
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

        // Process with local intelligence — never fabricate an answer. If no local LLM is
        // installed, or nothing has been transcribed yet, say so explicitly instead of
        // inventing a grounded-sounding response.
        val aiResponse = when (val result = pipelineIntelligence.askMeeting(question, transcript, emptyList())) {
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
