package com.example.ai.tools

import com.example.ai.common.AiResult
import com.example.ai.llm.LanguageModel
import com.example.core.model.TranscriptAiToolType
import com.example.core.model.TranscriptSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tool engine's job is to be suspicious of what a model returns. These tests are mostly about
 * what it refuses: a rewrite that drops a number, a citation that does not resolve, a revision
 * aimed at a segment that was never in scope.
 *
 * Robolectric only so `org.json` is the real implementation rather than the JVM stub; the engine
 * has no Android dependency.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranscriptToolEngineTest {

    private fun segment(id: String, text: String, startMs: Long = 0L) = TranscriptSegment(
        id = id, meetingId = "m1", speakerId = "spk_0", speakerName = "Winston",
        startMs = startMs, endMs = startMs + 5_000L, text = text
    )

    private fun engine(response: String) = TranscriptToolEngine(
        languageModel = object : LanguageModel {
            override suspend fun generate(prompt: String, maxOutputTokens: Int) = AiResult.Success(response)
        },
        engineName = "test-model"
    )

    private fun failingEngine(result: AiResult<String>) = TranscriptToolEngine(
        languageModel = object : LanguageModel {
            override suspend fun generate(prompt: String, maxOutputTokens: Int) = result
        },
        engineName = "test-model"
    )

    // ── Revisions ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a sound revision is accepted`() = runBlocking {
        val segments = listOf(segment("s1", "we should um ship the thing on friday i think"))
        val response = """{"revisions":[{"segmentId":"s1","text":"We should ship the thing on Friday, I think."}]}"""

        val outcome = engine(response).run(TranscriptAiToolType.IMPROVE_CLARITY, segments)

        val revision = (outcome as AiResult.Success).value as ToolOutcome.TranscriptRevision
        assertEquals(1, revision.edits.size)
        assertEquals("We should ship the thing on Friday, I think.", revision.edits[0].after)
        assertEquals(0, revision.rejectedCount)
    }

    @Test
    fun `a revision that drops a number is rejected and counted, never shown`() = runBlocking {
        // The exact failure mode the fidelity validator exists for: a quantity silently changing
        // in something the user is about to accept.
        val segments = listOf(segment("s1", "we are raising fifteen million at a 60 million valuation"))
        val response = """{"revisions":[{"segmentId":"s1","text":"We are raising at a 60 million valuation."}]}"""

        val outcome = engine(response).run(TranscriptAiToolType.CONDENSE, segments)

        val revision = (outcome as AiResult.Success).value as ToolOutcome.TranscriptRevision
        assertTrue(revision.edits.isEmpty())
        assertEquals(1, revision.rejectedCount)
    }

    @Test
    fun `a revision aimed at a segment that was not in scope is discarded`() = runBlocking {
        // Applying it to "the nearest plausible segment" would edit text the user never selected.
        val segments = listOf(segment("s1", "the first paragraph of the meeting"))
        val response = """{"revisions":[{"segmentId":"s99","text":"Something else entirely here."}]}"""

        val outcome = engine(response).run(TranscriptAiToolType.IMPROVE_CLARITY, segments)

        assertTrue(((outcome as AiResult.Success).value as ToolOutcome.TranscriptRevision).edits.isEmpty())
    }

    @Test
    fun `a revision identical to the original is not offered as a change`() = runBlocking {
        val segments = listOf(segment("s1", "This sentence is already fine."))
        val response = """{"revisions":[{"segmentId":"s1","text":"This sentence is already fine."}]}"""

        val outcome = engine(response).run(TranscriptAiToolType.IMPROVE_CLARITY, segments)

        val revision = (outcome as AiResult.Success).value as ToolOutcome.TranscriptRevision
        assertTrue(revision.edits.isEmpty())
        assertEquals(0, revision.rejectedCount)
    }

    // ── Findings ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `findings keep citations that resolve and carry the earliest timestamp`() = runBlocking {
        val segments = listOf(
            segment("s1", "We agreed to ship on Friday.", startMs = 12_000L),
            segment("s2", "Sarah will tell the customers.", startMs = 30_000L)
        )
        val response = """
            {"findings":[
              {"text":"The team agreed a Friday ship date.","sourceSegmentIds":["s2","s1"]}
            ]}
        """.trimIndent()

        val outcome = engine(response).run(TranscriptAiToolType.EXTRACT_KEY_POINTS, segments)

        val findings = (outcome as AiResult.Success).value as ToolOutcome.Findings
        assertEquals(listOf("s2", "s1"), findings.items[0].sourceSegmentIds)
        assertEquals(12_000L, findings.items[0].startMs)
    }

    @Test
    fun `a citation that does not resolve is dropped, leaving the finding uncited not falsely cited`() = runBlocking {
        val segments = listOf(segment("s1", "We agreed to ship on Friday."))
        val response = """{"findings":[{"text":"Something was decided.","sourceSegmentIds":["s404"]}]}"""

        val outcome = engine(response).run(TranscriptAiToolType.EXTRACT_KEY_POINTS, segments)

        val finding = ((outcome as AiResult.Success).value as ToolOutcome.Findings).items.single()
        assertTrue(finding.sourceSegmentIds.isEmpty())
        assertEquals(null, finding.startMs)
    }

    @Test
    fun `an empty findings list is a valid result, not a failure`() = runBlocking {
        val segments = listOf(segment("s1", "Just some chat about the weather."))

        val outcome = engine("""{"findings":[]}""").run(TranscriptAiToolType.FIND_IMPORTANT_MOMENTS, segments)

        assertTrue(((outcome as AiResult.Success).value as ToolOutcome.Findings).items.isEmpty())
    }

    // ── Documents and titles ─────────────────────────────────────────────────────────────────

    @Test
    fun `a document result carries its markdown`() = runBlocking {
        val segments = listOf(segment("s1", "We discussed the roadmap."))
        val response = """{"markdown":"## Roadmap\n- Discussed"}"""

        val outcome = engine(response).run(TranscriptAiToolType.CREATE_NOTES, segments)

        assertTrue((((outcome as AiResult.Success).value) as ToolOutcome.TextDocument).markdown.contains("Roadmap"))
    }

    @Test
    fun `an empty document is a failure rather than an empty result sheet`() = runBlocking {
        val segments = listOf(segment("s1", "Anything."))

        val outcome = engine("""{"markdown":"   "}""").run(TranscriptAiToolType.CREATE_OUTLINE, segments)

        assertTrue(outcome is AiResult.Failed)
    }

    @Test
    fun `a suggested title goes through the same validation an automatic title does`() = runBlocking {
        val segments = listOf(segment("s1", "We reviewed the Series A narrative."))

        val good = engine("""{"title":"Series A narrative review"}""")
            .run(TranscriptAiToolType.GENERATE_TITLE, segments)
        assertEquals(
            "Series A narrative review",
            ((good as AiResult.Success).value as ToolOutcome.TitleSuggestion).title
        )

        val empty = engine("""{"title":"   "}""").run(TranscriptAiToolType.GENERATE_TITLE, segments)
        assertTrue(empty is AiResult.Failed)
    }

    // ── Failure handling ─────────────────────────────────────────────────────────────────────

    @Test
    fun `an unreadable response fails rather than being salvaged into a half-result`() = runBlocking {
        val segments = listOf(segment("s1", "Anything."))

        val outcome = engine("I'm sorry, I can't do that.").run(TranscriptAiToolType.CREATE_NOTES, segments)

        assertTrue(outcome is AiResult.Failed)
    }

    @Test
    fun `JSON wrapped in a code fence or a sentence is still read`() = runBlocking {
        val segments = listOf(segment("s1", "We discussed the roadmap."))
        val response = "Here you go:\n```json\n{\"markdown\":\"## Notes\"}\n```"

        val outcome = engine(response).run(TranscriptAiToolType.CREATE_NOTES, segments)

        assertTrue(outcome is AiResult.Success)
    }

    @Test
    fun `an unavailable model is reported, never worked around`() = runBlocking {
        val segments = listOf(segment("s1", "Anything."))
        val unavailable = failingEngine(AiResult.ModelUnavailable("local-llm", "No model installed."))

        val outcome = unavailable.run(TranscriptAiToolType.CREATE_NOTES, segments)

        assertTrue((outcome as AiResult.Failed).message.contains("No model installed."))
    }

    @Test
    fun `an empty scope fails with something the user can act on`() = runBlocking {
        val outcome = engine("{}").run(TranscriptAiToolType.CREATE_NOTES, emptyList())

        assertTrue((outcome as AiResult.Failed).message.contains("nothing in the selected part"))
    }

    // ── Prompt contract ──────────────────────────────────────────────────────────────────────

    @Test
    fun `every model-backed tool's prompt carries the fidelity contract and the segment ids`() {
        val segments = listOf(segment("s1", "We ship on Friday."), segment("s2", "Sounds good."))
        val modelBacked = TranscriptAiToolType.entries.filter {
            it.readiness == com.example.core.model.TranscriptAiToolReadiness.MODEL_BACKED
        }

        assertTrue("there should be model-backed tools to check", modelBacked.isNotEmpty())
        for (tool in modelBacked) {
            val prompt = TranscriptToolPrompts.build(tool, segments)
            assertTrue("${tool.name} lost the no-invention clause", prompt.contains("You MUST NOT"))
            assertTrue("${tool.name} lost the citation requirement", prompt.contains("cite the id"))
            assertTrue("${tool.name} did not include segment ids", prompt.contains("[s1]") && prompt.contains("[s2]"))
            assertTrue("${tool.name} did not state its own task", prompt.contains("Task:"))
        }
    }

    @Test
    fun `the fidelity contract is identical for every tool - no tool weakens it`() {
        val segments = listOf(segment("s1", "Anything."))
        val modelBacked = TranscriptAiToolType.entries.filter {
            it.readiness == com.example.core.model.TranscriptAiToolReadiness.MODEL_BACKED
        }

        for (tool in modelBacked) {
            assertTrue(
                "${tool.name} does not carry the shared contract verbatim",
                TranscriptToolPrompts.build(tool, segments).contains(TranscriptToolPrompts.FIDELITY_CONTRACT.trim())
            )
        }
    }
}
