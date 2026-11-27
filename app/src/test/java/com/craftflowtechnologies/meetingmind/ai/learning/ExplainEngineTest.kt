package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainMode
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ExplainEngineTest {

    private class Canned(val reply: AiResult<String>) : LanguageModel {
        var lastPrompt = ""
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
            lastPrompt = prompt
            return reply
        }
    }

    private val segments = listOf(
        TranscriptSegment(id = "s1", meetingId = "m1", startMs = 842_000L, endMs = 850_000L,
            text = "Competitive inhibitors bind to the active site."),
        TranscriptSegment(id = "s2", meetingId = "m1", startMs = 1_000_000L, endMs = 1_008_000L,
            text = "Adding more substrate overcomes the inhibition.")
    )

    private fun concept(evidence: List<LearningEvidence>) = LearningConcept(
        id = "c1", sessionId = "sess", name = "Competitive inhibition",
        definition = "An inhibitor that competes for the active site.",
        evidence = evidence, createdAt = 0L, updatedAt = 0L
    )

    private val evidence = listOf(
        LearningEvidence(meetingId = "m1", segmentIds = listOf("s1"), startMs = 842_000L, endMs = 850_000L,
            quote = "Competitive inhibitors bind to the active site."),
        LearningEvidence(meetingId = "m1", segmentIds = listOf("s2"), startMs = 1_000_000L, endMs = 1_008_000L,
            quote = "Adding more substrate overcomes the inhibition.")
    )

    @Test
    fun `source label names the lecture moments the explanation came from`() {
        assertEquals("14:02 and 16:40", ExplainEngine.sourceLabel(evidence))
        assertNull(ExplainEngine.sourceLabel(emptyList()))
        assertEquals("14:02", ExplainEngine.sourceLabel(listOf(evidence[0])))
    }

    @Test
    fun `a used-up allowance is reported as the allowance, not as an error`() {
        val model = Canned(AiResult.Failed("This month's cloud allowance is used up.", retryable = false))
        val state = runBlocking { ExplainEngine(model).explain(concept(evidence), ExplainMode.SIMPLER, segments) }
        assertTrue(state.allowance != null)
        assertNull(state.error)
        assertNull(state.text)
    }

    @Test
    fun `a concept with no lecture evidence is not explained`() {
        val model = Canned(AiResult.Success("{\"text\": \"Made up.\"}"))
        val state = runBlocking { ExplainEngine(model).explain(concept(emptyList()), ExplainMode.SIMPLER, segments) }
        assertNotNull(state.error)
        assertEquals("", model.lastPrompt)
        assertNull(state.text)
    }

    @Test
    fun `a text explanation is grounded in the evidence and labelled with its moments`() {
        val model = Canned(AiResult.Success("{\"text\": \"Inhibitors compete with substrate for the same spot.\"}"))
        val state = runBlocking { ExplainEngine(model).explain(concept(evidence), ExplainMode.SIMPLER, segments) }
        assertEquals("Inhibitors compete with substrate for the same spot.", state.text)
        assertEquals("14:02 and 16:40", state.sourceLabel)
        assertTrue(model.lastPrompt.contains("QUOTE: Competitive inhibitors bind to the active site."))
        assertTrue(model.lastPrompt.contains("[842000] Competitive inhibitors"))
    }

    @Test
    fun `a diagram has at most six boxes and arrows must point at boxes that exist`() {
        val ok = JSONObject(
            """{"nodes": ["Inhibitor", "Active site", "Blocked"], "edges": [{"from": 0, "to": 1, "label": "binds"}, {"from": 1, "to": 2}]}"""
        )
        val spec = ExplainEngine.parseDiagram(ok)
        assertNotNull(spec)
        assertEquals(3, spec!!.nodes.size)
        assertEquals("binds", spec.edges[0].label)

        val tooMany = JSONObject(
            """{"nodes": ["a","b","c","d","e","f","g"], "edges": [{"from": 0, "to": 1}]}"""
        )
        assertNull(ExplainEngine.parseDiagram(tooMany))

        val badEdge = JSONObject("""{"nodes": ["a","b"], "edges": [{"from": 0, "to": 5}]}""")
        assertNull(ExplainEngine.parseDiagram(badEdge))

        val selfLoop = JSONObject("""{"nodes": ["a","b"], "edges": [{"from": 1, "to": 1}]}""")
        assertNull(ExplainEngine.parseDiagram(selfLoop))
    }

    @Test
    fun `a draw request returns a diagram from the model's reply`() {
        val model = Canned(
            AiResult.Success(
                """Here you go: {"nodes": ["Enzyme", "Inhibitor", "Substrate"], "edges": [{"from": 1, "to": 0, "label": "blocks"}], "caption": "Blocking"}"""
            )
        )
        val state = runBlocking { ExplainEngine(model).explain(concept(evidence), ExplainMode.DRAW, segments) }
        assertNotNull(state.diagram)
        assertEquals("Blocking", state.diagram!!.caption)
        assertEquals("14:02 and 16:40", state.sourceLabel)
    }

    @Test
    fun `a model failure is shown as an error, not as an explanation`() {
        val model = Canned(AiResult.ModelUnavailable("local-llm", "No local language model is installed on this device."))
        val state = runBlocking { ExplainEngine(model).explain(concept(evidence), ExplainMode.ANALOGY, segments) }
        assertEquals("No local language model is installed on this device.", state.error)
        assertNull(state.text)
    }
}
