package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.notes.SourcePassage
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import org.json.JSONArray
import org.json.JSONObject

data class GeneratedConcept(
    val name: String,
    val definition: String,
    val emphasis: String?,
    val relationships: List<String>,
    val evidenceIds: List<String>
)

data class GeneratedActivity(
    val prompt: String,
    val expectedAnswer: String,
    val options: List<String>,
    val type: LearningActivityType,
    val difficulty: String,
    val conceptName: String?,
    val evidenceIds: List<String>
)

data class GeneratedMistakeFeedback(
    val correctReasoning: String,
    val misconception: String,
    val nextQuestionPrompt: String?,
    val evidenceIds: List<String>
)

/**
 * AI extraction and generation engine for the Learning vertical (docs/PLAN_LEARNING.md §5.4).
 * Strictly validates citations, prevents question leakage, and grounds every concept in source evidence.
 */
class LearningIntelligenceEngine(
    private val languageModel: LanguageModel,
    private val maxOutputTokens: Int = 2048
) {

    suspend fun extractStudyGuide(
        passages: List<SourcePassage>
    ): AiResult<List<GeneratedConcept>> {
        val usable = passages.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) {
            return AiResult.Failed("No source passages available to extract concepts from.")
        }

        val aliases = usable.mapIndexed { index, p -> "p${index + 1}" to p }.toMap()
        val prompt = buildStudyGuidePrompt(aliases)

        val response = when (val res = languageModel.generate(prompt, maxOutputTokens)) {
            is AiResult.Success -> res.value
            else -> return AiResult.Failed(res.describeFailure() ?: "Study guide extraction failed.")
        }

        val json = extractJsonObject(response)
            ?: return AiResult.Failed("Malformed model response for study guide.")

        return AiResult.Success(parseStudyGuide(json, aliases))
    }

    suspend fun generateDiagnostic(
        concepts: List<GeneratedConcept>,
        passages: List<SourcePassage>
    ): AiResult<List<GeneratedActivity>> {
        val usable = passages.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return AiResult.Failed("No source text available for diagnostic generation.")

        val aliases = usable.mapIndexed { index, p -> "p${index + 1}" to p }.toMap()
        val prompt = buildDiagnosticPrompt(concepts, aliases)

        val response = when (val res = languageModel.generate(prompt, maxOutputTokens)) {
            is AiResult.Success -> res.value
            else -> return AiResult.Failed(res.describeFailure() ?: "Diagnostic generation failed.")
        }

        val json = extractJsonObject(response)
            ?: return AiResult.Failed("Malformed model response for diagnostic.")

        val activities = parseActivities(json, aliases, isDiagnostic = true)
        // Diagnostic requirement: 5-8 valid activities.
        // Reject and do not persist partial diagnostics (< 5 questions)
        if (activities.size < 5) {
            return AiResult.Failed("Diagnostic generation yielded fewer than 5 valid questions (${activities.size} generated).")
        }

        // Bounded to 5-8 questions (PLAN_LEARNING §2)
        val bounded = activities.take(8)
        return AiResult.Success(bounded)
    }

    suspend fun explainMistake(
        questionPrompt: String,
        learnerAnswer: String,
        expectedAnswer: String,
        passages: List<SourcePassage>
    ): AiResult<GeneratedMistakeFeedback> {
        val aliases = passages.mapIndexed { index, p -> "p${index + 1}" to p }.toMap()
        val prompt = buildMistakePrompt(questionPrompt, learnerAnswer, expectedAnswer, aliases)

        val response = when (val res = languageModel.generate(prompt, maxOutputTokens)) {
            is AiResult.Success -> res.value
            else -> return AiResult.Failed(res.describeFailure() ?: "Failed to generate mistake feedback.")
        }

        val json = extractJsonObject(response)
            ?: return AiResult.Failed("Malformed mistake explanation JSON.")

        val reasoning = json.optString("correctReasoning").trim()
        val misconception = json.optString("misconception").trim()
        val nextPrompt = json.optString("nextPrompt").trim().takeIf { it.isNotBlank() }
        val sources = parseSourceIds(json.optJSONArray("sources"), aliases)

        return AiResult.Success(
            GeneratedMistakeFeedback(
                correctReasoning = reasoning.ifBlank { "The expected answer is: $expectedAnswer" },
                misconception = misconception,
                nextQuestionPrompt = nextPrompt,
                evidenceIds = sources
            )
        )
    }

    internal fun parseStudyGuide(
        json: JSONObject,
        aliases: Map<String, SourcePassage>
    ): List<GeneratedConcept> {
        val arr = json.optJSONArray("concepts") ?: return emptyList()
        val results = mutableListOf<GeneratedConcept>()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            val definition = obj.optString("definition").trim()
            if (name.isBlank() || definition.isBlank()) continue

            val emphasis = obj.optString("emphasis").trim().takeIf { it.isNotBlank() }
            val rels = mutableListOf<String>()
            obj.optJSONArray("relationships")?.let { rArr ->
                for (j in 0 until rArr.length()) rels.add(rArr.optString(j).trim())
            }

            val sources = parseSourceIds(obj.optJSONArray("sources"), aliases)
            // Strict grounding: only keep concept if it has valid citations
            if (sources.isNotEmpty()) {
                results.add(
                    GeneratedConcept(
                        name = name,
                        definition = definition,
                        emphasis = emphasis,
                        relationships = rels.filter { it.isNotBlank() },
                        evidenceIds = sources
                    )
                )
            }
        }
        return results
    }

    internal fun parseActivities(
        json: JSONObject,
        aliases: Map<String, SourcePassage>,
        isDiagnostic: Boolean
    ): List<GeneratedActivity> {
        val arr = json.optJSONArray("activities") ?: return emptyList()
        val results = mutableListOf<GeneratedActivity>()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val prompt = obj.optString("prompt").trim()
            val expectedAnswer = obj.optString("expectedAnswer").trim()
            if (prompt.isBlank() || expectedAnswer.isBlank()) continue

            // Anti-leakage gate: The prompt must NOT reveal the expected answer
            if (isAnswerLeaked(prompt, expectedAnswer)) continue

            val typeStr = obj.optString("type", "MULTIPLE_CHOICE").trim().uppercase()
            // Reject APPLICATION in R1 generation (do not silently reclassify)
            if (typeStr == "APPLICATION") continue

            val type = when (typeStr) {
                "RECALL" -> LearningActivityType.RECALL
                "MULTIPLE_CHOICE" -> LearningActivityType.MULTIPLE_CHOICE
                else -> continue // Reject unrecognized types
            }

            val options = mutableListOf<String>()
            if (type == LearningActivityType.MULTIPLE_CHOICE) {
                obj.optJSONArray("options")?.let { optArr ->
                    for (j in 0 until optArr.length()) {
                        val opt = optArr.optString(j).trim()
                        if (opt.isNotBlank()) options.add(opt)
                    }
                }
                val uniqueOptions = options.distinct()
                // Multiple choice: exactly 4 normalized unique non-blank options
                if (uniqueOptions.size != 4) continue

                // Multiple choice: exactly 1 match for expectedAnswer
                val matchCount = uniqueOptions.count { it.equals(expectedAnswer, ignoreCase = true) }
                if (matchCount != 1) continue
            }

            val difficulty = obj.optString("difficulty", "MEDIUM")
            val conceptName = obj.optString("conceptName").trim().takeIf { it.isNotBlank() }
            val sources = parseSourceIds(obj.optJSONArray("sources"), aliases)

            if (sources.isNotEmpty()) {
                results.add(
                    GeneratedActivity(
                        prompt = prompt,
                        expectedAnswer = expectedAnswer,
                        options = if (type == LearningActivityType.MULTIPLE_CHOICE) options.distinct() else emptyList(),
                        type = type,
                        difficulty = difficulty,
                        conceptName = conceptName,
                        evidenceIds = sources
                    )
                )
            }
        }
        return results
    }

    internal fun isAnswerLeaked(prompt: String, expectedAnswer: String): Boolean =
        Companion.isAnswerLeaked(prompt, expectedAnswer)

    companion object {
        fun isAnswerLeaked(prompt: String, expectedAnswer: String): Boolean {
            if (prompt.equals(expectedAnswer, ignoreCase = true)) return true
            val cleanAnswer = expectedAnswer.trim().lowercase()
            // If expected answer is a significant multi-word phrase or specific technical term, ensure it's not simply in the prompt
            if (cleanAnswer.length > 3 && prompt.lowercase().contains(cleanAnswer)) {
                return true
            }
            return false
        }
    }

    private fun parseSourceIds(
        array: JSONArray?,
        aliases: Map<String, SourcePassage>
    ): List<String> {
        if (array == null) return emptyList()
        val ids = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val alias = array.optString(i).trim()
            aliases[alias]?.let { ids.add(it.id) }
        }
        return ids.distinct()
    }

    private fun buildStudyGuidePrompt(aliases: Map<String, SourcePassage>): String = buildString {
        appendLine("You are an expert tutor creating a grounded Study Guide from this lecture material.")
        appendLine("FIDELITY REQUIREMENT: Every concept, definition, and emphasis MUST cite one or more source passages using their keys (e.g. [\"p1\"]). Do NOT fabricate information not present in the passages.")
        appendLine()
        appendLine("SOURCE PASSAGES:")
        aliases.forEach { (key, p) ->
            appendLine("[$key] (${p.label}): ${p.text}")
        }
        appendLine()
        appendLine("Output strictly valid JSON with this schema:")
        appendLine("""
        {
          "concepts": [
            {
              "name": "Concept name",
              "definition": "Clear concise definition from the source",
              "emphasis": "Lecturer's emphasis or key takeaway, if any",
              "relationships": ["Related concept A", "Related concept B"],
              "sources": ["p1", "p2"]
            }
          ]
        }
        """.trimIndent())
    }

    private fun buildDiagnosticPrompt(
        concepts: List<GeneratedConcept>,
        aliases: Map<String, SourcePassage>
    ): String = buildString {
        appendLine("You are creating a 5 to 8 question Diagnostic practice set for a student.")
        appendLine("Rules:")
        appendLine("1. Every question MUST be grounded in the source passages and cite them.")
        appendLine("2. NEVER reveal or leak the expected answer in the question prompt.")
        appendLine("3. Provide a mix of MULTIPLE_CHOICE (with 4 realistic options) and RECALL questions.")
        appendLine("4. Multiple-choice options must include the correct answer and 3 plausible distractors.")
        appendLine()
        appendLine("CONCEPTS:")
        concepts.forEach { c -> appendLine("- ${c.name}: ${c.definition}") }
        appendLine()
        appendLine("SOURCE PASSAGES:")
        aliases.forEach { (key, p) ->
            appendLine("[$key]: ${p.text}")
        }
        appendLine()
        appendLine("Output strictly valid JSON with this schema:")
        appendLine("""
        {
          "activities": [
            {
              "prompt": "Question text without giving away the answer",
              "expectedAnswer": "Correct answer",
              "options": ["Option 1", "Option 2", "Option 3", "Option 4"],
              "type": "MULTIPLE_CHOICE",
              "difficulty": "MEDIUM",
              "conceptName": "Concept name",
              "sources": ["p1"]
            }
          ]
        }
        """.trimIndent())
    }

    private fun buildMistakePrompt(
        prompt: String,
        learnerAnswer: String,
        expectedAnswer: String,
        aliases: Map<String, SourcePassage>
    ): String = buildString {
        appendLine("The student answered a practice question incorrectly. Provide constructive, cited feedback.")
        appendLine("Question: $prompt")
        appendLine("Student's Answer: $learnerAnswer")
        appendLine("Expected Answer: $expectedAnswer")
        appendLine()
        appendLine("SOURCE PASSAGES:")
        aliases.forEach { (key, p) -> appendLine("[$key]: ${p.text}") }
        appendLine()
        appendLine("Output strictly valid JSON:")
        appendLine("""
        {
          "correctReasoning": "Why the expected answer is correct based on the source",
          "misconception": "Why the student's answer was incorrect or incomplete",
          "nextPrompt": "Follow-up question or hint",
          "sources": ["p1"]
        }
        """.trimIndent())
    }

    private fun extractJsonObject(raw: String): JSONObject? = runCatching {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start != -1 && end > start) {
            JSONObject(trimmed.substring(start, end + 1))
        } else null
    }.getOrNull()
}
