package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType

/**
 * One stretch of a note as speech. [text] is the note's own words; [lead] is the app's fixed
 * label or announcement ("Scripture. John chapter 3, verses 16 to 18."). They stay separate so a
 * rewrite can never touch the note's words, and a listener can always tell the two apart.
 */
data class SpeechUnit(
    /** The note blocks this was drawn from, so playback can scroll back to them. */
    val blockIds: List<String>,
    val role: SpeechRole,
    val lead: String?,
    val text: String,
    /** Verbatim in Smart Read: no model sees or changes it. */
    val locked: Boolean,
    /** From a private note or a private template section: never leaves the phone. */
    val isPrivate: Boolean,
    val aiGenerated: Boolean,
    /** Scripture in a translation whose licence is for display, not for a cloud service or stored audio. */
    val licenseRestricted: Boolean = false,
    /** A passage that could not be fetched: [text] says so, and nothing is cached as audio. */
    val unavailable: Boolean = false,
    val sectionKey: String? = null
) {
    val spoken: String get() = listOfNotNull(lead?.takeIf { it.isNotBlank() }, text.takeIf { it.isNotBlank() }).joinToString(" ")
}

/** A heading-bounded stretch: the unit of generation, caching, navigation and resume. */
data class SpeechSection(
    /** Stable across edits elsewhere in the note: the first block's id. */
    val id: String,
    val title: String,
    val units: List<SpeechUnit>,
    /** Scripture references in this section, for the jump list ("John 3:16–18"). */
    val references: List<String> = emptyList()
) {
    val blockIds: List<String> get() = units.flatMap { it.blockIds }.distinct()
}

enum class SkipReason { EMPTY, UNSUPPORTED_TYPE }

data class SkippedBlock(val blockId: String, val type: NoteBlockType, val reason: SkipReason)

data class SpeechSource(val sections: List<SpeechSection>, val skipped: List<SkippedBlock> = emptyList()) {
    private val units: Sequence<SpeechUnit> get() = sections.asSequence().flatMap { it.units.asSequence() }

    val isEmpty: Boolean get() = units.none { it.role != SpeechRole.HEADING }
    val containsPrivate: Boolean get() = units.any { it.isPrivate }
    val containsLicensedScripture: Boolean get() = units.any { it.licenseRestricted }

    /** The same reading with the private sections left out — the way to offer a cloud voice for the rest. */
    fun withoutPrivate(): SpeechSource = copy(sections = sections.mapNotNull { s ->
        val kept = s.units.filterNot { it.isPrivate }
        if (kept.none { it.role != SpeechRole.HEADING }) null else s.copy(units = kept)
    })
}

/** A passage as it will be spoken. The text always comes from the Bible library or the person's own typing. */
data class ResolvedScripture(
    /** "John chapter 3, verses 16 to 18" (see SpeechScript.spoken). */
    val spokenReference: String,
    val translationName: String? = null,
    /** Per-verse text when the library gave it; the only way to read verse numbers exactly. */
    val verses: List<Verse>? = null,
    /** Flattened text, used when [verses] is absent. */
    val text: String? = null,
    /** Verse text the person typed themselves; read as theirs, never as the translation. */
    val userTyped: Boolean = false,
    /** Whether the translation's licence allows a cloud speech service and stored audio. */
    val openForCloud: Boolean = false
) {
    data class Verse(val number: Int, val text: String)

    val hasText: Boolean get() = !verses.isNullOrEmpty() || !text.isNullOrBlank()
}

fun interface ScriptureLookup {
    /** Null when the passage can't be resolved (offline, not licensed, not found). */
    fun resolve(block: NoteBlock): ResolvedScripture?
}

enum class TranslationAnnouncement { ALWAYS, WHEN_CHANGED, NEVER }

data class ExtractOptions(
    val readVerseNumbers: Boolean = false,
    val labelStyle: LabelStyle = LabelStyle.FULL,
    val announceTranslation: TranslationAnnouncement = TranslationAnnouncement.WHEN_CHANGED
)

/**
 * Turns a note's blocks into [SpeechSource]. Pure and deterministic: no model, no network. The note
 * is never changed, and nothing here rephrases a word of it; the only additions are the fixed
 * labels from [RoleLabels] and the scripture announcement.
 */
object NoteSpeechExtractor {

