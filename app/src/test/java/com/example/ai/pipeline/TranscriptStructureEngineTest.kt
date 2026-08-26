package com.example.ai.pipeline

import com.example.core.model.RecordingType
import com.example.core.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DeterministicTranscriptStructureEngine] replaces the old fixed-threshold
 * `TranscriptParagraphBuilder`. These tests exercise every signal the engine's merge decision
 * actually reads — speaker identity, recording type, gap duration, sentence punctuation,
 * incomplete-sentence wording, fragment length, single-speaker mode, and the hard ceilings — plus
 * the invariants a professional transcript layer must never violate: no invented/reordered text,
 * timestamp and source-segment-id provenance surviving a merge, and a user's own edit never being
 * silently absorbed.
 */
class TranscriptStructureEngineTest {

    private val engine = DeterministicTranscriptStructureEngine

    private fun seg(
        id: String,
        startMs: Long,
        endMs: Long,
        text: String,
        speakerId: String? = null,
        confidence: Float? = null,
        isUserEdited: Boolean = false
    ) = TranscriptSegment(
        id = id,
        meetingId = "m1",
        speakerId = speakerId,
        speakerName = speakerId?.let { "Speaker $it" },
        startMs = startMs,
        endMs = endMs,
        text = text,
        confidence = confidence,
        isUserEdited = isUserEdited
    )

    // --- Single-speaker mode: natural pauses must not fragment a solo recording ---

    @Test
    fun `single-speaker mode merges a natural pause across two complete sentences that would otherwise break`() {
        // MEETING's own base gap (1500ms) would normally break this — proving the single-speaker
        // boost itself is doing the work, not the recording type's own generosity.
        val fragments = listOf(
            seg("1", 0, 2000, "I think this works."),
            seg("2", 5500, 7000, "Let's move forward.")
        )

        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = true)

