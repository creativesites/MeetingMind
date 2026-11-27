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
import kotlinx.coroutines.flow.update
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

    /** Raises the shown "N% done" as parts finish. Never lowers it, and never brings back a run that ended. */
    internal fun raisePercent(meetingId: String, percent: Int) {
        _info.update { all ->
            val current = all[meetingId] ?: return@update all
            if (percent <= current.percentDone) all else all + (meetingId to current.copy(percentDone = percent.coerceAtMost(100)))
        }
    }

    /** Forgets everything about [meetingId]: its route and any switch asked for. Called when a run starts and ends. */
    fun clear(meetingId: String) {
        consume(meetingId)
        publish(meetingId, null)
    }
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
    val geminiFailure: String? = null,
    /** The phone was needed but has no speech model it can use. */
    val localModelMissing: Boolean = false,
    /** The person moved the run to this phone themselves (not a Gemini failure). */
    val switchedToPhoneByUser: Boolean = false
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
    /**
     * Whether this phone has a speech model it can transcribe with. When false the phone is never
     * offered as a route, and a Gemini failure ends the run as "model missing" instead of handing
     * over to an engine that can only fail.
     */
    private val localAvailable: Boolean = true,
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
        // A switch asked for in an earlier run (or after it ended) must not steer this one, and
        // nothing is offered once the run is over.
        TranscriptionRoutes.clear(meetingId)
        try {
            return runRoutes(meetingId, audioFile, totalMs, start, vocabularyHints, onProgress, onPartial)
        } finally {
            TranscriptionRoutes.clear(meetingId)
        }
    }

    private suspend fun runRoutes(
        meetingId: String,
        audioFile: File,
        totalMs: Long,
        start: TranscriptionRoute,
        vocabularyHints: List<String>,
        onProgress: (Float, String) -> Unit,
        onPartial: suspend (List<CanonicalWord>, Int, Int) -> Unit
    ): RunOutcome {
        val fp = store.fingerprint(audioFile, totalMs)
        var regions = store.load(meetingId, fp)
        var route = if (start == TranscriptionRoute.GEMINI && gemini == null) TranscriptionRoute.LOCAL else start
        var geminiFailure: String? = null
        var failure: String? = null
        var switchedByUser = false
        var note: String? = if (regions.isNotEmpty()) "Continuing from where it stopped" else null
        // What is done so far, kept in memory so "N% done" can rise after every finished part.
        val live = java.util.Collections.synchronizedList(regions.toMutableList())
        val onSaved: (DoneRegion) -> Unit = { region ->
            live += region
            TranscriptionRoutes.raisePercent(meetingId, percent(totalMs, synchronized(live) { live.toList() }))
        }
        // Guard against a request that flips forever: a handful of hand-overs is plenty.
        repeat(MAX_HANDOVERS) {
            val gaps = Coverage.gaps(totalMs, regions)
            if (gaps.isEmpty()) return RunOutcome(regions, true, null, geminiFailure, switchedToPhoneByUser = switchedByUser)
            if (route == TranscriptionRoute.LOCAL && !localAvailable) {
                return RunOutcome(regions, false, failure ?: geminiFailure ?: NO_PHONE_MODEL, geminiFailure, localModelMissing = true)
            }
            val other = if (route == TranscriptionRoute.GEMINI) TranscriptionRoute.LOCAL else TranscriptionRoute.GEMINI
            val offered = other.takeIf { (it != TranscriptionRoute.GEMINI || gemini != null) && (it != TranscriptionRoute.LOCAL || localAvailable) }
            val shown = maxOf(percent(totalMs, regions), TranscriptionRoutes.info.value[meetingId]?.percentDone ?: 0)
            TranscriptionRoutes.publish(meetingId, RouteInfo(route, offered, shown, note))
            val stage = runStage(meetingId, fp, audioFile, totalMs, route, gaps, regions, vocabularyHints, onProgress, onPartial, onSaved)
            regions = store.load(meetingId, fp)
            synchronized(live) { live.clear(); live.addAll(regions) }
            when (stage) {
                is Stage.Finished -> { if (Coverage.gaps(totalMs, regions).isEmpty()) return RunOutcome(regions, true, null, geminiFailure, switchedToPhoneByUser = switchedByUser) }
                is Stage.Switched -> {
                    TranscriptionRoutes.consume(meetingId)
                    route = stage.to
                    switchedByUser = route == TranscriptionRoute.LOCAL
                    note = "Switched to ${route.label}"
                }
                is Stage.Failed -> {
                    if (route == TranscriptionRoute.GEMINI) {
                        geminiFailure = stage.message
                        route = TranscriptionRoute.LOCAL
                        note = "Gemini stopped — continuing on this phone"
                    } else {
                        failure = stage.message
                        return RunOutcome(regions, false, failure, geminiFailure, localModelMissing = stage.modelMissing, switchedToPhoneByUser = switchedByUser)
                    }
                }
            }
        }
        return RunOutcome(regions, Coverage.gaps(totalMs, regions).isEmpty(), failure ?: "Transcription kept switching engines without finishing.", geminiFailure, switchedToPhoneByUser = switchedByUser)
    }

    private sealed interface Stage {
        data object Finished : Stage
        data class Switched(val to: TranscriptionRoute) : Stage
        data class Failed(val message: String, val modelMissing: Boolean = false) : Stage
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun runStage(
        meetingId: String, fp: String, audio: File, totalMs: Long, route: TranscriptionRoute, gaps: List<LongRange>,
        regions: List<DoneRegion>, hints: List<String>, onProgress: (Float, String) -> Unit, onPartial: suspend (List<CanonicalWord>, Int, Int) -> Unit,
        onSaved: (DoneRegion) -> Unit
    ): Stage = coroutineScope {
        val work = async<Stage> {
            try { when (route) {
                TranscriptionRoute.GEMINI -> {
                    val engine = gemini ?: return@async Stage.Failed("Gemini isn't set up.")
                    val r = engine.transcribe(
                        audioFile = audio, totalDurationMs = totalMs, vocabularyHints = hints, onProgress = onProgress, onPartial = onPartial,
                        restored = regions,
                        onChunkDone = { chunk: AudioChunk, words ->
                            val region = DoneRegion(chunk.startMs, chunk.endMs, TranscriptionRoute.GEMINI, words)
                            store.append(meetingId, fp, region)
                            onSaved(region)
                        }
                    )
                    if (r is AiResult.Success) {
                        Stage.Finished
                    } else Stage.Failed(r.describeFailure() ?: "Gemini failed.")
                }
                TranscriptionRoute.LOCAL -> {
                    val r = local(gaps, { s, e, words ->
                        val region = DoneRegion(s, e, TranscriptionRoute.LOCAL, words)
                        store.append(meetingId, fp, region)
                        onSaved(region)
                    }, onProgress)
                    if (r is AiResult.Success) {
                        // Whatever the engine returned that no window reported (and stretches with no
                        // speech at all) is recorded now, so nothing is left looking "missing".
                        val after = store.load(meetingId, fp)
                        Coverage.gaps(totalMs, after).forEach { g ->
                            store.append(meetingId, fp, DoneRegion(g.first, g.last + 1, TranscriptionRoute.LOCAL, r.value.filter { it.startMs >= g.first && it.startMs <= g.last }))
                        }
                        Stage.Finished
                    } else Stage.Failed(r.describeFailure() ?: "Transcription on this phone failed.", modelMissing = r is AiResult.ModelUnavailable)
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

    private companion object {
        const val MAX_HANDOVERS = 6
        const val NO_PHONE_MODEL = "There is no speech model on this phone to continue with."
    }
}
