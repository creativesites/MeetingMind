package com.example.core.model

data class Meeting(
    val id: String,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val source: MeetingSource,
    val audioFilePath: String?,
    val status: MeetingStatus,
    val participantCount: Int = 1,
    val language: String = "en",
    val summaryPreview: String? = null,
    val actionItemsCount: Int = 0,
    val decisionsCount: Int = 0
)

data class TranscriptSegment(
    val id: String,
    val meetingId: String,
    // Null until real speaker diarization is implemented — never a fabricated "Speaker 1" label.
    val speakerId: String? = null,
    val speakerName: String? = null,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    // Null when the ASR engine doesn't provide a confidence score for a segment.
    val confidence: Float? = null
)

data class Transcript(
    val meetingId: String,
    val segments: List<TranscriptSegment>,
    val language: String = "en",
    val createdAt: Long = System.currentTimeMillis()
)

data class Speaker(
    val id: String,
    val meetingId: String,
    val originalLabel: String,
    val customName: String,
    val colorHex: String
)

data class ActionItem(
    val id: String,
    val meetingId: String,
    val task: String,
    val assignee: String,
    val deadline: String,
    val confidence: Float = 0.9f,
    val isCompleted: Boolean = false,
    val sourceTimestampMs: Long? = null
)

data class Decision(
    val id: String,
    val meetingId: String,
    val text: String,
    val confidence: Float = 0.92f,
    val timestampMs: Long? = null
)

data class Question(
    val id: String,
    val meetingId: String,
    val text: String,
    val resolved: Boolean = false,
    val answer: String? = null,
    val timestampMs: Long? = null
)

data class Topic(
    val id: String,
    val meetingId: String,
    val name: String,
    val relevance: Float = 1.0f
)

data class MeetingSummary(
    val title: String,
    val summary: String,
    val topics: List<String>,
    val decisions: List<Decision>,
    val actionItems: List<ActionItem>,
    val questions: List<Question>,
    val followUps: List<String> = emptyList()
)

data class ChatMessage(
    val id: String,
    val meetingId: String,
    val isUser: Boolean,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceTimestamps: List<Long> = emptyList(),
    val sourceQuotes: List<String> = emptyList()
)

/**
 * One downloadable component of a model (e.g. a Transducer ASR model is encoder + decoder +
 * joiner + tokens — four separate files, all required before the model can be considered
 * installed). [fileName] is the name it is stored under inside
 * `ModelStorage.getModelDirectory(modelId)`.
 */
data class ModelFileSpec(
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long
)

data class AiModelInfo(
    val id: String,
    val name: String,
    val capability: Set<ModelCapability>,
    /** One or more files that together make up this model. Empty only for not-yet-sourced candidates. */
    val files: List<ModelFileSpec> = emptyList(),
    val minimumRamMb: Int,
    val recommendedRamMb: Int,
    val version: String,
    val isInstalled: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val description: String = "",
    val parameterCount: String = "",
    val quantization: String = "q4_0"
) {
    val sizeBytes: Long get() = files.sumOf { it.sizeBytes }

    /** A model can only actually be downloaded once every one of its files has a real URL + checksum. */
    val isDownloadable: Boolean
        get() = files.isNotEmpty() && files.all { it.downloadUrl.isNotBlank() && it.sha256.isNotBlank() }
}

data class ProcessingJob(
    val id: String,
    val meetingId: String,
    val meetingTitle: String,
    val currentStep: String,
    val progressPercent: Int,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null,
    val startedAt: Long = System.currentTimeMillis()
)

data class DeviceCapabilities(
    val totalRamGb: Float,
    val availableRamGb: Float,
    val cpuArch: String,
    val isArm64: Boolean,
    val recommendedAsrModelId: String,
    val recommendedLlmModelId: String,
    val devicePerformanceTier: String
)
