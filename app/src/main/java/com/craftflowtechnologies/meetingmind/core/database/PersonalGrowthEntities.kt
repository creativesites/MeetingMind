package com.craftflowtechnologies.meetingmind.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Schema 32: Personal Vertical growth & living-journal substrate.
 *
 * Reuses `notebooks` (kind = LIFE_CHAPTER), `people` (space = PERSONAL), `notes`, `note_blocks`,
 * and `note_links` instead of duplicating concepts. These 6 normalized tables store:
 * - User routines & ordered activity steps (with minimum-effort fallbacks)
 * - Routine completions & resumable in-progress sessions
 * - Active & completed Journey / Challenge enrollments and per-step progress
 * - The daily Personal Today Plan (intention, low-energy mode, committed/dismissed items, day-page link)
 */

@Entity(
    tableName = "personal_routines",
    indices = [Index(value = ["timeWindow", "sortOrder"]), Index(value = ["isArchived"])]
)
data class PersonalRoutineEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(defaultValue = "") val whyDescription: String = "",
    /** MORNING, AFTERNOON, EVENING, ANYTIME. */
    @ColumnInfo(defaultValue = "MORNING") val timeWindow: String = "MORNING",
    /** Optional wall-clock alarm minute of day (0..1439). */
    val scheduledMinuteOfDay: Int? = null,
    /** Bitmask for days of week: Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64 (127 = every day). */
    @ColumnInfo(defaultValue = "127") val daysOfWeekMask: Int = 127,
    @ColumnInfo(defaultValue = "0") val reminderEnabled: Boolean = false,
    /** Behavioral cue, e.g. "After I pour my morning coffee". */
    @ColumnInfo(defaultValue = "") val anchorCue: String = "",
    /** NONE, RAIN, FIREPLACE, FOREST, DAWN. */
    @ColumnInfo(defaultValue = "NONE") val soundscapeId: String = "NONE",
    /** Optional Journey catalog ID that recommended this routine. */
    val linkedJourneyId: String? = null,
    /** Optional Life Chapter notebook ID (`notebooks.id`). */
    val lifeChapterNotebookId: String? = null,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "routine_steps",
    foreignKeys = [
        ForeignKey(
            entity = PersonalRoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["routineId", "position"])]
)
data class RoutineStepEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val position: Int,
    /** CHECK, BREATHING, VOICE_REFLECTION, QUIET_WRITE, INTENTION, COACHING_AUDIO, TIMER, MEMORY. */
    @ColumnInfo(defaultValue = "CHECK") val kind: String = "CHECK",
    val title: String,
    /** Low-energy fallback title, e.g. "Read just 1 page". Blank falls back to [title]. */
    @ColumnInfo(defaultValue = "") val minEffortTitle: String = "",
    @ColumnInfo(defaultValue = "120") val durationSeconds: Int = 120,
    @ColumnInfo(defaultValue = "45") val minEffortDurationSeconds: Int = 45,
    /** Optional reflection prompt shown during VOICE_REFLECTION, QUIET_WRITE or INTENTION. */
    @ColumnInfo(defaultValue = "") val promptText: String = "",
    /** Optional link to a bundled coaching script / guided activity in `PersonalGrowthCatalog`. */
    val guidedActivityId: String? = null,
    /** Companion visual hint: BREATHING, LISTENING, COZY, IDLE, READING. */
    @ColumnInfo(defaultValue = "IDLE") val companionState: String = "IDLE",
    @ColumnInfo(defaultValue = "1") val isEnabled: Boolean = true
)

