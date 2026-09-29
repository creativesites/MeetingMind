package com.example.core.work

import com.example.core.database.ItemEntity
import com.example.core.database.ItemWithOwner
import com.example.core.model.ActionItem
import com.example.core.model.Decision
import com.example.core.model.DecisionType
import com.example.core.model.FollowUp
import com.example.core.model.Question
import org.json.JSONArray

/*
 * Between the one `items` table and the shapes screens already use. Meeting Detail, exports and AI
 * tools keep seeing ActionItem, Decision, Question and FollowUp; the Work screens see [WorkItem].
 * Owner names always come from the join (ItemDao), never from a stored copy.
 */

fun ItemWithOwner.toWorkItem(): WorkItem = item.toWorkItem(ownerDisplay, ownerIsSelf == true)

fun ItemEntity.toWorkItem(ownerDisplay: String? = ownerName, ownerIsSelf: Boolean = false) = WorkItem(
    id = id,
    meetingId = meetingId,
    noteId = noteId,
    kind = enumOr(kind, ItemKind.TASK),
    subtype = subtype,
    text = text,
    status = enumOr(status, ItemStatus.OPEN),
    ownerSpeakerId = ownerSpeakerId,
    ownerPersonId = ownerPersonId,
    ownerName = ownerDisplay,
    ownerIsSelf = ownerIsSelf,
    dueAt = dueAt,
    dueText = dueText,
    answer = answer,
    answeredAt = answeredAt,
    projectId = projectId,
    source = enumOr(source, ItemSource.USER),
    confidence = confidence,
    reviewed = reviewed,
    sourceSegmentIds = idList(sourceSegmentIdsJson),
    sourceStartMs = sourceStartMs,
    supersededById = supersededById,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt
)

fun WorkItem.toEntity(metadataJson: String = "{}") = ItemEntity(
    id = id,
    meetingId = meetingId,
    noteId = noteId,
    kind = kind.name,
    subtype = subtype,
    text = text,
    status = status.name,
    ownerSpeakerId = ownerSpeakerId,
    ownerPersonId = ownerPersonId,
    ownerName = ownerName,
    dueAt = dueAt,
    dueText = dueText,
    answer = answer,
    answeredAt = answeredAt,
    projectId = projectId,
    source = source.name,
    confidence = confidence,
    reviewed = reviewed,
    sourceSegmentIdsJson = idsJson(sourceSegmentIds),
    sourceStartMs = sourceStartMs,
    supersededById = supersededById,
    metadataJson = metadataJson,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt
)

fun ItemWithOwner.toActionItem() = ActionItem(
    id = item.id,
    meetingId = item.meetingId.orEmpty(),
    task = item.text,
    assigneeSpeakerId = item.ownerSpeakerId,
    assigneeName = ownerDisplay,
    deadline = item.dueText,
    confidence = item.confidence,
    isCompleted = item.status == ItemStatus.DONE.name,
    sourceSegmentIds = idList(item.sourceSegmentIdsJson)
)

fun ItemWithOwner.toDecision() = Decision(
    id = item.id,
    meetingId = item.meetingId.orEmpty(),
    text = item.text,
    type = enumOr(item.subtype, DecisionType.DISCUSSION),
    confidence = item.confidence,
    sourceSegmentIds = idList(item.sourceSegmentIdsJson)
)

fun ItemWithOwner.toQuestion() = Question(
    id = item.id,
    meetingId = item.meetingId.orEmpty(),
    text = item.text,
    askedBySpeakerId = item.ownerSpeakerId,
    resolved = item.status == ItemStatus.ANSWERED.name,
    answer = item.answer,
    sourceSegmentIds = idList(item.sourceSegmentIdsJson)
)

fun ItemWithOwner.toFollowUp() = FollowUp(
    id = item.id,
    meetingId = item.meetingId.orEmpty(),
    description = item.text,
    ownerSpeakerId = item.ownerSpeakerId,
    deadline = item.dueText,
    sourceSegmentIds = idList(item.sourceSegmentIdsJson)
)

