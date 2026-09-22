package com.example.ai.transcript

import com.example.core.model.RecordingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end over the structural layer: a word stream and diarization turns in, a canonical
 * transcript and its segment projection out.
 *
 * Each test here names the defect from the overhaul brief it covers. These are **structural**
 * proofs — they show the code path that produced each defect is gone and cannot reproduce it on
 * this input. They are not a substitute for measuring WER and speaker error on real recordings,
 * which needs a device and an audio corpus; see docs/TRANSCRIPTION_OVERHAUL.md section 6.
 */
class CanonicalTranscriptAssemblerTest {

    private val metadata = TranscriptMetadata(
        meetingId = "m1",
        processingMode = "OFFLINE",
        transcriptionEngine = "test"
    )

    /** Lays a sentence out as timed words at a steady speaking rate. */
    private fun speak(
        sentence: String,
        fromMs: Long,
        speaker: String?,
        msPerWord: Long = 350L,
        confidence: AttributionConfidence = AttributionConfidence.HIGH
    ): List<CanonicalWord> = sentence.split(" ").mapIndexed { i, text ->
        CanonicalWord(
            id = "tmp",
            text = text,
            startMs = fromMs + i * msPerWord,
            endMs = fromMs + (i + 1) * msPerWord,
            speakerId = speaker,
            attribution = if (speaker == null) AttributionConfidence.NONE else confidence
        )
    }

    /** Assigns transcript-wide ids the way the reconciler does, so ids are realistic. */
    private fun stream(vararg parts: List<CanonicalWord>): List<CanonicalWord> =
        parts.toList().flatten().sortedBy { it.startMs }
            .mapIndexed { i, word -> word.copy(id = canonicalWordId(i)) }

    private fun assemble(
        words: List<CanonicalWord>,
        recordingType: RecordingType = RecordingType.MEETING,
        singleSpeakerMode: Boolean = false
    ) = CanonicalTranscriptAssembler.assemble(
        meetingId = "m1",
        words = words,
        recordingType = recordingType,
        singleSpeakerMode = singleSpeakerMode,
        metadata = metadata,
        speakerNameFor = { id -> "Speaker ${id.last().digitToIntOrNull()?.plus(1) ?: 1}" }
    )

    // --- Problem A / E: one sentence must not become several tiny pieces ---

    @Test
    fun `a sentence interrupted by a pause is one paragraph, not five fragments`() {
        // "I think we should probably move the launch to Friday because we still need to finish
        // the API integration." — spoken with two ordinary mid-thought pauses in it. Under the old
        // pipeline each pause was a VAD boundary and therefore a transcript boundary.
        val words = stream(
            speak("I think we should probably move the launch", fromMs = 0L, speaker = "s_0"),
            speak("to Friday because", fromMs = 3_600L, speaker = "s_0"),
            speak("we still need to finish the API integration.", fromMs = 5_800L, speaker = "s_0")
        )

        val transcript = assemble(words)

        assertEquals(1, transcript.paragraphs.size)
        assertEquals(
            "I think we should probably move the launch to Friday because we still need to finish the API integration.",
            transcript.paragraphs[0].text
        )
    }

    // --- Problem F: paragraphs should read the way a person would write them ---

    @Test
    fun `a two-speaker exchange reads as one paragraph each, with speakers attached`() {
        val words = stream(
            speak("I think we should move the launch to Friday.", fromMs = 0L, speaker = "s_0"),
            speak("Yeah, I agree. The authentication flow is the only thing holding us back.", fromMs = 3_200L, speaker = "s_1")
        )

        val transcript = assemble(words)

        assertEquals(listOf("s_0", "s_1"), transcript.paragraphs.map { it.speakerId })
        assertEquals("I think we should move the launch to Friday.", transcript.paragraphs[0].text)
        assertTrue(transcript.paragraphs[1].text.startsWith("Yeah, I agree."))
        assertEquals(listOf("Speaker 1", "Speaker 2"), transcript.paragraphs.map { it.speakerName })
    }

    // --- Problem D: a short response must stay its own contribution ---

    @Test
    fun `a backchannel from another speaker is never merged into the surrounding paragraph`() {
        val words = stream(
            speak("So we are aligned on shipping Friday.", fromMs = 0L, speaker = "s_0"),
            speak("Exactly.", fromMs = 2_400L, speaker = "s_1"),
            speak("Good. I will tell the customers.", fromMs = 3_200L, speaker = "s_0")
        )

        val transcript = assemble(words)

        assertEquals(3, transcript.paragraphs.size)
        assertEquals("Exactly.", transcript.paragraphs[1].text)
        assertEquals("s_1", transcript.paragraphs[1].speakerId)
        assertTrue(transcript.utterances.any { it.isBackchannel && it.text == "Exactly." })
    }

    // --- Problem B / C: speaker labels must be stable and evidence-based ---

    @Test
    fun `one speaker talking across pauses produces one speaker, not several`() {
        val words = stream(
            speak("First point about the roadmap.", fromMs = 0L, speaker = "s_0"),
            speak("Second point about the budget.", fromMs = 4_000L, speaker = "s_0"),
            speak("Third point about hiring.", fromMs = 9_000L, speaker = "s_0")
        )

        val transcript = assemble(words)

        assertEquals(listOf("s_0"), transcript.speakerIds)
    }

