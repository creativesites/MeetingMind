package com.example.ai.pipeline

import com.example.core.model.RecordingType
import com.example.core.model.TranscriptCleanupMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TranscriptQualityValidator] is the safety net between any [TranscriptCleanupEngine] candidate
 * (today's rule-based one, or a future SLM-backed one) and what actually gets cached/shown. These
 * tests exercise it directly against synthetic raw/cleaned pairs — including the exact three
 * dangerous-edit scenarios called out by the product spec — independent of which engine might
 * someday produce them.
 */
class TranscriptQualityValidatorTest {

    @Test
    fun `accepts a genuine filler-removal cleanup`() {
        val verdict = TranscriptQualityValidator.validate(
            raw = "Uh, so, I think this works pretty well, you know.",
            cleaned = "I think this works pretty well."
        )
        assertTrue(verdict.reason, verdict.accepted)
    }

    @Test
    fun `accepts identical text unchanged`() {
        val text = "The budget is fine and the launch is on track."
        assertTrue(TranscriptQualityValidator.validate(text, text).accepted)
    }

    @Test
    fun `rejects a changed dollar amount`() {
        val verdict = TranscriptQualityValidator.validate(
            raw = "We agreed on a budget of \$15,000 for this quarter.",
            cleaned = "We agreed on a budget of \$50,000 for this quarter."
        )
        assertFalse(verdict.accepted)
    }

    @Test
    fun `rejects a changed day of the week`() {
        val verdict = TranscriptQualityValidator.validate(
            raw = "Let's meet again on Monday to finalize things.",
            cleaned = "Let's meet again on Friday to finalize things."
        )
        assertFalse(verdict.accepted)
    }

    @Test
    fun `rejects a swapped name`() {
        val verdict = TranscriptQualityValidator.validate(
            raw = "John said he would handle the deployment.",
            cleaned = "Peter said he would handle the deployment."
        )
        assertFalse(verdict.accepted)
    }

    @Test
    fun `rejects a name being dropped entirely, not just swapped`() {
        val verdict = TranscriptQualityValidator.validate(
            raw = "Sarah is going to own the follow-up.",
            cleaned = "Someone is going to own the follow-up."
        )
        assertFalse(verdict.accepted)
    }

    @Test
    fun `rejects a null candidate`() {
        assertFalse(TranscriptQualityValidator.validate("Real transcript text.", null).accepted)
    }

    @Test
    fun `rejects empty output for non-blank input`() {
        assertFalse(TranscriptQualityValidator.validate("Real transcript text.", "").accepted)
    }

    @Test
    fun `accepts blank cleaned output only when the raw input was itself blank`() {
        assertTrue(TranscriptQualityValidator.validate("", "").accepted)
    }

    @Test
    fun `rejects output with no readable content`() {
        assertFalse(TranscriptQualityValidator.validate("Real transcript text.", "...").accepted)
    }

    @Test
    fun `rejects a candidate that is drastically shorter than the raw text`() {
        val verdict = TranscriptQualityValidator.validate(
            raw = "We discussed the entire roadmap for the next two quarters including hiring plans and budget allocation across every team.",
            cleaned = "Roadmap discussed."
        )
        assertFalse(verdict.accepted)
    }

    @Test
    fun `rejects a candidate that is drastically longer than the raw text - an invented expansion`() {
        val raw = "We should ship on Friday."
        val cleaned = "We should ship on Friday, and to elaborate further, this decision reflects a broader strategic shift in how the team plans to approach quarterly releases going forward, with implications for staffing and budget."
        assertFalse(TranscriptQualityValidator.validate(raw, cleaned).accepted)
    }

    @Test
    fun `rejects fluent but unrelated output that shares too little vocabulary with the raw text`() {
        val verdict = TranscriptQualityValidator.validate(
            raw = "The kitchen renovation should be done by next month.",
            cleaned = "Quarterly revenue targets are trending upward across all regions."
        )
        assertFalse(verdict.accepted)
    }

    @Test
    fun `a legitimate cleanup that moves a name to the start of a sentence is still accepted`() {
        // Removing a leading filler can legitimately make a name the first word of the cleaned
        // sentence — the validator must not require the exact same sentence position, only that
        // the name itself survives somewhere.
        val verdict = TranscriptQualityValidator.validate(
            raw = "Uh, John said he would handle the deployment.",
            cleaned = "John said he would handle the deployment."
        )
        assertTrue(verdict.reason, verdict.accepted)
    }

    // --- Mode-aware thresholds: the same length ratio is accepted or rejected depending on
    // which TranscriptCleanupProfile (recording type x mode) it's validated against ---

    private fun profile(mode: TranscriptCleanupMode) = RecordingType.MEETING.transcriptCleanupProfile(mode)

    @Test
    fun `a moderately restructured candidate is rejected under Conservative but accepted under Moderate`() {
        val raw = "I think what I want to do is, what I'm thinking is basically that we should probably use the smaller model here."
        val cleaned = "I think we should use the smaller model here."

        val conservative = TranscriptQualityValidator.validate(raw, cleaned, profile(TranscriptCleanupMode.CONSERVATIVE))
        assertFalse(conservative.reason, conservative.accepted)

        val moderate = TranscriptQualityValidator.validate(raw, cleaned, profile(TranscriptCleanupMode.MODERATE))
        assertTrue(moderate.reason, moderate.accepted)
    }

    @Test
    fun `a heavily restructured candidate is rejected under Conservative and Moderate but accepted under Aggressive`() {
        val raw = "So, um, what I think I want to say is, basically, I think we should probably use the smaller model, if that makes sense."
        val cleaned = "We should use the smaller model."

        val conservative = TranscriptQualityValidator.validate(raw, cleaned, profile(TranscriptCleanupMode.CONSERVATIVE))
        assertFalse(conservative.accepted)

        val moderate = TranscriptQualityValidator.validate(raw, cleaned, profile(TranscriptCleanupMode.MODERATE))
        assertFalse(moderate.accepted)

        val aggressive = TranscriptQualityValidator.validate(raw, cleaned, profile(TranscriptCleanupMode.AGGRESSIVE))
        assertTrue(aggressive.reason, aggressive.accepted)
    }

    @Test
    fun `null profile behaves identically to an explicit Conservative profile`() {
        val raw = "I think what I want to do is, what I'm thinking is basically that we should probably use the smaller model here."
        val cleaned = "I think we should use the smaller model here."

        val default = TranscriptQualityValidator.validate(raw, cleaned)
        val conservative = TranscriptQualityValidator.validate(raw, cleaned, profile(TranscriptCleanupMode.CONSERVATIVE))
        assertEquals(conservative.accepted, default.accepted)
    }

    // --- Numbers/dates/money are hard-rejected in every mode, including Aggressive ---

    @Test
    fun `a changed dollar amount is rejected even under Aggressive mode`() {
        val verdict = TranscriptQualityValidator.validate(
            raw = "We agreed on a budget of \$15,000 for this quarter.",
            cleaned = "We agreed on a budget of \$50,000 for this quarter.",
            profile = profile(TranscriptCleanupMode.AGGRESSIVE)
        )
        assertFalse(verdict.accepted)
    }

    @Test
    fun `a changed day of the week is rejected even under Aggressive mode`() {
        val verdict = TranscriptQualityValidator.validate(
            raw = "Let's meet again on Monday to finalize things.",
            cleaned = "Let's meet again on Friday to finalize things.",
            profile = profile(TranscriptCleanupMode.AGGRESSIVE)
        )
        assertFalse(verdict.accepted)
    }

    @Test
    fun `a swapped name is rejected even under Aggressive mode`() {
        val verdict = TranscriptQualityValidator.validate(
            raw = "John said he would handle the deployment.",
            cleaned = "Peter said he would handle the deployment.",
            profile = profile(TranscriptCleanupMode.AGGRESSIVE)
        )
        assertFalse(verdict.accepted)
    }

    @Test
    fun `a changed vehicle count is rejected even under Aggressive mode`() {
        val verdict = TranscriptQualityValidator.validate(
            raw = "We have three vehicles available for the trip.",
            cleaned = "We have five vehicles available for the trip.",
            profile = profile(TranscriptCleanupMode.AGGRESSIVE)
        )
        assertFalse(verdict.accepted)
    }

    // --- Self-correction / variant-spelling evidence exception ---

    @Test
    fun `a self-corrected entity name is accepted when the raw text contains both variants`() {
        // The default (no profile) thresholds are the strictest in the system — proving this
        // passes here shows the self-correction exception works even without a permissive mode.
        val verdict = TranscriptQualityValidator.validate(
            raw = "The company name is Myavana no Myavanna actually Myavana.",
            cleaned = "The company name is Myavana."
        )
        assertTrue(verdict.reason, verdict.accepted)
    }

    @Test
    fun `an unrelated model name swap is still rejected despite the self-correction exception existing`() {
        // Guards against the self-correction exception being too permissive: "Qwen" and "Gemini"
        // share no meaningful prefix, so this must not be mistaken for a self-correction.
        val verdict = TranscriptQualityValidator.validate(
            raw = "We're running this on Qwen for now.",
            cleaned = "We're running this on Gemini for now."
        )
        assertFalse(verdict.accepted)
    }
}
