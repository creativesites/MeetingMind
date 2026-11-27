package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import kotlin.math.ln

/**
 * Ask Learning (design spec §9, decision D10): answers come from the student's own sources, every
 * claim is numbered back to a passage, "not in your lecture" is said plainly, and general knowledge
 * is given only when the student asks for it, always labelled.
 *
 * This file is the part that needs no model: passages, retrieval and the offline answer.
 */

/** One numbered passage the answer may cite. [confidence] is the lowest ASR confidence inside it. */
data class AskPassage(
    val number: Int,
    val text: String,
    val evidence: LearningEvidence,
    val sourceTitle: String,
    val confidence: Float? = null
)

enum class AnswerKind { FROM_SOURCE, NOT_IN_SOURCE, GENERAL }

data class LearningAnswer(
    val text: String,
    val kind: AnswerKind,
    /** Passages the answer cites, in the order they are numbered in [text]. */
    val cited: List<AskPassage>,
    /** For NOT_IN_SOURCE: the closest passage, so the student can still check. */
    val closest: AskPassage? = null,
    /** True when a cited passage comes from audio that may be unclear. */
    val unclearSource: Boolean = false,
    /** True when no model was available and the answer quotes the passages directly. */
    val extractive: Boolean = false
)

object LearningAsk {
    const val UnclearConfidence = 0.6f
    private const val ChunkMs = 45_000L
    const val QuoteChars = 240

    private val stop = setOf(
        "the", "and", "for", "are", "was", "what", "why", "how", "does", "did", "with", "that", "this",
        "from", "into", "about", "when", "which", "who", "whom", "its", "it's", "is", "of", "to", "in",
        "a", "an", "on", "do", "be", "can", "you", "me", "my", "we", "our", "they", "their", "than",
        "then", "there", "these", "those", "explain", "tell", "mean", "means", "between", "difference"
    )

