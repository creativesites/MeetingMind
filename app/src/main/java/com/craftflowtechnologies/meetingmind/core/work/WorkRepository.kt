package com.craftflowtechnologies.meetingmind.core.work

import androidx.room.withTransaction
import com.craftflowtechnologies.meetingmind.core.database.ActionItemEntity
import com.craftflowtechnologies.meetingmind.core.database.FindingRow
import com.craftflowtechnologies.meetingmind.core.database.ItemEntity
import com.craftflowtechnologies.meetingmind.core.database.ItemLinkEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.MeetingEntity
import com.craftflowtechnologies.meetingmind.core.database.NoteEntity
import com.craftflowtechnologies.meetingmind.core.database.TaskEntity
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import com.craftflowtechnologies.meetingmind.core.tasks.TaskKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/** A recording waiting for its Wrap-up, or for its follow-up to be sent. */
data class MeetingRow(val meetingId: String, val noteId: String?, val title: String, val at: Long, val durationMs: Long, val type: RecordingType)

/** A work task, with its owner's name as it is now. */
data class WorkTask(
    val id: String,
    val title: String,
    val dueAt: Long?,
    val doneAt: Long?,
    val waitingOn: Boolean,
    val ownerName: String?,
    val personId: String?,
    val meetingId: String?,
    val noteId: String?,
    val startMs: Long?,
    val kind: TaskKind
) {
    val done get() = doneAt != null
    fun isOverdue(now: Long) = !done && dueAt != null && dueAt < DueDates.startOfDay(now)
}

/**
 * The Work space's reads and writes (docs/PLAN_PROFESSIONAL.md §5). Findings come from a
 * recording's action items, follow-ups, decisions and questions; confirming them in the Wrap-up
 * turns actions into the app's own tasks, owned by you or by someone you're waiting on.
 */
class WorkRepository(private val database: MeetMindDatabase) {
    private val work = database.workDao()

    fun isWork(type: String?): Boolean = type?.let { t -> runCatching { Workflows.space(RecordingType.valueOf(t)) == NotebookSpace.WORK }.getOrDefault(false) } ?: false

    // ---------------------------------------------------------------- recordings

    private fun MeetingEntity.row() = MeetingRow(id, noteId, title, createdAt, durationMs, runCatching { RecordingType.valueOf(recordingType) }.getOrDefault(RecordingType.GENERAL))

    /** Work recordings with findings nobody has reviewed yet (Home and Work → To review). */
    fun observeToReview(): Flow<List<MeetingRow>> = work.observeUnreviewed().map { list ->
        withContext(Dispatchers.IO) {
            list.filter { isWork(it.recordingType) || it.recordingType == "GENERAL" }.filter { hasFindings(it.id) }.map { it.row() }
        }
    }

    fun observeRecentWork(limit: Int = 12): Flow<List<MeetingRow>> = work.observeByTypes(WorkTypeNames, limit).map { l -> l.map { it.row() } }

    private suspend fun hasFindings(meetingId: String): Boolean =
        database.actionItemDao().getActionItemsForMeetingDirect(meetingId).isNotEmpty() ||
            database.decisionDao().getDecisionsForMeetingDirect(meetingId).isNotEmpty() ||
            database.questionDao().getQuestionsForMeetingDirect(meetingId).isNotEmpty() ||
            work.followUpsFor(meetingId).isNotEmpty()

    /** Whether a finished recording opens its Wrap-up: work, with findings, not reviewed (§4.3). */
    suspend fun wantsWrapUp(meetingId: String): Boolean = withContext(Dispatchers.IO) {
        val m = database.meetingDao().getMeetingById(meetingId) ?: return@withContext false
        (isWork(m.recordingType) || m.recordingType == "GENERAL" || m.recordingType == "CUSTOM") && m.reviewedAt == null && hasFindings(meetingId)
    }

    // ---------------------------------------------------------------- findings

