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
    val speakerId: String = "speaker_1",
    val speakerName: String = "Speaker 1",
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float = 0.95f
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

data class AiModelInfo(
    val id: String,
    val name: String,
    val capability: Set<ModelCapability>,
    val sizeBytes: Long,
    val minimumRamMb: Int,
    val recommendedRamMb: Int,
    /** Null until a specific production model has been selected and hosted. */
    val downloadUrl: String? = null,
    /** Expected SHA-256 of the downloaded model file, verified before it is activated. Null until a real model is pinned. */
    val sha256: String? = null,
    val version: String,
    val isInstalled: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val description: String = "",
    val parameterCount: String = "",
    val quantization: String = "q4_0"
) {
    /** A model can only actually be downloaded once it has both a URL and an expected checksum. */
    val isDownloadable: Boolean get() = !downloadUrl.isNullOrBlank() && !sha256.isNullOrBlank()
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
