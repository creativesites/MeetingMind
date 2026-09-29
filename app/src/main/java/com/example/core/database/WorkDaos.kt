package com.example.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Every read joins the owner's current name, so a speaker renamed in the transcript or a person
 * renamed on their page shows the new name everywhere at once (PLAN_PROFESSIONAL.md §5.5). Room
 * re-emits these flows when `speakers` or `people` change, not only `items`.
 */
private const val ITEM_WITH_OWNER = """
    SELECT items.*,
        COALESCE(p.name, sp.name, NULLIF(s.customName, ''), items.ownerName) AS ownerDisplay,
        COALESCE(p.isSelf, sp.isSelf) AS ownerIsSelf
    FROM items
    LEFT JOIN speakers s ON s.id = items.ownerSpeakerId
    LEFT JOIN people sp ON sp.id = s.personId
    LEFT JOIN people p ON p.id = items.ownerPersonId
"""

@Dao
interface ItemDao {
    @Query("$ITEM_WITH_OWNER WHERE items.meetingId = :meetingId ORDER BY items.createdAt, items.id")
    fun observeForMeeting(meetingId: String): Flow<List<ItemWithOwner>>

    @Query("$ITEM_WITH_OWNER WHERE items.meetingId = :meetingId ORDER BY items.createdAt, items.id")
    suspend fun getForMeeting(meetingId: String): List<ItemWithOwner>

    @Query("$ITEM_WITH_OWNER WHERE items.meetingId = :meetingId AND items.kind = :kind ORDER BY items.createdAt, items.id")
    fun observeForMeeting(meetingId: String, kind: String): Flow<List<ItemWithOwner>>

    @Query("$ITEM_WITH_OWNER WHERE items.meetingId = :meetingId AND items.kind = :kind ORDER BY items.createdAt, items.id")
    suspend fun getForMeeting(meetingId: String, kind: String): List<ItemWithOwner>

    @Query("$ITEM_WITH_OWNER WHERE items.noteId = :noteId ORDER BY items.createdAt, items.id")
    fun observeForNote(noteId: String): Flow<List<ItemWithOwner>>

    @Query("$ITEM_WITH_OWNER WHERE items.kind = :kind ORDER BY items.createdAt DESC")
    fun observeByKind(kind: String): Flow<List<ItemWithOwner>>

    /** Everything still open, across all recordings and notes: the Work Home and Work tab. */
    @Query("$ITEM_WITH_OWNER WHERE items.status = 'OPEN' ORDER BY items.dueAt IS NULL, items.dueAt, items.createdAt DESC")
    fun observeOpen(): Flow<List<ItemWithOwner>>

    @Query("$ITEM_WITH_OWNER WHERE items.status = 'OPEN' ORDER BY items.dueAt IS NULL, items.dueAt, items.createdAt DESC")
    suspend fun getOpen(): List<ItemWithOwner>

    /** A person's items, or an organisation's: its own and its people's. */
    @Query("$ITEM_WITH_OWNER WHERE items.ownerPersonId = :personId OR s.personId = :personId OR p.orgId = :personId OR sp.orgId = :personId ORDER BY items.createdAt DESC")
    fun observeForPerson(personId: String): Flow<List<ItemWithOwner>>

    @Query("$ITEM_WITH_OWNER WHERE items.projectId = :projectId ORDER BY items.createdAt DESC")
    fun observeForProject(projectId: String): Flow<List<ItemWithOwner>>

    /** Tasks with a due day in [from, to): the timeline's task layer. */
    @Query("$ITEM_WITH_OWNER WHERE items.kind = 'TASK' AND items.dueAt >= :from AND items.dueAt < :to ORDER BY items.dueAt")
    suspend fun getDueBetween(from: Long, to: Long): List<ItemWithOwner>

    @Query("SELECT * FROM items WHERE kind = 'TASK' AND status = 'OPEN' AND meetingId IN (:meetingIds)")
    suspend fun getOpenTasksForMeetings(meetingIds: List<String>): List<ItemEntity>

