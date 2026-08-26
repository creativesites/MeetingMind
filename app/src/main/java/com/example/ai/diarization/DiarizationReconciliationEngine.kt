package com.example.ai.diarization

import com.example.ai.common.AiResult
import com.example.ai.llm.LanguageModel
import com.example.core.model.DiarizationStrategy
import com.example.core.model.TranscriptSegment
import org.json.JSONArray

/**
 * Stage G: an optional, second-opinion layer on top of [SherpaSpeakerDiarizer]'s own deterministic
 * reconciliation ([analyzeSpeakerFragmentation]/[reconcileFragmentedSpeakers]) — never a
 * replacement for it. The deterministic pass already resolves the clear cases (several
 * near-silent speaker indices sitting beside one or two dominant ones); what it deliberately
 * leaves alone is a *single* minor speaker, because "a single minor speaker" is exactly what a
 * real, brief participant looks like from duration/turn-count numbers alone — there is no second
 * numeric signal left to distinguish "real small participant" from "one lingering diarization
 * fragment." This engine adds a semantic signal a local LLM can read but pure arithmetic cannot:
 * does the minor speaker's text actually read as a continuation of, or self-correction split out
 * of, a dominant speaker's turn?
 *
 * Deliberately narrow blast radius (see [computeSpeakerTranscriptFootprints]/[ambiguousSpeakerIds]): the
 * model is only ever asked about speakers already below [AMBIGUOUS_SHARE_THRESHOLD], and can only
 * ever propose merging one of THOSE into a speaker at or above that share — it can never be asked
 * to reconsider, let alone merge, two speakers that already look like real, substantial
 * participants. A recording with no minor speaker at all (e.g. a plausible 52%/48% two-person
 * split) never reaches the model — nothing ambiguous exists to ask about.
 */
interface DiarizationReconciliationEngine {
    suspend fun reconcile(segments: List<TranscriptSegment>): AiResult<DiarizationReconciliationResult>
}

/**
 * @param segments Every input segment, unchanged except that a merged minor speaker's
 *   [TranscriptSegment.speakerId]/[TranscriptSegment.speakerName] are reassigned to the confident
 *   speaker it was merged into — never a new, fabricated speaker identity.
 * @param mergedSpeakerIds Which originally-ambiguous speaker ids were reassigned, for logging/tests.
 * @param reasons One short human-readable reason per accepted merge, for logging/audit — never
 *   shown to the user, but real enough to explain a reconciliation decision after the fact.
 */
data class DiarizationReconciliationResult(
    val segments: List<TranscriptSegment>,
    val mergedSpeakerIds: Set<String>,
    val reasons: List<String>
)

/** One speaker id's aggregate footprint across a diarized transcript — the raw material both the
 * ambiguity check and the AI prompt reason about. */
data class SpeakerTranscriptFootprint(
    val speakerId: String,
    val speakerName: String?,
    val totalDurationMs: Long,
    val turnCount: Int,
    val shareOfTotal: Double,
    /** Up to a few short excerpts of this speaker's own turns, for the AI prompt's benefit —
     * never derived from anything but this speaker's own real segments. */
    val sampleTexts: List<String>
)

/** A speaker id's footprint below this share of total speaking time is a candidate for AI review
 * — deliberately the same order of magnitude as [MAX_NOISE_SHARE_WHEN_FLAGGING] in
 * [SherpaSpeakerDiarizer], since this engine exists specifically to cover the residual case that
 * threshold's own [MIN_NOISE_SPEAKERS_TO_FLAG] gate leaves untouched: exactly one minor speaker. */
const val AMBIGUOUS_SHARE_THRESHOLD = 0.15

/** Computes each distinct speaker id's aggregate footprint from already-diarized transcript
 * segments — pure and testable without touching the diarizer itself. A segment with a null
 * speakerId (diarization skipped/unavailable) is excluded; there is nothing to reconcile when
 * there is no speaker information at all. */
