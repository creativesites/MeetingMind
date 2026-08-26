package com.example.ai.diarization

import com.example.ai.common.AiResult
import com.example.ai.llm.LanguageModel
import com.example.core.model.DiarizationStrategy
import com.example.core.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [computeSpeakerTranscriptFootprints]/[ambiguousSpeakerIds]/[shouldAttemptAiReconciliation] are
 * pure and covered directly against the product spec's own worked examples (the 82/7/5/4/2%
 * suspicious case vs. the 52/48% plausible one). [RealDiarizationReconciliationEngine] is
 * exercised against a deterministic fake [LanguageModel] — no live model inference in a JVM test.
 * Robolectric is required (like [com.example.ai.pipeline.TranscriptAiCleanupEngineTest]) because
 * the engine parses JSON via org.json, which the plain JVM android.jar stub can't construct/parse.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiarizationReconciliationEngineTest {

    private fun seg(id: String, speakerId: String?, speakerName: String?, startMs: Long, endMs: Long, text: String = "Some text.") =
        TranscriptSegment(
            id = id, meetingId = "m1", speakerId = speakerId, speakerName = speakerName,
            startMs = startMs, endMs = endMs, text = text
        )

    // --- computeSpeakerTranscriptFootprints ---

    @Test
    fun `footprint share and turn count are computed correctly from real durations`() {
        val segments = listOf(
            seg("s1", "spk_0", "Speaker 1", 0, 8_000),
            seg("s2", "spk_1", "Speaker 2", 8_000, 10_000),
            seg("s3", "spk_0", "Speaker 1", 10_000, 18_000)
        )
        val footprints = computeSpeakerTranscriptFootprints(segments)

        val spk0 = footprints.first { it.speakerId == "spk_0" }
        val spk1 = footprints.first { it.speakerId == "spk_1" }
        assertEquals(16_000L, spk0.totalDurationMs)
        assertEquals(2, spk0.turnCount)
        assertEquals(2_000L, spk1.totalDurationMs)
        assertEquals(1, spk1.turnCount)
        assertEquals(16_000.0 / 18_000.0, spk0.shareOfTotal, 0.001)
        assertEquals(2_000.0 / 18_000.0, spk1.shareOfTotal, 0.001)
    }

    @Test
    fun `segments with no speakerId are excluded from footprints`() {
        val segments = listOf(seg("s1", null, null, 0, 1000), seg("s2", "spk_0", "Speaker 1", 1000, 2000))
        val footprints = computeSpeakerTranscriptFootprints(segments)
        assertEquals(1, footprints.size)
        assertEquals("spk_0", footprints.single().speakerId)
    }

    // --- ambiguousSpeakerIds: the product spec's own worked examples ---

    @Test
    fun `a suspicious 82-7-5-4-2 split flags every minor speaker as ambiguous`() {
        // Speaker 1 = 82%, Speaker 2 = 7%, Speaker 3 = 5%, Speaker 4 = 4%, Speaker 5 = 2%
        val total = 100_000L
        val segments = listOf(
            seg("s1", "spk_0", null, 0, 82_000),
            seg("s2", "spk_1", null, 82_000, 89_000),
            seg("s3", "spk_2", null, 89_000, 94_000),
            seg("s4", "spk_3", null, 94_000, 98_000),
            seg("s5", "spk_4", null, 98_000, total)
        )
        val ambiguous = ambiguousSpeakerIds(computeSpeakerTranscriptFootprints(segments))
        assertEquals(setOf("spk_1", "spk_2", "spk_3", "spk_4"), ambiguous)
    }

    @Test
    fun `a plausible 52-48 split flags nothing as ambiguous`() {
        val segments = listOf(
            seg("s1", "spk_0", null, 0, 52_000),
            seg("s2", "spk_1", null, 52_000, 100_000)
        )
        val ambiguous = ambiguousSpeakerIds(computeSpeakerTranscriptFootprints(segments))
        assertTrue(ambiguous.isEmpty())
    }

    @Test
    fun `a single real minor participant is still flagged as a candidate to review, not silently accepted`() {
        // One minor speaker at 12% alongside one dominant at 88% — this is exactly the residual
        // case the deterministic pass's own MIN_NOISE_SPEAKERS_TO_FLAG=2 gate leaves untouched;
        // this engine's job is to make it a REVIEWABLE candidate, not to decide it's noise itself.
        val segments = listOf(
            seg("s1", "spk_0", null, 0, 88_000),
            seg("s2", "spk_1", null, 88_000, 100_000)
        )
        val ambiguous = ambiguousSpeakerIds(computeSpeakerTranscriptFootprints(segments))
        assertEquals(setOf("spk_1"), ambiguous)
    }

    @Test
    fun `when every speaker is ambiguous there is nothing confident to anchor a merge - nothing flagged`() {
        // Eight near-equal speakers (12.5% each) — every one sits below AMBIGUOUS_SHARE_THRESHOLD,
        // so there is no confident speaker at all to merge an ambiguous one into.
        val segments = (0 until 8).map { i ->
            seg("s$i", "spk_$i", null, i * 1_000L, i * 1_000L + 1_000L)
        }
        val ambiguous = ambiguousSpeakerIds(computeSpeakerTranscriptFootprints(segments))
        assertTrue(ambiguous.isEmpty())
    }

    // --- shouldAttemptAiReconciliation ---

    @Test
    fun `DETERMINISTIC never attempts reconciliation even when ambiguity exists`() {
        val segments = listOf(seg("s1", "spk_0", null, 0, 88_000), seg("s2", "spk_1", null, 88_000, 100_000))
        val footprints = computeSpeakerTranscriptFootprints(segments)
        assertFalse(shouldAttemptAiReconciliation(footprints, DiarizationStrategy.DETERMINISTIC))
    }

    @Test
    fun `AI_ASSISTED and AUTO attempt reconciliation only when something is ambiguous`() {
        val ambiguousCase = computeSpeakerTranscriptFootprints(
            listOf(seg("s1", "spk_0", null, 0, 88_000), seg("s2", "spk_1", null, 88_000, 100_000))
        )
        val plausibleCase = computeSpeakerTranscriptFootprints(
            listOf(seg("s1", "spk_0", null, 0, 52_000), seg("s2", "spk_1", null, 52_000, 100_000))
        )
        assertTrue(shouldAttemptAiReconciliation(ambiguousCase, DiarizationStrategy.AI_ASSISTED))
        assertTrue(shouldAttemptAiReconciliation(ambiguousCase, DiarizationStrategy.AUTO))
        assertFalse(shouldAttemptAiReconciliation(plausibleCase, DiarizationStrategy.AI_ASSISTED))
        assertFalse(shouldAttemptAiReconciliation(plausibleCase, DiarizationStrategy.AUTO))
    }

    // --- RealDiarizationReconciliationEngine ---

    private class FakeLanguageModel(private val response: AiResult<String>) : LanguageModel {
        var callCount = 0
        var lastPrompt: String? = null
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
            callCount++
            lastPrompt = prompt
            return response
        }
    }

    private fun minorMajorSegments() = listOf(
        seg("s1", "spk_0", "Speaker 1", 0, 4_000, "I think we should use the smaller model for this."),
        seg("s2", "spk_1", "Speaker 2", 4_000, 4_800, "because it could help us"),
        seg("s3", "spk_0", "Speaker 1", 4_800, 10_000, "ship faster next quarter.")
    )

    @Test
    fun `a valid accepted merge reassigns the minor speaker's segments to the confident one`() = runBlocking {
        val model = FakeLanguageModel(AiResult.Success("""[{"from":"spk_1","into":"spk_0","reason":"continues the sentence"}]"""))
        val result = RealDiarizationReconciliationEngine(model).reconcile(minorMajorSegments())

        val value = (result as AiResult.Success).value
        assertEquals(setOf("spk_1"), value.mergedSpeakerIds)
        assertTrue(value.segments.all { it.speakerId == "spk_0" })
        assertTrue(value.segments.first { it.id == "s2" }.speakerName == "Speaker 1")
    }

    @Test
    fun `a proposal naming an id that was never offered is ignored`() = runBlocking {
        val model = FakeLanguageModel(AiResult.Success("""[{"from":"spk_1","into":"spk_99","reason":"hallucinated id"}]"""))
        val result = RealDiarizationReconciliationEngine(model).reconcile(minorMajorSegments())

        val value = (result as AiResult.Success).value
        assertTrue(value.mergedSpeakerIds.isEmpty())
        assertEquals("spk_1", value.segments.first { it.id == "s2" }.speakerId)
    }

    @Test
    fun `a proposal merging one confident speaker into another is never applied`() = runBlocking {
        // Two confident speakers, no ambiguous one at all — the engine must never even call the
        // model, since there is nothing to ask about.
        val segments = listOf(
            seg("s1", "spk_0", "Speaker 1", 0, 52_000),
            seg("s2", "spk_1", "Speaker 2", 52_000, 100_000)
        )
        val model = FakeLanguageModel(AiResult.Success("""[{"from":"spk_0","into":"spk_1","reason":"nice try"}]"""))
        val result = RealDiarizationReconciliationEngine(model).reconcile(segments)

        assertEquals(0, model.callCount)
        val value = (result as AiResult.Success).value
        assertTrue(value.mergedSpeakerIds.isEmpty())
        assertEquals(segments, value.segments)
    }

    @Test
    fun `an empty proposal array keeps every segment's speaker unchanged`() = runBlocking {
        val model = FakeLanguageModel(AiResult.Success("[]"))
        val result = RealDiarizationReconciliationEngine(model).reconcile(minorMajorSegments())

        val value = (result as AiResult.Success).value
        assertTrue(value.mergedSpeakerIds.isEmpty())
        assertEquals("spk_1", value.segments.first { it.id == "s2" }.speakerId)
    }

    @Test
    fun `malformed JSON falls back to the deterministic result without throwing`() = runBlocking {
        val model = FakeLanguageModel(AiResult.Success("not json at all"))
        val result = RealDiarizationReconciliationEngine(model).reconcile(minorMajorSegments())

        val value = (result as AiResult.Success).value
        assertTrue(value.mergedSpeakerIds.isEmpty())
    }

    @Test
    fun `a ModelUnavailable response is surfaced so the pipeline can fall back honestly`() = runBlocking {
        val model = FakeLanguageModel(AiResult.ModelUnavailable("m", "not installed"))
        val result = RealDiarizationReconciliationEngine(model).reconcile(minorMajorSegments())
        assertTrue(result is AiResult.ModelUnavailable)
    }

    @Test
    fun `the model is never called at all when nothing is ambiguous`() = runBlocking {
        val segments = listOf(
            seg("s1", "spk_0", "Speaker 1", 0, 52_000),
            seg("s2", "spk_1", "Speaker 2", 52_000, 100_000)
        )
        val model = FakeLanguageModel(AiResult.Success("[]"))
        RealDiarizationReconciliationEngine(model).reconcile(segments)
        assertEquals(0, model.callCount)
    }

    @Test
    fun `the prompt never asks the model to reconsider a confident speaker`() = runBlocking {
        val model = FakeLanguageModel(AiResult.Success("[]"))
        RealDiarizationReconciliationEngine(model).reconcile(minorMajorSegments())

        val prompt = model.lastPrompt!!
        assertTrue(prompt.contains("MAIN SPEAKERS"))
        assertTrue(prompt.contains("MINOR SPEAKERS"))
        assertTrue(prompt.contains("spk_0"))
        assertTrue(prompt.contains("spk_1"))
    }
}
