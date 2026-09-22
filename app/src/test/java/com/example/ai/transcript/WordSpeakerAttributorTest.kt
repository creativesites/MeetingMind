package com.example.ai.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Speaker attribution is where "one speaker split into several labels" and "speaker identities
 * flip" were actually created. The governing rule these tests encode: **a wrong speaker
 * attribution is worse than an explicitly uncertain one.**
 */
class WordSpeakerAttributorTest {

    private fun word(id: String, text: String, startMs: Long, endMs: Long) =
        CanonicalWord(id = id, text = text, startMs = startMs, endMs = endMs)

    @Test
    fun `a word well inside one turn is attributed with HIGH confidence`() {
        val words = listOf(word("w0", "hello", 1_000L, 1_400L))
        val turns = listOf(DiarizationTurn("A", 0L, 5_000L))

        val result = WordSpeakerAttributor.attribute(words, turns)

        assertEquals("A", result[0].speakerId)
        assertEquals(AttributionConfidence.HIGH, result[0].attribution)
    }

    @Test
    fun `a word straddling a speaker change is attributed but marked MEDIUM, not HIGH`() {
        // The old pipeline gave a whole segment to whoever held the most milliseconds and said
        // nothing about how close the call was. Here the word still gets a speaker — refusing to
        // attribute it would be its own kind of damage — but the transcript records the doubt.
        val words = listOf(word("w0", "right", 4_800L, 5_200L))
        val turns = listOf(
            DiarizationTurn("A", 0L, 5_000L),
            DiarizationTurn("B", 5_000L, 9_000L)
        )

        val result = WordSpeakerAttributor.attribute(words, turns)

        assertTrue(result[0].speakerId == "A" || result[0].speakerId == "B")
        assertEquals(AttributionConfidence.MEDIUM, result[0].attribution)
    }

    @Test
    fun `words on each side of a speaker change keep their own speakers`() {
        // This is the whole point of attributing per word: under segment-level majority overlap,
        // every one of these six words would have gone to a single speaker.
        val words = listOf(
            word("w0", "I", 0L, 400L),
            word("w1", "agree", 400L, 900L),
            word("w2", "completely", 900L, 1_900L),
            word("w3", "Well", 2_100L, 2_500L),
            word("w4", "I", 2_500L, 2_700L),
            word("w5", "don't", 2_700L, 3_200L)
        )
        val turns = listOf(
            DiarizationTurn("A", 0L, 2_000L),
            DiarizationTurn("B", 2_000L, 4_000L)
        )

        val result = WordSpeakerAttributor.attribute(words, turns)

        assertEquals(listOf("A", "A", "A", "B", "B", "B"), result.map { it.speakerId })
    }

    @Test
    fun `no diarization evidence leaves every word honestly unattributed`() {
        val words = listOf(word("w0", "hello", 0L, 500L), word("w1", "there", 500L, 1_000L))

        val result = WordSpeakerAttributor.attribute(words, emptyList())

        assertTrue(result.all { it.speakerId == null })
        assertTrue(result.all { it.attribution == AttributionConfidence.NONE })
    }

    @Test
    fun `a word far from every turn gets no speaker rather than the nearest guess`() {
        val words = listOf(word("w0", "later", 60_000L, 60_500L))
        val turns = listOf(DiarizationTurn("A", 0L, 5_000L))

        val result = WordSpeakerAttributor.attribute(words, turns)

        assertNull(result[0].speakerId)
        assertEquals(AttributionConfidence.NONE, result[0].attribution)
    }

    @Test
    fun `a word just outside a turn is attached to it as LOW`() {
        val words = listOf(word("w0", "yes", 5_100L, 5_400L))
        val turns = listOf(DiarizationTurn("A", 0L, 5_000L))

        val result = WordSpeakerAttributor.attribute(words, turns)

        assertEquals("A", result[0].speakerId)
        assertEquals(AttributionConfidence.LOW, result[0].attribution)
    }

    @Test
    fun `boundary jitter inside one speaker's run is smoothed back to that speaker as MEDIUM`() {
        // Problem B: one real speaker shredded into several labels by a momentary wobble in the
        // embedding model at a turn edge.
        val words = listOf(
            CanonicalWord("w0", "we", 0L, 400L, speakerId = "A", attribution = AttributionConfidence.HIGH),
            CanonicalWord("w1", "should", 400L, 800L, speakerId = "B", attribution = AttributionConfidence.LOW),
            CanonicalWord("w2", "ship", 800L, 1_200L, speakerId = "A", attribution = AttributionConfidence.HIGH)
        )

        val result = WordSpeakerAttributor.smoothIsolatedUncertainWords(words)

        assertEquals(listOf("A", "A", "A"), result.map { it.speakerId })
        assertEquals(
            "the smoothed word must not claim to be as certain as its neighbours",
            AttributionConfidence.MEDIUM,
            result[1].attribution
        )
    }

    @Test
    fun `a confidently attributed one-word backchannel is never smoothed away`() {
        // Problem D in its real form: "Yeah." from another speaker looks exactly like jitter, and
        // absorbing it is how short responses used to disappear into their neighbour's paragraph.
        val words = listOf(
            CanonicalWord("w0", "ready", 0L, 500L, speakerId = "A", attribution = AttributionConfidence.HIGH),
            CanonicalWord("w1", "Yeah", 520L, 900L, speakerId = "B", attribution = AttributionConfidence.HIGH),
            CanonicalWord("w2", "then", 950L, 1_400L, speakerId = "A", attribution = AttributionConfidence.HIGH)
        )

        val result = WordSpeakerAttributor.smoothIsolatedUncertainWords(words)

        assertEquals(listOf("A", "B", "A"), result.map { it.speakerId })
    }

    @Test
    fun `a long uncertain run is left alone rather than reassigned wholesale`() {
        val words = (0..5).map {
            CanonicalWord("w$it", "word$it", it * 400L, (it + 1) * 400L, speakerId = "B", attribution = AttributionConfidence.LOW)
        }
        val framed = listOf(
            CanonicalWord("start", "before", -400L, 0L, speakerId = "A", attribution = AttributionConfidence.HIGH)
        ) + words + listOf(
            CanonicalWord("end", "after", 2_400L, 2_800L, speakerId = "A", attribution = AttributionConfidence.HIGH)
        )

        val result = WordSpeakerAttributor.smoothIsolatedUncertainWords(framed)

        assertTrue(
            "six seconds of speech is a real turn, however uncertain its attribution",
            result.filter { it.id.startsWith("w") }.all { it.speakerId == "B" }
        )
    }

    @Test
    fun `attribution is deterministic for the same input`() {
        val words = listOf(word("w0", "hello", 0L, 500L), word("w1", "there", 2_400L, 2_900L))
        val turns = listOf(DiarizationTurn("A", 0L, 2_000L), DiarizationTurn("B", 2_000L, 4_000L))

        assertEquals(
            WordSpeakerAttributor.attribute(words, turns),
            WordSpeakerAttributor.attribute(words, turns)
        )
    }
}