@Entity(
    tableName = "routine_completions",
    foreignKeys = [
        ForeignKey(
            entity = PersonalRoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["reflectionNoteId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["routineId", "epochDay"]),
        Index(value = ["epochDay"]),
        Index(value = ["reflectionNoteId"])
    ]
)
data class RoutineCompletionEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    /** Local CivilDate epoch day (days since 1970-01-01). */
    val epochDay: Long,
    /** IN_PROGRESS, COMPLETED, COMPLETED_MIN_EFFORT, SKIPPED. */
    @ColumnInfo(defaultValue = "IN_PROGRESS") val status: String = "IN_PROGRESS",
    @ColumnInfo(defaultValue = "0") val currentStepIndex: Int = 0,
    /** JSON array of completed [RoutineStepEntity.id] values. */
    @ColumnInfo(defaultValue = "[]") val completedStepIdsJson: String = "[]",
    @ColumnInfo(defaultValue = "0") val usedMinEffortMode: Boolean = false,
    @ColumnInfo(defaultValue = "0") val actualDurationSeconds: Int = 0,
    val reflectionNoteId: String? = null,
    val startedAt: Long,
    val completedAt: Long? = null
)

@Entity(
    tableName = "growth_enrollments",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["anchorWhyNoteId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["finaleNoteId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["lifeChapterNotebookId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["kind", "status"]),
        Index(value = ["catalogId"]),
        Index(value = ["anchorWhyNoteId"]),
        Index(value = ["finaleNoteId"]),
        Index(value = ["lifeChapterNotebookId"])
    ]
)
data class GrowthEnrollmentEntity(
    @PrimaryKey val id: String,
    /** JOURNEY or CHALLENGE. */
    val kind: String,
    /** Catalog ID (e.g. "journey_morning") or "custom:<uuid>". */
    val catalogId: String,
    val title: String,
    /** JSON blob for user-created or YouTube-derived custom challenges/journeys. */
    @ColumnInfo(defaultValue = "{}") val customConfigJson: String = "{}",
    /** ACTIVE, PAUSED, COMPLETED, ABANDONED. */
    @ColumnInfo(defaultValue = "ACTIVE") val status: String = "ACTIVE",
    val startedEpochDay: Long,
    @ColumnInfo(defaultValue = "0") val currentStepOrDay: Int = 0,
    val targetStepsOrDays: Int,
    /** User's Day 1 "Why" note (voice or text). */
    val anchorWhyNoteId: String? = null,
    /** Grounded final review note created when completed. */
    val finaleNoteId: String? = null,
    val lifeChapterNotebookId: String? = null,
    @ColumnInfo(defaultValue = "1") val catalogVersion: Int = 1,
    val lastActivityAt: Long,
    val completedAt: Long? = null
)

@Entity(
    tableName = "growth_step_progress",
    foreignKeys = [
        ForeignKey(
            entity = GrowthEnrollmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["enrollmentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["reflectionNoteId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["enrollmentId", "stepIndex"], unique = true),
        Index(value = ["completedEpochDay"]),
        Index(value = ["reflectionNoteId"])
    ]
)
data class GrowthStepProgressEntity(
    @PrimaryKey val id: String,
    val enrollmentId: String,
    val stepIndex: Int,
    val completedEpochDay: Long,
    @ColumnInfo(defaultValue = "0") val usedMinEffort: Boolean = false,
    val reflectionNoteId: String? = null,
    val reflectionBlockId: String? = null,
    val completedAt: Long
)

@Entity(
    tableName = "personal_daily_plans",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayPageNoteId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["dayPageNoteId"])]
)
data class PersonalDailyPlanEntity(
    /** Local CivilDate epoch day. One plan row per calendar day. */
    @PrimaryKey val epochDay: Long,
    @ColumnInfo(defaultValue = "") val intentionText: String = "",
    val intentionNoteBlockId: String? = null,
    /** JSON array of committed routine/journey/challenge/activity IDs ordered by user. */
    @ColumnInfo(defaultValue = "[]") val committedItemIdsJson: String = "[]",
    /** JSON array of item IDs snoozed or dismissed for this day. */
    @ColumnInfo(defaultValue = "[]") val dismissedItemIdsJson: String = "[]",
    @ColumnInfo(defaultValue = "0") val isMinEffortDay: Boolean = false,
    /** PEACEFUL, GRATEFUL, JOYFUL, REFLECTIVE, CELEBRATORY, or null. */
    val moodVibe: String? = null,
    /** Primary stitched Journal Note for this date. */
    val dayPageNoteId: String? = null,
    val updatedAt: Long
)

@Dao
interface PersonalGrowthDao {

    // ---- Routines ----

    @Query("SELECT * FROM personal_routines WHERE isArchived = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeActiveRoutines(): Flow<List<PersonalRoutineEntity>>

    @Query("SELECT * FROM personal_routines WHERE isArchived = 0 ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getActiveRoutines(): List<PersonalRoutineEntity>

    @Query("SELECT * FROM personal_routines WHERE id = :id")
    suspend fun getRoutineById(id: String): PersonalRoutineEntity?

    @Query("SELECT COUNT(*) FROM personal_routines")
    suspend fun routineCount(): Int

    @Upsert
    suspend fun upsertRoutine(routine: PersonalRoutineEntity)

    @Query("UPDATE personal_routines SET isArchived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setRoutineArchived(id: String, archived: Boolean, updatedAt: Long)

    @Query("DELETE FROM personal_routines WHERE id = :id")
    suspend fun deleteRoutine(id: String)

    // ---- Routine Steps ----

    @Query("SELECT * FROM routine_steps WHERE routineId = :routineId ORDER BY position ASC")
    fun observeStepsForRoutine(routineId: String): Flow<List<RoutineStepEntity>>

    @Query("SELECT * FROM routine_steps ORDER BY routineId ASC, position ASC")
    fun observeAllSteps(): Flow<List<RoutineStepEntity>>

    @Query("SELECT * FROM routine_steps WHERE routineId = :routineId ORDER BY position ASC")
    suspend fun getStepsForRoutine(routineId: String): List<RoutineStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSteps(steps: List<RoutineStepEntity>)

    @Query("DELETE FROM routine_steps WHERE routineId = :routineId")
    suspend fun deleteStepsForRoutine(routineId: String)

    @Transaction
    suspend fun saveRoutineWithSteps(routine: PersonalRoutineEntity, steps: List<RoutineStepEntity>) {
        upsertRoutine(routine)
        deleteStepsForRoutine(routine.id)
        upsertSteps(steps.mapIndexed { idx, s -> s.copy(routineId = routine.id, position = idx) })
    }

    // ---- Routine Completions ----

    @Query("SELECT * FROM routine_completions WHERE epochDay = :epochDay ORDER BY startedAt DESC")
    fun observeCompletionsForDay(epochDay: Long): Flow<List<RoutineCompletionEntity>>

    @Query("SELECT * FROM routine_completions WHERE epochDay >= :fromEpochDay ORDER BY epochDay DESC, startedAt DESC")
    fun observeCompletionsSince(fromEpochDay: Long): Flow<List<RoutineCompletionEntity>>

    @Query("SELECT * FROM routine_completions WHERE routineId = :routineId AND epochDay = :epochDay ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestCompletionForDay(routineId: String, epochDay: Long): RoutineCompletionEntity?

    @Query("SELECT * FROM routine_completions WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgressCompletion(): Flow<RoutineCompletionEntity?>

    @Upsert
    suspend fun upsertCompletion(completion: RoutineCompletionEntity)

    // ---- Journeys & Challenges Enrollments ----

    @Query("SELECT * FROM growth_enrollments ORDER BY lastActivityAt DESC")
    fun observeAllEnrollments(): Flow<List<GrowthEnrollmentEntity>>

    @Query("SELECT * FROM growth_enrollments WHERE status = 'ACTIVE' ORDER BY lastActivityAt DESC")
    fun observeActiveEnrollments(): Flow<List<GrowthEnrollmentEntity>>

    @Query("SELECT * FROM growth_enrollments WHERE id = :id")
    suspend fun getEnrollmentById(id: String): GrowthEnrollmentEntity?

    @Query("SELECT * FROM growth_enrollments WHERE catalogId = :catalogId AND status = 'ACTIVE' LIMIT 1")
    suspend fun findActiveByCatalogId(catalogId: String): GrowthEnrollmentEntity?

    @Upsert
    suspend fun upsertEnrollment(enrollment: GrowthEnrollmentEntity)

    @Query("SELECT * FROM growth_step_progress WHERE enrollmentId = :enrollmentId ORDER BY stepIndex ASC")
    fun observeStepProgress(enrollmentId: String): Flow<List<GrowthStepProgressEntity>>

    @Query("SELECT * FROM growth_step_progress WHERE enrollmentId = :enrollmentId ORDER BY stepIndex ASC")
    suspend fun getStepProgress(enrollmentId: String): List<GrowthStepProgressEntity>

    @Query("SELECT * FROM growth_step_progress WHERE completedEpochDay >= :fromEpochDay ORDER BY completedAt DESC")
    fun observeStepProgressSince(fromEpochDay: Long): Flow<List<GrowthStepProgressEntity>>

    @Upsert
    suspend fun upsertStepProgress(progress: GrowthStepProgressEntity)

    // ---- Daily Plans ----

    @Query("SELECT * FROM personal_daily_plans WHERE epochDay = :epochDay")
    fun observeDailyPlan(epochDay: Long): Flow<PersonalDailyPlanEntity?>

    @Query("SELECT * FROM personal_daily_plans WHERE epochDay = :epochDay")
    suspend fun getDailyPlan(epochDay: Long): PersonalDailyPlanEntity?

    @Query("SELECT * FROM personal_daily_plans WHERE epochDay BETWEEN :fromDay AND :toDay ORDER BY epochDay DESC")
    fun observeDailyPlansBetween(fromDay: Long, toDay: Long): Flow<List<PersonalDailyPlanEntity>>

    @Upsert
    suspend fun upsertDailyPlan(plan: PersonalDailyPlanEntity)
}
