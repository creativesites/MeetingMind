package com.example.feature.notes.editor

import com.example.core.model.BlockSource
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.notes.RichText
import com.example.core.repository.NoteRepository

/** Where the cursor should go after an edit. */
data class FocusTarget(val blockId: String, val cursor: Int)

data class EditResult(val blocks: List<NoteBlock>, val focus: FocusTarget?)

/**
 * The note editor's structural rules, as pure functions over the block list.
 *
 * The editor is a column of plain text fields, one per block. Everything a word processor does
 * *between* paragraphs — Enter splitting a block, Backspace joining two, a list ending when you
 * press Enter on an empty item — happens here, where it can be tested without a screen.
 */
object BlockEditing {

    const val MAX_INDENT = 4

    /**
     * Applies new text to the block at [index].
     *
     * A newline in [newContent] means Enter was pressed (or several lines were pasted): the block
     * is split at every newline and the pieces become blocks of the same kind, as a word
     * processor does. [cursor] is where the caret sits in [newContent].
     */
    fun applyTextEdit(blocks: List<NoteBlock>, index: Int, newContent: RichText, cursor: Int): EditResult {
        val current = blocks.getOrNull(index) ?: return EditResult(blocks, null)
        val edited = current.markEdited()

        if ('\n' !in newContent.text) {
            return EditResult(blocks.replaceAt(index, edited.copy(content = newContent)), FocusTarget(current.id, cursor))
        }

        // Enter on an empty list item or quote ends the list instead of adding another item.
        if (newContent.text == "\n" && current.type != NoteBlockType.PARAGRAPH && current.content.isEmpty) {
            val exited = edited.copy(type = NoteBlockType.PARAGRAPH, content = RichText.EMPTY, indent = 0, checked = false)
            return EditResult(blocks.replaceAt(index, exited), FocusTarget(current.id, 0))
        }

        val pieces = mutableListOf<RichText>()
        var rest = newContent
        while (true) {
            val at = rest.text.indexOf('\n')
            if (at < 0) { pieces += rest; break }
            val (left, right) = rest.splitAt(at)
            pieces += left
            rest = right.splitAt(1).second
        }

        val continuation = continuationType(current.type)
        val created = pieces.drop(1).map { piece ->
            NoteBlock(
                id = NoteRepository.newId("block"),
                noteId = current.noteId,
                position = 0,
                type = continuation,
                content = piece,
                source = BlockSource.USER,
                indent = if (continuation.isListItem) current.indent else 0
            )
        }
        val first = edited.copy(content = pieces.first())
        val result = blocks.toMutableList().apply {
            this[index] = first
            addAll(index + 1, created)
        }

        // Put the caret in the piece that holds it: after a typed Enter that is the start of the
        // new block; after a paste it is wherever the pasted text ended.
        var remaining = cursor
        var focusIndex = 0
        for ((i, piece) in pieces.withIndex()) {
            if (remaining <= piece.text.length) { focusIndex = i; break }
            remaining -= piece.text.length + 1
            focusIndex = i
        }
        val focusBlock = if (focusIndex == 0) first else created[focusIndex - 1]
        return EditResult(result, FocusTarget(focusBlock.id, remaining.coerceIn(0, focusBlock.content.text.length)))
    }

    /**
     * Backspace with the caret at the very start of the block at [index].
     *
     * A formatted block first loses its formatting, as in every editor people know. A plain
     * paragraph joins the text block above it. If the block above is a picture or recording, an
     * empty paragraph is removed and focus moves up; a paragraph with text stays put, so a stray
     * Backspace can never delete a photo.
     */
    fun backspaceAtStart(blocks: List<NoteBlock>, index: Int): EditResult {
        val current = blocks.getOrNull(index) ?: return EditResult(blocks, null)
        if (current.type.isListItem && current.indent > 0) {
            return EditResult(blocks.replaceAt(index, current.copy(indent = current.indent - 1)), FocusTarget(current.id, 0))
        }
        if (current.type != NoteBlockType.PARAGRAPH && current.type.isText) {
            val plain = current.markEdited().copy(type = NoteBlockType.PARAGRAPH, indent = 0, checked = false)
            return EditResult(blocks.replaceAt(index, plain), FocusTarget(current.id, 0))
        }
        if (index == 0) return EditResult(blocks, FocusTarget(current.id, 0))

        val previous = blocks[index - 1]
        return when {
            previous.type.isText -> {
                val joinAt = previous.content.text.length
                val merged = previous.markEdited().copy(content = previous.content.append(current.content))
                EditResult(blocks.replaceAt(index - 1, merged).removeAt(index), FocusTarget(previous.id, joinAt))
            }
            current.content.isEmpty -> {
                val remaining = blocks.removeAt(index)
                // Focus the nearest text block above the media, if there is one.
                val above = remaining.take(index).lastOrNull { it.type.isText }
                EditResult(remaining.ifEmpty { listOf(emptyParagraph(current.noteId)) }, above?.let { FocusTarget(it.id, it.content.text.length) })
            }
            else -> EditResult(blocks, FocusTarget(current.id, 0))
        }
    }

