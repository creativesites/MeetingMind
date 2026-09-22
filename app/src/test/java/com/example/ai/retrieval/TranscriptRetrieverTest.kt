package com.example.ai.retrieval

import com.example.ai.embeddings.EmbeddingEngine
import com.example.core.model.Speaker
import com.example.core.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptRetrieverTest {

    /**
     * A deliberately flat embedding engine: every pair scores the same. It isolates the signals
     * this class adds — term overlap, speaker and temporal filtering, neighbour context — from the
     * semantic similarity that was already there, so a passing test cannot be explained by the
     * embedding happening to rank things well.
     */
    private val flatEmbeddings = object : EmbeddingEngine {
        override suspend fun embed(text: String) = FloatArray(4) { 1f }
        override fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray) = 0.5f
    }

    private fun segment(
        id: String,
        text: String,
        startMs: Long,
        speakerId: String? = "spk_0",
        speakerName: String? = "Winston"
    ) = TranscriptSegment(
        id = id, meetingId = "m1", speakerId = speakerId, speakerName = speakerName,
        startMs = startMs, endMs = startMs + 5_000L, text = text
    )

    private fun filler(count: Int, fromMs: Long = 100_000L) = (0 until count).map {
        segment("filler$it", "General discussion about the project and the team.", fromMs + it * 6_000L)
    }

    @Test
    fun `the passage containing a rare term outranks generically similar ones`() = runBlocking {
        val target = segment("target", "We decided to keep Parakeet for on-device transcription.", 50_000L)
        val segments = filler(20) + target
        val retriever = TranscriptRetriever(flatEmbeddings, topK = 3, includeNeighbours = false)

        val results = retriever.retrieve(segments, "What did we decide about Parakeet?")

        assertTrue("the only passage mentioning Parakeet must be retrieved",
            results.any { it.segment.id == "target" })
        assertTrue(results.first { it.segment.id == "target" }.keywordScore > 0f)
    }

    @Test
    fun `a question naming a participant is treated as a filter on that person's turns`() = runBlocking {
        val sarah = Speaker("spk_1", "m1", 1, "Speaker 2", "Sarah", "#000000")
        val winston = Speaker("spk_0", "m1", 0, "Speaker 1", "Winston", "#111111")
        val segments = filler(15) + listOf(
            segment("sarah1", "I will own the migration and report back on Tuesday.", 80_000L, "spk_1", "Sarah"),
            segment("winston1", "I will own the migration and report back on Tuesday.", 90_000L, "spk_0", "Winston")
        )
        val retriever = TranscriptRetriever(flatEmbeddings, topK = 5, includeNeighbours = false)

        val results = retriever.retrieve(segments, "What did Sarah commit to?", listOf(sarah, winston))

        assertTrue("every retrieved passage must be Sarah's", results.all { it.segment.speakerId == "spk_1" })
    }

    @Test
    fun `naming a speaker who never speaks falls back to the whole transcript, not to nothing`() = runBlocking {
        // Returning no passages reads to the user as "the meeting doesn't mention it", which is a
        // different and wrong answer.
        val ghost = Speaker("spk_9", "m1", 9, "Speaker 10", "Priya", "#222222")
        val segments = filler(15)
        val retriever = TranscriptRetriever(flatEmbeddings, topK = 5, includeNeighbours = false)

        val results = retriever.retrieve(segments, "What did Priya say about the budget?", listOf(ghost))

        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `a question about the end of the meeting restricts to the end of the meeting`() = runBlocking {
        val segments = (0 until 20).map { segment("s$it", "Point number $it about the plan.", it * 10_000L) }
        val retriever = TranscriptRetriever(flatEmbeddings, topK = 5, includeNeighbours = false)

        val results = retriever.retrieve(segments, "What did we agree at the end?")

        val lastStart = segments.maxOf { it.endMs }
        assertTrue("retrieved passages must come from the closing quarter",
            results.all { it.segment.endMs >= lastStart * 0.75 })
    }

    @Test
    fun `a retrieved passage brings its immediate neighbours along as context`() = runBlocking {
        val segments = (0 until 20).map {
            segment("s$it", if (it == 10) "We chose Parakeet." else "Unrelated point $it.", it * 10_000L)
        }
        val retriever = TranscriptRetriever(flatEmbeddings, topK = 1, includeNeighbours = true)

        val results = retriever.retrieve(segments, "Why Parakeet?")

        val ids = results.map { it.segment.id }
        assertTrue(ids.containsAll(listOf("s9", "s10", "s11")))
    }

    @Test
    fun `results are returned in chronological order`() = runBlocking {
        val segments = (0 until 30).map { segment("s$it", "Point $it about the roadmap.", it * 10_000L) }
        val retriever = TranscriptRetriever(flatEmbeddings, topK = 6)

        val results = retriever.retrieve(segments, "roadmap")

        assertEquals(results.sortedBy { it.segment.startMs }, results)
    }

    @Test
    fun `a transcript that already fits is returned whole rather than ranked`() = runBlocking {
        val segments = (0 until 4).map { segment("s$it", "Point $it.", it * 10_000L) }
        val retriever = TranscriptRetriever(flatEmbeddings, topK = 12)

        val results = retriever.retrieve(segments, "?")

        assertEquals(4, results.size)
    }

    @Test
    fun `an empty transcript retrieves nothing rather than throwing`() = runBlocking {
        assertTrue(TranscriptRetriever(flatEmbeddings).retrieve(emptyList(), "anything").isEmpty())
    }

    @Test
    fun `stopwords carry no keyword signal`() {
        val retriever = TranscriptRetriever(flatEmbeddings)
        val keywords = QueryAnalyzer.contentWords("What did they say about the deployment?")

        assertTrue("deployment" in keywords)
        assertTrue(keywords.none { it in setOf("what", "did", "they", "say", "about", "the") })
        assertEquals(1f, retriever.keywordOverlap(setOf("deployment"), "The deployment is Friday."), 0.001f)
        assertEquals(0f, retriever.keywordOverlap(setOf("deployment"), "Nothing relevant here."), 0.001f)
    }

    @Test
    fun `query analysis never invents a speaker filter from an ordinary capitalised word`() {
        val speakers = listOf(Speaker("spk_0", "m1", 0, "Speaker 1", "Winston", "#000000"))

        val intent = QueryAnalyzer.analyze("What did we decide about Friday?", speakers, emptyList())

        assertTrue(intent.speakerIds.isEmpty())
    }
}
