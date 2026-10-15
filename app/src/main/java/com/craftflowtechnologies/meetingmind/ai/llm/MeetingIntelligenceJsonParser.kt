package com.craftflowtechnologies.meetingmind.ai.llm

import com.craftflowtechnologies.meetingmind.core.model.ActionItem
import com.craftflowtechnologies.meetingmind.core.model.Decision
import com.craftflowtechnologies.meetingmind.core.model.DecisionType
import com.craftflowtechnologies.meetingmind.core.model.FollowUp
import com.craftflowtechnologies.meetingmind.core.model.Question
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChunkExtraction(
    val decisions: List<Decision> = emptyList(),
    val actionItems: List<ActionItem> = emptyList(),
    val questions: List<Question> = emptyList(),
    val followUps: List<FollowUp> = emptyList(),
    val briefSummary: String = "",
    /** Empty when the output is in the older format that has no signals. */
    val signals: List<com.craftflowtechnologies.meetingmind.core.model.Signal> = emptyList()
) {
    companion object {
        val EMPTY = ChunkExtraction()
    }
}

data class SynthesisResult(val title: String, val summary: String, val keyPoints: List<String>)

/**
 * Turns raw LLM text output into validated domain objects, or discards it. Never lets malformed
 * model output reach Room: any JSON parse failure, or an item missing its required text, is
 * dropped rather than guessed at. Every `sourceSegmentIds` reference is cross-checked against the
 * real segment ids the model was actually shown in that chunk's prompt — an id the model didn't
 * see (a hallucinated citation) is silently dropped, never trusted.
 */
object MeetingIntelligenceJsonParser {

    fun parseExtraction(
        raw: String,
        meetingId: String,
        validSegmentIds: Set<String>,
        speakerNameToId: Map<String, String> = emptyMap()
    ): ChunkExtraction {
        val json = extractJsonObject(raw) ?: return ChunkExtraction.EMPTY
        return try {
            val decisions = json.optJSONArray("decisions").mapItemsNotNull { obj ->
                val text = obj.optString("text").trim()
                if (text.isBlank()) return@mapItemsNotNull null
                val type = try {
                    DecisionType.valueOf(obj.optString("type", "DISCUSSION").trim().uppercase())
                } catch (e: Exception) {
                    DecisionType.DISCUSSION
                }
                Decision(
                    id = UUID.randomUUID().toString(),
                    meetingId = meetingId,
                    text = text,
                    type = type,
                    confidence = null,
                    sourceSegmentIds = obj.optJSONArray("sourceSegmentIds").toValidIds(validSegmentIds)
                )
            }

            val actionItems = json.optJSONArray("actionItems").mapItemsNotNull { obj ->
                val task = obj.optString("task").trim()
                if (task.isBlank()) return@mapItemsNotNull null
                val assigneeName = obj.optString("assigneeName", "").trim().ifBlank { null }
                ActionItem(
                    id = UUID.randomUUID().toString(),
                    meetingId = meetingId,
                    task = task,
                    assigneeSpeakerId = assigneeName?.let { speakerNameToId[it.lowercase()] },
                    assigneeName = assigneeName,
                    deadline = obj.optString("deadline", "").trim().ifBlank { null },
                    confidence = null,
                    isCompleted = false,
                    sourceSegmentIds = obj.optJSONArray("sourceSegmentIds").toValidIds(validSegmentIds)
                )
            }

            val questions = json.optJSONArray("questions").mapItemsNotNull { obj ->
                val questionText = obj.optString("question").trim()
                if (questionText.isBlank()) return@mapItemsNotNull null
                val askedBy = obj.optString("askedBy", "").trim().ifBlank { null }
                Question(
                    id = UUID.randomUUID().toString(),
                    meetingId = meetingId,
                    text = questionText,
                    askedBySpeakerId = askedBy?.let { speakerNameToId[it.lowercase()] },
                    resolved = false,
                    answer = null,
                    sourceSegmentIds = obj.optJSONArray("sourceSegmentIds").toValidIds(validSegmentIds)
                )
            }

            val followUps = json.optJSONArray("followUps").mapItemsNotNull { obj ->
                val description = obj.optString("description").trim()
                if (description.isBlank()) return@mapItemsNotNull null
                val owner = obj.optString("owner", "").trim().ifBlank { null }
                FollowUp(
                    id = UUID.randomUUID().toString(),
                    meetingId = meetingId,
                    description = description,
                    ownerSpeakerId = owner?.let { speakerNameToId[it.lowercase()] },
                    deadline = obj.optString("deadline", "").trim().ifBlank { null },
                    sourceSegmentIds = obj.optJSONArray("sourceSegmentIds").toValidIds(validSegmentIds)
                )
            }

            val signals = parseSignals(json.optJSONArray("signals"), meetingId, validSegmentIds, speakerNameToId)
            ChunkExtraction(decisions, actionItems, questions, followUps, json.optString("briefSummary", "").trim(), signals)
        } catch (e: Exception) {
            ChunkExtraction.EMPTY
        }
    }

