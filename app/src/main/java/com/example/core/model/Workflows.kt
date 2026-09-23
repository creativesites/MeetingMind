package com.example.core.model

import com.example.core.notes.RichText
import com.example.core.repository.NoteRepository
import com.example.core.scripture.BibleBooks

/** A workflow is a [RecordingType]: one concept, not two (docs/PLAN_V1.md §3). */
typealias Workflow = RecordingType

/** Who writes a template section's content. */
enum class SectionSource { USER, AI }

/**
 * One section of a workflow's note: a heading and a body.
 *
 * [SectionSource.AI] sections are written by processing from the recording and cite it;
 * [SectionSource.USER] sections are prompts for the person. [isPrivate] sections are left out of
 * export and share unless the person includes them.
 */
data class TemplateSection(
    val key: String,
    val title: String,
    val source: SectionSource,
    val bodyType: NoteBlockType = NoteBlockType.PARAGRAPH,
    val hint: String? = null,
    val isPrivate: Boolean = false
)

data class NoteTemplate(val sections: List<TemplateSection>) {
    val privateKeys: Set<String> get() = sections.filter { it.isPrivate }.map { it.key }.toSet()
    val isEmpty: Boolean get() = sections.isEmpty()
}

/** A processing-screen row: a label people understand, standing for one or more engine stages. */
data class ProcessingStageRow(
    val label: String,
    val stages: Set<ProcessingStage>
)

/**
 * The per-workflow hooks that sit beside [RecordingType.intelligenceProfile]. Every rule about
 * what a workflow means lives here or in [RecordingType]; no screen branches on a type itself.
 */
object Workflows {

    val faith: List<RecordingType> = listOf(
        RecordingType.SERMON, RecordingType.BIBLE_STUDY, RecordingType.DEVOTIONAL, RecordingType.PRAYER,
        RecordingType.PRAYER_REQUEST, RecordingType.TESTIMONY, RecordingType.GRATITUDE, RecordingType.REFLECTION
    )

    fun space(type: RecordingType): NotebookSpace = when (type) {
        RecordingType.MEETING, RecordingType.INTERVIEW, RecordingType.BRAINSTORM, RecordingType.CONVERSATION -> NotebookSpace.WORK
        RecordingType.LECTURE, RecordingType.RESEARCH -> NotebookSpace.LEARNING
        in faith -> NotebookSpace.FAITH
        else -> NotebookSpace.PERSONAL
    }

    /** Whether it makes sense to start this workflow by recording (a gratitude list doesn't). */
    fun isRecordable(type: RecordingType): Boolean = type !in setOf(RecordingType.GRATITUDE, RecordingType.PRAYER_REQUEST)

    /** Private notes keep their text out of the list and out of exports by default. */
    fun isPrivateByDefault(type: RecordingType): Boolean = type in setOf(
        RecordingType.JOURNAL, RecordingType.PRAYER, RecordingType.PRAYER_REQUEST, RecordingType.DEVOTIONAL,
        RecordingType.GRATITUDE, RecordingType.REFLECTION, RecordingType.TESTIMONY
    )

    /** Faith notes read in a serif face (PLAN_V1 §7.8). */
    fun usesSerif(type: RecordingType): Boolean = type in faith || type == RecordingType.JOURNAL

