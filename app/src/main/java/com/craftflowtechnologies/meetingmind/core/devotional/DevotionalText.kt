package com.craftflowtechnologies.meetingmind.core.devotional

import com.craftflowtechnologies.meetingmind.core.scripture.Passage
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference

/** A devotional block's words as plain text, for copying and sharing. */
enum class DevotionalBlock(val heading: String) {
    SCRIPTURE("Scripture"), REFLECTION("Reflection"), APPLICATION("Today I will"),
    PRAYER("Prayer"), WORD("A word for today"), QUOTE("Quote"), QUESTION("A question to sit with")
}

object DevotionalText {
    /** A passage with its reference and version, and the version's attribution when the text is known. */
    fun scripture(ref: ScriptureReference, passage: Passage?): String =
        if (passage == null) ref.display()
        else buildString {
            append("“").append(passage.text.trim()).append("”\n— ").append(ref.display()).append(" (").append(passage.versionAbbreviation).append(")")
            passage.attribution.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
        }

    fun block(d: Devotional, block: DevotionalBlock): String? = when (block) {
        DevotionalBlock.SCRIPTURE -> d.keyText?.let { "“$it”" } ?: d.scripture.joinToString("\n") { it.display() }.ifBlank { null }
        DevotionalBlock.REFLECTION -> d.reflection.joinToString("\n\n").ifBlank { null }
        DevotionalBlock.APPLICATION -> d.application.joinToString("\n") { "• $it" }.ifBlank { null }
        DevotionalBlock.PRAYER -> d.prayer?.ifBlank { null }
        DevotionalBlock.WORD -> d.motivation?.ifBlank { null }
        DevotionalBlock.QUOTE -> d.insight?.let { q -> listOf(q.author, q.source).filter { it.isNotBlank() }.joinToString(", ").let { by -> if (by.isBlank()) "“${q.text}”" else "“${q.text}”\n— $by" } }
        DevotionalBlock.QUESTION -> d.question?.ifBlank { null }
    }

    /** The whole devotional. [passages] maps a reference's display to its fetched text, for the verses to carry theirs. */
    fun all(d: Devotional, dateLine: String? = null, passages: Map<String, Passage> = emptyMap()): String = buildString {
        append(d.title)
        dateLine?.let { append("\n").append(it) }
        append("\n").append(d.label)
        fun section(heading: String, body: String?) { if (!body.isNullOrBlank()) append("\n\n").append(heading.uppercase()).append("\n").append(body) }
        section(DevotionalBlock.SCRIPTURE.heading, when {
            d.scripture.isEmpty() -> d.keyText?.let { "“$it”" }
            else -> listOfNotNull(d.keyText?.let { "“$it”" }, d.scripture.joinToString("\n\n") { scripture(it, passages[it.display()]) }).joinToString("\n\n")
        })
        section(DevotionalBlock.REFLECTION.heading, block(d, DevotionalBlock.REFLECTION))
        section(DevotionalBlock.APPLICATION.heading, block(d, DevotionalBlock.APPLICATION))
        section(DevotionalBlock.PRAYER.heading, block(d, DevotionalBlock.PRAYER))
        section(DevotionalBlock.WORD.heading, block(d, DevotionalBlock.WORD))
        section(DevotionalBlock.QUOTE.heading, block(d, DevotionalBlock.QUOTE))
        section(DevotionalBlock.QUESTION.heading, block(d, DevotionalBlock.QUESTION))
    }
}
