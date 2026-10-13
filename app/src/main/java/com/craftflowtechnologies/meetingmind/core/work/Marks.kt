package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.core.database.ActionItemEntity
import com.craftflowtechnologies.meetingmind.core.database.DecisionEntity
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.database.QuestionEntity
import com.craftflowtechnologies.meetingmind.core.database.RecordingMarkerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID
import kotlin.math.abs

/** What a tap during recording means (docs/PLAN_PROFESSIONAL.md §4.2). */
enum class MarkKind(val label: String) {
    KEY("Key moment"), ACTION("Action"), QUESTION("Question"),
    // R-1: the buttons that depend on what is being recorded. Stored in the same list.
    DECISION("Decision"), SCRIPTURE("Scripture"), NOTE("Note"), PRAYER("Prayer point"),
    // Schema 25. Appended last: stored by name, so the seven above keep their stored values.
    IDEA("Idea")
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
    /** How far from a mark a finding can be and still be the same thing. */
    private const val WINDOW_MS = 45_000L

    /**
     * Saves the marks tapped in one recording, to that recording. Each mark is a row owned by its
     * recording (recording_markers), so reconciling or displaying one recording can never reach
     * another's marks. Stage 2 moves the write to tap time; until then this runs once, at stop.
     */
    suspend fun save(database: MeetMindDatabase, meetingId: String, marks: List<Mark>) = withContext(Dispatchers.IO) {
        if (marks.isEmpty()) return@withContext
        if (database.meetingDao().getMeetingById(meetingId) == null) return@withContext
        val dao = database.recordingMarkerDao()
        val first = dao.nextSequence(meetingId)
        val now = System.currentTimeMillis()
        dao.insertAll(marks.mapIndexed { i, m ->
            RecordingMarkerEntity(
                id = UUID.randomUUID().toString(),
                meetingId = meetingId,
                sequence = first + i,
                timestampMs = m.atMs,
                kind = m.kind.name,
                text = m.text,
                createdAt = now,
                updatedAt = now
            )
        })
    }

    /**
     * The marks this recording owns, in timestamp order. A stored kind this build does not know is
     * skipped here, and stays in the table untouched.
     */
    suspend fun forMeeting(database: MeetMindDatabase, meetingId: String): List<Mark> = withContext(Dispatchers.IO) {
        database.recordingMarkerDao().forMeeting(meetingId).mapNotNull { row ->
            MarkKind.entries.firstOrNull { it.name == row.kind }?.let { Mark(it, row.timestampMs, row.text) }
        }
    }

    /** The highlights this recording owns, for jumping back to them. */
    suspend fun keyMoments(database: MeetMindDatabase, meetingId: String): List<Long> =
        forMeeting(database, meetingId).filter { it.kind == MarkKind.KEY }.map { it.atMs }

    /** The marks that become (or confirm) findings; the rest become note sections ([MarkerNote]). */
    private val RECONCILED = setOf(MarkKind.ACTION, MarkKind.QUESTION, MarkKind.DECISION)

    data class Segment(val id: String, val startMs: Long, val endMs: Long, val speakerId: String?, val text: String)

    /** Runs after extraction has written the recording's findings. Only this recording's own marks are reconciled. */
    suspend fun reconcile(database: MeetMindDatabase, meetingId: String, segments: List<Segment>) = withContext(Dispatchers.IO) {
        // Only this recording's own marks. An orphan recording (no note) is reconciled too.
        val marks = forMeeting(database, meetingId).filter { it.kind in RECONCILED }
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
