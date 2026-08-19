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
    val updatedAt: Long = System.currentTimeMillis()
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
    val speakerId: String,
    val speakerName: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float
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
    val originalLabel: String,
    val customName: String,
    val colorHex: String
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
    val assignee: String,
    val deadline: String,
    val confidence: Float,
    val isCompleted: Boolean,
    val sourceTimestampMs: Long? = null
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
    val confidence: Float,
    val timestampMs: Long? = null
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
    val resolved: Boolean,
    val answer: String? = null,
    val timestampMs: Long? = null
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
    val sizeBytes: Long,
    val minimumRamMb: Int,
    val recommendedRamMb: Int,
    val downloadUrl: String,
    val version: String,
    val isInstalled: Boolean,
    val isDownloading: Boolean,
    val downloadProgress: Float,
    val description: String,
    val parameterCount: String,
    val quantization: String
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
    val startedAt: Long
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
    val sourceQuotesJson: String = ""
)
