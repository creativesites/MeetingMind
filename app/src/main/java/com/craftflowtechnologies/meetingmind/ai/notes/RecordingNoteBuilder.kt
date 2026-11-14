package com.craftflowtechnologies.meetingmind.ai.notes

import com.craftflowtechnologies.meetingmind.ai.faith.GeneratedSections
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.MeetingSummary
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.ScriptureOrigin
import com.craftflowtechnologies.meetingmind.core.model.ScriptureRef
import com.craftflowtechnologies.meetingmind.core.model.Speaker
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.notes.TranscriptBlocks
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.scripture.DetectedScripture
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureDetector
import com.craftflowtechnologies.meetingmind.core.work.Mark

/**
 * Turns a processed recording's intelligence ([MeetingSummary]), marks, detected scripture,
 * optional AI-polished blocks, and speaker-grouped transcript into structured [NoteBlock]s
 * for any [RecordingType] other than Sermon/Lecture (which have their own dedicated builders).
 */
object RecordingNoteBuilder {

    const val KEY_POLISHED = NotePolishEngine.KEY_POLISHED
    const val KEY_SUMMARY = "ai_summary"
    const val KEY_TOPICS = "ai_topics"
    const val KEY_DECISIONS = "ai_decisions"
    const val KEY_ACTIONS = "ai_actions"
    const val KEY_QUESTIONS = "ai_questions"
    const val KEY_FOLLOW_UPS = "ai_follow_ups"
    const val KEY_SCRIPTURE = "ai_scripture_refs"
    const val KEY_MARKS = "ai_marked_moments"
    const val KEY_TRANSCRIPT = "ai_transcript"

