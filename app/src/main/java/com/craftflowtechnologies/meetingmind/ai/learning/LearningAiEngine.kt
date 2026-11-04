package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.core.learning.SpokenNotationNormalizer
import com.craftflowtechnologies.meetingmind.core.learning.SubjectDomain
import com.craftflowtechnologies.meetingmind.core.model.BlockSource
import com.craftflowtechnologies.meetingmind.core.model.ExamImportance
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningMasteryState
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.TeachBackCriterion
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.work.Mark
import com.craftflowtechnologies.meetingmind.core.work.MarkKind
import org.json.JSONArray
import org.json.JSONObject

class LearningAiEngine(
    private val transport: GeminiTransport? = null,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private suspend fun generateText(prompt: String): String? {
        val t = transport ?: return null
        if (!t.isConfigured()) return null
        val req = GeminiRequest(
            modelId = "gemini-1.5-flash",
            systemInstruction = "You are an expert academic tutor and university professor.",
            prompt = prompt
        )
        return when (val res = t.execute(req)) {
            is AiResult.Success -> res.value
            else -> null
        }
    }

    suspend fun synthesizeLecture(
        noteId: String,
        sessionId: String,
        segments: List<TranscriptSegment>,
        domain: SubjectDomain,
        marks: List<Mark>
    ): LectureSynthesis {
        val transcriptText = segments.joinToString("\n") { "[${it.startMs / 1000}s] ${it.text}" }
        val prompt = buildString {
            appendLine("You are an expert university professor and master tutor in ${domain.displayName}.")
            appendLine("Analyze the following lecture transcript and synthesize structured study materials.")
            appendLine("Extract key concepts, definitions, active-recall practice questions, exam hints, and explanations for confusing points.")
            appendLine("Vocabulary to prioritize: ${domain.vocabularyHints().take(12).joinToString(", ")}")
            appendLine("Transcript:")
            appendLine(transcriptText.take(15000))
            appendLine()
            appendLine("Respond with JSON:")
            appendLine("""
                {
                  "summary": "High-level summary of the lecture",
                  "concepts": [
                    { "name": "Concept Name", "description": "Crisp definition", "importance": "HIGH_YIELD|NORMAL", "formula": "LaTeX formula if applicable" }
                  ],
                  "questions": [
                    { "prompt": "Active recall question", "answer": "Model answer", "type": "RECALL|WORKED_PROBLEM|APPLICATION", "difficulty": "EASY|MEDIUM|HARD" }
                  ],
                  "examHints": ["Exam hint 1"],
                  "confusionUnpacks": ["Step-by-step unpack"]
                }
            """.trimIndent())
        }

        val response = generateText(prompt)
        if (response != null) {
            val parsed = runCatching { parseSynthesisJson(sessionId, response, domain) }.getOrNull()
            if (parsed != null) return parsed
        }

        // Deterministic offline fallback
        return fallbackSynthesis(sessionId, segments, domain, marks)
    }

    suspend fun evaluateTeachBack(
        rubric: List<TeachBackCriterion>,
        spokenTranscript: String,
        concept: LearningConcept?
    ): TeachBackEvaluation {
        val prompt = buildString {
            appendLine("Evaluate a student's spoken Teach-Back explanation against the provided rubric.")
            appendLine("Concept: ${concept?.name ?: "Topic"} (${concept?.definition ?: ""})")
            appendLine("Student Explanation:")
            appendLine(spokenTranscript)
            appendLine("Rubric Criteria:")
            rubric.forEach { appendLine("- ${it.claim} (Required: ${it.required})") }
            appendLine("Respond with JSON: {\"overall\": \"COVERED|PARTLY_COVERED|NOT_YET_DEMONSTRATED\", \"score\": 85, \"summary\": \"evaluation\", \"missing\": [\"points\"], \"socraticFollowUp\": \"question\", \"criteria\": [{\"claim\": \"c\", \"status\": \"MET|PARTIAL|UNMET\", \"feedback\": \"f\"}]}")
        }

        val res = generateText(prompt)
        if (res != null) {
            val eval = runCatching { parseTeachBackJson(res, rubric) }.getOrNull()
            if (eval != null) return eval
        }

        // Deterministic offline rubric evaluation
        val lowerTranscript = spokenTranscript.lowercase()
        val evaluations = rubric.map { c ->
            val keywords = c.claim.lowercase().split(" ").filter { it.length > 3 }
            val matchCount = keywords.count { lowerTranscript.contains(it) }
            val status = when {
                matchCount >= (keywords.size * 0.6).coerceAtLeast(1.0) -> TeachBackStatus.MET
                matchCount > 0 -> TeachBackStatus.PARTIAL
                else -> TeachBackStatus.UNMET
            }
            TeachBackCriterionEvaluation(
                criterion = c,
                status = status,
                feedback = if (status == TeachBackStatus.MET) "Good explanation of ${c.claim}" else "Remember to mention ${c.claim}",
                citedEvidence = if (status == TeachBackStatus.MET) spokenTranscript.take(80) else null
            )
        }

        val metCount = evaluations.count { it.status == TeachBackStatus.MET }
        val partialCount = evaluations.count { it.status == TeachBackStatus.PARTIAL }
        val total = rubric.size.coerceAtLeast(1)
        val score = (((metCount * 1.0) + (partialCount * 0.5)) / total * 100).toInt()
        val coverage = when {
            score >= 75 -> TeachBackCoverage.COVERED
            score >= 40 -> TeachBackCoverage.PARTLY_COVERED
            else -> TeachBackCoverage.NOT_YET_DEMONSTRATED
        }
        val missing = evaluations.filter { it.status == TeachBackStatus.UNMET }.map { it.criterion.claim }

        return TeachBackEvaluation(
            overallCoverage = coverage,
            scorePercent = score,
            summary = when (coverage) {
                TeachBackCoverage.COVERED -> "Excellent explanation covering the core intuition and mechanism."
                TeachBackCoverage.PARTLY_COVERED -> "Good start, but some key elements were skipped."
                TeachBackCoverage.NOT_YET_DEMONSTRATED -> "Keep going — try explaining the main definition first."
            },
            criteriaEvaluations = evaluations,
            missingClaims = missing,
            socraticFollowUp = if (missing.isNotEmpty()) "How does ${missing.first()} affect the final outcome?" else "Can you give an edge case example?"
        )
    }

    suspend fun explainMistake(
        activity: LearningActivity,
        studentAnswer: String
    ): MistakeExplanation {
        val prompt = buildString {
            appendLine("A student made a mistake on this question:")
            appendLine("Question: ${activity.prompt}")
            appendLine("Correct Answer: ${activity.expectedAnswer}")
            appendLine("Student's Answer: $studentAnswer")
            appendLine("Explain their mistake Socratic-style.")
            appendLine("Respond with JSON: {\"rootMisconception\": \"m\", \"correctMentalModel\": \"c\", \"stepByStepCorrection\": \"s\"}")
        }

        val res = generateText(prompt)
        if (res != null) {
            val obj = runCatching { JSONObject(res) }.getOrNull()
            if (obj != null) {
                return MistakeExplanation(
                    studentMistake = studentAnswer,
                    rootMisconception = obj.optString("rootMisconception", "Common confusion regarding the core rule"),
                    correctMentalModel = obj.optString("correctMentalModel", activity.expectedAnswer),
                    stepByStepCorrection = obj.optString("stepByStepCorrection", "Review the step: ${activity.expectedAnswer}")
                )
            }
        }

        return MistakeExplanation(
            studentMistake = studentAnswer,
            rootMisconception = "The rule or formula was applied without verifying the initial conditions.",
            correctMentalModel = activity.expectedAnswer,
            stepByStepCorrection = "1. Identify the given variables.\n2. Recall: ${activity.expectedAnswer}.\n3. Substitute carefully."
        )
    }

    suspend fun enrichNote(
        noteId: String,
        selectionText: String,
        kind: EnrichmentKind,
        domain: SubjectDomain
    ): EnrichmentResult {
        val prompt = buildString {
            appendLine("Enrich the following lecture note passage for a student in ${domain.displayName}.")
            appendLine("Task: ${kind.label}")
            appendLine("Passage: $selectionText")
            appendLine("Respond with JSON: {\"explanation\": \"e\", \"formula\": \"optional LaTeX\", \"steps\": [\"step 1\", \"step 2\"]}")
        }

        val res = generateText(prompt)
        val enrichedText = if (res != null) {
            val obj = runCatching { JSONObject(res) }.getOrNull()
            obj?.optString("explanation")?.takeIf { it.isNotBlank() } ?: "$selectionText (expanded with $kind)"
        } else {
            when (kind) {
                EnrichmentKind.UNPACK_STEPS -> "Step-by-step expansion for: $selectionText\n1. State the initial theorem.\n2. Apply the algebraic transformation.\n3. Simplify the resulting terms."
                EnrichmentKind.WORKED_EXAMPLE -> "Worked example for $selectionText:\nGiven: x = 2, f(x) = x^2\nFind: f'(2)\nSolution: f'(x) = 2x, so f'(2) = 4."
                EnrichmentKind.PREREQUISITE_BRIDGE -> "Prerequisite bridge: Before applying this concept, ensure you understand the base definition and domain constraints."
                EnrichmentKind.INTUITION -> "Intuition: Think of this as the instantaneous rate of change or physical flow across the boundary."
            }
        }

        val blocks = listOf(
            NoteBlock(
                id = NoteRepository.newId("block"),
                noteId = noteId,
                position = 0,
                type = NoteBlockType.CALLOUT,
                source = BlockSource.ENRICHED,
                content = RichText.plain(enrichedText),
                payload = mapOf(
                    NoteBlock.PAYLOAD_CALLOUT_KIND to "EXPLANATION",
                    NoteBlock.PAYLOAD_CALLOUT_TITLE to kind.label,
                    NoteBlock.PAYLOAD_ENRICH_LABEL to kind.label,
                    NoteBlock.PAYLOAD_UNPACKED to "0" // Folded by default!
                )
            )
        )

        return EnrichmentResult(kind, kind.label, blocks)
    }

    private fun parseSynthesisJson(sessionId: String, json: String, domain: SubjectDomain): LectureSynthesis {
        val obj = JSONObject(json)
        val summary = obj.optString("summary", "Lecture synthesis")
        val conceptsList = mutableListOf<LearningConcept>()
        val conceptsArr = obj.optJSONArray("concepts") ?: JSONArray()
        for (i in 0 until conceptsArr.length()) {
            val c = conceptsArr.getJSONObject(i)
            val name = c.getString("name")
            val desc = c.getString("description")
            val importance = if (c.optString("importance") == "HIGH_YIELD") ExamImportance.HIGH_YIELD else ExamImportance.NORMAL
            val formula = c.optString("formula").takeIf { it.isNotBlank() }
            conceptsList.add(
                LearningConcept(
                    id = NoteRepository.newId("concept"),
                    sessionId = sessionId,
                    name = name,
                    definition = desc,
                    state = LearningMasteryState.DEVELOPING,
                    examImportance = importance,
                    formulaLatex = formula,
                    createdAt = clock(),
                    updatedAt = clock()
                )
            )
        }

        val activitiesList = mutableListOf<LearningActivity>()
        val questionsArr = obj.optJSONArray("questions") ?: JSONArray()
        for (i in 0 until questionsArr.length()) {
            val q = questionsArr.getJSONObject(i)
            val prompt = q.getString("prompt")
            val answer = q.getString("answer")
            val type = when (q.optString("type")) {
                "WORKED_PROBLEM" -> LearningActivityType.WORKED_PROBLEM
                "APPLICATION" -> LearningActivityType.APPLICATION
                else -> LearningActivityType.RECALL
            }
            activitiesList.add(
                LearningActivity(
                    id = NoteRepository.newId("activity"),
                    sessionId = sessionId,
                    conceptId = conceptsList.firstOrNull()?.id,
                    type = type,
                    prompt = prompt,
                    expectedAnswer = answer,
                    difficulty = "MEDIUM",
                    isDiagnostic = i < 3,
                    createdAt = clock(),
                    updatedAt = clock()
                )
            )
        }

        val examHints = mutableListOf<String>()
        val hintsArr = obj.optJSONArray("examHints") ?: JSONArray()
        for (i in 0 until hintsArr.length()) examHints.add(hintsArr.getString(i))

        val confusionUnpacks = mutableListOf<String>()
        val unpacksArr = obj.optJSONArray("confusionUnpacks") ?: JSONArray()
        for (i in 0 until unpacksArr.length()) confusionUnpacks.add(unpacksArr.getString(i))

        return LectureSynthesis(
            summary = summary,
            concepts = conceptsList,
            activities = activitiesList,
            examHints = examHints,
            confusionUnpacks = confusionUnpacks
        )
    }

    private fun parseTeachBackJson(json: String, rubric: List<TeachBackCriterion>): TeachBackEvaluation {
        val obj = JSONObject(json)
        val overall = when (obj.optString("overall")) {
            "COVERED" -> TeachBackCoverage.COVERED
            "PARTLY_COVERED" -> TeachBackCoverage.PARTLY_COVERED
            else -> TeachBackCoverage.NOT_YET_DEMONSTRATED
        }
        val score = obj.optInt("score", 70)
        val summary = obj.optString("summary", "Teach-back evaluation completed.")
        val missing = mutableListOf<String>()
        val missingArr = obj.optJSONArray("missing") ?: JSONArray()
        for (i in 0 until missingArr.length()) missing.add(missingArr.getString(i))
        val socratic = obj.optString("socraticFollowUp", "Can you explain the main mechanism again?")

        val criteriaMap = mutableMapOf<String, TeachBackCriterionEvaluation>()
        val critArr = obj.optJSONArray("criteria") ?: JSONArray()
        for (i in 0 until critArr.length()) {
            val item = critArr.getJSONObject(i)
            val claim = item.optString("claim")
            val status = when (item.optString("status")) {
                "MET" -> TeachBackStatus.MET
                "PARTIAL" -> TeachBackStatus.PARTIAL
                else -> TeachBackStatus.UNMET
            }
            val crit = rubric.firstOrNull { it.claim == claim }
            if (crit != null) {
                criteriaMap[claim] = TeachBackCriterionEvaluation(
                    criterion = crit,
                    status = status,
                    feedback = item.optString("feedback", "")
                )
            }
        }

        val allEvaluations = rubric.map { c ->
            criteriaMap[c.claim] ?: TeachBackCriterionEvaluation(
                criterion = c,
                status = TeachBackStatus.MET,
                feedback = "Covered adequately"
            )
        }

        return TeachBackEvaluation(
            overallCoverage = overall,
            scorePercent = score,
            summary = summary,
            criteriaEvaluations = allEvaluations,
            missingClaims = missing,
            socraticFollowUp = socratic
        )
    }

    private fun fallbackSynthesis(
        sessionId: String,
        segments: List<TranscriptSegment>,
        domain: SubjectDomain,
        marks: List<Mark>
    ): LectureSynthesis {
        val allText = segments.joinToString(" ") { it.text }
        val matchedVocab = domain.vocabularyHints().filter { allText.contains(it, ignoreCase = true) }
        val concepts = matchedVocab.take(5).map { term ->
            LearningConcept(
                id = NoteRepository.newId("concept"),
                sessionId = sessionId,
                name = term,
                definition = "Key concept introduced in lecture regarding $term.",
                state = LearningMasteryState.DEVELOPING,
                examImportance = if (marks.any { it.kind == MarkKind.EXAM_HINT }) ExamImportance.HIGH_YIELD else ExamImportance.NORMAL,
                createdAt = clock(),
                updatedAt = clock()
            )
        }.ifEmpty {
            listOf(
                LearningConcept(
                    id = NoteRepository.newId("concept"),
                    sessionId = sessionId,
                    name = "${domain.shortLabel} Lecture Core",
                    definition = "Fundamental principles and definitions from this session.",
                    state = LearningMasteryState.DEVELOPING,
                    createdAt = clock(),
                    updatedAt = clock()
                )
            )
        }

        val activities = concepts.mapIndexed { idx, c ->
            LearningActivity(
                id = NoteRepository.newId("activity"),
                sessionId = sessionId,
                conceptId = c.id,
                type = LearningActivityType.RECALL,
                prompt = "Explain the significance of ${c.name} in this context.",
                expectedAnswer = c.definition,
                difficulty = "MEDIUM",
                isDiagnostic = idx < 3,
                createdAt = clock(),
                updatedAt = clock()
            )
        }

        val examHints = marks.filter { it.kind == MarkKind.EXAM_HINT }.map { it.text?.ifBlank { null } ?: "Exam hint recorded during lecture" }
        val confusionUnpacks = marks.filter { it.kind == MarkKind.CONFUSED }.map { it.text?.ifBlank { null } ?: "Explanation needed for confusing topic" }

        return LectureSynthesis(
            summary = "Summary of ${domain.displayName} lecture notes.",
            concepts = concepts,
            activities = activities,
            examHints = examHints,
            confusionUnpacks = confusionUnpacks
        )
    }
}
