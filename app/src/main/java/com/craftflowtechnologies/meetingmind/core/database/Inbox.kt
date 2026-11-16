package com.craftflowtechnologies.meetingmind.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
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
    val meetingId: String? = null,
    /** Schema 31 (R4): NONE, PENDING, READING, DONE, NO_TEXT, LOCKED or FAILED. */
    @ColumnInfo(defaultValue = "NONE") val readStatus: String = "NONE",
    /** Schema 31: how many pages a PDF or scan has, once known. */
    val pageCount: Int? = null,
    /** Schema 31: how many pages have been read so far, for progress. */
    @ColumnInfo(defaultValue = "0") val readPages: Int = 0
)

/**
 * One page of a read document (schema 31, R4). [source] is TEXT_LAYER or OCR. [boxesJson] holds
 * word boxes when the source gives them, so a finding can be highlighted on the page.
 */
@Entity(tableName = "document_pages", primaryKeys = ["itemId", "page"])
data class DocumentPageEntity(
    val itemId: String,
    val page: Int,
    val text: String,
    val source: String,
    val confidence: Double? = null,
    val boxesJson: String? = null
)

/** Full-text index over document pages; kept in step with [DocumentPageEntity] by triggers. */
@Fts4(contentEntity = DocumentPageEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "document_pages_fts")
data class DocumentPageFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int,
    val text: String
)

@Dao
interface DocumentPageDao {
    @Upsert suspend fun upsertPages(pages: List<DocumentPageEntity>)

    @Query("SELECT * FROM document_pages WHERE itemId = :itemId ORDER BY page")
    fun observeForItem(itemId: String): Flow<List<DocumentPageEntity>>

    @Query("SELECT * FROM document_pages WHERE itemId = :itemId ORDER BY page")
    suspend fun forItem(itemId: String): List<DocumentPageEntity>

    @Query("SELECT * FROM document_pages WHERE itemId = :itemId AND page = :page LIMIT 1")
    suspend fun page(itemId: String, page: Int): DocumentPageEntity?

    @Query("DELETE FROM document_pages WHERE itemId = :itemId")
    suspend fun deleteForItem(itemId: String)
}

@Dao
interface InboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: InboxItemEntity)
    @Update suspend fun update(item: InboxItemEntity)

    @Query("SELECT * FROM inbox_items WHERE id = :id")
    suspend fun get(id: String): InboxItemEntity?

    /** Schema 31: the item as it changes, for document reading progress. */
    @Query("SELECT * FROM inbox_items WHERE id = :id")
    fun observeById(id: String): Flow<InboxItemEntity?>

    /** Schema 31: one targeted update so a read never overwrites columns other code changes meanwhile. A null [text] keeps the current text. */
    @Query("UPDATE inbox_items SET readStatus = :status, pageCount = :pageCount, readPages = :readPages, text = COALESCE(:text, text) WHERE id = :id")
    suspend fun updateReadState(id: String, status: String, pageCount: Int?, readPages: Int, text: String?)

    @Query("SELECT * FROM inbox_items WHERE status IN ('NEW', 'PROPOSED') ORDER BY createdAt DESC")
    fun observeOpen(): Flow<List<InboxItemEntity>>

    @Query("SELECT * FROM inbox_items WHERE status = 'FILED' ORDER BY COALESCE(processedAt, createdAt) DESC LIMIT 50")
    fun observeFiled(): Flow<List<InboxItemEntity>>

    @Query("SELECT COUNT(*) FROM inbox_items WHERE status IN ('NEW', 'PROPOSED')")
    fun observeOpenCount(): Flow<Int>

    @Query("SELECT * FROM inbox_items WHERE status IN ('NEW', 'PROPOSED') ORDER BY createdAt DESC")
    suspend fun open(): List<InboxItemEntity>

    @Query("SELECT * FROM inbox_items ORDER BY createdAt DESC")
    suspend fun all(): List<InboxItemEntity>

    @Query("SELECT * FROM inbox_items WHERE uri = :uri ORDER BY createdAt DESC LIMIT 1")
    suspend fun getByUri(uri: String): InboxItemEntity?

    @Query("SELECT * FROM inbox_items WHERE meetingId = :meetingId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getByMeetingId(meetingId: String): InboxItemEntity?

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
