package com.example.ai.llm

import com.example.core.model.TranscriptSegment

data class TranscriptChunk(val segments: List<TranscriptSegment>, val chunkIndex: Int)

/**
 * Splits a transcript into chunks that fit a real, model-derived token budget — never an
 * arbitrary hardcoded size. [contextLengthTokens] must come from the actual installed LLM's
 * manifest entry ([com.example.ai.modelmanagement.ModelCatalog]); this function only turns that
 * real number into chunk boundaries.
 *
 * Splits only at transcript-segment boundaries (never mid-sentence, never mid-speaker-turn) —
 * each [TranscriptSegment] is one continuous ASR emission and is never divided. A single segment
 * exceeding the whole per-chunk budget is not specially handled because VAD caps every spoken
 * interval at 30s (see `SileroVadDetector.MAX_SPEECH_DURATION_SEC`), which is far shorter than
 * any reasonable chunk budget in characters.
 */
object TranscriptChunker {
    // Both the fixed instruction/schema text of an extraction prompt and the model's own
    // generated JSON output count against the same per-call token budget as the transcript text
    // itself — reserve room for both before deciding how much transcript text fits.
    private const val RESERVED_PROMPT_OVERHEAD_TOKENS = 700
    private const val RESERVED_OUTPUT_TOKENS = 700

    // Conservative (deliberately low) chars-per-token approximation for English text. This
    // engine has no tokenizer access outside a loaded model instance, so chunk sizing uses this
    // estimate rather than an exact count; erring toward smaller chunks only costs an extra
    // inference call, erring larger risks an over-budget prompt, so the bias is deliberate.
    private const val APPROX_CHARS_PER_TOKEN = 3.5

    fun chunk(segments: List<TranscriptSegment>, contextLengthTokens: Int): List<TranscriptChunk> {
        if (segments.isEmpty()) return emptyList()
        val budgetTokens = (contextLengthTokens - RESERVED_PROMPT_OVERHEAD_TOKENS - RESERVED_OUTPUT_TOKENS).coerceAtLeast(256)
        val budgetChars = (budgetTokens * APPROX_CHARS_PER_TOKEN).toInt()

        val chunks = mutableListOf<TranscriptChunk>()
        var current = mutableListOf<TranscriptSegment>()
        var currentChars = 0
        for (seg in segments) {
            val segChars = formattedLength(seg)
            if (current.isNotEmpty() && currentChars + segChars > budgetChars) {
                chunks += TranscriptChunk(current.toList(), chunks.size)
                current = mutableListOf()
                currentChars = 0
            }
            current.add(seg)
            currentChars += segChars
        }
        if (current.isNotEmpty()) chunks += TranscriptChunk(current.toList(), chunks.size)
        return chunks
    }

    // Rough allowance for how a segment is rendered into the prompt ("[id] Speaker: text\n").
    private fun formattedLength(seg: TranscriptSegment): Int =
        seg.id.length + (seg.speakerName?.length ?: 14) + seg.text.length + 8
}