    fun template(type: RecordingType): NoteTemplate = NoteTemplate(
        when (type) {
            RecordingType.SERMON -> listOf(
                TemplateSection("main_scripture", "Scripture", SectionSource.AI, hint = "The passages the sermon was built on"),
                TemplateSection("key_message", "Main message", SectionSource.AI, hint = "The sermon in a sentence or two"),
                TemplateSection("key_points", "Key points", SectionSource.AI, NoteBlockType.NUMBERED, "Each point, in order"),
                TemplateSection("quotes", "Memorable quotes", SectionSource.AI, NoteBlockType.QUOTE, "Words worth keeping"),
                TemplateSection("application", "Application", SectionSource.AI, hint = "What the preacher asked people to do"),
                TemplateSection("prayer_points", "Prayer points", SectionSource.AI, NoteBlockType.BULLET),
                TemplateSection("reflection_questions", "Questions to reflect on", SectionSource.AI, NoteBlockType.BULLET),
                TemplateSection("my_notes", "My notes", SectionSource.USER, hint = "What stood out to you"),
                TemplateSection("my_response", "My response", SectionSource.USER, NoteBlockType.CHECKLIST, "What you'll do this week", isPrivate = true),
                TemplateSection("my_prayer", "My prayer", SectionSource.USER, hint = "Write your prayer", isPrivate = true)
            )
            RecordingType.BIBLE_STUDY -> listOf(
                TemplateSection("passage", "Passage", SectionSource.USER, hint = "The passage you're studying"),
                TemplateSection("observations", "Observations", SectionSource.USER, NoteBlockType.BULLET, "What does it say?"),
                TemplateSection("interpretation", "What it means", SectionSource.USER, hint = "In your own words"),
                TemplateSection("application", "Application", SectionSource.USER, hint = "How does it apply to your life?"),
                TemplateSection("questions", "Questions", SectionSource.USER, NoteBlockType.BULLET, "What are you still wondering?")
            )
            RecordingType.DEVOTIONAL -> listOf(
                TemplateSection("scripture", "Scripture", SectionSource.USER, hint = "Today's passage"),
                TemplateSection("stood_out", "What stood out", SectionSource.USER, hint = "A word, a phrase, a thought"),
                TemplateSection("reflection", "Reflection", SectionSource.USER, hint = "What is it saying to you?", isPrivate = true),
                TemplateSection("prayer", "Prayer", SectionSource.USER, hint = "Write your prayer", isPrivate = true),
                TemplateSection("today", "Today I will", SectionSource.USER, NoteBlockType.CHECKLIST, "One small step")
            )
            RecordingType.PRAYER -> listOf(
                TemplateSection("prayer", "Prayer", SectionSource.USER, hint = "Write or speak your prayer", isPrivate = true)
            )
            RecordingType.PRAYER_REQUEST -> listOf(
                TemplateSection("request", "Request", SectionSource.USER, hint = "What are you praying for?", isPrivate = true),
                TemplateSection("scripture", "Scripture to stand on", SectionSource.USER, hint = "A promise to hold on to"),
                TemplateSection("updates", "Updates", SectionSource.USER, hint = "What has happened since", isPrivate = true)
            )
            RecordingType.TESTIMONY -> listOf(
                TemplateSection("situation", "The situation", SectionSource.USER, hint = "What were you facing?"),
                TemplateSection("prayed_for", "What I prayed for", SectionSource.USER),
                TemplateSection("what_happened", "What happened", SectionSource.USER),
                TemplateSection("learned", "What I learned", SectionSource.USER),
                TemplateSection("scripture", "Scripture", SectionSource.USER, hint = "A verse that carried you")
            )
            RecordingType.GRATITUDE -> listOf(
                TemplateSection("thankful", "Today I'm thankful for", SectionSource.USER, NoteBlockType.NUMBERED, "Something small counts too")
            )
            RecordingType.REFLECTION -> listOf(
                TemplateSection("reflection", "Reflection", SectionSource.USER, hint = "Your thoughts, in your words", isPrivate = true)
            )
            RecordingType.MEETING -> listOf(
                TemplateSection("agenda", "Agenda", SectionSource.USER, NoteBlockType.BULLET),
                TemplateSection("my_notes", "Notes", SectionSource.USER),
                TemplateSection("next_steps", "Next steps", SectionSource.USER, NoteBlockType.CHECKLIST)
            )
            RecordingType.LECTURE -> listOf(
                TemplateSection("concepts", "Key concepts", SectionSource.USER, NoteBlockType.BULLET),
                TemplateSection("my_notes", "Notes", SectionSource.USER),
                TemplateSection("questions", "Questions", SectionSource.USER, NoteBlockType.BULLET)
            )
            RecordingType.INTERVIEW -> listOf(
                TemplateSection("questions", "Questions", SectionSource.USER, NoteBlockType.NUMBERED),
                TemplateSection("my_notes", "Notes", SectionSource.USER)
            )
            else -> emptyList()
        }
    )

