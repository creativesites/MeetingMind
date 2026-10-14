package com.craftflowtechnologies.meetingmind.core.work

import androidx.room.withTransaction
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemEventEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemEvidenceEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemLinkEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/*
 * The durable record of professional work (docs/PLAN_PROFESSIONAL.md D4): decisions, commitments,
 * questions and the rest, each with evidence and a change log. Every write goes through
 * [ItemRepository], which logs it in the same transaction — nothing changes an item silently.
 */

enum class ItemKind {
    DECISION, COMMITMENT, QUESTION,
    RISK, REQUIREMENT, CONSTRAINT, ASSUMPTION, DEPENDENCY, OBJECTION,
    DEADLINE, METRIC, SCOPE_CHANGE, APPROVAL
}

enum class ItemStatus {
    // decisions
    PROPOSED, ACTIVE, SUPERSEDED, REVERSED,
    // commitments
    OPEN, COMPLETED, CANCELLED, UNCLEAR,
    // questions
    ANSWERED, DROPPED,
    // everything else
    CLOSED;

    /** The state is an ending: the item stops asking for attention. */
    val isEnd: Boolean get() = this in setOf(SUPERSEDED, REVERSED, COMPLETED, CANCELLED, ANSWERED, DROPPED, CLOSED)

    companion object {
        /** The statuses an item of this kind can hold; the first is where it starts. */
        fun allowedFor(kind: ItemKind): List<ItemStatus> = when (kind) {
            ItemKind.DECISION -> listOf(ACTIVE, PROPOSED, SUPERSEDED, REVERSED)
            ItemKind.COMMITMENT -> listOf(OPEN, COMPLETED, CANCELLED, UNCLEAR)
            ItemKind.QUESTION -> listOf(OPEN, ANSWERED, DROPPED)
            else -> listOf(OPEN, CLOSED)
        }
    }
}

enum class ItemEventType { CREATED, STATUS, DUE_CHANGED, OWNER_CHANGED, TEXT_CHANGED, SUPERSEDED, ANSWERED, LINKED, PROJECT_CHANGED }

enum class Direction { MINE, THEIRS }

object ItemSource { const val USER = "USER"; const val AI = "AI"; const val MARK = "MARK" }

object LinkType { const val PERSON = "PERSON"; const val ORG = "ORG"; const val PROJECT = "PROJECT"; const val NOTE = "NOTE"; const val MEETING = "MEETING"; const val ITEM = "ITEM" }

val ItemEntity.itemKind: ItemKind get() = enumOr(kind, ItemKind.DECISION)
val ItemEntity.itemStatus: ItemStatus get() = enumOr(status, ItemStatus.OPEN)
val ItemEntity.itemDirection: Direction? get() = direction?.let { d -> Direction.entries.firstOrNull { it.name == d } }

/** Computed, never stored: an open item whose date has passed (before today starts). */
fun ItemEntity.isOverdue(now: Long): Boolean = dueAt != null && !itemStatus.isEnd && dueAt < DueDates.startOfDay(now)
fun ItemEntity.isDueSoon(now: Long, days: Int = 3): Boolean =
    dueAt != null && !itemStatus.isEnd && !isOverdue(now) && dueAt < DueDates.startOfDay(now) + days * 86_400_000L

