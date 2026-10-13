package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType

/**
 * What a stretch of a note *is*, for the listener (docs/mvp/READ_TO_ME_PLAN.md §16.3). Notes have no
 * block types for observation, prayer or commentary; the role is read off the block type, the
 * section key, the nearest heading and who wrote it. Never decided by a model.
 */
enum class SpeechRole {
    SCRIPTURE, QUOTE, TRANSCRIPT, COMMENTARY, OBSERVATION, INTERPRETATION, APPLICATION,
    QUESTION, PRAYER, PERSONAL, HEADING, OTHER;

    /** Words that are not the app's to rephrase: verbatim in Smart Read as well as Read Exactly. */
    val lockedInSmartRead: Boolean
        get() = this == SCRIPTURE || this == QUOTE || this == TRANSCRIPT || this == PRAYER || this == PERSONAL
}

object StudyRoleClassifier {
    private val headings = setOf(NoteBlockType.HEADING_1, NoteBlockType.HEADING_2, NoteBlockType.HEADING_3)

    private val prayerKeys = setOf("prayer", "daily_prayer", "my_prayer", "prayer_points", "sg_prayer")
    private val questionKeys = setOf("questions", "reflection_questions", "daily_question", "sg_questions", "sg_icebreaker")
    private val applicationKeys = setOf("application", "daily_application", "daily_live", "live", "today", "sg_application")
    private val observationKeys = setOf("observations", "observation")
    private val interpretationKeys = setOf("interpretation")
    private val commentaryKeys = setOf("key_message", "key_points", "daily_reflection", "daily_motivation", "sg_main_idea", "main_idea")
    private val personalKeys = setOf("reflection", "my_notes", "my_response", "daily_my_response", "stood_out", "request", "updates")

    /** Guided-session step ids whose HEADING_3 holds the question that was shown (StudyJournal). */
    private val guidedAnswerKey = Regex("""q\d+|verse-.+""")
    private val guidedStepKeys = setOf("observation", "application", "prayer", "reflection", "live", "scripture")

    /** [headingText] is the nearest heading above [block], for sections that carry their meaning only in it. */
    fun classify(block: NoteBlock, headingText: String?): SpeechRole {
        when (block.type) {
            NoteBlockType.SCRIPTURE -> return SpeechRole.SCRIPTURE
            NoteBlockType.QUOTE -> return SpeechRole.QUOTE
            NoteBlockType.TRANSCRIPT_EXCERPT -> return SpeechRole.TRANSCRIPT
            else -> Unit
        }
        val key = block.sectionKey
        if (block.type in headings) return if (isQuestionHeading(block, key)) SpeechRole.QUESTION else SpeechRole.HEADING
        roleForKey(key)?.let { return it }
        // A guided answer: the heading above it is a question, whose wording must not decide the role.
        if (key != null && guidedAnswerKey.matches(key)) return SpeechRole.PERSONAL
        if (key != null) headingText?.let { roleForHeading(it) }?.let { return it }
        if (block.isUserEdited && key != null) return SpeechRole.PERSONAL
        if (block.source == BlockSource.AI) return SpeechRole.COMMENTARY
        return SpeechRole.OTHER
    }

    /** Written by a model and not touched by the person since. */
    fun isAiGenerated(block: NoteBlock): Boolean = block.source == BlockSource.AI && !block.isUserEdited

    private fun isQuestionHeading(block: NoteBlock, key: String?): Boolean {
        if (block.type != NoteBlockType.HEADING_3 || key == null) return false
        if (guidedAnswerKey.matches(key)) return true
        return key in guidedStepKeys && block.content.text.trimEnd().endsWith("?")
    }

    private fun roleForKey(key: String?): SpeechRole? = when {
        key == null -> null
        key in prayerKeys -> SpeechRole.PRAYER
        key in questionKeys -> SpeechRole.QUESTION
        key in applicationKeys -> SpeechRole.APPLICATION
        key in observationKeys -> SpeechRole.OBSERVATION
        key in interpretationKeys -> SpeechRole.INTERPRETATION
        key in commentaryKeys -> SpeechRole.COMMENTARY
        key in personalKeys -> SpeechRole.PERSONAL
        else -> null
    }

    /** Study templates (`study_soap_2`) and the small-group guide carry the section's meaning only in its heading. */
    internal fun roleForHeading(text: String): SpeechRole? {
        val t = text.lowercase()
        return when {
            "pray" in t -> SpeechRole.PRAYER
            "observ" in t -> SpeechRole.OBSERVATION
            "interpret" in t || "what it means" in t -> SpeechRole.INTERPRETATION
            "applic" in t || "live it" in t || "apply" in t -> SpeechRole.APPLICATION
            "question" in t || "discuss" in t -> SpeechRole.QUESTION
            else -> null
        }
    }
}

enum class LabelStyle { FULL, SHORT, OFF }

/** The closed set of phrases that tell a listener what they are hearing. Fixed text, never generated. */
object RoleLabels {
    fun lead(role: SpeechRole, aiGenerated: Boolean, style: LabelStyle): String? = when (style) {
        LabelStyle.OFF -> null
        LabelStyle.SHORT -> when (role) {
            SpeechRole.QUESTION -> "Question."
            SpeechRole.PRAYER -> "Prayer."
            SpeechRole.COMMENTARY -> if (aiGenerated) "AI-written." else null
            else -> null
        }
        LabelStyle.FULL -> when (role) {
            SpeechRole.QUESTION -> "Question for reflection."
            SpeechRole.PRAYER -> "Prayer."
            SpeechRole.PERSONAL -> "Your note."
            SpeechRole.OBSERVATION -> "Observation."
            SpeechRole.INTERPRETATION -> "What it means."
            SpeechRole.APPLICATION -> "Application."
            SpeechRole.COMMENTARY -> if (aiGenerated) "AI-written." else "Commentary."
            SpeechRole.QUOTE -> "Quote."
            else -> null
        }
    }
}
