package com.craftflowtechnologies.meetingmind.ai.modelmanagement

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the (at most one) loaded MediaPipe [LlmInference] engine for the whole process — a 1.5B
 * parameter model is a large native allocation that must not be reloaded per call or held as
 * multiple concurrent copies. Mirrors [SherpaEngineManager]'s reuse/mutex pattern, kept as a
 * separate object because the LLM runtime (MediaPipe) is entirely independent of the sherpa-onnx
 * runtime used for VAD/ASR/diarization — [com.craftflowtechnologies.meetingmind.ai.llm.LanguageModel] is the only thing
 * that depends on this class; nothing outside the `ai` package should ever import it.
 */
object LlmEngineManager {
    private val mutex = Mutex()

    private var engine: LlmInference? = null
    private var engineModelId: String? = null

    suspend fun getOrCreate(context: Context, modelId: String, modelPath: String, maxTokens: Int): LlmInference =
        mutex.withLock { createLocked(context, modelId, modelPath, maxTokens) }

    private fun createLocked(context: Context, modelId: String, modelPath: String, maxTokens: Int): LlmInference =
        run {
            val existing = engine
            if (existing != null && engineModelId == modelId) {
                return@run existing
            }
            // Forget the old engine before loading the new one, so a failed load never leaves a
            // closed engine behind for the next caller.
            runCatching { existing?.close() }
            engine = null
            engineModelId = null
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

    /** Set by [release] while a generation holds the engine; the user that finishes closes it. */
    @Volatile private var releasePending = false

    /**
     * Runs [block] with the engine held: one generation at a time, and [release] can't close the
     * native engine underneath it (closing mid-generation is a native crash no catch can stop).
     */
    suspend fun <T> withEngine(context: Context, modelId: String, modelPath: String, maxTokens: Int, block: (LlmInference) -> T): T =
        mutex.withLock {
            try {
                block(createLocked(context, modelId, modelPath, maxTokens))
            } finally {
                if (releasePending) closeLocked()
            }
        }

    /**
     * Releases the native engine. Safe to call even if nothing was ever loaded. If a generation is
     * running, the engine is closed as soon as it finishes instead of under it.
     */
    suspend fun release() {
        if (!mutex.tryLock()) {
            releasePending = true
            return
        }
        try {
            closeLocked()
        } finally {
            mutex.unlock()
        }
    }

    private fun closeLocked() {
        releasePending = false
        runCatching { engine?.close() }
        engine = null
        engineModelId = null
    }
}
