package com.craftflowtechnologies.meetingmind.ai.cloud

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.TimeUnit

private val Context.deepSeekStore by preferencesDataStore(name = "meetmind_deepseek")

/**
 * DeepSeek: the second writer for every AI text feature, used when Gemini can't answer.
 *
 * Three ways to reach it, best first: the MeetingMind proxy (`DEEPSEEK_PROXY_URL`), which holds
 * the key and enforces each install's allowance server-side; a key the person pasted in Settings;
 * and, for private builds only, a key compiled in (`SYSTEM_DEEPSEEK_API_KEY`). Public APKs carry
 * neither key — a compiled key can be pulled out of an APK in minutes.
 *
 * Every answer's tokens are counted per calendar month against [MONTHLY_TOKENS]; past it, DeepSeek
 * says so instead of answering. (On the phone the count guides; the proxy is what enforces.)
 */
class DeepSeek(private val context: Context, private val baseUrl: String = BASE_URL) {

    private val store get() = context.applicationContext.deepSeekStore

    val userKeyFlow: Flow<String?> = context.applicationContext.deepSeekStore.data.map { it[KEY]?.takeIf { k -> k.isNotBlank() } }

    suspend fun setKey(key: String) = store.edit { p -> key.trim().let { if (it.isEmpty()) p.remove(KEY) else p[KEY] = it } }

    suspend fun available(): Boolean = proxyUrl != null || userKeyFlow.first() != null || systemKey != null

    /** Tokens used this calendar month. */
    suspend fun usedThisMonth(): Long {
        val p = store.data.first()
        return if (p[USAGE_MONTH] == YearMonth.now().toString()) p[USAGE_TOKENS] ?: 0L else 0L
    }

    private suspend fun record(tokens: Long) = store.edit { p ->
        val month = YearMonth.now().toString()
        val used = if (p[USAGE_MONTH] == month) p[USAGE_TOKENS] ?: 0L else 0L
        p[USAGE_MONTH] = month
        p[USAGE_TOKENS] = used + tokens
    }

    private suspend fun installId(): String {
        store.data.first()[INSTALL]?.let { return it }
        val id = UUID.randomUUID().toString()
        store.edit { it[INSTALL] = id }
        return id
    }

    /** One chat completion. [json] asks for a JSON object back. */
    suspend fun complete(system: String, prompt: String, json: Boolean, timeoutMs: Long = 120_000L, maxTokens: Int = 4096): AiResult<String> = withContext(Dispatchers.IO) {
        GeminiLog.attach(context)
        if (usedThisMonth() >= MONTHLY_TOKENS) {
            return@withContext AiResult.Failed("This month's built-in AI allowance is used up. It resets on the 1st, or add your own Gemini key in Settings.")
        }
        val proxy = proxyUrl
        val key = userKeyFlow.first() ?: systemKey
        if (proxy == null && key == null) return@withContext AiResult.ModelUnavailable(MODEL, "DeepSeek isn't set up on this build.")

        val messages = JSONArray()
        if (system.isNotBlank()) messages.put(JSONObject().put("role", "system").put("content", system))
        // JSON mode needs the word "json" in the conversation; the app's prompts carry the schema.
        messages.put(JSONObject().put("role", "user").put("content", if (json && !prompt.contains("json", ignoreCase = true)) "$prompt\n\nReply in json." else prompt))
        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", messages)
            .put("max_tokens", maxTokens)
            .put("stream", false)
            .put("thinking", JSONObject().put("type", "disabled"))
        if (json) body.put("response_format", JSONObject().put("type", "json_object"))

        val request = Request.Builder()
            .url(if (proxy != null) "${proxy.trimEnd('/')}/chat/completions" else "$baseUrl/chat/completions")
            .apply {
                if (proxy != null) header("X-Install-Id", installId()) else header("Authorization", "Bearer $key")
            }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val started = System.currentTimeMillis()
        val client = com.craftflowtechnologies.meetingmind.core.net.Net.base.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
        try {
            client.newCall(request).execute().use { r ->
                val text = r.body?.string().orEmpty()
                val secs = "%.1fs".format((System.currentTimeMillis() - started) / 1000.0)
                if (!r.isSuccessful) {
                    val msg = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()?.takeIf { it.isNotBlank() } ?: text.take(200)
                    GeminiLog.add("DeepSeek → HTTP ${r.code} in $secs: $msg")
                    return@withContext AiResult.Failed(
                        when (r.code) {
                            401 -> "DeepSeek didn't accept its key."
                            402 -> "The DeepSeek account is out of credit."
                            429 -> if (msg.contains("allowance", true)) msg else "DeepSeek is busy right now. Try again shortly."
                            else -> "DeepSeek couldn't answer (HTTP ${r.code}). $msg".trim()
                        }
                    )
                }
                val o = JSONObject(text)
                val content = o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty().trim()
                val tokens = o.optJSONObject("usage")?.optLong("total_tokens") ?: ((system.length + prompt.length + content.length) / 4L)
                record(tokens)
                GeminiLog.add("DeepSeek → OK in $secs, $tokens tokens")
                if (content.isBlank()) AiResult.Failed("DeepSeek returned an empty answer.") else AiResult.Success(content)
            }
        } catch (e: java.io.IOException) {
            GeminiLog.add("DeepSeek → ${e.javaClass.simpleName}: ${e.message}")
            AiResult.Failed("Couldn't reach DeepSeek: ${e.message ?: "network error"}")
        }
    }