    /** Everything a recording found, with owners read through their speakers (names are dynamic). */
    suspend fun findings(meetingId: String): List<Finding> = withContext(Dispatchers.IO) {
        val speakers = database.speakerDao().getSpeakersForMeetingDirect(meetingId).associateBy { it.id }
        val selfId = work.self()?.id
        val starts = database.transcriptDao().getSegmentsForMeetingDirect(meetingId).associate { it.id to it.startMs }
        val tasks = work.tasksForMeeting(meetingId).associateBy { it.sourceItemId }
        val createdAt = database.meetingDao().getMeetingById(meetingId)?.createdAt ?: System.currentTimeMillis()
        fun start(ids: List<String>) = ids.firstNotNullOfOrNull { starts[it] }
        fun ownerName(sid: String?, stored: String?) = sid?.let { speakers[it] }?.let { it.customName.ifBlank { it.originalLabel } } ?: stored
        fun isSelf(sid: String?) = selfId != null && sid != null && speakers[sid]?.personId == selfId
        val out = mutableListOf<Finding>()
        database.actionItemDao().getActionItemsForMeetingDirect(meetingId).forEach { a ->
            val ids = idList(a.sourceSegmentIdsJson)
            out += Finding(a.id, meetingId, FindingKind.ACTION, a.task, a.assigneeSpeakerId, ownerName(a.assigneeSpeakerId, a.assigneeName),
                isSelf(a.assigneeSpeakerId), a.deadline, DueDates.parse(a.deadline, createdAt), a.confidence, ids, start(ids), taskId = tasks[a.id]?.id)
        }
        work.followUpsFor(meetingId).forEach { f ->
            val ids = idList(f.sourceSegmentIdsJson)
            out += Finding(f.id, meetingId, FindingKind.FOLLOW_UP, f.description, f.ownerSpeakerId, ownerName(f.ownerSpeakerId, null),
                isSelf(f.ownerSpeakerId), f.deadline, DueDates.parse(f.deadline, createdAt), null, ids, start(ids), taskId = tasks[f.id]?.id)
        }
        database.decisionDao().getDecisionsForMeetingDirect(meetingId).filter { it.type == "DECISION" || it.type == "SUGGESTION" }.forEach { d ->
            val ids = idList(d.sourceSegmentIdsJson)
            out += Finding(d.id, meetingId, FindingKind.DECISION, d.text, confidence = d.confidence, sourceSegmentIds = ids, startMs = start(ids))
        }
        database.questionDao().getQuestionsForMeetingDirect(meetingId).filter { !it.resolved }.forEach { q ->
            val ids = idList(q.sourceSegmentIdsJson)
            out += Finding(q.id, meetingId, FindingKind.QUESTION, q.text, q.askedBySpeakerId, ownerName(q.askedBySpeakerId, null),
                sourceSegmentIds = ids, startMs = start(ids), answer = q.answer)
        }
        out
    }

    suspend fun setText(f: Finding, text: String) = withContext(Dispatchers.IO) {
        val t = text.trim().ifEmpty { return@withContext }
        when (f.kind) {
            FindingKind.ACTION -> database.actionItemDao().getActionItemsForMeetingDirect(f.meetingId).firstOrNull { it.id == f.id }?.let { database.actionItemDao().updateActionItem(it.copy(task = t)) }
            FindingKind.FOLLOW_UP -> work.followUpsFor(f.meetingId).firstOrNull { it.id == f.id }?.let { work.updateFollowUp(it.copy(description = t)) }
            FindingKind.DECISION -> database.decisionDao().getDecisionsForMeetingDirect(f.meetingId).firstOrNull { it.id == f.id }?.let { work.updateDecision(it.copy(text = t)) }
            FindingKind.QUESTION -> database.questionDao().getQuestionsForMeetingDirect(f.meetingId).firstOrNull { it.id == f.id }?.let { database.questionDao().updateQuestion(it.copy(text = t)) }
        }
    }

    /** The owner is a speaker in the recording, or a typed name; null clears it. */
    suspend fun setOwner(f: Finding, speakerId: String?, name: String?) = withContext(Dispatchers.IO) {
        when (f.kind) {
            FindingKind.ACTION -> database.actionItemDao().getActionItemsForMeetingDirect(f.meetingId).firstOrNull { it.id == f.id }
                ?.let { database.actionItemDao().updateActionItem(it.copy(assigneeSpeakerId = speakerId, assigneeName = name)) }
            FindingKind.FOLLOW_UP -> work.followUpsFor(f.meetingId).firstOrNull { it.id == f.id }?.let { work.updateFollowUp(it.copy(ownerSpeakerId = speakerId)) }
            else -> Unit
        }
    }

    suspend fun setDue(f: Finding, dueText: String?) = withContext(Dispatchers.IO) {
        when (f.kind) {
            FindingKind.ACTION -> database.actionItemDao().getActionItemsForMeetingDirect(f.meetingId).firstOrNull { it.id == f.id }
                ?.let { database.actionItemDao().updateActionItem(it.copy(deadline = dueText)) }
            FindingKind.FOLLOW_UP -> work.followUpsFor(f.meetingId).firstOrNull { it.id == f.id }?.let { work.updateFollowUp(it.copy(deadline = dueText)) }
            else -> Unit
        }
    }

