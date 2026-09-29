package com.craftflowtechnologies.meetingmind.feature.notes.editor

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository

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

    /** What one edit did to a block's text: [text] replaced the characters from [start] to [removedEnd]. */
    data class Insertion(val start: Int, val removedEnd: Int, val text: String)

    fun insertion(old: String, new: String): Insertion {
        val max = minOf(old.length, new.length)
        var p = 0
        while (p < max && old[p] == new[p]) p++
        var s = 0
        while (s < max - p && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
        return Insertion(p, old.length - s, new.substring(p, new.length - s))
    }

    /**
     * What a paste becomes.
     *
     * Anything longer than a line — an answer from ChatGPT or Claude, an email, a README — goes
     * into **one** Markdown block, split around the caret: the note gains one block, not one per
     * line. [clipboardMarkdown] is the same text with its formatting, read from the clipboard's
     * HTML, for when the plain text lost it. A single pasted line keeps its bold, links and code
     * inline; a lone YouTube link in an empty line becomes a video. Returns null for ordinary
     * typing, which the normal rules handle.
     */
    fun pasteMarkdown(blocks: List<NoteBlock>, index: Int, inserted: Insertion, clipboardMarkdown: String? = null): EditResult? {
        val current = blocks.getOrNull(index) ?: return null
        if (!current.type.isText || inserted.text.length < 2) return null
        val pasted = inserted.text.replace("\r\n", "\n").replace('\r', '\n')
        val multiLine = '\n' in pasted.trim()
        val (before, _) = current.content.splitAt(inserted.start)
        val after = current.content.splitAt(inserted.removedEnd).second

        if (!multiLine) {
            val line = pasted.trim()
            val lone = before.text.isBlank() && after.text.isBlank() && line.none { it.isWhitespace() }
            if (lone && com.craftflowtechnologies.meetingmind.core.notes.YouTube.idOf(line) != null) {
                val video = com.craftflowtechnologies.meetingmind.core.notes.MarkdownImport.parse(line, current.noteId).singleOrNull()
                    ?.takeIf { it.type == NoteBlockType.EMBED } ?: return null
                val para = emptyParagraph(current.noteId)
                val out = blocks.toMutableList().apply { this[index] = video.copy(id = current.id); add(index + 1, para) }
                return EditResult(out, FocusTarget(para.id, 0))
            }
            if (!com.craftflowtechnologies.meetingmind.core.notes.MarkdownImport.looksLikeMarkdown(pasted)) return null
            val rich = com.craftflowtechnologies.meetingmind.core.notes.MarkdownImport.inline(pasted)
            if (rich.spans.isEmpty()) return null
            val content = before.append(rich).append(after)
            return EditResult(blocks.replaceAt(index, current.markEdited().copy(content = content)), FocusTarget(current.id, before.text.length + rich.text.length))
        }

        // Chat-app buttons, lost code fences and flattened tables are fixed on the way in; the
        // paste as it arrived is kept for "Show original".
        val tidy = com.craftflowtechnologies.meetingmind.core.notes.AiPasteCleanup.clean(pasted)
        val markdown = markdownSource(pasted, clipboardMarkdown)
        val payload = buildMap {
            if (markdown.trim() != pasted.trim()) put(NoteBlock.PAYLOAD_RAW, pasted.trim())
            tidy.source?.let { put(NoteBlock.PAYLOAD_PASTE_SOURCE, it) }
        }
        val out = mutableListOf<NoteBlock>()
        val keepBefore = before.text.isNotBlank()
        if (keepBefore) out += current.markEdited().copy(content = before)
        out += NoteBlock(
            id = if (keepBefore) NoteRepository.newId("block") else current.id,
            noteId = current.noteId, position = 0, type = NoteBlockType.MARKDOWN,
            content = RichText.plain(markdown), payload = payload, source = BlockSource.USER, sectionKey = current.sectionKey
        )
        val tail = NoteBlock(
            id = NoteRepository.newId("block"), noteId = current.noteId, position = 0,
            type = NoteBlockType.PARAGRAPH, content = after, source = BlockSource.USER, sectionKey = current.sectionKey
        )
        val next = blocks.getOrNull(index + 1)
        // Somewhere to keep typing after the paste, unless there already is an empty line there.
        val needTail = after.text.isNotBlank() || next == null || !(next.type.isText && next.content.isEmpty)
        if (needTail) out += tail
        val result = blocks.toMutableList().apply { removeAt(index); addAll(index, out) }
        val focus = if (needTail) FocusTarget(tail.id, 0) else FocusTarget(next!!.id, 0)
        return EditResult(result, focus)
    }

    /**
     * The Markdown to keep for a paste: the clipboard's formatted version when there is one, the
     * text itself when it is already Markdown, and otherwise plain lines kept as the lines they
     * were (Markdown would run single line breaks together into one paragraph).
     */
    internal fun markdownSource(pasted: String, clipboardMarkdown: String?): String {
        val text = pasted.trim('\n', ' ')
        clipboardMarkdown?.trim()?.takeIf { it.isNotEmpty() }?.let { return com.craftflowtechnologies.meetingmind.core.notes.AiPasteCleanup.clean(it).markdown }
        val tidied = com.craftflowtechnologies.meetingmind.core.notes.AiPasteCleanup.clean(text).markdown
        if (com.craftflowtechnologies.meetingmind.core.notes.MarkdownImport.looksLikeMarkdown(text) && !com.craftflowtechnologies.meetingmind.core.notes.AiPasteCleanup.looksLikeAiAnswer(text)) return tidied
        return keepLines(tidied)
    }

    /**
     * Text copied from a rendered page has one line per paragraph or item; Markdown would run
     * those together. A blank line goes between plain lines — never inside code, tables, lists or quotes.
     */
    internal fun keepLines(markdown: String): String {
        val out = StringBuilder()
        var fenced = false
        var previousPlain = false
        for (line in markdown.lines()) {
            val t = line.trim()
            if (t.startsWith("```")) fenced = !fenced
            val plain = !fenced && t.isNotEmpty() && !t.startsWith("```") && !t.startsWith("|") && !t.startsWith(">") &&
                !t.startsWith("#") && !Regex("^([-*+]|\\d+[.)])\\s").containsMatchIn(t)
            if (plain && previousPlain) out.append('\n')
            out.append(line).append('\n')
            previousPlain = plain
        }
        return out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** Text blocks from [from] to [to] (inclusive) as Markdown blocks; media, recordings and AI text stay as they are. */
    fun combineIntoMarkdown(blocks: List<NoteBlock>, from: Int, to: Int): List<NoteBlock> {
        val out = mutableListOf<NoteBlock>()
        val run = mutableListOf<NoteBlock>()
        fun flush() {
            val text = run.filter { it.type != NoteBlockType.PARAGRAPH || it.content.text.isNotBlank() }
            if (text.isNotEmpty()) {
                val md = com.craftflowtechnologies.meetingmind.core.notes.NoteText.markdown(text).trim()
                out += text.first().copy(type = NoteBlockType.MARKDOWN, content = RichText.plain(md), indent = 0, checked = false, payload = emptyMap())
            }
            run.clear()
        }
        for (b in blocks.subList(from, to + 1)) {
            val joinable = b.type.isText || b.type in COMBINABLE
            if (joinable && b.source == BlockSource.USER) run += b else { flush(); out += b }
        }
        flush()
        return blocks.subList(0, from) + out + blocks.subList(to + 1, blocks.size)
    }

    private val COMBINABLE = setOf(NoteBlockType.DIVIDER, NoteBlockType.CODE, NoteBlockType.TABLE, NoteBlockType.MARKDOWN)

    /** A folded heading hides what follows it up to the next heading of the same or a higher level. */
    fun visibleBlocks(blocks: List<NoteBlock>): List<NoteBlock> {
        val out = mutableListOf<NoteBlock>()
        var hideBelow = Int.MAX_VALUE
        for (b in blocks) {
            val level = headingLevel(b.type)
            if (level != null && level <= hideBelow) hideBelow = Int.MAX_VALUE
            if (hideBelow != Int.MAX_VALUE) continue
            out += b
            if (level != null && b.payload[NoteBlock.PAYLOAD_FOLDED] == "1") hideBelow = level
        }
        return out
    }

    fun headingLevel(type: NoteBlockType): Int? = when (type) {
        NoteBlockType.HEADING_1 -> 1
        NoteBlockType.HEADING_2 -> 2
        NoteBlockType.HEADING_3 -> 3
        else -> null
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
