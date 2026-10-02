package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.notes.SourcePassage
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LearningIntelligenceEngineTest {

    private class FakeLanguageModel(val response: String) : LanguageModel {
        override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
            return AiResult.Success(response)
        }
    }

    private val aliases = mapOf(
        "p1" to SourcePassage("block_1", "Enzymes lower the activation energy of a reaction.", "Lecture"),
        "p2" to SourcePassage("block_2", "Competitive inhibitors bind to the active site directly.", "Lecture")
    )

    @Test
    fun `parseStudyGuide extracts valid concepts and drops ungrounded ones`() {
        val engine = LearningIntelligenceEngine(FakeLanguageModel("{}"))
        val json = JSONObject("""
        {
          "concepts": [
            {
              "name": "Activation Energy",
              "definition": "The minimum energy required to initiate a reaction.",
              "emphasis": "Crucial for kinetic rate",
              "relationships": ["Enzymes", "Catalysts"],
              "sources": ["p1"]
            },
            {
              "name": "Hallucinated Concept",
              "definition": "Made up fact with no source.",
              "sources": []
            }
          ]
        }
        """)

        val result = engine.parseStudyGuide(json, aliases)
        assertEquals(1, result.size)
        val concept = result.first()
        assertEquals("Activation Energy", concept.name)
        assertEquals(listOf("block_1"), concept.evidenceIds)
        assertEquals("Crucial for kinetic rate", concept.emphasis)
    }

    @Test
    fun `parseActivities drops questions that leak the answer in the prompt`() {
        val engine = LearningIntelligenceEngine(FakeLanguageModel("{}"))
        val json = JSONObject("""
        {
          "activities": [
            {
              "prompt": "Where do competitive inhibitors bind?",
              "expectedAnswer": "Active site",
              "options": ["Active site", "Allosteric site", "Co-factor", "Substrate"],
              "type": "MULTIPLE_CHOICE",
              "sources": ["p2"]
            },
            {
              "prompt": "Why does the active site bind competitive inhibitors?",
              "expectedAnswer": "Active site",
              "options": ["A", "B"],
              "type": "MULTIPLE_CHOICE",
              "sources": ["p2"]
            }
          ]
        }
        """)

        val result = engine.parseActivities(json, aliases, isDiagnostic = true)
        // Second question has "active site" in prompt matching expectedAnswer "Active site", so it must be dropped
        assertEquals(1, result.size)
        assertEquals("Where do competitive inhibitors bind?", result.first().prompt)
        assertEquals("Active site", result.first().expectedAnswer)
    }

    @Test
    fun `isAnswerLeaked detects identical or embedded answers`() {
        val engine = LearningIntelligenceEngine(FakeLanguageModel("{}"))
        assertTrue(engine.isAnswerLeaked("Active site", "Active site"))
        assertTrue(engine.isAnswerLeaked("What happens at the active site during binding?", "active site"))
        assertFalse(engine.isAnswerLeaked("Where does the competitive inhibitor bind?", "Active site"))
    }

    @Test
    fun `explainMistake parses cited feedback`() = runBlocking {
        val mockResponse = """
        {
          "correctReasoning": "Competitive inhibitors directly compete with substrate at the active site.",
          "misconception": "Allosteric inhibitors bind elsewhere, not competitive ones.",
          "nextPrompt": "What happens if substrate concentration is increased?",
          "sources": ["p2"]
        }
        """
        val engine = LearningIntelligenceEngine(FakeLanguageModel(mockResponse))
        val result = engine.explainMistake(
            questionPrompt = "Where do competitive inhibitors bind?",
            learnerAnswer = "Allosteric site",
            expectedAnswer = "Active site",
            passages = aliases.values.toList()
        )

        assertTrue(result is AiResult.Success)
        val feedback = (result as AiResult.Success).value
        assertTrue(feedback.correctReasoning.contains("directly compete"))
        assertTrue(feedback.misconception.contains("Allosteric inhibitors"))
        assertEquals(listOf("block_2"), feedback.evidenceIds)
    }
}
