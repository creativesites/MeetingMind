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

    @Test
    fun `parseActivities rejects APPLICATION activity type in Release 1`() {
        val engine = LearningIntelligenceEngine(FakeLanguageModel("{}"))
        val json = JSONObject("""
        {
          "activities": [
            {
              "prompt": "Apply enzyme kinetics to calculate Vmax given initial velocities.",
              "expectedAnswer": "Vmax is 50",
              "type": "APPLICATION",
              "sources": ["p1"]
            }
          ]
        }
        """)

        val result = engine.parseActivities(json, aliases, isDiagnostic = true)
        assertTrue("APPLICATION activities must be rejected in Release 1", result.isEmpty())
    }

    @Test
    fun `parseActivities enforces exactly 4 unique options with 1 matching expectedAnswer for MCQ`() {
        val engine = LearningIntelligenceEngine(FakeLanguageModel("{}"))
        val json = JSONObject("""
        {
          "activities": [
            {
              "prompt": "Valid MCQ question?",
              "expectedAnswer": "Opt A",
              "options": ["Opt A", "Opt B", "Opt C", "Opt D"],
              "type": "MULTIPLE_CHOICE",
              "sources": ["p1"]
            },
            {
              "prompt": "Too few options?",
              "expectedAnswer": "Opt A",
              "options": ["Opt A", "Opt B", "Opt C"],
              "type": "MULTIPLE_CHOICE",
              "sources": ["p1"]
            },
            {
              "prompt": "Duplicate options?",
              "expectedAnswer": "Opt A",
              "options": ["Opt A", "Opt B", "Opt C", "Opt B"],
              "type": "MULTIPLE_CHOICE",
              "sources": ["p1"]
            },
            {
              "prompt": "Expected answer not in options?",
              "expectedAnswer": "Opt X",
              "options": ["Opt A", "Opt B", "Opt C", "Opt D"],
              "type": "MULTIPLE_CHOICE",
              "sources": ["p1"]
            }
          ]
        }
        """)

        val result = engine.parseActivities(json, aliases, isDiagnostic = true)
        assertEquals(1, result.size)
        assertEquals("Valid MCQ question?", result.first().prompt)
        assertEquals(4, result.first().options.size)
    }

    @Test
    fun `generateDiagnostic rejects and fails if fewer than 5 valid questions are generated`() = runBlocking {
        // Model only returns 3 valid questions
        val mockResponse = """
        {
          "activities": [
            { "prompt": "Q1?", "expectedAnswer": "A1", "options": ["A1", "B1", "C1", "D1"], "type": "MULTIPLE_CHOICE", "sources": ["p1"] },
            { "prompt": "Q2?", "expectedAnswer": "A2", "options": ["A2", "B2", "C2", "D2"], "type": "MULTIPLE_CHOICE", "sources": ["p1"] },
            { "prompt": "Q3?", "expectedAnswer": "A3", "options": ["A3", "B3", "C3", "D3"], "type": "MULTIPLE_CHOICE", "sources": ["p1"] }
          ]
        }
        """
        val engine = LearningIntelligenceEngine(FakeLanguageModel(mockResponse))
        val result = engine.generateDiagnostic(emptyList(), aliases.values.toList())
        assertTrue("Diagnostic with < 5 questions must fail", result is AiResult.Failed)
    }

    @Test
    fun `generateDiagnostic succeeds when 5 to 8 valid questions are generated`() = runBlocking {
        val mockResponse = """
        {
          "activities": [
            { "prompt": "Q1?", "expectedAnswer": "A1", "options": ["A1", "B1", "C1", "D1"], "type": "MULTIPLE_CHOICE", "sources": ["p1"] },
            { "prompt": "Q2?", "expectedAnswer": "A2", "options": ["A2", "B2", "C2", "D2"], "type": "MULTIPLE_CHOICE", "sources": ["p1"] },
            { "prompt": "Q3?", "expectedAnswer": "A3", "options": ["A3", "B3", "C3", "D3"], "type": "MULTIPLE_CHOICE", "sources": ["p1"] },
            { "prompt": "Q4?", "expectedAnswer": "A4", "options": ["A4", "B4", "C4", "D4"], "type": "MULTIPLE_CHOICE", "sources": ["p1"] },
            { "prompt": "Q5?", "expectedAnswer": "A5", "options": ["A5", "B5", "C5", "D5"], "type": "MULTIPLE_CHOICE", "sources": ["p1"] },
            { "prompt": "Q6?", "expectedAnswer": "A6", "options": ["A6", "B6", "C6", "D6"], "type": "MULTIPLE_CHOICE", "sources": ["p1"] }
          ]
        }
        """
        val engine = LearningIntelligenceEngine(FakeLanguageModel(mockResponse))
        val result = engine.generateDiagnostic(emptyList(), aliases.values.toList())
        assertTrue(result is AiResult.Success)
        val activities = (result as AiResult.Success).value
        assertEquals(6, activities.size)
    }
}