    private val unsupported = setOf(
        NoteBlockType.IMAGE, NoteBlockType.VIDEO, NoteBlockType.AUDIO, NoteBlockType.RECORDING,
        NoteBlockType.NOTE_LINK, NoteBlockType.CODE, NoteBlockType.TABLE, NoteBlockType.EMBED
    )

    private class SectionBuilder(val id: String, var title: String) {
        val units = mutableListOf<SpeechUnit>()
        val references = mutableListOf<String>()
        val hasContent: Boolean get() = units.any { it.role != SpeechRole.HEADING }
    }

    fun extract(
        noteTitle: String,
        notePrivate: Boolean,
        blocks: List<NoteBlock>,
        privateKeys: Set<String>,
        scripture: ScriptureLookup,
        options: ExtractOptions = ExtractOptions()
    ): SpeechSource {
        val sections = mutableListOf<SectionBuilder>()
        val skipped = mutableListOf<SkippedBlock>()
        var current: SectionBuilder? = null
        var currentHeading: String? = null
        val headingByKey = mutableMapOf<String, String>()
        var previousRole: SpeechRole? = null
        var previousKey: String? = null
        var lastAnnouncedTranslation: String? = null
        val numbering = mutableMapOf<Int, Int>()

        fun open(block: NoteBlock, title: String): SectionBuilder =
            SectionBuilder(block.id, title).also { sections += it; current = it }

        for (block in blocks.sortedBy { it.position }) {
            if (block.type == NoteBlockType.DIVIDER) continue
            if (block.type in unsupported) {
                skipped += SkippedBlock(block.id, block.type, SkipReason.UNSUPPORTED_TYPE)
                continue
            }
            if (block.type != NoteBlockType.NUMBERED) numbering.clear()
            val isPrivate = notePrivate || (block.sectionKey != null && block.sectionKey in privateKeys)
            val key = block.sectionKey

            if (block.type == NoteBlockType.SCRIPTURE) {
                val resolved = scripture.resolve(block)
                val reference = block.payload["reference"]?.takeIf { it.isNotBlank() } ?: resolved?.spokenReference.orEmpty()
                // A passage right under its heading belongs to that section; otherwise it starts its own.
                val section = current?.takeIf { !it.hasContent } ?: open(block, reference.ifBlank { noteTitle })
                if (reference.isNotBlank()) section.references += reference
                val unit = scriptureUnit(block, resolved, reference, isPrivate, options, lastAnnouncedTranslation)
                if (resolved?.hasText == true && !resolved.userTyped && options.announceTranslation != TranslationAnnouncement.NEVER) {
                    lastAnnouncedTranslation = resolved.translationName ?: lastAnnouncedTranslation
                }
                section.units += unit
                previousRole = SpeechRole.SCRIPTURE
                previousKey = key
                continue
            }

            val raw = when (block.type) {
                NoteBlockType.MARKDOWN -> plainFromMarkdown(block.content.text)
                else -> block.content.text.trim()
            }
            if (raw.isBlank()) {
                skipped += SkippedBlock(block.id, block.type, SkipReason.EMPTY)
                continue
            }

            if (block.type == NoteBlockType.HEADING_1 || block.type == NoteBlockType.HEADING_2) {
                currentHeading = raw
                key?.let { headingByKey[it] = raw }
                val section = open(block, raw)
                section.units += SpeechUnit(listOf(block.id), SpeechRole.HEADING, null, raw, locked = false, isPrivate = isPrivate, aiGenerated = false, sectionKey = key)
                previousRole = SpeechRole.HEADING
                previousKey = key
                continue
            }

            val heading = key?.let { headingByKey[it] } ?: currentHeading
            if (block.type == NoteBlockType.HEADING_3) {
                key?.let { headingByKey[it] = raw }
            }
            val role = StudyRoleClassifier.classify(block, heading)
            val section = current ?: open(block, noteTitle.ifBlank { "Note" })
            val ai = StudyRoleClassifier.isAiGenerated(block)

            val text = when (block.type) {
                NoteBlockType.NUMBERED -> {
                    val n = (numbering[block.indent] ?: 0) + 1
                    numbering[block.indent] = n
                    numbering.keys.removeAll { it > block.indent }
                    "$n. $raw"
                }
                NoteBlockType.CHECKLIST -> if (block.checked) "Done: $raw" else raw
                else -> raw
            }

            var lead: String? = null
            when (role) {
                SpeechRole.TRANSCRIPT -> lead = block.payload[NoteBlock.PAYLOAD_SPEAKER]?.takeIf { it.isNotBlank() }?.let { "$it:" }
                SpeechRole.HEADING -> Unit
                else -> if (role != previousRole || key != previousKey) lead = RoleLabels.lead(role, ai, options.labelStyle)
            }

            section.units += SpeechUnit(
                blockIds = listOf(block.id),
                role = role,
                lead = lead,
                text = text,
                locked = role.lockedInSmartRead,
                isPrivate = isPrivate,
                aiGenerated = ai,
                sectionKey = key
            )
            previousRole = role
            previousKey = key
        }

        return SpeechSource(
            sections = sections.filter { it.units.isNotEmpty() }.map { SpeechSection(it.id, it.title, it.units.toList(), it.references.toList()) },
            skipped = skipped
        )
    }

