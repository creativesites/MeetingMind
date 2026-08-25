package com.example.ai.diarization

import android.util.Log
import com.example.ai.common.AiResult
import com.example.ai.modelmanagement.ModelCatalog
import com.example.ai.modelmanagement.ModelStorage
import com.example.ai.modelmanagement.SherpaEngineManager
import com.example.core.audio.AudioFormatConverter
import com.example.core.model.Speaker
import com.example.core.model.TranscriptSegment
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File

/**
 * Real offline speaker diarization via sherpa-onnx's [com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization]:
 * pyannote segmentation-3.0 finds speaker-change boundaries, a 3D-Speaker CAM++ embedding model
 * turns each resulting segment into a vector, and sherpa-onnx's built-in clustering groups those
 * vectors into speakers — either automatically (`expectedSpeakerCount == null`) or forced to an
 * exact count. The raw (start, end, speakerIndex) segments this produces are then reconciled
 * against the real ASR transcript segments by timestamp overlap (see
 * [reconcileTranscriptWithSpeakers]) — never by turn order or any other heuristic.
 */
class SherpaSpeakerDiarizer(
    private val modelStorage: ModelStorage,
    private val modelId: String = ModelCatalog.speakerDiarization.id
) : SpeakerDiarizer {

    override suspend fun diarize(
        audioFile: File,
        totalDurationMs: Long,
        segments: List<TranscriptSegment>,
        knownSpeakers: List<Speaker>,
        expectedSpeakerCount: Int?
    ): AiResult<List<TranscriptSegment>> {
        if (segments.isEmpty()) return AiResult.Success(segments)
        if (!modelStorage.isInstalled(modelId)) {
            return AiResult.ModelUnavailable(modelId, "No local speaker diarization model is installed on this device.")
        }

        val modelDir = modelStorage.getModelDirectory(modelId)
        val segmentationFile = File(modelDir, SEGMENTATION_MODEL_FILE_NAME)
        val embeddingFile = File(modelDir, EMBEDDING_MODEL_FILE_NAME)
        if (!segmentationFile.exists() || !embeddingFile.exists()) {
            return AiResult.ModelUnavailable(modelId, "Speaker diarization model files are missing from local storage.")
        }

        return try {
            val decoded = AudioFormatConverter.decodeToMono16k(audioFile)
            if (decoded.samples.isEmpty()) {
                return AiResult.Success(segments)
            }

            val config = OfflineSpeakerDiarizationConfig(
                segmentation = OfflineSpeakerSegmentationModelConfig(
                    pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(model = segmentationFile.absolutePath),
                    numThreads = NUM_THREADS,
                    provider = "cpu"
                ),
                embedding = SpeakerEmbeddingExtractorConfig(
                    model = embeddingFile.absolutePath,
                    numThreads = NUM_THREADS,
                    provider = "cpu"
                ),
                clustering = buildClusteringConfig(expectedSpeakerCount),
                minDurationOn = MIN_DURATION_ON_SEC,
                minDurationOff = MIN_DURATION_OFF_SEC
            )

            val diarizer = SherpaEngineManager.getOrCreateDiarizer(modelId, config)
            // The segmentation/embedding models stay loaded across calls; only clustering (the
            // one thing that legitimately varies per meeting, e.g. a user-picked speaker count)
            // needs re-applying on a reused instance — the sherpa-onnx API documents setConfig()
            // as reading only config.clustering, never reloading the underlying models.
            diarizer.setConfig(config)

            val rawSegments = diarizer.process(decoded.samples).map {
                RawSpeakerSegment(
                    startMs = (it.start * 1000f).toLong(),
                    endMs = (it.end * 1000f).toLong(),
                    speakerIndex = it.speaker
                )
            }
            // Sub-second segments sandwiched between two same-speaker segments are almost always
            // segmentation/clustering noise (a breath, overlap bleed, a misclassified word) rather
            // than a real third speaker — see mergeShortSandwichedFragments doc for the reasoning.
            val sandwichMerged = mergeShortSandwichedFragments(rawSegments)

            // A second, broader pass: a handful of speaker indices that barely speak at all next
            // to one or two that dominate the recording is the classic fingerprint of clustering
            // noise rather than real extra participants — see reconcileFragmentedSpeakers's doc.
            // Only reassigns segments that were flagged as noise-like; genuinely balanced
            // multi-speaker output is never touched.
            val fragmentationAnalysis = analyzeSpeakerFragmentation(sandwichMerged)
            if (fragmentationAnalysis.isNotEmpty()) {
                Log.d(
                    DIARIZATION_QUALITY_TAG,
                    "Suspicious fragmentation detected — reassigning noise-like speaker indices $fragmentationAnalysis to their nearest real speaker"
                )
            }
            val reconciledSegments = reconcileFragmentedSpeakers(sandwichMerged, fragmentationAnalysis)

            AiResult.Success(reconcileTranscriptWithSpeakers(segments, reconciledSegments))
        } catch (e: Exception) {
            AiResult.Failed(e.message ?: "Speaker diarization failed.", e)
        }
    }

    private companion object {
        const val DIARIZATION_QUALITY_TAG = "MeetMindDiarizationQuality"
        const val SEGMENTATION_MODEL_FILE_NAME = "segmentation.onnx"
        const val EMBEDDING_MODEL_FILE_NAME = "embedding.onnx"
        const val NUM_THREADS = 2
        // Segmentation-stage minimum duration (seconds) for a detected speech region to be kept
        // at all. Raised from the library default (0.2s) to filter out sub-200ms blips — breaths,
        // clicks, VAD edge artifacts — before they ever reach clustering and get misread as a
        // distinct speaker turn.
        const val MIN_DURATION_ON_SEC = 0.35f
        const val MIN_DURATION_OFF_SEC = 0.5f
    }
}

