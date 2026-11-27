package com.craftflowtechnologies.meetingmind.ai.benchmark

import com.craftflowtechnologies.meetingmind.ai.assistant.AssistantToolStep
import com.craftflowtechnologies.meetingmind.ai.assistant.FaithGuard
import com.craftflowtechnologies.meetingmind.ai.retrieval.TranscriptRetriever
import com.craftflowtechnologies.meetingmind.core.model.Speaker
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import org.json.JSONObject

/**
 * Deterministic 2026 intelligence evaluation harness covering four regression axes:
 * 1. Grounded citation precision (timestamp `[mm:ss]` / source key `[n]` hallucination rate).
 * 2. Strict `responseSchema` JSON structural validity.
 * 3. Faith / theology safety boundary (`FaithGuard` direct speech suppression vs quoted reports).
 * 4. Multi-step agentic tool-call precision, recall, and completion rate.
 * 5. Hybrid RAG retrieval Recall@K.
 */
object IntelligenceBenchmark {

    data class CitationScore(val citedCount: Int, val validCount: Int) {
        val precision: Double get() = if (citedCount == 0) 1.0 else validCount.toDouble() / citedCount
    }

    data class ToolTrajectoryScore(
        val expectedCount: Int,
        val matchedCount: Int,
        val completedCount: Int
    ) {
        val recall: Double get() = if (expectedCount == 0) 1.0 else matchedCount.toDouble() / expectedCount
        val completionRate: Double get() = if (matchedCount == 0) 1.0 else completedCount.toDouble() / matchedCount
    }

    private val MARKER_REGEX = Regex("\\[(\\d+|\\d{1,2}:\\d{2})]")

    fun scoreCitations(generatedText: String, validMarkers: Set<String>): CitationScore {
        val matches = MARKER_REGEX.findAll(generatedText).map { it.groupValues[1] }.toList()
        if (matches.isEmpty()) return CitationScore(0, 0)
        val valid = matches.count { it in validMarkers }
        return CitationScore(matches.size, valid)
    }

    fun validateJsonSchema(rawJson: String, requiredKeys: Set<String>): Boolean = runCatching {
        val obj = JSONObject(rawJson.trim())
        requiredKeys.all { key -> obj.has(key) && !obj.isNull(key) }
    }.getOrDefault(false)

    fun verifyFaithGuard(unsafeInputs: List<String>, safeQuotedInputs: List<String>): Boolean {
        val removedUnsafe = unsafeInputs.all { input -> FaithGuard.clean(input) != input && !FaithGuard.clean(input).contains("God is telling you", ignoreCase = true) }
        val keptSafe = safeQuotedInputs.all { input -> FaithGuard.clean(input) == input }
        return removedUnsafe && keptSafe
    }

    fun scoreToolTrajectory(expectedToolIds: List<String>, steps: List<AssistantToolStep>): ToolTrajectoryScore {
        val executedIds = steps.map { it.id }.toSet()
        val matched = expectedToolIds.count { it in executedIds }
        val completed = expectedToolIds.count { id -> steps.any { it.id == id && it.state == AssistantToolStep.State.DONE } }
        return ToolTrajectoryScore(expectedToolIds.size, matched, completed)
    }

    suspend fun evaluateRetrievalRecall(
        retriever: TranscriptRetriever,
        segments: List<TranscriptSegment>,
        query: String,
        expectedSegmentIds: Set<String>,
        speakers: List<Speaker> = emptyList()
    ): Double {
        if (expectedSegmentIds.isEmpty()) return 1.0
        val retrievedIds = retriever.retrieve(segments, query, speakers).map { it.segment.id }.toSet()
        return expectedSegmentIds.count { it in retrievedIds }.toDouble() / expectedSegmentIds.size
    }
}