        assertEquals(1, result.size)
        assertEquals("I think this works. Let's move forward.", result[0].text)
    }

    @Test
    fun `single-speaker mode still breaks a genuinely long silence - it does not merge indefinitely`() {
        val fragments = listOf(
            seg("1", 0, 2000, "The budget is approved."),
            seg("2", 12000, 14000, "Let's move to the next topic.")
        )

        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = true)

        assertEquals(2, result.size)
    }

    @Test
    fun `single-speaker fragmented sentence across three fragments merges into one paragraph`() {
        val fragments = listOf(
            seg("1", 0, 800, "I think"),
            seg("2", 2600, 3400, "we should"),
            seg("3", 5200, 6500, "go with option B.")
        )

        val result = engine.structure(fragments, RecordingType.IDEA, singleSpeakerMode = true)

        assertEquals(1, result.size)
        assertEquals("I think we should go with option B.", result[0].text)
    }

    // --- Short fragments and incomplete-sentence cues extend gap tolerance on their own ---

    @Test
    fun `a one-word fragment merges across a gap that would otherwise break, even though both sides look complete`() {
        val fragments = listOf(
            seg("1", 0, 500, "Yes."),
            seg("2", 2500, 3500, "That works.")
        )

        // MEETING's base gap is 1500ms; this 2000ms gap only survives because "Yes." is a
        // one-word fragment, not because of anything about sentence completeness.
        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = false)

        assertEquals(1, result.size)
        assertEquals("Yes. That works.", result[0].text)
    }

    @Test
    fun `a long pause after a trailing conjunction still merges - the thought was not finished`() {
        val fragments = listOf(
            seg("1", 0, 2000, "We need to think about the budget and"),
            seg("2", 4800, 6000, "the timeline too.")
        )

        // 2800ms exceeds MEETING's base gap (1500ms) but stays under its extended gap (3000ms),
        // which only applies because the accumulated text trails off on "and".
        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = false)

        assertEquals(1, result.size)
        assertEquals("We need to think about the budget and the timeline too.", result[0].text)
    }

    @Test
    fun `a genuine sentence boundary still breaks even at a moderate pause`() {
        val fragments = listOf(
            seg("1", 0, 2000, "The budget is approved."),
            seg("2", 4000, 6000, "Let's move to the next topic.")
        )

        // 2000ms exceeds MEETING's base gap (1500ms); nothing about this text is incomplete or
        // short, so the extended gap never applies.
        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = false)

        assertEquals(2, result.size)
    }

    // --- Speaker identity always wins ---

    @Test
    fun `a genuine speaker change breaks the paragraph even with no pause at all`() {
        val fragments = listOf(
            seg("1", 0, 1200, "Are we agreed?", speakerId = "spk_0"),
            seg("2", 1250, 2000, "Yes.", speakerId = "spk_1")
        )

        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = false)

        assertEquals(2, result.size)
        assertEquals("spk_0", result[0].speakerId)
        assertEquals("spk_1", result[1].speakerId)
    }

    @Test
    fun `two speakers alternating rapidly never merge across each other`() {
        val fragments = listOf(
            seg("1", 0, 1000, "Point one.", speakerId = "spk_0"),
            seg("2", 1050, 2000, "Counterpoint.", speakerId = "spk_1"),
            seg("3", 2050, 3000, "Rebuttal.", speakerId = "spk_0"),
            seg("4", 3050, 4000, "Agreed.", speakerId = "spk_1")
        )

        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = false)

        assertEquals(4, result.size)
        assertEquals(listOf("spk_0", "spk_1", "spk_0", "spk_1"), result.map { it.speakerId })
    }

    @Test
    fun `single-speaker mode ignores speakerId entirely as a boundary signal`() {
        // Even if two fragments somehow carried different speakerIds, singleSpeakerMode means
        // "treat this as one continuous stream" — diarization was skipped precisely because the
        // user confirmed there is only one speaker.
        val fragments = listOf(
            seg("1", 0, 1000, "First part", speakerId = "spk_0"),
            seg("2", 1050, 2000, "second part.", speakerId = "spk_1")
        )

        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = true)

        assertEquals(1, result.size)
    }

    // --- Recording-type policy differences ---

    @Test
    fun `the same gap merges for Lecture and Idea but breaks for Meeting and Interview`() {
        fun fragments() = listOf(
            seg("1", 0, 2000, "That concludes this point."),
            seg("2", 3800, 5800, "Now here's the next one.")
        ) // 1800ms gap between fragment 1's end and fragment 2's start

        assertEquals(2, engine.structure(fragments(), RecordingType.INTERVIEW, singleSpeakerMode = false).size)
        assertEquals(2, engine.structure(fragments(), RecordingType.MEETING, singleSpeakerMode = false).size)
        assertEquals(1, engine.structure(fragments(), RecordingType.LECTURE, singleSpeakerMode = false).size)
        assertEquals(1, engine.structure(fragments(), RecordingType.IDEA, singleSpeakerMode = false).size)
    }

    // --- Ceilings: never blindly merge indefinitely ---

    @Test
    fun `breaks a long monologue at the duration ceiling instead of building one wall of text`() {
        val fragments = (0 until 60).map { i ->
            seg("$i", i * 1000L, i * 1000L + 900L, "word$i")
        }

        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = false)

        assertTrue("Expected more than one paragraph, got ${result.size}", result.size > 1)
        result.forEach { paragraph ->
            assertTrue(
                "Paragraph spans ${paragraph.endMs - paragraph.startMs}ms",
                paragraph.endMs - paragraph.startMs <= RecordingType.MEETING.transcriptMergePolicy().maxParagraphDurationMs
            )
        }
    }

    @Test
    fun `respects the character ceiling`() {
        val longText = "x".repeat(400)
        val fragments = listOf(
            seg("1", 0, 1000, longText),
            seg("2", 1100, 2000, longText)
        )

        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = false)

        assertEquals(2, result.size)
    }

    // --- Text fidelity ---

    @Test
    fun `preserves every word in order - no content is dropped or reordered when merging`() {
        val fragments = listOf(
            seg("1", 0, 1000, "alpha"),
            seg("2", 1500, 2000, "bravo"),
            seg("3", 2500, 3000, "charlie")
        )

        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = false)

        assertEquals(1, result.size)
        val words = result[0].text.lowercase().split(" ").filter { it.isNotBlank() }
        assertEquals(listOf("alpha", "bravo", "charlie"), words)
    }

    @Test
    fun `adds the sentence break ASR omitted between two unpunctuated fragments`() {
        val fragments = listOf(
            seg("1", 0, 1000, "That is done"),
            seg("2", 1100, 2000, "Next we review the budget")
        )

        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = false)

        assertEquals(1, result.size)
        assertEquals("That is done. Next we review the budget", result[0].text)
    }

    // --- Timestamp and provenance survival ---

    @Test
    fun `a merged paragraph keeps the earliest start and the latest end timestamp`() {
        val fragments = listOf(
            seg("1", 100, 1200, "So the thing is"),
            seg("2", 1500, 2600, "we need to ship this"),
            seg("3", 2900, 4000, "before Friday.")
        )

        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = false)

        assertEquals(1, result.size)
        assertEquals(100L, result[0].startMs)
        assertEquals(4000L, result[0].endMs)
    }

    @Test
    fun `a merged paragraph lists every source fragment id in order`() {
        val fragments = listOf(
            seg("frag-a", 0, 1000, "So the thing is"),
            seg("frag-b", 1200, 2000, "we need to ship this"),
            seg("frag-c", 2200, 3000, "before Friday.")
        )

        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = false)

        assertEquals(1, result.size)
        assertEquals(listOf("frag-a", "frag-b", "frag-c"), result[0].sourceSegmentIds)
    }

    @Test
    fun `an un-merged single-fragment paragraph lists itself as its own source`() {
        val fragments = listOf(
            seg("1", 0, 1200, "First thought.", speakerId = "spk_0"),
            seg("2", 5000, 6200, "Completely separate thought.", speakerId = "spk_0")
        )

        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = false)

        assertEquals(2, result.size)
        assertEquals(listOf("1"), result[0].sourceSegmentIds)
        assertEquals(listOf("2"), result[1].sourceSegmentIds)
    }

    @Test
    fun `a single segment and empty input pass through untouched, gaining self-provenance`() {
        assertEquals(emptyList<TranscriptSegment>(), engine.structure(emptyList(), RecordingType.MEETING, singleSpeakerMode = false))

        val one = listOf(seg("1", 0, 1000, "Only one."))
        val result = engine.structure(one, RecordingType.MEETING, singleSpeakerMode = false)
        assertEquals(1, result.size)
        assertEquals("Only one.", result[0].text)
        assertEquals(listOf("1"), result[0].sourceSegmentIds)
    }

    // --- User edits are never silently absorbed into a merge ---

    @Test
    fun `a user-edited fragment is never merged with its neighbors, in either direction`() {
        val fragments = listOf(
            seg("1", 0, 1000, "First part"),
            seg("2", 1500, 2500, "User corrected text", isUserEdited = true),
            seg("3", 3000, 4000, "Third part")
        )

        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = false)

        assertEquals(3, result.size)
        assertEquals("User corrected text", result[1].text)
        assertTrue(result[1].isUserEdited)
    }

    // --- Confidence handling (unchanged behavior from the engine this replaces) ---

    @Test
    fun `confidence stays null when any source segment lacked a real score`() {
        val fragments = listOf(
            seg("1", 0, 1000, "First part", confidence = 0.9f),
            seg("2", 1100, 2000, "second part.", confidence = null)
        )

        val result = engine.structure(fragments, RecordingType.MEETING, singleSpeakerMode = false)

        assertEquals(1, result.size)
        assertEquals(null, result[0].confidence)
    }
}
