package com.example.ai.retrieval

import com.example.ai.embeddings.EmbeddingEngine
import com.example.core.model.Speaker
import com.example.core.model.TranscriptSegment

/**
 * What a question is actually asking for, beyond its words.
 *
 * Derived deterministically from the question text and the meeting's own speaker list — never by
 * asking a model, because a model that misreads "what did Winston say" as a filter on the wrong
 * person silently hides the answer, and there is no way for the user to tell that happened.
 */
data class QueryIntent(
    val question: String,
    /** Speaker ids the question is explicitly about, e.g. "what did Sarah commit to". */
    val speakerIds: Set<String> = emptySet(),
    /** Time window the question restricts to, in ms, or null for the whole meeting. */
    val timeRangeMs: LongRange? = null,
    /** Content words from the question, used for the keyword half of retrieval. */
    val keywords: Set<String> = emptySet()
)

/** One retrieved passage and why it was retrieved. */
data class RetrievedPassage(
    val segment: TranscriptSegment,
    val semanticScore: Float,
    val keywordScore: Float,
    val finalScore: Float
)

/**
 * Hybrid retrieval over a meeting transcript.
 *
 * The previous implementation embedded the question, embedded every segment, and took the top K by
 * cosine similarity. That is a reasonable floor and it is kept — as one of several signals. Its
 * weaknesses are specific and fixable:
 *
 * - **Rare words are what questions are usually about.** "What did we decide about Parakeet?" is
 *   answered by the one paragraph containing "Parakeet", which a dense vector may rank below three
 *   paragraphs that are generally about models. Exact term overlap is added alongside.
 * - **"What did Sarah say" is a filter, not a topic.** A question naming a participant is asking
 *   about their turns specifically; ranking her paragraphs against everyone else's by similarity
 *   throws that constraint away.
 * - **"At the end we agreed…" is a filter too.** So is "at the start".
 * - **Adjacent context is usually part of the answer.** A decision's justification is generally in
 *   the paragraph before or after it.
 *
 * Everything here is deterministic and testable without a model; the language model is only ever
 * given the passages this produces, and is told to answer from them alone.
 */
class TranscriptRetriever(
    private val embeddingEngine: EmbeddingEngine,
    private val topK: Int = 12,
    /** Relative weight of exact term overlap against semantic similarity. */
    private val keywordWeight: Float = 0.4f,
    /** Whether a retrieved passage pulls in its immediate neighbours for context. */
    private val includeNeighbours: Boolean = true
) {

    suspend fun retrieve(
        segments: List<TranscriptSegment>,
        question: String,
        speakers: List<Speaker> = emptyList()
    ): List<RetrievedPassage> {
        if (segments.isEmpty()) return emptyList()
        val intent = QueryAnalyzer.analyze(question, speakers, segments)

        // Filters are applied before ranking, and only when they leave something behind: a
        // question that names a speaker who never actually speaks must fall back to the whole
        // transcript rather than returning nothing, since "no passages" reads to the user as "the
        // meeting doesn't mention it".
        val filtered = segments
            .filterByIntent(intent)
            .ifEmpty { segments }

        if (filtered.size <= topK && intent.keywords.isEmpty()) {
            return filtered.map { RetrievedPassage(it, 0f, 0f, 0f) }
        }

        val queryVector = embeddingEngine.embed(question)
        val scored = filtered.map { segment ->
            val text = segment.cleanedText ?: segment.text
            val semantic = embeddingEngine.cosineSimilarity(queryVector, embeddingEngine.embed(text))
            val keyword = keywordOverlap(intent.keywords, text)
            RetrievedPassage(
                segment = segment,
                semanticScore = semantic,
                keywordScore = keyword,
                finalScore = semantic * (1f - keywordWeight) + keyword * keywordWeight
            )
        }

        val top = scored.sortedByDescending { it.finalScore }.take(topK)
        val withContext = if (includeNeighbours) expandWithNeighbours(top, filtered) else top
        // Chronological, because an answer assembled from passages the model reads out of order
        // reads as if the meeting happened out of order.
        return withContext.sortedBy { it.segment.startMs }
    }

    private fun List<TranscriptSegment>.filterByIntent(intent: QueryIntent): List<TranscriptSegment> =
        filter { segment ->
            val speakerOk = intent.speakerIds.isEmpty() || segment.speakerId in intent.speakerIds
            val timeOk = intent.timeRangeMs == null ||
                (segment.endMs >= intent.timeRangeMs.first && segment.startMs <= intent.timeRangeMs.last)
            speakerOk && timeOk
        }

    /** Share of the question's content words that appear in this passage, 0f..1f. */
    internal fun keywordOverlap(keywords: Set<String>, text: String): Float {
        if (keywords.isEmpty()) return 0f
        val words = QueryAnalyzer.contentWords(text)
        val hits = keywords.count { it in words }
        return hits.toFloat() / keywords.size
    }

    /**
     * Adds the paragraph on each side of a retrieved one, scored at zero so it can never outrank a
     * genuine match — it is there as context, not as an answer.
     */
    private fun expandWithNeighbours(
        top: List<RetrievedPassage>,
        pool: List<TranscriptSegment>
    ): List<RetrievedPassage> {
        val ordered = pool.sortedBy { it.startMs }
        val indexById = ordered.withIndex().associate { (index, segment) -> segment.id to index }
        val chosen = LinkedHashMap<String, RetrievedPassage>()
        for (passage in top) chosen[passage.segment.id] = passage
        for (passage in top) {
            val index = indexById[passage.segment.id] ?: continue
            for (neighbourIndex in listOf(index - 1, index + 1)) {
                val neighbour = ordered.getOrNull(neighbourIndex) ?: continue
                chosen.putIfAbsent(neighbour.id, RetrievedPassage(neighbour, 0f, 0f, 0f))
            }
        }
        return chosen.values.toList()
    }
}

