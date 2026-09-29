package com.example.core.work

import com.example.core.database.ItemEntity
import com.example.core.database.MeetMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/** A recording waiting for its Wrap-up, or for its follow-up to be sent. */
data class MeetingRow(val meetingId: String, val noteId: String?, val title: String, val at: Long, val durationMs: Long, val count: Int)

/**
 * Everything the Work screens do to items (docs/PLAN_PROFESSIONAL.md §5.2). Every change marks the
 * item reviewed: touching it is reviewing it.
 */
class WorkRepository(private val database: MeetMindDatabase) {
    private val dao = database.itemDao()

    fun observeOpen(): Flow<List<WorkItem>> = dao.observeOpen().map { l -> l.map { it.toWorkItem() } }.flowOn(Dispatchers.IO)
    fun observeDecisions(): Flow<List<WorkItem>> = dao.observeByKind(ItemKind.DECISION.name).map { l -> l.map { it.toWorkItem() } }.flowOn(Dispatchers.IO)
    fun observeForMeeting(meetingId: String): Flow<List<WorkItem>> = dao.observeForMeeting(meetingId).map { l -> l.map { it.toWorkItem() } }.flowOn(Dispatchers.IO)
    fun observeForPerson(personId: String): Flow<List<WorkItem>> = dao.observeForPerson(personId).map { l -> l.map { it.toWorkItem() } }.flowOn(Dispatchers.IO)
    fun observeUnreviewedMeetingIds(): Flow<List<String>> = dao.observeUnreviewedMeetingIds()

    suspend fun get(id: String): WorkItem? = withContext(Dispatchers.IO) { dao.getById(id)?.toWorkItem() }

