package com.craftflowtechnologies.meetingmind.ai.learning

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiInteractions
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.learning.LearningAiStatus
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
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import com.craftflowtechnologies.meetingmind.core.model.TeachBackCriterion
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.work.Mark
import com.craftflowtechnologies.meetingmind.core.work.MarkKind
import com.craftflowtechnologies.meetingmind.feature.learning.ChartPoint
import com.craftflowtechnologies.meetingmind.feature.learning.ChartSeries
import com.craftflowtechnologies.meetingmind.feature.learning.LearningVisualKind
import com.craftflowtechnologies.meetingmind.feature.learning.LearningVisualSpec
import com.craftflowtechnologies.meetingmind.feature.learning.TimelineNode
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LearningAiEngine(
    private val transport: GeminiTransport? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val fallbackModel: LanguageModel? = null,
    private val fallbackModelProvider: (suspend () -> LanguageModel?)? = null,
    private val preferLocal: (suspend () -> Boolean)? = null,
    private val onLocalFinished: (() -> Unit)? = null
) {

    private fun stripMarkdownCodeFences(raw: String): String {
        val trimmed = raw.trim()
        val fencedMatch = Regex("""```(?:json)?\s*\n?([\s\S]*?)\n?```""", RegexOption.IGNORE_CASE).find(trimmed)
        if (fencedMatch != null) {
            return fencedMatch.groupValues[1].trim()
        }
        if (trimmed.startsWith("```")) {
            return trimmed.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        }
        return trimmed
    }

    private suspend fun shouldPreferLocal(): Boolean = preferLocal?.invoke() == true

    /**
     * How the last model call on this engine ended: [LearningAiStatus.Ready] when an answer came back,
     * otherwise the typed reason (allowance used up, no AI, or a failure). Screens read it right after
     * the call they made. One engine serves one screen's calls, so it is not a per-call record.
     */
    @Volatile var lastStatus: LearningAiStatus = LearningAiStatus.Ready
        private set

    /** True when the last answer was made on this phone because the cloud route did not answer. */
    @Volatile var lastMadeLocally: Boolean = false
        private set

    private suspend fun generateText(prompt: String, responseSchema: String? = null): String? {
        lastMadeLocally = false
        val forceLocal = shouldPreferLocal()
        val t = transport
        var cloudStatus: LearningAiStatus? = null
        if (!forceLocal && t != null && t.refreshConfigured()) {
            val req = GeminiRequest(
                modelId = com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL,
                systemInstruction = "You are an expert academic tutor and university professor.",
                prompt = prompt,
                responseSchema = responseSchema,
                jsonOutput = true,
                thinkingLevel = com.craftflowtechnologies.meetingmind.ai.cloud.ThinkingLevel.MEDIUM
            )
            when (val res = t.execute(req)) {
                is AiResult.Success -> {
                    lastStatus = LearningAiStatus.Ready
                    return res.value
                }
                else -> cloudStatus = LearningAiStatus.of(res)
            }
        }
        // The cloud route did not answer (allowance used up, failure, or offline): try the on-device model once.
        val model = fallbackModel ?: fallbackModelProvider?.invoke()
        if (model == null) {
            lastStatus = cloudStatus ?: LearningAiStatus.NoAi(LearningAiStatus.NO_AI_MESSAGE)
            return null
        }
        return try {
            val res = if (responseSchema != null) {
                model.generateStructured(prompt, responseSchema, maxOutputTokens = 1500)
            } else {
                model.generate(prompt, maxOutputTokens = 1500)
            }
            when (res) {
                is AiResult.Success -> {
                    lastStatus = LearningAiStatus.Ready
                    lastMadeLocally = cloudStatus != null
                    res.value
                }
                else -> {
                    lastStatus = cloudStatus ?: LearningAiStatus.of(res)
                    null
                }
            }
        } finally {
            if (fallbackModel == null) onLocalFinished?.invoke()
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
        val maxTranscriptChars = if (!shouldPreferLocal() && transport?.refreshConfigured() == true) 90_000 else 12_000
        val prompt = buildString {
            appendLine("You are an expert university professor and master tutor in ${domain.displayName}.")
            appendLine("Analyze the following lecture transcript and synthesize structured study materials.")
            appendLine("Extract key concepts, definitions, active-recall practice questions, exam hints, and explanations for confusing points.")
            appendLine("Vocabulary to prioritize: ${domain.vocabularyHints().take(12).joinToString(", ")}")
            appendLine("Transcript:")
            appendLine(transcriptText.take(maxTranscriptChars))
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

        val response = generateText(prompt, SYNTHESIS_SCHEMA)
        if (response != null) {
            val parsed = runCatching { parseSynthesisJson(sessionId, stripMarkdownCodeFences(response), domain) }.getOrNull()
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

        val res = generateText(prompt, TEACHBACK_SCHEMA)
        if (res != null) {
            val eval = runCatching { parseTeachBackJson(stripMarkdownCodeFences(res), rubric) }.getOrNull()
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

        val res = generateText(prompt, MISTAKE_SCHEMA)
        if (res != null) {
            val obj = runCatching { JSONObject(stripMarkdownCodeFences(res)) }.getOrNull()
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

        val res = generateText(prompt, ENRICH_SCHEMA)
        val enrichedText = if (res != null) {
            val obj = runCatching { JSONObject(stripMarkdownCodeFences(res)) }.getOrNull()
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
        val cleanedJson = stripMarkdownCodeFences(json)
        val obj = JSONObject(cleanedJson)
        val summary = obj.optString("summary", "Lecture synthesis")
        val conceptsList = mutableListOf<LearningConcept>()
        val conceptsArr = obj.optJSONArray("concepts") ?: JSONArray()
        for (i in 0 until conceptsArr.length()) {
            val c = conceptsArr.getJSONObject(i)
            val name = c.optString("name", "Concept").ifBlank { "Concept" }
            val desc = c.optString("description", "")
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
            val prompt = q.optString("prompt", "")
            val answer = q.optString("answer", "")
            val type = when (q.optString("type")) {
                "WORKED_PROBLEM" -> LearningActivityType.WORKED_PROBLEM
                "APPLICATION" -> LearningActivityType.APPLICATION
                else -> LearningActivityType.RECALL
            }
            val conceptKey = q.optString("conceptId").ifBlank {
                q.optString("concept").ifBlank {
                    q.optString("conceptName").ifBlank {
                        q.optString("topic")
                    }
                }
            }
            val matchedConceptId = if (conceptKey.isNotBlank()) {
                conceptsList.firstOrNull { it.id.equals(conceptKey, ignoreCase = true) || it.name.equals(conceptKey, ignoreCase = true) }?.id
                    ?: conceptsList.firstOrNull { it.name.contains(conceptKey, ignoreCase = true) || conceptKey.contains(it.name, ignoreCase = true) }?.id
            } else {
                conceptsList.firstOrNull { c -> c.name.isNotBlank() && (prompt.contains(c.name, ignoreCase = true) || answer.contains(c.name, ignoreCase = true)) }?.id
            } ?: conceptsList.firstOrNull()?.id

            activitiesList.add(
                LearningActivity(
                    id = NoteRepository.newId("activity"),
                    sessionId = sessionId,
                    conceptId = matchedConceptId,
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
        for (i in 0 until hintsArr.length()) {
            val hint = hintsArr.optString(i)
            if (hint.isNotBlank()) examHints.add(hint)
        }

        val confusionUnpacks = mutableListOf<String>()
        val unpacksArr = obj.optJSONArray("confusionUnpacks") ?: JSONArray()
        for (i in 0 until unpacksArr.length()) {
            val unpack = unpacksArr.optString(i)
            if (unpack.isNotBlank()) confusionUnpacks.add(unpack)
        }

        return LectureSynthesis(
            summary = summary,
            concepts = conceptsList,
            activities = activitiesList,
            examHints = examHints,
            confusionUnpacks = confusionUnpacks
        )
    }

    private fun parseTeachBackJson(json: String, rubric: List<TeachBackCriterion>): TeachBackEvaluation {
        val cleanedJson = stripMarkdownCodeFences(json)
        val obj = JSONObject(cleanedJson)
        val overall = when (obj.optString("overall")) {
            "COVERED" -> TeachBackCoverage.COVERED
            "PARTLY_COVERED" -> TeachBackCoverage.PARTLY_COVERED
            else -> TeachBackCoverage.NOT_YET_DEMONSTRATED
        }
        val score = obj.optInt("score", 70)
        val summary = obj.optString("summary", "Teach-back evaluation completed.")
        val missing = mutableListOf<String>()
        val missingArr = obj.optJSONArray("missing") ?: JSONArray()
        for (i in 0 until missingArr.length()) {
            val miss = missingArr.optString(i)
            if (miss.isNotBlank()) missing.add(miss)
        }
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

    suspend fun generateConceptVisual(
        context: Context,
        conceptTitle: String,
        definition: String,
        domain: SubjectDomain,
        preferredKind: LearningVisualKind = LearningVisualKind.AUTO,
        formulaLatex: String? = null
    ): LearningVisualSpec {
        val chosenKind = if (preferredKind != LearningVisualKind.AUTO) {
            preferredKind
        } else {
            when {
                !formulaLatex.isNullOrBlank() || domain == SubjectDomain.MATH_QUANT || domain == SubjectDomain.ENGINEERING -> LearningVisualKind.FUNCTION_PLOT
                domain == SubjectDomain.ECONOMICS || domain == SubjectDomain.ACCOUNTING_FINANCE -> LearningVisualKind.LINE_CHART
                domain == SubjectDomain.SCIENCES_MED -> LearningVisualKind.TIMELINE
                domain == SubjectDomain.PROGRAMMING_CS || domain == SubjectDomain.HUMANITIES_LAW -> LearningVisualKind.MERMAID_DIAGRAM
                else -> LearningVisualKind.BAR_CHART
            }
        }

        val prompt = buildString {
            appendLine("You are an expert academic visual instructional designer in ${domain.displayName}.")
            appendLine("Generate a structured visual specification for the academic concept: \"$conceptTitle\".")
            appendLine("Definition: $definition")
            if (!formulaLatex.isNullOrBlank()) {
                appendLine("Latex Formula: $formulaLatex")
            }
            appendLine("Selected Visual Kind: ${chosenKind.name}")
            appendLine("Respond strictly with JSON containing kind, title, subtitle, xLabel, yLabel, equations, xMin, xMax, series, timeline, mermaidCode, and caption:")
            appendLine("""
                {
                  "kind": "${chosenKind.name}",
                  "title": "$conceptTitle",
                  "subtitle": "Conceptual Overview",
                  "xLabel": "x",
                  "yLabel": "y",
                  "equations": ["x^2"],
                  "xMin": -5.0,
                  "xMax": 5.0,
                  "series": [
                    {
                      "name": "$conceptTitle",
                      "points": [{"label": "A", "value": 10.0}, {"label": "B", "value": 25.0}]
                    }
                  ],
                  "timeline": [
                    {"step": "1", "title": "Initial", "detail": "Setup"}
                  ],
                  "mermaidCode": "graph TD\n  A --> B",
                  "caption": "Key visual takeaway"
                }
            """.trimIndent())
        }

        val response = generateText(prompt, VISUAL_SCHEMA)
        if (response != null) {
            val parsed = runCatching { parseVisualSpecJson(stripMarkdownCodeFences(response), conceptTitle, chosenKind) }.getOrNull()
            if (parsed != null) return parsed
        }

        return fallbackVisualSpec(conceptTitle, definition, domain, chosenKind, formulaLatex)
    }

    suspend fun generateDiagramImage(
        context: Context,
        conceptTitle: String,
        definition: String,
        domain: SubjectDomain
    ): AiResult<String> {
        val prefs = UserPreferencesManager(context).preferencesFlow.first()
        val credStore = GeminiCredentialStore(context)
        val interactions = GeminiInteractions(credStore)
        if (prefs.processingProfile != ProcessingProfile.INTERNET || !interactions.isConfigured()) {
            return AiResult.ModelUnavailable(
                "gemini-3.1-flash-lite-image",
                "Turn on Internet mode with your Gemini key to generate diagram images."
            )
        }
        val prompt = "Create a clear, pedagogical academic diagram illustration explaining the concept of '$conceptTitle' in the field of ${domain.displayName}. Concept details: ${definition.take(300)}. Clean educational infographic style, high contrast, elegant and focused."
        val req = GeminiInteractions.imageRequest("gemini-3.1-flash-lite-image", prompt, "16:9")
        val result = interactions.media(req, "image")
        return when (result) {
            is AiResult.Success -> {
                val media = result.value
                val ext = if (media.mimeType.contains("jpeg") || media.mimeType.contains("jpg")) "jpg" else "png"
                val outDir = File(context.filesDir, "learning_visuals").apply { mkdirs() }
                val outFile = File(outDir, "diagram_${System.currentTimeMillis()}.$ext")
                outFile.writeBytes(media.bytes)
                AiResult.Success(outFile.absolutePath)
            }
            else -> result as AiResult<String>
        }
    }

    suspend fun explainSimply(
        context: Context,
        conceptTitle: String,
        definition: String,
        domain: SubjectDomain
    ): SimpleExplanation {
        val prompt = buildString {
            appendLine("You are a master teacher using the Feynman technique in ${domain.displayName}.")
            appendLine("Explain the following concept so a 12-year-old can deeply understand it intuitively.")
            appendLine("Concept: $conceptTitle")
            appendLine("Academic Definition: $definition")
            appendLine("Respond strictly with JSON:")
            appendLine("""
                {
                  "analogy": "A clear, intuitive physical analogy from the real world",
                  "everydayExample": "A practical scenario showing how this works in daily life",
                  "keyTakeaway": "One punchy, memorable sentence summarizing the core insight"
                }
            """.trimIndent())
        }

        val res = generateText(prompt, EXPLAIN_SIMPLY_SCHEMA)
        if (res != null) {
            val parsed = runCatching {
                val obj = JSONObject(stripMarkdownCodeFences(res))
                SimpleExplanation(
                    analogy = obj.optString("analogy", "").trim(),
                    everydayExample = obj.optString("everydayExample", "").trim(),
                    keyTakeaway = obj.optString("keyTakeaway", "").trim()
                )
            }.getOrNull()
            if (parsed != null && parsed.analogy.isNotBlank() && parsed.keyTakeaway.isNotBlank()) {
                return parsed
            }
        }

        return SimpleExplanation(
            analogy = "Think of $conceptTitle like a specialized team where each member has a precise role, working together like gears in a clock.",
            everydayExample = "Just like adjusting a thermostat smoothly changes room temperature, $conceptTitle balances inputs to achieve the desired outcome.",
            keyTakeaway = "$conceptTitle is the underlying rule that keeps the whole system stable and predictable."
        )
    }

    /**
     * Ask Learning: a grounded answer from [ranked] passages. Falls back to quoting the closest
     * passages when no model answers, and never returns an uncited claim as "from your lecture".
     */
    suspend fun answerFromSources(question: String, ranked: List<Pair<AskPassage, Double>>): LearningAnswer {
        // No model call is made when there is nothing to ground an answer in, so the status is reset first.
        lastStatus = LearningAiStatus.Ready
        lastMadeLocally = false
        val passages = ranked.map { it.first }
        val extractive = LearningAsk.extractiveAnswer(question, ranked)
        if (passages.isEmpty()) return extractive
        val raw = runCatching { generateText(LearningAsk.groundedPrompt(question, passages), LearningAsk.ANSWER_SCHEMA) }.getOrNull()
            ?: return extractive
        val obj = runCatching { JSONObject(stripMarkdownCodeFences(raw)) }.getOrNull() ?: return extractive
        val text = obj.optString("answer").trim()
        if (text.isBlank()) return extractive
        val cited = LearningAsk.citedIn(text, passages)
        return when {
            !obj.optBoolean("inSource", true) -> LearningAnswer(
                text = text, kind = AnswerKind.NOT_IN_SOURCE, cited = emptyList(), closest = passages.firstOrNull()
            )
            // An answer that cites nothing can't be checked, so show the passages instead.
            cited.isEmpty() -> extractive
            else -> LearningAnswer(
                text = text, kind = AnswerKind.FROM_SOURCE, cited = cited,
                unclearSource = cited.any { (it.confidence ?: 1f) < LearningAsk.UnclearConfidence }
            )
        }
    }

    /** General knowledge, only when the student asks for it. Null when no model is available. */
    suspend fun answerGenerally(question: String): LearningAnswer? {
        lastStatus = LearningAiStatus.Ready
        lastMadeLocally = false
        val raw = runCatching { generateText(LearningAsk.generalPrompt(question), LearningAsk.ANSWER_SCHEMA) }.getOrNull() ?: return null
        val text = runCatching { JSONObject(stripMarkdownCodeFences(raw)).optString("answer").trim() }.getOrNull()
        if (text.isNullOrBlank()) return null
        return LearningAnswer(text = text, kind = AnswerKind.GENERAL, cited = emptyList())
    }

    suspend fun generateHintLadder(
        context: Context,
        prompt: String,
        expectedAnswer: String,
        domain: SubjectDomain
    ): List<String> {
        val query = buildString {
            appendLine("You are a Socratic academic tutor in ${domain.displayName}.")
            appendLine("Provide exactly 3 progressive hints for a student solving this problem:")
            appendLine("Problem: $prompt")
            appendLine("Expected Answer: $expectedAnswer")
            appendLine("Hint 1: A gentle nudge pointing to the core concept.")
            appendLine("Hint 2: An intermediate hint pointing to the formula or relationship.")
            appendLine("Hint 3: A strong hint detailing the next immediate step.")
            appendLine("Never state the expected answer, or any part of it, in a hint.")
            appendLine("Respond strictly with JSON:")
            appendLine("""{"hints": ["Hint 1", "Hint 2", "Hint 3"]}""")
        }

        val res = generateText(query, HINT_LADDER_SCHEMA)
        if (res != null) {
            val parsed = runCatching {
                val obj = JSONObject(stripMarkdownCodeFences(res))
                val arr = obj.optJSONArray("hints") ?: JSONArray()
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val h = arr.optString(i).trim()
                    if (h.isNotBlank() && !hintRevealsAnswer(h, expectedAnswer)) list.add(h)
                }
                if (list.size >= 3) list.take(3) else null
            }.getOrNull()
            if (parsed != null) return parsed
        }

        // The offline fallback must never contain any part of the expected answer: a hint that
        // quotes the answer turns retrieval practice into reading (design spec §13 #5).
        return listOf(
            "Name the main definition or rule in ${domain.displayName} that this question is about.",
            "Write down what the question gives you and what it asks for, before you work anything out.",
            "Look back at where this came up in the lecture, then try the last step on your own."
        )
    }

    private fun parseVisualSpecJson(json: String, defaultTitle: String, fallbackKind: LearningVisualKind): LearningVisualSpec {
        val obj = JSONObject(stripMarkdownCodeFences(json))
        val title = obj.optString("title").ifBlank { defaultTitle }
        val subtitle = obj.optString("subtitle", "")
        val kindStr = obj.optString("kind")
        val kind = runCatching { LearningVisualKind.valueOf(kindStr) }.getOrDefault(fallbackKind)
        val xLabel = obj.optString("xLabel", "")
        val yLabel = obj.optString("yLabel", "")
        val xMin = obj.optDouble("xMin", -5.0)
        val xMax = obj.optDouble("xMax", 5.0)
        val caption = obj.optString("caption", "")
        val mermaidCode = obj.optString("mermaidCode", "")

        val equations = mutableListOf<String>()
        val eqArr = obj.optJSONArray("equations")
        if (eqArr != null) {
            for (i in 0 until eqArr.length()) {
                val eq = eqArr.optString(i)
                if (eq.isNotBlank()) equations.add(eq)
            }
        }

        val seriesList = mutableListOf<ChartSeries>()
        val seriesArr = obj.optJSONArray("series")
        if (seriesArr != null) {
            for (i in 0 until seriesArr.length()) {
                val sObj = seriesArr.getJSONObject(i)
                val sName = sObj.optString("name", "Data")
                val ptsArr = sObj.optJSONArray("points") ?: JSONArray()
                val pts = mutableListOf<ChartPoint>()
                for (j in 0 until ptsArr.length()) {
                    val pObj = ptsArr.getJSONObject(j)
                    pts.add(ChartPoint(pObj.optString("label", ""), pObj.optDouble("value", 0.0)))
                }
                seriesList.add(ChartSeries(sName, pts))
            }
        }

        val timeline = mutableListOf<TimelineNode>()
        val timeArr = obj.optJSONArray("timeline")
        if (timeArr != null) {
            for (i in 0 until timeArr.length()) {
                val tObj = timeArr.getJSONObject(i)
                timeline.add(
                    TimelineNode(
                        step = tObj.optString("step", "${i + 1}"),
                        title = tObj.optString("title", ""),
                        detail = tObj.optString("detail", "")
                    )
                )
            }
        }

        return LearningVisualSpec(
            id = NoteRepository.newId("visual"),
            title = title,
            subtitle = subtitle,
            kind = kind,
            xLabel = xLabel,
            yLabel = yLabel,
            series = seriesList,
            equations = equations,
            xMin = xMin,
            xMax = xMax,
            timeline = timeline,
            mermaidCode = mermaidCode,
            caption = caption
        )
    }

    private fun fallbackVisualSpec(
        conceptTitle: String,
        definition: String,
        domain: SubjectDomain,
        kind: LearningVisualKind,
        formulaLatex: String?
    ): LearningVisualSpec {
        val id = NoteRepository.newId("visual")
        return when (kind) {
            LearningVisualKind.FUNCTION_PLOT -> {
                val eq = if (!formulaLatex.isNullOrBlank()) {
                    formulaLatex
                        .replace("\\sin", "sin")
                        .replace("\\cos", "cos")
                        .replace("\\tan", "tan")
                        .replace("\\sqrt", "sqrt")
                        .replace("\\ln", "ln")
                        .replace("\\exp", "exp")
                        .replace("{", "(")
                        .replace("}", ")")
                        .replace(" ", "")
                        .replace("$", "")
                } else {
                    "x^2 - 4"
                }
                LearningVisualSpec(
                    id = id,
                    title = conceptTitle,
                    subtitle = "Mathematical Curve",
                    kind = LearningVisualKind.FUNCTION_PLOT,
                    xLabel = "x",
                    yLabel = "f(x)",
                    equations = listOf(eq),
                    xMin = -5.0,
                    xMax = 5.0,
                    caption = "Functional representation of $conceptTitle over the interval [-5, 5]."
                )
            }
            LearningVisualKind.BAR_CHART -> {
                LearningVisualSpec(
                    id = id,
                    title = conceptTitle,
                    subtitle = "Distribution Breakdown",
                    kind = LearningVisualKind.BAR_CHART,
                    xLabel = "Category",
                    yLabel = "Relative Magnitude",
                    series = listOf(
                        ChartSeries(
                            name = conceptTitle,
                            points = listOf(
                                ChartPoint("Baseline", 25.0),
                                ChartPoint("Phase 1", 45.0),
                                ChartPoint("Peak", 85.0),
                                ChartPoint("Final", 60.0)
                            )
                        )
                    ),
                    caption = "Distribution levels across key stages of $conceptTitle."
                )
            }
            LearningVisualKind.LINE_CHART -> {
                LearningVisualSpec(
                    id = id,
                    title = conceptTitle,
                    subtitle = "Dynamic Trend",
                    kind = LearningVisualKind.LINE_CHART,
                    xLabel = "Time / Index",
                    yLabel = "Intensity",
                    series = listOf(
                        ChartSeries(
                            name = "Observed Trend",
                            points = listOf(
                                ChartPoint("t0", 10.0),
                                ChartPoint("t1", 28.0),
                                ChartPoint("t2", 52.0),
                                ChartPoint("t3", 84.0),
                                ChartPoint("t4", 95.0)
                            )
                        )
                    ),
                    caption = "Progressive trajectory observed in $conceptTitle."
                )
            }
            LearningVisualKind.PIE_CHART -> {
                LearningVisualSpec(
                    id = id,
                    title = conceptTitle,
                    subtitle = "Component Share",
                    kind = LearningVisualKind.PIE_CHART,
                    series = listOf(
                        ChartSeries(
                            name = "Components",
                            points = listOf(
                                ChartPoint("Component A", 45.0),
                                ChartPoint("Component B", 35.0),
                                ChartPoint("Residual", 20.0)
                            )
                        )
                    ),
                    caption = "Proportional composition of $conceptTitle."
                )
            }
            LearningVisualKind.TIMELINE -> {
                LearningVisualSpec(
                    id = id,
                    title = conceptTitle,
                    subtitle = "Process Sequence",
                    kind = LearningVisualKind.TIMELINE,
                    timeline = listOf(
                        TimelineNode("Step 1", "Initial State", "Baseline conditions established for $conceptTitle."),
                        TimelineNode("Step 2", "Primary Reaction", "Key interactions and mechanisms engage."),
                        TimelineNode("Step 3", "System Stabilization", "Final state and persistent outcomes take effect.")
                    ),
                    caption = "Step-by-step evolution of $conceptTitle."
                )
            }
            LearningVisualKind.MERMAID_DIAGRAM -> {
                val safeTitle = conceptTitle.take(24).replace("\"", "").replace("'", "")
                val mermaid = """
                    graph TD
                        A["$safeTitle"] --> B["Core Mechanism"]
                        B --> C["Transformation"]
                        C --> D["Observable Outcome"]
                """.trimIndent()
                LearningVisualSpec(
                    id = id,
                    title = conceptTitle,
                    subtitle = "Conceptual Flow Diagram",
                    kind = LearningVisualKind.MERMAID_DIAGRAM,
                    mermaidCode = mermaid,
                    caption = "Structural relationship diagram for $conceptTitle."
                )
            }
            LearningVisualKind.AI_IMAGE, LearningVisualKind.AUTO -> {
                val safeTitle = conceptTitle.take(24).replace("\"", "").replace("'", "")
                LearningVisualSpec(
                    id = id,
                    title = conceptTitle,
                    subtitle = "Concept Flow",
                    kind = LearningVisualKind.MERMAID_DIAGRAM,
                    mermaidCode = "graph TD\n    A[\"$safeTitle\"] --> B[\"Mechanism\"]\n    B --> C[\"Outcome\"]",
                    caption = "Structural overview of $conceptTitle."
                )
            }
        }
    }

    private companion object {
        const val SYNTHESIS_SCHEMA = """{"type":"object","properties":{"summary":{"type":"string"},"concepts":{"type":"array","items":{"type":"object","properties":{"name":{"type":"string"},"description":{"type":"string"},"importance":{"type":"string"},"formula":{"type":"string"}},"required":["name","description"]}},"questions":{"type":"array","items":{"type":"object","properties":{"prompt":{"type":"string"},"answer":{"type":"string"},"type":{"type":"string"},"difficulty":{"type":"string"}},"required":["prompt","answer"]}},"examHints":{"type":"array","items":{"type":"string"}},"confusionUnpacks":{"type":"array","items":{"type":"string"}}},"required":["summary","concepts","questions"]}"""
        const val TEACHBACK_SCHEMA = """{"type":"object","properties":{"overall":{"type":"string"},"score":{"type":"integer"},"summary":{"type":"string"},"missing":{"type":"array","items":{"type":"string"}},"socraticFollowUp":{"type":"string"},"criteria":{"type":"array","items":{"type":"object","properties":{"claim":{"type":"string"},"status":{"type":"string"},"feedback":{"type":"string"}},"required":["claim","status"]}}},"required":["overall","score","summary"]}"""
        const val MISTAKE_SCHEMA = """{"type":"object","properties":{"rootMisconception":{"type":"string"},"correctMentalModel":{"type":"string"},"stepByStepCorrection":{"type":"string"}},"required":["rootMisconception","correctMentalModel","stepByStepCorrection"]}"""
        const val ENRICH_SCHEMA = """{"type":"object","properties":{"explanation":{"type":"string"},"formula":{"type":"string"},"steps":{"type":"array","items":{"type":"string"}}},"required":["explanation"]}"""
        const val VISUAL_SCHEMA = """{"type":"object","properties":{"kind":{"type":"string"},"title":{"type":"string"},"subtitle":{"type":"string"},"xLabel":{"type":"string"},"yLabel":{"type":"string"},"equations":{"type":"array","items":{"type":"string"}},"xMin":{"type":"number"},"xMax":{"type":"number"},"series":{"type":"array","items":{"type":"object","properties":{"name":{"type":"string"},"points":{"type":"array","items":{"type":"object","properties":{"label":{"type":"string"},"value":{"type":"number"}},"required":["label","value"]}}},"required":["name","points"]}},"timeline":{"type":"array","items":{"type":"object","properties":{"step":{"type":"string"},"title":{"type":"string"},"detail":{"type":"string"}},"required":["step","title","detail"]}},"mermaidCode":{"type":"string"},"caption":{"type":"string"}},"required":["title","kind"]}"""
        const val EXPLAIN_SIMPLY_SCHEMA = """{"type":"object","properties":{"analogy":{"type":"string"},"everydayExample":{"type":"string"},"keyTakeaway":{"type":"string"}},"required":["analogy","everydayExample","keyTakeaway"]}"""
        const val HINT_LADDER_SCHEMA = """{"type":"object","properties":{"hints":{"type":"array","items":{"type":"string"}}},"required":["hints"]}"""
    }
}

/** True when a hint quotes the expected answer (when the answer is long enough to tell). */
internal fun hintRevealsAnswer(hint: String, expectedAnswer: String): Boolean {
    val answer = expectedAnswer.trim().lowercase()
    if (answer.length < 4) return false
    return hint.lowercase().contains(answer)
}