/** Where a finding from processing lands. [reference] is when the conversation happened. */
object ItemsFrom {
    fun action(a: ActionItem, noteId: String?, projectId: String?, reference: Long, startMs: Long?, source: ItemSource = ItemSource.AI, reviewed: Boolean = false) = ItemEntity(
        id = a.id, meetingId = a.meetingId.ifBlank { null }, noteId = noteId, kind = ItemKind.TASK.name, subtype = null,
        text = a.task, status = (if (a.isCompleted) ItemStatus.DONE else ItemStatus.OPEN).name,
        ownerSpeakerId = a.assigneeSpeakerId, ownerPersonId = null, ownerName = a.assigneeName,
        dueAt = DueDates.parse(a.deadline, reference), dueText = a.deadline, answer = null, answeredAt = null,
        projectId = projectId, source = source.name, confidence = a.confidence, reviewed = reviewed,
        sourceSegmentIdsJson = idsJson(a.sourceSegmentIds), sourceStartMs = startMs, supersededById = null,
        metadataJson = "{}", createdAt = reference, updatedAt = reference, completedAt = null
    )

    fun decision(d: Decision, noteId: String?, projectId: String?, reference: Long, startMs: Long?) = ItemEntity(
        id = d.id, meetingId = d.meetingId.ifBlank { null }, noteId = noteId, kind = ItemKind.DECISION.name, subtype = d.type.name,
        text = d.text, status = ItemStatus.OPEN.name, ownerSpeakerId = null, ownerPersonId = null, ownerName = null,
        dueAt = null, dueText = null, answer = null, answeredAt = null, projectId = projectId, source = ItemSource.AI.name,
        confidence = d.confidence, reviewed = false, sourceSegmentIdsJson = idsJson(d.sourceSegmentIds), sourceStartMs = startMs,
        supersededById = null, metadataJson = "{}", createdAt = reference, updatedAt = reference, completedAt = null
    )

    fun question(q: Question, noteId: String?, projectId: String?, reference: Long, startMs: Long?) = ItemEntity(
        id = q.id, meetingId = q.meetingId.ifBlank { null }, noteId = noteId, kind = ItemKind.QUESTION.name, subtype = null,
        text = q.text, status = (if (q.resolved) ItemStatus.ANSWERED else ItemStatus.OPEN).name,
        ownerSpeakerId = q.askedBySpeakerId, ownerPersonId = null, ownerName = null, dueAt = null, dueText = null,
        answer = q.answer, answeredAt = null, projectId = projectId, source = ItemSource.AI.name, confidence = null,
        reviewed = false, sourceSegmentIdsJson = idsJson(q.sourceSegmentIds), sourceStartMs = startMs, supersededById = null,
        metadataJson = "{}", createdAt = reference, updatedAt = reference, completedAt = null
    )

    fun followUp(f: FollowUp, noteId: String?, projectId: String?, reference: Long, startMs: Long?) = ItemEntity(
        id = f.id, meetingId = f.meetingId.ifBlank { null }, noteId = noteId, kind = ItemKind.TASK.name, subtype = SUBTYPE_FOLLOW_UP,
        text = f.description, status = ItemStatus.OPEN.name, ownerSpeakerId = f.ownerSpeakerId, ownerPersonId = null, ownerName = null,
        dueAt = DueDates.parse(f.deadline, reference), dueText = f.deadline, answer = null, answeredAt = null, projectId = projectId,
        source = ItemSource.AI.name, confidence = null, reviewed = false, sourceSegmentIdsJson = idsJson(f.sourceSegmentIds),
        sourceStartMs = startMs, supersededById = null, metadataJson = "{}", createdAt = reference, updatedAt = reference, completedAt = null
    )
}

internal inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback

internal fun idList(json: String?): List<String> = runCatching {
    val a = JSONArray(json ?: "[]"); (0 until a.length()).map { a.getString(it) }
}.getOrDefault(emptyList())

internal fun idsJson(ids: List<String>): String = JSONArray().apply { ids.forEach { put(it) } }.toString()