    /** Recordings whose findings the person hasn't reviewed yet: the Home "To review" list. */
    @Query("SELECT DISTINCT meetingId FROM items WHERE reviewed = 0 AND meetingId IS NOT NULL")
    fun observeUnreviewedMeetingIds(): Flow<List<String>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: String): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<ItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ItemEntity)

    @Update
    suspend fun update(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Clears what processing wrote so a re-run starts clean. Items the person wrote or marked are
     * theirs and survive reprocessing.
     */
    @Query("DELETE FROM items WHERE meetingId = :meetingId AND source = 'AI'")
    suspend fun deleteExtractedForMeeting(meetingId: String)

    @Query("UPDATE items SET reviewed = 1, updatedAt = :now WHERE meetingId = :meetingId")
    suspend fun markReviewed(meetingId: String, now: Long)

    @Query("UPDATE items SET noteId = :noteId, projectId = :projectId WHERE meetingId = :meetingId")
    suspend fun attachToNote(meetingId: String, noteId: String?, projectId: String?)

    @Query("UPDATE items SET projectId = :projectId WHERE noteId = :noteId")
    suspend fun setProjectForNote(noteId: String, projectId: String?)

    @Query("SELECT * FROM items WHERE meetingId = :meetingId")
    suspend fun getRawForMeeting(meetingId: String): List<ItemEntity>
}

@Dao
interface PeopleDao {
    @Query("SELECT * FROM people WHERE kind = :kind ORDER BY COALESCE(lastSeenAt, updatedAt) DESC")
    fun observeByKind(kind: String): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<PersonEntity>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getById(id: String): PersonEntity?

    @Query("SELECT * FROM people WHERE id = :id")
    fun observeById(id: String): Flow<PersonEntity?>

    @Query("SELECT * FROM people WHERE isSelf = 1 LIMIT 1")
    suspend fun getSelf(): PersonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(person: PersonEntity)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM speakers WHERE personId = :personId")
    suspend fun getSpeakersFor(personId: String): List<SpeakerEntity>

    @Query("UPDATE speakers SET personId = :personId WHERE id = :speakerId")
    suspend fun linkSpeaker(speakerId: String, personId: String?)

    @Query("UPDATE speakers SET personId = :toId WHERE personId = :fromId")
    suspend fun moveSpeakers(fromId: String, toId: String)

    @Query("UPDATE items SET ownerPersonId = :toId WHERE ownerPersonId = :fromId")
    suspend fun moveItems(fromId: String, toId: String)

    @Query("UPDATE people SET orgId = :toId WHERE orgId = :fromId")
    suspend fun moveMembers(fromId: String, toId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addNotePerson(ref: NotePersonCrossRef)

    @Query("INSERT OR IGNORE INTO note_people (noteId, personId, role, createdAt) SELECT noteId, :toId, role, createdAt FROM note_people WHERE personId = :fromId")
    suspend fun copyNoteLinks(fromId: String, toId: String)

    @Query("SELECT * FROM note_people WHERE noteId = :noteId")
    suspend fun getNoteLinks(noteId: String): List<NotePersonCrossRef>

    /** Notes a person is in; for an organisation, notes any of its people are in. */
    @Query("SELECT DISTINCT noteId FROM note_people WHERE personId = :personId OR personId IN (SELECT id FROM people WHERE orgId = :personId)")
    fun observeNoteIdsFor(personId: String): Flow<List<String>>

    @Query("SELECT DISTINCT noteId FROM note_people WHERE personId IN (:personIds)")
    suspend fun getNoteIdsFor(personIds: List<String>): List<String>

    @Query("SELECT * FROM people WHERE orgId = :orgId ORDER BY name COLLATE NOCASE")
    fun observeMembers(orgId: String): Flow<List<PersonEntity>>
}
