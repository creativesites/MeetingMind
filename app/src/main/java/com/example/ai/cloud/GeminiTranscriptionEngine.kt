package com.example.ai.cloud

import com.example.ai.common.AiResult
import com.example.ai.common.describeFailure
import com.example.ai.routing.AiModelRouter
import com.example.ai.routing.AiRoute
import com.example.ai.routing.DefaultAiModelRouter
import com.example.ai.transcript.AsrWindowReconciler
import com.example.ai.transcript.AttributionConfidence
import com.example.ai.transcript.CanonicalWord
import com.example.ai.transcript.TranscriptSource
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

/** What a cloud transcription run produced, and how it got there. */
data class CloudTranscriptionResult(
    val words: List<CanonicalWord>,
    val chunkCount: Int,
    /** True when the smart pass ran and was actually fused in. */
    val smartPassApplied: Boolean,
    val alignmentCoverage: Float?,
    /** Populated when a stage failed but the run continued with what it already had. */
    val degradedReason: String? = null
)

/**
 * Internet-mode transcription: two Gemini passes over the same chunk plan, fused.
 *
 * ```
 * chunks -> verbatim pass   (words, timestamps, diarization)  -- the fidelity layer
 *        -> global speaker resolution across chunks
 *        -> cross-chunk overlap reconciliation
 *        -> smart pass      (punctuation, paragraphs, cleanup) -- the readability layer
 *        -> fusion          (smart text onto verbatim timing)
 * ```
 *
 * ### Why two passes
 *
 * The transcription model cannot give diarization and word-level timestamps *and* polished,
 * disfluency-cleaned prose in one call — the configurations are mutually exclusive. Asking for the
 * polished version alone would mean a transcript with no timing and no speakers, which makes
 * provenance, playback sync, search-by-time and speaker attribution all impossible. So the app
 * asks for both, separately, and treats the first as truth and the second as presentation.
 *
 * ### Degradation
 *
 * Every stage after verbatim is optional. If the smart pass fails, the verbatim transcript is
 * returned. If fusion finds too little agreement to trust, the verbatim transcript is returned.
 * A failure downstream never discards a successful result upstream.
 */
