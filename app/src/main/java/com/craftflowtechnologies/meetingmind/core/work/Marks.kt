package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ActionItemEntity
import com.craftflowtechnologies.meetingmind.core.database.DecisionEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.QuestionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

/** What a tap during recording means (docs/PLAN_PROFESSIONAL.md §4.2). */
enum class MarkKind(val label: String) {
    KEY("Key moment"), ACTION("Action"), QUESTION("Question"),
    // R-1: the buttons that depend on what is being recorded. Stored in the same list.
    DECISION("Decision"), SCRIPTURE("Scripture"), NOTE("Note"), PRAYER("Prayer point")
}

/** A tap at [atMs] into the recording, with the words the person typed for it, if any. */
data class Mark(val kind: MarkKind, val atMs: Long, val text: String? = null)

/**
 * Marks tapped while recording, kept on the recording's note. After processing, an action or
 * question mark meets what extraction found: a finding near it is the same thing, and is marked
 * certain; if nothing was found, the words said at that moment become the finding, so nothing the
 * person flagged is missed. Key moments stay on the note to jump back to.
 */
object Marks {
    const val KEY = "marks"
    /** How far from a mark a finding can be and still be the same thing. */
    private const val WINDOW_MS = 45_000L

    suspend fun save(database: MeetMindDatabase, meetingId: String, marks: List<Mark>) = withContext(Dispatchers.IO) {
        if (marks.isEmpty()) return@withContext
        val noteId = database.meetingDao().getMeetingById(meetingId)?.noteId ?: return@withContext
        val note = database.noteDao().getById(noteId) ?: return@withContext
        val meta = runCatching { JSONObject(note.metadataJson) }.getOrDefault(JSONObject())
        val list = runCatching { JSONArray(meta.optString(KEY, "[]")) }.getOrDefault(JSONArray())
        marks.forEach { list.put(JSONObject().put("kind", it.kind.name).put("at", it.atMs).apply { it.text?.let { t -> put("text", t) } }) }
        meta.put(KEY, list.toString())
        database.noteDao().upsert(note.copy(metadataJson = meta.toString()))
    }

    fun read(metadataJson: String?): List<Mark> = runCatching {
        val a = JSONArray(JSONObject(metadataJson ?: "{}").optString(KEY, "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { Mark(enumOr(it.optString("kind"), MarkKind.KEY), it.optLong("at"), it.optString("text").ifBlank { null }) } }
    }.getOrDefault(emptyList())

    fun keyMoments(metadataJson: String?): List<Long> = read(metadataJson).filter { it.kind == MarkKind.KEY }.map { it.atMs }

    /** The marks that become (or confirm) findings; the rest become note sections ([MarkerNote]). */
    private val RECONCILED = setOf(MarkKind.ACTION, MarkKind.QUESTION, MarkKind.DECISION)

    data class Segment(val id: String, val startMs: Long, val endMs: Long, val speakerId: String?, val text: String)

    /** Runs after extraction has written the recording's findings. */
    suspend fun reconcile(database: MeetMindDatabase, meetingId: String, segments: List<Segment>) = withContext(Dispatchers.IO) {
        val noteId = database.meetingDao().getMeetingById(meetingId)?.noteId ?: return@withContext
        val marks = read(database.noteDao().getById(noteId)?.metadataJson).filter { it.kind in RECONCILED }
        if (marks.isEmpty()) return@withContext
        val starts = segments.associate { it.id to it.startMs }
        fun startOf(json: String) = idList(json).firstNotNullOfOrNull { starts[it] }
        fun said(at: Long) = segments.lastOrNull { it.startMs <= at } ?: segments.firstOrNull()
        fun words(s: Segment) = s.text.trim().let { if (it.length > 160) it.take(157).trimEnd() + "…" else it }
        val actions = database.actionItemDao()
        val questions = database.questionDao()
        val decisions = database.decisionDao()
        // Same mark, same id: reprocessing a recording replaces what a mark made instead of adding to it.
        fun idOf(m: Mark) = "mark_${meetingId}_${m.kind.name}_${m.atMs}"
        marks.forEach { m ->
            when (m.kind) {
                MarkKind.ACTION -> {
                    val match = actions.getActionItemsForMeetingDirect(meetingId).filter { a -> a.id != idOf(m) && startOf(a.sourceSegmentIdsJson)?.let { abs(it - m.atMs) <= WINDOW_MS } == true }
                        .minByOrNull { abs(startOf(it.sourceSegmentIdsJson)!! - m.atMs) }
                    if (match != null) actions.updateActionItem(match.copy(confidence = maxOf(match.confidence ?: 0f, 0.95f)))
                    else said(m.atMs)?.let { s -> actions.insertActionItem(ActionItemEntity(idOf(m), meetingId, m.text ?: words(s), null, null, null, 0.95f, false, JSONArray(listOf(s.id)).toString())) }
                }
                MarkKind.QUESTION -> {
                    val match = questions.getQuestionsForMeetingDirect(meetingId).any { q -> q.id != idOf(m) && startOf(q.sourceSegmentIdsJson)?.let { abs(it - m.atMs) <= WINDOW_MS } == true }
                    if (!match) said(m.atMs)?.let { s -> questions.insertQuestions(listOf(QuestionEntity(idOf(m), meetingId, m.text ?: words(s), s.speakerId, false, null, JSONArray(listOf(s.id)).toString()))) }
                }
                MarkKind.DECISION -> {
                    val match = decisions.getDecisionsForMeetingDirect(meetingId).any { d -> d.id != idOf(m) && startOf(d.sourceSegmentIdsJson)?.let { abs(it - m.atMs) <= WINDOW_MS } == true }
                    if (!match) said(m.atMs)?.let { s -> decisions.insertDecisions(listOf(DecisionEntity(idOf(m), meetingId, m.text ?: words(s), "DECISION", 0.95f, JSONArray(listOf(s.id)).toString()))) }
                }
                else -> Unit
            }
        }
    }
}