    /**
     * The blocks a new note of [type] starts with: a heading and a body per section.
     *
     * A note written by hand gets every section to fill in. A recording's note gets only the
     * person's own sections, because the AI sections are written by processing once the
     * transcript exists.
     */
    fun startingBlocks(type: RecordingType, noteId: String, forRecording: Boolean): List<NoteBlock> {
        val sections = template(type).sections.filter { !forRecording || it.source == SectionSource.USER }
        return sections.flatMap { section ->
            listOf(
                NoteBlock(
                    id = NoteRepository.newId("block"), noteId = noteId, position = 0, type = NoteBlockType.HEADING_2,
                    content = RichText.plain(section.title), sectionKey = section.key
                ),
                NoteBlock(
                    id = NoteRepository.newId("block"), noteId = noteId, position = 0, type = section.bodyType,
                    payload = section.hint?.let { mapOf(NoteBlock.PAYLOAD_HINT to it) }.orEmpty(),
                    sectionKey = section.key
                )
            )
        }
    }

    /**
     * The processing screen's rows for [type] (design spec §5.1). A row only appears for work the
     * pipeline will actually do: "Identifying speakers" is gone for a one-person recording.
     */
    fun processingStageRows(type: RecordingType, speakerCount: Int?): List<ProcessingStageRow> {
        val head = listOfNotNull(
            ProcessingStageRow("Preparing audio", setOf(ProcessingStage.IDLE, ProcessingStage.PREPARING_AUDIO, ProcessingStage.DETECTING_SPEECH)),
            ProcessingStageRow("Transcribing", setOf(ProcessingStage.TRANSCRIBING)),
            ProcessingStageRow("Identifying speakers", setOf(ProcessingStage.DIARIZING)).takeIf { speakerCount != 1 },
            ProcessingStageRow("Building the transcript", setOf(ProcessingStage.CLEANING_TRANSCRIPT))
        )
        val (analysis, notes) = when (type) {
            RecordingType.SERMON -> "Themes, passages & scripture references" to "Sermon notes"
            RecordingType.BIBLE_STUDY -> "Passages & observations" to "Study notes"
            RecordingType.LECTURE -> "Concepts & definitions" to "Study notes"
            RecordingType.INTERVIEW -> "Questions, answers & quotes" to "Interview notes"
            RecordingType.MEETING, RecordingType.CUSTOM, RecordingType.GENERAL -> "Topics, decisions & actions" to "Meeting brief"
            RecordingType.IDEA, RecordingType.VOICE_MEMO, RecordingType.JOURNAL, RecordingType.DICTATION -> "Key points" to "Cleaned-up note"
            else -> type.intelligenceProfile().analyzingStageLabel.removeSuffix("...") to "${type.displayName} notes"
        }
        return head + listOf(
            ProcessingStageRow(analysis, setOf(ProcessingStage.ANALYZING)),
            ProcessingStageRow(notes, setOf(ProcessingStage.SAVING_RESULTS, ProcessingStage.COMPLETED))
        )
    }

    /** Words speech recognition should expect for this workflow. */
    fun vocabularyHints(type: RecordingType): List<String> = when (type) {
        RecordingType.SERMON, RecordingType.BIBLE_STUDY, RecordingType.DEVOTIONAL ->
            BibleBooks.spokenNames + listOf("Psalm", "Selah", "Hallelujah", "Amen", "Gospel", "Pharisees", "Gentiles", "Messiah")
        else -> emptyList()
    }
}
