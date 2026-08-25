package com.example.ai.pipeline

import com.example.core.common.FillerWordCleaner
import com.example.core.model.TranscriptSegment

/**
 * Produces a cleaned reading version of one transcript segment's text — filler/disfluency removal
 * today, with headroom for heavier cleanup (false starts, obvious spoken-number normalization,
 * sentence cohesion) in a future implementation. This is a seam, not a rewrite engine: the raw
 * [TranscriptSegment.text] a caller passes in is never mutated, and the result is only ever
 * proposed — [TranscriptQualityValidator] decides whether it's safe to keep.
 *
 * Never called for a segment with `isUserEdited == true` — callers own that check (see
 * [MeetingProcessingPipeline]) so this interface doesn't have to re-derive "should I run at all"
 * policy that belongs one layer up.
 */
interface TranscriptCleanupEngine {
    /** Returns a cleaned candidate for [segment.text], or null if no candidate could be produced. */
    fun clean(segment: TranscriptSegment): String?
}

/**
 * The only implementation today: wraps the existing, already-tested
 * [com.example.core.common.FillerWordCleaner] rule-based transform. Deliberately not a new model —
 * per the standing "do not add random new models" constraint, a local-SLM-backed
 * [TranscriptCleanupEngine] is a legitimate future extension of this same interface, not something
 * built here.
 *
 * The real architectural change over calling [FillerWordCleaner] directly at prompt-render time
 * (as [com.example.ai.llm.RealMeetingIntelligenceEngine] does today) is that cleanup becomes an
 * explicit, cached, validated pipeline stage: computed once per segment instead of recomputed
 * live on every LLM call, and never trusted without [TranscriptQualityValidator] checking it
 * first.
 */
class RuleBasedTranscriptCleanupEngine : TranscriptCleanupEngine {
    override fun clean(segment: TranscriptSegment): String? {
        if (segment.text.isBlank()) return null
        return FillerWordCleaner.clean(segment.text)
    }
}

/**
 * Runs [engine] over every segment that isn't user-edited, keeping a candidate only when
 * [TranscriptQualityValidator] accepts it. Extracted as a small, pure, directly-testable function
 * so [MeetingProcessingPipeline]'s own cleanup stage stays a one-line call rather than inline
 * mapping logic that could only be exercised through a full pipeline run.
 *
 * A user-edited segment is returned completely untouched — never even offered to [engine] — so a
 * hand-correction can never be second-guessed or overwritten by a cached cleanup of the text it
 * replaced.
 */
internal fun applyTranscriptCleanup(
    segments: List<TranscriptSegment>,
    engine: TranscriptCleanupEngine
): List<TranscriptSegment> = segments.map { seg ->
    if (seg.isUserEdited) return@map seg
    val candidate = engine.clean(seg)
    val verdict = TranscriptQualityValidator.validate(seg.text, candidate)
    if (verdict.accepted) seg.copy(cleanedText = candidate) else seg
}
