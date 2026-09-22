package com.example.ai.benchmark

import com.example.ai.transcript.AttributionConfidence
import com.example.ai.transcript.CanonicalParagraph
import com.example.ai.transcript.CanonicalTranscript
import com.example.ai.transcript.CanonicalWord
import com.example.ai.transcript.TranscriptMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Metrics tested against cases with known answers. A benchmark that is only correct when the
 * pipeline is correct measures nothing.
 */
class TranscriptBenchmarkTest {

    private fun reference(sentence: String, speaker: String? = "A") = ReferenceTranscript(
        recordingId = "r1",
        words = sentence.split(" ").mapIndexed { i, text ->
            ReferenceWord(text, i * 400L, (i + 1) * 400L, speaker)
        }
    )

    private fun hypothesis(
        sentence: String,
        speakerPerWord: List<String?>? = null,
        startOffsetMs: Long = 0L
    ): CanonicalTranscript {
        val words = sentence.split(" ").mapIndexed { i, text ->
            CanonicalWord(
                id = "w$i", text = text,
                startMs = startOffsetMs + i * 400L, endMs = startOffsetMs + (i + 1) * 400L,
                speakerId = speakerPerWord?.getOrNull(i) ?: "H0",
                attribution = AttributionConfidence.HIGH
            )
        }
        return CanonicalTranscript(
            meetingId = "m1", words = words, speakerTurns = emptyList(), utterances = emptyList(),
            paragraphs = listOf(
                CanonicalParagraph("p0", "H0", "Speaker 1", 0L, 10_000L, sentence, words.map { it.id }, listOf("u0"))
            ),
            metadata = TranscriptMetadata("m1", processingMode = "OFFLINE", transcriptionEngine = "test")
        )
    }

    @Test
    fun `a perfect transcript scores zero error`() {
        val result = TranscriptBenchmark.score(
            reference("we should ship on friday"),
            hypothesis("we should ship on friday"),
            "test"
        )

        assertEquals(0.0, result.wer, 0.0001)
        assertEquals(0.0, result.cer, 0.0001)
        assertEquals(0.0, result.speakerAttributionError!!, 0.0001)
        assertEquals(0.0, result.timestampErrorMs!!, 0.0001)
    }

    @Test
    fun `one substitution in five words is a word error rate of one fifth`() {
        val result = TranscriptBenchmark.score(
            reference("we should ship on friday"),
            hypothesis("we should ship on monday"),
            "test"
        )

        assertEquals(0.2, result.wer, 0.0001)
    }

    @Test
    fun `deletions and insertions both count`() {
        val deletion = TranscriptBenchmark.score(
            reference("we should ship on friday"), hypothesis("we should ship friday"), "test"
        )
        val insertion = TranscriptBenchmark.score(
            reference("we should ship on friday"), hypothesis("we should really ship on friday"), "test"
        )

        assertEquals(0.2, deletion.wer, 0.0001)
        assertEquals(0.2, insertion.wer, 0.0001)
    }

    @Test
    fun `casing and punctuation are not counted as word errors`() {
        val result = TranscriptBenchmark.score(
            reference("we should ship on friday"),
            hypothesis("We should ship on Friday."),
            "test"
        )

        assertEquals(0.0, result.wer, 0.0001)
    }

    @Test
    fun `an empty hypothesis against a real reference is a total loss, not a division by zero`() {
        val result = TranscriptBenchmark.score(reference("we should ship"), hypothesis(""), "test")

        assertTrue(result.wer >= 1.0 - 0.0001)
        assertNull(result.timestampErrorMs)
    }

    @Test
    fun `one speaker split across two hypothesis speakers is counted as fragmentation`() {
        // Problem B: one real person becoming several labels.
        val result = TranscriptBenchmark.score(
            reference("we should ship on friday", speaker = "A"),
            hypothesis("we should ship on friday", speakerPerWord = listOf("H0", "H0", "H1", "H0", "H0")),
            "test"
        )

        assertEquals(1, result.speakerFragmentation)
        assertEquals(0, result.speakerConfusion)
        assertEquals("one of five words went to the wrong speaker", 0.2, result.speakerAttributionError!!, 0.0001)
    }

    @Test
    fun `two speakers merged into one is counted as confusion`() {
        val ref = ReferenceTranscript(
            "r1",
            listOf(
                ReferenceWord("we", 0L, 400L, "A"),
                ReferenceWord("ship", 400L, 800L, "A"),
                ReferenceWord("agreed", 800L, 1_200L, "B"),
                ReferenceWord("then", 1_200L, 1_600L, "B")
            )
        )

        val result = TranscriptBenchmark.score(
            ref, hypothesis("we ship agreed then", speakerPerWord = listOf("H0", "H0", "H0", "H0")), "test"
        )

        assertEquals(1, result.speakerConfusion)
        assertEquals(0, result.speakerFragmentation)
    }

    @Test
    fun `speaker labels being named differently is not an error`() {
        // The pipeline cannot be expected to guess the annotator's label names.
        val result = TranscriptBenchmark.score(
            reference("we should ship", speaker = "SpeakerAlice"),
            hypothesis("we should ship", speakerPerWord = listOf("spk_m1_0", "spk_m1_0", "spk_m1_0")),
            "test"
        )

        assertEquals(0.0, result.speakerAttributionError!!, 0.0001)
    }

    @Test
    fun `a reference with no speaker labels reports no speaker error rather than zero`() {
        // Zero would read as "perfect", which is a different claim from "not measured".
        val result = TranscriptBenchmark.score(
            reference("we should ship", speaker = null), hypothesis("we should ship"), "test"
        )

        assertNull(result.speakerAttributionError)
    }

    @Test
    fun `timestamp error is the mean absolute start offset over matched words`() {
        val result = TranscriptBenchmark.score(
            reference("we should ship"),
            hypothesis("we should ship", startOffsetMs = 250L),
            "test"
        )

        assertEquals(250.0, result.timestampErrorMs!!, 0.0001)
    }

    @Test
    fun `an unreconciled overlap shows up in the duplicate rate`() {
        val result = TranscriptBenchmark.score(
            reference("we still need to we still need to"),
            hypothesis("we still need to we still need to"),
            "test"
        )

        assertTrue(result.duplicateRatePer1000 > 0.0)
    }

    @Test
    fun `character error rate is finer-grained than word error rate`() {
        // "friday" vs "fridey" is one whole word wrong but only one character wrong.
        val result = TranscriptBenchmark.score(
            reference("we ship friday"), hypothesis("we ship fridey"), "test"
        )

        assertTrue(result.wer > result.cer)
        assertEquals(1.0 / 3.0, result.wer, 0.0001)
    }

    @Test
    fun `alignment reports one operation per reference and hypothesis position`() {
        val ops = TranscriptBenchmark.align(listOf("a", "b", "c"), listOf("a", "x", "c", "d"))

        assertEquals(2, ops.count { it is TranscriptBenchmark.EditOp.Match })
        assertEquals(1, ops.count { it is TranscriptBenchmark.EditOp.Substitute })
        assertEquals(1, ops.count { it is TranscriptBenchmark.EditOp.Insert })
        assertEquals(0, ops.count { it is TranscriptBenchmark.EditOp.Delete })
    }

    @Test
    fun `the log line carries metrics and never transcript text`() {
        val line = TranscriptBenchmark.score(
            reference("confidential-project-name"), hypothesis("confidential-project-name"), "local"
        ).toLogLine()

        assertTrue(line.startsWith("local/r1"))
        assertTrue("reference text must never reach a benchmark log", !line.contains("confidential"))
    }
}
