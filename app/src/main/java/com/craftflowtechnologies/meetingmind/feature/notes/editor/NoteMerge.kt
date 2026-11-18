package com.craftflowtechnologies.meetingmind.feature.notes.editor

import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType

/**
 * Block-level three-way merge for the note editor: the person's unsaved edits and blocks that were
 * written to the same note meanwhile (a finished retry, a polish, another screen) both survive.
 *
 * [base] is the note as the editor last read or wrote it, [ours] is what the editor shows now, and
 * [theirs] is what is stored now. Pure; positions are ignored.
 */
internal object NoteMerge {

    /** The same block, wherever it sits. */
    fun same(a: NoteBlock, b: NoteBlock): Boolean = a.copy(position = 0) == b.copy(position = 0)

    fun sameList(a: List<NoteBlock>, b: List<NoteBlock>): Boolean = a.size == b.size && a.indices.all { same(a[it], b[it]) }

    /** [blocks] without the empty line the editor keeps at the end to type on. */
    fun core(blocks: List<NoteBlock>): List<NoteBlock> {
        val last = blocks.lastOrNull() ?: return blocks
        val emptyLine = last.type == NoteBlockType.PARAGRAPH && last.content.text.isEmpty() && last.payload.isEmpty()
        return if (emptyLine) blocks.dropLast(1) else blocks
    }

    /**
     * [theirs] with the person's changes laid on top: a block they edited or added is theirs to keep
     * (an added block goes after the block it followed), and a block they deleted goes unless it was
     * changed in the store meanwhile. Everything else is as stored.
     */
    fun merge(base: List<NoteBlock>, ours: List<NoteBlock>, theirs: List<NoteBlock>): List<NoteBlock> {
        val baseById = base.associateBy { it.id }
        val oursById = ours.associateBy { it.id }
        val theirIds = theirs.mapTo(HashSet()) { it.id }
        val changedByUs = ours.filter { b -> baseById[b.id]?.let { !same(it, b) } ?: true }.mapTo(HashSet()) { it.id }
        val deletedByUs = base.mapNotNull { b -> b.id.takeIf { it !in oursById } }.toSet()

        val result = ArrayList<NoteBlock>(theirs.size + ours.size)
        for (t in theirs) {
            when {
                t.id in deletedByUs && baseById[t.id]?.let { same(it, t) } == true -> Unit
                t.id in changedByUs -> result += oursById.getValue(t.id)
                else -> result += t
            }
        }
        // Ours that the store doesn't have: new blocks, or ones replaced there that the person edited.
        ours.forEachIndexed { i, b ->
            if (b.id in theirIds || b.id !in changedByUs) return@forEachIndexed
            val anchor = (i - 1 downTo 0).firstNotNullOfOrNull { j -> result.indexOfFirst { it.id == ours[j].id }.takeIf { it >= 0 } }
            result.add(if (anchor == null) 0 else anchor + 1, b)
        }
        return result
    }
}
