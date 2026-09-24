package com.example.ai.cloud

import android.util.Base64
import com.example.ai.common.AiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Media a model produced: decoded bytes and their type ("audio/wav", "image/png"). */
class GeneratedMedia(val bytes: ByteArray, val mimeType: String)

/**
 * `POST /v1beta/interactions` — the endpoint Gemini's speech (TTS) and image models answer on.
 * Like [GeminiHttpTransport], the key is read per request from [GeminiCredentialStore] and never
 * stored anywhere else.
 */
class GeminiInteractions(
    private val credentials: GeminiCredentialStore,
    private val client: OkHttpClient = mediaClient(),
    private val baseUrl: String = GeminiHttpTransport.DEFAULT_BASE_URL
) {
    suspend fun isConfigured(): Boolean = credentials.getApiKey() != null

    /** Sends [body] and returns the last media item of [type] ("audio" or "image") in the answer. */
    suspend fun media(body: JSONObject, type: String): AiResult<GeneratedMedia> = withContext(Dispatchers.IO) {
        val key = credentials.getApiKey() ?: return@withContext AiResult.ModelUnavailable(body.optString("model"), "No Gemini API key is set.")
        try {
            val request = Request.Builder()
                .url("$baseUrl/v1beta/interactions")
                .addHeader("x-goog-api-key", key)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
                    return@withContext AiResult.Failed("Gemini ${response.code}: ${message?.take(200) ?: "request failed"}")
                }
                val found = findMedia(JSONObject(text), type) ?: return@withContext AiResult.Failed("Gemini returned no $type.")
                AiResult.Success(GeneratedMedia(Base64.decode(found.first, Base64.DEFAULT), found.second))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AiResult.Failed(e.message ?: "Gemini request failed.", e)
        }
    }

    companion object {
        fun mediaClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        /** The last `{type, data, mime_type}` item of [type] in the response's model output steps. */
        fun findMedia(response: JSONObject, type: String): Pair<String, String>? {
            var found: Pair<String, String>? = null
            val steps = response.optJSONArray("steps") ?: response.optJSONArray("outputs")?.let { JSONArray().put(JSONObject().put("content", it)) } ?: return null
            for (i in 0 until steps.length()) {
                val step = steps.optJSONObject(i) ?: continue
                val content = step.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val item = content.optJSONObject(j) ?: continue
                    if (item.optString("type") == type && item.optString("data").isNotEmpty()) {
                        found = item.getString("data") to item.optString("mime_type", if (type == "audio") "audio/wav" else "image/png")
                    }
                }
            }
            return found
        }

        /** A single-speaker speech request (Gemini TTS). */
        fun speechRequest(model: String, text: String, style: String, voice: String): JSONObject = JSONObject()
            .put("model", model)
            .put("input", JSONArray().put(JSONObject()
                .put("type", "user_input")
                .put("content", JSONArray().put(JSONObject()
                    .put("type", "text")
                    .put("text", text)
                    .put("annotations", JSONArray().put(JSONObject().put("type", "speech_metadata").put("style", style)))))))
            .put("response_format", JSONObject().put("type", "audio").put("mime_type", "audio/wav").put("sample_rate", 24000))
            .put("generation_config", JSONObject().put("speech_config", JSONArray().put(JSONObject().put("voice", voice))))

        /** An image request (Gemini image models). */
        fun imageRequest(model: String, prompt: String, aspectRatio: String = "9:16", size: String = "1K"): JSONObject = JSONObject()
            .put("model", model)
            .put("input", JSONArray().put(JSONObject().put("type", "text").put("text", prompt)))
            .put("response_format", JSONObject().put("type", "image").put("mime_type", "image/png").put("aspect_ratio", aspectRatio).put("image_size", size))
    }
}