/** Pulls structure out of a question deterministically. */
object QueryAnalyzer {

    /** A question about "the start"/"the end" restricts to this share of the meeting. */
    private const val EDGE_WINDOW_SHARE = 0.25

    fun analyze(question: String, speakers: List<Speaker>, segments: List<TranscriptSegment>): QueryIntent {
        val lower = question.lowercase()
        val totalMs = segments.maxOfOrNull { it.endMs } ?: 0L
        val window = (totalMs * EDGE_WINDOW_SHARE).toLong()

        return QueryIntent(
            question = question,
            // Only a name the meeting actually has: matching on an arbitrary capitalised word
            // would filter a question about "Friday" down to nothing.
            speakerIds = speakers
                .filter { speaker ->
                    val name = speaker.customName.lowercase()
                    name.length >= MIN_NAME_LENGTH && name in lower
                }
                .map { it.id }
                .toSet(),
            timeRangeMs = when {
                totalMs <= 0L -> null
                START_CUES.any { it in lower } -> 0L..window
                END_CUES.any { it in lower } -> (totalMs - window)..totalMs
                else -> null
            },
            keywords = contentWords(question)
        )
    }

    /** Lowercased words worth matching on: stopwords and very short tokens carry no signal. */
    fun contentWords(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}']+"))
            .filter { it.length >= MIN_KEYWORD_LENGTH && it !in STOPWORDS }
            .toSet()

    private const val MIN_KEYWORD_LENGTH = 3
    private const val MIN_NAME_LENGTH = 3

    private val START_CUES = setOf("at the start", "at the beginning", "early on", "opened", "kicked off")
    private val END_CUES = setOf("at the end", "finally", "wrapped up", "closing", "last part")

    private val STOPWORDS = setOf(
        "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "had", "her", "was",
        "one", "our", "out", "day", "get", "has", "him", "his", "how", "its", "new", "now", "old",
        "see", "two", "way", "who", "did", "say", "said", "what", "when", "where", "which", "that",
        "this", "with", "from", "they", "them", "then", "there", "their", "have", "were", "about",
        "would", "could", "should", "does", "into", "than", "very", "just", "also", "been", "over",
        "much", "more", "most", "some", "such", "only", "same", "each", "tell", "give", "show", "was"
    )
}
