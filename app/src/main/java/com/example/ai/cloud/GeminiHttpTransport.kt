package com.example.ai.cloud

import android.util.Log
import com.example.ai.common.AiResult
import com.example.core.audio.AudioFormatConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * The real Gemini transport: audio uploaded through the Files API, then `generateContent`.
 *
 * Audio does not go inline. A meeting chunk is megabytes of PCM, and inlining it means holding the
 * whole thing in memory as base64 — roughly a third larger again — for every request. The Files
 * API takes a streamed upload from disk and hands back a URI that both passes of the transcription
 * engine can reference, so one chunk is uploaded once and read twice.
 *
 * ### Credential
 *
 * Read per-request from [GeminiCredentialStore], not captured at construction, so a key the user
 * enters (or clears) takes effect immediately and a cleared key cannot be used by a transport
 * instance that happens to still be alive. No key in the repository, the build or the APK — see
 * [GeminiCredentialStore]'s own note.
 */
class GeminiHttpTransport(
    private val credentials: GeminiCredentialStore,
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = DEFAULT_BASE_URL
) : GeminiTransport {

    /**
     * Whether a key has been entered. Cached from the last read so the UI can ask this cheaply and
     * synchronously; [execute] always re-reads the real value rather than trusting it.
     */
    @Volatile
    private var lastKnownConfigured: Boolean = false

    override fun isConfigured(): Boolean = lastKnownConfigured

    /** Refreshes [isConfigured] from storage. */
    override suspend fun refreshConfigured(): Boolean {
        lastKnownConfigured = credentials.getApiKey() != null
        return lastKnownConfigured
    }

    override suspend fun execute(request: GeminiRequest): AiResult<String> = withContext(Dispatchers.IO) {
        val apiKey = credentials.getApiKey()
        if (apiKey == null) {
            lastKnownConfigured = false
            return@withContext AiResult.ModelUnavailable(
                request.modelId,
                "No Gemini API key is set. Add one in Settings to use Internet mode."
            )
        }
        lastKnownConfigured = true

        try {
            val uploadedFileUri = request.audioFile?.let { file ->
                coroutineContext.ensureActive()
                when (val upload = uploadAudioSlice(apiKey, file, request.audioStartMs, request.audioEndMs)) {
                    is AiResult.Success -> upload.value
                    else -> return@withContext upload as AiResult<String>
                }
            }

            coroutineContext.ensureActive()
            generateContent(apiKey, request, uploadedFileUri)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AiResult.Failed(e.message ?: "Gemini request failed.", e)
        }
    }

    // ── Files API ────────────────────────────────────────────────────────────────────────────

    /**
     * Extracts the requested slice of audio, writes it as a 16 kHz mono WAV, and uploads it.
     *
     * A WAV is written rather than re-encoding because the audio is already decoded to PCM by
     * [AudioFormatConverter] for the local pipeline, the header is forty-four bytes of arithmetic,
     * and the alternative — handing Gemini the original container and asking it to look at one
     * time range — is not something the API offers. The temporary file is always deleted.
     */
    private suspend fun uploadAudioSlice(
        apiKey: String,
        audioFile: File,
        startMs: Long,
        endMs: Long
    ): AiResult<String> {
        val decoded = AudioFormatConverter.decodeToMono16k(audioFile)
        if (decoded.samples.isEmpty()) return AiResult.Failed("The recording contains no audio to upload.")

        val sampleRate = decoded.sampleRate
        val fromSample = (startMs * sampleRate / 1000L).toInt().coerceIn(0, decoded.samples.size)
        val toSample = if (endMs > startMs) {
            (endMs * sampleRate / 1000L).toInt().coerceIn(fromSample, decoded.samples.size)
        } else {
            decoded.samples.size
        }
        if (toSample <= fromSample) return AiResult.Failed("The requested audio range is empty.")

        val wav = File.createTempFile("meetmind_chunk_", ".wav")
        return try {
            writeWav(wav, decoded.samples, fromSample, toSample, sampleRate)
            uploadFile(apiKey, wav)
        } finally {
            wav.delete()
        }
    }

    private suspend fun uploadFile(apiKey: String, file: File): AiResult<String> {
        val start = Request.Builder()
            .url("$baseUrl/upload/v1beta/files?key=$apiKey")
            .addHeader("X-Goog-Upload-Protocol", "resumable")
            .addHeader("X-Goog-Upload-Command", "start")
            .addHeader("X-Goog-Upload-Header-Content-Length", file.length().toString())
            .addHeader("X-Goog-Upload-Header-Content-Type", AUDIO_MIME)
            .post(
                JSONObject().put("file", JSONObject().put("display_name", "meetmind_audio"))
                    .toString().toRequestBody(JSON_MIME)
            )
            .build()

        val uploadUrl = client.newCall(start).execute().use { response ->
            if (!response.isSuccessful) {
                return describeHttpFailure("Uploading audio", response.code, response.body?.string())
            }
            response.header("X-Goog-Upload-URL")
        } ?: return AiResult.Failed("Gemini did not return an upload URL.")

        val upload = Request.Builder()
            .url(uploadUrl)
            .addHeader("X-Goog-Upload-Offset", "0")
            .addHeader("X-Goog-Upload-Command", "upload, finalize")
            .post(file.asRequestBody(AUDIO_MIME.toMediaType()))
            .build()

        val fileJson = client.newCall(upload).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return describeHttpFailure("Uploading audio", response.code, body)
            JSONObject(body).optJSONObject("file")
        } ?: return AiResult.Failed("Gemini's upload response was unreadable.")

        val uri = fileJson.optString("uri").takeIf { it.isNotBlank() }
            ?: return AiResult.Failed("Gemini's upload response contained no file URI.")

        // An uploaded file is PROCESSING before it can be referenced; using it too early fails
        // with an opaque error, so wait for it rather than letting that surface to the user.
        return when (val ready = awaitFileActive(apiKey, fileJson.optString("name"))) {
            is AiResult.Success -> AiResult.Success(uri)
            else -> ready as AiResult<String>
        }
    }

    private suspend fun awaitFileActive(apiKey: String, name: String): AiResult<Unit> {
        if (name.isBlank()) return AiResult.Success(Unit)
        repeat(FILE_READY_ATTEMPTS) { attempt ->
            coroutineContext.ensureActive()
            val request = Request.Builder().url("$baseUrl/v1beta/$name?key=$apiKey").get().build()
            val state = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                JSONObject(response.body?.string().orEmpty()).optString("state")
            }
            when (state) {
                "ACTIVE" -> return AiResult.Success(Unit)
                "FAILED" -> return AiResult.Failed("Gemini could not process the uploaded audio.")
            }
            kotlinx.coroutines.delay(FILE_READY_POLL_MS * (attempt + 1))
        }
        return AiResult.Failed("Gemini did not finish processing the uploaded audio in time.")
    }

    // ── generateContent ──────────────────────────────────────────────────────────────────────

    private fun generateContent(apiKey: String, request: GeminiRequest, fileUri: String?): AiResult<String> {
        val parts = JSONArray().put(JSONObject().put("text", buildPrompt(request)))
        if (fileUri != null) {
            parts.put(
                JSONObject().put(
                    "file_data",
                    JSONObject().put("mime_type", AUDIO_MIME).put("file_uri", fileUri)
                )
            )
        }

        val generationConfig = JSONObject().put("temperature", request.temperature.toDouble())
        if (request.responseSchema != null) {
            // Structured output, so nothing downstream has to parse prose. See
            // GeminiTranscriptParser and GeminiIntelligenceEngine's schemas.
            generationConfig.put("response_mime_type", "application/json")
            generationConfig.put("response_schema", JSONObject(request.responseSchema))
        }

        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", generationConfig)
        if (request.systemInstruction.isNotBlank()) {
            body.put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.systemInstruction)))
            )
        }

        val httpRequest = Request.Builder()
            .url("$baseUrl/v1beta/models/${request.modelId}:generateContent?key=$apiKey")
            .post(body.toString().toRequestBody(JSON_MIME))
            .build()

        return client.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@use describeHttpFailure("Analysing the recording", response.code, responseBody)
            }
            extractText(responseBody)
                ?.let { AiResult.Success(it) }
                ?: AiResult.Failed("Gemini returned no usable content.")
        }
    }

    private fun buildPrompt(request: GeminiRequest): String = buildString {
        append(request.prompt)
        if (request.vocabularyHints.isNotEmpty()) {
            appendLine()
            appendLine()
            // Framed as a preference, never an instruction to substitute: a hint must not license
            // rewriting a word that was clearly said into one from this list.
            appendLine(
                "These terms are likely to appear. Prefer them when a word is genuinely ambiguous, " +
                    "but never replace a word you heard clearly with one from this list:"
            )
            append(request.vocabularyHints.joinToString(", "))
        }
    }

    private fun extractText(responseBody: String): String? = try {
        val candidates = JSONObject(responseBody).optJSONArray("candidates")
        val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        buildString {
            for (i in 0 until (parts?.length() ?: 0)) {
                val part = parts!!.optJSONObject(i) ?: continue
                if (part.optBoolean("thought")) continue // a thinking summary, not the answer
                append(part.optString("text"))
            }
        }.takeIf { it.isNotBlank() }
    } catch (e: org.json.JSONException) {
        null
    }

    /**
     * Turns an HTTP failure into something a person can act on.
     *
     * The distinctions matter: a bad key is fixed in Settings, a quota error is fixed by waiting
     * or upgrading, and neither is "something went wrong". The server's own message is included
     * when there is one, because it is usually more specific than anything guessed from a code.
     */
    private fun describeHttpFailure(stage: String, code: Int, body: String?): AiResult<Nothing> {
        val serverMessage = try {
            JSONObject(body.orEmpty()).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        } catch (e: org.json.JSONException) {
            null
        }
        // Never log the body: a failed transcription request's error can echo prompt content.
        Log.w(TAG, "$stage failed with HTTP $code")
        return when (code) {
            400 -> AiResult.Failed("$stage failed: Gemini rejected the request. ${serverMessage.orEmpty()}".trim())
            401, 403 -> AiResult.Failed(
                "$stage failed: your Gemini API key was rejected. Check it in Settings. ${serverMessage.orEmpty()}".trim()
            )
            429 -> AiResult.Failed("$stage failed: Gemini quota exceeded. Try again later, or process this recording offline.")
            in 500..599 -> AiResult.Failed("$stage failed: Gemini is unavailable right now. Try again shortly.")
            else -> AiResult.Failed("$stage failed (HTTP $code). ${serverMessage.orEmpty()}".trim())
        }
    }

    // ── WAV ──────────────────────────────────────────────────────────────────────────────────

    /** Writes `[from, to)` of a float sample buffer as 16-bit PCM mono WAV. */
    internal fun writeWav(target: File, samples: FloatArray, from: Int, to: Int, sampleRate: Int) {
        val sampleCount = to - from
        val dataBytes = sampleCount * 2
        val header = ByteArrayOutputStream(44)

        fun ascii(text: String) = header.write(text.toByteArray(Charsets.US_ASCII))
        fun int32(value: Int) = repeat(4) { header.write((value shr (it * 8)) and 0xFF) }
        fun int16(value: Int) = repeat(2) { header.write((value shr (it * 8)) and 0xFF) }

        ascii("RIFF"); int32(36 + dataBytes); ascii("WAVE")
        ascii("fmt "); int32(16); int16(1); int16(1)
        int32(sampleRate); int32(sampleRate * 2); int16(2); int16(16)
        ascii("data"); int32(dataBytes)

        target.outputStream().buffered().use { out ->
            out.write(header.toByteArray())
            for (i in from until to) {
                // Clamped before scaling: a sample marginally outside [-1, 1] would otherwise wrap
                // to the opposite extreme and produce an audible click in the uploaded audio.
                val clamped = samples[i].coerceIn(-1f, 1f)
                val value = (clamped * Short.MAX_VALUE).toInt()
                out.write(value and 0xFF)
                out.write((value shr 8) and 0xFF)
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"
        private const val TAG = "MeetMindGemini"
        private const val AUDIO_MIME = "audio/wav"
        private val JSON_MIME = "application/json".toMediaType()
        private const val FILE_READY_ATTEMPTS = 12
        private const val FILE_READY_POLL_MS = 500L

        /** Generous timeouts: a meeting chunk is a large upload and transcription is not fast. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }
}
