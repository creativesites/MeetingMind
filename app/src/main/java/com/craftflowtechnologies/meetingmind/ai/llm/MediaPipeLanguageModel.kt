package com.craftflowtechnologies.meetingmind.ai.llm

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LlmEngineManager
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real on-device [LanguageModel] backed by MediaPipe's LlmInference API
 * (`com.google.mediapipe:tasks-genai`), running the downloaded-on-demand Qwen2.5-1.5B-Instruct
 * `.task` model (see [ModelCatalog.qwen25_1_5bInstruct]). Never makes a network call — inference
 * is entirely local. Checks install state before touching any MediaPipe class, matching the same
 * testability pattern as [com.craftflowtechnologies.meetingmind.ai.vad.SileroVadDetector] /
 * [com.craftflowtechnologies.meetingmind.ai.asr.SherpaParakeetSpeechRecognizer] / [com.craftflowtechnologies.meetingmind.ai.diarization.SherpaSpeakerDiarizer].
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

        val totalRam = LocalModelFit.totalRamBytes(context)
        if (!LocalModelFit.fits(modelFile.length(), totalRam)) {
            return AiResult.InsufficientMemory(requiredMb = manifestEntry.recommendedRamMb, availableMb = (totalRam / (1024 * 1024)).toInt())
        }

        return try {
            withContext(Dispatchers.Default) {
                // Held for the whole generation, so a release() elsewhere can't close it underneath.
                val response = LlmEngineManager.withEngine(context.applicationContext, modelId, modelFile.absolutePath, contextTokens) { engine ->
                    engine.generateResponse(prompt)
                }
                AiResult.Success(response)
            }
        } catch (e: OutOfMemoryError) {
            AiResult.InsufficientMemory(requiredMb = manifestEntry.recommendedRamMb, availableMb = 0)
        } catch (e: LinkageError) {
            // The engine's native library isn't there for this phone's processor (e.g. a 32-bit build).
            AiResult.ModelUnavailable(modelId, "This phone can't run the on-device model.")
        } catch (e: Exception) {
            AiResult.Failed(e.message ?: "Local language model inference failed.", e)
        }
    }

    private companion object {
        const val MIN_CONTEXT_TOKENS = 1024
    }
}
