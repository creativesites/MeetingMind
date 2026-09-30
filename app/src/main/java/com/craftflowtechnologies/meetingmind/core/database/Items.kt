package com.craftflowtechnologies.meetingmind.core.database

import androidx.room.ColumnInfo
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
 * Schema 18 (docs/PLAN_PROFESSIONAL.md D4, D12): the durable, cross-meeting record. A recording's
 * findings stay in `action_items`, `decisions`, `questions` and `follow_ups`; confirming the
 * Wrap-up promotes them to items, which is what Pulse, context pages and the logs read.
 */
@Entity(
    tableName = "items",
    indices = [
        Index(value = ["kind", "status"]),
        Index(value = ["projectId"]),
        Index(value = ["orgId"]),
        Index(value = ["ownerPersonId"]),
        Index(value = ["counterpartyPersonId"]),
        Index(value = ["dueAt"]),
        // One item per finding, so promoting twice is harmless. Rows with no finding may repeat.
        Index(value = ["sourceFindingId"], unique = true)
    ]
)
data class ItemEntity(
    @PrimaryKey val id: String,
    /** [com.craftflowtechnologies.meetingmind.core.work.ItemKind] name. */
    val kind: String,
    /** [com.craftflowtechnologies.meetingmind.core.work.ItemStatus] name. */
    val status: String,
    val text: String,
    /** A structured value: a date for DEADLINE, a number for METRIC. */
    val value: String? = null,
    /** Who holds it; null means the app's own user. */
    val ownerPersonId: String? = null,
    /** The speaker who holds it, until they're named. */
    val ownerSpeakerId: String? = null,
    /** Commitments: who it is owed to. */
    val counterpartyPersonId: String? = null,
    val projectId: String? = null,
    val orgId: String? = null,
    val meetingId: String? = null,
    val noteId: String? = null,
    val dueAt: Long? = null,
    val dueText: String? = null,
    /** Decisions, deadlines and scope: the item this one replaced. */
    val supersedesId: String? = null,
    val answerText: String? = null,
    val answeredAt: Long? = null,
    val answerItemId: String? = null,
    /** The app's own task that executes it, if any. */
    val taskId: String? = null,
    val reason: String? = null,
    val severity: String? = null,
    /** MINE or THEIRS, for commitments. */
    val direction: String? = null,
    val confidence: Float? = null,
    @ColumnInfo(defaultValue = "0") val reviewed: Boolean = false,
    /** USER, AI or MARK. */
    @ColumnInfo(defaultValue = "AI") val source: String = "AI",
    val sourceFindingId: String? = null,
    @ColumnInfo(defaultValue = "WORK") val space: String = "WORK",
    val createdAt: Long,
    val updatedAt: Long,
    val closedAt: Long? = null,
    val deletedAt: Long? = null
)

/** Where an item was said: the transcript paragraphs, their times, and a quote. */
@Entity(tableName = "item_evidence", indices = [Index(value = ["itemId"])])
data class ItemEvidenceEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val meetingId: String? = null,
    val noteId: String? = null,
    val blockId: String? = null,
    val segmentIdsJson: String = "[]",
    val startMs: Long? = null,
    val endMs: Long? = null,
    val quote: String = ""
)

/** What an item is about: a person, organisation, project, note, meeting or another item. */
@Entity(
    tableName = "item_links",
    primaryKeys = ["itemId", "targetType", "targetId"],
    indices = [Index(value = ["targetType", "targetId"])]
)
data class ItemLinkEntity(
    val itemId: String,
    /** PERSON, ORG, PROJECT, NOTE, MEETING or ITEM. */
    val targetType: String,
    val targetId: String,
    val role: String = ""
)

/** The change log Pulse, "What changed" and timelines read. One row per change. */
@Entity(tableName = "item_events", indices = [Index(value = ["at"]), Index(value = ["itemId"])])
data class ItemEventEntity(
    @PrimaryKey val id: String,
    val itemId: String? = null,
    /** ITEM for now; other entity types can log here later. */
    val entityType: String,
    val entityId: String,
    /** [com.craftflowtechnologies.meetingmind.core.work.ItemEventType] name. */
    val type: String,
    val beforeJson: String? = null,
    val afterJson: String? = null,
    val at: Long,
    val evidenceId: String? = null
)

