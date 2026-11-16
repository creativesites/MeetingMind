package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ask Learning answers only from the student's sources, with citations (design spec §9, D10). */
class LearningAskTest {

    private fun seg(id: String, startS: Long, text: String, conf: Float? = 0.9f) =
        TranscriptSegment(id = id, meetingId = "m1", startMs = startS * 1000, endMs = startS * 1000 + 8000, text = text, confidence = conf)

    private val lecture = listOf(
        seg("s1", 0, "Today we look at enzyme kinetics and the Michaelis Menten equation."),
        seg("s2", 20, "Vmax is the maximum reaction rate when the enzyme is saturated."),
        seg("s3", 1452, "A competitive inhibitor binds the active site, so the apparent Km increases while Vmax stays the same."),
        seg("s4", 1460, "You can outcompete it with more substrate.", conf = 0.4f)
    )

    private fun passages() = LearningAsk.transcriptPassages(lecture, "n1", "m1", "Lecture 07")

    @Test
    fun `transcript is chunked with segment ids and timing kept`() {
        val p = passages()
        assertEquals(2, p.size)
        assertEquals(listOf("s3", "s4"), p[1].evidence.segmentIds)
        assertEquals(1_452_000L, p[1].evidence.startMs)
        assertEquals(0.4f, p[1].confidence)
    }

    @Test
    fun `the clip a citation plays ends where its quote ends`() {
        val long = "x".repeat(200)
        val p = LearningAsk.transcriptPassages(
            listOf(seg("a", 0, long), seg("b", 10, "This sentence pushes past the quote length limit for sure."), seg("c", 20, "And this one too.")),
            "n1", "m1", "Lecture"
        ).single()
        assertEquals(long, p.evidence.quote)
        assertEquals(8_000L, p.evidence.endMs)
        assertTrue(p.text.contains("And this one too."))
    }

    @Test
    fun `ranking finds the passage about the question and numbers from one`() {
        val ranked = LearningAsk.rank("What does a competitive inhibitor do to Km?", passages())
        assertEquals(1, ranked.first().first.number)
        assertTrue(ranked.first().first.text.contains("competitive inhibitor"))
    }

    @Test
    fun `offline answer quotes and cites the passage, and flags unclear audio`() {
        val q = "What does a competitive inhibitor do to Km?"
        val a = LearningAsk.extractiveAnswer(q, LearningAsk.rank(q, passages()))
        assertEquals(AnswerKind.FROM_SOURCE, a.kind)
        assertTrue(a.text.contains("[1]"))
        assertTrue(a.unclearSource)
        assertTrue(a.extractive)
    }

    @Test
    fun `a question the lecture does not cover is said plainly`() {
        val q = "Who won the 1998 football world cup final?"
        val a = LearningAsk.extractiveAnswer(q, LearningAsk.rank(q, passages()))
        assertEquals(AnswerKind.NOT_IN_SOURCE, a.kind)
        assertTrue(a.cited.isEmpty())
    }

    @Test
    fun `citations in a model answer resolve only to real passages`() {
        val ranked = LearningAsk.rank("inhibitor Km Vmax", passages()).map { it.first }
        val cited = LearningAsk.citedIn("Km rises [1] and Vmax holds [1, 2]. Also [9].", ranked)
        assertEquals(ranked.map { it.number }.filter { it in setOf(1, 2) }, cited.map { it.number })
    }

    @Test
    fun `note passages split typed notes into paragraphs`() {
        val p = LearningAsk.notePassages("First paragraph about enzymes and rates.\n\nSecond paragraph about inhibitors and Km.\n\nok", "n1", "Notes")
        assertEquals(2, p.size)
        assertEquals("n1", p.first().evidence.noteId)
    }

    @Test
    fun `with no model the engine still answers from the passages`() = runBlocking {
        val q = "What does a competitive inhibitor do to Km?"
        val a = LearningAiEngine().answerFromSources(q, LearningAsk.rank(q, passages()))
        assertEquals(AnswerKind.FROM_SOURCE, a.kind)
        assertFalse(a.cited.isEmpty())
        assertNotNull(a.cited.first().evidence.startMs)
    }

    @Test
    fun `general knowledge is unavailable without a model rather than invented`() = runBlocking {
        assertEquals(null, LearningAiEngine().answerGenerally("What is ATP?"))
        assertTrue(LearningEvidence().quote.isEmpty())
    }
}
