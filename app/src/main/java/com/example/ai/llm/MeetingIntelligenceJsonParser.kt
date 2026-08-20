package com.example.ai.llm

import com.example.core.model.ActionItem
import com.example.core.model.Decision
import com.example.core.model.DecisionType
import com.example.core.model.FollowUp
import com.example.core.model.Question
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChunkExtraction(
    val decisions: List<Decision> = emptyList(),
    val actionItems: List<ActionItem> = emptyList(),
    val questions: List<Question> = emptyList(),
    val followUps: List<FollowUp> = emptyList(),
    val briefSummary: String = ""
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

            ChunkExtraction(decisions, actionItems, questions, followUps, json.optString("briefSummary", "").trim())
        } catch (e: Exception) {
            ChunkExtraction.EMPTY
        }
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
