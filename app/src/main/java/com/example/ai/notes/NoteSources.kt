package com.example.ai.notes

import com.example.core.model.Note
import com.example.core.model.NoteBlock
import com.example.core.model.NoteBlockType
import com.example.core.model.TranscriptSegment
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Turns notes into [SourcePassage]s, and picks which ones fit in what a model can read.
 *
 * Pure functions, so what the model is shown — and what it isn't — can be tested exactly.
 */
object NoteSources {

    /** A note's text blocks as passages, each labelled with the note and the section it sits in. */
    fun passagesOf(note: Note, blocks: List<NoteBlock>): List<SourcePassage> {
        var section: String? = null
        return buildList {
            for (b in blocks) {
                if (b.type in HEADINGS) { section = b.content.text.trim().ifEmpty { null }; continue }
                if (!b.type.isText && b.type != NoteBlockType.SCRIPTURE) continue
                val text = b.content.text.trim()
                if (text.isEmpty()) continue
                val prefix = when (b.type) {
                    NoteBlockType.CHECKLIST -> if (b.checked) "[done] " else "[to do] "
                    NoteBlockType.SCRIPTURE -> "Scripture: "
                    else -> ""
                }
                add(SourcePassage(b.id, prefix + text, listOfNotNull(note.title.ifBlank { "Untitled" }, section).joinToString(" · "), note.id))
            }
        }
    }

    /** A recording's transcript paragraphs as passages. */
    fun transcriptPassagesOf(note: Note, segments: List<TranscriptSegment>): List<SourcePassage> =
        segments.mapNotNull { s ->
            val text = (s.cleanedText ?: s.text).trim()
            if (text.isEmpty()) null else SourcePassage(s.id, text, "${note.title.ifBlank { "Recording" }} · recording", note.id)
        }

    /** Passages in order until [budgetChars] is used; the rest are left out, and counted. */
    fun fit(passages: List<SourcePassage>, budgetChars: Int): Pair<List<SourcePassage>, Int> {
        var used = 0
        val kept = mutableListOf<SourcePassage>()
        for (p in passages) {
            val cost = p.text.length + p.label.length + 12
            if (used + cost > budgetChars) break
            kept += p; used += cost
        }
        return kept to (passages.size - kept.size)
    }

    /** Passages most relevant to [query] first (TF-IDF over words), then fitted to the budget in document order. */
    fun relevant(passages: List<SourcePassage>, query: String, budgetChars: Int): List<SourcePassage> {
        val q = words(query).toSet()
        if (q.isEmpty()) return fit(passages, budgetChars).first
        val docs = passages.map { words(it.text + " " + it.label) }
        val df = HashMap<String, Int>()
        docs.forEach { d -> d.toSet().forEach { df[it] = (df[it] ?: 0) + 1 } }
        val n = passages.size.toDouble()
        val scored = passages.indices.map { i ->
            val tf = docs[i].groupingBy { it }.eachCount()
            val score = q.sumOf { w -> (tf[w] ?: 0).let { c -> if (c == 0) 0.0 else (1 + ln(c.toDouble())) * ln(1 + n / (df[w] ?: 1)) } }
            i to score
        }.sortedByDescending { it.second }
        val chosen = mutableSetOf<Int>()
        var used = 0
        for ((i, score) in scored) {
            if (score <= 0.0 && chosen.size >= 3) break
            val cost = passages[i].text.length + passages[i].label.length + 12
            if (used + cost > budgetChars) continue
            chosen += i; used += cost
            // Neighbouring passages give an answer its context.
            listOf(i - 1, i + 1).filter { it in passages.indices && it !in chosen }.forEach { j ->
                val c = passages[j].text.length + passages[j].label.length + 12
                if (score > 0 && used + c <= budgetChars && passages[j].noteId == passages[i].noteId) { chosen += j; used += c }
            }
        }
        return chosen.sorted().map { passages[it] }
    }

    /** Roughly what fits: a token is about four characters; leave room for the contract and the answer. */
    fun budgetChars(contextTokens: Int, maxOutputTokens: Int): Int =
        ((contextTokens - maxOutputTokens - 700).coerceAtLeast(600) * 3.2).toInt()

