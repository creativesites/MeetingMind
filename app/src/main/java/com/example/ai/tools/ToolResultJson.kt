package com.example.ai.tools

import com.example.core.model.TranscriptAiToolType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Serializes a [ToolRunResult] into `AiJobEntity.resultPayloadJson` and back.
 *
 * A tool run is a background job that must survive process death — the user starts "Create notes"
 * on a long recording, leaves the app, and comes back to the result. That means the outcome is
 * persisted, and persisted means serialized. The discriminator is the outcome's shape rather than
 * the tool, because several tools share a shape and a reader should not need the tool registry to
 * know how to render a payload it has been handed.
 */
object ToolResultJson {

    private const val KEY_SHAPE = "shape"

    fun encode(result: ToolRunResult): String {
        val json = JSONObject()
            .put("tool", result.tool.name)
            .put("scope", result.scopeDescription)
            .put("engine", result.engine)

        when (val outcome = result.outcome) {
            is ToolOutcome.TranscriptRevision -> {
                json.put(KEY_SHAPE, "revision")
                json.put("rejectedCount", outcome.rejectedCount)
                json.put(
                    "edits",
                    JSONArray().apply {
                        outcome.edits.forEach { edit ->
                            put(
                                JSONObject()
                                    .put("segmentId", edit.segmentId)
                                    .put("before", edit.before)
                                    .put("after", edit.after)
                            )
                        }
                    }
                )
            }

            is ToolOutcome.Findings -> {
                json.put(KEY_SHAPE, "findings")
                json.put(
                    "findings",
                    JSONArray().apply {
                        outcome.items.forEach { finding ->
                            put(
                                JSONObject()
                                    .put("text", finding.text)
                                    .put("sourceSegmentIds", JSONArray(finding.sourceSegmentIds))
                                    .apply {
                                        finding.detail?.let { put("detail", it) }
                                        finding.startMs?.let { put("startMs", it) }
                                    }
                            )
                        }
                    }
                )
            }

            is ToolOutcome.TextDocument -> {
                json.put(KEY_SHAPE, "document")
                json.put("markdown", outcome.markdown)
            }

            is ToolOutcome.TitleSuggestion -> {
                json.put(KEY_SHAPE, "title")
                json.put("title", outcome.title)
            }

            is ToolOutcome.ContextExpansion -> {
                json.put(KEY_SHAPE, "context")
                json.put("segmentIds", JSONArray(outcome.segmentIds))
            }
        }
        return json.toString()
    }

    /** Returns null for a payload that cannot be read, rather than a partially-populated result. */
    fun decode(payload: String?): ToolRunResult? {
        if (payload.isNullOrBlank()) return null
        return try {
            val json = JSONObject(payload)
            val tool = TranscriptAiToolType.entries.firstOrNull { it.name == json.optString("tool") } ?: return null
            val outcome = when (json.optString(KEY_SHAPE)) {
                "revision" -> ToolOutcome.TranscriptRevision(
                    edits = json.optJSONArray("edits").mapObjects { item ->
                        SegmentEdit(
                            segmentId = item.optString("segmentId"),
                            before = item.optString("before"),
                            after = item.optString("after")
                        )
                    },
                    rejectedCount = json.optInt("rejectedCount")
                )

                "findings" -> ToolOutcome.Findings(
                    json.optJSONArray("findings").mapObjects { item ->
                        ToolFinding(
                            text = item.optString("text"),
                            sourceSegmentIds = item.optJSONArray("sourceSegmentIds").toStringList(),
                            detail = item.optString("detail").takeIf { it.isNotBlank() },
                            startMs = if (item.has("startMs")) item.optLong("startMs") else null
                        )
                    }
                )

                "document" -> ToolOutcome.TextDocument(json.optString("markdown"))
                "title" -> ToolOutcome.TitleSuggestion(json.optString("title"))
                "context" -> ToolOutcome.ContextExpansion(json.optJSONArray("segmentIds").toStringList())
                else -> return null
            }
            ToolRunResult(
                tool = tool,
                scopeDescription = json.optString("scope"),
                outcome = outcome,
                engine = json.optString("engine")
            )
        } catch (e: JSONException) {
            null
        }
    }

    private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }.map(transform)
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
    }
}