// sherpa-onnx's FastClustering cuts its dendrogram at this cosine-dissimilarity distance
// (1 - cosine_similarity) when no fixed speaker count is given: a HIGHER threshold merges more
// aggressively (fewer, larger clusters), a lower one keeps more clusters apart. The library's own
// default (0.5, used verbatim here before this change) was observed on real two-person recordings
// splitting one speaker into as many as six clusters — every small acoustic wobble (breath, room
// noise, a word ASR barely picked up) was similar enough to itself but not similar enough to the
// rest of that speaker's segments at a 0.5 cutoff to stay merged. Raising it well above the
// default trades a small risk of merging two genuinely-similar-sounding speakers for the much
// larger, already-observed risk of shredding one real speaker into several. Only applies in Auto
// mode — a fixed speaker count bypasses threshold-based cutting entirely in favor of cutting to an
// exact cluster count (see [buildClusteringConfig]). Top-level (not on the class) so it can be
// referenced from the free function below without a companion-object visibility dance.
internal const val DIARIZATION_CLUSTERING_THRESHOLD = 0.72f

/**
 * Resolves the clustering knobs for one diarization run. A positive [expectedSpeakerCount] tells
 * sherpa-onnx's FastClustering to cut its dendrogram to exactly that many clusters (`numClusters`
 * takes priority over `threshold` in the native implementation whenever it is > 0); anything else
 * (null, zero, negative — i.e. "Auto") falls back to threshold-based cutting via
 * [DIARIZATION_CLUSTERING_THRESHOLD], which is tuned conservative on purpose (see that constant's
 * doc). Extracted as a pure function purely so this resolution logic is unit-testable without
 * touching any native sherpa-onnx class.
 */
internal fun buildClusteringConfig(expectedSpeakerCount: Int?): FastClusteringConfig =
    FastClusteringConfig(
        numClusters = expectedSpeakerCount?.takeIf { it > 0 } ?: -1,
        threshold = DIARIZATION_CLUSTERING_THRESHOLD
    )

