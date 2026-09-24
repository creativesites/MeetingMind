package com.example.core.devotional

import com.example.core.model.BlockSource
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.model.NoteDocument
import com.example.core.model.ScriptureOrigin
import com.example.core.model.ScriptureRef
import com.example.core.notes.RichText
import com.example.core.repository.NoteRepository
import com.example.core.scripture.ScriptureReferenceParser

/**
 * A devotional is a note (PLAN_V2 F2): it shows up in the journey, the calendar, search and export
 * for free. This maps a [Devotional] to note blocks and back. Pure, and unit-tested both ways.
 */
object DevotionalNotes {
    // Metadata.
    const val META_DAY = "devotionalDay"
    const val META_ORIGIN = "devotionalOrigin"
    const val META_LABEL = "devotionalLabel"
    const val META_ENGINE = "devotionalEngine"
    const val META_SEASON = "devotionalSeason"
    const val META_FEEDBACK = "devotionalFeedback"
    const val META_OPENED = "devotionalOpened"
    const val META_EVENING = "devotionalEvening"
    /** One per day and slot: "2026-09-24" or "2026-09-24-pm". What a day's devotional is found by. */
    const val META_KEY = "devotionalKey"

    fun key(day: LocalDay, evening: Boolean = false) = if (evening) "${day.iso}-pm" else day.iso

    // Sections. Distinct from the hand-written devotional template's keys, so an AI reflection is
    // not treated as the person's private reflection (in exports, say).
    const val S_SCRIPTURE = "daily_scripture"
    const val S_REFLECTION = "daily_reflection"
    const val S_APPLICATION = "daily_application"
    const val S_PRAYER = "daily_prayer"
    const val S_MOTIVATION = "daily_motivation"
    const val S_INSIGHT = "daily_insight"
    const val S_QUESTION = "daily_question"
    const val S_RESPONSE = "daily_my_response"

    val titles = linkedMapOf(
        S_SCRIPTURE to "Scripture",
        S_REFLECTION to "Reflection",
        S_APPLICATION to "Today I will",
        S_PRAYER to "Prayer",
        S_MOTIVATION to "A word for today",
        S_INSIGHT to "Quote",
        S_QUESTION to "A question to sit with",
        S_RESPONSE to "My response"
    )

    private const val PAYLOAD_KEY_TEXT = "keyText"
    private const val PAYLOAD_AUTHOR = "author"
    private const val PAYLOAD_SOURCE = "source"

    fun metadata(d: Devotional, evening: Boolean = false): Map<String, String> = buildMap {
        put(META_DAY, d.day.iso)
        put(META_KEY, key(d.day, evening))
        put(META_ORIGIN, d.origin.name)
        put(META_LABEL, d.label)
        d.engine?.let { put(META_ENGINE, it) }
        d.season?.let { put(META_SEASON, it.describe()) }
        if (evening) put(META_EVENING, "1")
    }

    /** Blocks and references for [d] in note [noteId]. */
    fun build(noteId: String, d: Devotional, now: Long = System.currentTimeMillis()): Pair<List<NoteBlock>, List<ScriptureRef>> {
        val blocks = mutableListOf<NoteBlock>()
        val refs = mutableListOf<ScriptureRef>()
        val source = if (d.origin == DevotionalOrigin.CLASSIC) BlockSource.IMPORTED else BlockSource.AI
        fun add(type: NoteBlockType, text: String, key: String, payload: Map<String, String> = emptyMap(), src: BlockSource = source) {
            blocks += NoteBlock(NoteRepository.newId("block"), noteId, blocks.size, type, RichText.plain(text), payload, src, sectionKey = key)
        }
        fun heading(key: String) = add(NoteBlockType.HEADING_2, titles.getValue(key), key)

        heading(S_SCRIPTURE)
        d.keyText?.let { add(NoteBlockType.QUOTE, it, S_SCRIPTURE, mapOf(PAYLOAD_KEY_TEXT to "1")) }
        d.scripture.forEach { ref ->
            val blockId = NoteRepository.newId("block")
            val refId = NoteRepository.newId("scripture")
            blocks += NoteBlock(
                blockId, noteId, blocks.size, NoteBlockType.SCRIPTURE, RichText.plain(ref.display()),
                mapOf(NoteBlock.PAYLOAD_SCRIPTURE_REF_ID to refId, "reference" to ref.display()), BlockSource.SCRIPTURE, sectionKey = S_SCRIPTURE
            )
            refs += ScriptureRef(refId, noteId, blockId, ref.usfm, ref.chapter, ref.verseStart, ref.verseEnd, null,
                if (d.origin == DevotionalOrigin.CLOUD_AI || d.origin == DevotionalOrigin.DEVICE_AI) ScriptureOrigin.AI else ScriptureOrigin.USER, createdAt = now)
        }
        if (d.reflection.isNotEmpty()) { heading(S_REFLECTION); d.reflection.forEach { add(NoteBlockType.PARAGRAPH, it, S_REFLECTION) } }
        if (d.application.isNotEmpty()) { heading(S_APPLICATION); d.application.forEach { add(NoteBlockType.CHECKLIST, it, S_APPLICATION) } }
        d.prayer?.let { heading(S_PRAYER); add(NoteBlockType.PARAGRAPH, it, S_PRAYER) }
        d.motivation?.let { heading(S_MOTIVATION); add(NoteBlockType.PARAGRAPH, it, S_MOTIVATION) }
        d.insight?.let { heading(S_INSIGHT); add(NoteBlockType.QUOTE, it.text, S_INSIGHT, mapOf(PAYLOAD_AUTHOR to it.author, PAYLOAD_SOURCE to it.source), BlockSource.IMPORTED) }
        d.question?.let { heading(S_QUESTION); add(NoteBlockType.PARAGRAPH, it, S_QUESTION) }
        if (d.origin != DevotionalOrigin.CARE) {
            add(NoteBlockType.HEADING_2, titles.getValue(S_RESPONSE), S_RESPONSE, src = BlockSource.USER)
            add(NoteBlockType.PARAGRAPH, "", S_RESPONSE, src = BlockSource.USER)
        }
        return blocks to refs
    }

