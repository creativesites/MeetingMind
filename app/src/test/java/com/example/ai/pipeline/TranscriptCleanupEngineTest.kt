package com.example.ai.pipeline

import com.example.core.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [RuleBasedTranscriptCleanupEngine] is a thin, honest wrapper over the already-tested
 * [com.example.core.common.FillerWordCleaner] — these tests pin down the wrapper's own contract
 * (delegates correctly, never fabricates output for blank input) rather than re-testing
 * FillerWordCleaner's cleaning rules themselves.
 */
class TranscriptCleanupEngineTest {

    private val engine = RuleBasedTranscriptCleanupEngine()

    private fun segment(text: String, isUserEdited: Boolean = false) = TranscriptSegment(
        id = "s1", meetingId = "m1", startMs = 0L, endMs = 1000L, text = text, isUserEdited = isUserEdited
    )

    @Test
    fun `delegates to FillerWordCleaner for real content`() {
        val result = engine.clean(segment("Uh, so, I think this works."))
        assertEquals(com.example.core.common.FillerWordCleaner.clean("Uh, so, I think this works."), result)
    }

    @Test
    fun `returns null for blank input rather than fabricating a candidate`() {
        assertNull(engine.clean(segment("")))
        assertNull(engine.clean(segment("   ")))
    }

    @Test
    fun `text with nothing to clean is returned unchanged`() {
        val result = engine.clean(segment("This sentence has no filler words at all."))
        assertEquals("This sentence has no filler words at all.", result)
    }

    // --- applyTranscriptCleanup: the pipeline-facing entry point ---

    @Test
    fun `a validator-accepted candidate is attached as cleanedText`() {
        val result = applyTranscriptCleanup(listOf(segment("Uh, I think this works.")), engine)
        assertEquals("I think this works.", result[0].cleanedText)
    }

    @Test
    fun `a user-edited segment is never offered to the cleanup engine at all`() {
        val edited = segment("Uh, this text was hand-corrected by the user.", isUserEdited = true)
        val result = applyTranscriptCleanup(listOf(edited), engine)
        // cleanedText stays null (never generated), and the user's own text is completely untouched.
        assertEquals(null, result[0].cleanedText)
        assertEquals(edited.text, result[0].text)
    }

    @Test
    fun `a candidate the quality validator rejects is discarded, not persisted as cleanedText`() {
        val dangerousEngine = object : TranscriptCleanupEngine {
            override fun clean(segment: TranscriptSegment) = "Completely unrelated fabricated sentence here."
        }
        val result = applyTranscriptCleanup(listOf(segment("We agreed on a budget of \$15,000.")), dangerousEngine)
        assertEquals(null, result[0].cleanedText)
    }
}