    private val HEADINGS = setOf(NoteBlockType.HEADING_1, NoteBlockType.HEADING_2, NoteBlockType.HEADING_3)
    private val WORD = Regex("[\\p{L}\\p{N}]+")
    private val STOP = setOf(
        "the", "a", "an", "and", "or", "to", "of", "in", "on", "for", "with", "is", "are", "was", "were", "be", "that",
        "this", "it", "at", "by", "as", "from", "i", "we", "you", "my", "our", "your", "what", "who", "when", "how",
        "did", "do", "does", "about", "have", "has", "had", "not", "but", "so", "if", "they", "he", "she", "his", "her"
    )

    fun words(text: String): List<String> =
        WORD.findAll(text.lowercase()).map { it.value }.filter { it.length > 1 && it !in STOP }.map { stem(it) }.toList()

    /** A light stem so "prayer", "prayers" and "praying" meet. */
    private fun stem(w: String): String = when {
        w.length > 5 && w.endsWith("ing") -> w.dropLast(3)
        w.length > 4 && w.endsWith("es") -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }
}

/** One note that relates to another, and why — every reason is something the person can see. */
data class RelatedNote(val noteId: String, val score: Double, val reasons: List<String>)

/** What is known about a note for relating it to others. */
data class NoteSignals(
    val noteId: String,
    val text: String,
    val tags: Set<String>,
    /** "ROM 8" style keys, book and chapter. */
    val passages: Set<String>,
    val passageLabels: Map<String, String>,
    val linked: Set<String>
)

/**
 * Finds notes related to one note, with no model: shared scripture, shared tags, explicit links,
 * and similar wording (TF-IDF cosine). Deterministic, instant, works offline — and every match
 * says why, so it can be trusted or ignored.
 */
object RelatedNotes {

    fun find(target: NoteSignals, others: List<NoteSignals>, limit: Int = 8): List<RelatedNote> {
        val all = others.filter { it.noteId != target.noteId }
        if (all.isEmpty()) return emptyList()
        val docs = (all + target).associate { it.noteId to NoteSources.words(it.text) }
        val df = HashMap<String, Int>()
        docs.values.forEach { d -> d.toSet().forEach { df[it] = (df[it] ?: 0) + 1 } }
        val n = docs.size.toDouble()
        fun vector(words: List<String>): Map<String, Double> {
            val tf = words.groupingBy { it }.eachCount()
            return tf.mapValues { (w, c) -> (1 + ln(c.toDouble())) * ln(1 + n / (df[w] ?: 1)) }
        }
        val tv = vector(docs.getValue(target.noteId))
        val tNorm = sqrt(tv.values.sumOf { it * it })

        return all.mapNotNull { o ->
            val reasons = mutableListOf<String>()
            var score = 0.0
            if (o.noteId in target.linked || target.noteId in o.linked) { score += 1.0; reasons += "Linked" }
            val sharedPassages = target.passages intersect o.passages
            if (sharedPassages.isNotEmpty()) {
                score += 0.6 * sharedPassages.size.coerceAtMost(3)
                reasons += "Both cite " + sharedPassages.take(2).joinToString(", ") { target.passageLabels[it] ?: it }
            }
            val sharedTags = target.tags.map { it.lowercase() }.toSet() intersect o.tags.map { it.lowercase() }.toSet()
            if (sharedTags.isNotEmpty()) {
                score += 0.4 * sharedTags.size.coerceAtMost(3)
                reasons += sharedTags.take(3).joinToString(" ") { "#$it" }
            }
            val ov = vector(docs.getValue(o.noteId))
            val oNorm = sqrt(ov.values.sumOf { it * it })
            val cosine = if (tNorm == 0.0 || oNorm == 0.0) 0.0 else tv.entries.sumOf { (w, x) -> x * (ov[w] ?: 0.0) } / (tNorm * oNorm)
            if (cosine >= 0.12) {
                score += cosine
                val top = tv.keys.filter { it in ov }.sortedByDescending { (tv[it] ?: 0.0) * (ov[it] ?: 0.0) }.take(3)
                if (top.isNotEmpty()) reasons += "Similar words: " + top.joinToString(", ")
            }
            if (score < 0.2) null else RelatedNote(o.noteId, score, reasons)
        }.sortedByDescending { it.score }.take(limit)
    }
}
