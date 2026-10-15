package com.craftflowtechnologies.meetingmind.ai.diarization

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.transcript.DiarizationTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** User-facing line shown when speaker separation had to be abandoned. */
const val SPEAKERS_NOT_SEPARATED_MESSAGE = "Couldn't separate speakers — continuing without speaker labels"

internal const val MIN_DIARIZATION_BUDGET_MS = 60_000L
internal const val MAX_DIARIZATION_BUDGET_MS = 20 * 60_000L

/**
 * How long speaker separation may run before it is abandoned: half the audio's duration,
 * clamped to 60 s .. 20 min. Sherpa's clustering is super-linear in recording length and is one
 * blocking native call, so without a ceiling a long recording can sit "Identifying speakers"
 * for hours.
 */
fun diarizationBudgetMs(audioDurationMs: Long): Long =
    (audioDurationMs / 2).coerceIn(MIN_DIARIZATION_BUDGET_MS, MAX_DIARIZATION_BUDGET_MS)

/** What a budgeted diarization attempt produced. */
sealed interface DiarizationOutcome {
    /** Speakers were separated. */
    data class Separated(val turns: List<DiarizationTurn>) : DiarizationOutcome

    /** No diarization model is installed — the pre-existing, silent no-labels path. */
    data object Unavailable : DiarizationOutcome

    /** It ran out of time or crashed (including OutOfMemoryError); continue without speaker labels. */
    data class Degraded(val reason: String) : DiarizationOutcome
}

/**
 * Runs [run] under a [budgetMs] ceiling and never lets it take the pipeline down: a timeout or
 * any non-cancellation [Throwable] (OutOfMemoryError included) becomes [DiarizationOutcome.Degraded].
 * Caller cancellation still propagates.
 *
 * [run] must itself be cancellable (suspend on something that completes when the work does, e.g.
 * `Deferred.await()` of a job on a dedicated thread), otherwise the timeout cannot fire.
 */
suspend fun runDiarizationWithBudget(
    budgetMs: Long,
    run: suspend () -> AiResult<List<DiarizationTurn>>
): DiarizationOutcome = try {
    when (val result = withTimeoutOrNull(budgetMs) { run() }) {
        null -> DiarizationOutcome.Degraded("Speaker separation exceeded its ${budgetMs / 1000}s budget")
        is AiResult.Success -> DiarizationOutcome.Separated(result.value)
        is AiResult.ModelUnavailable -> DiarizationOutcome.Unavailable
        else -> DiarizationOutcome.Degraded(result.describeFailure() ?: "Speaker separation failed")
    }
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    DiarizationOutcome.Degraded(t.message ?: t::class.java.simpleName)
}

internal const val MIN_CLEANUP_BUDGET_MS = 2 * 60_000L
internal const val MAX_CLEANUP_BUDGET_MS = 15 * 60_000L
internal const val MIN_RECONCILIATION_BUDGET_MS = 60_000L
internal const val MAX_RECONCILIATION_BUDGET_MS = 5 * 60_000L

/**
 * How long the optional AI transcript-cleanup pass may run: a quarter of the audio's duration,
 * clamped to 2 .. 15 min. On timeout the pipeline keeps the rule-based cleanup it already has.
 */
fun cleanupBudgetMs(audioDurationMs: Long): Long =
    (audioDurationMs / 4).coerceIn(MIN_CLEANUP_BUDGET_MS, MAX_CLEANUP_BUDGET_MS)

/**
 * How long the optional AI speaker-reconciliation pass may run: a tenth of the audio's duration,
 * clamped to 1 .. 5 min. On timeout the deterministic speaker result stands.
 */
fun reconciliationBudgetMs(audioDurationMs: Long): Long =
    (audioDurationMs / 10).coerceIn(MIN_RECONCILIATION_BUDGET_MS, MAX_RECONCILIATION_BUDGET_MS)
