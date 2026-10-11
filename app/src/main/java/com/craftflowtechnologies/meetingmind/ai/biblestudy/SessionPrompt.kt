package com.craftflowtechnologies.meetingmind.ai.biblestudy

import com.craftflowtechnologies.meetingmind.ai.assistant.FaithGuard
import com.craftflowtechnologies.meetingmind.ai.faith.Prompts
import com.craftflowtechnologies.meetingmind.ai.notes.NoteAiEngine
import com.craftflowtechnologies.meetingmind.core.biblestudy.Need
import com.craftflowtechnologies.meetingmind.core.biblestudy.StepType
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser

/** What the model is asked about for one step of a guided session. */
data class SessionBrief(
    val passage: ScriptureReference,
    /** The passage's text, when available, so the model works from the real words. */
    val passageText: String?,
    val step: StepType,
    /** For a verse step: the verse. */
    val verse: Int? = null,
    val need: Need? = null,
    /** Style settings (tradition, voice, reading level, language) always apply; profile text only with a personal touch. */
    val profile: DevotionalProfile = DevotionalProfile(),
    val date: java.time.LocalDate? = null,
    val name: String? = null
)

/** One line of background and the verse it is drawn from. */
data class CitedLine(val text: String, val cite: ScriptureReference)

/** What the model suggested: background with citations, and questions. Never answers. */
data class Suggestions(val context: List<CitedLine>, val questions: List<String>) {
    val isEmpty: Boolean get() = context.isEmpty() && questions.isEmpty()
}

/**
 * The prompt for a guided study step and the checks on the answer (ONE_PLACE §3.2). Generic by
 * default: the reader's profile, name and life season go in only when Personal touch allows it for
 * the day, exactly as for devotionals (BIBLE_DEVOTIONAL_V2 §8).
 */
object SessionPrompt {

    val CONTRACT: String get() = Prompts.faithContract + "\n\n" + Prompts.get("bible_session").system()

    fun prompt(brief: SessionBrief): String {
        val p = brief.profile
        val personal = p.personalTouch.appliesOn(brief.date)
        return buildString {
            appendLine(CONTRACT)
            appendLine()
            appendLine("Tradition: ${p.tradition.label}.")
            appendLine("Voice: ${p.tone.guidance}.")
            appendLine("Written for ${p.audience.guidance}, in ${p.readingLevel.guidance}.")
            if (p.language.isNotBlank()) appendLine("Write in ${p.language}.")
            appendLine("Passage: ${brief.passage.display()}")
            brief.passageText?.let { appendLine("Passage text (for your understanding; do not copy it out): ${it.take(1500)}") }
            when (brief.step) {
                StepType.WHY -> appendLine("Step: why this passage matters. Give 2 context lines, each with a cite, and no questions.")
                StepType.OBSERVE, StepType.SCRIPTURE -> appendLine("Step: observation. Give 3 open questions that help the reader notice what the passage says, and 1 context line with a cite.")
                StepType.APPLY -> appendLine("Step: application. Give 2 open questions that help the reader think about what the passage asks of them, and no context lines.")
                StepType.VERSE -> appendLine("Step: ${brief.verse?.let { "verse $it of the passage" } ?: "the passage"}. Give 2 open questions about it and 1 context line with a cite.")
                else -> appendLine("Step: ${brief.step.label}. Give 2 open questions.")
            }
            brief.need?.let { appendLine("The reader came to this passage feeling: ${it.label.lowercase()}. Let that shape which questions you ask, gently; do not assume more.") }
            if (!personal) appendLine("Audience: a general reader. You know nothing about them; do not guess or mention their job, family, health, mood, location or circumstances.")
            if (personal) appendPersonal(brief)
            appendLine()
            appendLine("Return only the JSON object.")
        }
    }

    private fun StringBuilder.appendPersonal(brief: SessionBrief) {
        val p = brief.profile
        val lines = buildList {
            brief.name?.takeIf { it.isNotBlank() }?.let { add("Their first name: $it (use it at most once, or not at all).") }
            if (p.topics.isNotEmpty()) add("They'd like to grow in: ${p.topics.joinToString(", ")}.")
            p.season?.let { add("Life season: $it.") }
            p.aboutMe.trim().takeIf { it.isNotEmpty() }?.let { add("About them, in their words: ${it.take(400)}") }
        }
        if (lines.isEmpty()) return
        appendLine()
        appendLine("Personal background - the reader invited a personal touch. Keep it gentle background: the passage is the subject, never their situation. Use it in at most one question, lightly.")
        lines.forEach { appendLine("- $it") }
    }

    /**
     * Reads the model's answer. A context line without a real Bible reference is dropped (every claim
     * cites a verse), a question that is not a question is dropped, and the faith guard strips any
     * voice-of-God sentence. Returns null when nothing usable is left.
     */
    fun parse(raw: String): Suggestions? {
        val json = NoteAiEngine.extractJsonObject(raw) ?: return null
        val context = buildList {
            val arr = json.optJSONArray("context")
            for (i in 0 until (arr?.length() ?: 0)) {
                val o = arr!!.optJSONObject(i) ?: continue
                val text = FaithGuard.clean(o.optString("text").trim())
                val cite = ScriptureReferenceParser.parse(o.optString("cite").trim()) ?: continue
                if (text.isNotEmpty() && text.length <= 300) add(CitedLine(text, cite))
            }
        }.take(3)
        val questions = buildList {
            val arr = json.optJSONArray("questions")
            for (i in 0 until (arr?.length() ?: 0)) {
                val q = FaithGuard.clean(arr!!.optString(i).trim())
                if (q.endsWith("?") && q.length in 8..220) add(q)
            }
        }.take(3)
        return Suggestions(context, questions).takeUnless { it.isEmpty }
    }
}