    /** Changes what a finding is: an action that was really a decision, and so on. */
    suspend fun setKind(f: Finding, kind: FindingKind) = withContext(Dispatchers.IO) {
        if (kind == f.kind) return@withContext
        database.withTransaction {
            dismiss(f)
            val id = f.id
            when (kind) {
                FindingKind.ACTION -> database.actionItemDao().insertActionItem(ActionItemEntity(id, f.meetingId, f.text, f.ownerSpeakerId, f.ownerName, f.dueText, f.confidence, false, jsonList(f.sourceSegmentIds)))
                FindingKind.FOLLOW_UP -> database.followUpDao().insertFollowUps(listOf(com.craftflowtechnologies.meetingmind.core.database.FollowUpEntity(id, f.meetingId, f.text, f.ownerSpeakerId, f.dueText, jsonList(f.sourceSegmentIds))))
                FindingKind.DECISION -> database.decisionDao().insertDecisions(listOf(com.craftflowtechnologies.meetingmind.core.database.DecisionEntity(id, f.meetingId, f.text, "DECISION", f.confidence, jsonList(f.sourceSegmentIds))))
                FindingKind.QUESTION -> database.questionDao().insertQuestions(listOf(com.craftflowtechnologies.meetingmind.core.database.QuestionEntity(id, f.meetingId, f.text, f.ownerSpeakerId, false, null, jsonList(f.sourceSegmentIds))))
            }
        }
    }

    suspend fun setAnswer(f: Finding, answer: String) = withContext(Dispatchers.IO) {
        database.questionDao().getQuestionsForMeetingDirect(f.meetingId).firstOrNull { it.id == f.id }
            ?.let { database.questionDao().updateQuestion(it.copy(answer = answer.trim(), resolved = answer.isNotBlank())) }
    }

    /** Swipe away: a wrong finding is removed. */
    suspend fun dismiss(f: Finding) = withContext(Dispatchers.IO) {
        when (f.kind) {
            FindingKind.ACTION -> database.actionItemDao().deleteActionItemById(f.id)
            FindingKind.FOLLOW_UP -> work.deleteFollowUp(f.id)
            FindingKind.DECISION -> work.deleteDecision(f.id)
            FindingKind.QUESTION -> work.deleteQuestion(f.id)
        }
    }

    suspend fun addFinding(meetingId: String, text: String, kind: FindingKind) = withContext(Dispatchers.IO) {
        val t = text.trim().ifEmpty { return@withContext }
        val id = "found_${UUID.randomUUID()}"
        when (kind) {
            FindingKind.ACTION -> database.actionItemDao().insertActionItem(ActionItemEntity(id, meetingId, t, null, null, null, 1f, false, "[]"))
            FindingKind.FOLLOW_UP -> database.followUpDao().insertFollowUps(listOf(com.craftflowtechnologies.meetingmind.core.database.FollowUpEntity(id, meetingId, t, null, null, "[]")))
            FindingKind.DECISION -> database.decisionDao().insertDecisions(listOf(com.craftflowtechnologies.meetingmind.core.database.DecisionEntity(id, meetingId, t, "DECISION", 1f, "[]")))
            FindingKind.QUESTION -> database.questionDao().insertQuestions(listOf(com.craftflowtechnologies.meetingmind.core.database.QuestionEntity(id, meetingId, t, null, false, null, "[]")))
        }
    }

