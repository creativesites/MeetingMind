package com.example.ai.modelmanagement

import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the (at most one) loaded sherpa-onnx [OfflineRecognizer] and [Vad] instance for the
 * whole process. Loading the Parakeet TDT encoder alone is a ~650MB native allocation, so this
 * app must never load more than one copy of it concurrently, and must not reload it for every
 * VAD-detected speech segment within a single meeting — only once per model, reused across
 * every stream/segment until explicitly released.
 *
 * A [Mutex] serializes both loading and access: sherpa-onnx's native objects are not documented
 * as safe for concurrent use from multiple threads, and this also naturally prevents two
 * concurrent transcription jobs from each trying to load their own copy of the model.
 */
object SherpaEngineManager {
    private val mutex = Mutex()

    private var recognizer: OfflineRecognizer? = null
    private var recognizerModelId: String? = null

    private var vad: Vad? = null
    private var vadModelId: String? = null

    suspend fun getOrCreateRecognizer(modelId: String, config: OfflineRecognizerConfig): OfflineRecognizer =
        mutex.withLock {
            val existing = recognizer
            if (existing != null && recognizerModelId == modelId) {
                return@withLock existing
            }
            existing?.release()
            val created = OfflineRecognizer(config = config)
            recognizer = created
            recognizerModelId = modelId
            created
        }

    suspend fun getOrCreateVad(modelId: String, config: VadModelConfig): Vad =
        mutex.withLock {
            val existing = vad
            if (existing != null && vadModelId == modelId) {
                return@withLock existing
            }
            existing?.release()
            val created = Vad(config = config)
            vad = created
            vadModelId = modelId
            created
        }

    /** Releases all native resources. Safe to call even if nothing was ever loaded. */
    suspend fun releaseAll() = mutex.withLock {
        recognizer?.release()
        recognizer = null
        recognizerModelId = null

        vad?.release()
        vad = null
        vadModelId = null
    }
}