class GeminiTranscriptionEngine(
    private val transport: GeminiTransport,
    private val parser: GeminiTranscriptParser = GeminiTranscriptParser,
    private val router: AiModelRouter = DefaultAiModelRouter,
    private val chunkConfig: ChunkPlanConfig = ChunkPlanConfig(),
    /**
     * The readability pass. Off: it doubled the time to a transcript, and the transcription model
     * gives it no timestamps or speakers to fuse onto. Readability now comes from the transcript
     * cleanup stage after the verbatim words are in (docs/PLAN_V3.md W12, phase 1).
     */
    private val smartPass: Boolean = false
) {

    suspend fun transcribe(
        audioFile: File,
        totalDurationMs: Long,
        vocabularyHints: List<String> = emptyList(),
        onProgress: (progress: Float, statusText: String) -> Unit = { _, _ -> },
        /** The words of the parts finished so far, in order — to show before the rest is done. */
        onPartial: suspend (words: List<CanonicalWord>, done: Int, total: Int) -> Unit = { _, _, _ -> }
    ): AiResult<CloudTranscriptionResult> {
        if (!transport.refreshConfigured()) {
            return AiResult.ModelUnavailable(
                modelId = router.route(AiRoute.GEMINI_TRANSCRIPTION_VERBATIM).modelId ?: "gemini",
                message = "Internet mode isn't set up on this build. Offline processing is unaffected."
            )
        }

        val chunks = GeminiChunkPlanner.plan(totalDurationMs, chunkConfig)
        GeminiLog.add("Transcription plan: ${chunks.size} part(s) for ${totalDurationMs / 1000}s of audio")
        if (chunks.isEmpty()) return AiResult.Success(CloudTranscriptionResult(emptyList(), 0, false, null))

        // --- Pass A: verbatim. This one is required; without it there is no transcript at all. ---
        val verbatimModel = router.route(AiRoute.GEMINI_TRANSCRIPTION_VERBATIM).modelId
            ?: return AiResult.Failed("No Gemini transcription model is configured.")

        // Parts go out a few at a time and come back in order: each finished prefix of the
        // recording is handed on at once (a progressive transcript), without waiting for the rest.
        val transcribedChunks = mutableListOf<ChunkTranscription>()
        val started = System.currentTimeMillis()
        val failure: AiResult<CloudTranscriptionResult>? = kotlinx.coroutines.coroutineScope {
            val gate = kotlinx.coroutines.sync.Semaphore(if (chunks.size > 1) CONCURRENCY else 1)
            val jobs = chunks.map { chunk ->
                async {
                    gate.acquire()
                    try {
                        transport.execute(
                            GeminiRequest(
                                modelId = verbatimModel,
                                systemInstruction = VERBATIM_SYSTEM_INSTRUCTION,
                                prompt = VERBATIM_PROMPT,
                                audioFile = audioFile,
                                audioStartMs = chunk.startMs,
                                audioEndMs = chunk.endMs,
                                // The transcription model's own request. Custom vocabulary is left out:
                                // the API rejects it alongside timestamps and speakers.
                                transcription = AudioTranscriptionConfig(mode = "VERBATIM", wordTimestamp = true, diarization = true),
                                // One chunk is the whole recording: send the file as it is, no decoding.
                                uploadWholeFile = chunks.size == 1
                            )
                        )
                    } finally { gate.release() }
                }
            }
            onProgress(0f, if (chunks.size > 1) "Transcribing ${chunks.size} parts…" else "Transcribing…")
            for ((i, job) in jobs.withIndex()) {
                val chunk = chunks[i]
                val response = job.await()
                if (response !is AiResult.Success) {
                    // A transcript with a silent hole in the middle is worse than an honest failure.
                    jobs.forEach { it.cancel() }
                    return@coroutineScope AiResult.Failed(
                        "Cloud transcription failed on part ${chunk.index + 1} of ${chunks.size}: " + (response.describeFailure() ?: "unknown error")
                    )
                }
                val words = parser.parseVerbatim(response.value, chunkStartMs = chunk.startMs)
                GeminiLog.add("Part ${chunk.index + 1}: ${words?.size ?: "unreadable"} words")
                if (words == null) {
                    jobs.forEach { it.cancel() }
                    return@coroutineScope AiResult.Failed("Cloud transcription returned an unreadable result for part ${chunk.index + 1}.")
                }
                transcribedChunks += ChunkTranscription(chunk, words)
                if (i == 0) GeminiLog.add("Time to first transcript: ${(System.currentTimeMillis() - started) / 1000}s")
                onProgress((i + 1).toFloat() / (chunks.size * 2), "Transcribed ${i + 1} of ${chunks.size} parts")
                if (chunks.size > 1 && i < chunks.lastIndex) {
                    val so = GlobalSpeakerResolver.applyMapping(transcribedChunks, GlobalSpeakerResolver.resolve(transcribedChunks))
                    runCatching { onPartial(AsrWindowReconciler.reconcile(so.map { it.words }), i + 1, chunks.size) }
                }
            }
            null
        }
        if (failure != null) return failure

        // --- Cross-chunk resolution: speakers first, then duplicated words. ---
        val mapping = GlobalSpeakerResolver.resolve(transcribedChunks)
        val globalised = GlobalSpeakerResolver.applyMapping(transcribedChunks, mapping)
        val verbatimWords = AsrWindowReconciler.reconcile(globalised.map { it.words })

        // An empty transcript is reported, not saved as a finished recording with nothing in it.
        if (verbatimWords.isEmpty()) {
            return AiResult.Failed("Gemini returned no words for this recording. If there is speech in it, open Settings → Gemini log and send the lines to the developer.")
        }

        if (!smartPass) {
            onProgress(1f, "Transcript ready")
            return AiResult.Success(CloudTranscriptionResult(verbatimWords, chunks.size, smartPassApplied = false, alignmentCoverage = null))
        }

        // --- Pass B: smart. Optional by design — a failure here costs polish, not the transcript. ---
        onProgress(0.6f, "Polishing the transcript...")
        val smartModel = router.route(AiRoute.GEMINI_TRANSCRIPTION_SMART).modelId
        val smartByChunk = mutableMapOf<Int, String>()
        if (smartModel != null) {
            for (chunk in chunks) {
                coroutineContext.ensureActive()
                val response = transport.execute(
                    GeminiRequest(
                        modelId = smartModel,
                        systemInstruction = SMART_SYSTEM_INSTRUCTION,
                        prompt = SMART_PROMPT,
                        audioFile = audioFile,
                        audioStartMs = chunk.startMs,
                        audioEndMs = chunk.endMs,
                        vocabularyHints = vocabularyHints
                    )
                )
                // One chunk failing costs that chunk's polish, not the whole readability pass:
                // the chunks that did come back are still fused, and the rest stay verbatim.
                if (response is AiResult.Success && response.value.isNotBlank()) {
                    smartByChunk[chunk.index] = response.value.trim()
                }
            }
        }

        if (smartByChunk.isEmpty()) {
            return AiResult.Success(
                CloudTranscriptionResult(
                    words = verbatimWords,
                    chunkCount = chunks.size,
                    smartPassApplied = false,
                    alignmentCoverage = null,
                    degradedReason = "The readability pass didn't complete; the verbatim transcript is unaffected."
                )
            )
        }

        val fused = fusePerChunk(verbatimWords, chunks, smartByChunk)
        onProgress(1f, "Transcript ready")
        return AiResult.Success(
            CloudTranscriptionResult(
                words = fused.words,
                chunkCount = chunks.size,
                smartPassApplied = fused.alignmentCoverage >= TranscriptFusionEngine.MIN_USABLE_COVERAGE,
                alignmentCoverage = fused.alignmentCoverage,
                degradedReason = when {
                    fused.alignmentCoverage >= TranscriptFusionEngine.MIN_USABLE_COVERAGE &&
                        smartByChunk.size == chunks.size -> null
                    fused.alignmentCoverage < TranscriptFusionEngine.MIN_USABLE_COVERAGE ->
                        "The readability pass disagreed too broadly with the verbatim transcript to be merged; the verbatim transcript is unaffected."
                    else ->
                        "The readability pass didn't complete for every part of the recording; those parts are shown verbatim."
                }
            )
        )
    }

    /**
     * Fuses one chunk at a time rather than the whole recording at once.
     *
     * Alignment is quadratic in the number of words, so fusing a long meeting in one pass builds a
     * table large enough that [TranscriptFusionEngine] refuses it outright — a 90-minute recording
     * would silently come back verbatim with no indication why. Chunk-sized passes keep every
     * table small regardless of how long the recording is, and they align text against the audio
     * it was actually produced from, which is a better-conditioned problem than aligning a
     * concatenation against a concatenation.
     *
     * Words are assigned to the first chunk whose range contains them, so a word in an overlap
     * region is fused exactly once. A chunk with no smart text keeps its verbatim words.
     */
    internal fun fusePerChunk(
        verbatimWords: List<CanonicalWord>,
        chunks: List<AudioChunk>,
        smartByChunk: Map<Int, String>
    ): FusedTranscript {
        val fused = mutableListOf<CanonicalWord>()
        var coverageSum = 0f
        var coverageCount = 0
        var cursor = 0

        for (chunk in chunks) {
            val group = mutableListOf<CanonicalWord>()
            while (cursor < verbatimWords.size && verbatimWords[cursor].startMs < chunk.endMs) {
                group += verbatimWords[cursor]
                cursor++
            }
            if (group.isEmpty()) continue

            val smart = smartByChunk[chunk.index]
            if (smart == null) {
                fused += group
                continue
            }
            val chunkFusion = TranscriptFusionEngine.fuse(group, smart)
            coverageSum += chunkFusion.alignmentCoverage
            coverageCount++
            fused += chunkFusion.words
        }
        // Any trailing words past the last chunk's end (possible only if the plan and the word
        // stream disagree) are kept rather than dropped: losing real transcript to a bookkeeping
        // mismatch is far worse than a few unpolished words.
        while (cursor < verbatimWords.size) {
            fused += verbatimWords[cursor]
            cursor++
        }

        return FusedTranscript(
            words = fused.mapIndexed { index, word -> word.copy(id = "w$index") },
            alignmentCoverage = if (coverageCount == 0) 0f else coverageSum / coverageCount
        )
    }

    private companion object {
        /** Parts in flight at once: fast without tripping Gemini's per-minute limits on a free key. */
        const val CONCURRENCY = 2
        const val VERBATIM_SYSTEM_INSTRUCTION =
            "You are a verbatim transcription engine. Transcribe exactly what is said, including " +
                "false starts, repetitions and filler words. Do not summarise, correct, reorder or " +
                "paraphrase. Attribute every word to a speaker and give every word a start and end " +
                "time in milliseconds relative to the start of the supplied audio."

        const val VERBATIM_PROMPT =
            "Transcribe this audio verbatim with speaker diarization and word-level timestamps."

        const val SMART_SYSTEM_INSTRUCTION =
            "You are a transcript editor. Produce a readable transcript of this audio: correct " +
                "punctuation and casing, remove filler words and false starts, and resolve " +
                "self-corrections to what the speaker settled on. Never add information that was " +
                "not spoken, never reorder what was said, and never invent a speaker label or a " +
                "timestamp."

        const val SMART_PROMPT = "Produce a clean, readable transcript of this audio."
    }
}