    /**
     * Done in the Wrap-up (§4.3): every action and follow-up becomes a task — yours, or one you're
     * waiting on from the person who owns it — filed with the recording's note, and the recording
     * stops asking to be reviewed. Decisions and questions stay with the recording, in the logs.
     */
    suspend fun confirm(meetingId: String) = withContext(Dispatchers.IO) {
        val meeting = database.meetingDao().getMeetingById(meetingId) ?: return@withContext
        val people = WorkPeople(database)
        val speakers = database.speakerDao().getSpeakersForMeetingDirect(meetingId).associateBy { it.id }
        val now = System.currentTimeMillis()
        findings(meetingId).filter { (it.kind == FindingKind.ACTION || it.kind == FindingKind.FOLLOW_UP) && it.taskId == null }.forEach { f ->
            val ownerPerson = ownerPersonOf(f, speakers, people)
            database.taskDao().upsert(
                TaskEntity(
                    id = "task_${UUID.randomUUID()}", title = f.text, notes = "", kind = if (f.kind == FindingKind.FOLLOW_UP) TaskKind.FOLLOW_UP.name else TaskKind.TASK.name,
                    dueAt = f.dueAt, remindAt = null, repeat = "NONE", doneAt = null, personId = ownerPerson, noteId = meeting.noteId, blockId = null,
                    meetingId = meetingId, startMs = f.startMs, scripture = null, createdAt = now, updatedAt = now,
                    waitingOn = !f.isMine, ownerSpeakerId = if (!f.isMine) f.ownerSpeakerId else null, sourceItemId = f.id, space = "WORK"
                )
            )
        }
        work.setReviewed(meetingId, now)
        promoteToItems(meetingId, reviewed = true)
    }

    /** Who a finding belongs to: null for the app's own user (or nobody named), else a person. */
    private suspend fun ownerPersonOf(f: Finding, speakers: Map<String, com.craftflowtechnologies.meetingmind.core.database.SpeakerEntity>, people: WorkPeople): String? {
        val speaker = f.ownerSpeakerId?.let { speakers[it] }
        return when {
            f.isMine -> null
            speaker?.personId != null -> speaker.personId
            f.ownerName != null && !SpeakerNames.isGenericLabel(f.ownerName) -> people.resolve(f.ownerName)?.id
            else -> null
        }
    }

    /**
     * Promotes a recording's findings to items (D4.1). Called by [confirm], and once for older
     * recordings by [WorkStartup]. Safe to repeat: each finding becomes one item.
     */
    suspend fun promoteToItems(meetingId: String, reviewed: Boolean) = withContext(Dispatchers.IO) {
        val people = WorkPeople(database)
        val speakers = database.speakerDao().getSpeakersForMeetingDirect(meetingId).associateBy { it.id }
        ItemPromotion(database).promote(meetingId, findings(meetingId), reviewed) { ownerPersonOf(it, speakers, people) }
    }

    // ---------------------------------------------------------------- tasks

    /** Work tasks with owners' current names: a person's, or the speaker's until they're named. */
    fun observeTasks(): Flow<List<WorkTask>> = combine(
        work.observeWorkTasks(WorkTypeNames),
        database.peopleDao().observeWithCounts()
    ) { tasks, people ->
        val names = people.associate { it.id to it.name }
        withContext(Dispatchers.IO) { tasks.map { it.toWork(names) } }
    }

    fun observeTasksWith(personId: String): Flow<List<WorkTask>> = combine(work.observeTasksWith(personId), database.peopleDao().observeWithCounts()) { t, p ->
        val names = p.associate { it.id to it.name }; withContext(Dispatchers.IO) { t.map { it.toWork(names) } }
    }

    fun observeTasksIn(notebookId: String): Flow<List<WorkTask>> = combine(work.observeTasksIn(notebookId), database.peopleDao().observeWithCounts()) { t, p ->
        val names = p.associate { it.id to it.name }; withContext(Dispatchers.IO) { t.map { it.toWork(names) } }
    }

    private suspend fun TaskEntity.toWork(names: Map<String, String>) = WorkTask(
        id, title, dueAt, doneAt, waitingOn,
        ownerName = personId?.let { names[it] } ?: ownerSpeakerId?.let { work.speaker(it) }?.let { it.customName.ifBlank { it.originalLabel } },
        personId = personId, meetingId = meetingId, noteId = noteId, startMs = startMs, kind = enumOr(kind, TaskKind.TASK)
    )