    /**
     * Signals need a known kind, some text, and at least one real paragraph id: one that cites
     * nothing the model was shown is dropped, never trusted. Commitments carry who, to whom and when
     * as a small JSON object in `value`.
     */
    internal fun parseSignals(arr: JSONArray?, meetingId: String, validSegmentIds: Set<String>, speakerNameToId: Map<String, String>): List<com.craftflowtechnologies.meetingmind.core.model.Signal> =
        arr.mapItemsNotNull { obj ->
            val kind = com.craftflowtechnologies.meetingmind.core.work.ItemKind.entries.firstOrNull { it.name == obj.optString("kind").trim().uppercase() } ?: return@mapItemsNotNull null
            val text = obj.optString("text").trim()
            val ids = obj.optJSONArray("sourceSegmentIds").toValidIds(validSegmentIds)
            if (text.isBlank() || ids.isEmpty()) return@mapItemsNotNull null
            val speaker = obj.optString("speaker", "").trim().takeIf { it.isNotBlank() && !obj.isNull("speaker") }
            val counterparty = obj.optString("counterparty", "").trim().takeIf { it.isNotBlank() && !obj.isNull("counterparty") }
            val due = obj.optString("due", "").trim().takeIf { it.isNotBlank() && !obj.isNull("due") }
            val plain = obj.optString("value", "").trim().takeIf { it.isNotBlank() && !obj.isNull("value") }
            val details = if (kind == com.craftflowtechnologies.meetingmind.core.work.ItemKind.COMMITMENT && (counterparty != null || due != null)) {
                JSONObject().apply { counterparty?.let { put("counterparty", it) }; due?.let { put("due", it) } }.toString()
            } else null
            com.craftflowtechnologies.meetingmind.core.model.Signal(
                id = UUID.randomUUID().toString(), meetingId = meetingId, kind = kind.name, text = text, sourceSegmentIds = ids,
                value = plain ?: details, speakerId = speaker?.let { speakerNameToId[it.lowercase()] },
                confidence = if (obj.has("confidence") && !obj.isNull("confidence")) obj.optDouble("confidence", 0.6).toFloat().coerceIn(0f, 1f) else com.craftflowtechnologies.meetingmind.core.model.Signal.DEFAULT_CONFIDENCE
            )
        }

    fun parseSynthesis(raw: String, fallbackTitle: String): SynthesisResult {
        val json = extractJsonObject(raw) ?: return SynthesisResult(fallbackTitle, "", emptyList())
        val title = json.optString("title", "").trim().ifBlank { fallbackTitle }
        val summary = json.optString("summary", "").trim()
        val keyPoints = json.optJSONArray("keyPoints").let { arr ->
            if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { arr.optString(it, "").trim().ifBlank { null } }
        }
        return SynthesisResult(title, summary, keyPoints)
    }

    /**
     * Last-resort recovery of a chunk summary from model output whose JSON could not be parsed.
     *
     * Small on-device models frequently answer a nested-schema prompt with correct, well-grounded
     * prose wrapped in broken JSON (or no JSON at all). Discarding that entirely was making whole
     * recordings look unanalyzable when the model had in fact described them fine. This recovers
     * only free prose: code fences, any brace/bracket structure, and JSON key syntax are stripped,
     * and what remains is accepted only if it still looks like real sentences.
     *
     * Returns null when nothing usable survives — never a placeholder, and never a claim that the
     * recording contained nothing.
     */
    fun salvagePlainSummary(raw: String): String? {
        val withoutFences = raw.replace("```json", "", ignoreCase = true).replace("```", "")
        // Drop anything that is recognizably JSON structure rather than prose.
        val prose = withoutFences
            .replace(Regex("""[\[\]{}]"""), " ")
            .replace(Regex(""""\s*\w+\s*"\s*:"""), " ")
            .replace(Regex("""["']"""), "")
            // Stripping the structure out of e.g. {"summary":"...","decisions":[],"questions":[]}
            // leaves the separators behind as a trail of ", , ,". Collapse any run of separators
            // into a single one, then drop the ones now dangling at either end.
            .replace(Regex("""(\s*[,;:]\s*){2,}"""), ", ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim(',', ':', ';', '-', ' ')
            .trim()

        if (prose.length < MIN_SALVAGED_SUMMARY_CHARS) return null
        // Require actual words, not a residue of field names and punctuation.
        if (prose.count { it == ' ' } < MIN_SALVAGED_SUMMARY_WORDS - 1) return null
        return prose.take(MAX_SALVAGED_SUMMARY_CHARS).trim()
    }

    private const val MIN_SALVAGED_SUMMARY_CHARS = 40
    private const val MIN_SALVAGED_SUMMARY_WORDS = 8
    private const val MAX_SALVAGED_SUMMARY_CHARS = 600

    /** Strips markdown code fences and any leading/trailing chatter outside the outermost braces — the standard, minimal "repair" for LLM JSON output; a real parse failure after this is treated as no evidence, never guessed at. */
    private fun extractJsonObject(raw: String): JSONObject? {
        val cleaned = raw.replace("```json", "", ignoreCase = true).replace("```", "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return null
        return try {
            JSONObject(cleaned.substring(start, end + 1))
        } catch (e: Exception) {
            null
        }
    }

    private inline fun <T> JSONArray?.mapItemsNotNull(transform: (JSONObject) -> T?): List<T> {
        if (this == null) return emptyList()
        val result = mutableListOf<T>()
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            transform(obj)?.let { result.add(it) }
        }
        return result
    }

    private fun JSONArray?.toValidIds(validSegmentIds: Set<String>): List<String> {
        if (this == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until length()) {
            val id = optString(i, "")
            if (id.isNotBlank() && id in validSegmentIds) result.add(id)
        }
        return result
    }
}
