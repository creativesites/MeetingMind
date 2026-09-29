package com.craftflowtechnologies.meetingmind.ai.transcription

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.cloud.AudioChunk
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTranscriptionEngine
import com.craftflowtechnologies.meetingmind.ai.transcript.AsrWindowReconciler
import com.craftflowtechnologies.meetingmind.ai.transcript.CanonicalWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import java.io.File

/** What the person sees about a transcription in progress, and what they can ask of it. */
data class RouteInfo(
    val current: TranscriptionRoute,
    /** The other engine can take over right now. */
    val canSwitchTo: TranscriptionRoute?,
    val percentDone: Int = 0,
    /** Why the last switch happened: "Gemini stopped answering — continuing on this phone". */
    val note: String? = null
)

/**
 * Per-recording state for switching engines mid-run: what's running and what the person asked
 * for. Held in memory — a request only means something while the run is alive.
 */
object TranscriptionRoutes {
    private val _info = MutableStateFlow<Map<String, RouteInfo>>(emptyMap())
    val info: StateFlow<Map<String, RouteInfo>> = _info.asStateFlow()
    private val requests = MutableStateFlow<Map<String, TranscriptionRoute>>(emptyMap())

    /** Ask a running transcription to continue on [route]. */
    fun request(meetingId: String, route: TranscriptionRoute) { requests.value = requests.value + (meetingId to route) }

    internal suspend fun awaitRequestOtherThan(meetingId: String, current: TranscriptionRoute): TranscriptionRoute =
        requests.first { it[meetingId]?.let { r -> r != current } == true }.getValue(meetingId)

    internal fun consume(meetingId: String) { requests.value = requests.value - meetingId }
    internal fun publish(meetingId: String, info: RouteInfo?) { _info.value = if (info == null) _info.value - meetingId else _info.value + (meetingId to info) }
    internal fun peek(meetingId: String): TranscriptionRoute? = requests.value[meetingId]
}

/** How a whole transcription run ended. */
data class RunOutcome(
    val regions: List<DoneRegion>,
    /** Every stretch is done (a few seconds of silence at a seam don't count). */
    val complete: Boolean,
    /** Set when the run stopped short: what the last engine said. */
    val failure: String?,
    /** Gemini's message when it failed and the phone took over. */
    val geminiFailure: String? = null
) {
    val usedGemini get() = regions.any { it.route == TranscriptionRoute.GEMINI }
    val usedLocal get() = regions.any { it.route == TranscriptionRoute.LOCAL }

    /** Everything, in order, with the overlap between regions reconciled. */
    fun words(): List<CanonicalWord> {
        val sorted = regions.sortedBy { it.startMs }
        // Gemini's speaker ids are per part; they are matched up across parts before anything is joined.
        val geminiParts = sorted.filter { it.route == TranscriptionRoute.GEMINI }.mapIndexed { i, r -> com.craftflowtechnologies.meetingmind.ai.cloud.ChunkTranscription(AudioChunk(i, r.startMs, r.endMs), r.words) }
        val resolved = if (geminiParts.isEmpty()) emptyList() else {
            com.craftflowtechnologies.meetingmind.ai.cloud.GlobalSpeakerResolver.applyMapping(geminiParts, com.craftflowtechnologies.meetingmind.ai.cloud.GlobalSpeakerResolver.resolve(geminiParts))
        }
        var g = 0
        return AsrWindowReconciler.reconcile(sorted.map { r -> if (r.route == TranscriptionRoute.GEMINI) resolved[g++].words else r.words })
    }
}

/**
 * Runs one recording's transcription across engines, with the finished regions saved as it goes.
 *
 * ```
 * start on Gemini or the phone
 *   ├─ a stretch finishes  → saved
 *   ├─ the person switches → the running engine stops; the other one does what isn't done
 *   ├─ the engine fails    → Gemini falls back to the phone; the phone has no fallback
 *   └─ the app is killed   → next run starts from what was saved
 * ```
 * Pure orchestration: the engines are passed in, so every path is tested without audio.
 */
