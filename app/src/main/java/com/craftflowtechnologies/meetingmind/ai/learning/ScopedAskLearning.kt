package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.notes.SourcePassage
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.model.LearningActivity
import com.craftflowtechnologies.meetingmind.core.model.LearningActivityType
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LearningAnswer(
    val answer: String,
    val citedEvidence: List<LearningEvidence>,
    val foundInSource: Boolean,
    val suggestedConcept: String? = null
)

class ScopedAskLearning(
    private val database: MeetMindDatabase,
    private val languageModel: LanguageModel
) {

    suspend fun ask(
        sessionId: String,
        question: String
    ): AiResult<LearningAnswer> = withContext(Dispatchers.IO) {
        val passages = gatherSessionPassages(sessionId)
        if (passages.isEmpty()) {
            return@withContext AiResult.Success(
                LearningAnswer(
                    answer = "There is no lecture transcript or note text in this session yet to answer from.",
                    citedEvidence = emptyList(),
                    foundInSource = false
                )
            )
        }

        val aliases = passages.mapIndexed { i, p -> "p${i + 1}" to p }.toMap()
        val prompt = buildAskPrompt(question, aliases)

        val response = when (val res = languageModel.generate(prompt, maxOutputTokens = 1024)) {
            is AiResult.Success -> res.value
            else -> return@withContext AiResult.Failed(res.describeFailure() ?: "Could not get an answer.")
        }

        val json = extractJsonObject(response)
            ?: return@withContext AiResult.Failed("Malformed model response.")

        val answerText = json.optString("answer").trim()
        val found = json.optBoolean("found", true)
        val concept = json.optString("suggestedConcept").trim().takeIf { it.isNotBlank() }
        val sourcesArr = json.optJSONArray("sources")
        val citedPassages = mutableListOf<SourcePassage>()

        if (sourcesArr != null) {
            for (i in 0 until sourcesArr.length()) {
                val alias = sourcesArr.optString(i).trim()
                aliases[alias]?.let { citedPassages.add(it) }
            }
        }

        val evidenceList = citedPassages.map { p ->
            LearningEvidence(
                noteId = p.noteId,
                segmentIds = listOf(p.id),
                quote = p.text.take(150)
            )
        }

        AiResult.Success(
            LearningAnswer(
                answer = if (found && answerText.isNotBlank()) answerText else "The lecture does not discuss '$question'.",
                citedEvidence = evidenceList,
                foundInSource = found && answerText.isNotBlank(),
                suggestedConcept = concept
            )
        )
    }

    /**
     * "Quiz me" handoff (PLAN_LEARNING §2, Release 1):
     * Turns an answer or concept discussed in tutor into a practice activity.
     */
    suspend fun createQuizMeActivity(
        sessionId: String,
        conceptName: String,
        answerText: String,
        evidence: List<LearningEvidence>
    ): AiResult<LearningActivity> = withContext(Dispatchers.IO) {
        val prompt = """
        Create a single multiple-choice practice question to test the student on:
        Concept: $conceptName
        Context: $answerText
        
        Output strictly JSON:
        {
          "prompt": "Question text without leaking the answer",
          "expectedAnswer": "Correct answer",
          "options": ["Correct answer", "Distractor 1", "Distractor 2", "Distractor 3"]
        }
        """.trimIndent()

        val response = when (val res = languageModel.generate(prompt, 512)) {
            is AiResult.Success -> res.value
            else -> return@withContext AiResult.Failed("Could not generate quiz question.")
        }

        val json = extractJsonObject(response)
            ?: return@withContext AiResult.Failed("Malformed quiz response.")

        val qPrompt = json.optString("prompt").trim()
        val expected = json.optString("expectedAnswer").trim()
        val optArr = json.optJSONArray("options")
        val options = mutableListOf<String>()
        if (optArr != null) {
            for (i in 0 until optArr.length()) {
                options.add(optArr.optString(i).trim())
            }
        }

        if (qPrompt.isBlank() || expected.isBlank() || options.isEmpty()) {
            return@withContext AiResult.Failed("Invalid quiz format generated.")
        }

        val now = System.currentTimeMillis()
        val activity = LearningActivity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            conceptId = null,
            type = LearningActivityType.MULTIPLE_CHOICE,
            prompt = qPrompt,
            expectedAnswer = expected,
            options = options.shuffled(),
            difficulty = "MEDIUM",
            evidence = evidence,
            isDiagnostic = false,
            createdAt = now,
            updatedAt = now
        )

        AiResult.Success(activity)
    }

    suspend fun gatherSessionPassages(sessionId: String): List<SourcePassage> = withContext(Dispatchers.IO) {
        val session = database.learningSessionDao().getById(sessionId) ?: return@withContext emptyList()
        val results = mutableListOf<SourcePassage>()

        // 1. Note Blocks
        val blocks = database.noteDao().getBlocks(session.noteId)
        blocks.filter { it.text.isNotBlank() }.forEach { b ->
            results.add(
                SourcePassage(
                    id = b.id,
                    text = b.text,
                    label = b.sectionKey ?: "Note section",
                    noteId = session.noteId
                )
            )
        }

        // 2. Transcript segments (if linked to a lecture recording)
        if (session.meetingId != null) {
            val segments = database.transcriptDao().getSegmentsForMeetingDirect(session.meetingId)
            segments.filter { it.text.isNotBlank() }.take(50).forEach { seg ->
                results.add(
                    SourcePassage(
                        id = seg.id,
                        text = seg.cleanedText ?: seg.text,
                        label = "Lecture @ ${seg.startMs / 1000}s",
                        noteId = session.noteId
                    )
                )
            }
        }

        results
    }

    private fun buildAskPrompt(question: String, aliases: Map<String, SourcePassage>): String = buildString {
        appendLine("You are an expert tutor answering a student's question based strictly on their lecture.")
        appendLine("Student Question: $question")
        appendLine()
        appendLine("FIDELITY RULES:")
        appendLine("1. Only use facts present in the source passages.")
        appendLine("2. If the lecture does not contain the answer, set 'found': false.")
        appendLine("3. Cite all used passages in 'sources'.")
        appendLine()
        appendLine("PASSAGES:")
        aliases.forEach { (key, p) -> appendLine("[$key]: ${p.text}") }
        appendLine()
        appendLine("Output strictly valid JSON:")
        appendLine("""
        {
          "answer": "Concise, grounded explanation",
          "found": true,
          "suggestedConcept": "The main concept name discussed, for Quiz Me handoff",
          "sources": ["p1"]
        }
        """.trimIndent())
    }

    private fun extractJsonObject(raw: String): JSONObject? = runCatching {
        val trimmed = raw.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start != -1 && end > start) JSONObject(trimmed.substring(start, end + 1)) else null
    }.getOrNull()
}
