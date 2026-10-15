package com.craftflowtechnologies.meetingmind.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Schema 20 (docs/PLAN_PROFESSIONAL.md D5.3, D5.5): the model's prose for a brief, kept so it isn't
 * paid for twice, and the monthly story of a person, organisation or project. The structure of a
 * brief is never stored: it's read from the items each time.
 */
@Entity(tableName = "briefs", indices = [Index(value = ["entityType", "entityId", "kind"])])
data class BriefEntity(
    @PrimaryKey val id: String,
    /** PERSON, ORG, PROJECT, MEETING or WORK (everything). */
    val entityType: String,
    val entityId: String,
    /** MEETING, CLIENT, PROJECT, RELATIONSHIP or WEEKLY. */
    val kind: String,
    /** The cited sentences the model wrote, as JSON. */
    val contentJson: String,
    /** The item, evidence and meeting ids those sentences cite. */
    val citedIdsJson: String,
    val createdAt: Long
)

/** One month of one entity's history, told in a paragraph that cites what it draws on. */
@Entity(tableName = "memory_stories", primaryKeys = ["entityType", "entityId", "month"])
data class MemoryStoryEntity(
    val entityType: String,
    val entityId: String,
    /** "2026-09". */
    val month: String,
    val text: String,
    val citedIdsJson: String,
    /** How many items and changes the month held when the story was written; a different count means it's stale. */
    val itemCount: Int,
    val createdAt: Long
)

@Dao
interface BriefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(brief: BriefEntity)

    @Query("SELECT * FROM briefs WHERE entityType = :entityType AND entityId = :entityId AND kind = :kind ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(entityType: String, entityId: String, kind: String): BriefEntity?

    @Query("DELETE FROM briefs WHERE entityType = :entityType AND entityId = :entityId AND kind = :kind")
    suspend fun clear(entityType: String, entityId: String, kind: String)

    @Query("SELECT COUNT(*) FROM briefs") suspend fun count(): Int
}

@Dao
interface MemoryStoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(story: MemoryStoryEntity)

    @Query("SELECT * FROM memory_stories WHERE entityType = :entityType AND entityId = :entityId AND month = :month")
    suspend fun get(entityType: String, entityId: String, month: String): MemoryStoryEntity?

    @Query("SELECT * FROM memory_stories WHERE entityType = :entityType AND entityId = :entityId ORDER BY month DESC")
    suspend fun forEntity(entityType: String, entityId: String): List<MemoryStoryEntity>
}