    companion object {
        const val MODEL = "deepseek-flash"
        const val BASE_URL = "https://api.deepseek.com"
        const val MONTHLY_TOKENS = 6_000_000L
        private val KEY = stringPreferencesKey("deepseek_key")
        private val USAGE_MONTH = stringPreferencesKey("usage_month")
        private val USAGE_TOKENS = longPreferencesKey("usage_tokens")
        private val INSTALL = stringPreferencesKey("install_id")
        val proxyUrl: String? = com.craftflowtechnologies.meetingmind.BuildConfig.DEEPSEEK_PROXY_URL.trim().takeIf { it.startsWith("https://") }
        val systemKey: String? = com.craftflowtechnologies.meetingmind.BuildConfig.SYSTEM_DEEPSEEK_API_KEY.trim().takeIf { it.isNotEmpty() }

        /** Which service wrote the most recent AI text, for the small "written by" line. */
        @Volatile var lastProvider: String? = null
    }
}

/**
 * The transport every AI feature uses. Audio (transcription, analysis of a recording) goes to
 * Gemini only. Text goes to Gemini when there's a key, and to DeepSeek when Gemini has no key or
 * fails for any reason — a timeout, quota, an outage — so writing features keep working.
 */
class FallbackTransport(
    private val gemini: GeminiTransport,
    private val deepSeek: DeepSeek
) : GeminiTransport {

    @Volatile private var geminiReady = false
    @Volatile private var deepSeekReady = false

    override fun isConfigured(): Boolean = geminiReady || deepSeekReady

    override suspend fun refreshConfigured(): Boolean {
        geminiReady = gemini.refreshConfigured()
        deepSeekReady = deepSeek.available()
        return geminiReady || deepSeekReady
    }

    override suspend fun execute(request: GeminiRequest): AiResult<String> {
        val audio = request.audioFile != null || request.transcription != null
        if (audio) return gemini.execute(request).also { if (it is AiResult.Success) DeepSeek.lastProvider = "Gemini" }
        refreshConfigured()
        var geminiError: String? = null
        if (geminiReady) {
            val first = gemini.execute(request)
            if (first is AiResult.Success) { DeepSeek.lastProvider = "Gemini"; return first }
            geminiError = (first as? AiResult.Failed)?.message ?: (first as? AiResult.ModelUnavailable)?.message
            if (!deepSeekReady) return first
            GeminiLog.add("Falling back to DeepSeek after Gemini: ${geminiError ?: "failed"}")
        }
        if (!deepSeekReady) return AiResult.ModelUnavailable(request.modelId, "No AI is set up. Add a Gemini key in Settings.")
        val second = deepSeek.complete(
            system = request.systemInstruction,
            prompt = buildString {
                append(request.prompt)
                request.responseSchema?.let { append("\n\nReply with a JSON object matching this JSON Schema, and nothing else:\n").append(it) }
                if (request.vocabularyHints.isNotEmpty()) append("\n\nTerms likely to appear: ").append(request.vocabularyHints.joinToString(", "))
            },
            json = request.responseSchema != null,
            timeoutMs = request.timeoutMs ?: 120_000L
        )
        if (second is AiResult.Success) DeepSeek.lastProvider = "DeepSeek"
        return if (second !is AiResult.Success && geminiError != null) AiResult.Failed("Gemini: $geminiError · DeepSeek: ${(second as? AiResult.Failed)?.message ?: "unavailable"}") else second
    }
}

/** The one place AI transports are made, so every feature gets the same fallback. */
object CloudAi {
    fun transport(context: Context): GeminiTransport =
        FallbackTransport(GeminiHttpTransport(GeminiCredentialStore(context.applicationContext)), DeepSeek(context.applicationContext))
}
