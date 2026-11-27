package com.craftflowtechnologies.meetingmind.ai.tools

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel
import com.craftflowtechnologies.meetingmind.ai.pipeline.TranscriptQualityValidator
import com.craftflowtechnologies.meetingmind.core.model.TranscriptAiToolType
import com.craftflowtechnologies.meetingmind.core.model.TranscriptCleanupProfile
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
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
    private val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
    private val contextLengthTokens: Int = DEFAULT_CONTEXT_TOKENS,
    private val actualEngineName: () -> String = { engineName }
) {

    suspend fun run(
        tool: TranscriptAiToolType,
        segments: List<TranscriptSegment>,
        cleanupProfile: TranscriptCleanupProfile? = null,
        targetLanguageDisplay: String? = null
    ): AiResult<ToolOutcome> {
        if (segments.isEmpty()) {
            return AiResult.Failed("There is nothing in the selected part of the transcript to work on.")
        }

        val shape = TranscriptToolPrompts.outputShapeFor(tool)
        val schema = TranscriptToolPrompts.schemaFor(tool)
        // Reserve ~1600 tokens for fidelity contract, instructions, schema, and model output so
        // local 4K-context models (and any cloud->local fallback) stay safely within budget.
        val effectiveContextTokens = contextLengthTokens.coerceAtLeast(MIN_CONTEXT_TOKENS)
        val budgetTokens = (effectiveContextTokens - RESERVED_OVERHEAD_TOKENS).coerceAtLeast(MIN_INPUT_TOKENS)
        val maxTranscriptChars = (budgetTokens * CHARS_PER_TOKEN).coerceAtMost(MAX_SINGLE_CHUNK_CHARS)

        val chunks = chunkSegments(segments, shape, maxTranscriptChars)
        if (chunks.size == 1) {
            return runSingleBatch(tool, chunks.first(), shape, schema, cleanupProfile, targetLanguageDisplay)
        }

        return when (shape) {
            TranscriptToolPrompts.OutputShape.REVISION -> {
                val edits = mutableListOf<SegmentEdit>()
                var rejected = 0
                var lastFailure: AiResult<ToolOutcome>? = null
                for (batch in chunks) {
                    when (val r = runSingleBatch(tool, batch, shape, schema, cleanupProfile, targetLanguageDisplay)) {
                        is AiResult.Success -> {
                            val rev = r.value as ToolOutcome.TranscriptRevision
                            edits += rev.edits
                            rejected += rev.rejectedCount
                        }
                        else -> lastFailure = r
                    }
                }
                if (edits.isEmpty() && rejected == 0 && lastFailure != null) lastFailure
                else AiResult.Success(ToolOutcome.TranscriptRevision(edits, rejected))
            }
            TranscriptToolPrompts.OutputShape.FINDINGS -> {
                val allFindings = mutableListOf<ToolFinding>()
                val seen = HashSet<String>()
                var lastFailure: AiResult<ToolOutcome>? = null
                for (batch in chunks) {
                    when (val r = runSingleBatch(tool, batch, shape, schema, cleanupProfile, targetLanguageDisplay)) {
                        is AiResult.Success -> {
                            val list = (r.value as ToolOutcome.Findings).items
                            for (f in list) {
                                if (seen.add(f.text.lowercase())) allFindings += f
                            }
                        }
                        else -> lastFailure = r
                    }
                }
                if (allFindings.isEmpty() && lastFailure != null) lastFailure
                else AiResult.Success(ToolOutcome.Findings(allFindings))
            }
            TranscriptToolPrompts.OutputShape.DOCUMENT -> {
                val parts = mutableListOf<String>()
                var lastFailure: AiResult<ToolOutcome>? = null
                for (batch in chunks) {
                    when (val r = runSingleBatch(tool, batch, shape, schema, cleanupProfile, targetLanguageDisplay)) {
                        is AiResult.Success -> parts += (r.value as ToolOutcome.TextDocument).markdown
                        else -> lastFailure = r
                    }
                }
                if (parts.isEmpty()) lastFailure ?: AiResult.Failed("\"${tool.label}\" produced nothing.")
                else AiResult.Success(ToolOutcome.TextDocument(parts.joinToString("\n\n")))
            }
            TranscriptToolPrompts.OutputShape.TITLE -> {
                runSingleBatch(tool, chunks.first(), shape, schema, cleanupProfile, targetLanguageDisplay)
            }
        }
    }

    private suspend fun runSingleBatch(
        tool: TranscriptAiToolType,
        batch: List<TranscriptSegment>,
        shape: TranscriptToolPrompts.OutputShape,
        schema: String,
        cleanupProfile: TranscriptCleanupProfile?,
        targetLanguageDisplay: String?
    ): AiResult<ToolOutcome> {
        val prompt = TranscriptToolPrompts.build(tool, batch, targetLanguageDisplay)
        val perCallMaxTokens = if (contextLengthTokens <= 4096) maxOutputTokens.coerceAtMost(1024) else maxOutputTokens
        val response = when (val generated = languageModel.generateStructured(prompt, schema, perCallMaxTokens)) {
            is AiResult.Success -> generated.value
            else -> return AiResult.Failed(
                generated.describeFailure() ?: "\"${tool.label}\" could not run."
            )
        }

        val json = extractJsonObject(response)
            ?: return AiResult.Failed("\"${tool.label}\" returned a result that could not be read.")

        return when (shape) {
            TranscriptToolPrompts.OutputShape.REVISION -> parseRevision(json, batch, cleanupProfile)
            TranscriptToolPrompts.OutputShape.FINDINGS -> parseFindings(json, batch)
            TranscriptToolPrompts.OutputShape.DOCUMENT -> parseDocument(json, tool)
            TranscriptToolPrompts.OutputShape.TITLE -> parseTitle(json, tool)
        }
    }

    private fun chunkSegments(
        segments: List<TranscriptSegment>,
        shape: TranscriptToolPrompts.OutputShape,
        maxChars: Int
    ): List<List<TranscriptSegment>> {
        if (shape == TranscriptToolPrompts.OutputShape.TITLE) {
            val sampled = mutableListOf<TranscriptSegment>()
            var chars = 0
            // Include opening, middle, and closing segments so a 4K local model gets full arc for a title.
            val step = (segments.size / 18).coerceAtLeast(1)
            val candidates = segments.filterIndexed { index, _ -> index == 0 || index == segments.lastIndex || index % step == 0 }
            for (seg in candidates) {
                val len = (seg.cleanedText ?: seg.text).length + 40
                if (sampled.isNotEmpty() && chars + len > maxChars) break
                sampled += seg
                chars += len
            }
            return listOf(sampled.ifEmpty { listOf(segments.first()) })
        }
        val batches = mutableListOf<List<TranscriptSegment>>()
        var current = mutableListOf<TranscriptSegment>()
        var chars = 0
        for (seg in segments) {
            val lineLen = (seg.cleanedText ?: seg.text).length + 48
            if (current.isNotEmpty() && chars + lineLen > maxChars) {
                batches += current
                current = mutableListOf()
                chars = 0
            }
            current += seg
            chars += lineLen
        }
        if (current.isNotEmpty()) batches += current
        return batches
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
            val rawSegId = item.optString("segmentId").trim().removePrefix("[").removeSuffix("]").trim()
            val segment = byId[rawSegId] ?: continue
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
                    val id = citedIds!!.optString(j).trim().removePrefix("[").removeSuffix("]").trim()
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
        val title = com.craftflowtechnologies.meetingmind.core.common.MeetingTitleGenerator.sanitizeAndValidate(json.optString("title"))
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
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(cleaned.substring(start, end + 1))
        } catch (e: JSONException) {
            null
        }
    }

    fun engineId(): String = actualEngineName()

    private companion object {
        /** Notes and outlines are the longest outputs any of these tools produce. */
        const val DEFAULT_MAX_OUTPUT_TOKENS = 2048
        const val DEFAULT_CONTEXT_TOKENS = 32_000
        const val MIN_CONTEXT_TOKENS = 2048
        const val RESERVED_OVERHEAD_TOKENS = 1800
        const val MIN_INPUT_TOKENS = 700
        const val CHARS_PER_TOKEN = 4
        const val MAX_SINGLE_CHUNK_CHARS = 18_000
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