/** Who is on a project, and in what role. */
@Entity(tableName = "project_members", primaryKeys = ["notebookId", "personId"])
data class ProjectMemberEntity(
    val notebookId: String,
    val personId: String,
    val role: String = ""
)

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: ItemEntity)
    @Update suspend fun update(item: ItemEntity)

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: String): ItemEntity?

    @Query("SELECT * FROM items WHERE sourceFindingId = :findingId LIMIT 1")
    suspend fun bySourceFinding(findingId: String): ItemEntity?

    @Query("SELECT * FROM items WHERE taskId = :taskId AND kind = 'COMMITMENT' AND deletedAt IS NULL")
    suspend fun commitmentsForTask(taskId: String): List<ItemEntity>

    @Query("SELECT * FROM items WHERE meetingId = :meetingId AND deletedAt IS NULL ORDER BY createdAt")
    suspend fun forMeeting(meetingId: String): List<ItemEntity>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL")
    suspend fun allLive(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND kind = :kind AND (:reviewedOnly = 0 OR reviewed = 1) ORDER BY createdAt DESC LIMIT :limit")
    fun observeKind(kind: String, reviewedOnly: Int, limit: Int): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND kind = 'COMMITMENT' AND direction = :direction AND reviewed = 1 ORDER BY status = 'OPEN' DESC, dueAt IS NULL, dueAt, createdAt DESC")
    fun observeCommitments(direction: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND kind = 'QUESTION' AND status = 'OPEN' AND reviewed = 1 ORDER BY createdAt DESC LIMIT :limit")
    fun observeOpenQuestions(limit: Int): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND kind = 'COMMITMENT' AND status = 'UNCLEAR' ORDER BY createdAt DESC")
    fun observeUnclear(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND kind = 'DECISION' ORDER BY createdAt DESC LIMIT :limit")
    fun observeDecisions(limit: Int): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND (projectId = :entityId OR orgId = :entityId OR ownerPersonId = :entityId OR counterpartyPersonId = :entityId OR id IN (SELECT itemId FROM item_links WHERE targetId = :entityId)) ORDER BY createdAt DESC")
    fun observeAround(entityId: String): Flow<List<ItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvidence(e: ItemEvidenceEntity)

    @Query("SELECT * FROM item_evidence WHERE itemId = :itemId ORDER BY startMs")
    suspend fun evidenceFor(itemId: String): List<ItemEvidenceEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLink(link: ItemLinkEntity)

    @Query("SELECT * FROM item_links WHERE itemId = :itemId")
    suspend fun linksFor(itemId: String): List<ItemLinkEntity>

    @Insert suspend fun insertEvent(event: ItemEventEntity)

    @Query("SELECT * FROM item_events WHERE itemId = :itemId ORDER BY at, rowid")
    suspend fun eventsFor(itemId: String): List<ItemEventEntity>

    @Query("SELECT * FROM item_events WHERE at > :since ORDER BY at DESC, rowid DESC")
    suspend fun eventsSince(since: Long): List<ItemEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMember(m: ProjectMemberEntity)

    @Query("SELECT * FROM project_members WHERE notebookId = :notebookId")
    suspend fun membersOf(notebookId: String): List<ProjectMemberEntity>

    @Query("SELECT * FROM item_events WHERE itemId IN (:itemIds) ORDER BY at DESC, rowid DESC")
    suspend fun eventsForItems(itemIds: List<String>): List<ItemEventEntity>

    @Query("DELETE FROM project_members WHERE notebookId = :notebookId AND personId = :personId")
    suspend fun removeMember(notebookId: String, personId: String)

    /** Changes whenever an item or its log does, so screens built on them can refresh. */
    @Query("SELECT (SELECT COUNT(*) FROM item_events) + (SELECT IFNULL(MAX(updatedAt), 0) FROM items)")
    fun observeVersion(): Flow<Long>

    @Query("SELECT * FROM items") suspend fun exportAll(): List<ItemEntity>

    /** Reviewed items that are about any of these people, organisations or projects. */
    @Query(
        """SELECT * FROM items WHERE deletedAt IS NULL AND reviewed = 1 AND (projectId IN (:ids) OR orgId IN (:ids) OR ownerPersonId IN (:ids)
             OR counterpartyPersonId IN (:ids) OR id IN (SELECT itemId FROM item_links WHERE targetId IN (:ids))) ORDER BY createdAt DESC"""
    )
    suspend fun around(ids: List<String>): List<ItemEntity>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND reviewed = 1 AND kind = :kind AND status = :status ORDER BY createdAt DESC")
    suspend fun byKindStatus(kind: String, status: String): List<ItemEntity>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND reviewed = 1 AND kind = 'COMMITMENT' AND status IN ('OPEN', 'UNCLEAR')")
    suspend fun openCommitments(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND reviewed = 1 AND status IN ('OPEN', 'UNCLEAR', 'PROPOSED')")
    suspend fun openItems(): List<ItemEntity>

    @Query("SELECT taskId FROM items WHERE deletedAt IS NULL AND taskId IS NOT NULL AND kind = 'COMMITMENT' AND status IN ('OPEN', 'UNCLEAR')")
    suspend fun openCommitmentTaskIds(): List<String>

    @Query("SELECT * FROM item_events WHERE at >= :from AND at < :to ORDER BY at DESC, rowid DESC")
    suspend fun eventsBetween(from: Long, to: Long): List<ItemEventEntity>

    // A merged person's items follow them (docs/PLAN_PROFESSIONAL.md principle 7).
    @Query("UPDATE items SET ownerPersonId = :toId WHERE ownerPersonId = :fromId")
    suspend fun moveOwner(fromId: String, toId: String)

    @Query("UPDATE items SET counterpartyPersonId = :toId WHERE counterpartyPersonId = :fromId")
    suspend fun moveCounterparty(fromId: String, toId: String)

    @Query("UPDATE OR IGNORE item_links SET targetId = :toId WHERE targetType IN ('PERSON', 'ORG') AND targetId = :fromId")
    suspend fun moveLinks(fromId: String, toId: String)

    @Query("UPDATE items SET orgId = :toId WHERE orgId = :fromId")
    suspend fun moveOrg(fromId: String, toId: String)
}