    fun terms(text: String): List<String> =
        text.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length > 2 && it !in stop }

    /** Transcript in ~45 s chunks, each keeping its segment ids and timing for the citation. */
    fun transcriptPassages(
        segments: List<TranscriptSegment>,
        noteId: String?,
        meetingId: String?,
        sourceTitle: String
    ): List<AskPassage> {
        if (segments.isEmpty()) return emptyList()
        val out = mutableListOf<AskPassage>()
        var bucket = mutableListOf<TranscriptSegment>()
        fun flush() {
            if (bucket.isEmpty()) return
            val first = bucket.first()
            // The quote the student sees and the clip they hear cover the same segments:
            // whole segments up to about QuoteChars, never fewer than one.
            var quoteEnd = 1
            var chars = first.text.trim().length
            while (quoteEnd < bucket.size && chars + bucket[quoteEnd].text.trim().length + 1 <= QuoteChars) {
                chars += bucket[quoteEnd].text.trim().length + 1
                quoteEnd++
            }
            val quoted = bucket.take(quoteEnd)
            out += AskPassage(
                number = 0,
                text = bucket.joinToString(" ") { it.text.trim() },
                evidence = LearningEvidence(
                    noteId = noteId, meetingId = meetingId ?: first.meetingId,
                    segmentIds = bucket.map { it.id }, startMs = first.startMs, endMs = quoted.last().endMs,
                    quote = quoted.joinToString(" ") { it.text.trim() }
                ),
                sourceTitle = sourceTitle,
                confidence = bucket.mapNotNull { it.confidence }.minOrNull()
            )
            bucket = mutableListOf()
        }
        segments.sortedBy { it.startMs }.forEach { seg ->
            if (bucket.isNotEmpty() && seg.startMs - bucket.first().startMs > ChunkMs) flush()
            bucket += seg
        }
        flush()
        return out
    }

    /** Concept definitions, cited to their own lecture evidence when they have it. */
    fun conceptPassages(concepts: List<LearningConcept>, titleFor: (sessionId: String) -> String): List<AskPassage> =
        concepts.filter { !it.isDismissed && it.definition.isNotBlank() }.map { c ->
            val ev = c.evidence.firstOrNull { it.quote.isNotBlank() || it.startMs != null } ?: LearningEvidence(quote = c.definition.take(220))
            AskPassage(0, "${c.name}: ${c.definition}" + (ev.quote.takeIf { it.isNotBlank() }?.let { " (said: $it)" } ?: ""), ev, titleFor(c.sessionId))
        }

    /** Paragraphs of typed notes, for sessions with no transcript. */
    fun notePassages(noteText: String, noteId: String?, sourceTitle: String): List<AskPassage> =
        noteText.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.length > 20 }.map { p ->
            AskPassage(0, p, LearningEvidence(noteId = noteId, quote = p.take(220)), sourceTitle)
        }

    /** Scores passages by shared terms, rarer terms counting more. Returns the best [k], numbered 1..k. */
    fun rank(question: String, passages: List<AskPassage>, k: Int = 8): List<Pair<AskPassage, Double>> {
        val q = terms(question).distinct()
        if (q.isEmpty() || passages.isEmpty()) return emptyList()
        val docs = passages.map { terms(it.text).toSet() }
        val df = q.associateWith { t -> docs.count { t in it } }
        val n = passages.size.toDouble()
        return passages.mapIndexed { i, p ->
            val score = q.sumOf { t -> if (t in docs[i]) ln(1.0 + n / (1.0 + (df[t] ?: 0))) else 0.0 }
            p to score
        }.filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(k)
            .mapIndexed { i, (p, s) -> p.copy(number = i + 1) to s }
    }

    /** True when the best passage shares enough of the question to be worth answering from. */
    fun coversQuestion(question: String, ranked: List<Pair<AskPassage, Double>>): Boolean {
        val best = ranked.firstOrNull() ?: return false
        val q = terms(question).distinct()
        if (q.isEmpty()) return false
        val shared = q.count { it in terms(best.first.text).toSet() }
        return shared * 2 >= q.size || (q.size >= 4 && shared >= 2)
    }

    /** The answer with no model: the closest passages, quoted and numbered. */
    fun extractiveAnswer(question: String, ranked: List<Pair<AskPassage, Double>>): LearningAnswer {
        if (!coversQuestion(question, ranked)) {
            return LearningAnswer(
                text = "This doesn't seem to be covered in your sources.",
                kind = AnswerKind.NOT_IN_SOURCE, cited = emptyList(),
                closest = ranked.firstOrNull()?.first, extractive = true
            )
        }
        val top = ranked.take(2).map { it.first }
        val text = top.joinToString("\n\n") { "“${it.text.trim().take(320)}” [${it.number}]" }
        return LearningAnswer(
            text = text, kind = AnswerKind.FROM_SOURCE, cited = top,
            unclearSource = top.any { (it.confidence ?: 1f) < UnclearConfidence }, extractive = true
        )
    }

    /** Numbers like [2] or [1, 3] in a model answer, resolved to passages. Unknown numbers are dropped. */
    /**
     * Whether an answer is carried by the passages it cites: at least [SupportOverlap] of its content
     * words (longer than three letters, citation marks removed) appear in those passages. A paraphrase
     * passes; a claim the passages don't make does not, so it is never shown as "from your lecture".
     */
    fun supportedBy(text: String, cited: List<AskPassage>): Boolean {
        if (cited.isEmpty()) return false
        fun words(s: String) = Regex("""[\p{L}\p{N}]+""").findAll(s.lowercase()).map { it.value }.filter { it.length > 3 }.toList()
        val answer = words(text.replace(Regex("""\[[\d,\s]+]"""), " ")).distinct()
        if (answer.isEmpty()) return false
        val source = words(cited.joinToString(" ") { it.text }).toSet()
        return answer.count { it in source }.toDouble() / answer.size >= SupportOverlap
    }

    const val SupportOverlap = 0.4

    fun citedIn(text: String, ranked: List<AskPassage>): List<AskPassage> {
        val nums = Regex("""\[(\d+(?:\s*,\s*\d+)*)]""").findAll(text)
            .flatMap { m -> m.groupValues[1].split(",").mapNotNull { it.trim().toIntOrNull() } }
            .distinct().toList()
        return nums.mapNotNull { n -> ranked.firstOrNull { it.number == n } }
    }

    fun groundedPrompt(question: String, passages: List<AskPassage>): String = buildString {
        appendLine("A student is asking about their own lecture material. Answer ONLY from the numbered passages below.")
        appendLine("Rules:")
        appendLine("- Cite every claim with the passage number in square brackets, like [2].")
        appendLine("- Never add facts that are not in the passages. Do not use outside knowledge.")
        appendLine("- If the passages answer part of the question, answer that part with citations, then say plainly which part the lecture doesn't cover. Keep inSource true.")
        appendLine("- Only if no passage helps at all, set inSource to false and give one sentence, such as: Your lecture doesn't cover this.")
        appendLine("- Speak to the student about \"your lecture\" or \"your notes\". Never mention passages, sources or these rules.")
        appendLine("- Write for a student: plain, short (under 140 words), no headings.")
        appendLine()
        passages.forEach { appendLine("[${it.number}] (${it.sourceTitle}) ${it.text.take(900)}") }
        appendLine()
        appendLine("Question: $question")
        appendLine("""Respond strictly with JSON: {"answer": "...", "inSource": true}""")
    }

    fun generalPrompt(question: String): String = buildString {
        appendLine("A student asked a question their lecture does not cover and asked for a general answer.")
        appendLine("Answer from general academic knowledge in under 120 words, plainly. If you are unsure, say so.")
        appendLine("Question: $question")
        appendLine("""Respond strictly with JSON: {"answer": "...", "inSource": false}""")
    }

    const val ANSWER_SCHEMA = """{"type":"object","properties":{"answer":{"type":"string"},"inSource":{"type":"boolean"}},"required":["answer","inSource"]}"""
}