    @Test
    fun `words with no diarization evidence never acquire a speaker through grouping`() {
        val words = stream(speak("nobody knows who said this", fromMs = 0L, speaker = null))

        val transcript = assemble(words)

        assertTrue(transcript.words.all { it.speakerId == null })
        assertTrue(transcript.paragraphs.all { it.speakerId == null })
        assertTrue("no speaker may be invented", transcript.speakerIds.isEmpty())
    }

    // --- Provenance: the defining MeetingMind capability ---

    @Test
    fun `every paragraph traces back through utterances to real word ids and timestamps`() {
        val words = stream(
            speak("We will ship on Friday.", fromMs = 0L, speaker = "s_0"),
            speak("Sounds good to me.", fromMs = 2_500L, speaker = "s_1")
        )

        val transcript = assemble(words)

        for (paragraph in transcript.paragraphs) {
            assertTrue("paragraph ${paragraph.id} has no source utterances", paragraph.utteranceIds.isNotEmpty())
            assertTrue("paragraph ${paragraph.id} has no source words", paragraph.wordIds.isNotEmpty())
            val sourceWords = transcript.wordsFor(paragraph.wordIds)
            assertEquals(paragraph.wordIds.size, sourceWords.size)
            assertEquals(paragraph.startMs, sourceWords.minOf { it.startMs })
            assertEquals(paragraph.endMs, sourceWords.maxOf { it.endMs })
        }
    }

    @Test
    fun `no word is dropped, duplicated or reordered by assembly`() {
        val words = stream(
            speak("alpha bravo charlie delta echo foxtrot golf", fromMs = 0L, speaker = "s_0"),
            speak("hotel india juliet kilo lima mike", fromMs = 4_000L, speaker = "s_1")
        )

        val transcript = assemble(words)

        val emitted = transcript.paragraphs.flatMap { it.wordIds }
        assertEquals(words.map { it.id }, emitted)
    }

    // --- Projection: the existing app must keep working ---

    @Test
    fun `the segment projection carries speakers, timing, provenance and words`() {
        val words = stream(
            speak("We will ship on Friday.", fromMs = 0L, speaker = "s_0"),
            speak("Sounds good to me.", fromMs = 2_500L, speaker = "s_1")
        )
        val transcript = assemble(words)

        val segments = CanonicalTranscriptAssembler.projectToSegments(transcript)

        assertEquals(transcript.paragraphs.size, segments.size)
        for ((index, segment) in segments.withIndex()) {
            val paragraph = transcript.paragraphs[index]
            assertEquals(paragraph.text, segment.text)
            assertEquals(paragraph.speakerId, segment.speakerId)
            assertEquals(paragraph.startMs, segment.startMs)
            assertEquals(paragraph.endMs, segment.endMs)
            assertEquals("m1", segment.meetingId)
            assertEquals(paragraph.utteranceIds, segment.sourceSegmentIds)
            assertEquals(paragraph.wordIds, segment.words.map { it.id })
            assertTrue("word provenance must survive the projection", segment.words.isNotEmpty())
        }
    }

    @Test
    fun `a projected word round-trips back into the canonical layer unchanged`() {
        val original = CanonicalWord(
            id = "w7", text = "integration", startMs = 1_000L, endMs = 1_600L,
            speakerId = "s_1", attribution = AttributionConfidence.MEDIUM,
            confidence = 0.8f, source = TranscriptSource.GEMINI_VERBATIM
        )

        assertEquals(original, original.toDomainWord().toCanonicalWord(fallbackId = "unused"))
    }

    @Test
    fun `a legacy word with no provenance fields reads back as honestly unknown`() {
        val legacy = com.example.core.model.TranscriptWord(text = "hello", startMs = 0L, endMs = 500L)

        val canonical = legacy.toCanonicalWord(fallbackId = "w0")

        assertEquals("w0", canonical.id)
        assertEquals(null, canonical.speakerId)
        assertEquals(AttributionConfidence.NONE, canonical.attribution)
        assertEquals(null, canonical.confidence)
    }

    // --- Solo recordings ---

    @Test
    fun `single-speaker mode does not fragment a solo recording at thinking pauses`() {
        val words = stream(
            speak("So the idea is a meeting recorder", fromMs = 0L, speaker = "speaker_0"),
            speak("that actually understands what was said.", fromMs = 5_000L, speaker = "speaker_0")
        )

        val transcript = assemble(words, RecordingType.IDEA, singleSpeakerMode = true)

        assertEquals(1, transcript.paragraphs.size)
    }

    // --- Degenerate input ---

    @Test
    fun `an empty word stream assembles into an empty transcript rather than throwing`() {
        val transcript = assemble(emptyList())

        assertTrue(transcript.isEmpty)
        assertTrue(transcript.paragraphs.isEmpty())
        assertTrue(CanonicalTranscriptAssembler.projectToSegments(transcript).isEmpty())
        assertNotNull(transcript.metadata)
    }
}
