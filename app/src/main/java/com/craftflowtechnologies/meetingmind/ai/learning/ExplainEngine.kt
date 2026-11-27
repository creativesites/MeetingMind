package com.craftflowtechnologies.meetingmind.ai.learning

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.core.learning.LearningAiStatus
import com.craftflowtechnologies.meetingmind.core.model.LearningConcept
import com.craftflowtechnologies.meetingmind.core.model.LearningEvidence
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import com.craftflowtechnologies.meetingmind.feature.learn.DiagramEdge
import com.craftflowtechnologies.meetingmind.feature.learn.DiagramSpec
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainMode
import com.craftflowtechnologies.meetingmind.feature.learn.ExplainState
import com.craftflowtechnologies.meetingmind.feature.learn.formatLectureTime
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Explain it another way" for one concept (PLAN_LEARNING §5.4). Grounded in the concept's own
 * lecture evidence: the prompt carries the quoted moments and the transcript around them, and the
 * answer is refused when there is no such evidence to ground it.
 */
class ExplainEngine(
    private val model: LanguageModel,
    private val maxOutputTokens: Int = DEFAULT_OUTPUT_TOKENS
) {

    suspend fun explain(
        concept: LearningConcept,
        mode: ExplainMode,
        transcript: List<TranscriptSegment>
    ): ExplainState {
        val excerpts = excerptsFor(concept, transcript)
        if (excerpts.isEmpty()) {
            return ExplainState(mode = mode, error = NO_EVIDENCE_MESSAGE)
        }
        val label = sourceLabel(concept.evidence)
        val reply = when (val res = model.generate(prompt(concept, mode, excerpts), maxOutputTokens)) {
            is AiResult.Success -> res.value
            else -> {
                val status = LearningAiStatus.of(res)
                // A used-up allowance is not an error to read out: the sheet shows the calm panel with plans.
                return if (status is LearningAiStatus.AllowanceUsedUp) ExplainState(mode = mode, allowance = status)
                else ExplainState(mode = mode, error = res.describeFailure() ?: GENERIC_FAILURE)
            }
        }
        val json = StudyKitGenerator.extractJsonObject(reply)
            ?: return ExplainState(mode = mode, error = MALFORMED_MESSAGE)

        return if (mode == ExplainMode.DRAW) {
            val diagram = parseDiagram(json)
                ?: return ExplainState(mode = mode, error = MALFORMED_MESSAGE)
            ExplainState(mode = mode, diagram = diagram, sourceLabel = label)
        } else {
            val text = parseText(json)
                ?: return ExplainState(mode = mode, error = MALFORMED_MESSAGE)
            ExplainState(mode = mode, text = text, sourceLabel = label)
        }
    }

    companion object {
        const val DEFAULT_OUTPUT_TOKENS = 768
        const val MAX_NODES = 6
        const val MAX_TEXT_CHARS = 1_200
        const val NO_EVIDENCE_MESSAGE = "There is no lecture evidence for this idea yet, so there is nothing to explain from."
        private const val GENERIC_FAILURE = "The explanation could not be made."
        private const val MALFORMED_MESSAGE = "The explanation could not be read. Try again."
        private const val CONTEXT_WINDOW_MS = 30_000L
        private const val MAX_EXCERPTS = 8

        /** "14:02 and 16:40": the lecture moments an explanation came from. Null when there are none. */
        fun sourceLabel(evidence: List<LearningEvidence>): String? {
            val times = evidence.mapNotNull { it.startMs }.distinct().sorted().map { formatLectureTime(it) }.take(3)
            return when (times.size) {
                0 -> null
                1 -> times[0]
                2 -> "${times[0]} and ${times[1]}"
                else -> "${times[0]}, ${times[1]} and ${times[2]}"
            }
        }

        /** The transcript lines around the concept's evidence, in lecture order. */
        fun excerptsFor(concept: LearningConcept, transcript: List<TranscriptSegment>): List<String> {
            val lines = transcript.sortedBy { it.startMs }
            val picked = linkedSetOf<TranscriptSegment>()
            for (e in concept.evidence) {
                val start = e.startMs ?: continue
                val end = e.endMs ?: start
                lines.filter { it.endMs >= start - CONTEXT_WINDOW_MS && it.startMs <= end + CONTEXT_WINDOW_MS }
                    .forEach { picked += it }
            }
            val quotes = concept.evidence.mapNotNull { it.quote.takeIf { q -> q.isNotBlank() } }
            val spoken = picked.take(MAX_EXCERPTS).map { "[${it.startMs}] ${(it.cleanedText ?: it.text).trim()}" }
            return if (quotes.isEmpty() && spoken.isEmpty()) emptyList() else quotes.map { "QUOTE: $it" } + spoken
        }

        fun parseText(json: JSONObject): String? {
            val text = json.optString("text").trim()
            return text.takeIf { it.isNotEmpty() && it.length <= MAX_TEXT_CHARS }
        }

        /** Boxes and labelled arrows, at most [MAX_NODES] boxes; every arrow must point at boxes that exist. */
        fun parseDiagram(json: JSONObject): DiagramSpec? {
            val nodesArr = json.optJSONArray("nodes") ?: return null
            val nodes = strings(nodesArr)
            if (nodes.size !in 2..MAX_NODES || nodes.size != nodesArr.length()) return null
            val edgesArr = json.optJSONArray("edges") ?: return null
            val edges = mutableListOf<DiagramEdge>()
            for (i in 0 until edgesArr.length()) {
                val e = edgesArr.optJSONObject(i) ?: return null
                if (!e.has("from") || !e.has("to")) return null
                val from = e.optInt("from", -1)
                val to = e.optInt("to", -1)
                if (from !in nodes.indices || to !in nodes.indices || from == to) return null
                edges += DiagramEdge(from, to, e.optString("label").trim().takeIf { it.isNotEmpty() })
            }
            if (edges.isEmpty()) return null
            return DiagramSpec(
                nodes = nodes,
                edges = edges,
                caption = json.optString("caption").trim().takeIf { it.isNotEmpty() }
            )
        }

        private fun strings(arr: JSONArray): List<String> =
            List(arr.length()) { i -> arr.optString(i).trim() }.filter { it.isNotEmpty() }

        private fun prompt(concept: LearningConcept, mode: ExplainMode, excerpts: List<String>): String = buildString {
            appendLine("A student asked for the idea \"${concept.name}\" to be explained another way.")
            appendLine("Definition from the lecture: ${concept.definition}")
            appendLine("Use only the lecture excerpts below. Do not add facts they do not contain.")
            appendLine()
            appendLine("LECTURE EXCERPTS:")
            excerpts.forEach { appendLine(it) }
            appendLine()
            when (mode) {
                ExplainMode.SIMPLER -> appendLine("Explain it more simply, in at most 80 words. Output only {\"text\": \"...\"}.")
                ExplainMode.ANALOGY -> appendLine("Explain it with one everyday analogy, in at most 80 words. Output only {\"text\": \"...\"}.")
                ExplainMode.EXAMPLE -> appendLine("Give one concrete worked example, in at most 80 words. Output only {\"text\": \"...\"}.")
                ExplainMode.DRAW -> {
                    appendLine("Draw it as at most $MAX_NODES boxes joined by labelled arrows.")
                    appendLine("Output only this shape, with edges referring to node indexes from 0:")
                    appendLine("{\"nodes\": [\"...\"], \"edges\": [{\"from\": 0, \"to\": 1, \"label\": \"...\"}], \"caption\": \"...\"}")
                }
            }
        }
    }
}
