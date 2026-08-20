package com.example.ai.modelmanagement

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the (at most one) loaded MediaPipe [LlmInference] engine for the whole process — a 1.5B
 * parameter model is a large native allocation that must not be reloaded per call or held as
 * multiple concurrent copies. Mirrors [SherpaEngineManager]'s reuse/mutex pattern, kept as a
 * separate object because the LLM runtime (MediaPipe) is entirely independent of the sherpa-onnx
 * runtime used for VAD/ASR/diarization — [com.example.ai.llm.LanguageModel] is the only thing
 * that depends on this class; nothing outside the `ai` package should ever import it.
 */
object LlmEngineManager {
    private val mutex = Mutex()

    private var engine: LlmInference? = null
    private var engineModelId: String? = null

    suspend fun getOrCreate(context: Context, modelId: String, modelPath: String, maxTokens: Int): LlmInference =
        mutex.withLock {
            val existing = engine
            if (existing != null && engineModelId == modelId) {
                return@withLock existing
            }
            existing?.close()
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(maxTokens)
                // CPU is the safe, universally-supported backend across "ordinary Android
                // phones" (GPU delegate support varies a lot by device/driver); predictable
                // behavior here matters more than raw speed for a background meeting-processing
                // step. Revisit if device-tier-aware backend selection becomes worthwhile.
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            val created = LlmInference.createFromOptions(context.applicationContext, options)
            engine = created
            engineModelId = modelId
            created
        }

    /** Releases the native engine. Safe to call even if nothing was ever loaded. */
    suspend fun release() = mutex.withLock {
        engine?.close()
        engine = null
        engineModelId = null
    }
}
