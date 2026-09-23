package com.example.ai.faith

import com.example.core.model.BlockSource
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.model.ScriptureOrigin
import com.example.core.model.ScriptureRef
import com.example.core.model.TranscriptSegment
import com.example.core.model.Workflows
import com.example.core.notes.RichText
import com.example.core.repository.NoteRepository
import com.example.core.scripture.DetectedScripture
import com.example.core.scripture.ScriptureDetector
import com.example.core.scripture.ScriptureReference

/** The generated part of a sermon note: its blocks, and the scripture references they show. */
data class GeneratedSections(val blocks: List<NoteBlock>, val refs: List<ScriptureRef>, val keys: Set<String>)

/**
 * Turns a sermon's extraction and the scripture heard in it into note sections (docs/PLAN_V1.md §5).
 *
 * Pure: it builds blocks and references and writes nothing, so the whole layout is unit-tested.
 * Every generated block carries the transcript segments it came from, and every scripture block a
 * moment in the audio, so the note is evidence you can tap, not a summary you have to trust.
 */
object SermonNoteBuilder {

    const val KEY_ALL_SCRIPTURE = "scripture_refs"

    fun build(
        noteId: String,
        meetingId: String,
        segments: List<TranscriptSegment>,
        extraction: SermonExtraction?,
        detections: List<DetectedScripture>,
        now: Long = System.currentTimeMillis()
    ): GeneratedSections {
        val bySegment = segments.associateBy { it.id }
        val blocks = mutableListOf<NoteBlock>()
        val refs = mutableListOf<ScriptureRef>()
        val titles = Workflows.template(com.example.core.model.RecordingType.SERMON).sections.associate { it.key to it.title }

        fun heading(key: String, title: String = titles[key] ?: key) {
            blocks += NoteBlock(NoteRepository.newId("block"), noteId, 0, NoteBlockType.HEADING_2, RichText.plain(title), source = BlockSource.AI, sectionKey = key)
        }

        fun text(key: String, type: NoteBlockType, item: CitedText) {
            blocks += NoteBlock(
                NoteRepository.newId("block"), noteId, 0, type, RichText.plain(item.text),
                payload = startOf(item.segmentIds, bySegment)?.let { mapOf(NoteBlock.PAYLOAD_MEETING_ID to meetingId, NoteBlock.PAYLOAD_START_MS to it.toString()) }.orEmpty(),
                source = BlockSource.AI, sourceSegmentIds = item.segmentIds, sectionKey = key
            )
        }

        fun scripture(key: String, ref: ScriptureReference, origin: ScriptureOrigin, segmentId: String?, startMs: Long?, source: BlockSource) {
            val blockId = NoteRepository.newId("block")
            val refId = NoteRepository.newId("scripture")
            blocks += NoteBlock(
                blockId, noteId, 0, NoteBlockType.SCRIPTURE, RichText.plain(ref.display()),
                payload = buildMap {
                    put(NoteBlock.PAYLOAD_SCRIPTURE_REF_ID, refId)
                    put("reference", ref.display())
                    put(NoteBlock.PAYLOAD_MEETING_ID, meetingId)
                    startMs?.let { put(NoteBlock.PAYLOAD_START_MS, it.toString()) }
                },
                source = source, sourceSegmentIds = listOfNotNull(segmentId), sectionKey = key
            )
            refs += ScriptureRef(refId, noteId, blockId, ref.usfm, ref.chapter, ref.verseStart, ref.verseEnd, null, origin, meetingId, segmentId, startMs, now)
        }

        val mentions = ScriptureDetector.mentions(detections)

        // Scripture the sermon was built on: the model's picks (re-checked by the parser), or — with
        // no model — the passages mentioned most, which is what a listener would call the text.
        val main = extraction?.mainScripture.orEmpty()
        if (main.isNotEmpty()) {
            heading("main_scripture")
            main.forEach { c ->
                val heard = mentions.firstOrNull { it.reference == c.reference }?.first
                scripture("main_scripture", c.reference, ScriptureOrigin.AI, heard?.segmentId ?: c.segmentIds.firstOrNull(),
                    heard?.startMs ?: startOf(c.segmentIds, bySegment), BlockSource.AI)
            }
        } else if (mentions.isNotEmpty()) {
            heading("main_scripture")
            mentions.sortedByDescending { it.occurrences.size }.take(3).sortedBy { it.first.startMs }.forEach { m ->
                scripture("main_scripture", m.reference, ScriptureOrigin.DETECTED, m.first.segmentId, m.first.startMs, BlockSource.SCRIPTURE)
            }
        }

        extraction?.keyMessage?.let { heading("key_message"); text("key_message", NoteBlockType.PARAGRAPH, it) }
        extraction?.keyPoints?.takeIf { it.isNotEmpty() }?.let { points ->
            heading("key_points"); points.forEach { text("key_points", NoteBlockType.NUMBERED, it) }
        }
        extraction?.quotes?.takeIf { it.isNotEmpty() }?.let { quotes ->
            heading("quotes")
            quotes.forEach { q ->
                val seg = q.segmentIds.firstOrNull()?.let { bySegment[it] }
                blocks += NoteBlock(
                    NoteRepository.newId("block"), noteId, 0, NoteBlockType.TRANSCRIPT_EXCERPT, RichText.plain(q.text),
                    payload = buildMap {
                        put(NoteBlock.PAYLOAD_MEETING_ID, meetingId)
                        seg?.let { put(NoteBlock.PAYLOAD_START_MS, it.startMs.toString()) }
                        seg?.speakerName?.let { put(NoteBlock.PAYLOAD_SPEAKER, it) }
                    },
                    source = BlockSource.TRANSCRIPT, sourceSegmentIds = q.segmentIds, sectionKey = "quotes"
                )
            }
        }
        extraction?.application?.let { heading("application"); text("application", NoteBlockType.PARAGRAPH, it) }
        extraction?.prayerPoints?.takeIf { it.isNotEmpty() }?.let { list ->
            heading("prayer_points"); list.forEach { text("prayer_points", NoteBlockType.BULLET, it) }
        }
        extraction?.reflectionQuestions?.takeIf { it.isNotEmpty() }?.let { list ->
            heading("reflection_questions"); list.forEach { text("reflection_questions", NoteBlockType.BULLET, it) }
        }

        // Every passage mentioned, in the order it came up — the sermon's reading list.
        if (mentions.isNotEmpty()) {
            heading(KEY_ALL_SCRIPTURE, "Scripture references")
            mentions.forEach { m ->
                scripture(KEY_ALL_SCRIPTURE, m.reference, ScriptureOrigin.DETECTED, m.first.segmentId, m.first.startMs, BlockSource.SCRIPTURE)
            }
        }

        val keys = Workflows.template(com.example.core.model.RecordingType.SERMON).sections
            .filter { it.source == com.example.core.model.SectionSource.AI }.map { it.key }.toSet() + KEY_ALL_SCRIPTURE
        return GeneratedSections(blocks, refs, keys)
    }

    private fun startOf(ids: List<String>, bySegment: Map<String, TranscriptSegment>): Long? =
        ids.mapNotNull { bySegment[it]?.startMs }.minOrNull()
}
