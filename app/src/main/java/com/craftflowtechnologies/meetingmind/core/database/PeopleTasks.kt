package com.craftflowtechnologies.meetingmind.core.database

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
    val deletedAt: Long? = null,
    // Schema 17: work (docs/PLAN_PROFESSIONAL.md §5.1). People are built from history — named
    // speakers, calendar guests, names typed — never read from the phone's contacts.
    /** PERSON, or ORG for an organisation (a client, a company). */
    @ColumnInfo(defaultValue = "PERSON") val kind: String = "PERSON",
    /** The organisation this person belongs to. */
    val orgId: String? = null,
    @ColumnInfo(defaultValue = "[]") val emailsJson: String = "[]",
    @ColumnInfo(defaultValue = "[]") val phonesJson: String = "[]",
    /** Earlier names, so "Sarah" and "Sarah Chen" stay one person. */
    @ColumnInfo(defaultValue = "[]") val aliasesJson: String = "[]",
    /** The channel a follow-up last went out on to them: WHATSAPP, EMAIL, SMS. */
    val preferredChannel: String? = null,
    /** The app's own user. At most one. */
    @ColumnInfo(defaultValue = "0") val isSelf: Boolean = false,
    /** Anything involving them stays on this phone. */
    @ColumnInfo(defaultValue = "0") val confidential: Boolean = false,
    val lastSeenAt: Long? = null,
    /** WORK for people met at work; null for everyone else (prayer list, family). */
    val space: String? = null,
    // Schema 18: an organisation's context (docs/PLAN_PROFESSIONAL.md D4.4).
    @ColumnInfo(defaultValue = "[]") val domainsJson: String = "[]",
    @ColumnInfo(defaultValue = "") val description: String = "",
    @ColumnInfo(defaultValue = "[]") val urlsJson: String = "[]",
    val logoPath: String? = null,
    @ColumnInfo(defaultValue = "{}") val propertiesJson: String = "{}"
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.SET_NULL),
        // A task outlives the note it came from: deleting the note keeps the task, without its link.
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["noteId"]), Index(value = ["personId"]), Index(value = ["doneAt", "dueAt"]), Index(value = ["listId"])]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    /** [com.craftflowtechnologies.meetingmind.core.model.TaskKind] name. */
    val kind: String,
    val dueAt: Long?,
    val remindAt: Long?,
    /** [com.craftflowtechnologies.meetingmind.core.model.TaskRepeat] name. */
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
    val deletedAt: Long? = null,
    // Schema 17: work tasks (docs/PLAN_PROFESSIONAL.md §5.2).
    /** Someone else owes this: [personId] (or [ownerSpeakerId]) is who. Listed under Waiting on. */
    @ColumnInfo(defaultValue = "0") val waitingOn: Boolean = false,
    /** The speaker who owes it, until they're named; their current name is always shown. */
    val ownerSpeakerId: String? = null,
    /** The finding in a recording this task was confirmed from, so it's never made twice. */
    val sourceItemId: String? = null,
    /** WORK for work tasks; null otherwise. */
    val space: String? = null,
    // Schema 31: the Tasks manager (docs/design work-vertical-design-spec.md R3).
    /** 1 (urgent) to 4 (none). */
    @ColumnInfo(defaultValue = "4") val priority: Int = 4,
    /** The task this is a subtask of. */
    val parentId: String? = null,
    /** The task list (a notebook of kind TASK_LIST or PROJECT). Null is the Inbox. */
    val listId: String? = null,
    /** A section inside [listId]. */
    val sectionId: String? = null,
    /** Manual order within a list or section. */
    @ColumnInfo(defaultValue = "0") val position: Double = 0.0,
    /** False for a date with no time of day ("Friday"); true when a time was given. */
    @ColumnInfo(defaultValue = "0") val hasTime: Boolean = false,
    /** An RRULE such as FREQ=WEEKLY;BYDAY=MO. [repeat] stays for older code. */
    val repeatRule: String? = null
)

/** A section inside a task list: "This week", "Waiting", "Someday". */
@Entity(tableName = "task_sections", indices = [Index(value = ["listId"])])
data class TaskSectionEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    @ColumnInfo(defaultValue = "0") val position: Double = 0.0
)

