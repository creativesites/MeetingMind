package com.craftflowtechnologies.meetingmind.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * The Work notes list's scope and optional filters (W-1), shared by the keyset queries below. Same scope as
 * [WorkDao.observeWorkNotes]. A filter is off when its flag is 0 / its id null / its bound 0:
 * kindMode 0 = any, 1 = workflow IN kindTypes ("Meetings"), 2 = NOT IN ("My notes").
 */
private const val WORK_NOTE_SCOPE = """n.deletedAt IS NULL AND n.isDraft = 0 AND n.archivedAt IS NULL
    AND (n.workflow IN (:types) OR n.notebookId IN (SELECT id FROM notebooks WHERE kind = 'PROJECT' OR space = 'WORK'))"""
private const val WORK_NOTE_FILTERS = """AND (:kindMode = 0 OR (:kindMode = 1 AND n.workflow IN (:kindTypes)) OR (:kindMode = 2 AND n.workflow NOT IN (:kindTypes)))
    AND (:notebookId IS NULL OR n.notebookId = :notebookId)
    AND n.createdAt >= :createdSince
    AND (:hasTasks = 0 OR EXISTS (SELECT 1 FROM tasks t WHERE t.deletedAt IS NULL AND t.doneAt IS NULL AND t.noteId = n.id)
         OR EXISTS (SELECT 1 FROM tasks t JOIN meetings tm ON tm.id = t.meetingId WHERE t.deletedAt IS NULL AND t.doneAt IS NULL AND tm.noteId = n.id))
    AND (:hasRecording = 0 OR EXISTS (SELECT 1 FROM meetings rm WHERE rm.noteId = n.id))"""
private const val WORK_OPEN_TASK_SCOPE = """t.deletedAt IS NULL AND t.doneAt IS NULL
    AND (t.space = 'WORK' OR t.meetingId IN (SELECT id FROM meetings WHERE recordingType IN (:types)))"""

/** A decision or question with the recording it came from, for the logs across all work. */
data class FindingRow(
    val id: String,
    val meetingId: String,
    val text: String,
    val detail: String?,
    val answer: String?,
    val resolved: Boolean,
    val sourceSegmentIdsJson: String,
    val meetingTitle: String,
    val createdAt: Long,
    val noteId: String?,
    val recordingType: String
)

/** A meeting's id and title only, for lines like "from Acme review". */
data class MeetingTitle(val id: String, val title: String)

/** A person's id and name only. */
data class PersonName(val id: String, val name: String)

/**
 * The queries the Work space needs across recordings, people, tasks and projects
 * (docs/PLAN_PROFESSIONAL.md §5). Kept apart from the per-table DAOs so those stay as they were.
 */
@Dao
interface WorkDao {
    // Recordings

    @Query("SELECT * FROM meetings ORDER BY createdAt DESC")
    suspend fun allMeetings(): List<MeetingEntity>

    /** Finished recordings made in [from]..[to], newest first; the caller narrows to work types. */
    @Query("SELECT * FROM meetings WHERE status = 'READY' AND createdAt BETWEEN :from AND :to ORDER BY createdAt DESC")
    suspend fun readyMeetingsBetween(from: Long, to: Long): List<MeetingEntity>

    /** Titles of the meetings that a live task or an item points at: all a task line needs. */
    @Query("SELECT id, title FROM meetings WHERE id IN (SELECT meetingId FROM tasks WHERE deletedAt IS NULL AND meetingId IS NOT NULL) OR id IN (SELECT meetingId FROM items WHERE meetingId IS NOT NULL)")
    fun observeReferencedMeetingTitles(): Flow<List<MeetingTitle>>

    /** Titles of the meetings that recorded risks came from. */
    @Query("SELECT id, title FROM meetings WHERE id IN (SELECT meetingId FROM items WHERE kind = 'RISK' AND meetingId IS NOT NULL)")
    suspend fun riskMeetingTitles(): List<MeetingTitle>

    /** Finished recordings whose findings haven't been through a Wrap-up. */
    @Query("SELECT * FROM meetings WHERE status = 'READY' AND reviewedAt IS NULL ORDER BY createdAt DESC")
    fun observeUnreviewed(): Flow<List<MeetingEntity>>

    @Query("UPDATE meetings SET reviewedAt = :at WHERE id = :meetingId")
    suspend fun setReviewed(meetingId: String, at: Long?)

