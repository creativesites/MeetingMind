package com.example.core.faith

import com.example.core.model.BlockSource
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.notes.RichText
import com.example.core.repository.NoteRepository

/**
 * Bible study methods as templates: each makes an ordinary Bible-study note with the method's
 * sections and prompts, the passage at the top. Not special screens — notes, like everything else.
 */
enum class StudyTemplate(val label: String, val description: String, val sections: List<Pair<String, String>>) {
    SOAP("SOAP", "Scripture, Observation, Application, Prayer", listOf(
        "Scripture" to "Write out the verse that stands out to you",
        "Observation" to "What does it say? Who, what, where, when",
        "Application" to "What does it mean for you today?",
        "Prayer" to "Pray it back to God in your own words"
    )),
    INDUCTIVE("Inductive study", "Observe, interpret, apply", listOf(
        "Observe" to "What does the text say? Repeated words, people, places, commands, contrasts",
        "Interpret" to "What did it mean to its first readers? What does it teach about God and people?",
        "Apply" to "What will you believe, change or do because of it?"
    )),
    BOOK_OVERVIEW("Book overview", "The shape of a whole book", listOf(
        "Author and audience" to "Who wrote it, to whom, and when (as far as is known)",
        "Purpose" to "Why was it written?",
        "Structure" to "Its main sections, chapter by chapter",
        "Key themes" to "What keeps coming back",
        "Key verses" to "The verses that carry the book",
        "What it shows about God" to ""
    )),
    CHARACTER("Character study", "One person's story in Scripture", listOf(
        "Who they were" to "Family, place, role",
        "Their story" to "Where they appear (references) and what happens",
        "Strengths and failings" to "",
        "What God did" to "",
        "What I learn" to ""
    )),
    WORD("Word study", "One word, in context and across Scripture", listOf(
        "The word" to "The English word, and its Hebrew or Greek behind it if known",
        "In this passage" to "How it's used here",
        "Elsewhere in Scripture" to "Other places it appears (references)",
        "What it means for me" to ""
    )),
    TOPICAL("Topical study", "What Scripture says about one subject", listOf(
        "The question" to "What do you want to understand?",
        "Passages" to "Key passages on the topic (references)",
        "What they say together" to "",
        "Where they seem to differ" to "",
        "Living it" to ""
    )),
    SERMON_PREP("Sermon prep", "From text to message", listOf(
        "Text" to "The passage and its context",
        "Big idea" to "The message in one sentence",
        "Outline" to "Main points",
        "Illustrations" to "",
        "Application" to "What should listeners believe or do?",
        "Prayer" to ""
    )),
    SMALL_GROUP("Small group guide", "Questions for a group", listOf(
        "Open" to "An easy question to start the conversation",
        "Read" to "Who reads which part",
        "Discuss" to "Questions about the text",
        "Apply" to "What will we do this week?",
        "Pray" to "What to pray for together"
    ));

    /** The note's blocks: each section a heading and an empty line showing its prompt. */
    fun blocks(noteId: String): List<NoteBlock> = sections.flatMapIndexed { i, (title, hint) ->
        val key = "study_${name.lowercase()}_$i"
        listOf(
            NoteBlock(NoteRepository.newId("block"), noteId, 0, NoteBlockType.HEADING_2, RichText.plain(title), source = BlockSource.USER, sectionKey = key),
            NoteBlock(NoteRepository.newId("block"), noteId, 0, NoteBlockType.PARAGRAPH, RichText.EMPTY,
                payload = if (hint.isNotBlank()) mapOf(NoteBlock.PAYLOAD_HINT to hint) else emptyMap(), source = BlockSource.USER, sectionKey = key)
        )
    }
}

/** Every note that touches a passage, grouped the way people think of them. */
data class PassageLinks(
    val myNotes: List<LinkedNote> = emptyList(),
    val sermons: List<LinkedNote> = emptyList(),
    val devotionals: List<LinkedNote> = emptyList()
) {
    val total get() = myNotes.size + sermons.size + devotionals.size
    val isEmpty get() = total == 0
}

/** A note that mentions a passage; [startMs] is where in its recording, for a sermon. */
data class LinkedNote(val noteId: String, val title: String, val reference: String, val meetingId: String?, val startMs: Long?, val updatedAt: Long)
