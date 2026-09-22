package com.example.ai.tools

import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.ai.llm.LanguageModel
import com.example.ai.pipeline.TranscriptQualityValidator
import com.example.core.model.TranscriptAiToolType
import com.example.core.model.TranscriptCleanupProfile
import com.example.core.model.TranscriptSegment
import org.json.JSONException
import org.json.JSONObject

/**
 * Runs the model-backed entries of the "✨ AI tools" menu.
 *
 * One engine for all of them rather than one class per tool: every model-backed tool is the same
 * operation — take a scoped transcript, send it with the shared fidelity contract and a
 * tool-specific instruction, get structured output back, and validate it before anyone sees it.
 * What differs between tools is a prompt and an output shape, both of which are data
 * ([TranscriptToolPrompts]). Eleven near-identical classes would have made the differences harder
 * to see, not easier.
 *
 * ### Validation is not optional
 *
 * Nothing the model returns is passed through untouched:
 *
 * - **Revisions** go through [TranscriptQualityValidator], the same check the cleanup pass uses.
 *   It rejects a candidate that drops a number, drops a name, or diverges too far in length or
 *   word overlap from the text it claims to be a revision of. A rejected revision is counted and
 *   reported, never silently dropped.
 * - **Findings** have every citation checked against the real segment ids in scope. A citation
 *   that does not resolve is removed, which leaves the finding visibly uncited rather than
 *   falsely cited.
 * - **Everything** must parse as the requested schema. Unparseable output is a failure, not a
 *   salvage operation.
 *
 * @param languageModel Either the on-device model or the cloud one — both implement
 *   [LanguageModel], so the profile decides which engine runs a tool and nothing here has to know.
 */