class TranscriptionRunner(
    private val store: TranscriptionCheckpoints,
    private val gemini: GeminiTranscriptionEngine?,
    /** Transcribes only [gaps] on this phone; reports each finished stretch through [onRegion]. */
    private val local: suspend (gaps: List<LongRange>, onRegion: (Long, Long, List<CanonicalWord>) -> Unit, onProgress: (Float, String) -> Unit) -> AiResult<List<CanonicalWord>>
) {
    suspend fun run(
        meetingId: String,
        audioFile: File,
        totalMs: Long,
        start: TranscriptionRoute,
        vocabularyHints: List<String> = emptyList(),
        onProgress: (Float, String) -> Unit = { _, _ -> },
        onPartial: suspend (List<CanonicalWord>, Int, Int) -> Unit = { _, _, _ -> }
    ): RunOutcome {
        val fp = store.fingerprint(audioFile, totalMs)
        var regions = store.load(meetingId, fp)
        var route = if (start == TranscriptionRoute.GEMINI && gemini == null) TranscriptionRoute.LOCAL else start
        var geminiFailure: String? = null
        var failure: String? = null
        var note: String? = if (regions.isNotEmpty()) "Continuing from where it stopped" else null
        // Guard against a request that flips forever: a handful of hand-overs is plenty.
        repeat(MAX_HANDOVERS) {
            val gaps = Coverage.gaps(totalMs, regions)
            if (gaps.isEmpty()) return RunOutcome(regions, true, null, geminiFailure)
            val other = if (route == TranscriptionRoute.GEMINI) TranscriptionRoute.LOCAL else TranscriptionRoute.GEMINI
            TranscriptionRoutes.publish(meetingId, RouteInfo(route, other.takeIf { it != TranscriptionRoute.GEMINI || gemini != null }, percent(totalMs, regions), note))
            val stage = runStage(meetingId, fp, audioFile, totalMs, route, gaps, regions, vocabularyHints, onProgress, onPartial)
            regions = store.load(meetingId, fp)
            when (stage) {
                is Stage.Finished -> { if (Coverage.gaps(totalMs, regions).isEmpty()) return RunOutcome(regions, true, null, geminiFailure) }
                is Stage.Switched -> {
                    TranscriptionRoutes.consume(meetingId)
                    route = stage.to
                    note = "Switched to ${route.label}"
                }
                is Stage.Failed -> {
                    if (route == TranscriptionRoute.GEMINI) {
                        geminiFailure = stage.message
                        route = TranscriptionRoute.LOCAL
                        note = "Gemini stopped — continuing on this phone"
                    } else {
                        failure = stage.message
                        return RunOutcome(regions, false, failure, geminiFailure)
                    }
                }
            }
        }
        return RunOutcome(regions, Coverage.gaps(totalMs, regions).isEmpty(), failure ?: "Transcription kept switching engines without finishing.", geminiFailure)
    }

    private sealed interface Stage {
        data object Finished : Stage
        data class Switched(val to: TranscriptionRoute) : Stage
        data class Failed(val message: String) : Stage
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun runStage(
        meetingId: String, fp: String, audio: File, totalMs: Long, route: TranscriptionRoute, gaps: List<LongRange>,
        regions: List<DoneRegion>, hints: List<String>, onProgress: (Float, String) -> Unit, onPartial: suspend (List<CanonicalWord>, Int, Int) -> Unit
    ): Stage = coroutineScope {
        val work = async<Stage> {
            try { when (route) {
                TranscriptionRoute.GEMINI -> {
                    val engine = gemini ?: return@async Stage.Failed("Gemini isn't set up.")
                    val r = engine.transcribe(
                        audioFile = audio, totalDurationMs = totalMs, vocabularyHints = hints, onProgress = onProgress, onPartial = onPartial,
                        restored = regions,
                        onChunkDone = { chunk: AudioChunk, words -> store.append(meetingId, fp, DoneRegion(chunk.startMs, chunk.endMs, TranscriptionRoute.GEMINI, words)) }
                    )
                    if (r is AiResult.Success) {
                        Stage.Finished
                    } else Stage.Failed(r.describeFailure() ?: "Gemini failed.")
                }
                TranscriptionRoute.LOCAL -> {
                    val r = local(gaps, { s, e, words -> store.append(meetingId, fp, DoneRegion(s, e, TranscriptionRoute.LOCAL, words)) }, onProgress)
                    if (r is AiResult.Success) {
                        // Whatever the engine returned that no window reported (and stretches with no
                        // speech at all) is recorded now, so nothing is left looking "missing".
                        val after = store.load(meetingId, fp)
                        Coverage.gaps(totalMs, after).forEach { g ->
                            store.append(meetingId, fp, DoneRegion(g.first, g.last + 1, TranscriptionRoute.LOCAL, r.value.filter { it.startMs >= g.first && it.startMs <= g.last }))
                        }
                        Stage.Finished
                    } else Stage.Failed(r.describeFailure() ?: "Transcription on this phone failed.")
                }
            } } catch (e: CancellationException) { throw e } catch (e: Exception) { Stage.Failed(e.message ?: "The transcription engine stopped unexpectedly.") }
        }
        val switch = async { Stage.Switched(TranscriptionRoutes.awaitRequestOtherThan(meetingId, route)) }
        try {
            select<Stage> {
                work.onAwait { switch.cancel(); it }
                switch.onAwait { work.cancel(); it }
            }
        } catch (e: CancellationException) {
            work.cancel(); switch.cancel(); throw e
        }
    }

    private fun percent(totalMs: Long, regions: List<DoneRegion>) = if (totalMs <= 0) 0 else (Coverage.doneMs(totalMs, regions) * 100 / totalMs).toInt()

    private companion object { const val MAX_HANDOVERS = 6 }
}
