package com.example.ai.pipeline

import com.example.ai.common.AiResult
import com.example.ai.llm.LanguageModel
import com.example.ai.llm.TranscriptChunker
import com.example.core.model.TranscriptCleanupProfile
import com.example.core.model.TranscriptSegment
import org.json.JSONArray

/**
 * A dedicated transcript-cleaning capability — deliberately separate from
 * [com.example.ai.llm.MeetingIntelligenceEngine] (summarization/extraction/synthesis/Ask Meeting).
 * Its only job is reconstructing already-structured, already-rule-based-cleaned paragraphs
 * ([TranscriptStructureEngine] + [TranscriptCleanupEngine]) into a more faithful, readable
 * transcript using a real on-device LLM — never a summarizer, never asked to add or infer
 * anything the transcript doesn't already say. How interventionist this is allowed to be is
 * entirely governed by [TranscriptCleanupProfile] (recording type × cleanup mode) — there is one
 * engine, not a different implementation per mode or per recording type. Every candidate is
 * validated by [TranscriptQualityValidator] against [profile] before it can ever replace the
 * cleanup that already ran; a rejected or unavailable candidate simply falls back (see
 * [TranscriptAiCleanupResult]) rather than ever fabricating a replacement.
 */
interface TranscriptAiCleanupEngine {
    /**
     * @param segments Already rule-based-cleaned paragraphs (post [TranscriptStructureEngine] +
     *   [applyTranscriptCleanup]) — a segment's [TranscriptSegment.cleanedText], if present, is
     *   used as this pass's *input* (building on top of the deterministic pass rather than
     *   redoing it), while [TranscriptSegment.text] (the real raw ASR text) is always what every
     *   candidate is validated against.
     * @param profile Recording type × cleanup mode, the single source of truth for prompt
     *   permissiveness and validator thresholds — see [com.example.core.model.RecordingType.transcriptCleanupProfile].
     * @param singleSpeakerMode True when diarization was skipped for a confirmed single speaker.
     * @return [AiResult.ModelUnavailable] only when no cleanup-capable model could be reached at
     *   all (the very first attempt fails that way); a later per-chunk failure or a rejected
     *   candidate degrades gracefully within an [AiResult.Success] instead — a single bad model
     *   output must never destroy an entire transcript.
     */
    suspend fun clean(
        segments: List<TranscriptSegment>,
        profile: TranscriptCleanupProfile,
        singleSpeakerMode: Boolean
    ): AiResult<TranscriptAiCleanupResult>
}

/**
 * @param segments Every input segment, unchanged except that a validated AI candidate replaces
 *   [TranscriptSegment.cleanedText] where one was accepted — a segment whose candidate was
 *   rejected, unavailable, or never attempted (user-edited) keeps whatever it already had.
 * @param chunksAttempted How many model calls were actually made.
 * @param paragraphsAccepted How many paragraphs received a validated AI candidate.
 * @param paragraphsFallback How many eligible paragraphs did NOT get an AI candidate (parse
 *   failure, validation rejection, or a single paragraph too large for the model's real budget)
 *   and kept their prior (deterministic-cleaned or raw) text instead.
 */
data class TranscriptAiCleanupResult(
    val segments: List<TranscriptSegment>,
    val chunksAttempted: Int,
    val paragraphsAccepted: Int,
    val paragraphsFallback: Int
)

/**
 * Real implementation: reuses [TranscriptChunker] (parameterized with a smaller, cleanup-specific
 * overhead reservation — the cleanup prompt is far terser than the extraction/synthesis schema
 * prompts that chunker's defaults were tuned for) to pack already-structured paragraphs into
 * model calls sized to [contextLengthTokens], which must come from the real selected model's
 * catalog entry — never an arbitrary constant.
 *
 * Each call also carries a bounded, read-only context window — the paragraph immediately before
 * and immediately after the chunk being rewritten — so the model can resolve a self-correction,
 * reference, or terminology repetition that spans a chunk boundary without ever being asked (or
 * allowed) to rewrite that neighboring paragraph itself. This is a deliberate compromise between
 * "one paragraph per call with full neighbor context" (maximally precise, far more model calls)
 * and "the whole transcript in one call" (fast, but a tiny on-device model has no business
 * reading hours of audio at once) — chunk-level, not per-paragraph, context.
 */