    @Query("SELECT * FROM meetings WHERE recordingType IN (:types) ORDER BY createdAt DESC LIMIT :limit")
    fun observeByTypes(types: List<String>, limit: Int): Flow<List<MeetingEntity>>

    // Findings

    @Query("SELECT * FROM follow_ups WHERE meetingId = :meetingId")
    suspend fun followUpsFor(meetingId: String): List<FollowUpEntity>

    @Update suspend fun updateDecision(d: DecisionEntity)
    @Update suspend fun updateFollowUp(f: FollowUpEntity)
    @Query("DELETE FROM decisions WHERE id = :id") suspend fun deleteDecision(id: String)
    @Query("DELETE FROM questions WHERE id = :id") suspend fun deleteQuestion(id: String)
    @Query("DELETE FROM follow_ups WHERE id = :id") suspend fun deleteFollowUp(id: String)

    @Query(
        """SELECT d.id, d.meetingId, d.text, d.type AS detail, NULL AS answer, 0 AS resolved, d.sourceSegmentIdsJson,
                  m.title AS meetingTitle, m.createdAt, m.noteId, m.recordingType
           FROM decisions d JOIN meetings m ON m.id = d.meetingId
           WHERE m.recordingType IN (:types) AND d.type = 'DECISION'
           ORDER BY m.createdAt DESC LIMIT :limit"""
    )
    fun observeDecisions(types: List<String>, limit: Int): Flow<List<FindingRow>>

    @Query(
        """SELECT q.id, q.meetingId, q.text, NULL AS detail, q.answer, q.resolved, q.sourceSegmentIdsJson,
                  m.title AS meetingTitle, m.createdAt, m.noteId, m.recordingType
           FROM questions q JOIN meetings m ON m.id = q.meetingId
           WHERE m.recordingType IN (:types) AND q.resolved = 0 AND m.reviewedAt IS NOT NULL
           ORDER BY m.createdAt DESC LIMIT :limit"""
    )
    fun observeOpenQuestions(types: List<String>, limit: Int): Flow<List<FindingRow>>

    @Query("SELECT d.* FROM decisions d JOIN meetings m ON m.id = d.meetingId JOIN note_people np ON np.noteId = m.noteId WHERE np.personId = :personId AND d.type = 'DECISION' ORDER BY m.createdAt DESC")
    fun observeDecisionsWith(personId: String): Flow<List<DecisionEntity>>

    @Query("SELECT d.* FROM decisions d JOIN meetings m ON m.id = d.meetingId JOIN notes n ON n.id = m.noteId WHERE n.notebookId = :notebookId AND d.type = 'DECISION' ORDER BY m.createdAt DESC")
    fun observeDecisionsIn(notebookId: String): Flow<List<DecisionEntity>>

    @Query("SELECT q.* FROM questions q JOIN meetings m ON m.id = q.meetingId JOIN notes n ON n.id = m.noteId WHERE n.notebookId = :notebookId AND q.resolved = 0 ORDER BY m.createdAt DESC")
    fun observeQuestionsIn(notebookId: String): Flow<List<QuestionEntity>>

    // Chat and AI results, for renames

    @Query("SELECT * FROM chat_messages WHERE meetingId = :meetingId")
    suspend fun chatFor(meetingId: String): List<ChatMessageEntity>

    @Query("UPDATE chat_messages SET content = :content WHERE id = :id")
    suspend fun setChatContent(id: String, content: String)

    @Query("SELECT * FROM ai_jobs WHERE meetingId = :meetingId")
    suspend fun aiJobsFor(meetingId: String): List<AiJobEntity>

    // Speakers and people

    @Query("UPDATE speakers SET personId = :personId WHERE id = :speakerId")
    suspend fun linkSpeaker(speakerId: String, personId: String?)

    @Query("SELECT * FROM speakers WHERE personId = :personId")
    suspend fun speakersFor(personId: String): List<SpeakerEntity>

    @Query("SELECT * FROM speakers WHERE id = :id")
    suspend fun speaker(id: String): SpeakerEntity?

    @Query("UPDATE speakers SET personId = :toId WHERE personId = :fromId")
    suspend fun moveSpeakers(fromId: String, toId: String)

