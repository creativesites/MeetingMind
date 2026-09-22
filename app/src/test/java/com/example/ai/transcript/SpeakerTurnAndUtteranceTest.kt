package com.example.ai.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerTurnAndUtteranceTest {

    private fun w(
        index: Int,
        text: String,
        startMs: Long,
        endMs: Long,
        speaker: String?,
        confidence: AttributionConfidence = AttributionConfidence.HIGH
    ) = CanonicalWord("w$index", text, startMs, endMs, speakerId = speaker, attribution = confidence)

    // --- SpeakerTurnBuilder ---

    @Test
    fun `consecutive words from one speaker form a single turn`() {
        val words = (0..4).map { w(it, "word$it", it * 500L, (it + 1) * 500L, "A") }

        val turns = SpeakerTurnBuilder.build(words)

        assertEquals(1, turns.size)
        assertEquals("A", turns[0].speakerId)
        assertEquals(0L, turns[0].startMs)
        assertEquals(2_500L, turns[0].endMs)
        assertEquals(words.map { it.id }, turns[0].wordIds)
    }

    @Test
    fun `a speaker change starts a new turn`() {
        val words = listOf(
            w(0, "hello", 0L, 500L, "A"),
            w(1, "there", 500L, 1_000L, "A"),
            w(2, "hi", 1_000L, 1_500L, "B")
        )

        val turns = SpeakerTurnBuilder.build(words)

        assertEquals(listOf("A", "B"), turns.map { it.speakerId })
    }

    @Test
    fun `a word with no speaker extends the current turn without claiming to belong to anyone`() {
        val words = listOf(
            w(0, "hello", 0L, 500L, "A"),
            w(1, "mumble", 500L, 900L, null, AttributionConfidence.NONE),
            w(2, "there", 900L, 1_400L, "A")
        )

        val turns = SpeakerTurnBuilder.build(words)

        assertEquals("containment is not attribution", 1, turns.size)
        assertEquals(3, turns[0].wordIds.size)
        // The word itself still says it has no speaker — nothing downstream may read turn
        // membership as evidence of identity.
        assertEquals(null, words[1].speakerId)
    }

    @Test
    fun `turn confidence is the share of HIGH-attributed words`() {
        val words = listOf(
            w(0, "a", 0L, 500L, "A", AttributionConfidence.HIGH),
            w(1, "b", 500L, 1_000L, "A", AttributionConfidence.LOW),
            w(2, "c", 1_000L, 1_500L, "A", AttributionConfidence.HIGH),
            w(3, "d", 1_500L, 2_000L, "A", AttributionConfidence.MEDIUM)
        )

        val turns = SpeakerTurnBuilder.build(words)

        assertEquals(0.5f, turns[0].confidence!!, 0.001f)
    }

    @Test
    fun `a short unconfident turn between two turns of one speaker is absorbed`() {
        val words = listOf(
            w(0, "we", 0L, 800L, "A"),
            w(1, "um", 800L, 1_100L, "B", AttributionConfidence.LOW),
            w(2, "should", 1_100L, 1_900L, "A")
        )

        val turns = SpeakerTurnBuilder.build(words)

        assertEquals(1, turns.size)
        assertEquals("A", turns[0].speakerId)
    }

    @Test
    fun `a short CONFIDENT turn between two turns of one speaker survives`() {
        val words = listOf(
            w(0, "ready", 0L, 800L, "A"),
            w(1, "Yeah", 820L, 1_100L, "B", AttributionConfidence.HIGH),
            w(2, "good", 1_150L, 1_900L, "A")
        )

        val turns = SpeakerTurnBuilder.build(words)

        assertEquals(listOf("A", "B", "A"), turns.map { it.speakerId })
    }

    // --- UtteranceBuilder ---

    @Test
    fun `a long turn is split at sentence punctuation, not left as one block`() {
        val words = listOf(
            w(0, "We", 0L, 300L, "A"),
            w(1, "ship", 300L, 700L, "A"),
            w(2, "Friday.", 700L, 1_200L, "A"),
            w(3, "Everyone", 1_400L, 1_900L, "A"),
            w(4, "agreed.", 1_900L, 2_400L, "A")
        )
        val turns = SpeakerTurnBuilder.build(words)

        val utterances = UtteranceBuilder.build(turns, words)

        assertEquals(2, utterances.size)
        assertEquals("We ship Friday.", utterances[0].text)
        assertEquals("Everyone agreed.", utterances[1].text)
    }

    @Test
    fun `a pause inside an unfinished clause does not end the utterance`() {
        // "...to Friday because" [1.6s] "we still need to finish the API integration."
        val words = listOf(
            w(0, "Move", 0L, 400L, "A"),
            w(1, "it", 400L, 700L, "A"),
            w(2, "to", 700L, 900L, "A"),
            w(3, "Friday", 900L, 1_400L, "A"),
            w(4, "because", 1_400L, 1_900L, "A"),
            w(5, "the", 3_500L, 3_700L, "A"),
            w(6, "API", 3_700L, 4_200L, "A"),
            w(7, "isn't", 4_200L, 4_600L, "A"),
            w(8, "ready.", 4_600L, 5_100L, "A")
        )
        val turns = SpeakerTurnBuilder.build(words)

        val utterances = UtteranceBuilder.build(turns, words)

        assertEquals(1, utterances.size)
        assertEquals("Move it to Friday because the API isn't ready.", utterances[0].text)
    }

    @Test
    fun `a pause after a completed sentence does end the utterance`() {
        val words = listOf(
            w(0, "That", 0L, 400L, "A"),
            w(1, "works.", 400L, 900L, "A"),
            w(2, "Next", 2_600L, 3_000L, "A"),
            w(3, "topic.", 3_000L, 3_500L, "A")
        )
        val turns = SpeakerTurnBuilder.build(words)

        val utterances = UtteranceBuilder.build(turns, words)

        assertEquals(2, utterances.size)
    }

    @Test
    fun `a whole turn of acknowledgement is flagged as a backchannel`() {
        val words = listOf(
            w(0, "ready", 0L, 500L, "A"),
            w(1, "Exactly.", 600L, 1_100L, "B"),
            w(2, "then", 1_200L, 1_700L, "A")
        )
        val turns = SpeakerTurnBuilder.build(words)

        val utterances = UtteranceBuilder.build(turns, words)

        val backchannels = utterances.filter { it.isBackchannel }
        assertEquals(1, backchannels.size)
        assertEquals("Exactly.", backchannels[0].text)
        assertEquals("B", backchannels[0].speakerId)
    }

    @Test
    fun `an acknowledgement that begins a real sentence is not a backchannel`() {
        val words = listOf(
            w(0, "Yeah,", 0L, 400L, "B"),
            w(1, "but", 400L, 700L, "B"),
            w(2, "we're", 700L, 1_100L, "B"),
            w(3, "almost", 1_100L, 1_600L, "B"),
            w(4, "done.", 1_600L, 2_100L, "B")
        )
        val turns = SpeakerTurnBuilder.build(words)

        val utterances = UtteranceBuilder.build(turns, words)

        assertEquals(1, utterances.size)
        assertFalse(utterances[0].isBackchannel)
    }

    @Test
    fun `punctuation inside an abbreviation does not end an utterance`() {
        val words = listOf(
            w(0, "The", 0L, 200L, "A"),
            w(1, "U.S.", 200L, 600L, "A"),
            w(2, "team", 600L, 1_000L, "A"),
            w(3, "agreed.", 1_000L, 1_500L, "A")
        )
        val turns = SpeakerTurnBuilder.build(words)

        val utterances = UtteranceBuilder.build(turns, words)

        assertEquals(1, utterances.size)
    }

    @Test
    fun `every word of the input survives into exactly one utterance, in order`() {
        val words = (0..30).map { w(it, "word$it", it * 400L, (it + 1) * 400L, if (it < 15) "A" else "B") }
        val turns = SpeakerTurnBuilder.build(words)

        val utterances = UtteranceBuilder.build(turns, words)

        val emitted = utterances.flatMap { it.wordIds }
        assertEquals(words.map { it.id }, emitted)
        assertTrue(utterances.all { it.wordIds.isNotEmpty() })
    }

    @Test
    fun `an empty input produces no turns and no utterances`() {
        assertTrue(SpeakerTurnBuilder.build(emptyList()).isEmpty())
        assertTrue(UtteranceBuilder.build(emptyList(), emptyList()).isEmpty())
    }
}