    suspend fun addTask(title: String, waitingOnPersonId: String? = null, noteId: String? = null, dueText: String? = null) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val task = TaskEntity(
            "task_${UUID.randomUUID()}", title.trim(), "", TaskKind.TASK.name, DueDates.parse(dueText ?: title, now), null, "NONE", null,
            waitingOnPersonId, noteId, null, null, null, null, now, now, waitingOn = waitingOnPersonId != null, space = "WORK"
        )
        database.taskDao().upsert(task)
        // Something someone owes you is a commitment, so it shows under They owe and in the change log.
        if (waitingOnPersonId != null) {
            val notebook = noteId?.let { database.noteDao().getById(it) }?.notebookId
            ItemRepository(database).create(
                ItemEntity("", ItemKind.COMMITMENT.name, ItemStatus.OPEN.name, task.title, ownerPersonId = waitingOnPersonId, projectId = notebook, noteId = noteId,
                    dueAt = task.dueAt, taskId = task.id, direction = Direction.THEIRS.name, reviewed = true, source = ItemSource.USER, createdAt = now, updatedAt = now),
                links = listOf(ItemLinkEntity("", LinkType.PERSON, waitingOnPersonId, "OWNER"))
            )
        }
    }

    suspend fun toggle(taskId: String) = withContext(Dispatchers.IO) {
        // A commitment with no task of its own is ticked as the item.
        if (taskId.startsWith("item_")) return@withContext toggleCommitment(taskId)
        val t = database.taskDao().getById(taskId) ?: return@withContext
        database.taskDao().setDone(taskId, if (t.doneAt == null) System.currentTimeMillis() else null, System.currentTimeMillis())
        ItemRepository(database).onTaskDone(taskId, done = t.doneAt == null)
    }

    suspend fun snooze(taskId: String, days: Int) = withContext(Dispatchers.IO) {
        if (taskId.startsWith("item_")) {
            val due = DueDates.startOfDay(System.currentTimeMillis()) + days * 86_400_000L
            return@withContext ItemRepository(database).setDue(taskId, due, null)
        }
        val t = database.taskDao().getById(taskId) ?: return@withContext
        database.taskDao().upsert(t.copy(dueAt = DueDates.startOfDay(System.currentTimeMillis()) + days * 86_400_000L, updatedAt = System.currentTimeMillis()))
    }

    // ---------------------------------------------------------------- logs

    /** Decisions in force, from items (D4.3): superseded and reversed ones drop out of the working list. */
    fun observeDecisions(limit: Int = 200): Flow<List<FindingRow>> =
        database.itemDao().observeDecisions(limit).map { l -> l.filter { it.status == ItemStatus.ACTIVE.name || it.status == ItemStatus.PROPOSED.name }.map { it.toRow() } }

    /** The whole decision log, newest first, including what was replaced. */
    fun observeDecisionLog(limit: Int = 500): Flow<List<FindingRow>> = database.itemDao().observeDecisions(limit).map { l -> l.map { it.toRow() } }

    fun observeOpenQuestions(limit: Int = 200): Flow<List<FindingRow>> = database.itemDao().observeOpenQuestions(limit).map { l -> l.map { it.toRow() } }

    private suspend fun ItemEntity.toRow(): FindingRow = withContext(Dispatchers.IO) {
        val meeting = meetingId?.let { database.meetingDao().getMeetingById(it) }
        FindingRow(id, meetingId.orEmpty(), text, status, answerText, status == ItemStatus.ANSWERED.name,
            database.itemDao().evidenceFor(id).firstOrNull()?.segmentIdsJson ?: "[]", meeting?.title.orEmpty(), createdAt, noteId, meeting?.recordingType.orEmpty())
    }

    /**
     * Commitments as the Work lists show them (D4.2): what you owe, or what you're owed. A row
     * carries its task's id when it has one, so editing and nudging work as they did.
     */
    fun observeCommitments(direction: Direction): Flow<List<WorkTask>> = combine(
        database.itemDao().observeCommitments(direction.name), database.peopleDao().observeWithCounts()
    ) { list, people ->
        val names = people.associate { it.id to it.name }
        withContext(Dispatchers.IO) {
            list.filter { it.status != ItemStatus.CANCELLED.name }.map { i ->
                WorkTask(
                    id = i.taskId ?: i.id, title = i.text, dueAt = i.dueAt, doneAt = if (i.status == ItemStatus.COMPLETED.name) (i.closedAt ?: i.updatedAt) else null,
                    waitingOn = direction == Direction.THEIRS,
                    ownerName = i.ownerPersonId?.let { names[it] } ?: i.ownerSpeakerId?.let { work.speaker(it) }?.let { it.customName.ifBlank { it.originalLabel } },
                    personId = i.ownerPersonId, meetingId = i.meetingId, noteId = i.noteId,
                    startMs = database.itemDao().evidenceFor(i.id).firstOrNull()?.startMs, kind = TaskKind.TASK
                )
            }
        }
    }

    private suspend fun toggleCommitment(itemId: String) {
        val items = ItemRepository(database)
        val item = items.get(itemId) ?: return
        items.setStatus(itemId, if (item.status == ItemStatus.COMPLETED.name) ItemStatus.OPEN else ItemStatus.COMPLETED)
    }
    fun observeWorkNotes(limit: Int = 30): Flow<List<NoteEntity>> = work.observeWorkNotes(WorkTypeNames, limit)

    suspend fun resolveQuestion(id: String, meetingId: String, answer: String?) = withContext(Dispatchers.IO) {
        // From the Work lists the id is the item's; the recording's own question is kept in step.
        database.itemDao().getById(id)?.let { item ->
            ItemRepository(database).answer(id, answer)
            item.sourceFindingId?.let { qid -> database.questionDao().getQuestionsForMeetingDirect(item.meetingId ?: meetingId).firstOrNull { it.id == qid } }
                ?.let { database.questionDao().updateQuestion(it.copy(resolved = true, answer = answer?.trim()?.ifEmpty { null } ?: it.answer)) }
            return@withContext
        }
        database.questionDao().getQuestionsForMeetingDirect(meetingId).firstOrNull { it.id == id }
            ?.let { database.questionDao().updateQuestion(it.copy(resolved = true, answer = answer?.trim()?.ifEmpty { null } ?: it.answer)) }
    }

    // ---------------------------------------------------------------- follow-ups

    /**
     * Recent reviewed work recordings with something agreed but no follow-up sent yet
     * (Home and Work → Follow-ups to send).
     */
    fun observeFollowUps(days: Int = 14): Flow<List<MeetingRow>> = work.observeByTypes(WorkTypeNames, 60).map { list ->
        withContext(Dispatchers.IO) {
            val since = System.currentTimeMillis() - days * 86_400_000L
            list.filter { it.createdAt >= since && it.status == "READY" && it.reviewedAt != null && it.noteId != null }
                .filter { m ->
                    val meta = database.noteDao().getById(m.noteId!!)?.metadataJson ?: return@filter false
                    followUpSentAt(meta) == null && JSONObject(meta).optString(FOLLOW_UP_SKIPPED) != "1" &&
                        (work.tasksForMeeting(m.id).isNotEmpty() || database.decisionDao().getDecisionsForMeetingDirect(m.id).isNotEmpty())
                }.map { it.row() }
        }
    }

    /** Records that the follow-up went, and closes any follow-up task the person owned. */
    suspend fun markFollowUpSent(meetingId: String, channel: Channel) = withContext(Dispatchers.IO) {
        val m = database.meetingDao().getMeetingById(meetingId) ?: return@withContext
        m.noteId?.let { setNoteMeta(it, mapOf(FOLLOW_UP_SENT to System.currentTimeMillis().toString(), FOLLOW_UP_CHANNEL to channel.name)) }
        work.tasksForMeeting(meetingId).filter { it.kind == TaskKind.FOLLOW_UP.name && !it.waitingOn && it.doneAt == null }
            .forEach {
                database.taskDao().setDone(it.id, System.currentTimeMillis(), System.currentTimeMillis())
                ItemRepository(database).onTaskDone(it.id, done = true)
            }
    }

    suspend fun skipFollowUp(meetingId: String) = withContext(Dispatchers.IO) {
        database.meetingDao().getMeetingById(meetingId)?.noteId?.let { setNoteMeta(it, mapOf(FOLLOW_UP_SKIPPED to "1")) }
    }

    private suspend fun setNoteMeta(noteId: String, values: Map<String, String>) {
        val note = database.noteDao().getById(noteId) ?: return
        val meta = runCatching { JSONObject(note.metadataJson) }.getOrDefault(JSONObject())
        values.forEach { (k, v) -> meta.put(k, v) }
        database.noteDao().upsert(note.copy(metadataJson = meta.toString()))
    }

    // ---------------------------------------------------------------- projects

    /** Files a note, and its tasks, under a project. */
    suspend fun setProject(noteId: String, notebookId: String?) = withContext(Dispatchers.IO) {
        val note = database.noteDao().getById(noteId) ?: return@withContext
        database.noteDao().upsert(note.copy(notebookId = notebookId, updatedAt = System.currentTimeMillis()))
    }

    companion object {
        const val FOLLOW_UP_SENT = "followUpSentAt"
        const val FOLLOW_UP_CHANNEL = "followUpChannel"
        const val FOLLOW_UP_SKIPPED = "followUpSkipped"

        fun followUpSentAt(metadataJson: String): Long? =
            runCatching { JSONObject(metadataJson).optString(FOLLOW_UP_SENT).toLongOrNull() }.getOrNull()
    }
}