    @Query("UPDATE tasks SET personId = :toId WHERE personId = :fromId")
    suspend fun moveTasks(fromId: String, toId: String)

    @Query("UPDATE people SET orgId = :toId WHERE orgId = :fromId")
    suspend fun moveMembers(fromId: String, toId: String)

    @Query("INSERT OR IGNORE INTO note_people (noteId, personId) SELECT noteId, :toId FROM note_people WHERE personId = :fromId")
    suspend fun copyNoteLinks(fromId: String, toId: String)

    @Query("SELECT * FROM people WHERE deletedAt IS NULL")
    suspend fun allPeople(): List<PersonEntity>

    /** Everyone but the app's own user. */
    @Query("SELECT * FROM people WHERE deletedAt IS NULL AND isSelf = 0")
    suspend fun otherPeople(): List<PersonEntity>

    @Query("SELECT id, name FROM people WHERE deletedAt IS NULL")
    suspend fun peopleNames(): List<PersonName>

    @Query("SELECT * FROM people WHERE deletedAt IS NULL AND isSelf = 1 LIMIT 1")
    suspend fun self(): PersonEntity?

    @Query("SELECT * FROM people WHERE id = :id")
    fun observePerson(id: String): Flow<PersonEntity?>

    /** People met at work: marked so, in a work note, or in a work recording. */
    @Query(
        """SELECT * FROM people WHERE deletedAt IS NULL AND isSelf = 0 AND kind = :kind AND (space = 'WORK'
             OR id IN (SELECT np.personId FROM note_people np JOIN notes n ON n.id = np.noteId WHERE n.workflow IN (:types))
             OR id IN (SELECT personId FROM speakers WHERE personId IS NOT NULL))
           ORDER BY COALESCE(lastSeenAt, updatedAt) DESC"""
    )
    fun observeWorkPeople(kind: String, types: List<String>): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE deletedAt IS NULL AND orgId = :orgId ORDER BY name COLLATE NOCASE")
    fun observeMembers(orgId: String): Flow<List<PersonEntity>>

    @Query("SELECT noteId FROM note_people WHERE personId = :personId")
    suspend fun noteIdsFor(personId: String): List<String>

    @Query("SELECT personId FROM note_people WHERE noteId = :noteId")
    suspend fun peopleIdsFor(noteId: String): List<String>

    // Tasks

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND (space = 'WORK' OR meetingId IN (SELECT id FROM meetings WHERE recordingType IN (:types))) ORDER BY doneAt IS NOT NULL, dueAt IS NULL, dueAt, createdAt DESC")
    fun observeWorkTasks(types: List<String>): Flow<List<TaskEntity>>

