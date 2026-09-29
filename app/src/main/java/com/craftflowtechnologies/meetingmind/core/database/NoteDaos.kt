package com.craftflowtechnologies.meetingmind.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks WHERE archivedAt IS NULL AND deletedAt IS NULL ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeActive(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun getAll(): List<NotebookEntity>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getById(id: String): NotebookEntity?

    @Query("SELECT * FROM notebooks WHERE space = :space AND archivedAt IS NULL AND deletedAt IS NULL ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun getBySpace(space: String): List<NotebookEntity>

    @Upsert
    suspend fun upsert(notebook: NotebookEntity)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM notebooks WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashed(): Flow<List<NotebookEntity>>

    @Query("UPDATE notebooks SET deletedAt = :at WHERE id = :id")
    suspend fun setDeleted(id: String, at: Long?)

    @Query("SELECT id FROM notebooks WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun trashedBefore(before: Long): List<String>

    /** Note counts per notebook, for the library's notebook list. */
    @Query("SELECT notebookId AS notebookId, COUNT(*) AS count FROM notes WHERE archivedAt IS NULL AND isDraft = 0 AND deletedAt IS NULL AND notebookId IS NOT NULL GROUP BY notebookId")
    fun observeNoteCounts(): Flow<List<NotebookNoteCount>>
}

data class NotebookNoteCount(val notebookId: String, val count: Int)

data class NameCount(val name: String, val count: Int)

data class NoteTagName(val noteId: String, val name: String)

@Dao
interface NoteDao {
    /** Notes the person wrote (not drafts, not the daily devotional) — for "getting started". */
    @Query("SELECT COUNT(*) FROM notes WHERE archivedAt IS NULL AND isDraft = 0 AND deletedAt IS NULL AND workflow != 'DEVOTIONAL'")
    fun observeWrittenCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT * FROM notes WHERE archivedAt IS NULL AND isDraft = 0 AND deletedAt IS NULL ORDER BY pinned DESC, updatedAt DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE archivedAt IS NULL AND isDraft = 0 AND deletedAt IS NULL AND notebookId = :notebookId ORDER BY pinned DESC, updatedAt DESC")
    fun observeInNotebook(notebookId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE archivedAt IS NULL AND isDraft = 0 AND deletedAt IS NULL AND workflow IN (:workflows) ORDER BY pinned DESC, updatedAt DESC")
    fun observeByWorkflows(workflows: List<String>): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE archivedAt IS NULL AND isDraft = 0 AND deletedAt IS NULL ORDER BY pinned DESC, updatedAt DESC")
    suspend fun getActiveOnce(): List<NoteEntity>

    // ---- trash (PRD_M0 §4.5) ----

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashed(): Flow<List<NoteEntity>>

    @Query("UPDATE notes SET deletedAt = :at WHERE id = :id")
    suspend fun setDeleted(id: String, at: Long?)

    @Query("UPDATE notes SET deletedAt = :at WHERE notebookId = :notebookId AND deletedAt IS NULL")
    suspend fun trashInNotebook(notebookId: String, at: Long)

    @Query("UPDATE notes SET deletedAt = NULL WHERE notebookId = :notebookId AND deletedAt = :at")
    suspend fun restoreInNotebook(notebookId: String, at: Long)

    @Query("SELECT id FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun trashedBefore(before: Long): List<String>

    @Query("SELECT * FROM notes WHERE archivedAt IS NOT NULL AND deletedAt IS NULL ORDER BY archivedAt DESC")
    fun observeArchived(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAll(): List<NoteEntity>

    /** Notes whose day (event date, else created) falls in [from, to) — the timeline's backbone. */
    @Query("SELECT * FROM notes WHERE archivedAt IS NULL AND isDraft = 0 AND deletedAt IS NULL AND COALESCE(eventDate, createdAt) >= :from AND COALESCE(eventDate, createdAt) < :to ORDER BY COALESCE(eventDate, createdAt)")
    suspend fun getNotesBetween(from: Long, to: Long): List<NoteEntity>

    /** Prayer requests answered in [from, to), for the "Answered" milestone. */
    @Query("SELECT * FROM notes WHERE archivedAt IS NULL AND deletedAt IS NULL AND answeredAt IS NOT NULL AND answeredAt >= :from AND answeredAt < :to")
    suspend fun getAnsweredBetween(from: Long, to: Long): List<NoteEntity>

    /** Notes from the same month and day in earlier years ("MM-dd", local time). */
    @Query("SELECT * FROM notes WHERE archivedAt IS NULL AND isDraft = 0 AND deletedAt IS NULL AND COALESCE(eventDate, createdAt) < :before AND strftime('%m-%d', COALESCE(eventDate, createdAt) / 1000, 'unixepoch', 'localtime') = :monthDay ORDER BY COALESCE(eventDate, createdAt) DESC")
    suspend fun getOnThisDay(monthDay: String, before: Long): List<NoteEntity>

    @Query("SELECT * FROM meetings WHERE noteId IN (:noteIds)")
    suspend fun getMeetingsForNotes(noteIds: List<String>): List<MeetingEntity>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND metadataJson LIKE :pattern ESCAPE '\\' LIMIT 1")
    suspend fun findByMetadata(pattern: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE archivedAt IS NULL AND deletedAt IS NULL AND metadataJson LIKE :pattern ESCAPE '\\' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun findAllByMetadata(pattern: String, limit: Int): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE metadataJson LIKE :pattern ESCAPE '\\' AND archivedAt IS NULL AND deletedAt IS NULL ORDER BY createdAt ASC")
    fun observeAllByMetadata(pattern: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND metadataJson LIKE :pattern ESCAPE '\\' ORDER BY createdAt DESC LIMIT 1")
    fun observeByMetadata(pattern: String): Flow<NoteEntity?>

    /** Notes changed since [since], newest first — the devotional's view of "lately". */
    @Query("SELECT * FROM notes WHERE archivedAt IS NULL AND isDraft = 0 AND deletedAt IS NULL AND updatedAt >= :since ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getUpdatedSince(since: Long, limit: Int = 80): List<NoteEntity>

    @Query(
        "SELECT * FROM notes WHERE archivedAt IS NULL AND isDraft = 0 AND deletedAt IS NULL AND " +
            "(title LIKE '%' || :query || '%' OR plainText LIKE '%' || :query || '%') " +
            "ORDER BY pinned DESC, updatedAt DESC LIMIT :limit"
    )
    suspend fun searchText(query: String, limit: Int = 50): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE archivedAt IS NULL AND isDraft = 0 AND deletedAt IS NULL AND workflow = :workflow AND status = :status ORDER BY updatedAt DESC")
    fun observeByWorkflowAndStatus(workflow: String, status: String): Flow<List<NoteEntity>>

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("UPDATE notes SET updatedAt = :updatedAt, plainText = :plainText WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long, plainText: String)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    // ---- blocks ----

    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId ORDER BY position")
    fun observeBlocks(noteId: String): Flow<List<NoteBlockEntity>>

    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId ORDER BY position")
    suspend fun getBlocks(noteId: String): List<NoteBlockEntity>

    /** A task linked to a checklist line was ticked elsewhere. */
    @Query("UPDATE note_blocks SET checked = :checked, updatedAt = :now WHERE id = :blockId")
    suspend fun setBlockChecked(blockId: String, checked: Boolean, now: Long)

    @Upsert
    suspend fun upsertBlocks(blocks: List<NoteBlockEntity>)

    @Query("DELETE FROM note_blocks WHERE noteId = :noteId AND id NOT IN (:keepIds)")
    suspend fun deleteBlocksExcept(noteId: String, keepIds: List<String>)

    @Query("DELETE FROM note_blocks WHERE noteId = :noteId")
    suspend fun deleteAllBlocks(noteId: String)

    /**
     * Replaces a note's blocks in one transaction, so a crash mid-save can never leave half a
     * note. Blocks are upserted by id, so an unchanged block keeps its row.
     */
    @Transaction
    suspend fun replaceBlocks(noteId: String, blocks: List<NoteBlockEntity>) {
        if (blocks.isEmpty()) deleteAllBlocks(noteId) else deleteBlocksExcept(noteId, blocks.map { it.id })
        if (blocks.isNotEmpty()) upsertBlocks(blocks)
    }

    /** Notes whose NOTE_LINK blocks point at [noteId] — the backlinks panel. */
    @Query(
        "SELECT DISTINCT n.* FROM notes n JOIN note_blocks b ON b.noteId = n.id " +
            "WHERE b.type = 'NOTE_LINK' AND b.payloadJson LIKE '%' || :noteId || '%' AND n.id != :noteId AND n.archivedAt IS NULL AND n.deletedAt IS NULL"
    )
    fun observeBacklinks(noteId: String): Flow<List<NoteEntity>>

    // ---- links ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: NoteLinkEntity)

    @Query("SELECT * FROM note_links WHERE fromNoteId = :noteId OR toNoteId = :noteId ORDER BY createdAt")
    fun observeLinks(noteId: String): Flow<List<NoteLinkEntity>>

    @Query("SELECT * FROM note_links WHERE fromNoteId = :noteId OR toNoteId = :noteId ORDER BY createdAt")
    suspend fun getLinks(noteId: String): List<NoteLinkEntity>

    @Query("DELETE FROM note_links WHERE id = :id")
    suspend fun deleteLink(id: String)

    @Query("SELECT * FROM note_links")
    suspend fun getAllLinks(): List<NoteLinkEntity>

    @Query("SELECT nt.noteId AS noteId, t.name AS name FROM note_tags nt JOIN tags t ON t.id = nt.tagId")
    suspend fun getAllNoteTags(): List<NoteTagName>

    // ---- tags ----

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteTag(ref: NoteTagCrossRef)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun deleteNoteTag(noteId: String, tagId: String)

    @Query("SELECT t.* FROM tags t JOIN note_tags nt ON nt.tagId = t.id WHERE nt.noteId = :noteId ORDER BY t.name COLLATE NOCASE")
    fun observeTagsForNote(noteId: String): Flow<List<TagEntity>>

    @Query("SELECT t.* FROM tags t JOIN note_tags nt ON nt.tagId = t.id WHERE nt.noteId = :noteId ORDER BY t.name COLLATE NOCASE")
    suspend fun getTagsForNote(noteId: String): List<TagEntity>

    @Query(
        "SELECT n.* FROM notes n JOIN note_tags nt ON nt.noteId = n.id " +
            "WHERE nt.tagId = :tagId AND n.archivedAt IS NULL AND n.deletedAt IS NULL ORDER BY n.updatedAt DESC"
    )
    fun observeNotesWithTag(tagId: String): Flow<List<NoteEntity>>

    /** How often each tag is used on notes of these workflows — the Faith space's themes. */
    @Query(
        "SELECT t.name AS name, COUNT(*) AS count FROM tags t JOIN note_tags nt ON nt.tagId = t.id " +
            "JOIN notes n ON n.id = nt.noteId WHERE n.workflow IN (:workflows) AND n.archivedAt IS NULL AND n.deletedAt IS NULL " +
            "GROUP BY t.id ORDER BY count DESC LIMIT 30"
    )
    fun observeTagCountsForWorkflows(workflows: List<String>): Flow<List<NameCount>>

    /** Topics processing found in recordings of these workflows, by how many recordings raised them. */
    @Query(
        "SELECT tp.name AS name, COUNT(DISTINCT tp.meetingId) AS count FROM topics tp JOIN meetings m ON m.id = tp.meetingId " +
            "WHERE m.recordingType IN (:workflows) GROUP BY lower(tp.name) ORDER BY count DESC LIMIT 30"
    )
    fun observeTopicCountsForWorkflows(workflows: List<String>): Flow<List<NameCount>>

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tagId FROM note_tags)")
    suspend fun deleteUnusedTags()

    // ---- meetings ----

    @Query("SELECT * FROM meetings WHERE noteId = :noteId ORDER BY createdAt")
    fun observeMeetingsForNote(noteId: String): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE noteId = :noteId ORDER BY createdAt")
    suspend fun getMeetingsForNote(noteId: String): List<MeetingEntity>

    @Query("UPDATE meetings SET noteId = :noteId WHERE id = :meetingId")
    suspend fun attachMeeting(meetingId: String, noteId: String?)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt")
    fun observeForNote(noteId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt")
    suspend fun getForNote(noteId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getById(id: String): AttachmentEntity?

    /** Picture covers for the timeline: every image attachment of these notes, oldest first. */
    @Query("SELECT * FROM attachments WHERE noteId IN (:noteIds) AND kind = 'IMAGE' ORDER BY createdAt")
    suspend fun getImagesForNotes(noteIds: List<String>): List<AttachmentEntity>

    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)

    /** Media across Faith notes, newest first — the Faith "Media" view. */
    @Query(
        "SELECT a.* FROM attachments a JOIN notes n ON n.id = a.noteId " +
            "WHERE n.workflow IN (:workflows) AND n.archivedAt IS NULL AND n.deletedAt IS NULL ORDER BY a.createdAt DESC"
    )
    fun observeForWorkflows(workflows: List<String>): Flow<List<AttachmentEntity>>
}

@Dao
interface ScriptureDao {
    @Query("SELECT * FROM scripture_refs WHERE noteId = :noteId ORDER BY COALESCE(startMs, 0), createdAt")
    fun observeForNote(noteId: String): Flow<List<ScriptureRefEntity>>

    @Query("SELECT * FROM scripture_refs WHERE noteId = :noteId ORDER BY COALESCE(startMs, 0), createdAt")
    suspend fun getForNote(noteId: String): List<ScriptureRefEntity>

    @Query("SELECT * FROM scripture_refs WHERE id = :id")
    suspend fun getById(id: String): ScriptureRefEntity?

    @Upsert
    suspend fun upsert(refs: List<ScriptureRefEntity>)

    @Query("DELETE FROM scripture_refs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM scripture_refs WHERE noteId = :noteId AND origin = :origin")
    suspend fun deleteForNoteWithOrigin(noteId: String, origin: String)

    @Query("SELECT * FROM scripture_refs ORDER BY bookUsfm, chapter, COALESCE(verseStart, 0)")
    fun observeAll(): Flow<List<ScriptureRefEntity>>

    @Query("SELECT * FROM scripture_refs")
    suspend fun getAll(): List<ScriptureRefEntity>

    /** Every reference into one chapter — "notes on this passage". Uses the (book, chapter) index. */
    @Query("SELECT * FROM scripture_refs WHERE bookUsfm = :book AND chapter = :chapter ORDER BY createdAt DESC")
    suspend fun getForChapter(book: String, chapter: Int): List<ScriptureRefEntity>

    @Query("SELECT * FROM scripture_collections ORDER BY name COLLATE NOCASE")
    fun observeCollections(): Flow<List<ScriptureCollectionEntity>>

    @Upsert
    suspend fun upsertCollection(collection: ScriptureCollectionEntity)

    @Query("DELETE FROM scripture_collections WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Query("SELECT * FROM scripture_collection_items WHERE collectionId = :collectionId ORDER BY position")
    fun observeCollectionItems(collectionId: String): Flow<List<ScriptureCollectionItemEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM scripture_collection_items WHERE collectionId = :collectionId")
    suspend fun nextCollectionPosition(collectionId: String): Int

    @Upsert
    suspend fun upsertCollectionItem(item: ScriptureCollectionItemEntity)

    @Query("DELETE FROM scripture_collection_items WHERE id = :id")
    suspend fun deleteCollectionItem(id: String)
}

@Dao
interface NoteAiJobDao {
    @Query("SELECT * FROM note_ai_jobs WHERE id = :id")
    suspend fun getById(id: String): NoteAiJobEntity?

    @Query("SELECT * FROM note_ai_jobs WHERE targetId = :targetId ORDER BY createdAt DESC")
    fun observeForTarget(targetId: String): Flow<List<NoteAiJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: NoteAiJobEntity)

    @Query("DELETE FROM note_ai_jobs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM note_ai_jobs WHERE targetId = :targetId AND status IN ('SUCCEEDED', 'FAILED', 'CANCELLED')")
    suspend fun deleteFinished(targetId: String)
}

/** Version history (PRD_M0 §4.6). Listing reads everything but the snapshot itself. */
@Dao
interface NoteVersionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(version: NoteVersionEntity)

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY createdAt DESC")
    suspend fun forNote(noteId: String): List<NoteVersionEntity>

    @Query("SELECT id, noteId, createdAt, reason, label, title, byteSize FROM note_versions WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun observeSummaries(noteId: String): Flow<List<NoteVersionSummary>>

    @Query("SELECT * FROM note_versions WHERE id = :id")
    suspend fun getById(id: String): NoteVersionEntity?

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(noteId: String): NoteVersionEntity?

    @Query("DELETE FROM note_versions WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("SELECT COALESCE(SUM(byteSize), 0) FROM note_versions")
    suspend fun totalBytes(): Long

    @Query("SELECT id, noteId, createdAt, reason, label, title, byteSize FROM note_versions ORDER BY createdAt ASC")
    suspend fun allSummariesOldestFirst(): List<NoteVersionSummary>
}

data class NoteVersionSummary(
    val id: String,
    val noteId: String,
    val createdAt: Long,
    val reason: String,
    val label: String?,
    val title: String,
    val byteSize: Int
)
