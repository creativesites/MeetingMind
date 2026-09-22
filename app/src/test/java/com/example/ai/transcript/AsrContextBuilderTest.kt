package com.example.ai.transcript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AsrContextBuilder] is the component that ends "a VAD boundary is a transcript boundary". These
 * tests pin the property that matters: a conversational pause must not split the audio the ASR
 * engine hears, while a genuine break in the recording still may.
 */
class AsrContextBuilderTest {

    private val config = AsrWindowConfig()

    @Test
    fun `a mid-sentence pause keeps both halves inside one decode window`() {
        // "I think we should move the deadline to Friday..." [900ms] "...because we still need to
        // finish the API integration." Under the old pipeline these were two ASR calls and two
        // transcript segments; the model never heard the second clause with the first in context.
        val regions = listOf(
            SpeechRegion(0L, 3_000L),
            SpeechRegion(3_900L, 7_000L)
        )

        val windows = AsrContextBuilder.buildWindows(regions, totalDurationMs = 10_000L, config = config)

        assertEquals(1, windows.size)
        assertTrue(windows[0].startMs <= 0L + config.paddingMs)
        assertTrue(windows[0].endMs >= 7_000L)
    }

    @Test
    fun `a genuine break in the recording does split windows`() {
        // A 30-second silence is not someone thinking mid-sentence; there is nothing to gain from
        // decoding across it, and plenty of silence to waste the model's attention on.
        val regions = listOf(
            SpeechRegion(0L, 3_000L),
            SpeechRegion(33_000L, 36_000L)
        )

        val windows = AsrContextBuilder.buildWindows(regions, totalDurationMs = 40_000L, config = config)

        assertEquals(2, windows.size)
    }

    @Test
    fun `the gap threshold is what decides, and it is configurable`() {
        val regions = listOf(SpeechRegion(0L, 2_000L), SpeechRegion(5_000L, 7_000L))

        val strict = AsrContextBuilder.buildWindows(regions, 10_000L, config.copy(maxInternalGapMs = 1_000L))
        val lenient = AsrContextBuilder.buildWindows(regions, 10_000L, config.copy(maxInternalGapMs = 4_000L))

        assertEquals("a 3s gap exceeds a 1s threshold", 2, strict.size)
        assertEquals("...and does not exceed a 4s one", 1, lenient.size)
    }

    @Test
    fun `a long continuous span is split into overlapping windows, never abutting ones`() {
        val regions = listOf(SpeechRegion(0L, 120_000L))

        val windows = AsrContextBuilder.buildWindows(regions, 120_000L, config)

        assertTrue("a two-minute monologue needs several windows", windows.size > 3)
        for (i in 0 until windows.size - 1) {
            val overlap = windows[i].endMs - windows[i + 1].startMs
            assertTrue(
                "window $i and ${i + 1} must share audio, got ${overlap}ms",
                overlap > 0
            )
        }
    }

    @Test
    fun `windows stay inside the recording and in chronological order`() {
        val regions = listOf(SpeechRegion(0L, 40_000L), SpeechRegion(45_000L, 90_000L))

        val windows = AsrContextBuilder.buildWindows(regions, 90_000L, config)

        assertTrue(windows.all { it.startMs >= 0L && it.endMs <= 90_000L })
        assertTrue(windows.all { it.endMs > it.startMs })
        assertEquals(windows.sortedBy { it.startMs }, windows)
        assertEquals(windows.indices.toList(), windows.map { it.index })
    }

    @Test
    fun `no VAD regions at all windows the whole recording rather than transcribing nothing`() {
        // A missing VAD model must degrade transcription quality, never silence the app.
        val windows = AsrContextBuilder.buildWindows(emptyList(), totalDurationMs = 50_000L, config = config)

        assertTrue(windows.isNotEmpty())
        assertEquals(0L, windows.first().startMs)
        assertEquals(50_000L, windows.last().endMs)
    }

    @Test
    fun `an empty recording produces no windows`() {
        assertTrue(AsrContextBuilder.buildWindows(emptyList(), 0L, config).isEmpty())
    }

    @Test
    fun `overlapping or out-of-order regions never produce a backwards span`() {
        val regions = listOf(
            SpeechRegion(5_000L, 9_000L),
            SpeechRegion(0L, 6_000L),
            SpeechRegion(2_000L, 3_000L)
        )

        val spans = AsrContextBuilder.mergeIntoSpans(regions, maxInternalGapMs = 500L)

        assertEquals(listOf(0L to 9_000L), spans)
    }

    @Test
    fun `a sliver of speech is folded into its neighbour instead of being decoded alone`() {
        // 120ms of audio gives the model essentially no context to work with.
        val regions = listOf(SpeechRegion(0L, 3_000L), SpeechRegion(60_000L, 60_120L))

        val windows = AsrContextBuilder.buildWindows(regions, 61_000L, config.copy(paddingMs = 0L))

        assertTrue("the sliver must not be its own window", windows.all { it.durationMs >= config.minWindowMs })
    }

    @Test
    fun `windows stay ordered and each covers new ground, whatever the span length`() {
        // The reconciler walks windows in order and assumes each one moves forward in time, so
        // this is an invariant the splitter owes it. Swept across span lengths, including the ones
        // that end just past a step boundary and leave a very short final window to be pulled back.
        for (extraMs in 0L..40_000L step 137L) {
            val spanEnd = config.targetWindowMs + (config.targetWindowMs - config.overlapMs) + extraMs
            val windows = AsrContextBuilder.buildWindows(
                listOf(SpeechRegion(0L, spanEnd)), spanEnd, config.copy(paddingMs = 0L)
            )

            assertEquals(
                "windows out of order for a span of ${spanEnd}ms",
                windows.sortedBy { it.startMs }, windows
            )
            assertTrue(
                "windows must each cover new ground for a span of ${spanEnd}ms",
                windows.zipWithNext().all { (a, b) -> b.startMs > a.startMs && b.endMs > a.endMs }
            )
        }
    }
}