    private suspend fun change(id: String, block: (ItemEntity) -> ItemEntity) = withContext(Dispatchers.IO) {
        val e = dao.getById(id) ?: return@withContext
        dao.update(block(e).copy(reviewed = true, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setStatus(id: String, status: ItemStatus) = change(id) {
        it.copy(
            status = status.name,
            completedAt = if (status == ItemStatus.DONE) System.currentTimeMillis() else null,
            answeredAt = if (status == ItemStatus.ANSWERED) System.currentTimeMillis() else it.answeredAt
        )
    }

    suspend fun setText(id: String, text: String) = change(id) { it.copy(text = text.trim().ifEmpty { it.text }) }

    suspend fun setKind(id: String, kind: ItemKind) = change(id) {
        it.copy(kind = kind.name, subtype = if (kind == ItemKind.DECISION) "DECISION" else null, status = ItemStatus.OPEN.name)
    }

    suspend fun setDue(id: String, dueAt: Long?, text: String?) = change(id) { it.copy(dueAt = dueAt, dueText = text) }

    /** Moves an open task to [days] from today. */
    suspend fun snooze(id: String, days: Int) = change(id) {
        val day = DueDates.startOfDay(System.currentTimeMillis()) + days * 24L * 60 * 60 * 1000
        it.copy(dueAt = day, dueText = if (days == 1) "tomorrow" else it.dueText)
    }

    /** The owner is a speaker in the recording, a person, or a typed name. Exactly one wins. */
    suspend fun setOwner(id: String, speakerId: String? = null, personId: String? = null, name: String? = null) = change(id) {
        it.copy(ownerSpeakerId = speakerId, ownerPersonId = personId, ownerName = name)
    }

    suspend fun setAnswer(id: String, answer: String) = change(id) {
        it.copy(answer = answer.trim(), status = ItemStatus.ANSWERED.name, answeredAt = System.currentTimeMillis())
    }

    suspend fun delete(id: String): ItemEntity? = withContext(Dispatchers.IO) {
        dao.getById(id)?.also { dao.deleteById(id) }
    }

    suspend fun restore(entity: ItemEntity) = withContext(Dispatchers.IO) { dao.upsert(entity) }

    suspend fun add(
        text: String,
        kind: ItemKind = ItemKind.TASK,
        meetingId: String? = null,
        noteId: String? = null,
        ownerPersonId: String? = null,
        dueText: String? = null,
        source: ItemSource = ItemSource.USER,
        startMs: Long? = null,
        reviewed: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val note = noteId ?: meetingId?.let { database.meetingDao().getMeetingById(it)?.noteId }
        val project = note?.let { database.noteDao().getById(it)?.notebookId }
        val id = UUID.randomUUID().toString()
        dao.upsert(
            WorkItem(
                id = id, meetingId = meetingId, noteId = note, kind = kind, text = text.trim(), ownerPersonId = ownerPersonId,
                dueAt = DueDates.parse(dueText, now), dueText = dueText, projectId = project, source = source,
                reviewed = reviewed, sourceStartMs = startMs, createdAt = now
            ).toEntity()
        )
        id
    }

    suspend fun markReviewed(meetingId: String) = withContext(Dispatchers.IO) { dao.markReviewed(meetingId, System.currentTimeMillis()) }

    /** Files a recording's note, and its items, under a project notebook. */
    suspend fun setProject(noteId: String, notebookId: String?) = withContext(Dispatchers.IO) {
        val note = database.noteDao().getById(noteId) ?: return@withContext
        database.noteDao().upsert(note.copy(notebookId = notebookId, updatedAt = System.currentTimeMillis()))
        dao.setProjectForNote(noteId, notebookId)
    }

    suspend fun meetingRow(meetingId: String): MeetingRow? = withContext(Dispatchers.IO) {
        val m = database.meetingDao().getMeetingById(meetingId) ?: return@withContext null
        val count = dao.getRawForMeeting(meetingId).count { !it.reviewed }
        MeetingRow(m.id, m.noteId, m.title, m.createdAt, m.durationMs, count)
    }

    /**
     * Recent work recordings whose findings were reviewed but whose follow-up hasn't gone yet
     * (the Home "Follow-ups to send" list). Only recordings with something worth sending.
     */
    suspend fun followUpsToSend(days: Int = 14): List<MeetingRow> = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val out = mutableListOf<MeetingRow>()
        for (m in database.meetingDao().getAllMeetingsDirect()) {
            if (m.createdAt < since || m.status != "READY" || m.noteId == null) continue
            val type = runCatching { com.example.core.model.RecordingType.valueOf(m.recordingType) }.getOrDefault(com.example.core.model.RecordingType.GENERAL)
            if (com.example.core.model.Workflows.space(type) != com.example.core.model.NotebookSpace.WORK) continue
            val items = dao.getRawForMeeting(m.id)
            if (items.isEmpty() || items.any { !it.reviewed }) continue
            if (items.none { it.kind == ItemKind.DECISION.name || it.kind == ItemKind.TASK.name }) continue
            val meta = database.noteDao().getById(m.noteId)?.metadataJson ?: continue
            if (followUpSentAt(meta) != null || runCatching { JSONObject(meta).optString(FOLLOW_UP_SKIPPED) }.getOrNull() == "1") continue
            out += MeetingRow(m.id, m.noteId, m.title, m.createdAt, m.durationMs, items.size)
        }
        out
    }

    /** Records that the follow-up went, and closes any follow-up task the person owned. */
    suspend fun markFollowUpSent(meetingId: String, channel: Channel) = withContext(Dispatchers.IO) {
        val m = database.meetingDao().getMeetingById(meetingId) ?: return@withContext
        m.noteId?.let { setNoteMeta(it, mapOf(FOLLOW_UP_SENT to System.currentTimeMillis().toString(), FOLLOW_UP_CHANNEL to channel.name)) }
        val self = database.peopleDao().getSelf()?.id
        dao.getRawForMeeting(meetingId).filter {
            it.subtype == SUBTYPE_FOLLOW_UP && it.status == ItemStatus.OPEN.name && (it.ownerPersonId == null || it.ownerPersonId == self) && it.ownerSpeakerId == null
        }.forEach { dao.update(it.copy(status = ItemStatus.DONE.name, completedAt = System.currentTimeMillis(), reviewed = true)) }
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

    companion object {
        const val FOLLOW_UP_SENT = "followUpSentAt"
        const val FOLLOW_UP_CHANNEL = "followUpChannel"
        const val FOLLOW_UP_SKIPPED = "followUpSkipped"

        fun followUpSentAt(metadataJson: String): Long? =
            runCatching { JSONObject(metadataJson).optString(FOLLOW_UP_SENT).toLongOrNull() }.getOrNull()
    }
}
