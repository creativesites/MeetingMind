package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.faith.GeneratedSections
import com.craftflowtechnologies.meetingmind.core.common.Formatters
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.ScriptureOrigin
import com.craftflowtechnologies.meetingmind.core.model.ScriptureRef
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser

/**
 * What the person marked while recording, written into the recording's note (R-1).
 *
 * Highlights become "Key moments", each with the words said around it and a tap-to-hear time;
 * scripture marks become scripture blocks; typed notes and prayer points go under their own
 * headings. Decisions, actions and questions are not here: [Marks.reconcile] merges them into the
 * findings the note already shows.
 *
 * Pure and idempotent: every block and reference id comes from the mark, and each section is
 * replaced by its key, so processing the same recording again gives the same note.
 */
object MarkerNote {
    const val KEY_MOMENTS = "marker_key_moments"
    const val KEY_SCRIPTURE = "marker_scripture"
    const val KEY_NOTES = "marker_notes"
    const val KEY_PRAYER = "marker_prayer"
    val keys = setOf(KEY_MOMENTS, KEY_SCRIPTURE, KEY_NOTES, KEY_PRAYER)

    /** Highlights are pressed just after the thing is said, so look back further than forward. */
    private const val LOOK_BACK_MS = 20_000L
    private const val LOOK_AHEAD_MS = 4_000L
    private const val MAX_QUOTE = 400

    fun build(noteId: String, meetingId: String, marks: List<Mark>, segments: List<Marks.Segment>, now: Long = System.currentTimeMillis()): GeneratedSections {
        val blocks = mutableListOf<NoteBlock>()
        val refs = mutableListOf<ScriptureRef>()
        val sorted = marks.sortedBy { it.atMs }

        fun heading(key: String, title: String) {
            blocks += NoteBlock("mk_${meetingId}_h_$key", noteId, 0, NoteBlockType.HEADING_2, RichText.plain(title), source = BlockSource.AI, sectionKey = key)
        }
        fun at(ms: Long) = mapOf(NoteBlock.PAYLOAD_MEETING_ID to meetingId, NoteBlock.PAYLOAD_START_MS to ms.toString())
        fun id(m: Mark) = "mk_${meetingId}_${m.kind.name}_${m.atMs}"

        sorted.filter { it.kind == MarkKind.KEY }.takeIf { it.isNotEmpty() }?.let { list ->
            heading(KEY_MOMENTS, "Key moments")
            list.forEach { m ->
                val near = segments.filter { it.endMs >= m.atMs - LOOK_BACK_MS && it.startMs <= m.atMs + LOOK_AHEAD_MS && it.text.isNotBlank() }.sortedBy { it.startMs }
                if (near.isEmpty()) {
                    blocks += NoteBlock(id(m), noteId, 0, NoteBlockType.PARAGRAPH, RichText.plain("Marked at ${stamp(m.atMs)}"),
                        payload = at(m.atMs), source = BlockSource.TRANSCRIPT, sectionKey = KEY_MOMENTS)
                } else {
                    val quote = near.joinToString(" ") { it.text.trim() }.let { if (it.length > MAX_QUOTE) it.take(MAX_QUOTE - 1).trimEnd() + "…" else it }
                    blocks += NoteBlock(id(m), noteId, 0, NoteBlockType.TRANSCRIPT_EXCERPT, RichText.plain(quote),
                        payload = at(near.first().startMs),
                        source = BlockSource.TRANSCRIPT, sourceSegmentIds = near.map { it.id }, sectionKey = KEY_MOMENTS)
                }
            }
        }

        sorted.filter { it.kind == MarkKind.SCRIPTURE }.takeIf { it.isNotEmpty() }?.let { list ->
            heading(KEY_SCRIPTURE, "Scripture you marked")
            list.forEach { m ->
                val typed = m.text.orEmpty()
                val ref = ScriptureReferenceParser.parse(typed)
                if (ref == null) {
                    blocks += NoteBlock(id(m), noteId, 0, NoteBlockType.PARAGRAPH, RichText.plain(typed.ifBlank { "Scripture at ${stamp(m.atMs)}" }),
                        payload = at(m.atMs), source = BlockSource.USER, sectionKey = KEY_SCRIPTURE)
                } else {
                    val refId = "mkref_${meetingId}_${m.atMs}"
                    blocks += NoteBlock(id(m), noteId, 0, NoteBlockType.SCRIPTURE, RichText.plain(ref.display()),
                        payload = at(m.atMs) + mapOf(NoteBlock.PAYLOAD_SCRIPTURE_REF_ID to refId, "reference" to ref.display()),
                        source = BlockSource.SCRIPTURE, sectionKey = KEY_SCRIPTURE)
                    refs += ScriptureRef(refId, noteId, id(m), ref.usfm, ref.chapter, ref.verseStart, ref.verseEnd, null, ScriptureOrigin.USER, meetingId, null, m.atMs, now)
                }
            }
        }

        fun lines(kind: MarkKind, key: String, title: String, type: NoteBlockType) {
            val list = sorted.filter { it.kind == kind }
            if (list.isEmpty()) return
            heading(key, title)
            list.forEach { m ->
                val text = m.text.orEmpty().ifBlank { "${kind.label} at ${stamp(m.atMs)}" }
                blocks += NoteBlock(id(m), noteId, 0, type, RichText.plain(text), payload = at(m.atMs), source = BlockSource.USER, sectionKey = key)
            }
        }
        lines(MarkKind.NOTE, KEY_NOTES, "Your notes", NoteBlockType.PARAGRAPH)
        lines(MarkKind.PRAYER, KEY_PRAYER, "Prayer points", NoteBlockType.BULLET)

        return GeneratedSections(blocks, refs, keys)
    }

    private fun stamp(ms: Long) = Formatters.formatDurationHms(ms)

    /** Runs after processing has written the note's own sections. Does nothing when nothing was marked for the note. */
    suspend fun apply(context: Context, database: MeetMindDatabase, meetingId: String, segments: List<Marks.Segment>) {
        val noteId = database.meetingDao().getMeetingById(meetingId)?.noteId ?: return
        val marks = Marks.read(database.noteDao().getById(noteId)?.metadataJson)
        val generated = build(noteId, meetingId, marks, segments)
        if (generated.blocks.isEmpty()) return
        NoteRepository(context, database).applyGeneratedSections(noteId, generated.blocks, generated.refs, generated.keys)
    }
}