class ItemRepository(
    private val database: MeetMindDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = database.itemDao()

    // ---------------------------------------------------------------- reads

    suspend fun get(id: String): ItemEntity? = withContext(Dispatchers.IO) { dao.getById(id) }
    suspend fun evidence(itemId: String): List<ItemEvidenceEntity> = withContext(Dispatchers.IO) { dao.evidenceFor(itemId) }
    suspend fun links(itemId: String): List<ItemLinkEntity> = withContext(Dispatchers.IO) { dao.linksFor(itemId) }
    suspend fun events(itemId: String): List<ItemEventEntity> = withContext(Dispatchers.IO) { dao.eventsFor(itemId) }
    suspend fun eventsSince(since: Long): List<ItemEventEntity> = withContext(Dispatchers.IO) { dao.eventsSince(since) }

    /** What a decision replaced, oldest first, ending with the decision itself. */
    suspend fun chain(id: String): List<ItemEntity> = withContext(Dispatchers.IO) {
        val out = ArrayDeque<ItemEntity>()
        var current = dao.getById(id)
        while (current != null && out.none { it.id == current!!.id }) {
            out.addFirst(current)
            current = current.supersedesId?.let { dao.getById(it) }
        }
        out.toList()
    }

    fun observeCommitments(direction: Direction): Flow<List<ItemEntity>> = dao.observeCommitments(direction.name)
    fun observeUnclear(): Flow<List<ItemEntity>> = dao.observeUnclear()
    fun observeOpenQuestions(limit: Int = 200): Flow<List<ItemEntity>> = dao.observeOpenQuestions(limit)
    fun observeDecisions(limit: Int = 200): Flow<List<ItemEntity>> = dao.observeDecisions(limit)

    // ---------------------------------------------------------------- writes

    /** Adds an item with its evidence and links, and logs CREATED, in one transaction. */
    suspend fun create(
        item: ItemEntity,
        evidence: List<ItemEvidenceEntity> = emptyList(),
        links: List<ItemLinkEntity> = emptyList()
    ): ItemEntity = withContext(Dispatchers.IO) {
        database.withTransaction { createIn(item, evidence, links) }
    }

    private suspend fun createIn(item: ItemEntity, evidence: List<ItemEvidenceEntity>, links: List<ItemLinkEntity>): ItemEntity {
        val now = clock()
        val id = item.id.ifBlank { "item_${UUID.randomUUID()}" }
        val saved = item.copy(id = id, createdAt = item.createdAt.takeIf { it > 0 } ?: now, updatedAt = now)
        dao.insert(saved)
        evidence.forEach { dao.insertEvidence(it.copy(itemId = id, id = it.id.ifBlank { "ev_${UUID.randomUUID()}" })) }
        links.forEach { dao.insertLink(it.copy(itemId = id)) }
        log(id, ItemEventType.CREATED, null, snapshot(saved), evidence.firstOrNull()?.id)
        return saved
    }

    suspend fun setStatus(id: String, status: ItemStatus, evidenceId: String? = null): ItemEntity? = withContext(Dispatchers.IO) {
        database.withTransaction { setStatusIn(id, status, evidenceId) }
    }

    private suspend fun setStatusIn(id: String, status: ItemStatus, evidenceId: String?): ItemEntity? {
        val item = dao.getById(id) ?: return null
        if (item.status == status.name) return item
        val now = clock()
        val updated = item.copy(status = status.name, updatedAt = now, closedAt = if (status.isEnd) now else null)
        dao.update(updated)
        log(id, ItemEventType.STATUS, JSONObject().put("status", item.status), JSONObject().put("status", status.name), evidenceId)
        if (item.kind == ItemKind.COMMITMENT.name && item.taskId != null) writeThroughToTask(item.taskId, done = status == ItemStatus.COMPLETED, wasDone = item.status == ItemStatus.COMPLETED.name)
        return updated
    }

    suspend fun setDue(id: String, dueAt: Long?, dueText: String?) = edit(id, ItemEventType.DUE_CHANGED,
        before = { JSONObject().put("dueAt", it.dueAt ?: JSONObject.NULL).put("dueText", it.dueText ?: JSONObject.NULL) },
        change = { it.copy(dueAt = dueAt, dueText = dueText) },
        after = { JSONObject().put("dueAt", dueAt ?: JSONObject.NULL).put("dueText", dueText ?: JSONObject.NULL) },
        unchanged = { it.dueAt == dueAt && it.dueText == dueText })

    suspend fun setOwner(id: String, personId: String?, speakerId: String? = null) = edit(id, ItemEventType.OWNER_CHANGED,
        before = { JSONObject().put("personId", it.ownerPersonId ?: JSONObject.NULL).put("speakerId", it.ownerSpeakerId ?: JSONObject.NULL) },
        change = { it.copy(ownerPersonId = personId, ownerSpeakerId = speakerId) },
        after = { JSONObject().put("personId", personId ?: JSONObject.NULL).put("speakerId", speakerId ?: JSONObject.NULL) },
        unchanged = { it.ownerPersonId == personId && it.ownerSpeakerId == speakerId })

    suspend fun setText(id: String, text: String) {
        val t = text.trim().ifEmpty { return }
        edit(id, ItemEventType.TEXT_CHANGED,
            before = { JSONObject().put("text", it.text) }, change = { it.copy(text = t) },
            after = { JSONObject().put("text", t) }, unchanged = { it.text == t })
    }

    suspend fun setProject(id: String, projectId: String?, orgId: String?) = edit(id, ItemEventType.PROJECT_CHANGED,
        before = { JSONObject().put("projectId", it.projectId ?: JSONObject.NULL).put("orgId", it.orgId ?: JSONObject.NULL) },
        change = { it.copy(projectId = projectId, orgId = orgId) },
        after = { JSONObject().put("projectId", projectId ?: JSONObject.NULL).put("orgId", orgId ?: JSONObject.NULL) },
        unchanged = { it.projectId == projectId && it.orgId == orgId })

    /** An unclear promise, settled: who owes it. It becomes an ordinary open commitment. */
    suspend fun settle(id: String, direction: Direction, ownerPersonId: String?) {
        edit(id, ItemEventType.OWNER_CHANGED,
            before = { JSONObject().put("direction", it.direction ?: JSONObject.NULL).put("personId", it.ownerPersonId ?: JSONObject.NULL) },
            change = { it.copy(direction = direction.name, ownerPersonId = if (direction == Direction.THEIRS) ownerPersonId else null) },
            after = { JSONObject().put("direction", direction.name).put("personId", ownerPersonId ?: JSONObject.NULL) },
            unchanged = { it.direction == direction.name && it.ownerPersonId == ownerPersonId })
        setStatus(id, ItemStatus.OPEN)
    }

    /** The person has looked at it (the Wrap-up, or opening it): AI items become part of the record. */
    suspend fun markReviewed(id: String) = edit(id, ItemEventType.STATUS,
        before = { JSONObject().put("reviewed", false) }, change = { it.copy(reviewed = true) },
        after = { JSONObject().put("reviewed", true) }, unchanged = { it.reviewed })

    /** Links the app's own task that carries a commitment out. */
    suspend fun setTask(id: String, taskId: String?) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val item = dao.getById(id) ?: return@withTransaction
            if (item.taskId == taskId) return@withTransaction
            dao.update(item.copy(taskId = taskId, updatedAt = clock()))
            log(id, ItemEventType.LINKED, JSONObject().put("taskId", item.taskId ?: JSONObject.NULL), JSONObject().put("taskId", taskId ?: JSONObject.NULL), null)
        }
    }

    /** Removes an item from every list; the change log keeps the record. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val item = dao.getById(id) ?: return@withTransaction
            if (item.deletedAt != null) return@withTransaction
            val now = clock()
            dao.update(item.copy(deletedAt = now, updatedAt = now))
            log(id, ItemEventType.STATUS, JSONObject().put("deleted", false), JSONObject().put("deleted", true), null)
        }
    }

    /**
     * [newItem] replaces [oldId]: the old one becomes SUPERSEDED, the new one points back at it,
     * and both the status and the link are logged. The person has already confirmed this.
     */
    suspend fun supersede(oldId: String, newItem: ItemEntity, evidence: List<ItemEvidenceEntity> = emptyList(), links: List<ItemLinkEntity> = emptyList()): ItemEntity? =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val old = dao.getById(oldId) ?: return@withTransaction null
                val created = createIn(newItem.copy(supersedesId = oldId, projectId = newItem.projectId ?: old.projectId, orgId = newItem.orgId ?: old.orgId), evidence, links)
                dao.insertLink(ItemLinkEntity(created.id, LinkType.ITEM, oldId, "SUPERSEDES"))
                val now = clock()
                dao.update(old.copy(status = ItemStatus.SUPERSEDED.name, closedAt = now, updatedAt = now))
                log(oldId, ItemEventType.SUPERSEDED, JSONObject().put("status", old.status).put("text", old.text),
                    JSONObject().put("status", ItemStatus.SUPERSEDED.name).put("by", created.id).put("text", created.text), evidence.firstOrNull()?.id)
                created
            }
        }

    /**
     * [newId], already an item (promoted from the same recording), replaces [oldId]: the old one is
     * SUPERSEDED, the new one points back at it, and both the status and the link are logged.
     */
    suspend fun supersedeWith(oldId: String, newId: String, evidenceId: String? = null): Boolean = withContext(Dispatchers.IO) {
        database.withTransaction {
            val old = dao.getById(oldId) ?: return@withTransaction false
            val new = dao.getById(newId) ?: return@withTransaction false
            if (old.id == new.id || new.supersedesId == oldId) return@withTransaction false
            val now = clock()
            dao.update(new.copy(supersedesId = oldId, projectId = new.projectId ?: old.projectId, orgId = new.orgId ?: old.orgId, updatedAt = now))
            dao.insertLink(ItemLinkEntity(newId, LinkType.ITEM, oldId, "SUPERSEDES"))
            dao.update(old.copy(status = ItemStatus.SUPERSEDED.name, closedAt = now, updatedAt = now))
            log(oldId, ItemEventType.SUPERSEDED, JSONObject().put("status", old.status).put("text", old.text),
                JSONObject().put("status", ItemStatus.SUPERSEDED.name).put("by", newId).put("text", new.text), evidenceId)
            true
        }
    }

    /** A question is answered, in words and optionally by the later item that answered it. */
    suspend fun answer(id: String, answerText: String?, answerItemId: String? = null, evidenceId: String? = null): ItemEntity? = withContext(Dispatchers.IO) {
        database.withTransaction {
            val q = dao.getById(id) ?: return@withTransaction null
            val now = clock()
            val updated = q.copy(status = ItemStatus.ANSWERED.name, answerText = answerText?.trim()?.ifEmpty { null } ?: q.answerText,
                answeredAt = now, answerItemId = answerItemId ?: q.answerItemId, updatedAt = now, closedAt = now)
            dao.update(updated)
            log(id, ItemEventType.ANSWERED, JSONObject().put("status", q.status), JSONObject().put("status", ItemStatus.ANSWERED.name).put("answer", updated.answerText ?: JSONObject.NULL), evidenceId)
            updated
        }
    }

    suspend fun link(itemId: String, targetType: String, targetId: String, role: String = "") = withContext(Dispatchers.IO) {
        database.withTransaction {
            if (dao.linksFor(itemId).any { it.targetType == targetType && it.targetId == targetId }) return@withTransaction
            dao.insertLink(ItemLinkEntity(itemId, targetType, targetId, role))
            log(itemId, ItemEventType.LINKED, null, JSONObject().put("targetType", targetType).put("targetId", targetId).put("role", role), null)
        }
    }

    // ---------------------------------------------------------------- task sync

    /**
     * A task was ticked or reopened elsewhere (the task screens, or the Work space): its
     * commitment follows. Called from both paths, so the two never disagree.
     */
    suspend fun onTaskDone(taskId: String, done: Boolean) = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.commitmentsForTask(taskId).forEach { c ->
                val open = c.status == ItemStatus.OPEN.name || c.status == ItemStatus.UNCLEAR.name
                when {
                    done && open -> setStatusIn(c.id, ItemStatus.COMPLETED, null)
                    !done && c.status == ItemStatus.COMPLETED.name -> setStatusIn(c.id, ItemStatus.OPEN, null)
                }
            }
        }
    }

    /** A commitment closed or reopened: its task follows, unless it's already there. */
    private suspend fun writeThroughToTask(taskId: String, done: Boolean, wasDone: Boolean) {
        if (done == wasDone) return
        val task = database.taskDao().getById(taskId) ?: return
        if ((task.doneAt != null) == done) return
        val now = clock()
        database.taskDao().setDone(taskId, if (done) now else null, now)
    }

    // ---------------------------------------------------------------- internals

    private suspend fun edit(
        id: String, type: ItemEventType,
        before: (ItemEntity) -> JSONObject, change: (ItemEntity) -> ItemEntity, after: (ItemEntity) -> JSONObject, unchanged: (ItemEntity) -> Boolean
    ): Unit = withContext(Dispatchers.IO) {
        database.withTransaction {
            val item = dao.getById(id) ?: return@withTransaction
            if (unchanged(item)) return@withTransaction
            val updated = change(item).copy(updatedAt = clock())
            dao.update(updated)
            log(id, type, before(item), after(updated), null)
        }
    }

    private suspend fun log(itemId: String, type: ItemEventType, before: JSONObject?, after: JSONObject?, evidenceId: String?) {
        dao.insertEvent(ItemEventEntity("ev_${UUID.randomUUID()}", itemId, "ITEM", itemId, type.name, before?.toString(), after?.toString(), clock(), evidenceId))
    }

    private fun snapshot(i: ItemEntity) = JSONObject().put("kind", i.kind).put("status", i.status).put("text", i.text)
        .put("dueAt", i.dueAt ?: JSONObject.NULL).put("direction", i.direction ?: JSONObject.NULL)
}