    /** Turns the block into [type], or back into a paragraph if it already is one. */
    fun toggleType(blocks: List<NoteBlock>, index: Int, type: NoteBlockType): List<NoteBlock> {
        val current = blocks.getOrNull(index) ?: return blocks
        if (!current.type.isText || !type.isText) return blocks
        val target = if (current.type == type) NoteBlockType.PARAGRAPH else type
        return blocks.replaceAt(
            index,
            current.markEdited().copy(
                type = target,
                indent = if (target.isListItem) current.indent else 0,
                checked = if (target == NoteBlockType.CHECKLIST) current.checked else false
            )
        )
    }

    fun indent(blocks: List<NoteBlock>, index: Int, delta: Int): List<NoteBlock> {
        val current = blocks.getOrNull(index) ?: return blocks
        if (!current.type.isListItem) return blocks
        // An item can be at most one level deeper than the item above it.
        val above = blocks.getOrNull(index - 1)?.takeIf { it.type.isListItem }?.indent ?: -1
        val next = (current.indent + delta).coerceIn(0, minOf(MAX_INDENT, above + 1))
        return blocks.replaceAt(index, current.copy(indent = next))
    }

    fun toggleChecked(blocks: List<NoteBlock>, index: Int): List<NoteBlock> {
        val current = blocks.getOrNull(index)?.takeIf { it.type == NoteBlockType.CHECKLIST } ?: return blocks
        return blocks.replaceAt(index, current.copy(checked = !current.checked))
    }

    /** Inserts [newBlocks] after [index] (-1 for the top), and returns where to focus. */
    fun insertAfter(blocks: List<NoteBlock>, index: Int, newBlocks: List<NoteBlock>): EditResult {
        if (newBlocks.isEmpty()) return EditResult(blocks, null)
        val at = (index + 1).coerceIn(0, blocks.size)
        // Inserting into an empty paragraph replaces it, so the note doesn't collect blank lines.
        val replaceEmpty = blocks.getOrNull(index)?.let { it.type == NoteBlockType.PARAGRAPH && it.content.isEmpty } == true
        val result = blocks.toMutableList().apply {
            if (replaceEmpty) removeAt(index)
            addAll(if (replaceEmpty) index else at, newBlocks)
        }.let { ensureTrailingParagraph(it, newBlocks.first().noteId) }
        val focus = newBlocks.lastOrNull { it.type.isText }?.let { FocusTarget(it.id, it.content.text.length) }
            ?: result.getOrNull(result.indexOfFirst { it.id == newBlocks.last().id } + 1)?.let { FocusTarget(it.id, 0) }
        return EditResult(result, focus)
    }