    /** Whether [doc] is a generated daily devotional (not a hand-written one). */
    fun isDaily(doc: NoteDocument) = doc.note.metadata[META_DAY] != null

    /** Reads a devotional back from its note; null for a note that isn't one. */
    fun read(doc: NoteDocument): Devotional? {
        val meta = doc.note.metadata
        val day = meta[META_DAY] ?: return null
        val origin = runCatching { DevotionalOrigin.valueOf(meta[META_ORIGIN].orEmpty()) }.getOrDefault(DevotionalOrigin.CLOUD_AI)
        val blocks = doc.blocks.sortedBy { it.position }.filter { it.type != NoteBlockType.HEADING_2 }
        fun texts(key: String) = blocks.filter { it.sectionKey == key && it.type != NoteBlockType.SCRIPTURE }.map { it.content.text.trim() }.filter { it.isNotEmpty() }
        val insight = blocks.firstOrNull { it.sectionKey == S_INSIGHT }?.let {
            Quote(it.content.text, it.payload[PAYLOAD_AUTHOR].orEmpty(), it.payload[PAYLOAD_SOURCE].orEmpty(), emptySet())
        }
        return Devotional(
            day = LocalDay(day),
            origin = origin,
            title = doc.note.title,
            scripture = blocks.filter { it.type == NoteBlockType.SCRIPTURE }.mapNotNull { b -> b.payload["reference"]?.let { ScriptureReferenceParser.parse(it) } },
            keyText = blocks.firstOrNull { it.payload[PAYLOAD_KEY_TEXT] == "1" }?.content?.text,
            reflection = texts(S_REFLECTION),
            application = texts(S_APPLICATION),
            prayer = texts(S_PRAYER).joinToString("\n\n").ifBlank { null },
            motivation = texts(S_MOTIVATION).joinToString(" ").ifBlank { null },
            insight = insight,
            question = texts(S_QUESTION).firstOrNull(),
            label = meta[META_LABEL] ?: DevotionalLabels.CLOUD,
            engine = meta[META_ENGINE]
        )
    }

    /** The person's own response, as written in the note. */
    fun response(doc: NoteDocument): String =
        doc.blocks.sortedBy { it.position }.filter { it.sectionKey == S_RESPONSE && it.type != NoteBlockType.HEADING_2 }
            .joinToString("\n") { it.content.text }.trim()

    /** [doc]'s blocks with the response section's text replaced by [text]. */
    fun withResponse(doc: NoteDocument, text: String): List<NoteBlock> {
        val ordered = doc.blocks.sortedBy { it.position }
        val kept = ordered.filterNot { it.sectionKey == S_RESPONSE && it.type != NoteBlockType.HEADING_2 }
        val headingIndex = kept.indexOfFirst { it.sectionKey == S_RESPONSE }
        val paragraphs = text.split("\n").map { line ->
            NoteBlock(NoteRepository.newId("block"), doc.note.id, 0, NoteBlockType.PARAGRAPH, RichText.plain(line), source = BlockSource.USER, sectionKey = S_RESPONSE, isUserEdited = true)
        }
        return if (headingIndex < 0) {
            kept + NoteBlock(NoteRepository.newId("block"), doc.note.id, 0, NoteBlockType.HEADING_2, RichText.plain(titles.getValue(S_RESPONSE)), sectionKey = S_RESPONSE) + paragraphs
        } else kept.toMutableList().apply { addAll(headingIndex + 1, paragraphs) }
    }
}
