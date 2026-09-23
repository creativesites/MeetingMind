package com.example.ai.notes

import com.example.core.model.BlockSource
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.notes.RichText
import com.example.core.repository.NoteRepository

/**
 * Puts a note AI result into a note's blocks. Pure, so every rule is tested: results are added,
 * never silently replace what the person wrote, and anything the AI didn't use is kept.
 */
object NoteAiApply {
    const val SUMMARY_KEY = "ai_summary"
    const val ACTIONS_KEY = "ai_actions"

    /** A "Summary" section at the top, replacing an earlier AI summary but nothing else. */
    fun withSummary(blocks: List<NoteBlock>, noteId: String, points: List<CitedItem>): List<NoteBlock> {
        val rest = blocks.filterNot { it.sectionKey == SUMMARY_KEY }
        val section = listOf(heading(noteId, "Summary", SUMMARY_KEY)) + points.map { p ->
            NoteBlock(NoteRepository.newId("block"), noteId, 0, NoteBlockType.BULLET, RichText.plain(p.text), source = BlockSource.AI, sectionKey = SUMMARY_KEY)
        }
        return section + rest
    }

    /** Chosen action items as a checklist at the end, above the trailing empty line. */
    fun withActions(blocks: List<NoteBlock>, noteId: String, actions: List<CitedItem>): List<NoteBlock> {
        if (actions.isEmpty()) return blocks
        val existing = blocks.filter { it.sectionKey == ACTIONS_KEY && it.type == NoteBlockType.CHECKLIST }.map { it.content.text.lowercase() }.toSet()
        val fresh = actions.filter { it.text.lowercase() !in existing && itemText(it).lowercase() !in existing }
        if (fresh.isEmpty()) return blocks
        val items = fresh.map { a ->
            NoteBlock(NoteRepository.newId("block"), noteId, 0, NoteBlockType.CHECKLIST, RichText.plain(itemText(a)), source = BlockSource.AI, sectionKey = ACTIONS_KEY)
        }
        val hasHeading = blocks.any { it.sectionKey == ACTIONS_KEY && it.type == NoteBlockType.HEADING_2 }
        val lastAction = blocks.indexOfLast { it.sectionKey == ACTIONS_KEY }
        if (hasHeading && lastAction >= 0) return blocks.toMutableList().apply { addAll(lastAction + 1, items) }
        val trailing = blocks.lastOrNull()?.takeIf { it.type == NoteBlockType.PARAGRAPH && it.content.isEmpty }
        val body = if (trailing != null) blocks.dropLast(1) else blocks
        return body + heading(noteId, "Action items", ACTIONS_KEY) + items + listOfNotNull(trailing)
    }

    /**
     * The note rebuilt in sections from the person's own sentences. Media, recordings and verses
     * stay first, in order; each section follows; and every text block the organised version
     * didn't use is kept under "Other notes", unchanged.
     */
    fun organized(blocks: List<NoteBlock>, noteId: String, result: NoteAiOutcome.Sections): List<NoteBlock> {
        if (result.sections.isEmpty()) return blocks
        val headings = setOf(NoteBlockType.HEADING_1, NoteBlockType.HEADING_2, NoteBlockType.HEADING_3)
        val fixed = blocks.filter { !it.type.isText }
        // Kept: every text block the sections don't cite — including any the model never saw.
        val used = result.sections.flatMap { s -> s.items.flatMap { it.sourceIds } }.toSet()
        val kept = blocks.filter { it.type.isText && it.type !in headings && it.id !in used && !it.content.isEmpty }
        val sections = result.sections.flatMap { s ->
            listOf(heading(noteId, s.title, s.key).copy(source = BlockSource.USER)) + s.items.map { item ->
                NoteBlock(NoteRepository.newId("block"), noteId, 0, NoteBlockType.PARAGRAPH, RichText.plain(item.text), source = BlockSource.USER, sectionKey = s.key)
            }
        }
        val other = if (kept.isEmpty()) emptyList() else listOf(heading(noteId, "Other notes", "other").copy(source = BlockSource.USER)) + kept
        return fixed + sections + other + NoteBlock(NoteRepository.newId("block"), noteId, 0, NoteBlockType.PARAGRAPH)
    }

    private fun itemText(a: CitedItem) = a.detail?.let { "${a.text} — $it" } ?: a.text

    private fun heading(noteId: String, title: String, key: String) =
        NoteBlock(NoteRepository.newId("block"), noteId, 0, NoteBlockType.HEADING_2, RichText.plain(title), source = BlockSource.AI, sectionKey = key)
}
