package com.craftflowtechnologies.meetingmind.ai.faith

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.ScriptureOrigin
import com.craftflowtechnologies.meetingmind.core.model.ScriptureRef
import com.craftflowtechnologies.meetingmind.core.model.Speaker
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.notes.TranscriptBlocks
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.scripture.DetectedScripture
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureDetector
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference

/** The generated part of a sermon note: its blocks, and the scripture references they show. */
data class GeneratedSections(
    val blocks: List<NoteBlock>,
    val refs: List<ScriptureRef>,
    val keys: Set<String>,
    /** Sections that go at the very end of the note instead of straight after the recording. */
    val endKeys: Set<String> = emptySet()
)

/**
 * Turns a sermon's extraction and the scripture heard in it into note sections (docs/PLAN_V1.md §5).
 *
 * Pure: it builds blocks and references and writes nothing, so the whole layout is unit-tested.
 * Every generated block carries the transcript segments it came from, and every scripture block a
 * moment in the audio, so the note is evidence you can tap, not a summary you have to trust.
 */
object SermonNoteBuilder {

    const val KEY_ALL_SCRIPTURE = "scripture_refs"

    /** The section that says why the sermon notes are missing. Replaced on every run, so a retry clears it. */
    const val KEY_STATUS = "sermon_status"

    /** The transcript at the end of the note: heading and paragraphs, replaced on every run. */
    const val KEY_TRANSCRIPT = "sermon_transcript"

    /**
     * The one plain line shown at the top of a sermon note whose AI notes could not be written.
     * [reason] is a short, user-facing cause, e.g. "no AI model is available on this phone".
     */
    fun unavailableMessage(reason: String): String =
        "Sermon notes couldn't be generated ($reason). The transcript and scripture are below. " +
            "Use Retry sermon notes in the recording menu to try again."

    fun build(
        noteId: String,
        meetingId: String,
        segments: List<TranscriptSegment>,
        extraction: SermonExtraction?,
        detections: List<DetectedScripture>,
        now: Long = System.currentTimeMillis(),
        /** Why there is no extraction, when the sermon notes could not be written. Null when they were. */
        unavailableReason: String? = null,
        /** What was spoken (worship already left out), for the transcript section; empty writes none. */
        spoken: List<TranscriptSegment> = emptyList(),
        speakers: List<Speaker> = emptyList()
    ): GeneratedSections {
        val bySegment = segments.associateBy { it.id }
        val blocks = mutableListOf<NoteBlock>()
        val refs = mutableListOf<ScriptureRef>()
        val titles = Workflows.template(com.craftflowtechnologies.meetingmind.core.model.RecordingType.SERMON).sections.associate { it.key to it.title }

        // Not AI output, so no AI attribution: the line is about the recording, not written by a model.
        unavailableReason?.let { reason ->
            blocks += NoteBlock(NoteRepository.newId("block"), noteId, 0, NoteBlockType.PARAGRAPH, RichText.plain(unavailableMessage(reason)),
                source = BlockSource.TRANSCRIPT, sectionKey = KEY_STATUS)
        }

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

        // The whole sermon, as readable paragraphs by speaker, at the end of the note. Always written when
        // there is speech, so a note without AI notes still has its transcript.
        val transcript = TranscriptBlocks.build(noteId, meetingId, spoken, speakers, sectionKey = KEY_TRANSCRIPT)
        if (transcript.isNotEmpty()) {
            heading(KEY_TRANSCRIPT, "Transcript")
            blocks += transcript
        }

        val keys = Workflows.template(com.craftflowtechnologies.meetingmind.core.model.RecordingType.SERMON).sections
            .filter { it.source == com.craftflowtechnologies.meetingmind.core.model.SectionSource.AI }.map { it.key }.toSet() + KEY_ALL_SCRIPTURE + KEY_STATUS + KEY_TRANSCRIPT
        return GeneratedSections(blocks, refs, keys, endKeys = setOf(KEY_TRANSCRIPT))
    }

    private fun startOf(ids: List<String>, bySegment: Map<String, TranscriptSegment>): Long? =
        ids.mapNotNull { bySegment[it]?.startMs }.minOrNull()
}
