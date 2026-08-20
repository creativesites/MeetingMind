package com.example.ai.llm

import android.content.Context
import com.example.ai.common.AiResult
import com.example.ai.modelmanagement.LlmEngineManager
import com.example.ai.modelmanagement.ModelCatalog
import com.example.ai.modelmanagement.ModelStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real on-device [LanguageModel] backed by MediaPipe's LlmInference API
 * (`com.google.mediapipe:tasks-genai`), running the downloaded-on-demand Qwen2.5-1.5B-Instruct
 * `.task` model (see [ModelCatalog.qwen25_1_5bInstruct]). Never makes a network call — inference
 * is entirely local. Checks install state before touching any MediaPipe class, matching the same
 * testability pattern as [com.example.ai.vad.SileroVadDetector] /
 * [com.example.ai.asr.SherpaParakeetSpeechRecognizer] / [com.example.ai.diarization.SherpaSpeakerDiarizer].
 */
class MediaPipeLanguageModel(
    private val context: Context,
    private val modelStorage: ModelStorage,
    private val modelId: String = ModelCatalog.qwen25_1_5bInstruct.id
) : LanguageModel {

    override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
        if (!modelStorage.isInstalled(modelId)) {
            return AiResult.ModelUnavailable(modelId, "No local language model is installed on this device.")
        }
        val manifestEntry = ModelCatalog.entries.find { it.id == modelId }
            ?: return AiResult.ModelUnavailable(modelId, "Unknown local language model id: $modelId")
        val modelFileName = manifestEntry.files.firstOrNull()?.fileName
            ?: return AiResult.ModelUnavailable(modelId, "Local language model manifest has no file entry.")
        val modelFile = File(modelStorage.getModelDirectory(modelId), modelFileName)
        if (!modelFile.exists()) {
            return AiResult.ModelUnavailable(modelId, "Local language model file is missing from local storage.")
        }
        // The engine's total token budget (prompt + generated) is fixed at model-load time to
        // this specific build's real context length — maxOutputTokens is a caller hint, not an
        // independently enforceable per-call limit on this runtime.
        val contextTokens = manifestEntry.contextLengthTokens ?: maxOutputTokens.coerceAtLeast(MIN_CONTEXT_TOKENS)

        return try {
            withContext(Dispatchers.Default) {
                val engine = LlmEngineManager.getOrCreate(context.applicationContext, modelId, modelFile.absolutePath, contextTokens)
                val response = engine.generateResponse(prompt)
                AiResult.Success(response)
            }
        } catch (e: OutOfMemoryError) {
            AiResult.InsufficientMemory(requiredMb = manifestEntry.recommendedRamMb, availableMb = 0)
        } catch (e: Exception) {
            AiResult.Failed(e.message ?: "Local language model inference failed.", e)
        }
    }

    private companion object {
        const val MIN_CONTEXT_TOKENS = 1024
    }
}