    fun build(
        noteId: String,
        meetingId: String,
        recordingType: RecordingType,
        segments: List<TranscriptSegment>,
        summary: MeetingSummary?,
        speakers: List<Speaker> = emptyList(),
        marks: List<Mark> = emptyList(),
        detections: List<DetectedScripture> = emptyList(),
        polishedBlocks: List<NoteBlock> = emptyList(),
        now: Long = System.currentTimeMillis()
    ): GeneratedSections {
        val blocks = mutableListOf<NoteBlock>()
        val refs = mutableListOf<ScriptureRef>()
        val keys = mutableSetOf(
            KEY_POLISHED, KEY_SUMMARY, KEY_TOPICS, KEY_DECISIONS, KEY_ACTIONS,
            KEY_QUESTIONS, KEY_FOLLOW_UPS, KEY_SCRIPTURE, KEY_MARKS, KEY_TRANSCRIPT
        )

        fun heading(sectionKey: String, title: String) {
            blocks += NoteBlock(
                id = NoteRepository.newId("block"),
                noteId = noteId,
                position = 0,
                type = NoteBlockType.HEADING_2,
                content = RichText.plain(title),
                source = BlockSource.AI,
                sectionKey = sectionKey
            )
        }

        fun addBlock(
            sectionKey: String,
            type: NoteBlockType,
            text: String,
            source: BlockSource = BlockSource.AI,
            checked: Boolean = false,
            payload: Map<String, String> = mapOf(NoteBlock.PAYLOAD_MEETING_ID to meetingId)
        ) {
            blocks += NoteBlock(
                id = NoteRepository.newId("block"),
                noteId = noteId,
                position = 0,
                type = type,
                content = RichText.plain(text),
                payload = payload,
                source = source,
                sectionKey = sectionKey,
                checked = checked
            )
        }

        if (polishedBlocks.isNotEmpty()) {
            blocks += polishedBlocks.map { it.copy(noteId = noteId, sectionKey = KEY_POLISHED) }
        } else if (summary != null) {
            if (summary.summary.isNotBlank()) {
                val summaryTitle = if (recordingType == RecordingType.DICTATION) "Overview" else "Summary"
                heading(KEY_SUMMARY, summaryTitle)
                addBlock(
                    KEY_SUMMARY,
                    NoteBlockType.CALLOUT,
                    summary.summary.trim(),
                    payload = mapOf(
                        NoteBlock.PAYLOAD_MEETING_ID to meetingId,
                        NoteBlock.PAYLOAD_CALLOUT_KIND to "KEY_TAKEAWAY",
                        NoteBlock.PAYLOAD_CALLOUT_TITLE to summaryTitle
                    )
                )
            }
            if (summary.topics.isNotEmpty()) {
                heading(KEY_TOPICS, "Key Topics")
                summary.topics.filter { it.isNotBlank() }.forEach { topic ->
                    addBlock(KEY_TOPICS, NoteBlockType.BULLET, topic.trim())
                }
            }
            if (summary.decisions.isNotEmpty()) {
                heading(KEY_DECISIONS, "Decisions")
                summary.decisions.filter { it.text.isNotBlank() }.forEach { d ->
                    addBlock(KEY_DECISIONS, NoteBlockType.BULLET, d.text.trim())
                }
            }
            if (summary.actionItems.isNotEmpty()) {
                heading(KEY_ACTIONS, "Action Items")
                summary.actionItems.filter { it.task.isNotBlank() }.forEach { item ->
                    val meta = listOfNotNull(
                        item.assigneeName?.takeIf { it.isNotBlank() },
                        item.deadline?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    val line = if (meta.isNotEmpty()) "${item.task.trim()} ($meta)" else item.task.trim()
                    addBlock(KEY_ACTIONS, NoteBlockType.CHECKLIST, line, checked = item.isCompleted)
                }
            }
            if (summary.questions.isNotEmpty()) {
                heading(KEY_QUESTIONS, "Open Questions")
                summary.questions.filter { it.text.isNotBlank() }.forEach { q ->
                    addBlock(KEY_QUESTIONS, NoteBlockType.NUMBERED, q.text.trim())
                }
            }
            if (summary.followUps.isNotEmpty()) {
                heading(KEY_FOLLOW_UPS, "Follow-ups")
                summary.followUps.filter { it.description.isNotBlank() }.forEach { f ->
                    addBlock(KEY_FOLLOW_UPS, NoteBlockType.BULLET, f.description.trim())
                }
            }
        }

        // Scripture references detected in faith-adjacent recordings (Bible study, devotional, etc.)
        val mentions = ScriptureDetector.mentions(detections)
        if (mentions.isNotEmpty()) {
            heading(KEY_SCRIPTURE, "Scripture references")
            mentions.forEach { m ->
                val blockId = NoteRepository.newId("block")
                val refId = NoteRepository.newId("scripture")
                blocks += NoteBlock(
                    id = blockId,
                    noteId = noteId,
                    position = 0,
                    type = NoteBlockType.SCRIPTURE,
                    content = RichText.plain(m.reference.display()),
                    payload = mapOf(
                        NoteBlock.PAYLOAD_SCRIPTURE_REF_ID to refId,
                        "reference" to m.reference.display(),
                        NoteBlock.PAYLOAD_MEETING_ID to meetingId,
                        NoteBlock.PAYLOAD_START_MS to m.first.startMs.toString()
                    ),
                    source = BlockSource.SCRIPTURE,
                    sourceSegmentIds = listOfNotNull(m.first.segmentId),
                    sectionKey = KEY_SCRIPTURE
                )
                refs += ScriptureRef(
                    id = refId,
                    noteId = noteId,
                    blockId = blockId,
                    bookUsfm = m.reference.usfm,
                    chapter = m.reference.chapter,
                    verseStart = m.reference.verseStart,
                    verseEnd = m.reference.verseEnd,
                    versionId = null,
                    origin = ScriptureOrigin.DETECTED,
                    meetingId = meetingId,
                    segmentId = m.first.segmentId,
                    startMs = m.first.startMs,
                    createdAt = now
                )
            }
        }

        // User-marked moments during the recording
        val noteMarks = marks.filter { !it.text.isNullOrBlank() }
        if (noteMarks.isNotEmpty()) {
            heading(KEY_MARKS, "Marked Moments")
            noteMarks.forEach { m ->
                addBlock(
                    sectionKey = KEY_MARKS,
                    type = NoteBlockType.TRANSCRIPT_EXCERPT,
                    text = m.text!!.trim(),
                    source = BlockSource.TRANSCRIPT,
                    payload = mapOf(
                        NoteBlock.PAYLOAD_MEETING_ID to meetingId,
                        NoteBlock.PAYLOAD_START_MS to m.atMs.toString()
                    )
                )
            }
        }

        // Clean, speaker-grouped transcript at the end of the note
        val transcript = TranscriptBlocks.build(noteId, meetingId, segments, speakers, sectionKey = KEY_TRANSCRIPT)
        if (transcript.isNotEmpty()) {
            blocks += NoteBlock(
                id = NoteRepository.newId("block"),
                noteId = noteId,
                position = 0,
                type = NoteBlockType.DIVIDER,
                source = BlockSource.TRANSCRIPT,
                sectionKey = KEY_TRANSCRIPT
            )
            heading(KEY_TRANSCRIPT, "Transcript")
            blocks += transcript
        }

        return GeneratedSections(
            blocks = blocks,
            refs = refs,
            keys = keys,
            endKeys = setOf(KEY_TRANSCRIPT)
        )
    }
}
