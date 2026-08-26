package com.example.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meetings",
    indices = [Index(value = ["createdAt"]), Index(value = ["title"])]
)
data class MeetingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val source: String, // "LOCAL_RECORDING", "IMPORTED_AUDIO", "IMPORTED_VIDEO", "REMOTE_BOT"
    val audioFilePath: String?,
    val status: String, // "RECORDING", "PROCESSING", "READY", "ERROR"
    val participantCount: Int,
    val language: String,
    val summaryText: String?,
    val updatedAt: Long = System.currentTimeMillis(),
    val recordingType: String = "GENERAL",
    val customContext: String? = null,
    /** What the user told MeetingMind about expected speakers — see
     * [com.example.core.model.RecordingContext]. Null means unspecified. */
    val speakerCountPreference: Int? = null
)

@Entity(
    tableName = "transcript_segments",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["meetingId"]),
        Index(value = ["meetingId", "startMs"]),
        Index(value = ["text"])
    ]
)
data class TranscriptSegmentEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val speakerId: String?,
    val speakerName: String?,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float?,
    val isUserEdited: Boolean = false,
    /** Output of [com.example.ai.pipeline.TranscriptCleanupEngine], cached so cleanup only runs
     * once per segment instead of being recomputed on every LLM prompt render. Null means cleanup
     * hasn't run yet (or was rejected by [com.example.ai.pipeline.TranscriptQualityValidator]) —
     * callers fall back to [text]. Never written to when [isUserEdited] is true: the user's own
     * correction always wins over a cached cleanup of the text it replaced. */
    val cleanedText: String? = null,
    /** JSON array of the raw ASR fragment ids this paragraph was built from — see
     * [com.example.core.model.TranscriptSegment.sourceSegmentIds]. */
    val sourceSegmentIdsJson: String = "[]",
    /** JSON array of `{"text","startMs","endMs"}` objects — see
     * [com.example.core.model.TranscriptSegment.words]. `"[]"` for a segment transcribed before
     * this existed, or whose ASR engine doesn't provide word-level timing. */
    val wordsJson: String = "[]"
)

@Entity(
    tableName = "speakers",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["meetingId"])]
)
data class SpeakerEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val speakerIndex: Int = 0,
    val originalLabel: String,
    val customName: String,
    val colorHex: String,
    val confidence: Float? = null
)

@Entity(
    tableName = "action_items",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["meetingId"])]
)
data class ActionItemEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val task: String,
    val assigneeSpeakerId: String? = null,
    val assigneeName: String? = null,
    val deadline: String? = null,
    val confidence: Float? = null,
    val isCompleted: Boolean,
    val sourceSegmentIdsJson: String = "[]"
)

@Entity(
    tableName = "decisions",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["meetingId"])]
)
data class DecisionEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val text: String,
    val type: String = "DISCUSSION",
    val confidence: Float? = null,
    val sourceSegmentIdsJson: String = "[]"
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["meetingId"])]
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val text: String,
    val askedBySpeakerId: String? = null,
    val resolved: Boolean,
    val answer: String? = null,
    val sourceSegmentIdsJson: String = "[]"
)

@Entity(
    tableName = "follow_ups",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["meetingId"])]
)
data class FollowUpEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val description: String,
    val ownerSpeakerId: String? = null,
    val deadline: String? = null,
    val sourceSegmentIdsJson: String = "[]"
)

@Entity(
    tableName = "topics",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["meetingId"])]
)
data class TopicEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val name: String,
    val relevance: Float
)

@Entity(
    tableName = "embeddings",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["meetingId"])]
)
data class EmbeddingEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val segmentId: String?,
    val textChunk: String,
    val vectorData: String, // Comma separated float values
    val startMs: Long,
    val endMs: Long
)

@Entity(tableName = "ai_models")
data class AiModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val capabilities: String, // comma-separated ModelCapability
    // JSON array of {fileName,downloadUrl,sha256,sizeBytes} — a model may need multiple files
    // (e.g. a transducer ASR model = encoder + decoder + joiner + tokens). See ModelFileSpec.
    val filesJson: String,
    val minimumRamMb: Int,
    val recommendedRamMb: Int,
    val version: String,
    val isInstalled: Boolean,
    val isDownloading: Boolean,
    val downloadProgress: Float,
    val description: String,
    val parameterCount: String,
    val quantization: String,
    val contextLengthTokens: Int? = null
)

@Entity(tableName = "processing_jobs")
data class ProcessingJobEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val meetingTitle: String,
    val currentStep: String,
    val progressPercent: Int,
    val isCompleted: Boolean,
    val isFailed: Boolean,
    val errorMessage: String?,
    val startedAt: Long,
    // Real typed stage (ProcessingStage.name) alongside the human-readable currentStep label —
    // never an invented percentage substituting for actual stage identity.
    val stage: String = "IDLE"
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["meetingId", "timestamp"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val isUser: Boolean,
    val content: String,
    val timestamp: Long,
    val sourceTimestampsJson: String = "", // JSON or comma-separated
    val sourceQuotesJson: String = "",
    /** How many transcript segments were actually retrieved/read as context to produce this
     * answer — see [com.example.core.model.ChatMessage.readSegmentCount]. 0 for a user message,
     * or an AI answer from before this field existed. */
    val readSegmentCount: Int = 0
)

/**
 * A learned correction — "the ASR keeps writing X, the user always means Y" — persisted so it can
 * be looked up later, never so the model can be retrained (see docs/AI_ARCHITECTURE.md §11: this
 * is a lookup table read at prompt-construction time, not on-device fine-tuning). Global across
 * every recording, not scoped to one meeting: a name or product term the user corrects once is
 * worth remembering everywhere. [surfaceForm] is deliberately not the primary key — the same
 * mis-transcription can recur and should increment [frequency]/refresh [lastConfirmedAt] on the
 * existing row rather than create a duplicate.
 */
@Entity(tableName = "vocabulary", indices = [Index(value = ["surfaceForm"], unique = true)])
data class VocabularyEntity(
    @PrimaryKey val id: String,
    val surfaceForm: String,
    val canonicalForm: String,
    /** [com.example.core.model.VocabularyTermType.name] — honestly OTHER when nothing inferred it. */
    val type: String,
    /** How sure this mapping is real, not a one-off typo. 1.0 for a direct user correction — the
     * user saying so explicitly is the strongest signal this system has; left room for a future
     * AI-suggested entry to persist at something lower. */
    val confidence: Float,
    /** [com.example.core.model.VocabularySource.name] — where this correction came from. */
    val source: String,
    /** How many times this exact correction has been made/confirmed. */
    val frequency: Int,
    val lastConfirmedAt: Long
)
