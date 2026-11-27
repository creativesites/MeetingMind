package com.craftflowtechnologies.meetingmind.ai.synthesis

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeepSynthesisEngineTest {

    private class FakeModel(private val response: String) : LanguageModel {
        var lastPrompt: String = ""
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
            lastPrompt = prompt
            return AiResult.Success(response)
        }
    }

    private fun sampleSources(): List<DeepSynthesisSource> = listOf(
        DeepSynthesisSource(
            id = "s1",
            title = "Q4 Architecture Review",
            dateLabel = "15 Nov",
            kindLabel = "Current recording",
            snippet = "Alice confirmed v2 migration slips to Dec 5. Bob still owns load testing.",
            meetingId = "m1",
            noteId = "n1",
            wordCount = 14
        ),
        DeepSynthesisSource(
            id = "s2",
            title = "Q4 Kickoff",
            dateLabel = "1 Nov",
            kindLabel = "Same notebook",
            snippet = "Original target for v2 migration was Nov 20. Bob committed to finish load tests.",
            meetingId = "m2",
            noteId = "n2",
            wordCount = 15
        )
    )

    @Test
    fun `synthesizes cross-session dossier and drops hallucinated citations`() = runBlocking {
        val json = """
            ```json
            {
              "headline": "v2 migration pushed from Nov 20 to Dec 5",
              "executive_summary": "Across Q4 Kickoff and Review, launch moved back 15 days while load testing stayed open.",
              "evolution": [
                {"heading": "Launch Date Shifted", "detail": "Moved from Nov 20 to Dec 5.", "sources": ["s2", "s1"]},
                {"heading": "Hallucinated Entry", "detail": "Invented fact.", "sources": ["s99"]}
              ],
              "commitment_drift": [
                {"commitment": "Finish load tests", "owner": "Bob", "status_label": "Carried Over", "evidence": "Unfinished since Nov 1", "sources": ["s2", "s1"]}
              ],
              "blind_spots": [
                {"heading": "No rollback rehearsal scheduled", "detail": "Not assigned in either sync.", "sources": ["s1"]}
              ],
              "deliverable_title": "Executive Sync Update",
              "deliverable_markdown": "## Summary\n- Launch moved to Dec 5\n- Owner: Bob"
            }
            ```
        """.trimIndent()
        val model = FakeModel(json)
        val engine = DeepSynthesisEngine(model)

        val res = engine.synthesize(
            mode = DeepSynthesisMode.EXECUTIVE_PACK,
            anchorTitle = "Q4 Architecture Review",
            sources = sampleSources(),
            openTasks = listOf("Finish load tests (Bob)"),
            engineLabel = "On-device (Gemma)"
        )

        assertTrue(res is AiResult.Success)
        val dossier = (res as AiResult.Success).value
        assertEquals(DeepSynthesisMode.EXECUTIVE_PACK, dossier.mode)
        assertEquals("v2 migration pushed from Nov 20 to Dec 5", dossier.headline)
        assertEquals(1, dossier.evolution.size)
        assertEquals(listOf("s2", "s1"), dossier.evolution.single().sourceIds)
        assertEquals(1, dossier.commitmentDrift.size)
        assertEquals("Bob", dossier.commitmentDrift.single().owner)
        assertEquals(1, dossier.blindSpots.size)
        assertTrue(model.lastPrompt.contains("OPEN COMMITMENTS & TASKS IN WORKSPACE"))
    }

    @Test
    fun `fitToBudget always preserves anchor source under tight local context`() {
        val sources = listOf(
            sampleSources()[0].copy(snippet = "A".repeat(3_000)),
            sampleSources()[1].copy(snippet = "B".repeat(5_000)),
            sampleSources()[1].copy(id = "s3", snippet = "C".repeat(5_000))
        )
        val engine = DeepSynthesisEngine(FakeModel("{}"), contextBudgetChars = 4_000)
        val fitted = engine.fitToBudget(sources, 4_000)

        assertTrue(fitted.isNotEmpty())
        assertEquals("s1", fitted.first().id)
        assertTrue(fitted.size < sources.size)
    }

    @Test
    fun `all synthesis modes produce distinct directives`() {
        val engine = DeepSynthesisEngine(FakeModel("{}"))
        val prompts = DeepSynthesisMode.entries.map { mode ->
            engine.buildPrompt(mode, "Study Anchor", sampleSources(), emptyList())
        }.toSet()
        assertEquals(DeepSynthesisMode.entries.size, prompts.size)
    }
}
