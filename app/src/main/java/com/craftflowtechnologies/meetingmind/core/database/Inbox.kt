package com.craftflowtechnologies.meetingmind.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Schema 21 (docs/PLAN_PROFESSIONAL.md D7): the Work Inbox, the front door for things shared into
 * the app. Schema 29 adds source tracking, media metadata, and transcription status.
 */
@Entity(tableName = "inbox_items", indices = [Index(value = ["status"])])
data class InboxItemEntity(
    @PrimaryKey val id: String,
    /** TEXT, URL, PDF, AUDIO, IMAGE, VIDEO or FILE. */
    val kind: String,
    /** A copy kept in the app's own storage (a file path), for anything that arrived as a file. */
    val uri: String? = null,
    /** What was shared as text, or the text read from a file. */
    val text: String? = null,
    val title: String? = null,
    /** NEW, PROPOSED, FILED or DISMISSED. */
    val status: String,
    /** The one filing proposed for it, as JSON. */
    val proposedJson: String? = null,
    val createdAt: Long,
    val processedAt: Long? = null,
    /** Where it was filed, as JSON. */
    val resultRefJson: String? = null,
    val source: String? = null,
    val mediaDurationMs: Long? = null,
    val mediaThumbnailUri: String? = null,
    val transcriptionStatus: String = "NONE",
    val meetingId: String? = null
)

@Dao
interface InboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: InboxItemEntity)
    @Update suspend fun update(item: InboxItemEntity)

    @Query("SELECT * FROM inbox_items WHERE id = :id")
    suspend fun get(id: String): InboxItemEntity?

    @Query("SELECT * FROM inbox_items WHERE status IN ('NEW', 'PROPOSED') ORDER BY createdAt DESC")
    fun observeOpen(): Flow<List<InboxItemEntity>>

    @Query("SELECT COUNT(*) FROM inbox_items WHERE status IN ('NEW', 'PROPOSED')")
    fun observeOpenCount(): Flow<Int>

    @Query("SELECT * FROM inbox_items WHERE status IN ('NEW', 'PROPOSED') ORDER BY createdAt DESC")
    suspend fun open(): List<InboxItemEntity>

    @Query("SELECT * FROM inbox_items ORDER BY createdAt DESC")
    suspend fun all(): List<InboxItemEntity>

    @Query("""
        UPDATE inbox_items
        SET transcriptionStatus = :status,
            text = CASE WHEN :transcript IS NOT NULL THEN :transcript ELSE text END,
            meetingId = CASE WHEN :meetingId IS NOT NULL THEN :meetingId ELSE meetingId END,
            mediaDurationMs = CASE WHEN :durationMs IS NOT NULL THEN :durationMs ELSE mediaDurationMs END
        WHERE id = :id
    """)
    suspend fun updateTranscription(
        id: String,
        status: String,
        transcript: String? = null,
        meetingId: String? = null,
        durationMs: Long? = null
    )

    @Query("SELECT * FROM inbox_items WHERE transcriptionStatus = :status ORDER BY createdAt DESC")
    fun observeByTranscriptionStatus(status: String): Flow<List<InboxItemEntity>>

    @Query("SELECT * FROM inbox_items WHERE transcriptionStatus = :status ORDER BY createdAt DESC")
    suspend fun byTranscriptionStatus(status: String): List<InboxItemEntity>
}
