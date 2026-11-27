package com.craftflowtechnologies.meetingmind.feature.personal.seeds

import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace

/** What the seed jar would do with a note that landed in Personal without saying where it belongs. */
sealed interface Suggestion {
    /** Move the note into a space the person uses. */
    data class MoveTo(val space: NotebookSpace) : Suggestion

    /** Make the note a task in Personal. */
    data object MakeTask : Suggestion
}

/**
 * Keyword rules for the To sort list. Pure: it returns data, and the screen words it.
 *
 * Matching is case-insensitive and on whole words. A space rule wins over the task rule, and the
 * task rule only applies to short text (under [TASK_MAX_WORDS] words), so a long recording never
 * turns into a task. A space the person does not use is never suggested, and Personal never is.
 */
object SeedSort {

    /** Text with this many words or more is never suggested as a task. */
    const val TASK_MAX_WORDS = 15

    private val spaceRules: List<Pair<NotebookSpace, Regex>> = listOf(
        NotebookSpace.WORK to words(
            "meeting", "client", "budget", "project", "deadline", "team", "invoice", "quarter",
            "Q[1-4]", "hiring", "report", "boss", "colleague"
        ),
        NotebookSpace.FAITH to words(
            "pray", "prayer", "psalm", "verse", "church", "sermon", "god", "bible", "scripture", "worship"
        ),
        NotebookSpace.LEARNING to words(
            "lecture", "chapter", "exam", "study", "course", "assignment", "revise"
        )
    )

    private val taskRule: Regex = words(
        "call", "buy", "book", "pay", "email", "remind", "fix", "pick up", "renew", "send"
    )

    /**
     * The suggestion for a note's text, or null when nothing matches. [spaces] are the spaces the
     * person uses. When two spaces match, the one with more keyword hits wins; ties go to Work,
     * then Faith, then Learning.
     */
    fun suggest(text: String, spaces: Set<NotebookSpace>): Suggestion? {
        val clean = text.trim()
        if (clean.isEmpty()) return null

        val bestSpace = spaceRules
            .filter { (space, _) -> space in spaces && space != NotebookSpace.PERSONAL }
            .map { (space, rule) -> space to rule.findAll(clean).count() }
            .filter { (_, hits) -> hits > 0 }
            .maxByOrNull { (_, hits) -> hits }
        if (bestSpace != null) return Suggestion.MoveTo(bestSpace.first)

        val wordCount = clean.split(WHITESPACE).size
        if (wordCount < TASK_MAX_WORDS && taskRule.containsMatchIn(clean)) return Suggestion.MakeTask
        return null
    }

    private val WHITESPACE = Regex("\\s+")

    /** Whole-word, case-insensitive alternation. A space in a term matches any run of whitespace. */
    private fun words(vararg terms: String): Regex {
        val alternation = terms.joinToString("|") { it.replace(" ", "\\s+") }
        return Regex("\\b(?:$alternation)\\b", RegexOption.IGNORE_CASE)
    }
}
