package com.example.ai.cloud

import com.example.ai.transcript.AttributionConfidence
import com.example.ai.transcript.CanonicalWord
import com.example.ai.transcript.TranscriptSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fusion's contract in one line: **the smart pass contributes surface forms and nothing else.**
 * Every timestamp and every speaker in the output came from the verbatim pass, which actually
 * heard the audio — no model is ever asked to produce either.
 */
class TranscriptFusionEngineTest {

    private fun verbatim(sentence: String, speaker: String = "A", startMs: Long = 0L): List<CanonicalWord> =
        sentence.split(" ").mapIndexed { i, text ->
            CanonicalWord(
                id = "v$i",
                text = text,
                startMs = startMs + i * 400L,
                endMs = startMs + (i + 1) * 400L,
                speakerId = speaker,
                attribution = AttributionConfidence.HIGH,
                source = TranscriptSource.GEMINI_VERBATIM
            )
        }

    private fun textOf(words: List<CanonicalWord>) = words.joinToString(" ") { it.text }

    @Test
    fun `the smart pass supplies punctuation and casing while timing comes from verbatim`() {
        val words = verbatim("so i think we should ship on friday")
        val smart = "So I think we should ship on Friday."

        val fused = TranscriptFusionEngine.fuse(words, smart)

        assertEquals("So I think we should ship on Friday.", textOf(fused.words))
        assertEquals(words.first().startMs, fused.words.first().startMs)
        assertEquals(words.last().endMs, fused.words.last().endMs)
    }

    @Test
    fun `filler words the smart pass removed simply do not appear`() {
        val words = verbatim("so um i think we uh should ship")
        val smart = "So I think we should ship."

        val fused = TranscriptFusionEngine.fuse(words, smart)

        assertTrue(fused.words.none { it.text.lowercase().trim('.', ',') in setOf("um", "uh") })
        assertEquals("So I think we should ship.", textOf(fused.words))
    }

    @Test
    fun `every fused word keeps the speaker the verbatim pass attributed it to`() {
        val first = verbatim("we ship on friday", speaker = "global_speaker_0", startMs = 0L)
        val second = verbatim("sounds good to me", speaker = "global_speaker_1", startMs = 4_000L)
            .mapIndexed { i, w -> w.copy(id = "v2$i") }

        val fused = TranscriptFusionEngine.fuse(first + second, "We ship on Friday. Sounds good to me.")

        assertEquals(
            listOf("global_speaker_0", "global_speaker_0", "global_speaker_0", "global_speaker_0",
                "global_speaker_1", "global_speaker_1", "global_speaker_1", "global_speaker_1"),
            fused.words.map { it.speakerId }
        )
    }

    @Test
    fun `a smart word matching nothing is dropped rather than given invented timing`() {
        val words = verbatim("we ship friday")
        val smart = "We ship Friday, obviously."

        val fused = TranscriptFusionEngine.fuse(words, smart)

        assertTrue(
            "a word with no anchor has no honest timestamp and must not be inserted",
            fused.words.none { it.text.contains("obviously") }
        )
        assertTrue(fused.words.all { anchored -> words.any { it.startMs == anchored.startMs } })
    }

    @Test
    fun `fused words stay in chronological order`() {
        val words = verbatim("one two three four five six seven eight")
        val smart = "One two three four five six seven eight."

        val fused = TranscriptFusionEngine.fuse(words, smart)

        assertEquals(fused.words.sortedBy { it.startMs }, fused.words)
        assertEquals(listOf("w0", "w1", "w2", "w3", "w4", "w5", "w6", "w7"), fused.words.map { it.id })
    }

    @Test
    fun `a smart pass that disagrees too broadly is rejected and verbatim is kept intact`() {
        val words = verbatim("alpha bravo charlie delta echo foxtrot")
        val smart = "Completely different sentence about unrelated matters entirely."

        val fused = TranscriptFusionEngine.fuse(words, smart)

        assertTrue(fused.alignmentCoverage < TranscriptFusionEngine.MIN_USABLE_COVERAGE)
        assertEquals("the fidelity layer must survive a failed readability layer", words, fused.words)
    }

    @Test
    fun `an empty smart transcript leaves verbatim untouched`() {
        val words = verbatim("we ship friday")

        assertEquals(words, TranscriptFusionEngine.fuse(words, "").words)
        assertEquals(words, TranscriptFusionEngine.fuse(words, "   ").words)
    }

    @Test
    fun `an empty verbatim transcript fuses to nothing`() {
        assertTrue(TranscriptFusionEngine.fuse(emptyList(), "Anything at all.").words.isEmpty())
    }

    @Test
    fun `alignment never crosses itself, so a repeated phrase cannot reorder the transcript`() {
        val verbatimKeys = listOf("we", "need", "to", "ship", "we", "need", "to", "test")
        val smartKeys = listOf("we", "need", "to", "ship", "we", "need", "to", "test")

        val alignment = TranscriptFusionEngine.align(verbatimKeys, smartKeys)

        val matched = alignment.filter { it >= 0 }
        assertEquals("alignment must be strictly increasing", matched.sorted(), matched)
        assertEquals(8, matched.size)
    }

    @Test
    fun `fused words are marked as coming from the smart pass`() {
        val fused = TranscriptFusionEngine.fuse(verbatim("we ship friday"), "We ship Friday.")

        assertTrue(fused.words.all { it.source == TranscriptSource.GEMINI_SMART })
    }
}