fun computeSpeakerTranscriptFootprints(segments: List<TranscriptSegment>): List<SpeakerTranscriptFootprint> {
    val withSpeaker = segments.filter { it.speakerId != null }
    val totalDurationMs = withSpeaker.sumOf { it.endMs - it.startMs }
    if (totalDurationMs <= 0L) return emptyList()
    return withSpeaker.groupBy { it.speakerId!! }.map { (speakerId, segs) ->
        val duration = segs.sumOf { it.endMs - it.startMs }
        SpeakerTranscriptFootprint(
            speakerId = speakerId,
            speakerName = segs.firstOrNull()?.speakerName,
            totalDurationMs = duration,
            turnCount = segs.size,
            shareOfTotal = duration.toDouble() / totalDurationMs,
            sampleTexts = segs.sortedBy { it.startMs }.take(3).map { (it.cleanedText ?: it.text).take(160) }
        )
    }
}

/** Speaker ids worth asking the AI layer about: below [AMBIGUOUS_SHARE_THRESHOLD], with at least
 * one "confident" (at-or-above-threshold) speaker present to potentially merge into. Returns the
 * empty set — no AI call worth making — when every speaker is confident (a plausible multi-speaker
 * split, e.g. 52%/48%) or when every speaker is ambiguous (nothing confident to anchor a merge
 * against, so guessing would be exactly the "arbitrarily rewrite speaker IDs" behavior this engine
 * must never do). */
fun ambiguousSpeakerIds(footprints: List<SpeakerTranscriptFootprint>): Set<String> {
    if (footprints.size < 2) return emptySet()
    val ambiguous = footprints.filter { it.shareOfTotal < AMBIGUOUS_SHARE_THRESHOLD }
    val confident = footprints.filter { it.shareOfTotal >= AMBIGUOUS_SHARE_THRESHOLD }
    if (ambiguous.isEmpty() || confident.isEmpty()) return emptySet()
    return ambiguous.map { it.speakerId }.toSet()
}

/**
 * Decides whether an AI-assisted reconciliation pass is actually worth attempting, given the
 * user's selected [DiarizationStrategy] and what the deterministic pass already left behind.
 * [DiarizationStrategy.DETERMINISTIC] never attempts it. [DiarizationStrategy.AI_ASSISTED] and
 * [DiarizationStrategy.AUTO] both only attempt it when [ambiguousSpeakerIds] actually finds
 * something to ask about — there is no behavioral difference between the two today (AUTO has no
 * additional signal yet to be more or less conservative than an explicit request with), but they
 * are kept as separate cases so a future AUTO heuristic (e.g. skip on a very long recording to
 * save battery) has a real branch to grow into without touching AI_ASSISTED's meaning.
 */
fun shouldAttemptAiReconciliation(
    footprints: List<SpeakerTranscriptFootprint>,
    strategy: DiarizationStrategy
): Boolean = when (strategy) {
    DiarizationStrategy.DETERMINISTIC -> false
    DiarizationStrategy.AI_ASSISTED -> ambiguousSpeakerIds(footprints).isNotEmpty()
    DiarizationStrategy.AUTO -> ambiguousSpeakerIds(footprints).isNotEmpty()
}

/**
 * Real implementation: prompts a local instruct LLM with each ambiguous speaker's footprint and
 * sample text alongside every confident speaker's footprint and sample text, and asks it to
 * propose merges using textual continuity (a self-correction split across the boundary, a
 * sentence that plainly continues) — never asked to reconsider a confident speaker's own identity.
 * Every proposed merge is validated before being applied: both ids must be real ids from this
 * exact call's footprints, `from` must be one of the ambiguous ids offered, and `into` must be one
 * of the confident ids offered — a model that hallucinates an id, proposes merging two confident
 * speakers, or returns anything unparseable simply contributes no change, never a guessed one.
 */