    private fun scriptureUnit(
        block: NoteBlock,
        resolved: ResolvedScripture?,
        reference: String,
        isPrivate: Boolean,
        options: ExtractOptions,
        lastAnnounced: String?
    ): SpeechUnit {
        val spokenRef = resolved?.spokenReference?.takeIf { it.isNotBlank() } ?: reference.ifBlank { "this passage" }
        if (resolved == null || !resolved.hasText) {
            return SpeechUnit(
                blockIds = listOf(block.id), role = SpeechRole.SCRIPTURE,
                lead = if (options.labelStyle == LabelStyle.OFF) null else "Scripture.",
                text = "$spokenRef. This passage isn't available right now.",
                locked = false, isPrivate = isPrivate, aiGenerated = false, unavailable = true, sectionKey = block.sectionKey
            )
        }
        val name = resolved.translationName?.trim().orEmpty()
        val translation = when {
            resolved.userTyped -> "Your text."
            options.announceTranslation == TranslationAnnouncement.NEVER || name.isEmpty() -> null
            options.announceTranslation == TranslationAnnouncement.WHEN_CHANGED && name == lastAnnounced -> null
            else -> name.trimEnd('.') + "."
        }
        val lead = when (options.labelStyle) {
            LabelStyle.FULL -> listOfNotNull("Scripture.", "$spokenRef.", translation).joinToString(" ")
            LabelStyle.SHORT -> listOfNotNull("Scripture, $spokenRef.", translation).joinToString(" ")
            LabelStyle.OFF -> "$spokenRef."
        }
        return SpeechUnit(
            blockIds = listOf(block.id), role = SpeechRole.SCRIPTURE, lead = lead,
            text = scriptureText(resolved, options.readVerseNumbers),
            locked = true, isPrivate = isPrivate, aiGenerated = false,
            licenseRestricted = !resolved.userTyped && !resolved.openForCloud,
            sectionKey = block.sectionKey
        )
    }

    /**
     * The passage's own words. Verse numbers are spoken only when asked for, and only from per-verse
     * data, so they are exact; no heuristic ever strips digits out of scripture.
     */
    internal fun scriptureText(resolved: ResolvedScripture, readVerseNumbers: Boolean): String {
        val verses = resolved.verses
        if (!verses.isNullOrEmpty()) {
            return verses.joinToString(" ") { v ->
                val body = v.text.replace(Regex("""\s*\n\s*"""), " ").trim()
                if (readVerseNumbers) "Verse ${v.number}. $body" else body
            }.trim()
        }
        return resolved.text.orEmpty().replace(Regex("""\s*\n\s*"""), " ").trim()
    }

    /** Markdown markers out, words kept; each line ends in punctuation so the voice pauses between lines. */
    internal fun plainFromMarkdown(markdown: String): String {
        var inFence = false
        val lines = markdown.lines().mapNotNull { rawLine ->
            if (rawLine.trimStart().startsWith("```")) { inFence = !inFence; return@mapNotNull null }
            if (inFence) return@mapNotNull null
            var line = rawLine.trim()
                .replace(Regex("""^#{1,6}\s+"""), "")
                .replace(Regex("""^>\s?"""), "")
                .replace(Regex("""^[-*+]\s+"""), "")
                .replace(Regex("""^\d+[.)]\s+"""), "")
                .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")
                .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
                .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
                .replace(Regex("""__(.+?)__"""), "$1")
                .replace(Regex("""\*(.+?)\*"""), "$1")
                .replace(Regex("""`([^`]*)`"""), "$1")
                .trim()
            if (line.isBlank() || line.matches(Regex("""[-*_]{3,}"""))) return@mapNotNull null
            if (line.last() !in ".!?:;,…") line += "."
            line
        }
        return lines.joinToString(" ")
    }
}
