package com.example.core.work

import com.example.core.database.MeetMindDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** What a tap during recording means (docs/PLAN_PROFESSIONAL.md §4.2). */
enum class MarkKind(val label: String) { KEY("Key moment"), ACTION("Action"), QUESTION("Question") }

data class Mark(val kind: MarkKind, val atMs: Long)

/**
 * Marks tapped while recording. An action or question mark becomes an item at that moment, so
 * nothing the person flagged can be missed; a key moment is kept on the note to jump back to.
 * After processing, a mark that extraction also found is folded into the extracted item, and one
 * it didn't find takes the words said at that moment.
 */
object Marks {
    const val KEY_MOMENTS = "keyMoments"
    /** How far from a mark an extracted item can be and still be the same thing. */
    private const val WINDOW_MS = 45_000L

    suspend fun save(database: MeetMindDatabase, meetingId: String, marks: List<Mark>) = withContext(Dispatchers.IO) {
        if (marks.isEmpty()) return@withContext
        val work = WorkRepository(database)
        marks.filter { it.kind != MarkKind.KEY }.forEach { m ->
            val kind = if (m.kind == MarkKind.QUESTION) ItemKind.QUESTION else ItemKind.TASK
            work.add(
                text = "${m.kind.label} at ${clock(m.atMs)}", kind = kind, meetingId = meetingId,
                source = ItemSource.MARK, startMs = m.atMs, reviewed = false
            )
        }
        val keys = marks.filter { it.kind == MarkKind.KEY }.map { it.atMs }
        if (keys.isNotEmpty()) {
            val noteId = database.meetingDao().getMeetingById(meetingId)?.noteId ?: return@withContext
            val note = database.noteDao().getById(noteId) ?: return@withContext
            val meta = runCatching { JSONObject(note.metadataJson) }.getOrDefault(JSONObject())
            val existing = runCatching { JSONArray(meta.optString(KEY_MOMENTS, "[]")) }.getOrDefault(JSONArray())
            keys.forEach { existing.put(it) }
            meta.put(KEY_MOMENTS, existing.toString())
            database.noteDao().upsert(note.copy(metadataJson = meta.toString()))
        }
    }

    /**
     * Runs after extraction. [segments] are (startMs, endMs, speakerId, text) for the transcript.
     */
    suspend fun reconcile(database: MeetMindDatabase, meetingId: String, segments: List<Segment>) = withContext(Dispatchers.IO) {
        val dao = database.itemDao()
        val all = dao.getRawForMeeting(meetingId)
        val marks = all.filter { it.source == ItemSource.MARK.name && it.sourceStartMs != null }
        val found = all.filter { it.source == ItemSource.AI.name }
        marks.forEach { mark ->
            val at = mark.sourceStartMs!!
            val match = found.filter { it.kind == mark.kind && it.sourceStartMs != null && abs(it.sourceStartMs - at) <= WINDOW_MS }
                .minByOrNull { abs(it.sourceStartMs!! - at) }
            if (match != null) {
                // Extraction found it: keep its words, and the person's mark means it's not a guess.
                dao.update(match.copy(confidence = maxOf(match.confidence ?: 0f, 0.95f)))
                dao.deleteById(mark.id)
            } else if (mark.text.endsWith(" at ${clock(at)}")) {
                // Not found: take what was said as the mark was tapped (a mark comes a moment after the words).
                val seg = segments.lastOrNull { it.startMs <= at } ?: segments.firstOrNull()
                if (seg != null) {
                    val words = seg.text.trim().let { if (it.length > 160) it.take(157).trimEnd() + "…" else it }
                    dao.update(mark.copy(text = words, ownerSpeakerId = if (mark.kind == ItemKind.QUESTION.name) seg.speakerId else mark.ownerSpeakerId,
                        sourceSegmentIdsJson = JSONArray(listOf(seg.id)).toString(), updatedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    data class Segment(val id: String, val startMs: Long, val endMs: Long, val speakerId: String?, val text: String)

    fun keyMoments(metadataJson: String): List<Long> = runCatching {
        val a = JSONArray(JSONObject(metadataJson).optString(KEY_MOMENTS, "[]")); (0 until a.length()).map { a.getLong(it) }
    }.getOrDefault(emptyList())

    fun clock(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}
