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
}
