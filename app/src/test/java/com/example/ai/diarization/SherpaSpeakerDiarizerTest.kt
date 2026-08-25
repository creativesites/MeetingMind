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

    // --- analyzeSpeakerFragmentation / reconcileFragmentedSpeakers: the "5-speaker but really
    // 2" fingerprint from the product spec, without falsely flagging real multi-speaker output ---

    @Test
    fun `flags the exact spec example - two dominant speakers plus three near-silent noise speakers`() {
        // Speaker 0: 42%, Speaker 4: 52%, speakers 1-3: 3%+2%+1% = 6% combined — the product
        // spec's own illustrative "may be legitimate, may be fragmentation" case, which really
        // is fragmentation once you look at the distribution rather than the raw cluster count.
        val segments = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 4200L, speakerIndex = 0),
            RawSpeakerSegment(startMs = 4200L, endMs = 4500L, speakerIndex = 1),
            RawSpeakerSegment(startMs = 4500L, endMs = 4700L, speakerIndex = 2),
            RawSpeakerSegment(startMs = 4700L, endMs = 4800L, speakerIndex = 3),
            RawSpeakerSegment(startMs = 4800L, endMs = 10000L, speakerIndex = 4)
        )

        val flagged = analyzeSpeakerFragmentation(segments)

        assertEquals(setOf(1, 2, 3), flagged)
    }

    @Test
    fun `reconciliation reassigns flagged noise speakers to their nearest real neighbor in time`() {
        // Two real speakers of deliberately different lengths (so their midpoints land far apart
        // and each noise fragment has an unambiguous nearest neighbor), with one noise fragment
        // bordering each side.
        val segments = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 2000L, speakerIndex = 0),        // real, mid=1000
            RawSpeakerSegment(startMs = 2000L, endMs = 2100L, speakerIndex = 1),     // noise, nearer to 0
            RawSpeakerSegment(startMs = 2100L, endMs = 20100L, speakerIndex = 2),    // real, mid=11100
            RawSpeakerSegment(startMs = 20100L, endMs = 20200L, speakerIndex = 3)    // noise, nearer to 2
        )
        val flagged = analyzeSpeakerFragmentation(segments)
        assertEquals(setOf(1, 3), flagged)

        val result = reconcileFragmentedSpeakers(segments, flagged)

        assertEquals(listOf(0, 0, 2, 2), result.map { it.speakerIndex })
    }

    @Test
    fun `two genuinely balanced speakers are never flagged`() {
        val segments = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 5000L, speakerIndex = 0),
            RawSpeakerSegment(startMs = 5000L, endMs = 10000L, speakerIndex = 1)
        )

        assertEquals(emptySet<Int>(), analyzeSpeakerFragmentation(segments))
    }

    @Test
    fun `a single small but real minority speaker is not flagged alone`() {
        // Only ONE speaker index falls under the noise-share threshold — MIN_NOISE_SPEAKERS_TO_FLAG
        // requires at least two before anything is second-guessed, since a lone quiet participant
        // is exactly the kind of real speaker diarization exists to catch.
        val segments = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 9000L, speakerIndex = 0),
            RawSpeakerSegment(startMs = 9000L, endMs = 9500L, speakerIndex = 1)
        )

        assertEquals(emptySet<Int>(), analyzeSpeakerFragmentation(segments))
    }

    @Test
    fun `several genuinely balanced speakers (a real small meeting) are never flagged`() {
        val segments = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 3000L, speakerIndex = 0),
            RawSpeakerSegment(startMs = 3000L, endMs = 6000L, speakerIndex = 1),
            RawSpeakerSegment(startMs = 6000L, endMs = 9000L, speakerIndex = 2),
            RawSpeakerSegment(startMs = 9000L, endMs = 12000L, speakerIndex = 3)
        )

        assertEquals(emptySet<Int>(), analyzeSpeakerFragmentation(segments))
    }

    @Test
    fun `a single speaker throughout is never flagged - nothing to compare it against`() {
        val segments = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 1000L, speakerIndex = 0),
            RawSpeakerSegment(startMs = 1000L, endMs = 2000L, speakerIndex = 0)
        )

        assertEquals(emptySet<Int>(), analyzeSpeakerFragmentation(segments))
    }

    @Test
    fun `empty input is never flagged`() {
        assertEquals(emptySet<Int>(), analyzeSpeakerFragmentation(emptyList()))
    }

    @Test
    fun `reconciliation is a no-op when nothing was flagged`() {
        val segments = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 5000L, speakerIndex = 0),
            RawSpeakerSegment(startMs = 5000L, endMs = 10000L, speakerIndex = 1)
        )

        assertEquals(segments, reconcileFragmentedSpeakers(segments, emptySet()))
    }

    // --- analyzeSpeakerFragmentation: turn-count/alternation signal (Stage B4) ---

    @Test
    fun `a speaker with many very short turns is flagged even when its share alone would not trigger`() {
        // Speaker 1: 9% of total duration (above NOISE_SHARE_THRESHOLD, so share alone would NOT
        // flag it) but spread across 40 turns averaging 450ms each — the "chattering" clustering-
        // flicker fingerprint. Speaker 2 is a genuine low-share noise speaker (3.5%) so
        // MIN_NOISE_SPEAKERS_TO_FLAG is met without changing what's being tested about speaker 1.
        val speaker0 = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 87_500L, speakerIndex = 0),
            RawSpeakerSegment(startMs = 87_500L, endMs = 175_000L, speakerIndex = 0)
        )
        val speaker1 = (0 until 40).map { i ->
            RawSpeakerSegment(startMs = 200_000L + i * 450L, endMs = 200_000L + (i + 1) * 450L, speakerIndex = 1)
        }
        val speaker2 = (0 until 20).map { i ->
            RawSpeakerSegment(startMs = 300_000L + i * 350L, endMs = 300_000L + (i + 1) * 350L, speakerIndex = 2)
        }

        val flagged = analyzeSpeakerFragmentation(speaker0 + speaker1 + speaker2)

        assertEquals(setOf(1, 2), flagged)
    }

    @Test
    fun `a real speaker at 28 percent of total audio is never flagged, even in many short bursts`() {
        // Speaker 1 legitimately talks in quick bursts (60 short turns, avg 466ms — well under
        // AVG_TURN_NOISE_MS, so the alternation signal alone WOULD mark it a candidate) but still
        // accounts for 28% of the whole recording. Speaker 2 is a genuine tiny noise speaker (5%)
        // so MIN_NOISE_SPEAKERS_TO_FLAG is reached and the share-sum gate is actually exercised —
        // together candidates would total 33%, over MAX_NOISE_SHARE_WHEN_FLAGGING, so NEITHER is
        // reassigned. A real, if bursty, participant must never be silently removed this way.
        val speaker0 = listOf(
            RawSpeakerSegment(startMs = 0L, endMs = 33_500L, speakerIndex = 0),
            RawSpeakerSegment(startMs = 33_500L, endMs = 67_000L, speakerIndex = 0)
        )
        val speaker1 = (0 until 60).map { i ->
            RawSpeakerSegment(startMs = 100_000L + i * 466L, endMs = 100_000L + (i + 1) * 466L, speakerIndex = 1)
        }
        val speaker2 = listOf(
            RawSpeakerSegment(startMs = 300_000L, endMs = 301_667L, speakerIndex = 2),
            RawSpeakerSegment(startMs = 301_667L, endMs = 303_333L, speakerIndex = 2),
            RawSpeakerSegment(startMs = 303_333L, endMs = 305_000L, speakerIndex = 2)
        )

        val flagged = analyzeSpeakerFragmentation(speaker0 + speaker1 + speaker2)

        assertEquals(emptySet<Int>(), flagged)
    }
}
