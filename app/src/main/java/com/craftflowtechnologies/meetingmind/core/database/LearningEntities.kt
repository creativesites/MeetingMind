package com.craftflowtechnologies.meetingmind.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.craftflowtechnologies.meetingmind.core.model.ActivityAttempt
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.LearningSession
import com.craftflowtechnologies.meetingmind.core.model.RecallRating
import com.craftflowtechnologies.meetingmind.core.model.ReviewSchedule
import org.json.JSONArray

@Entity(
    tableName = "learning_sessions",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["noteId"]),
        Index(value = ["meetingId"]),
        Index(value = ["status"])
    ]
)
data class LearningSessionEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val meetingId: String? = null,
    val title: String,
    val courseName: String? = null,
    @ColumnInfo(defaultValue = "ACTIVE") val status: String = "ACTIVE",
    val lastStudiedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): LearningSession = LearningSession(
        id = id,
        noteId = noteId,
        meetingId = meetingId,
        title = title,
        courseName = courseName,
        status = status,
        lastStudiedAt = lastStudiedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(domain: LearningSession): LearningSessionEntity = LearningSessionEntity(
            id = domain.id,
            noteId = domain.noteId,
            meetingId = domain.meetingId,
            title = domain.title,
            courseName = domain.courseName,
            status = domain.status,
            lastStudiedAt = domain.lastStudiedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}

@Entity(
    tableName = "learning_concepts",
    foreignKeys = [
        ForeignKey(
            entity = LearningSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["state"])
    ]
)
data class LearningConceptEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val name: String,
    val definition: String,
    val emphasis: String? = null,
    @ColumnInfo(defaultValue = "[]") val relationshipsJson: String = "[]",
    @ColumnInfo(defaultValue = "[]") val evidenceJson: String = "[]",
    @ColumnInfo(defaultValue = "NEW") val state: String = "NEW",
    @ColumnInfo(defaultValue = "0") val isUserEdited: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isDismissed: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): LearningConcept = LearningConcept(
        id = id,
        sessionId = sessionId,
        name = name,
        definition = definition,
        emphasis = emphasis,
        relationships = runCatching {
            val arr = JSONArray(relationshipsJson)
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList()),
        evidence = LearningEvidence.parseList(evidenceJson),
        state = runCatching { LearningMasteryState.valueOf(state) }.getOrDefault(LearningMasteryState.NEW),
        isUserEdited = isUserEdited,
        isDismissed = isDismissed,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(domain: LearningConcept): LearningConceptEntity = LearningConceptEntity(
            id = domain.id,
            sessionId = domain.sessionId,
            name = domain.name,
            definition = domain.definition,
            emphasis = domain.emphasis,
            relationshipsJson = JSONArray(domain.relationships).toString(),
            evidenceJson = LearningEvidence.listToJson(domain.evidence),
            state = domain.state.name,
            isUserEdited = domain.isUserEdited,
            isDismissed = domain.isDismissed,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}

@Entity(
    tableName = "learning_activities",
    foreignKeys = [
        ForeignKey(
            entity = LearningSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LearningConceptEntity::class,
            parentColumns = ["id"],
            childColumns = ["conceptId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["conceptId"]),
        Index(value = ["type"]),
        Index(value = ["isDiagnostic"])
    ]
)
data class LearningActivityEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val conceptId: String? = null,
    val type: String,
    val prompt: String,
    val expectedAnswer: String,
    @ColumnInfo(defaultValue = "[]") val optionsJson: String = "[]",
    @ColumnInfo(defaultValue = "MEDIUM") val difficulty: String = "MEDIUM",
    @ColumnInfo(defaultValue = "[]") val evidenceJson: String = "[]",
    @ColumnInfo(defaultValue = "0") val isDiagnostic: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isDismissed: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isStale: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): LearningActivity = LearningActivity(
        id = id,
        sessionId = sessionId,
        conceptId = conceptId,
        type = runCatching { LearningActivityType.valueOf(type) }.getOrDefault(LearningActivityType.RECALL),
        prompt = prompt,
        expectedAnswer = expectedAnswer,
        options = runCatching {
            val arr = JSONArray(optionsJson)
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList()),
        difficulty = difficulty,
        evidence = LearningEvidence.parseList(evidenceJson),
        isDiagnostic = isDiagnostic,
        isDismissed = isDismissed,
        isStale = isStale,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(domain: LearningActivity): LearningActivityEntity = LearningActivityEntity(
            id = domain.id,
            sessionId = domain.sessionId,
            conceptId = domain.conceptId,
            type = domain.type.name,
            prompt = domain.prompt,
            expectedAnswer = domain.expectedAnswer,
            optionsJson = JSONArray(domain.options).toString(),
            difficulty = domain.difficulty,
            evidenceJson = LearningEvidence.listToJson(domain.evidence),
            isDiagnostic = domain.isDiagnostic,
            isDismissed = domain.isDismissed,
            isStale = domain.isStale,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}

@Entity(
    tableName = "activity_attempts",
    foreignKeys = [
        ForeignKey(
            entity = LearningActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LearningSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["activityId"]),
        Index(value = ["sessionId"]),
        Index(value = ["conceptId"]),
        Index(value = ["createdAt"])
    ]
)
data class ActivityAttemptEntity(
    @PrimaryKey val id: String,
    val activityId: String,
    val sessionId: String,
    val conceptId: String? = null,
    val userResponse: String,
    val isCorrect: Boolean,
    val selfRating: String? = null,
    @ColumnInfo(defaultValue = "") val feedback: String = "",
    val createdAt: Long
) {
    fun toDomain(): ActivityAttempt = ActivityAttempt(
        id = id,
        activityId = activityId,
        sessionId = sessionId,
        conceptId = conceptId,
        userResponse = userResponse,
        isCorrect = isCorrect,
        selfRating = selfRating?.let { runCatching { RecallRating.valueOf(it) }.getOrNull() },
        feedback = feedback,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(domain: ActivityAttempt): ActivityAttemptEntity = ActivityAttemptEntity(
            id = domain.id,
            activityId = domain.activityId,
            sessionId = domain.sessionId,
            conceptId = domain.conceptId,
            userResponse = domain.userResponse,
            isCorrect = domain.isCorrect,
            selfRating = domain.selfRating?.name,
            feedback = domain.feedback,
            createdAt = domain.createdAt
        )
    }
}

@Entity(
    tableName = "review_schedules",
    foreignKeys = [
        ForeignKey(
            entity = LearningActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LearningSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["activityId"], unique = true),
        Index(value = ["sessionId"]),
        Index(value = ["dueAt"]),
        Index(value = ["isPaused"])
    ]
)
data class ReviewScheduleEntity(
    @PrimaryKey val id: String,
    val activityId: String,
    val sessionId: String,
    val conceptId: String? = null,
    val dueAt: Long,
    @ColumnInfo(defaultValue = "0") val intervalDays: Int = 0,
    @ColumnInfo(defaultValue = "0") val repetitionCount: Int = 0,
    @ColumnInfo(defaultValue = "2.5") val easeFactor: Double = 2.5,
    @ColumnInfo(defaultValue = "0") val isPaused: Boolean = false,
    val snoozedUntil: Long? = null,
    val lastReviewedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): ReviewSchedule = ReviewSchedule(
        id = id,
        activityId = activityId,
        sessionId = sessionId,
        conceptId = conceptId,
        dueAt = dueAt,
        intervalDays = intervalDays,
        repetitionCount = repetitionCount,
        easeFactor = easeFactor,
        isPaused = isPaused,
        snoozedUntil = snoozedUntil,
        lastReviewedAt = lastReviewedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(domain: ReviewSchedule): ReviewScheduleEntity = ReviewScheduleEntity(
            id = domain.id,
            activityId = domain.activityId,
            sessionId = domain.sessionId,
            conceptId = domain.conceptId,
            dueAt = domain.dueAt,
            intervalDays = domain.intervalDays,
            repetitionCount = domain.repetitionCount,
            easeFactor = domain.easeFactor,
            isPaused = domain.isPaused,
            snoozedUntil = domain.snoozedUntil,
            lastReviewedAt = domain.lastReviewedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
