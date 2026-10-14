package com.craftflowtechnologies.meetingmind.ai.modelmanagement

import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
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

    private var diarizer: OfflineSpeakerDiarization? = null
    private var diarizerModelId: String? = null

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

    private val diarizerLock = Any()
    private var diarizerBusy = false

    /**
     * Checks out a diarizer for exactly one call. The segmentation/embedding models load once per
     * [modelId] and are reused (clustering is re-applied via [OfflineSpeakerDiarization.setConfig]).
     * A busy or detached instance is never handed out twice: if the shared one is mid-call, the
     * caller gets a private instance. Every checkout must be paired with [checkinDiarizer], which
     * releases any instance the manager no longer owns.
     */
    fun checkoutDiarizer(modelId: String, config: OfflineSpeakerDiarizationConfig): OfflineSpeakerDiarization =
        synchronized(diarizerLock) {
            val existing = diarizer
            if (existing != null && diarizerModelId == modelId && !diarizerBusy) {
                existing.setConfig(config)
                diarizerBusy = true
                return existing
            }
            if (existing != null && diarizerModelId == modelId) {
                // Shared instance is mid-call: use a private one rather than share a busy handle.
                return OfflineSpeakerDiarization(config = config)
            }
            existing?.let { if (!diarizerBusy) it.release() }
            val created = OfflineSpeakerDiarization(config = config)
            diarizer = created
            diarizerModelId = modelId
            diarizerBusy = true
            created
        }

    /** Ends a checkout. Releases [instance] unless it is still the manager's own, idle-again instance. */
    fun checkinDiarizer(instance: OfflineSpeakerDiarization) {
        val owned = synchronized(diarizerLock) {
            if (diarizer === instance) {
                diarizerBusy = false
                true
            } else false
        }
        if (!owned) runCatching { instance.release() }
    }

    /**
     * Stops owning [instance] because its native call is being abandoned (timeout). From here
     * [releaseAll] cannot free it mid-call and nobody can reuse it; the abandoned call frees it via
     * [checkinDiarizer] when it finally returns.
     */
    fun detachDiarizer(instance: OfflineSpeakerDiarization) {
        synchronized(diarizerLock) {
            if (diarizer === instance) {
                diarizer = null
                diarizerModelId = null
                diarizerBusy = false
            }
        }
    }

    /** Releases all native resources. Safe to call even if nothing was ever loaded. */
    suspend fun releaseAll() = mutex.withLock {
        recognizer?.release()
        recognizer = null
        recognizerModelId = null

        vad?.release()
        vad = null
        vadModelId = null

        synchronized(diarizerLock) {
            // A checked-out diarizer is mid-call: freeing it would crash the native code. Drop
            // ownership instead; its checkin releases it.
            if (!diarizerBusy) diarizer?.release()
            diarizer = null
            diarizerModelId = null
            diarizerBusy = false
        }
    }
}
