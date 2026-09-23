package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks WHERE archivedAt IS NULL ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeActive(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun getAll(): List<NotebookEntity>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getById(id: String): NotebookEntity?

    @Query("SELECT * FROM notebooks WHERE space = :space AND archivedAt IS NULL ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun getBySpace(space: String): List<NotebookEntity>

    @Upsert
    suspend fun upsert(notebook: NotebookEntity)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun delete(id: String)

    /** Note counts per notebook, for the library's notebook list. */
    @Query("SELECT notebookId AS notebookId, COUNT(*) AS count FROM notes WHERE archivedAt IS NULL AND metadataJson NOT LIKE '%\"draft\":\"1\"%' AND notebookId IS NOT NULL GROUP BY notebookId")
    fun observeNoteCounts(): Flow<List<NotebookNoteCount>>
}

data class NotebookNoteCount(val notebookId: String, val count: Int)

data class NameCount(val name: String, val count: Int)

data class NoteTagName(val noteId: String, val name: String)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE archivedAt IS NULL AND metadataJson NOT LIKE '%\"draft\":\"1\"%' ORDER BY pinned DESC, updatedAt DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE archivedAt IS NULL AND metadataJson NOT LIKE '%\"draft\":\"1\"%' AND notebookId = :notebookId ORDER BY pinned DESC, updatedAt DESC")
    fun observeInNotebook(notebookId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE archivedAt IS NULL AND metadataJson NOT LIKE '%\"draft\":\"1\"%' AND workflow IN (:workflows) ORDER BY pinned DESC, updatedAt DESC")
    fun observeByWorkflows(workflows: List<String>): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE archivedAt IS NOT NULL ORDER BY archivedAt DESC")
    fun observeArchived(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE metadataJson LIKE :pattern ESCAPE '\\' LIMIT 1")
    suspend fun findByMetadata(pattern: String): NoteEntity?

    @Query(
        "SELECT * FROM notes WHERE archivedAt IS NULL AND metadataJson NOT LIKE '%\"draft\":\"1\"%' AND " +
            "(title LIKE '%' || :query || '%' OR plainText LIKE '%' || :query || '%') " +
            "ORDER BY pinned DESC, updatedAt DESC LIMIT :limit"
    )
    suspend fun searchText(query: String, limit: Int = 50): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE archivedAt IS NULL AND metadataJson NOT LIKE '%\"draft\":\"1\"%' AND workflow = :workflow AND status = :status ORDER BY updatedAt DESC")
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
            "WHERE b.type = 'NOTE_LINK' AND b.payloadJson LIKE '%' || :noteId || '%' AND n.id != :noteId AND n.archivedAt IS NULL"
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
            "WHERE nt.tagId = :tagId AND n.archivedAt IS NULL ORDER BY n.updatedAt DESC"
    )
    fun observeNotesWithTag(tagId: String): Flow<List<NoteEntity>>

    /** How often each tag is used on notes of these workflows — the Faith space's themes. */
    @Query(
        "SELECT t.name AS name, COUNT(*) AS count FROM tags t JOIN note_tags nt ON nt.tagId = t.id " +
            "JOIN notes n ON n.id = nt.noteId WHERE n.workflow IN (:workflows) AND n.archivedAt IS NULL " +
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

    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)

    /** Media across Faith notes, newest first — the Faith "Media" view. */
    @Query(
        "SELECT a.* FROM attachments a JOIN notes n ON n.id = a.noteId " +
            "WHERE n.workflow IN (:workflows) AND n.archivedAt IS NULL ORDER BY a.createdAt DESC"
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