/** Parses Gemini's structured transcription responses. Separated so it is testable without a network. */
object GeminiTranscriptParser {

    /**
     * The schema the verbatim pass must answer in. Requesting structured output rather than prose
     * is what makes this parseable at all: there is no regex over free text anywhere in the cloud
     * path.
     */
    const val VERBATIM_SCHEMA: String = """
    {
      "type": "object",
      "properties": {
        "words": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "text": { "type": "string" },
              "startMs": { "type": "integer" },
              "endMs": { "type": "integer" },
              "speaker": { "type": "string" }
            },
            "required": ["text", "startMs", "endMs"]
          }
        }
      },
      "required": ["words"]
    }
    """

    /**
     * @param chunkStartMs Added to every timestamp, because the model reports times relative to the
     *   audio it was given, not to the recording.
     * @return Words, or null when the response is not readable — never a partial guess.
     */
    fun parseVerbatim(json: String, chunkStartMs: Long): List<CanonicalWord>? = try {
        val root = org.json.JSONObject(json)
        if (root.has("turns")) parseTurns(root.getJSONArray("turns"), chunkStartMs)
        else parseWords(root.getJSONArray("words"), chunkStartMs)
    } catch (e: org.json.JSONException) {
        null
    }

    /**
     * The transcription model's answer: speaker turns, each with words timed as offsets like
     * `"1.250s"` from the start of the audio it was given.
     */
    private fun parseTurns(turns: org.json.JSONArray, chunkStartMs: Long): List<CanonicalWord> {
        val words = mutableListOf<CanonicalWord>()
        for (t in 0 until turns.length()) {
            val turn = turns.getJSONObject(t)
            val speaker = turn.optString("speakerLabel").ifBlank { turn.optString("speaker_label") }.ifBlank { null }
            val items = turn.optJSONArray("words") ?: continue
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val text = item.optString("word").ifBlank { item.optString("text") }.trim()
                if (text.isEmpty()) continue
                val start = offsetMs(item.opt("startOffset") ?: item.opt("start_offset")) ?: continue
                val end = offsetMs(item.opt("endOffset") ?: item.opt("end_offset")) ?: start
                words += CanonicalWord(
                    id = "c${chunkStartMs}_${words.size}",
                    text = text,
                    startMs = chunkStartMs + start,
                    endMs = chunkStartMs + maxOf(end, start),
                    speakerId = speaker,
                    attribution = if (speaker != null) AttributionConfidence.HIGH else AttributionConfidence.NONE,
                    source = TranscriptSource.GEMINI_VERBATIM
                )
            }
        }
        return words.sortedBy { it.startMs }.mapIndexed { i, w -> w.copy(id = "c${chunkStartMs}_$i") }
    }

    /** A protobuf Duration as JSON — `"1.250s"` — or a plain number of seconds. */
    internal fun offsetMs(value: Any?): Long? = when (value) {
        is Number -> (value.toDouble() * 1000).toLong()
        is String -> value.trim().removeSuffix("s").toDoubleOrNull()?.let { (it * 1000).toLong() }
        else -> null
    }

    private fun parseWords(array: org.json.JSONArray, chunkStartMs: Long): List<CanonicalWord> = run {
        (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            val text = item.optString("text").trim()
            if (text.isEmpty()) return@mapNotNull null
            val startMs = chunkStartMs + item.getLong("startMs")
            val endMs = chunkStartMs + item.getLong("endMs")
            val speaker = item.optString("speaker", "").ifEmpty { null }
            CanonicalWord(
                id = "c${chunkStartMs}_$index",
                text = text,
                startMs = startMs,
                endMs = maxOf(endMs, startMs),
                speakerId = speaker,
                // The model reported a speaker for this word; that is direct evidence, but it is
                // the model's own clustering rather than a measured acoustic overlap, so it is
                // recorded as HIGH only when a speaker was actually given.
                attribution = if (speaker != null) AttributionConfidence.HIGH else AttributionConfidence.NONE,
                source = TranscriptSource.GEMINI_VERBATIM
            )
        }
    }
}
