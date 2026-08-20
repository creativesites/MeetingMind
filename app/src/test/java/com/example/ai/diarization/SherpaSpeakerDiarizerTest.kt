package com.example.ai.diarization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.common.AiResult
import com.example.ai.modelmanagement.LocalModelStorage
import com.example.core.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * [SherpaSpeakerDiarizer] has two independently-verifiable halves:
 *
 * 1. With no diarization model installed (this test's Robolectric environment, matching a fresh
 *    app install), it must report [AiResult.ModelUnavailable] without ever touching a
 *    sherpa-onnx native class — which is also what makes this half testable on the JVM at all.
 * 2. [reconcileTranscriptWithSpeakers] — the pure timestamp-overlap logic that turns raw
 *    (start, end, speakerIndex) diarization output into speaker-labeled transcript segments —
 *    has no sherpa-onnx dependency whatsoever and is fully unit-testable with synthetic data.
 *    Real acoustic diarization accuracy itself is NOT covered here — see
 *    docs/AI_ARCHITECTURE.md "Known Limitations" for what remains device-only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SherpaSpeakerDiarizerTest {

    @Test
    fun `reports model unavailable when no diarization model is installed`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val diarizer = SherpaSpeakerDiarizer(LocalModelStorage(context))
        val segments = listOf(
            TranscriptSegment(id = "s1", meetingId = "m1", startMs = 0L, endMs = 1000L, text = "Hello")
        )

        val result = diarizer.diarize(File(context.cacheDir, "does-not-matter.m4a"), 5000L, segments)

        assertTrue(result is AiResult.ModelUnavailable)
    }

    @Test
    fun `empty transcript segments short-circuit without requiring a model`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        // No model installed either — proves the empty-segments short-circuit happens first.
        val diarizer = SherpaSpeakerDiarizer(LocalModelStorage(context))

        val result = diarizer.diarize(File(context.cacheDir, "does-not-matter.m4a"), 5000L, emptyList())

        assertTrue(result is AiResult.Success)
        assertTrue((result as AiResult.Success).value.isEmpty())
    }

    @Test
    fun `reconciliation assigns the speaker with the greatest timestamp overlap`() {
        val segments = listOf(
            TranscriptSegment(id = "seg1", meetingId = "m1", startMs = 0L, endMs = 2000L, text = "First"),
            TranscriptSegment(id = "seg2", meetingId = "m1", startMs = 2000L, endMs = 4000L, text = "Second")
        )
        val speakerSegments = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 2000L, speakerIndex = 0),
            RawSpeakerSegment(startMs = 2000L, endMs = 4000L, speakerIndex = 1)
        )

        val result = reconcileTranscriptWithSpeakers(segments, speakerSegments)

        assertEquals("spk_m1_0", result[0].speakerId)
        assertEquals("Speaker 1", result[0].speakerName)
        assertEquals("spk_m1_1", result[1].speakerId)
        assertEquals("Speaker 2", result[1].speakerName)
    }

    @Test
    fun `reconciliation picks the speaker interval with the largest overlap, not the first one`() {
        // seg spans 0-3000ms: speaker 0 covers only 0-500ms (500ms overlap), speaker 1 covers
        // 500-3000ms (2500ms overlap) — speaker 1 must win despite appearing second.
        val segments = listOf(TranscriptSegment(id = "seg1", meetingId = "m1", startMs = 0L, endMs = 3000L, text = "Mixed"))
        val speakerSegments = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 500L, speakerIndex = 0),
            RawSpeakerSegment(startMs = 500L, endMs = 3000L, speakerIndex = 1)
        )

        val result = reconcileTranscriptWithSpeakers(segments, speakerSegments)

        assertEquals("spk_m1_1", result[0].speakerId)
    }

    @Test
    fun `a segment with no overlapping speaker interval is left with no speaker rather than a guess`() {
        val segments = listOf(TranscriptSegment(id = "seg1", meetingId = "m1", startMs = 10_000L, endMs = 11_000L, text = "Later"))
        val speakerSegments = listOf(RawSpeakerSegment(startMs = 0L, endMs = 2000L, speakerIndex = 0))

        val result = reconcileTranscriptWithSpeakers(segments, speakerSegments)

        assertNull(result[0].speakerId)
        assertNull(result[0].speakerName)
    }

    @Test
    fun `no diarization output at all leaves segments completely unmodified`() {
        val segments = listOf(TranscriptSegment(id = "seg1", meetingId = "m1", startMs = 0L, endMs = 1000L, text = "Alone"))

        val result = reconcileTranscriptWithSpeakers(segments, emptyList())

        assertEquals(segments, result)
    }

    @Test
    fun `speaker identity is stable across repeated reconciliation of the same input`() {
        val segments = listOf(TranscriptSegment(id = "seg1", meetingId = "m1", startMs = 0L, endMs = 1000L, text = "Stable"))
        val speakerSegments = listOf(RawSpeakerSegment(startMs = 0L, endMs = 1000L, speakerIndex = 2))

        val first = reconcileTranscriptWithSpeakers(segments, speakerSegments)
        val second = reconcileTranscriptWithSpeakers(segments, speakerSegments)

        assertEquals(first[0].speakerId, second[0].speakerId)
        assertEquals("spk_m1_2", first[0].speakerId)
    }

    // --- buildClusteringConfig: fixed speaker count vs. conservative Auto mode ---

    @Test
    fun `a fixed speaker count is passed through as numClusters`() {
        val config = buildClusteringConfig(3)
        assertEquals(3, config.numClusters)
    }

    @Test
    fun `null speaker count (Auto) requests -1 clusters and the conservative threshold`() {
        val config = buildClusteringConfig(null)
        assertEquals(-1, config.numClusters)
        assertEquals(DIARIZATION_CLUSTERING_THRESHOLD, config.threshold)
    }

    @Test
    fun `Auto mode threshold is more conservative than sherpa-onnx's own library default`() {
        // The library's own default is 0.5f; a HIGHER threshold merges more aggressively (fewer,
        // larger clusters) since cutree_cdist merges anything within `threshold` cosine
        // dissimilarity. Going below 0.5f here would silently reintroduce the speaker-explosion
        // bug this constant exists to fix.
        assertTrue(DIARIZATION_CLUSTERING_THRESHOLD > 0.5f)
    }

    @Test
    fun `a zero or negative speaker count is treated as Auto, not a literal cluster count`() {
        assertEquals(-1, buildClusteringConfig(0).numClusters)
        assertEquals(-1, buildClusteringConfig(-5).numClusters)
    }

    // --- mergeShortSandwichedFragments: fragment reconciliation without fabricating identity ---

    @Test
    fun `two real speakers with no short fragments are left as two speakers`() {
        val segments = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 3000L, speakerIndex = 0),
            RawSpeakerSegment(startMs = 3000L, endMs = 6000L, speakerIndex = 1),
            RawSpeakerSegment(startMs = 6000L, endMs = 9000L, speakerIndex = 0)
        )

        val result = mergeShortSandwichedFragments(segments)

        assertEquals(segments, result)
    }

    @Test
    fun `a short fragment sandwiched by the same speaker on both sides is merged into that speaker`() {
        // The exact scenario from the product spec: Speaker 1 talks, a 300ms fragment gets
        // misclassified as Speaker 3, then Speaker 1 resumes.
        val segments = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 2000L, speakerIndex = 1),
            RawSpeakerSegment(startMs = 2000L, endMs = 2300L, speakerIndex = 3),
            RawSpeakerSegment(startMs = 2300L, endMs = 5000L, speakerIndex = 1)
        )

        val result = mergeShortSandwichedFragments(segments)

        assertEquals(listOf(1, 1, 1), result.map { it.speakerIndex })
    }

    @Test
    fun `a short fragment between two DIFFERENT surrounding speakers is left alone, not fabricated away`() {
        val segments = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 2000L, speakerIndex = 0),
            RawSpeakerSegment(startMs = 2000L, endMs = 2300L, speakerIndex = 2),
            RawSpeakerSegment(startMs = 2300L, endMs = 5000L, speakerIndex = 1)
        )

        val result = mergeShortSandwichedFragments(segments)

        // No basis to guess which neighbor (if either) the fragment really belongs to, so it's
        // preserved as its own uncertain segment rather than merged into either one.
        assertEquals(listOf(0, 2, 1), result.map { it.speakerIndex })
    }

    @Test
    fun `a long segment between two same-speaker segments is NOT merged, only short fragments are`() {
        val segments = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 2000L, speakerIndex = 0),
            // 5 seconds — a real, sustained turn, not a fragment.
            RawSpeakerSegment(startMs = 2000L, endMs = 7000L, speakerIndex = 1),
            RawSpeakerSegment(startMs = 7000L, endMs = 9000L, speakerIndex = 0)
        )

        val result = mergeShortSandwichedFragments(segments)

        assertEquals(listOf(0, 1, 0), result.map { it.speakerIndex })
    }

    @Test
    fun `the first and last segments are never merged, even if short, since they have no two-sided sandwich`() {
        val segments = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 200L, speakerIndex = 5),
            RawSpeakerSegment(startMs = 200L, endMs = 3000L, speakerIndex = 0),
            RawSpeakerSegment(startMs = 3000L, endMs = 3200L, speakerIndex = 6)
        )

        val result = mergeShortSandwichedFragments(segments)

        assertEquals(listOf(5, 0, 6), result.map { it.speakerIndex })
    }

    @Test
    fun `fewer than three segments cannot be sandwiched and are returned unchanged`() {
        val segments = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 200L, speakerIndex = 0),
            RawSpeakerSegment(startMs = 200L, endMs = 400L, speakerIndex = 1)
        )

        assertEquals(segments, mergeShortSandwichedFragments(segments))
    }
}
