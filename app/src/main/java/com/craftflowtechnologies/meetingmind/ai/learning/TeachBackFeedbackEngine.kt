package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.notes.SourcePassage
import org.json.JSONArray
import org.json.JSONObject

/** A claim the learner can demonstrate, always tied to source passages already in the session. */
data class TeachBackRubricClaim(
    val id: String,
    val text: String,
    val sourceIds: List<String>
)

enum class TeachBackCoverage(val label: String) {
    COVERED("Covered"), PARTLY_COVERED("Partly covered"), NOT_YET_DEMONSTRATED("Not yet demonstrated")
}

data class TeachBackClaimFeedback(
    val claimId: String,
    val coverage: TeachBackCoverage,
    val explanation: String,
    val sourceIds: List<String>
)

/**
 * Typed result deliberately contains no score. [modelId] and [contractVersion] make a later
 * regeneration auditable without pretending that model output is a grade.
 */
data class TeachBackFeedback(
    val overall: TeachBackCoverage,
    val coveredClaims: List<TeachBackClaimFeedback>,
    val missingOrMisunderstoodClaims: List<TeachBackClaimFeedback>,
    val nextPrompt: String,
    val modelId: String,
    val contractVersion: String = CONTRACT_VERSION
) {
    companion object { const val CONTRACT_VERSION = "teach-back-feedback-v1" }
}

/**
 * Strictly grounded Teach-Back evaluator. It accepts only claim ids and source ids supplied by
 * the caller; unknown claims/citations are rejected instead of being displayed as lecture fact.
 */
class TeachBackFeedbackEngine(
    private val languageModel: LanguageModel,
    private val modelId: String = "configured-learning-model",
    private val maxOutputTokens: Int = 1024
) {
    suspend fun assess(
        explanation: String,
        rubric: List<TeachBackRubricClaim>,
        passages: List<SourcePassage>
    ): AiResult<TeachBackFeedback> {
        if (explanation.isBlank()) return AiResult.Failed("There is no explanation to assess yet.")
        if (rubric.isEmpty() || passages.isEmpty()) return AiResult.Failed("Teach-Back needs cited concept evidence before it can give feedback.")
        if (rubric.any { it.text.isBlank() || it.sourceIds.isEmpty() }) return AiResult.Failed("Teach-Back rubric has incomplete source evidence.")

        val knownSources = passages.associateBy { it.id }
        if (rubric.any { claim -> claim.sourceIds.any { it !in knownSources } }) {
            return AiResult.Failed("Teach-Back rubric refers to source evidence that is no longer available.")
        }
        val response = when (val result = languageModel.generate(prompt(explanation, rubric, knownSources), maxOutputTokens)) {
            is AiResult.Success -> result.value
            else -> return AiResult.Failed(result.describeFailure() ?: "Teach-Back feedback is unavailable.")
        }
        val json = extractJsonObject(response) ?: return AiResult.Failed("Teach-Back feedback was not in the expected format.")
        return parse(json, rubric, knownSources.keys)
    }

    internal fun parse(
        json: JSONObject,
        rubric: List<TeachBackRubricClaim>,
        knownSourceIds: Set<String>
    ): AiResult<TeachBackFeedback> {
        val rubricById = rubric.associateBy { it.id }
        val feedback = json.optJSONArray("claims") ?: return AiResult.Failed("Teach-Back feedback did not include claim feedback.")
        val parsed = mutableListOf<TeachBackClaimFeedback>()
        for (i in 0 until feedback.length()) {
            val item = feedback.optJSONObject(i) ?: return AiResult.Failed("Teach-Back claim feedback was invalid.")
            val claimId = item.optString("claimId").trim()
            val claim = rubricById[claimId] ?: return AiResult.Failed("Teach-Back feedback referenced an unknown claim.")
            val coverage = when (item.optString("coverage").trim().uppercase()) {
                "COVERED" -> TeachBackCoverage.COVERED
                "PARTLY_COVERED" -> TeachBackCoverage.PARTLY_COVERED
                "NOT_YET_DEMONSTRATED" -> TeachBackCoverage.NOT_YET_DEMONSTRATED
                else -> return AiResult.Failed("Teach-Back feedback used an unsupported coverage state.")
            }
            val explanation = item.optString("explanation").trim()
            if (explanation.isBlank()) return AiResult.Failed("Teach-Back feedback omitted an explanation.")
            val sources = ids(item.optJSONArray("sources"))
            // A model may cite only the selected claim's existing evidence, never a plausible-looking id.
            if (sources.isEmpty() || sources.any { it !in knownSourceIds || it !in claim.sourceIds }) {
                return AiResult.Failed("Teach-Back feedback contained invalid source citations.")
            }
            parsed += TeachBackClaimFeedback(claimId, coverage, explanation, sources)
        }
        if (parsed.map { it.claimId }.toSet().size != rubric.size || parsed.size != rubric.size) {
            return AiResult.Failed("Teach-Back feedback must assess every cited rubric claim exactly once.")
        }
        val nextPrompt = json.optString("nextPrompt").trim()
        if (nextPrompt.isBlank()) return AiResult.Failed("Teach-Back feedback omitted a next prompt.")
        val overall = when {
            parsed.all { it.coverage == TeachBackCoverage.COVERED } -> TeachBackCoverage.COVERED
            parsed.any { it.coverage == TeachBackCoverage.COVERED || it.coverage == TeachBackCoverage.PARTLY_COVERED } -> TeachBackCoverage.PARTLY_COVERED
            else -> TeachBackCoverage.NOT_YET_DEMONSTRATED
        }
        return AiResult.Success(TeachBackFeedback(overall, parsed.filter { it.coverage == TeachBackCoverage.COVERED }, parsed.filter { it.coverage != TeachBackCoverage.COVERED }, nextPrompt, modelId))
    }

    private fun prompt(explanation: String, rubric: List<TeachBackRubricClaim>, passages: Map<String, SourcePassage>) = buildString {
        appendLine("Assess a learner's spoken explanation against this cited rubric. Do not grade or use percentages. Do not infer facts beyond the sources.")
        appendLine("Return JSON only: {\"claims\":[{\"claimId\":string,\"coverage\":\"COVERED|PARTLY_COVERED|NOT_YET_DEMONSTRATED\",\"explanation\":string,\"sources\":[sourceId]}],\"nextPrompt\":string}")
        appendLine("LEARNER EXPLANATION: $explanation")
        appendLine("RUBRIC:")
        rubric.forEach { appendLine("${it.id}: ${it.text} [${it.sourceIds.joinToString()}]") }
        appendLine("SOURCES:")
        passages.forEach { (id, p) -> appendLine("[$id] ${p.text}") }
    }

    private fun ids(array: JSONArray?): List<String> = buildList {
        array ?: return@buildList
        for (i in 0 until array.length()) array.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add)
    }.distinct()

    private fun extractJsonObject(text: String): JSONObject? = runCatching {
        val start = text.indexOf('{'); val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) null else JSONObject(text.substring(start, end + 1))
    }.getOrNull()
}
