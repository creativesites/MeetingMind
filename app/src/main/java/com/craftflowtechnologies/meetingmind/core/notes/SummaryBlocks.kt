package com.craftflowtechnologies.meetingmind.core.notes

import com.craftflowtechnologies.meetingmind.core.model.ActionItem
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.Decision
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.Question
import java.util.UUID

/**
 * A recording's summary as note blocks (N-1): a "Summary" heading with its paragraphs, then
 * Decisions, Action items (a checklist) and Questions as lists, each only when there is something
 * to show. Blocks are marked as AI and carry the transcript segments they were drawn from.
 */
object SummaryBlocks {

    fun build(
        noteId: String,
        meetingId: String,
        summaryText: String?,
        decisions: List<Decision> = emptyList(),
        actionItems: List<ActionItem> = emptyList(),
        questions: List<Question> = emptyList(),
        /** Start time of each transcript segment, so a block can jump to the audio. */
        segmentStarts: Map<String, Long> = emptyMap(),
        newId: () -> String = { "block_${UUID.randomUUID()}" }
    ): List<NoteBlock> {
        val out = mutableListOf<NoteBlock>()
        fun block(type: NoteBlockType, text: String, ids: List<String> = emptyList(), checked: Boolean = false) {
            val start = ids.mapNotNull { segmentStarts[it] }.minOrNull()
            out += NoteBlock(
                id = newId(), noteId = noteId, position = 0, type = type, content = RichText.plain(text),
                payload = if (start != null) mapOf(NoteBlock.PAYLOAD_MEETING_ID to meetingId, NoteBlock.PAYLOAD_START_MS to start.toString()) else emptyMap(),
                source = BlockSource.AI, sourceSegmentIds = ids, checked = checked
            )
        }

        val paragraphs = summaryText.orEmpty().split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        if (paragraphs.isNotEmpty()) {
            block(NoteBlockType.HEADING_2, "Summary")
            paragraphs.forEach { para ->
                val lines = para.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.size > 1 && lines.all { BULLET.containsMatchIn(it) }) {
                    lines.forEach { block(NoteBlockType.BULLET, it.replace(BULLET, "")) }
                } else block(NoteBlockType.PARAGRAPH, lines.joinToString(" "))
            }
        }
        decisions.filter { it.text.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { list ->
            block(NoteBlockType.HEADING_3, "Decisions")
            list.forEach { block(NoteBlockType.BULLET, it.text.trim(), it.sourceSegmentIds) }
        }
        actionItems.filter { it.task.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { list ->
            block(NoteBlockType.HEADING_3, "Action items")
            list.forEach { a ->
                val who = a.assigneeName?.takeIf { it.isNotBlank() }
                val due = a.deadline?.takeIf { it.isNotBlank() }
                val tail = listOfNotNull(who, due?.let { "due $it" }).joinToString(", ")
                block(NoteBlockType.CHECKLIST, a.task.trim() + if (tail.isNotEmpty()) " ($tail)" else "", a.sourceSegmentIds, checked = a.isCompleted)
            }
        }
        questions.filter { it.text.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { list ->
            block(NoteBlockType.HEADING_3, "Questions")
            list.forEach { q ->
                val answer = q.answer?.takeIf { it.isNotBlank() }
                block(NoteBlockType.BULLET, q.text.trim() + if (answer != null) " — $answer" else "", q.sourceSegmentIds)
            }
        }
        return out
    }

    private val BULLET = Regex("^[-*•]\\s+")
}