    /** The most pressing open work tasks (soonest due first), capped: Home and the Work space show a handful. */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND doneAt IS NULL AND (space = 'WORK' OR meetingId IN (SELECT id FROM meetings WHERE recordingType IN (:types))) ORDER BY dueAt IS NULL, dueAt, createdAt DESC LIMIT :limit")
    fun observeOpenWorkTasks(types: List<String>, limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND doneAt IS NULL AND waitingOn = 0 AND (space = 'WORK' OR meetingId IN (SELECT id FROM meetings WHERE recordingType IN (:types)))")
    fun observeOpenMyWorkTaskCount(types: List<String>): Flow<Int>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND meetingId = :meetingId")
    suspend fun tasksForMeeting(meetingId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND noteId IN (SELECT id FROM notes WHERE notebookId = :notebookId) ORDER BY doneAt IS NOT NULL, dueAt IS NULL, dueAt")
    fun observeTasksIn(notebookId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND (personId = :personId OR personId IN (SELECT id FROM people WHERE orgId = :personId)) ORDER BY doneAt IS NOT NULL, dueAt IS NULL, dueAt")
    fun observeTasksWith(personId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND doneAt IS NULL AND (space = 'WORK' OR meetingId IN (SELECT id FROM meetings WHERE recordingType IN (:types)))")
    suspend fun openWorkTasks(types: List<String>): List<TaskEntity>

    // Meetings around a person, organisation or project

    @Query("SELECT m.* FROM meetings m JOIN note_people np ON np.noteId = m.noteId WHERE np.personId IN (:ids) ORDER BY m.createdAt DESC")
    suspend fun meetingsWithPeople(ids: List<String>): List<MeetingEntity>

    @Query("SELECT m.* FROM meetings m JOIN notes n ON n.id = m.noteId WHERE n.notebookId = :notebookId ORDER BY m.createdAt DESC")
    suspend fun meetingsInProject(notebookId: String): List<MeetingEntity>

    @Query("SELECT id FROM notes WHERE deletedAt IS NULL AND notebookId = :notebookId")
    suspend fun noteIdsInProject(notebookId: String): List<String>

    // Projects and work notes

    @Query("SELECT * FROM notebooks WHERE deletedAt IS NULL AND archivedAt IS NULL AND (kind = 'PROJECT' OR space = 'WORK') ORDER BY kind = 'PROJECT' DESC, updatedAt DESC")
    fun observeProjects(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isDraft = 0 AND archivedAt IS NULL AND (workflow IN (:types) OR notebookId IN (SELECT id FROM notebooks WHERE kind = 'PROJECT' OR space = 'WORK')) ORDER BY COALESCE(eventDate, updatedAt) DESC LIMIT :limit")
    fun observeWorkNotes(types: List<String>, limit: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isDraft = 0 AND notebookId = :notebookId ORDER BY COALESCE(eventDate, updatedAt) DESC")
    fun observeNotesIn(notebookId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND id IN (SELECT noteId FROM note_people WHERE personId = :personId OR personId IN (SELECT id FROM people WHERE orgId = :personId)) ORDER BY COALESCE(eventDate, updatedAt) DESC")
    fun observeNotesWith(personId: String): Flow<List<NoteEntity>>

    @Query("SELECT COUNT(*) FROM notes WHERE deletedAt IS NULL AND notebookId = :notebookId")
    fun observeNoteCount(notebookId: String): Flow<Int>

    /** Every notebook's note count in one query (was one query per project). */
    @Query("SELECT notebookId AS notebookId, COUNT(*) AS count FROM notes WHERE deletedAt IS NULL AND notebookId IS NOT NULL GROUP BY notebookId")
    fun observeNoteCountsByNotebook(): Flow<List<NotebookNoteCount>>

    /** Open tasks per project (tasks on notes filed in it), for the Work page's project cards. */
    @Query("SELECT n.notebookId AS notebookId, COUNT(*) AS count FROM tasks t JOIN notes n ON n.id = t.noteId WHERE t.deletedAt IS NULL AND t.doneAt IS NULL AND n.notebookId IS NOT NULL GROUP BY n.notebookId")
    fun observeOpenTaskCountsByNotebook(): Flow<List<NotebookNoteCount>>

    /** Last edit per notebook, in one grouped query. */
    @Query("SELECT notebookId AS notebookId, MAX(updatedAt) AS lastAt FROM notes WHERE deletedAt IS NULL AND notebookId IS NOT NULL GROUP BY notebookId")
    fun observeNotebookActivity(): Flow<List<NotebookActivity>>

    // ---- Indicators for one page of notes (bounded by the page's ids).

    @Query("SELECT noteId AS noteId, status AS status FROM meetings WHERE noteId IN (:ids)")
    suspend fun meetingStatusesForNotes(ids: List<String>): List<NoteMeetingStatus>

    @Query("SELECT noteId AS noteId, COUNT(*) AS count FROM tasks WHERE deletedAt IS NULL AND doneAt IS NULL AND noteId IN (:ids) GROUP BY noteId")
    suspend fun openTaskCountsForNotes(ids: List<String>): List<NoteTaskCount>

    @Query("""SELECT m.noteId AS noteId, COUNT(*) AS count FROM tasks t JOIN meetings m ON m.id = t.meetingId
        WHERE t.deletedAt IS NULL AND t.doneAt IS NULL AND (t.noteId IS NULL OR t.noteId != m.noteId) AND m.noteId IN (:ids) GROUP BY m.noteId""")
    suspend fun openMeetingTaskCountsForNotes(ids: List<String>): List<NoteTaskCount>

    // ---- Keyset pages (W-1). Each is a bounded LIMIT query; pass the last row's key + id as the cursor, or
    // ---- Long.MAX_VALUE / "\uFFFF" (dates, updatedAt) for the first page. Ties on the key fall back to id.

    /** Work notes, most recently edited first: ORDER BY updatedAt DESC, id DESC. Uses the notes(updatedAt) index. */
    @Query("""SELECT n.* FROM notes n WHERE $WORK_NOTE_SCOPE $WORK_NOTE_FILTERS
        AND (n.updatedAt < :sortKey OR (n.updatedAt = :sortKey AND n.id < :id))
        ORDER BY n.updatedAt DESC, n.id DESC LIMIT :limit""")
    suspend fun workNotesByUpdated(
        types: List<String>, kindMode: Int, kindTypes: List<String>, notebookId: String?, createdSince: Long,
        hasTasks: Int, hasRecording: Int, sortKey: Long, id: String, limit: Int
    ): List<NoteEntity>

    /** By meeting date, newest first; notes with no event date sort last (key 0): ORDER BY COALESCE(eventDate, 0) DESC, id DESC. */
    @Query("""SELECT n.* FROM notes n WHERE $WORK_NOTE_SCOPE $WORK_NOTE_FILTERS
        AND (COALESCE(n.eventDate, 0) < :sortKey OR (COALESCE(n.eventDate, 0) = :sortKey AND n.id < :id))
        ORDER BY COALESCE(n.eventDate, 0) DESC, n.id DESC LIMIT :limit""")
    suspend fun workNotesByEventDate(
        types: List<String>, kindMode: Int, kindTypes: List<String>, notebookId: String?, createdSince: Long,
        hasTasks: Int, hasRecording: Int, sortKey: Long, id: String, limit: Int
    ): List<NoteEntity>

    /** By title A-Z, case-insensitive: ORDER BY title COLLATE NOCASE, id. [hasCursor] 0 = first page. The cursor key is the raw title. */
    @Query("""SELECT n.* FROM notes n WHERE $WORK_NOTE_SCOPE $WORK_NOTE_FILTERS
        AND (:hasCursor = 0 OR n.title COLLATE NOCASE > :sortKey COLLATE NOCASE
             OR (n.title COLLATE NOCASE = :sortKey COLLATE NOCASE AND n.id > :id))
        ORDER BY n.title COLLATE NOCASE ASC, n.id ASC LIMIT :limit""")
    suspend fun workNotesByTitle(
        types: List<String>, kindMode: Int, kindTypes: List<String>, notebookId: String?, createdSince: Long,
        hasTasks: Int, hasRecording: Int, hasCursor: Int, sortKey: String, id: String, limit: Int
    ): List<NoteEntity>

    /**
     * Open work tasks, soonest due first, undated last: ORDER BY COALESCE(dueAt, Long.MAX), id. [waiting] 0 = mine,
     * 1 = waiting on someone (tasks.waitingOn). First page: sortKey Long.MIN_VALUE, id "".
     */
    @Query("""SELECT t.* FROM tasks t WHERE $WORK_OPEN_TASK_SCOPE AND t.waitingOn = :waiting
        AND (COALESCE(t.dueAt, 9223372036854775807) > :sortKey OR (COALESCE(t.dueAt, 9223372036854775807) = :sortKey AND t.id > :id))
        ORDER BY COALESCE(t.dueAt, 9223372036854775807), t.id LIMIT :limit""")
    suspend fun openWorkTasksPage(types: List<String>, waiting: Int, sortKey: Long, id: String, limit: Int): List<TaskEntity>

    /**
     * Work-scoped full-text search over notes_fts (the table the global search uses). Title hits rank first, then
     * most recently edited. FTS4 has no stable keyset-able rank, so this alone pages by OFFSET: results are a
     * bounded, short, query-dependent list (the repository caps offset + limit), not the browsable library.
     */
    @Query("""SELECT n.* FROM notes_fts JOIN notes n ON n.rowid = notes_fts.rowid
        WHERE notes_fts MATCH :match AND $WORK_NOTE_SCOPE
        ORDER BY CASE WHEN n.rowid IN (SELECT docid FROM notes_fts WHERE title MATCH :match) THEN 0 ELSE 1 END,
                 n.updatedAt DESC, n.id DESC
        LIMIT :limit OFFSET :offset""")
    suspend fun searchWorkNotes(types: List<String>, match: String, limit: Int, offset: Int): List<NoteEntity>
}

data class NotebookActivity(val notebookId: String, val lastAt: Long)
data class NoteMeetingStatus(val noteId: String, val status: String)
data class NoteTaskCount(val noteId: String, val count: Int)
