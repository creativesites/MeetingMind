package com.example.ai.cloud

import com.example.ai.transcript.AttributionConfidence
import com.example.ai.transcript.CanonicalWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chunk-local speaker labels are cluster indices, not people. These tests hold the two properties
 * that matter: the same voice across two chunks resolves to one identity, and when the evidence
 * doesn't establish that, a *new* identity is created rather than a guessed link — an extra
 * speaker is visible and mergeable, a wrong merge is not recoverable.
 */
class GlobalSpeakerResolverTest {

    private fun words(speaker: String, fromMs: Long, toMs: Long, count: Int = 6): List<CanonicalWord> {
        val step = (toMs - fromMs) / count
        return (0 until count).map {
            CanonicalWord(
                id = "$speaker$fromMs$it",
                text = "word$it",
                startMs = fromMs + it * step,
                endMs = fromMs + (it + 1) * step,
                speakerId = speaker,
                attribution = AttributionConfidence.HIGH
            )
        }
    }

    @Test
    fun `the same voice under different local labels resolves to one global identity`() {
        // Chunk 1 called her SPEAKER_0; chunk 2's own clustering called her SPEAKER_1. They
        // overlap on 100s-120s, where only she is talking.
        val chunk1 = ChunkTranscription(
            AudioChunk(0, 0L, 120_000L),
            words("SPEAKER_0", 100_000L, 118_000L) + words("SPEAKER_9", 0L, 90_000L)
        )
        val chunk2 = ChunkTranscription(
            AudioChunk(1, 100_000L, 220_000L),
            words("SPEAKER_1", 100_000L, 118_000L) + words("SPEAKER_0", 130_000L, 200_000L)
        )

        val mapping = GlobalSpeakerResolver.resolve(listOf(chunk1, chunk2))

        assertEquals(
            mapping.resolve(0, "SPEAKER_0"),
            mapping.resolve(1, "SPEAKER_1")
        )
    }

    @Test
    fun `identical local labels for different voices are NOT assumed to be the same person`() {
        val chunk1 = ChunkTranscription(
            AudioChunk(0, 0L, 120_000L),
            words("SPEAKER_0", 100_000L, 118_000L)
        )
        val chunk2 = ChunkTranscription(
            AudioChunk(1, 100_000L, 220_000L),
            // In the overlap it is SPEAKER_1 who is talking; chunk 2's SPEAKER_0 appears only
            // afterwards and is a different person entirely.
            words("SPEAKER_1", 100_000L, 118_000L) + words("SPEAKER_0", 130_000L, 200_000L)
        )

        val mapping = GlobalSpeakerResolver.resolve(listOf(chunk1, chunk2))

        assertNotEquals(mapping.resolve(0, "SPEAKER_0"), mapping.resolve(1, "SPEAKER_0"))
    }

    @Test
    fun `a speaker with no evidence in the overlap becomes a new identity rather than a guess`() {
        val chunk1 = ChunkTranscription(AudioChunk(0, 0L, 120_000L), words("SPEAKER_0", 0L, 50_000L))
        val chunk2 = ChunkTranscription(AudioChunk(1, 100_000L, 220_000L), words("SPEAKER_0", 150_000L, 200_000L))

        val mapping = GlobalSpeakerResolver.resolve(listOf(chunk1, chunk2))

        assertEquals(2, mapping.globalSpeakerIds.size)
        assertNotEquals(mapping.resolve(0, "SPEAKER_0"), mapping.resolve(1, "SPEAKER_0"))
    }

    @Test
    fun `ambiguous evidence does not produce a confident link`() {
        // Both of chunk 1's speakers are active across the whole overlap, so chunk 2's speaker
        // matches both about equally. Linking either way would be a coin flip.
        val chunk1 = ChunkTranscription(
            AudioChunk(0, 0L, 120_000L),
            words("SPEAKER_0", 100_000L, 118_000L) + words("SPEAKER_1", 100_000L, 118_000L)
        )
        val chunk2 = ChunkTranscription(
            AudioChunk(1, 100_000L, 220_000L),
            words("SPEAKER_0", 100_000L, 118_000L)
        )

        val mapping = GlobalSpeakerResolver.resolve(listOf(chunk1, chunk2))

        assertNotEquals(mapping.resolve(0, "SPEAKER_0"), mapping.resolve(1, "SPEAKER_0"))
        assertNotEquals(mapping.resolve(0, "SPEAKER_1"), mapping.resolve(1, "SPEAKER_0"))
    }

    @Test
    fun `two local speakers can never both claim the same global identity`() {
        val chunk1 = ChunkTranscription(AudioChunk(0, 0L, 120_000L), words("SPEAKER_0", 100_000L, 118_000L))
        val chunk2 = ChunkTranscription(
            AudioChunk(1, 100_000L, 220_000L),
            words("SPEAKER_0", 100_000L, 110_000L) + words("SPEAKER_1", 110_000L, 118_000L)
        )

        val mapping = GlobalSpeakerResolver.resolve(listOf(chunk1, chunk2))

        val resolved = listOf(mapping.resolve(1, "SPEAKER_0"), mapping.resolve(1, "SPEAKER_1"))
        assertEquals("two voices cannot be one person", resolved.size, resolved.toSet().size)
    }

    @Test
    fun `identity carries across three chunks`() {
        val a = ChunkTranscription(AudioChunk(0, 0L, 120_000L), words("A0", 100_000L, 118_000L))
        val b = ChunkTranscription(
            AudioChunk(1, 100_000L, 220_000L),
            words("B7", 100_000L, 118_000L) + words("B7", 200_000L, 218_000L)
        )
        val c = ChunkTranscription(AudioChunk(2, 200_000L, 320_000L), words("C3", 200_000L, 218_000L))

        val mapping = GlobalSpeakerResolver.resolve(listOf(a, b, c))

        assertEquals(mapping.resolve(0, "A0"), mapping.resolve(2, "C3"))
    }

    @Test
    fun `applying the mapping rewrites every word's speaker`() {
        val chunk1 = ChunkTranscription(AudioChunk(0, 0L, 120_000L), words("SPEAKER_0", 100_000L, 118_000L))
        val chunk2 = ChunkTranscription(AudioChunk(1, 100_000L, 220_000L), words("SPEAKER_1", 100_000L, 118_000L))
        val mapping = GlobalSpeakerResolver.resolve(listOf(chunk1, chunk2))

        val applied = GlobalSpeakerResolver.applyMapping(listOf(chunk1, chunk2), mapping)

        assertTrue(applied.flatMap { it.words }.all { it.speakerId?.startsWith("global_speaker_") == true })
    }

    @Test
    fun `a single chunk simply names its own speakers`() {
        val only = ChunkTranscription(
            AudioChunk(0, 0L, 60_000L),
            words("SPEAKER_0", 0L, 20_000L) + words("SPEAKER_1", 20_000L, 40_000L)
        )

        val mapping = GlobalSpeakerResolver.resolve(listOf(only))

        assertEquals(2, mapping.globalSpeakerIds.size)
        assertNotEquals(mapping.resolve(0, "SPEAKER_0"), mapping.resolve(0, "SPEAKER_1"))
    }

    @Test
    fun `no chunks resolves to nothing rather than throwing`() {
        val mapping = GlobalSpeakerResolver.resolve(emptyList())

        assertTrue(mapping.globalSpeakerIds.isEmpty())
        assertEquals(null, mapping.resolve(0, "SPEAKER_0"))
    }
}
