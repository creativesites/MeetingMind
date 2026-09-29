package com.example.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Schema 16 (Faith spec slice E/F): people you pray for or follow up with, tasks with reminders,
 * which notes are about which people, and full-text indexes over notes and transcripts.
 */

@Entity(tableName = "people", indices = [Index(value = ["name"])])
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** "Friend", "Mum", "Small group" — the person's own words, never inferred. */
    val relationship: String?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.SET_NULL),
        // A task outlives the note it came from: deleting the note keeps the task, without its link.
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["noteId"]), Index(value = ["personId"]), Index(value = ["doneAt", "dueAt"])]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    /** [com.example.core.model.TaskKind] name. */
    val kind: String,
    val dueAt: Long?,
    val remindAt: Long?,
    /** [com.example.core.model.TaskRepeat] name. */
    val repeat: String,
    val doneAt: Long?,
    val personId: String?,
    val noteId: String?,
    /** The checklist block the task was made from, so ticking one ticks the other. */
    val blockId: String?,
    /** Where in a recording it was said, for "Apply this" items from a sermon. */
    val meetingId: String?,
    val startMs: Long?,
    /** A reference like "JHN 15:5" kept with a prayer task. */
    val scripture: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "note_people",
    primaryKeys = ["noteId", "personId"],
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["personId"])]
)
data class NotePersonCrossRef(val noteId: String, val personId: String)

/** Full-text index over notes; Room keeps it in step with [NoteEntity] through triggers. */
@Fts4(contentEntity = NoteEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int,
    val title: String,
    val plainText: String
)

/** Full-text index over transcript paragraphs. */
@Fts4(contentEntity = TranscriptSegmentEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "transcript_fts")
data class TranscriptFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int,
    val text: String
)

data class PersonWithCounts(
    val id: String,
    val name: String,
    val relationship: String?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
    val openTasks: Int,
    val noteCount: Int
)

@Dao
interface PeopleDao {
    @Upsert suspend fun upsert(person: PersonEntity)

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getById(id: String): PersonEntity?

    @Query("SELECT * FROM people WHERE deletedAt IS NULL AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): PersonEntity?

    @Query("SELECT * FROM people WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<PersonEntity>

    @Query(
        """SELECT p.id, p.name, p.relationship, p.notes, p.createdAt, p.updatedAt,
            (SELECT COUNT(*) FROM tasks t WHERE t.personId = p.id AND t.doneAt IS NULL AND t.deletedAt IS NULL) AS openTasks,
            (SELECT COUNT(*) FROM note_people np JOIN notes n ON n.id = np.noteId WHERE np.personId = p.id AND n.deletedAt IS NULL) AS noteCount
           FROM people p WHERE p.deletedAt IS NULL ORDER BY p.name COLLATE NOCASE"""
    )
    fun observeWithCounts(): Flow<List<PersonWithCounts>>

    @Query("UPDATE people SET deletedAt = :at WHERE id = :id")
    suspend fun setDeleted(id: String, at: Long?)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(ref: NotePersonCrossRef)

    @Query("DELETE FROM note_people WHERE noteId = :noteId AND personId = :personId")
    suspend fun unlink(noteId: String, personId: String)

    @Query("SELECT p.* FROM people p JOIN note_people np ON np.personId = p.id WHERE np.noteId = :noteId AND p.deletedAt IS NULL ORDER BY p.name")
    fun observeForNote(noteId: String): Flow<List<PersonEntity>>

    @Query("SELECT n.* FROM notes n JOIN note_people np ON np.noteId = n.id WHERE np.personId = :personId AND n.deletedAt IS NULL ORDER BY n.updatedAt DESC")
    fun observeNotesFor(personId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM people")
    suspend fun exportAll(): List<PersonEntity>

    @Query("SELECT * FROM note_people")
    suspend fun exportLinks(): List<NotePersonCrossRef>
}

@Dao
interface TaskDao {
    @Upsert suspend fun upsert(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE blockId = :blockId AND deletedAt IS NULL LIMIT 1")
    suspend fun getByBlock(blockId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY doneAt IS NOT NULL, dueAt IS NULL, dueAt, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND doneAt IS NULL ORDER BY dueAt IS NULL, dueAt, createdAt DESC")
    suspend fun getOpen(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE personId = :personId AND deletedAt IS NULL ORDER BY doneAt IS NOT NULL, dueAt IS NULL, dueAt")
    fun observeForPerson(personId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE noteId = :noteId AND deletedAt IS NULL")
    fun observeForNote(noteId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND doneAt IS NULL AND remindAt IS NOT NULL AND remindAt > :after ORDER BY remindAt")
    suspend fun upcomingReminders(after: Long): List<TaskEntity>

    @Query("UPDATE tasks SET doneAt = :at, updatedAt = :now WHERE id = :id")
    suspend fun setDone(id: String, at: Long?, now: Long)

    @Query("UPDATE tasks SET deletedAt = :at WHERE id = :id")
    suspend fun setDeleted(id: String, at: Long?)

    @Query("SELECT * FROM tasks")
    suspend fun exportAll(): List<TaskEntity>
}

data class NoteHit(val id: String, val title: String, val workflow: String, val updatedAt: Long, val snippet: String)
data class SegmentHit(val id: String, val meetingId: String, val startMs: Long, val speakerName: String?, val snippet: String, val meetingTitle: String, val recordingType: String)

@Dao
interface SearchDao {
    @Query(
        """SELECT n.id, n.title, n.workflow, n.updatedAt, snippet(notes_fts, '[', ']', '…', -1, 12) AS snippet
           FROM notes_fts JOIN notes n ON n.rowid = notes_fts.rowid
           WHERE notes_fts MATCH :query AND n.deletedAt IS NULL AND n.isDraft = 0
           ORDER BY n.updatedAt DESC LIMIT :limit"""
    )
    suspend fun notes(query: String, limit: Int): List<NoteHit>

    @Query(
        """SELECT s.id, s.meetingId, s.startMs, s.speakerName, snippet(transcript_fts, '[', ']', '…', -1, 12) AS snippet,
                  m.title AS meetingTitle, m.recordingType
           FROM transcript_fts JOIN transcript_segments s ON s.rowid = transcript_fts.rowid
           JOIN meetings m ON m.id = s.meetingId
           WHERE transcript_fts MATCH :query
           ORDER BY m.createdAt DESC, s.startMs LIMIT :limit"""
    )
    suspend fun segments(query: String, limit: Int): List<SegmentHit>

}
