package com.craftflowtechnologies.meetingmind.core.work

import androidx.room.withTransaction
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemEvidenceEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemLinkEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.TaskEntity
import org.json.JSONObject

/**
 * Turns a recording's findings into items (docs/PLAN_PROFESSIONAL.md D4.1). Once per finding:
 * `sourceFindingId` is unique, so promoting again only brings the item up to date.
 */
class ItemPromotion(
    private val database: MeetMindDatabase,
    private val items: ItemRepository = ItemRepository(database)
) {
    private val dao = database.itemDao()

    /**
     * [reviewed] is true when the person confirmed the Wrap-up. An unreviewed promotion (the
     * upgrade path, for recordings that never had one) keeps the items out of the lists until
     * they're confirmed. [ownerPerson] resolves who owns a finding.
     */
    suspend fun promote(meetingId: String, findings: List<Finding>, reviewed: Boolean, ownerPerson: suspend (Finding) -> String?): List<ItemEntity> {
        val meeting = database.meetingDao().getMeetingById(meetingId) ?: return emptyList()
        val note = meeting.noteId?.let { database.noteDao().getById(it) }
        val notebook = note?.notebookId?.let { database.notebookDao().getById(it) }
        val peopleIds = meeting.noteId?.let { database.workDao().peopleIdsFor(it) }.orEmpty()
        val people = peopleIds.mapNotNull { database.peopleDao().getById(it) }
        val orgId = notebook?.let { runCatching { JSONObject(it.propertiesJson).optString("orgId").ifBlank { null } }.getOrNull() }
            ?: people.mapNotNull { it.orgId }.distinct().singleOrNull()
        val segments = database.transcriptDao().getSegmentsForMeetingDirect(meetingId).associateBy { it.id }
        val decisionTypes = database.decisionDao().getDecisionsForMeetingDirect(meetingId).associate { it.id to it.type }
        val tasks = database.workDao().tasksForMeeting(meetingId).associateBy { it.sourceItemId }
        val speakers = database.speakerDao().getSpeakersForMeetingDirect(meetingId).associateBy { it.id }
        val selfId = database.workDao().self()?.id

        fun evidenceFor(f: Finding): ItemEvidenceEntity? {
            val segs = f.sourceSegmentIds.mapNotNull { segments[it] }
            if (segs.isEmpty()) return null
            return ItemEvidenceEntity(
                id = "", itemId = "", meetingId = meetingId, noteId = meeting.noteId, segmentIdsJson = jsonList(segs.map { it.id }),
                startMs = segs.minOf { it.startMs }, endMs = segs.maxOf { it.endMs }, quote = segs.joinToString(" ") { it.text.trim() }.take(QUOTE_LIMIT)
            )
        }

        fun links(personId: String?): List<ItemLinkEntity> = buildList {
            add(ItemLinkEntity("", LinkType.MEETING, meetingId, "SOURCE"))
            meeting.noteId?.let { add(ItemLinkEntity("", LinkType.NOTE, it, "SOURCE")) }
            notebook?.let { add(ItemLinkEntity("", LinkType.PROJECT, it.id, "PROJECT")) }
            orgId?.let { add(ItemLinkEntity("", LinkType.ORG, it, "ORG")) }
            peopleIds.forEach { add(ItemLinkEntity("", LinkType.PERSON, it, if (it == personId) "OWNER" else "PARTICIPANT")) }
            if (personId != null && personId !in peopleIds) add(ItemLinkEntity("", LinkType.PERSON, personId, "OWNER"))
        }

        val out = mutableListOf<ItemEntity>()
        val promoted = mutableSetOf<String>()
        database.withTransaction {
            for (f in findings) {
                val owner = if (f.kind == FindingKind.ACTION || f.kind == FindingKind.FOLLOW_UP) ownerPerson(f) else null
                val task = tasks[f.id]
                val existing = dao.bySourceFinding(f.id)
                promoted += f.id
                val (kind, status, direction) = when (f.kind) {
                    FindingKind.DECISION -> Triple(ItemKind.DECISION, if (reviewed || decisionTypes[f.id] != "SUGGESTION") ItemStatus.ACTIVE else ItemStatus.PROPOSED, null)
                    FindingKind.QUESTION -> Triple(ItemKind.QUESTION, ItemStatus.OPEN, null)
                    else -> Triple(ItemKind.COMMITMENT, if (task?.doneAt != null) ItemStatus.COMPLETED else ItemStatus.OPEN, if (f.isMine) Direction.MINE else Direction.THEIRS)
                }
                if (existing != null) {
                    out += refresh(existing, f, task, owner, status, reviewed)
                    continue
                }
                val askerPerson = if (f.kind == FindingKind.QUESTION) f.ownerSpeakerId?.let { speakers[it]?.personId }?.takeIf { it != selfId } else null
                val item = ItemEntity(
                    id = "", kind = kind.name, status = status.name, text = f.text,
                    ownerPersonId = if (direction == Direction.THEIRS) owner else askerPerson,
                    ownerSpeakerId = when {
                        f.kind == FindingKind.QUESTION -> f.ownerSpeakerId
                        direction == Direction.THEIRS && owner == null -> f.ownerSpeakerId
                        else -> null
                    },
                    projectId = notebook?.id, orgId = orgId, meetingId = meetingId, noteId = meeting.noteId,
                    dueAt = f.dueAt, dueText = f.dueText, taskId = task?.id, direction = direction?.name,
                    confidence = f.confidence, reviewed = reviewed, source = if (f.sourceSegmentIds.isEmpty()) ItemSource.USER else ItemSource.AI,
                    sourceFindingId = f.id, createdAt = meeting.createdAt, updatedAt = meeting.createdAt,
                    closedAt = if (status == ItemStatus.COMPLETED) task?.doneAt else null
                )
                out += items.create(item, listOfNotNull(evidenceFor(f)), links(owner))
            }
            // Questions already answered in the recording come across as answered.
            for (q in database.questionDao().getQuestionsForMeetingDirect(meetingId).filter { it.resolved }) {
                promoted += q.id
                if (dao.bySourceFinding(q.id) != null) continue
                val f = Finding(q.id, meetingId, FindingKind.QUESTION, q.text, q.askedBySpeakerId, sourceSegmentIds = idList(q.sourceSegmentIdsJson))
                val created = items.create(
                    ItemEntity("", ItemKind.QUESTION.name, ItemStatus.ANSWERED.name, q.text, ownerSpeakerId = q.askedBySpeakerId, projectId = notebook?.id, orgId = orgId,
                        meetingId = meetingId, noteId = meeting.noteId, answerText = q.answer, answeredAt = meeting.createdAt, reviewed = reviewed,
                        source = ItemSource.AI, sourceFindingId = q.id, createdAt = meeting.createdAt, updatedAt = meeting.createdAt, closedAt = meeting.createdAt),
                    listOfNotNull(evidenceFor(f)), links(null)
                )
                out += created
            }
            // A finding the person removed in the Wrap-up shouldn't linger from an earlier, unconfirmed promotion.
            if (reviewed) dao.forMeeting(meetingId).filter { !it.reviewed && it.sourceFindingId != null && it.sourceFindingId !in promoted }.forEach { items.delete(it.id) }
        }
        return out
    }

    /** An item promoted earlier, now confirmed: brings in the person's edits and marks it reviewed. */
    private suspend fun refresh(existing: ItemEntity, f: Finding, task: TaskEntity?, owner: String?, status: ItemStatus, reviewed: Boolean): ItemEntity {
        if (existing.deletedAt != null) return existing
        if (!existing.reviewed && reviewed) {
            if (existing.text != f.text) items.setText(existing.id, f.text)
            if (existing.dueAt != f.dueAt || existing.dueText != f.dueText) items.setDue(existing.id, f.dueAt, f.dueText)
            if (existing.direction == Direction.THEIRS.name && existing.ownerPersonId != owner && owner != null) items.setOwner(existing.id, owner, null)
            if (existing.kind == ItemKind.DECISION.name && existing.status == ItemStatus.PROPOSED.name) items.setStatus(existing.id, status)
            items.markReviewed(existing.id)
        }
        if (existing.taskId == null && task != null) items.setTask(existing.id, task.id)
        return items.get(existing.id) ?: existing
    }

    companion object { const val QUOTE_LIMIT = 280 }
}
