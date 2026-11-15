package com.craftflowtechnologies.meetingmind.ai.benchmark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantTool
import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantToolStep
import com.craftflowtechnologies.meetingmind.ai.cloud.AiCallMetric
import com.craftflowtechnologies.meetingmind.ai.cloud.AiTelemetry
import com.craftflowtechnologies.meetingmind.ai.embeddings.LocalEmbeddingEngine
import com.craftflowtechnologies.meetingmind.ai.llm.AiCoreLanguageModel
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.retrieval.TranscriptRetriever
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntelligenceBenchmarkTest {

    @Test
    fun citationSchemaFaithAndToolMetricsScoreDeterministically() = runBlocking {
        val cScore = IntelligenceBenchmark.scoreCitations(
            "Launch stays on Friday [01:20] and owner is Sam [99:99].",
            setOf("01:20")
        )
        assertEquals(2, cScore.citedCount)
        assertEquals(1, cScore.validCount)
        assertEquals(0.5, cScore.precision, 0.001)

        assertTrue(IntelligenceBenchmark.validateJsonSchema("""{"summary":"ok","actionItems":[]}""", setOf("summary", "actionItems")))
        assertFalse(IntelligenceBenchmark.validateJsonSchema("""{"summary":"ok"}""", setOf("summary", "actionItems")))

        assertTrue(
            IntelligenceBenchmark.verifyFaithGuard(
                unsafeInputs = listOf("God is telling you to quit today. Rest first."),
                safeQuotedInputs = listOf("Pastor Sam said in the sermon that God told Paul to wait.")
            )
        )

        val steps = listOf(
            AssistantToolStep("read_note", "Reading note", AssistantToolStep.State.DONE),
            AssistantToolStep("get_verses", "Fetching verses", AssistantToolStep.State.DONE)
        )
        val traj = IntelligenceBenchmark.scoreToolTrajectory(listOf("read_note", "get_verses"), steps)
        assertEquals(1.0, traj.recall, 0.001)
        assertEquals(1.0, traj.completionRate, 0.001)

        val segments = listOf(
            TranscriptSegment("s1", "m1", "spk1", "Alice", 0L, 4000L, "We decided to defer pricing until Q4."),
            TranscriptSegment("s2", "m1", "spk2", "Bob", 5000L, 9000L, "Let us double-check server memory limits.")
        )
        val recall = IntelligenceBenchmark.evaluateRetrievalRecall(
            TranscriptRetriever(LocalEmbeddingEngine(), topK = 1, includeNeighbours = false),
            segments,
            "What happened to pricing?",
            setOf("s1")
        )
        assertEquals(1.0, recall, 0.001)
    }

    @Test
    fun aiCoreUnavailableGracefullyReportsModelUnavailable() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val result = AiCoreLanguageModel(context).generate("Summarise this.")
        assertTrue(result is AiResult.ModelUnavailable)
    }

    @Test
    fun telemetryRecordsAndSummarizesTtft() {
        AiTelemetry.clear()
        AiTelemetry.record(AiCallMetric(1000L, "Gemini", "gemini-3.8-flash", streamed = true, ttftMs = 240L, durationMs = 950L, promptTokens = 100, cachedTokens = 60, outputTokens = 20, success = true))
        AiTelemetry.record(AiCallMetric(2000L, "Gemini", "gemini-3.8-flash", streamed = true, ttftMs = 160L, durationMs = 700L, promptTokens = 100, cachedTokens = 0, outputTokens = 20, success = true))
        assertEquals(200L, AiTelemetry.averageTtftMs())
        assertEquals(2, AiTelemetry.recent().size)
    }
}