class RealTranscriptAiCleanupEngine(
    private val languageModel: LanguageModel,
    private val contextLengthTokens: Int
) : TranscriptAiCleanupEngine {

    override suspend fun clean(
        segments: List<TranscriptSegment>,
        profile: TranscriptCleanupProfile,
        singleSpeakerMode: Boolean
    ): AiResult<TranscriptAiCleanupResult> {
        // A hand-correction is never sent through automatic cleanup — the user's own edit always
        // wins and is never second-guessed by a model, matching TranscriptCleanupEngine's rule.
        val eligible = segments.filter { !it.isUserEdited }
        if (eligible.isEmpty()) {
            return AiResult.Success(TranscriptAiCleanupResult(segments, 0, 0, 0))
        }
        val sortedAll = segments.sortedBy { it.startMs }

        val chunks = TranscriptChunker.chunk(
            eligible,
            contextLengthTokens,
            reservedPromptOverheadTokens = CLEANUP_PROMPT_OVERHEAD_TOKENS,
            reservedOutputTokens = CLEANUP_OUTPUT_TOKENS
        )
        val budgetChars = ((contextLengthTokens - CLEANUP_PROMPT_OVERHEAD_TOKENS - CLEANUP_OUTPUT_TOKENS)
            .coerceAtLeast(256) * TranscriptChunker.APPROX_CHARS_PER_TOKEN).toInt()

        val cleanedById = mutableMapOf<String, String>()
        var chunksAttempted = 0
        var accepted = 0
        var fallback = 0

        for (chunk in chunks) {
            // A single paragraph that alone exceeds this model's real per-call budget is never
            // truncated or partially guessed at — it falls back whole, honestly, rather than
            // risking a cut-off, unparseable, or silently-incomplete model response.
            val chunkChars = chunk.segments.sumOf { (it.cleanedText ?: it.text).length }
            if (chunk.segments.size == 1 && chunkChars > budgetChars) {
                fallback++
                continue
            }

            val contextBefore = contextParagraphBefore(sortedAll, chunk.segments.first())
            val contextAfter = contextParagraphAfter(sortedAll, chunk.segments.last())

            chunksAttempted++
            val prompt = buildCleanupPrompt(chunk.segments, contextBefore, contextAfter, profile, singleSpeakerMode)
            val result = languageModel.generate(prompt, maxOutputTokens = CLEANUP_OUTPUT_TOKENS)

            // Nothing has succeeded yet and the very first real attempt reports the model isn't
            // installed — every later chunk would fail identically, so this is a whole-call
            // failure, not a per-chunk one worth continuing past.
            if (result is AiResult.ModelUnavailable && chunksAttempted == 1 && cleanedById.isEmpty()) {
                return result
            }

            val parsed = (result as? AiResult.Success)?.value?.let { parseCleanupResponse(it) }
            if (parsed == null) {
                fallback += chunk.segments.size
                continue
            }
            for (seg in chunk.segments) {
                val candidate = parsed[seg.id]
                val verdict = TranscriptQualityValidator.validate(seg.text, candidate, profile)
                if (verdict.accepted) {
                    cleanedById[seg.id] = candidate!!
                    accepted++
                } else {
                    fallback++
                }
            }
        }

        val resultSegments = segments.map { seg -> cleanedById[seg.id]?.let { seg.copy(cleanedText = it) } ?: seg }
        return AiResult.Success(TranscriptAiCleanupResult(resultSegments, chunksAttempted, accepted, fallback))
    }

    private fun contextParagraphBefore(sortedAll: List<TranscriptSegment>, first: TranscriptSegment): TranscriptSegment? {
        val idx = sortedAll.indexOfFirst { it.id == first.id }
        return if (idx > 0) sortedAll[idx - 1] else null
    }

    private fun contextParagraphAfter(sortedAll: List<TranscriptSegment>, last: TranscriptSegment): TranscriptSegment? {
        val idx = sortedAll.indexOfFirst { it.id == last.id }
        return if (idx in 0 until sortedAll.lastIndex) sortedAll[idx + 1] else null
    }

    private fun buildCleanupPrompt(
        primarySegments: List<TranscriptSegment>,
        contextBefore: TranscriptSegment?,
        contextAfter: TranscriptSegment?,
        profile: TranscriptCleanupProfile,
        singleSpeakerMode: Boolean
    ): String {
        val guidance = profile.typeGuidance.let { if (it.isNotBlank()) "\n$it" else "" }
        val speakerNote = if (singleSpeakerMode) {
            "\nThis is a single continuous speaker. Reconstruct natural paragraphs — a pause is not, by itself, evidence of a new thought."
        } else ""
        val contextBeforeBlock = contextBefore?.let {
            "\nCONTEXT BEFORE (for reference only — do NOT rewrite this, do NOT include it in your response):\n${renderParagraph(it)}\n"
        } ?: ""
        val contextAfterBlock = contextAfter?.let {
            "\nCONTEXT AFTER (for reference only — do NOT rewrite this, do NOT include it in your response):\n${renderParagraph(it)}\n"
        } ?: ""
        val primaryBlock = primarySegments.joinToString("\n") { renderParagraph(it) }

        return """
            You are reconstructing real speech-to-text output into a faithful, professional transcript. You are NOT summarizing, NOT improving the speaker's argument, and NOT inferring missing information — you are turning disfluent spoken text into readable written text of the exact same content.

            You MUST preserve: meaning, chronology, names, numbers, dates, monetary amounts, commitments, uncertainty, contradictions, speaker identity, and every substantive detail.
            ${profile.permissivenessGuidance}
            You MUST NOT: invent words, facts, names, numbers, dates, or amounts; summarize; add explanations; infer missing speech; remove meaningful uncertainty or contradictions; change speaker attribution; or reorder events.
            $guidance$speakerNote
            $contextBeforeBlock
            PRIMARY TEXT — rewrite ONLY this. Respond with exactly one entry per [id] below, using the exact id given:
            $primaryBlock
            $contextAfterBlock
            Respond with ONLY a JSON array, no markdown, no commentary: [{"id":string,"text":string}]
        """.trimIndent()
    }

    private fun renderParagraph(seg: TranscriptSegment): String {
        val speaker = seg.speakerName ?: "Unknown speaker"
        return "[${seg.id}] $speaker: ${seg.cleanedText ?: seg.text}"
    }

    /** Salvage-tolerant: takes the first `[` through the last `]` so a model that adds stray
     * prose around the array (a common small-model failure mode) still parses. Returns null only
     * when no array-shaped content is present at all or the content inside isn't valid JSON —
     * both trigger a whole-chunk fallback, never a guessed partial result. */
    private fun parseCleanupResponse(rawText: String): Map<String, String>? {
        val start = rawText.indexOf('[')
        val end = rawText.lastIndexOf(']')
        if (start == -1 || end == -1 || end < start) return null
        return try {
            val array = JSONArray(rawText.substring(start, end + 1))
            val map = mutableMapOf<String, String>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                val text = obj.optString("text").takeIf { it.isNotBlank() } ?: continue
                map[id] = text
            }
            map
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        // The cleanup prompt's fixed instruction text is far shorter than an extraction/synthesis
        // schema prompt — reserving less here (vs. TranscriptChunker's own defaults) leaves more
        // real budget for actual transcript text per call, especially on a small cleanup-tier model.
        const val CLEANUP_PROMPT_OVERHEAD_TOKENS = 350
        // Generous relative to the input budget: the response must return the full cleaned text of
        // every paragraph in the chunk PLUS JSON structure overhead, not just a short verdict.
        const val CLEANUP_OUTPUT_TOKENS = 600
    }
}
