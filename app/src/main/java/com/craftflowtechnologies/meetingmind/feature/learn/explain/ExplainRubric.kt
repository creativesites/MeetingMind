package com.craftflowtechnologies.meetingmind.feature.learn.explain

import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.TeachBackCriterion

/** How one key idea stands. The words are the on-screen words; there is no percentage. */
enum class IdeaCoverage(val label: String) {
    COVERED("Covered"),
    PARTLY("Partly"),
    NOT_YET("Not yet")
}

/**
 * The rubric for "Explain it aloud", built from the concept's own definition and the lecturer's words
 * (its evidence). Pure and deterministic: no AI is needed to make it, so the live checklist can use it too.
 */
object ExplainRubric {
    const val MAX_CRITERIA = 4
    private const val MAX_TERMS = 6
    private const val MAX_LABEL_CHARS = 60
    private const val COVERED_SHARE = 0.6

    private val stopWords = setOf(
        "about", "above", "after", "again", "against", "also", "because", "before", "being", "between",
        "could", "does", "during", "each", "either", "every", "from", "have", "into", "itself", "just",
        "more", "most", "other", "over", "same", "should", "some", "such", "than", "that", "their",
        "them", "then", "there", "these", "they", "this", "those", "through", "under", "until", "very",
        "what", "when", "where", "which", "while", "will", "with", "within", "would", "your", "lecturer",
        "concept", "idea", "example", "explain", "student", "means", "called", "using", "used", "like"
    )

    /** One criterion per definition sentence and per lecturer quote, most important first, at most [MAX_CRITERIA]. */
    fun build(concept: LearningConcept): List<TeachBackCriterion> {
        val fromDefinition = sentences(concept.definition)
            .filter { keyTerms(it).isNotEmpty() }
            .take(2)
            .map { TeachBackCriterion(claim = it, required = true, evidence = concept.evidence.firstOrNull()) }
        val seen = fromDefinition.map { it.claim.lowercase() }.toMutableSet()
        val fromQuotes = concept.evidence
            .filter { it.quote.isNotBlank() }
            .flatMap { ev -> sentences(ev.quote).map { it to ev } }
            .filter { (sentence, _) -> keyTerms(sentence).isNotEmpty() && seen.add(sentence.lowercase()) }
            .take(MAX_CRITERIA - fromDefinition.size)
            .map { (sentence, ev) -> TeachBackCriterion(claim = sentence, required = false, evidence = ev) }
        val all = fromDefinition + fromQuotes
        if (all.isNotEmpty()) return all.take(MAX_CRITERIA)
        // Nothing usable in the definition or quotes: the name alone is the one idea, when it has words.
        return if (keyTerms(concept.name).isNotEmpty()) {
            listOf(TeachBackCriterion(claim = concept.name, required = true, evidence = concept.evidence.firstOrNull()))
        } else {
            emptyList()
        }
    }

    /** The short label shown in the checklist: the first clause of the claim, cut to a line. */
    fun label(claim: String): String {
        val clause = claim.split(',', ';', ':', '—', '–', '(').first().trim().trimEnd('.')
        return if (clause.length <= MAX_LABEL_CHARS) clause else clause.take(MAX_LABEL_CHARS - 1).trimEnd() + "…"
    }

    /** Content words of a claim, in order, without stop words. These are what the live match looks for. */
    fun keyTerms(claim: String): List<String> = claim.lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 4 && it !in stopWords }
        .distinct()
        .take(MAX_TERMS)

    /**
     * Cheap local check of one idea against spoken or typed words: a term counts when a word of the
     * explanation starts with its first four letters (so "binds" and "binding" match). Covered when at
     * least [COVERED_SHARE] of the terms are there, partly when some are, not yet when none are.
     */
    fun coverage(claim: String, transcript: String): IdeaCoverage {
        val terms = keyTerms(claim)
        if (terms.isEmpty()) return IdeaCoverage.NOT_YET
        val words = transcript.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        val matched = terms.count { term -> words.any { it.startsWith(term.take(4)) } }
        return when {
            matched == 0 -> IdeaCoverage.NOT_YET
            matched.toDouble() / terms.size >= COVERED_SHARE -> IdeaCoverage.COVERED
            else -> IdeaCoverage.PARTLY
        }
    }

    /** The first key term of [claim] that the explanation has not used yet, for a hint. */
    fun unusedTerm(claim: String, transcript: String): String? {
        val words = transcript.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        return keyTerms(claim).firstOrNull { term -> words.none { it.startsWith(term.take(4)) } }
    }

    private fun sentences(text: String): List<String> = text
        .split(Regex("(?<=[.!?;])\\s+"))
        .map { it.trim().trimEnd('.', '!', '?', ';') }
        .filter { it.length >= 12 }
        .map { it.take(180) }
}