    fun move(blocks: List<NoteBlock>, from: Int, to: Int): List<NoteBlock> {
        if (from !in blocks.indices || to !in blocks.indices || from == to) return blocks
        return blocks.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun delete(blocks: List<NoteBlock>, index: Int): List<NoteBlock> {
        if (index !in blocks.indices) return blocks
        val remaining = blocks.removeAt(index)
        return remaining.ifEmpty { listOf(emptyParagraph(blocks[index].noteId)) }
    }

    fun duplicate(blocks: List<NoteBlock>, index: Int): List<NoteBlock> {
        val current = blocks.getOrNull(index) ?: return blocks
        return blocks.toMutableList().apply { add(index + 1, current.copy(id = NoteRepository.newId("block"))) }
    }

    /**
     * The number shown on a numbered item: its place among the consecutive numbered items at its
     * level. Deeper items don't interrupt the count; anything shallower or non-list restarts it.
     */
    fun numberFor(blocks: List<NoteBlock>, index: Int): Int {
        val target = blocks.getOrNull(index) ?: return 1
        var n = 1
        var i = index - 1
        while (i >= 0) {
            val b = blocks[i]
            if (!b.type.isListItem || b.indent < target.indent) break
            if (b.indent == target.indent) {
                if (b.type != NoteBlockType.NUMBERED) break
                n++
            }
            i--
        }
        return n
    }

    /**
     * Markdown-style shortcuts typed at the start of a paragraph: "- " or "* " starts a bullet,
     * "1. " a numbered item, "[] " a checklist, "#"–"###" headings and "> " a quote. Returns the
     * new type and how many characters to remove, or null when [text] starts no shortcut.
     */
    fun markdownShortcut(text: String): Pair<NoteBlockType, Int>? {
        val shortcuts = listOf(
            "### " to NoteBlockType.HEADING_3, "## " to NoteBlockType.HEADING_2, "# " to NoteBlockType.HEADING_1,
            "- " to NoteBlockType.BULLET, "* " to NoteBlockType.BULLET, "• " to NoteBlockType.BULLET,
            "1. " to NoteBlockType.NUMBERED, "1) " to NoteBlockType.NUMBERED,
            "[] " to NoteBlockType.CHECKLIST, "[ ] " to NoteBlockType.CHECKLIST, "> " to NoteBlockType.QUOTE
        )
        return shortcuts.firstOrNull { text.startsWith(it.first) }?.let { it.second to it.first.length }
    }

    /**
     * Applies [markdownShortcut] to a paragraph whose text was just typed. Only fires when the
     * space completing the shortcut is the character just typed ([cursor] sits right after it),
     * so pasting "- item" or editing an old line doesn't reformat it by surprise.
     */
    fun applyShortcut(blocks: List<NoteBlock>, index: Int, cursor: Int): EditResult? {
        val block = blocks.getOrNull(index)?.takeIf { it.type == NoteBlockType.PARAGRAPH } ?: return null
        val (type, length) = markdownShortcut(block.content.text) ?: return null
        if (cursor != length) return null
        val stripped = block.content.splitAt(length).second
        return EditResult(blocks.replaceAt(index, block.copy(type = type, content = stripped)), FocusTarget(block.id, 0))
    }

    /** The note always ends in a paragraph, so there's somewhere to type after a picture. */
    fun ensureTrailingParagraph(blocks: List<NoteBlock>, noteId: String): List<NoteBlock> =
        if (blocks.lastOrNull()?.type?.isText == true) blocks else blocks + emptyParagraph(noteId)

    fun emptyParagraph(noteId: String) = NoteBlock(
        id = NoteRepository.newId("block"),
        noteId = noteId,
        position = 0,
        type = NoteBlockType.PARAGRAPH
    )

    fun wordCount(blocks: List<NoteBlock>): Int =
        blocks.filter { it.type.isText }.sumOf { b -> b.content.text.split(WHITESPACE).count { it.isNotEmpty() } }

    private val WHITESPACE = Regex("\\s+")

    private fun continuationType(type: NoteBlockType): NoteBlockType = when (type) {
        NoteBlockType.BULLET, NoteBlockType.NUMBERED, NoteBlockType.CHECKLIST -> type
        else -> NoteBlockType.PARAGRAPH
    }

    /** Text the user has touched is theirs: an AI or transcript block edited by hand says so. */
    private fun NoteBlock.markEdited(): NoteBlock = if (source == BlockSource.USER) this else copy(isUserEdited = true)

    private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> = toMutableList().apply { this[index] = value }
    private fun <T> List<T>.removeAt(index: Int): List<T> = toMutableList().apply { removeAt(index) }
}

/**
 * Undo and redo over whole-note snapshots.
 *
 * Typing is coalesced: consecutive text edits to one block make one undo step, and a new step
 * starts when the user moves to another block, changes structure, or pauses.
 */
class UndoHistory(private val limit: Int = 100) {
    private val undo = ArrayDeque<List<NoteBlock>>()
    private val redo = ArrayDeque<List<NoteBlock>>()
    private var lastTypingBlock: String? = null
    private var lastTypingAt = 0L

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    /** Records [before] as an undo point, unless this edit continues the previous burst of typing. */
    fun record(before: List<NoteBlock>, typingBlockId: String? = null, now: Long = System.currentTimeMillis()) {
        val continuesTyping = typingBlockId != null && typingBlockId == lastTypingBlock && now - lastTypingAt < COALESCE_MS
        lastTypingBlock = typingBlockId
        lastTypingAt = now
        if (continuesTyping) return
        undo.addLast(before)
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    fun undo(current: List<NoteBlock>): List<NoteBlock>? {
        val previous = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        lastTypingBlock = null
        return previous
    }

    fun redo(current: List<NoteBlock>): List<NoteBlock>? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        lastTypingBlock = null
        return next
    }

    companion object {
        const val COALESCE_MS = 1_500L
    }
}