class RealDiarizationReconciliationEngine(
    private val languageModel: LanguageModel
) : DiarizationReconciliationEngine {

    override suspend fun reconcile(segments: List<TranscriptSegment>): AiResult<DiarizationReconciliationResult> {
        val footprints = computeSpeakerTranscriptFootprints(segments)
        val ambiguousIds = ambiguousSpeakerIds(footprints)
        if (ambiguousIds.isEmpty()) {
            return AiResult.Success(DiarizationReconciliationResult(segments, emptySet(), emptyList()))
        }
        val ambiguous = footprints.filter { it.speakerId in ambiguousIds }
        val confident = footprints.filter { it.speakerId !in ambiguousIds }

        val prompt = buildPrompt(ambiguous, confident)
        val result = languageModel.generate(prompt, maxOutputTokens = OUTPUT_TOKENS)
        if (result is AiResult.ModelUnavailable) return result
        val text = (result as? AiResult.Success)?.value
            ?: return AiResult.Success(DiarizationReconciliationResult(segments, emptySet(), emptyList()))

        val proposals = parseMergeResponse(text)
        val confidentIds = confident.map { it.speakerId }.toSet()
        val validMerges = proposals.filter { it.from in ambiguousIds && it.into in confidentIds }
        if (validMerges.isEmpty()) {
            return AiResult.Success(DiarizationReconciliationResult(segments, emptySet(), emptyList()))
        }

        val mergeTarget = validMerges.associate { it.from to it.into }
        val targetFootprint = confident.associateBy { it.speakerId }
        val mergedSegments = segments.map { seg ->
            val into = seg.speakerId?.let { mergeTarget[it] } ?: return@map seg
            val target = targetFootprint[into] ?: return@map seg
            seg.copy(speakerId = target.speakerId, speakerName = target.speakerName)
        }
        return AiResult.Success(
            DiarizationReconciliationResult(
                segments = mergedSegments,
                mergedSpeakerIds = mergeTarget.keys,
                reasons = validMerges.map { "${it.from} -> ${it.into}: ${it.reason}" }
            )
        )
    }

    private fun buildPrompt(ambiguous: List<SpeakerTranscriptFootprint>, confident: List<SpeakerTranscriptFootprint>): String {
        fun render(f: SpeakerTranscriptFootprint): String =
            "[${f.speakerId}] (${"%.0f".format(f.shareOfTotal * 100)}% of recording, ${f.turnCount} turns): " +
                f.sampleTexts.joinToString(" | ")

        return """
            You are reviewing possibly-mistaken speaker labels from an automatic speaker-detection system.
            A MINOR speaker below is a small share of the recording — it may be a real, brief participant, OR it may actually be the SAME PERSON as one of the MAIN speakers, misclassified due to a brief acoustic glitch (a cough, background noise, a garbled word).

            Only propose a merge when the minor speaker's own words plainly read as a continuation, self-correction, or restart of something a main speaker was already saying — never merely because the minor speaker is small.

            MAIN SPEAKERS:
            ${confident.joinToString("\n") { render(it) }}

            MINOR SPEAKERS TO REVIEW:
            ${ambiguous.joinToString("\n") { render(it) }}

            Respond with ONLY a JSON array, no markdown, no commentary. One entry per minor speaker you are CONFIDENT should merge — omit any minor speaker that may be a real separate person:
            [{"from":"<minor speaker id>","into":"<main speaker id>","reason":"short reason"}]
            If no minor speaker should merge, respond with: []
        """.trimIndent()
    }

    private data class MergeProposal(val from: String, val into: String, val reason: String)

    /** Salvage-tolerant like [com.example.ai.pipeline.RealTranscriptAiCleanupEngine]'s response
     * parsing — takes the first `[` through the last `]` so stray prose around the array still
     * parses, and returns an empty list (never a guessed partial result) on anything unparseable. */
    private fun parseMergeResponse(rawText: String): List<MergeProposal> {
        val start = rawText.indexOf('[')
        val end = rawText.lastIndexOf(']')
        if (start == -1 || end == -1 || end < start) return emptyList()
        return try {
            val array = JSONArray(rawText.substring(start, end + 1))
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val from = obj.optString("from").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val into = obj.optString("into").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                MergeProposal(from, into, obj.optString("reason").ifBlank { "no reason given" })
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private companion object {
        const val OUTPUT_TOKENS = 400
    }
}
