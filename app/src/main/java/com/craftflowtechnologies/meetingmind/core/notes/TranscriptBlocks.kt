package com.craftflowtechnologies.meetingmind.core.notes

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.Speaker
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import java.util.UUID

/**
 * Turns a recording's transcript into note blocks that read like prose: one speaker turn at a
 * time, merged into paragraphs of a few sentences, each block carrying the time range and the
 * transcript segments it came from, so tapping it jumps to the audio (N-1, N-2).
 *
 * Pure: it only builds blocks. Shared by "Insert transcript" in the editor and by the sermon
 * pipeline, so both produce the same layout.
 */
object TranscriptBlocks {

    /** A paragraph closes at the end of a segment once it has this many sentences… */
    const val TARGET_SENTENCES = 4
    /** …and never grows past this many, even in the middle of a long segment. */
    const val MAX_SENTENCES = 6
    /** Or this many characters, whichever comes first (at a sentence end). */
    const val MAX_CHARS = 900

    /**
     * The text the app shows for a segment: the person's own correction if there is one, else the
     * cleaned reading text, else the raw recognition. Never the raw text over an edit.
     */
    fun displayText(s: TranscriptSegment): String =
        (if (s.isUserEdited) s.text else s.cleanedText?.takeIf { it.isNotBlank() } ?: s.text).trim()

    /** The speaker's current name (a rename wins over the stored label), or null when unknown. */
    fun speakerLabel(s: TranscriptSegment, speakers: List<Speaker>): String? {
        val known = s.speakerId?.let { id -> speakers.firstOrNull { it.id == id } }
        val fromIdentity = known?.let { it.customName.trim().ifBlank { it.originalLabel.trim() } }?.takeIf { it.isNotBlank() }
        return fromIdentity ?: s.speakerName?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * TRANSCRIPT_EXCERPT blocks for [segments], in time order. [sectionKey] tags every block (the
     * sermon note uses it to replace the section on a re-run); null for blocks the person inserts.
     */
    fun build(
        noteId: String,
        meetingId: String,
        segments: List<TranscriptSegment>,
        speakers: List<Speaker> = emptyList(),
        sectionKey: String? = null,
        newId: () -> String = { "block_${UUID.randomUUID()}" }
    ): List<NoteBlock> {
        val ordered = segments.sortedWith(compareBy({ it.startMs }, { it.endMs }))
            .map { it to displayText(it) }.filter { it.second.isNotEmpty() }
        if (ordered.isEmpty()) return emptyList()

        // Speaker turns: consecutive segments by the same speaker.
        val turns = mutableListOf<MutableList<Pair<TranscriptSegment, String>>>()
        var lastLabel: String? = null
        ordered.forEach { item ->
            val label = speakerLabel(item.first, speakers)
            if (turns.isEmpty() || label != lastLabel) turns += mutableListOf<Pair<TranscriptSegment, String>>()
            turns.last() += item
            lastLabel = label
        }

        val out = mutableListOf<NoteBlock>()
        turns.forEach { turn ->
            val speaker = speakerLabel(turn.first().first, speakers)
            var sentences = 0
            var chars = 0
            val text = StringBuilder()
            val ids = mutableListOf<String>()
            var start = 0L
            var end = 0L

            fun flush() {
                if (text.isEmpty()) return
                out += NoteBlock(
                    id = newId(), noteId = noteId, position = 0, type = NoteBlockType.TRANSCRIPT_EXCERPT,
                    content = RichText.plain(text.toString().trim()),
                    payload = buildMap {
                        put(NoteBlock.PAYLOAD_MEETING_ID, meetingId)
                        put(NoteBlock.PAYLOAD_START_MS, start.toString())
                        put(NoteBlock.PAYLOAD_END_MS, end.toString())
                        speaker?.let { put(NoteBlock.PAYLOAD_SPEAKER, it) }
                    },
                    source = BlockSource.TRANSCRIPT, sourceSegmentIds = ids.toList(), sectionKey = sectionKey
                )
                text.clear(); ids.clear(); sentences = 0; chars = 0
            }

            turn.forEach { (seg, display) ->
                val parts = sentencesOf(display)
                parts.forEachIndexed { i, sentence ->
                    if (text.isEmpty()) start = seg.startMs else text.append(' ')
                    text.append(sentence)
                    if (seg.id !in ids) ids += seg.id
                    end = seg.endMs
                    sentences++
                    chars += sentence.length + 1
                    val lastOfSegment = i == parts.lastIndex
                    if (sentences >= MAX_SENTENCES || chars >= MAX_CHARS || (lastOfSegment && sentences >= TARGET_SENTENCES)) flush()
                }
            }
            flush()
        }
        return out
    }

    internal fun sentencesOf(text: String): List<String> =
        text.split(SENTENCE_END).map { it.trim() }.filter { it.isNotEmpty() }

    private val SENTENCE_END = Regex("(?<=[.!?…][\"')”’]?)\\s+(?=[\"'“‘(\\[]?[\\p{Lu}\\d])")
}