/** A label a task can carry: "email", "errand". Labels are plain words, matched without case. */
@Entity(tableName = "task_labels")
data class TaskLabelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String? = null
)

/** Which labels a task carries. */
@Entity(
    tableName = "task_label_links",
    primaryKeys = ["taskId", "labelId"],
    indices = [Index(value = ["labelId"])]
)
data class TaskLabelLinkEntity(
    val taskId: String,
    val labelId: String
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

/** Full-text index over tasks' title and notes (schema 31); kept in step with [TaskEntity] by triggers. */
@Fts4(contentEntity = TaskEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "tasks_fts")
data class TaskFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int,
    val title: String,
    val notes: String
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

    @Query("SELECT n.* FROM notes n JOIN note_people np ON np.noteId = n.id WHERE np.personId = :personId AND n.deletedAt IS NULL ORDER BY n.updatedAt DESC")
    suspend fun notesForOnce(personId: String): List<NoteEntity>

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

    // ---- Schema 31: the Tasks manager ----

    /** Task lists are notebooks of kind PROJECT (work) or TASK_LIST (the Tasks app). */
    @Query("SELECT * FROM notebooks WHERE deletedAt IS NULL AND archivedAt IS NULL AND kind IN ('PROJECT', 'TASK_LIST') ORDER BY kind = 'TASK_LIST' DESC, sortOrder, name COLLATE NOCASE")
    fun observeLists(): Flow<List<NotebookEntity>>

    @Query("SELECT space FROM notebooks WHERE id = :listId LIMIT 1")
    suspend fun spaceOfList(listId: String): String?

    @Query("SELECT nb.space FROM notes n JOIN notebooks nb ON nb.id = n.notebookId WHERE n.id = :noteId LIMIT 1")
    suspend fun spaceForNote(noteId: String): String?

    @Query("SELECT * FROM tasks WHERE listId = :listId AND deletedAt IS NULL ORDER BY doneAt IS NOT NULL, position, dueAt IS NULL, dueAt")
    fun observeInList(listId: String): Flow<List<TaskEntity>>

    /** Open tasks due today or earlier (overdue included). */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND doneAt IS NULL AND dueAt IS NOT NULL AND dueAt < :endOfToday ORDER BY priority, dueAt")
    fun observeDueBefore(endOfToday: Long): Flow<List<TaskEntity>>

    /** Open tasks due in [fromMs, untilMs). */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND doneAt IS NULL AND dueAt >= :fromMs AND dueAt < :untilMs ORDER BY dueAt, priority")
    fun observeDueBetween(fromMs: Long, untilMs: Long): Flow<List<TaskEntity>>

    /** Open tasks with no list: the Inbox of unsorted quick adds. */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND doneAt IS NULL AND listId IS NULL ORDER BY createdAt DESC")
    fun observeInbox(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND doneAt IS NULL AND priority = 1 ORDER BY dueAt IS NULL, dueAt, createdAt DESC")
    fun observePriority1(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND doneAt IS NOT NULL ORDER BY doneAt DESC LIMIT :limit")
    fun observeCompleted(limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND doneAt IS NOT NULL ORDER BY doneAt DESC LIMIT :limit")
    suspend fun completedOnce(limit: Int): List<TaskEntity>

    /** Full-text search over title and notes, open and done. Callers pass a sanitised MATCH query. */
    @Query(
        """SELECT t.* FROM tasks_fts JOIN tasks t ON t.rowid = tasks_fts.rowid
           WHERE tasks_fts MATCH :query AND t.deletedAt IS NULL
           ORDER BY t.doneAt IS NOT NULL, t.dueAt IS NULL, t.dueAt LIMIT :limit"""
    )
    suspend fun searchTasks(query: String, limit: Int): List<TaskEntity>

    @Query("UPDATE tasks SET priority = :priority, updatedAt = :now WHERE id = :id")
    suspend fun setPriority(id: String, priority: Int, now: Long)

    @Query("UPDATE tasks SET listId = :listId, sectionId = :sectionId, position = :position, updatedAt = :now WHERE id = :id")
    suspend fun move(id: String, listId: String?, sectionId: String?, position: Double, now: Long)

    @Query("UPDATE tasks SET dueAt = :dueAt, hasTime = :hasTime, updatedAt = :now WHERE id = :id")
    suspend fun reschedule(id: String, dueAt: Long?, hasTime: Boolean, now: Long)

    @Query("UPDATE tasks SET position = :position WHERE id = :id")
    suspend fun setTaskPosition(id: String, position: Double)

    @Upsert suspend fun upsertSection(section: TaskSectionEntity)

    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM task_sections WHERE listId = :listId")
    suspend fun nextSectionPosition(listId: String): Double

    @Query("SELECT * FROM task_sections WHERE listId = :listId ORDER BY position, name COLLATE NOCASE")
    fun observeSections(listId: String): Flow<List<TaskSectionEntity>>

    @Query("UPDATE task_sections SET position = :position WHERE id = :id")
    suspend fun setSectionPosition(id: String, position: Double)

    /** Used with [clearSectionOnTasks] in the repository, so deleting a section leaves its tasks unsectioned. */
    @Query("DELETE FROM task_sections WHERE id = :id")
    suspend fun deleteSection(id: String)

    @Query("UPDATE tasks SET sectionId = NULL WHERE sectionId = :sectionId")
    suspend fun clearSectionOnTasks(sectionId: String)

    @Upsert suspend fun upsertLabel(label: TaskLabelEntity)

    @Query("SELECT * FROM task_labels ORDER BY name COLLATE NOCASE")
    fun observeLabels(): Flow<List<TaskLabelEntity>>

    @Query("SELECT * FROM task_labels WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun labelByName(name: String): TaskLabelEntity?

    @Query("SELECT l.* FROM task_labels l JOIN task_label_links k ON k.labelId = l.id WHERE k.taskId = :taskId ORDER BY l.name COLLATE NOCASE")
    suspend fun labelsForTask(taskId: String): List<TaskLabelEntity>

    @Query("DELETE FROM task_label_links WHERE taskId = :taskId")
    suspend fun clearLabelLinks(taskId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkLabel(link: TaskLabelLinkEntity)

    @Query("SELECT COUNT(*) FROM task_label_links WHERE labelId = :labelId")
    suspend fun labelUseCount(labelId: String): Int

    /** Logs a kept day for a practice (schema 32). A second tick on the same day is ignored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPracticeDay(day: PracticeDayEntity): Long
}

data class NoteHit(val id: String, val title: String, val workflow: String, val updatedAt: Long, val snippet: String)
data class SegmentHit(val id: String, val meetingId: String, val startMs: Long, val speakerName: String?, val snippet: String, val meetingTitle: String, val recordingType: String)
/** A page of a read document (schema 31) that matches a search; [page] is 1-based. */
data class DocumentPageHit(val itemId: String, val page: Int, val snippet: String)

@Dao
interface SearchDao {
    @Query(
        """SELECT p.itemId, p.page, snippet(document_pages_fts, '', '', '…', -1, 14) AS snippet
           FROM document_pages_fts JOIN document_pages p ON p.rowid = document_pages_fts.rowid
           WHERE document_pages_fts MATCH :query
           ORDER BY p.itemId, p.page LIMIT :limit"""
    )
    suspend fun documentPages(query: String, limit: Int): List<DocumentPageHit>

    @Query(
        """SELECT n.id, n.title, n.workflow, n.updatedAt, snippet(notes_fts, '', '', '…', -1, 14) AS snippet
           FROM notes_fts JOIN notes n ON n.rowid = notes_fts.rowid
           WHERE notes_fts MATCH :query AND n.deletedAt IS NULL AND n.isDraft = 0
           ORDER BY n.updatedAt DESC LIMIT :limit"""
    )
    suspend fun notes(query: String, limit: Int): List<NoteHit>

    @Query(
        """SELECT s.id, s.meetingId, s.startMs, s.speakerName, snippet(transcript_fts, '', '', '…', -1, 14) AS snippet,
                  m.title AS meetingTitle, m.recordingType
           FROM transcript_fts JOIN transcript_segments s ON s.rowid = transcript_fts.rowid
           JOIN meetings m ON m.id = s.meetingId
           WHERE transcript_fts MATCH :query
           ORDER BY m.createdAt DESC, s.startMs LIMIT :limit"""
    )
    suspend fun segments(query: String, limit: Int): List<SegmentHit>

}
