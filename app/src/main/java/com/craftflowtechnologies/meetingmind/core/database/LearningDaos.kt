package com.craftflowtechnologies.meetingmind.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: LearningSessionEntity)

    @Update
    suspend fun update(session: LearningSessionEntity)

    @Query("SELECT * FROM learning_sessions WHERE id = :id")
    suspend fun getById(id: String): LearningSessionEntity?

    @Query("SELECT * FROM learning_sessions WHERE noteId = :noteId LIMIT 1")
    suspend fun getByNoteId(noteId: String): LearningSessionEntity?

    @Query("SELECT * FROM learning_sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LearningSessionEntity>>

    @Query("SELECT * FROM learning_sessions WHERE id = :id")
    fun observeById(id: String): Flow<LearningSessionEntity?>

    @Query("SELECT * FROM learning_sessions WHERE noteId = :noteId LIMIT 1")
    fun observeByNoteId(noteId: String): Flow<LearningSessionEntity?>

    @Query("DELETE FROM learning_sessions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface LearningConceptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(concept: LearningConceptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(concepts: List<LearningConceptEntity>)

    @Update
    suspend fun update(concept: LearningConceptEntity)

    @Query("SELECT * FROM learning_concepts WHERE id = :id")
    suspend fun getById(id: String): LearningConceptEntity?

    @Query("SELECT * FROM learning_concepts WHERE sessionId = :sessionId AND isDismissed = 0 ORDER BY createdAt ASC")
    fun observeBySession(sessionId: String): Flow<List<LearningConceptEntity>>

    @Query("SELECT * FROM learning_concepts WHERE sessionId = :sessionId AND isDismissed = 0")
    suspend fun getBySession(sessionId: String): List<LearningConceptEntity>

    @Query("SELECT * FROM learning_concepts WHERE isDismissed = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LearningConceptEntity>>

    @Query("DELETE FROM learning_concepts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface LearningActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(activity: LearningActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(activities: List<LearningActivityEntity>)

    @Update
    suspend fun update(activity: LearningActivityEntity)

    @Query("SELECT * FROM learning_activities WHERE id = :id")
    suspend fun getById(id: String): LearningActivityEntity?

    @Query("SELECT * FROM learning_activities WHERE sessionId = :sessionId AND isDismissed = 0 ORDER BY createdAt ASC")
    fun observeBySession(sessionId: String): Flow<List<LearningActivityEntity>>

    @Query("SELECT * FROM learning_activities WHERE sessionId = :sessionId AND isDismissed = 0 ORDER BY createdAt ASC")
    suspend fun getBySession(sessionId: String): List<LearningActivityEntity>

    @Query("SELECT * FROM learning_activities WHERE sessionId = :sessionId AND isDiagnostic = 1 AND isDismissed = 0")
    fun observeDiagnosticBySession(sessionId: String): Flow<List<LearningActivityEntity>>

    @Query("SELECT * FROM learning_activities WHERE sessionId = :sessionId AND isDiagnostic = 1 AND isDismissed = 0")
    suspend fun getDiagnosticBySession(sessionId: String): List<LearningActivityEntity>

    @Query("SELECT * FROM learning_activities WHERE conceptId = :conceptId AND isDismissed = 0 AND isStale = 0")
    suspend fun getByConcept(conceptId: String): List<LearningActivityEntity>

    @Query("SELECT * FROM learning_activities WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<LearningActivityEntity>

    @Query("SELECT * FROM learning_activities ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<LearningActivityEntity>>

    @Query("DELETE FROM learning_activities WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ActivityAttemptDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(attempt: ActivityAttemptEntity)

    @Query("SELECT * FROM activity_attempts WHERE activityId = :activityId ORDER BY createdAt ASC")
    suspend fun getByActivity(activityId: String): List<ActivityAttemptEntity>

    @Query("SELECT * FROM activity_attempts WHERE conceptId = :conceptId ORDER BY createdAt ASC")
    suspend fun getByConcept(conceptId: String): List<ActivityAttemptEntity>

    @Query("SELECT * FROM activity_attempts WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getBySession(sessionId: String): List<ActivityAttemptEntity>

    @Query("SELECT * FROM activity_attempts WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun observeBySession(sessionId: String): Flow<List<ActivityAttemptEntity>>

    @Query("SELECT * FROM activity_attempts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ActivityAttemptEntity>>
}

@Dao
interface ReviewScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: ReviewScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(schedules: List<ReviewScheduleEntity>)

    @Update
    suspend fun update(schedule: ReviewScheduleEntity)

    @Query("SELECT * FROM review_schedules WHERE activityId = :activityId LIMIT 1")
    suspend fun getByActivity(activityId: String): ReviewScheduleEntity?

    @Query("SELECT * FROM review_schedules WHERE conceptId = :conceptId LIMIT 1")
    suspend fun getByConcept(conceptId: String): ReviewScheduleEntity?

    @Query("""
        SELECT * FROM review_schedules 
        WHERE isPaused = 0 
          AND (snoozedUntil IS NULL OR snoozedUntil <= :now) 
          AND dueAt <= :now 
        ORDER BY dueAt ASC
    """)
    fun observeDue(now: Long): Flow<List<ReviewScheduleEntity>>

    @Query("""
        SELECT * FROM review_schedules 
        WHERE isPaused = 0 
          AND (snoozedUntil IS NULL OR snoozedUntil <= :now) 
          AND dueAt <= :now 
        ORDER BY dueAt ASC
    """)
    suspend fun getDue(now: Long): List<ReviewScheduleEntity>

    @Query("SELECT * FROM review_schedules WHERE sessionId = :sessionId")
    fun observeBySession(sessionId: String): Flow<List<ReviewScheduleEntity>>

    @Query("SELECT * FROM review_schedules WHERE sessionId = :sessionId")
    suspend fun getBySession(sessionId: String): List<ReviewScheduleEntity>

    @Query("DELETE FROM review_schedules WHERE activityId = :activityId")
    suspend fun deleteByActivity(activityId: String)
}