/**
 * Reassigns a short, isolated raw speaker segment to the speaker on both sides of it when that
 * surrounding speaker is the same on both sides — e.g. Speaker 1 talks, a 300ms fragment gets
 * clustered as "Speaker 3", then Speaker 1 resumes: that 300ms fragment is far more likely to be
 * segmentation/clustering noise (a breath, overlap bleed, a word the embedding model heard
 * differently) than a real third speaker who appears for a third of a second and never again.
 *
 * This never merges two distinct, sustained speakers into one — it only relabels a fragment that
 * is both short ([minFragmentDurationMs]) AND bordered by the identical speaker index on both
 * sides, which is the one case where "this is noise, not a new voice" can be asserted from timing
 * alone without guessing at speaker identity. The first and last segments in the recording have no
 * "both sides" to check and are always left untouched — preserving uncertainty rather than
 * fabricating a merge with nothing to compare against.
 */
internal fun mergeShortSandwichedFragments(
    segments: List<RawSpeakerSegment>,
    minFragmentDurationMs: Long = 700L
): List<RawSpeakerSegment> {
    if (segments.size < 3) return segments
    val sorted = segments.sortedBy { it.startMs }
    return sorted.mapIndexed { index, segment ->
        if (index == 0 || index == sorted.lastIndex) return@mapIndexed segment
        val durationMs = segment.endMs - segment.startMs
        if (durationMs > minFragmentDurationMs) return@mapIndexed segment
        val prev = sorted[index - 1]
        val next = sorted[index + 1]
        if (prev.speakerIndex == next.speakerIndex && prev.speakerIndex != segment.speakerIndex) {
            segment.copy(speakerIndex = prev.speakerIndex)
        } else {
            segment
        }
    }
}

/** A raw (start, end, speaker-index) interval as produced by the diarization engine — decoupled from the sherpa-onnx type so the reconciliation logic below is unit-testable without loading any native library. */
internal data class RawSpeakerSegment(val startMs: Long, val endMs: Long, val speakerIndex: Int)

/** A speaker index's aggregate footprint across the recording — the raw material the
 * fragmentation-quality check reasons about instead of trusting cluster count alone. */
internal data class SpeakerFootprint(val speakerIndex: Int, val totalDurationMs: Long, val turnCount: Int)

// A speaker index responsible for less than this share of total real speaking time is a
// candidate for "noise-like" — not yet a verdict on its own (see MIN_NOISE_SPEAKERS_TO_FLAG and
// MAX_NOISE_SHARE_WHEN_FLAGGING below, both of which must also hold).
internal const val NOISE_SHARE_THRESHOLD = 0.08

// Flagging requires at least this many distinct noise-like speaker indices — a single minor
// speaker is exactly the kind of real (if brief) participant diarization is supposed to catch,
// not something to second-guess. Two or more near-silent "speakers" appearing together is the
// actual fragmentation fingerprint.
internal const val MIN_NOISE_SPEAKERS_TO_FLAG = 2

// ...and even then, only when those noise-like speakers together still account for a small
// slice of the recording. A wider spread of many real participants can absolutely occur (the
// share threshold alone would false-positive there); this second gate protects that case.
internal const val MAX_NOISE_SHARE_WHEN_FLAGGING = 0.15

/**
 * Flags speaker indices that look like clustering/segmentation noise rather than real
 * participants: several speaker indices that together barely speak (see the threshold constants
 * above), sitting alongside one or more indices that account for the great majority of the
 * recording. This is the "Speaker 1: 42%, Speaker 2: 3%, Speaker 3: 2%, Speaker 4: 1%, Speaker 5:
 * 52%" pattern — plausible-looking cluster output that is actually one or two real speakers
 * shredded by acoustic noise (breaths, room echo, a misheard word) into extra phantom speakers.
 *
 * Deliberately conservative: a recording with several genuinely balanced speakers, or with only
 * one small-but-real speaker, is never flagged — both of [MIN_NOISE_SPEAKERS_TO_FLAG] and
 * [MAX_NOISE_SHARE_WHEN_FLAGGING] must hold before anything is touched. Returns the empty set
 * (never anything flagged) when the input is empty, so callers can treat "no result" and "no
 * fragmentation found" identically.
 */
