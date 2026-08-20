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

/**
 * A diarized speaker within one meeting. [id] is the stable diarization identity (never shown to
 * the user, never overwritten by a rename); [customName] is what the user sees and can rename —
 * keeping these separate means renaming "Speaker 1" to "Winston" never disturbs the underlying
 * transcript-to-speaker assignment. Null [confidence] means the diarization engine (or the
 * absence of one) didn't provide a meaningful score — never fabricated.
 */
data class Speaker(
    val id: String,
    val meetingId: String,
    val speakerIndex: Int,
    val originalLabel: String,
    val customName: String,
    val colorHex: String,
    val confidence: Float? = null
)

/**
 * Distinguishes a firm agreement from something merely discussed or proposed — collapsing this
 * distinction is exactly the kind of overclaiming the Meeting Intelligence engine must avoid.
 * See docs/AI_ARCHITECTURE.md "No-Hallucination Requirement".
 */
enum class DecisionType { DECISION, SUGGESTION, DISCUSSION, POSSIBILITY }

data class ActionItem(
    val id: String,
    val meetingId: String,
    val task: String,
    val assigneeSpeakerId: String? = null,
    val assigneeName: String? = null,
    val deadline: String? = null,
    // Null when the source (LLM extraction, or a user manually adding one) doesn't provide a
    // meaningful confidence — never a fabricated default.
    val confidence: Float? = null,
    val isCompleted: Boolean = false,
    val sourceSegmentIds: List<String> = emptyList()
)

data class Decision(
    val id: String,
    val meetingId: String,
    val text: String,
    val type: DecisionType = DecisionType.DISCUSSION,
    val confidence: Float? = null,
    val sourceSegmentIds: List<String> = emptyList()
)

data class Question(
    val id: String,
    val meetingId: String,
    val text: String,
    val askedBySpeakerId: String? = null,
    val resolved: Boolean = false,
    val answer: String? = null,
    val sourceSegmentIds: List<String> = emptyList()
)

data class FollowUp(
    val id: String,
    val meetingId: String,
    val description: String,
    val ownerSpeakerId: String? = null,
    val deadline: String? = null,
    val sourceSegmentIds: List<String> = emptyList()
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
    val followUps: List<FollowUp> = emptyList()
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
    /** SHA-256 of the final installed file at [fileName] — of the extracted entry, not the archive, when [archiveEntryPath] is set. */
    val sha256: String,
    /** Size of the final installed file at [fileName]. */
    val sizeBytes: Long,
    /** Raw HTTP payload size, when it differs from [sizeBytes] (i.e. [downloadUrl] points at an archive). Defaults to [sizeBytes]. */
    val downloadSizeBytes: Long? = null,
    /** When set, [downloadUrl] points at a `.tar.bz2` archive; this is the path of the entry to extract and rename to [fileName]. Null means [downloadUrl] is the file itself. */
    val archiveEntryPath: String? = null
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
    val quantization: String = "q4_0",
    /** Maximum total tokens (prompt + generated) this specific installed model build supports, when known — e.g. an LLM's real KV-cache size. Null for non-LLM models. */
    val contextLengthTokens: Int? = null
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