class TranscriptToolEngine(
    private val languageModel: LanguageModel,
    private val engineName: String,
    private val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS
) {

    suspend fun run(
        tool: TranscriptAiToolType,
        segments: List<TranscriptSegment>,
        cleanupProfile: TranscriptCleanupProfile? = null
    ): AiResult<ToolOutcome> {
        if (segments.isEmpty()) {
            return AiResult.Failed("There is nothing in the selected part of the transcript to work on.")
        }

        val prompt = TranscriptToolPrompts.build(tool, segments)
        val response = when (val generated = languageModel.generate(prompt, maxOutputTokens)) {
            is AiResult.Success -> generated.value
            else -> return AiResult.Failed(
                generated.describeFailure() ?: "\"${tool.label}\" could not run."
            )
        }

        val json = extractJsonObject(response)
            ?: return AiResult.Failed("\"${tool.label}\" returned a result that could not be read.")

        return when (TranscriptToolPrompts.outputShapeFor(tool)) {
            TranscriptToolPrompts.OutputShape.REVISION -> parseRevision(json, segments, cleanupProfile)
            TranscriptToolPrompts.OutputShape.FINDINGS -> parseFindings(json, segments)
            TranscriptToolPrompts.OutputShape.DOCUMENT -> parseDocument(json, tool)
            TranscriptToolPrompts.OutputShape.TITLE -> parseTitle(json, tool)
        }
    }

    // ── Parsing and validation ───────────────────────────────────────────────────────────────

    private fun parseRevision(
        json: JSONObject,
        segments: List<TranscriptSegment>,
        cleanupProfile: TranscriptCleanupProfile?
    ): AiResult<ToolOutcome> {
        val byId = segments.associateBy { it.id }
        val array = json.optJSONArray("revisions")
        val edits = mutableListOf<SegmentEdit>()
        var rejected = 0

        for (i in 0 until (array?.length() ?: 0)) {
            val item = array!!.optJSONObject(i) ?: continue
            // A revision for a segment that is not in scope is discarded outright: applying it
            // somewhere plausible would edit text the user did not ask to have edited.
            val segment = byId[item.optString("segmentId")] ?: continue
            val proposed = item.optString("text").trim()
            if (proposed.isEmpty()) continue

            val raw = segment.cleanedText ?: segment.text
            if (proposed == raw) continue

            val verdict = TranscriptQualityValidator.validate(raw, proposed, cleanupProfile)
            if (verdict.accepted) {
                edits += SegmentEdit(segmentId = segment.id, before = raw, after = proposed)
            } else {
                rejected++
            }
        }

        if (edits.isEmpty() && rejected == 0) {
            return AiResult.Success(ToolOutcome.TranscriptRevision(emptyList()))
        }
        return AiResult.Success(ToolOutcome.TranscriptRevision(edits, rejected))
    }

    private fun parseFindings(json: JSONObject, segments: List<TranscriptSegment>): AiResult<ToolOutcome> {
        val validIds = segments.associateBy { it.id }
        val array = json.optJSONArray("findings")
        val findings = mutableListOf<ToolFinding>()

        for (i in 0 until (array?.length() ?: 0)) {
            val item = array!!.optJSONObject(i) ?: continue
            val text = item.optString("text").trim()
            if (text.isEmpty()) continue

            val citedIds = item.optJSONArray("sourceSegmentIds")
            val resolved = buildList {
                for (j in 0 until (citedIds?.length() ?: 0)) {
                    val id = citedIds!!.optString(j)
                    if (id.isNotBlank() && validIds.containsKey(id)) add(id)
                }
            }
            findings += ToolFinding(
                text = text,
                sourceSegmentIds = resolved,
                detail = item.optString("detail").trim().takeIf { it.isNotEmpty() },
                startMs = resolved.mapNotNull { validIds[it]?.startMs }.minOrNull()
            )
        }
        return AiResult.Success(ToolOutcome.Findings(findings))
    }

    private fun parseDocument(json: JSONObject, tool: TranscriptAiToolType): AiResult<ToolOutcome> {
        val markdown = json.optString("markdown").trim()
        return if (markdown.isEmpty()) {
            AiResult.Failed("\"${tool.label}\" produced nothing.")
        } else {
            AiResult.Success(ToolOutcome.TextDocument(markdown))
        }
    }

    private fun parseTitle(json: JSONObject, tool: TranscriptAiToolType): AiResult<ToolOutcome> {
        // Reuses the same sanitiser the processing pipeline's own title generation uses, so a
        // title suggested here is held to exactly the standard an automatic one is.
        val title = com.example.core.common.MeetingTitleGenerator.sanitizeAndValidate(json.optString("title"))
            ?: return AiResult.Failed("\"${tool.label}\" did not produce a usable title.")
        return AiResult.Success(ToolOutcome.TitleSuggestion(title))
    }

    /**
     * Finds the JSON object in a response.
     *
     * A model asked for JSON sometimes wraps it in a code fence or a sentence. Locating the object
     * is tolerant; what it contains is not — a response with no parseable object at all is a
     * failure, never partially salvaged into a half-result that looks complete.
     */
    internal fun extractJsonObject(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(raw.substring(start, end + 1))
        } catch (e: JSONException) {
            null
        }
    }

    fun engineId(): String = engineName

    private companion object {
        /** Notes and outlines are the longest outputs any of these tools produce. */
        const val DEFAULT_MAX_OUTPUT_TOKENS = 2048
    }
}

/**
 * The tools that need no model at all.
 *
 * Kept separate and deterministic because they genuinely are: expanding context is arithmetic over
 * segment order, and reading back decisions the processing pipeline already extracted is a
 * database query. Routing either through a language model would add a failure mode, a latency cost
 * and a hallucination risk to an operation that has an exact answer.
 */
object DeterministicTranscriptTools {

    /** How many paragraphs on each side [TranscriptAiToolType.EXPAND_CONTEXT] adds per run. */
    const val CONTEXT_EXPANSION_RADIUS = 2

    fun expandContext(
        allSegments: List<TranscriptSegment>,
        selectedIds: Collection<String>,
        radius: Int = CONTEXT_EXPANSION_RADIUS
    ): ToolOutcome.ContextExpansion {
        val ordered = allSegments.sortedBy { it.startMs }
        val indexById = ordered.withIndex().associate { (index, segment) -> segment.id to index }
        val expanded = sortedSetOf<Int>()
        for (id in selectedIds) {
            val index = indexById[id] ?: continue
            for (offset in -radius..radius) {
                val neighbour = index + offset
                if (neighbour in ordered.indices) expanded += neighbour
            }
        }
        return ToolOutcome.ContextExpansion(expanded.map { ordered[it].id })
    }
}