internal fun analyzeSpeakerFragmentation(segments: List<RawSpeakerSegment>): Set<Int> {
    if (segments.isEmpty()) return emptySet()
    val totalDurationMs = segments.sumOf { it.endMs - it.startMs }
    if (totalDurationMs <= 0L) return emptySet()

    val footprints = segments.groupBy { it.speakerIndex }.map { (index, segs) ->
        SpeakerFootprint(index, segs.sumOf { it.endMs - it.startMs }, segs.size)
    }
    // Only one speaker index exists at all — nothing to distinguish "noise" from "the speaker".
    if (footprints.size < 2) return emptySet()

    val noiseLike = footprints.filter { it.totalDurationMs.toDouble() / totalDurationMs < NOISE_SHARE_THRESHOLD }
    if (noiseLike.size < MIN_NOISE_SPEAKERS_TO_FLAG) return emptySet()
    if (noiseLike.size == footprints.size) return emptySet() // Every speaker is "noise" — degenerate, not a real verdict.

    val noiseShare = noiseLike.sumOf { it.totalDurationMs }.toDouble() / totalDurationMs
    return if (noiseShare < MAX_NOISE_SHARE_WHEN_FLAGGING) {
        noiseLike.map { it.speakerIndex }.toSet()
    } else {
        emptySet()
    }
}

/**
 * Reassigns every segment whose speaker index was flagged by [analyzeSpeakerFragmentation] to
 * whichever non-flagged ("real") speaker is temporally nearest to it — never to an arbitrary or
 * majority speaker, since the nearest real speaker in time is the one a noise blip most plausibly
 * actually belongs to. A no-op when nothing was flagged. If every speaker was somehow flagged
 * (analyzeSpeakerFragmentation already refuses to return that case, but this stays defensive
 * against a future change to that function), the input is returned unchanged rather than
 * guessing at a fallback.
 */
internal fun reconcileFragmentedSpeakers(
    segments: List<RawSpeakerSegment>,
    flaggedSpeakerIndices: Set<Int>
): List<RawSpeakerSegment> {
    if (flaggedSpeakerIndices.isEmpty()) return segments
    val sorted = segments.sortedBy { it.startMs }
    val real = sorted.filter { it.speakerIndex !in flaggedSpeakerIndices }
    if (real.isEmpty()) return segments

    return sorted.map { seg ->
        if (seg.speakerIndex !in flaggedSpeakerIndices) return@map seg
        val segMidMs = (seg.startMs + seg.endMs) / 2
        val nearest = real.minBy { kotlin.math.abs((it.startMs + it.endMs) / 2 - segMidMs) }
        seg.copy(speakerIndex = nearest.speakerIndex)
    }
}

/**
 * Assigns each ASR transcript segment the speaker whose raw diarization interval overlaps it the
 * most, by real timestamp overlap — never by alternating turns, position, or any other guess. A
 * segment with no overlapping speaker interval is left exactly as it was (its speakerId stays
 * whatever it already was, typically null) rather than being assigned a fabricated guess.
 */
internal fun reconcileTranscriptWithSpeakers(
    asrSegments: List<TranscriptSegment>,
    speakerSegments: List<RawSpeakerSegment>
): List<TranscriptSegment> {
    if (speakerSegments.isEmpty()) return asrSegments
    return asrSegments.map { seg ->
        var bestSpeakerIndex: Int? = null
        var bestOverlapMs = 0L
        for (spk in speakerSegments) {
            val overlapMs = minOf(seg.endMs, spk.endMs) - maxOf(seg.startMs, spk.startMs)
            if (overlapMs > bestOverlapMs) {
                bestOverlapMs = overlapMs
                bestSpeakerIndex = spk.speakerIndex
            }
        }
        val speakerIndex = bestSpeakerIndex ?: return@map seg
        seg.copy(
            speakerId = "spk_${seg.meetingId}_$speakerIndex",
            // "Speaker N" is the honest, generic default the product spec requires — never a
            // guessed real name. The user can rename it later without disturbing speakerId.
            speakerName = "Speaker ${speakerIndex + 1}"
        )
    }
}
