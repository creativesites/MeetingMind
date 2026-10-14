package com.craftflowtechnologies.meetingmind.core.devotional

import com.craftflowtechnologies.meetingmind.core.biblestudy.DEVOTIONAL_STEP
import com.craftflowtechnologies.meetingmind.core.biblestudy.JournalMeta
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionKind
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionPlan
import com.craftflowtechnologies.meetingmind.core.biblestudy.SessionState
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser

/**
 * The "Live it today" line a devotional falls back to when nothing wrote one for it: a classic, or
 * any devotional read offline. Generic on purpose (it knows nothing about the reader), one concrete
 * action, one line. An AI-written devotional is never given one of these: if the model left it out,
 * the block is simply not shown.
 */
object LiveItToday {
    val FALLBACKS = listOf(
        "Pause once today, put your phone down, and say the passage's main idea out loud.",
        "Write one sentence from today's reading on paper and keep it where you will see it.",
        "Thank one person today for something specific they did.",
        "Take a five-minute walk and notice three good things you would normally miss.",
        "Do one small kindness today that nobody will find out about.",
        "Before your next meal, stop and give thanks for it slowly.",
        "Send a short message of encouragement to someone you have not spoken to in a while.",
        "Spend two quiet minutes with no screen, then read the passage once more.",
        "Forgive one small thing today, and let it go.",
        "Offer your help to someone before they have to ask.",
        "Pray for one person by name before you go to sleep tonight.",
        "Choose one thing you were about to put off, and do it now, as an offering.",
        "Listen to someone today without planning what to say next.",
        "Give something away today: a coin, an hour or a good word.",
        "Memorise one line from today's reading and say it again tonight."
    )

    /** The same day always gets the same line; consecutive days get different ones. */
    fun fallback(day: LocalDay): String = FALLBACKS[Math.floorMod(day.date.toEpochDay(), FALLBACKS.size.toLong()).toInt()]
}

/**
 * A devotional's reflection, kept in the Bible study journal (ONE_PLACE 3.3) by the same
 * [com.craftflowtechnologies.meetingmind.core.biblestudy.JournalSession] a guided session uses.
 * Pure helpers only; the repository does the reading and writing.
 */
object DevotionalJournal {
    /** The question a reflection answers, or a plain prompt when the devotional asked none. */
    const val DEFAULT_PROMPT = "Your reflection"

    /** The passage the entry is filed under: the devotional's first scripture, else the one remembered in its metadata. */
    fun passageOf(d: Devotional, note: Note): ScriptureReference? =
        d.scripture.firstOrNull() ?: note.metadata[DevotionalNotes.META_PASSAGE]?.let { ScriptureReferenceParser.parse(it) }

    /** Metadata that links the journal entry back to its devotional. */
    fun links(daily: DailyDevotional): Map<String, String> = mapOf(
        JournalMeta.DEVOTIONAL_NOTE to daily.note.id,
        JournalMeta.DEVOTIONAL_DAY to daily.devotional.day.iso
    )

    /** The session state a reflection is written through: one answerable step, headed by the question it answers. */
    fun state(question: String?, text: String): SessionState =
        SessionState(SessionKind.DEVOTIONAL, SessionPlan.steps(SessionKind.DEVOTIONAL))
            .withPrompt(DEVOTIONAL_STEP, question?.takeIf { it.isNotBlank() } ?: DEFAULT_PROMPT)
            .answer(DEVOTIONAL_STEP, text)

    /** The reflection text stored in a journal entry's blocks. */
    fun textOf(blocks: List<NoteBlock>): String =
        blocks.sortedBy { it.position }.filter { it.sectionKey == DEVOTIONAL_STEP && it.type == NoteBlockType.PARAGRAPH }
            .joinToString("\n") { it.content.text }.trim()

    /** Whether [note] is a devotional's reflection in the journal. */
    fun isReflection(note: Note) = note.metadata[JournalMeta.SESSION_KIND] == SessionKind.DEVOTIONAL.key

    /** The first non-blank line of a reflection, trimmed for a list row. */
    fun firstLine(text: String, max: Int = 140): String? =
        text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.let { if (it.length > max) it.take(max - 1).trimEnd() + "…" else it }
}
