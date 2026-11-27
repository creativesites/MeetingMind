package com.craftflowtechnologies.meetingmind.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Schema 32 (the Your garden spec): a practice is something the person chose to try, shown as a
 * plant that grows as they keep it. It is also a repeating task, linked by [taskId]; the task's
 * [TaskEntity.sourceItemId] is the practice id, so ticking the task logs a day here.
 */
@Entity(tableName = "practices")
data class PracticeEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** The note it was taken from, if any. Kept as a plain id: the practice outlives the note. */
    val sourceNoteId: String?,
    val sourceTitle: String?,
    /** The repeating task in Tasks that carries it. */
    val taskId: String?,
    /** [com.craftflowtechnologies.meetingmind.core.practice.Rhythm] name. */
    val rhythm: String,
    /** Minutes after midnight for the reminder; null for none. */
    val remindMinutes: Int?,
    /** Weeks until the check-in question; 0 means no end. */
    val checkInWeeks: Int,
    val startedAt: Long,
    val checkInAt: Long?,
    /** GROWING, BLOOMED or LET_GO: [com.craftflowtechnologies.meetingmind.core.practice.PracticeState] name. */
    val state: String,
    val endedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

/** A day a practice was kept. [day] is an ISO local date (yyyy-MM-dd). One row per practice per day. */
@Entity(
    tableName = "practice_days",
    primaryKeys = ["practiceId", "day"],
    indices = [Index(value = ["practiceId"])]
)
data class PracticeDayEntity(
    val practiceId: String,
    val day: String,
    val keptAt: Long
)

@Dao
interface PracticeDao {
    @Upsert suspend fun upsert(practice: PracticeEntity)

    @Query("SELECT * FROM practices WHERE id = :id")
    suspend fun getById(id: String): PracticeEntity?

    /** Growing practices first, then bloomed ones; oldest first within each group. */
    @Query(
        """SELECT * FROM practices WHERE state IN ('GROWING', 'BLOOMED')
           ORDER BY CASE state WHEN 'GROWING' THEN 0 ELSE 1 END, startedAt, createdAt"""
    )
    fun observeGarden(): Flow<List<PracticeEntity>>

    @Query("SELECT * FROM practice_days")
    fun observeAllDays(): Flow<List<PracticeDayEntity>>

    @Query("SELECT * FROM practice_days WHERE practiceId = :practiceId ORDER BY day")
    suspend fun daysFor(practiceId: String): List<PracticeDayEntity>

    // Logging a day is on TaskDao.insertPracticeDay, so ticking a task needs no extra dependency.

    @Query("DELETE FROM practice_days WHERE practiceId = :practiceId AND day = :day")
    suspend fun deleteDay(practiceId: String, day: String)
}
