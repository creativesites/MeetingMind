package com.craftflowtechnologies.meetingmind.ai.automation

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.tasks.TaskKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostSessionAutopilotTest {

    @Test fun `recommended recipe follows session workflow`() {
        assertEquals(AutopilotRecipe.SERMON_SMALL_GROUP_PACK, AutopilotRecipe.recommendedFor(RecordingType.SERMON))
        assertEquals(AutopilotRecipe.SERMON_SMALL_GROUP_PACK, AutopilotRecipe.recommendedFor(RecordingType.BIBLE_STUDY))
        assertEquals(AutopilotRecipe.STUDY_FLASHCARD_DRILL, AutopilotRecipe.recommendedFor(RecordingType.LECTURE))
        assertEquals(AutopilotRecipe.STUDY_FLASHCARD_DRILL, AutopilotRecipe.recommendedFor(RecordingType.RESEARCH))
        assertEquals(AutopilotRecipe.CLIENT_CRM_LOG, AutopilotRecipe.recommendedFor(RecordingType.MEETING))
        assertEquals(AutopilotRecipe.EXECUTIVE_DEBRIEF, AutopilotRecipe.recommendedFor(RecordingType.GENERAL))
    }

    @Test fun `executes workflow cleanly via local or cloud LanguageModel and parses actions`() = runBlocking {
        val fakeJson = """
            ```json
            {
              "headline": "Q4 Launch Moves to Nov 18",
              "summary": "Engineering cleared authentication blockers and locked November 18 for release.",
              "key_outcomes": ["SSO bug closed", "Staging freeze Friday at 17:00"],
              "action_items": [
                {"title": "Publish rollout checklist", "owner": "Sarah", "due_hint": "Friday"},
                {"title": "", "owner": "Ignored"}
              ],
              "deliverable_title": "Launch Debrief",
              "deliverable_markdown": "## Summary\nRelease locked for Nov 18.",
              "note_title": "Q4 Launch Debrief"
            }
            ```
        """.trimIndent()
        var seenPrompt = ""
        val model = object : LanguageModel {
            override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
                seenPrompt = prompt
                return AiResult.Success(fakeJson)
            }
        }
        val engine = PostSessionAutopilotEngine(model, clock = { 12345L })
        val result = engine.execute(
            recipe = AutopilotRecipe.EXECUTIVE_DEBRIEF,
            sessionTitle = "Release Sync",
            workflow = RecordingType.MEETING,
            sourceText = "Sarah: SSO bug is fixed. Let's ship Nov 18.",
            engineLabel = "On-device (Gemma)"
        )

        assertTrue(result is AiResult.Success)
        val pack = (result as AiResult.Success).value
        assertEquals("Q4 Launch Moves to Nov 18", pack.headline)
        assertEquals(2, pack.keyOutcomes.size)
        assertEquals(1, pack.actions.size)
        assertEquals("Publish rollout checklist", pack.actions.first().title)
        assertEquals("Sarah", pack.actions.first().owner)
        assertEquals(TaskKind.FOLLOW_UP, pack.actions.first().kind)
        assertEquals("On-device (Gemma)", pack.engineLabel)
        assertEquals(12345L, pack.generatedAtMs)
        assertTrue(seenPrompt.contains("Executive Debrief"))
    }

    @Test fun `small group recipe prompt enforces faith grounding contract`() = runBlocking {
        val model = object : LanguageModel {
            override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
                assertTrue(prompt.contains("Faith Contract"))
                return AiResult.Success(
                    """
                    {
                      "headline": "Grace transforms daily work",
                      "summary": "Pastor taught from Romans 12:1-2.",
                      "key_outcomes": ["Romans 12:1-2", "Renewed mind"],
                      "action_items": [{"title": "Pray with accountability partner", "owner": "", "due_hint": "This week"}],
                      "deliverable_title": "Small Group Guide",
                      "deliverable_markdown": "1. Read Romans 12:1-2 together.",
                      "note_title": "Sunday Group Pack"
                    }
                    """.trimIndent()
                )
            }
        }
        val res = PostSessionAutopilotEngine(model).execute(
            recipe = AutopilotRecipe.SERMON_SMALL_GROUP_PACK,
            sessionTitle = "Living Sacrifice",
            workflow = RecordingType.SERMON,
            sourceText = "Today we read Romans 12:1-2 about living sacrifice."
        )
        val pack = (res as? AiResult.Success)?.value
        assertNotNull(pack)
        assertEquals(TaskKind.APPLY, pack!!.actions.first().kind)
    }

    @Test fun `blank source returns clear failure instead of calling model`() = runBlocking {
        var called = false
        val model = object : LanguageModel {
            override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
                called = true
                return AiResult.Success("{}")
            }
        }
        val res = PostSessionAutopilotEngine(model).execute(
            recipe = AutopilotRecipe.EXECUTIVE_DEBRIEF,
            sessionTitle = "Empty",
            workflow = RecordingType.GENERAL,
            sourceText = "   "
        )
        assertTrue(res is AiResult.Failed)
        assertTrue(!called)
    }

    @Test fun `personal recipes are registered and mapped for personal workflows`() {
        assertEquals(4, AutopilotRecipe.PERSONAL_RECIPES.size)
        assertEquals(AutopilotRecipe.EVENING_STOIC_DEBRIEF, AutopilotRecipe.recommendedFor(RecordingType.JOURNAL))
        assertEquals(AutopilotRecipe.IDEA_TO_ACTION_BLUEPRINT, AutopilotRecipe.recommendedFor(RecordingType.IDEA))
        assertEquals(AutopilotRecipe.IDEA_TO_ACTION_BLUEPRINT, AutopilotRecipe.recommendedFor(RecordingType.BRAINSTORM))
        assertEquals(AutopilotRecipe.EVENING_STOIC_DEBRIEF, AutopilotRecipe.recommendedFor(RecordingType.VOICE_MEMO))
    }
}
