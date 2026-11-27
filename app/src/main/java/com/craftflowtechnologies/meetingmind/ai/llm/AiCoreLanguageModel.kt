package com.craftflowtechnologies.meetingmind.ai.llm

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Capability seam for on-device Android AICore / ML Kit GenAI Prompt API (Gemini Nano).
 *
 * Checks OS & AICore system package availability first, then probes whether the optional ML Kit
 * GenAI Prompt runtime (`com.google.mlkit.genai.prompt.Generation`) is linked into the build.
 * When unavailable, returns [AiResult.ModelUnavailable] so [LanguageModelFactory] falls cleanly
 * to the downloadable MediaPipe / LiteRT model in [com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelStorage].
 */
object AiCoreRuntime {
    private const val AICORE_PACKAGE = "com.google.android.aicore"
    private const val MLKIT_GENERATION_CLASS = "com.google.mlkit.genai.prompt.Generation"

    fun hasSystemService(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return runCatching {
            context.packageManager.getPackageInfo(AICORE_PACKAGE, PackageManager.GET_META_DATA)
            true
        }.getOrDefault(false)
    }

    fun hasPromptSdk(): Boolean = runCatching {
        Class.forName(MLKIT_GENERATION_CLASS)
        true
    }.getOrDefault(false)

    fun isReady(context: Context): Boolean = hasSystemService(context) && hasPromptSdk()
}

class AiCoreLanguageModel(
    private val context: Context
) : LanguageModel {

    override suspend fun generate(prompt: String, maxOutputTokens: Int): AiResult<String> {
        if (!AiCoreRuntime.hasSystemService(context)) {
            return AiResult.ModelUnavailable(MODEL_ID, "Android AICore is not available on this device.")
        }
        if (!AiCoreRuntime.hasPromptSdk()) {
            return AiResult.ModelUnavailable(MODEL_ID, "ML Kit GenAI Prompt runtime is not bundled in this build.")
        }
        return withContext(Dispatchers.Default) {
            runCatching {
                val genClass = Class.forName("com.google.mlkit.genai.prompt.Generation")
                val client = genClass.getMethod("getClient").invoke(null)
                    ?: return@runCatching AiResult.ModelUnavailable(MODEL_ID, "AICore client returned null.")
                val generateMethod = client.javaClass.methods.firstOrNull {
                    it.name == "generateContent" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
                } ?: return@runCatching AiResult.DeviceUnsupported("AICore Prompt API signature mismatch.")

                val taskOrFuture = generateMethod.invoke(client, prompt)
                val responseObj = awaitReflectiveResult(taskOrFuture)
                val text = extractText(responseObj)
                if (text.isNullOrBlank()) {
                    AiResult.Failed("AICore returned an empty response.")
                } else {
                    AiResult.Success(text)
                }
            }.getOrElse { err ->
                AiResult.Failed(err.message ?: "AICore inference failed.", err)
            }
        }
    }

    private suspend fun awaitReflectiveResult(resultHandle: Any?): Any? {
        if (resultHandle == null) return null
        // Supports java.util.concurrent.Future / CompletableFuture and Play Services Task via Tasks.await().
        if (resultHandle is java.util.concurrent.Future<*>) {
            return withContext(Dispatchers.IO) { resultHandle.get() }
        }
        val tasksClass = runCatching { Class.forName("com.google.android.gms.tasks.Tasks") }.getOrNull()
        val taskClass = runCatching { Class.forName("com.google.android.gms.tasks.Task") }.getOrNull()
        if (tasksClass != null && taskClass != null && taskClass.isInstance(resultHandle)) {
            return withContext(Dispatchers.IO) {
                tasksClass.getMethod("await", taskClass).invoke(null, resultHandle)
            }
        }
        return resultHandle
    }

    private fun extractText(response: Any?): String? {
        if (response == null) return null
        if (response is String) return response
        val candidates = runCatching { response.javaClass.getMethod("getCandidates").invoke(response) as? List<*> }.getOrNull()
        val first = candidates?.firstOrNull() ?: response
        return runCatching { first.javaClass.getMethod("getText").invoke(first) as? String }.getOrNull()
    }

    companion object {
        const val MODEL_ID = "gemini-nano-aicore"
        const val CONTEXT_TOKENS = 4096
    }
}
