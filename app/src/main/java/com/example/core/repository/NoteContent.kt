package com.example.core.repository

import com.example.core.model.Note
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.model.Workflows

/**
 * When a note has something in it worth keeping (PLAN_V2 F0).
 *
 * A note opened and left blank — even one that starts with a template's headings, or a calendar
 * event's title — isn't saved and never shows in a list. It stays a *draft* until the person adds
 * something of their own.
 */
object NoteContent {
    const val DRAFT = "draft"
    /** The title a draft was created with (an event's name), which doesn't count as content. */
    const val DRAFT_TITLE = "draftTitle"

    /** SQL: excludes drafts. Must match how [NoteCodec.encodeMap] writes `"draft":"1"`. */
    const val NOT_DRAFT_SQL = "metadataJson NOT LIKE '%\"draft\":\"1\"%'"

    fun isDraft(note: Note) = note.metadata[DRAFT] == "1"

    private val HEADINGS = setOf(NoteBlockType.HEADING_1, NoteBlockType.HEADING_2, NoteBlockType.HEADING_3)

    fun hasUserContent(
        note: Note,
        blocks: List<NoteBlock>,
        hasAttachments: Boolean = false,
        hasTags: Boolean = false,
        hasRecordings: Boolean = false
    ): Boolean {
        if (hasAttachments || hasTags || hasRecordings) return true
        val title = note.title.trim()
        if (title.isNotEmpty() && title != note.metadata[DRAFT_TITLE].orEmpty().trim()) return true
        val templateTitles = Workflows.template(note.workflow).sections.map { it.title.trim() }.toSet()
        return blocks.any { b ->
            when {
                b.type in HEADINGS -> b.content.text.isNotBlank() && b.content.text.trim() !in templateTitles
                b.type.isText -> b.content.text.isNotBlank()
                b.type == NoteBlockType.DIVIDER -> false
                else -> true // scripture, media, recordings, excerpts, links
            }
        }
    }
}
