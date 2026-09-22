package com.example.ai.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptQualityEvaluatorTest {

    private fun word(index: Int, text: String, speaker: String?, attribution: AttributionConfidence) =
        CanonicalWord("w$index", text, index * 400L, (index + 1) * 400L, speaker, attribution)

    private fun transcript(
        words: List<CanonicalWord>,
        paragraphs: List<CanonicalParagraph>,
        turns: List<SpeakerTurn> = emptyList()
    ) = CanonicalTranscript(
        meetingId = "m1", words = words, speakerTurns = turns, utterances = emptyList(),
        paragraphs = paragraphs,
        metadata = TranscriptMetadata("m1", processingMode = "OFFLINE", transcriptionEngine = "test")
    )

    private fun paragraph(id: String, wordIds: List<String>, text: String) =
        CanonicalParagraph(id, "A", "Speaker 1", 0L, 1_000L, text, wordIds, listOf(id))

    @Test
    fun `an empty transcript reports zeroes and EMPTY rather than dividing by zero`() {
        val report = TranscriptQualityEvaluator.evaluate(transcript(emptyList(), emptyList()))

        assertEquals(0, report.wordCount)
        assertEquals(TranscriptQualityReport.Verdict.EMPTY, report.verdict)
    }

    @Test
    fun `a heavily fragmented transcript is flagged`() {
        // Twelve two-word paragraphs: exactly the defect this overhaul exists to remove.
        val words = (0..23).map { word(it, "w$it", "A", AttributionConfidence.HIGH) }
        val paragraphs = (0..11).map { paragraph("p$it", listOf("w${it * 2}", "w${it * 2 + 1}"), "two words") }

        val report = TranscriptQualityEvaluator.evaluate(transcript(words, paragraphs))

        assertEquals(1.0, report.fragmentationRate, 0.001)
        assertEquals(TranscriptQualityReport.Verdict.SUSPECT, report.verdict)
    }

    @Test
    fun `a well-formed transcript reads as SOUND`() {
        val words = (0..59).map { word(it, "w$it", "A", AttributionConfidence.HIGH) }
        val paragraphs = (0..2).map { p ->
            paragraph("p$p", (0..19).map { "w${p * 20 + it}" }, "A complete sentence about the project.")
        }
        val turns = listOf(SpeakerTurn("t0", "A", 0L, 24_000L, words.map { it.id }, 1f))

        val report = TranscriptQualityEvaluator.evaluate(transcript(words, paragraphs, turns))

        assertEquals(20.0, report.meanParagraphWords, 0.001)
        assertEquals(0.0, report.fragmentationRate, 0.001)
        assertEquals(1.0, report.highConfidenceAttributionRate, 0.001)
        assertEquals(TranscriptQualityReport.Verdict.SOUND, report.verdict)
    }

    @Test
    fun `unattributed words are counted honestly`() {
        val words = listOf(
            word(0, "a", "A", AttributionConfidence.HIGH),
            word(1, "b", null, AttributionConfidence.NONE),
            word(2, "c", null, AttributionConfidence.NONE),
            word(3, "d", "A", AttributionConfidence.LOW)
        )

        val report = TranscriptQualityEvaluator.evaluate(transcript(words, emptyList()))

        assertEquals(0.5, report.unattributedWordRate, 0.001)
        assertEquals(0.25, report.highConfidenceAttributionRate, 0.001)
    }

    @Test
    fun `an unreconciled overlap shows up as a duplicate phrase`() {
        val repeated = "we still need to we still need to".split(" ")
            .mapIndexed { i, text -> word(i, text, "A", AttributionConfidence.HIGH) }

        assertEquals(1, TranscriptQualityEvaluator.countDuplicatePhrases(repeated))
    }

    @Test
    fun `a phrase recurring later in the meeting is not a duplicate`() {
        val words = "we still need to ship the launch and we still need to"
            .split(" ").mapIndexed { i, text -> word(i, text, "A", AttributionConfidence.HIGH) }

        assertEquals(0, TranscriptQualityEvaluator.countDuplicatePhrases(words))
    }

    @Test
    fun `non-monotonic and negative-length timings are counted as anomalies`() {
        val words = listOf(
            CanonicalWord("w0", "a", 0L, 500L),
            CanonicalWord("w1", "b", 400L, 300L),
            CanonicalWord("w2", "c", 10L, 200L)
        )

        assertTrue(TranscriptQualityEvaluator.countTimestampAnomalies(words) >= 2)
    }

    @Test
    fun `the log line carries metrics and never transcript text`() {
        val words = listOf(word(0, "confidential-project-name", "A", AttributionConfidence.HIGH))
        val paragraphs = listOf(paragraph("p0", listOf("w0"), "confidential-project-name"))

        val line = TranscriptQualityEvaluator.evaluate(transcript(words, paragraphs)).toLogLine()

        assertTrue(line.contains("words=1"))
        assertTrue("transcript text must never reach logs", !line.contains("confidential"))
    }
}
