package com.example.ai.diarization

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
                clustering = FastClusteringConfig(
                    numClusters = expectedSpeakerCount?.takeIf { it > 0 } ?: -1,
                    threshold = CLUSTERING_THRESHOLD
                ),
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

            AiResult.Success(reconcileTranscriptWithSpeakers(segments, rawSegments))
        } catch (e: Exception) {
            AiResult.Failed(e.message ?: "Speaker diarization failed.", e)
        }
    }

    private companion object {
        const val SEGMENTATION_MODEL_FILE_NAME = "segmentation.onnx"
        const val EMBEDDING_MODEL_FILE_NAME = "embedding.onnx"
        const val NUM_THREADS = 2
        const val CLUSTERING_THRESHOLD = 0.5f
        const val MIN_DURATION_ON_SEC = 0.2f
        const val MIN_DURATION_OFF_SEC = 0.5f
    }
}

/** A raw (start, end, speaker-index) interval as produced by the diarization engine — decoupled from the sherpa-onnx type so the reconciliation logic below is unit-testable without loading any native library. */
internal data class RawSpeakerSegment(val startMs: Long, val endMs: Long, val speakerIndex: Int)

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
