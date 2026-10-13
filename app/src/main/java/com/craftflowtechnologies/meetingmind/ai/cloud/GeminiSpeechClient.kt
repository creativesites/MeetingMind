package com.craftflowtechnologies.meetingmind.ai.cloud

import com.craftflowtechnologies.meetingmind.ai.voice.Pcm
import com.craftflowtechnologies.meetingmind.ai.voice.Wav
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Whether the app's own rules allow a cloud request right now. Asked before every single request. */
fun interface CloudPermission {
    /** Null to allow; otherwise the reason it is blocked. */
    suspend fun blockedReason(): String?

    companion object {
        val Allowed = CloudPermission { null }
    }
}

data class RetryPolicy(
    val maxAttemptsPerModel: Int = 3,
    val baseDelayMs: Long = 600,
    val factor: Double = 3.0,
    val maxDelayMs: Long = 8_000,
    /** A server-requested wait longer than this is not ridden out. */
    val retryAfterCapMs: Long = 15_000,
    /** All attempts together may not take longer than this. */
    val totalCapMs: Long = 60_000
)

data class SpeechClip(val audio: Pcm, val model: String)

/**
 * One Gemini speech request, made safely: validated before sending, checked against the app's cloud
 * rules, retried a bounded number of times for errors that can pass, and never sent anywhere else.
 * Errors that only the person can fix (key, plan, quota) stop immediately.
 */
class GeminiSpeechClient(
    private val interactions: GeminiInteractions,
    private val permission: CloudPermission = CloudPermission.Allowed,
    private val models: List<String> = DEFAULT_MODELS,
    private val retry: RetryPolicy = RetryPolicy(),
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val now: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default
) {
    /**
     * [onlyModel] pins the request to one model, so a reading keeps one voice character from the first
     * piece to the last; with it set there is no fallback to the other model.
     */
    suspend fun speak(text: String, style: String, voice: String, onlyModel: String? = null): GeminiResult<SpeechClip> {
        validate(text, voice)?.let { return GeminiResult.Failure(it) }
        val started = now()
        var last: GeminiError? = null
        for (model in onlyModel?.let { listOf(it) } ?: models) {
            var attempt = 0
            while (attempt < retry.maxAttemptsPerModel) {
                attempt++
                permission.blockedReason()?.let {
                    return GeminiResult.Failure(GeminiError(GeminiErrorKind.POLICY_BLOCKED, null, it))
                }
                val error = when (val r = interactions.mediaTyped(GeminiInteractions.speechRequest(model, text, style, voice), "audio")) {
                    is GeminiResult.Success -> decode(r.value, text)?.let { return GeminiResult.Success(SpeechClip(it.first, model)) }
                        ?: GeminiError(GeminiErrorKind.EMPTY_AUDIO, null, "Gemini's audio was empty or too short for the text.")
                    is GeminiResult.Failure -> r.error
                }
                last = error
                if (error.needsUser || error.kind == GeminiErrorKind.POLICY_BLOCKED) return GeminiResult.Failure(error)
                if (!error.retryable) break // wrong request or unknown model: try the next model, not the same one
                if (attempt >= retry.maxAttemptsPerModel) break
                val wait = backoff(attempt, error)
                if (wait == null || now() - started + wait > retry.totalCapMs) {
                    return GeminiResult.Failure(error.copy(retriesExhausted = true, kind = if (error.kind == GeminiErrorKind.RATE_LIMITED && wait == null) GeminiErrorKind.QUOTA_EXHAUSTED else error.kind))
                }
                sleep(wait)
            }
            // Another model can only help when this one is the problem.
            if (last?.kind !in MODEL_FALLBACK_KINDS) break
        }
        return GeminiResult.Failure((last ?: GeminiError(GeminiErrorKind.BAD_REQUEST, null, "No model to try.")).copy(retriesExhausted = last?.retryable == true))
    }

    /** Null when the request is fine. Nothing invalid is ever sent to the network. */
    private fun validate(text: String, voice: String): GeminiError? = when {
        text.isBlank() -> GeminiError(GeminiErrorKind.BAD_REQUEST, null, "Nothing to read.")
        text.length > MAX_TEXT_CHARS -> GeminiError(GeminiErrorKind.BAD_REQUEST, null, "That passage is too long for one voice request.")
        !isKnownVoice(voice) -> GeminiError(GeminiErrorKind.BAD_REQUEST, null, "\"$voice\" isn't a Gemini voice.")
        else -> null
    }

    /** Waits grow 0.6 s, 1.8 s, 5.4 s (capped) with jitter; a server-requested wait wins if it is short enough. */
    private fun backoff(attempt: Int, error: GeminiError): Long? {
        val computed = (retry.baseDelayMs * Math.pow(retry.factor, (attempt - 1).toDouble())).toLong().coerceAtMost(retry.maxDelayMs)
        val jittered = (computed * (0.8 + random.nextDouble() * 0.4)).toLong()
        val asked = error.retryAfterMs ?: return jittered
        if (asked > retry.retryAfterCapMs) return null
        return maxOf(asked, jittered)
    }

    /** The audio and its length, or null if it is unusable. */
    private fun decode(media: GeneratedMedia, text: String): Pair<Pcm, Long>? {
        val mime = media.mimeType.lowercase()
        val pcm = if ("l16" in mime) {
            val rate = Regex("""rate=(\d+)""").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: DEFAULT_SAMPLE_RATE
            Wav.fromL16(media.bytes, rate)
        } else Wav.read(media.bytes)
        pcm ?: return null
        val duration = pcm.durationMs
        // A few words cannot take milliseconds: far too short for the text means the model cut out.
        if (pcm.samples.isEmpty() || duration < minOf(maxOf(150L, text.length * MIN_MS_PER_CHAR), 60_000L)) return null
        return pcm to duration
    }

    companion object {
        val DEFAULT_MODELS = listOf("gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts")
        const val DEFAULT_SAMPLE_RATE = 24_000
        /** Under this many milliseconds per character is not plausible speech (normal is about 40-90). */
        const val MIN_MS_PER_CHAR = 12L
        const val MAX_TEXT_CHARS = 4_000

        /** The model, not the request, may be at fault. */
        private val MODEL_FALLBACK_KINDS = setOf(
            GeminiErrorKind.NOT_FOUND, GeminiErrorKind.BAD_REQUEST, GeminiErrorKind.SERVER,
            GeminiErrorKind.MALFORMED, GeminiErrorKind.EMPTY_AUDIO
        )

        /** The 30 prebuilt voices on the speech generation page. */
        val PREBUILT_VOICES = setOf(
            "Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede", "Callirrhoe", "Autonoe",
            "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina", "Erinome", "Algenib", "Rasalgethi", "Laomedeia", "Achernar",
            "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi", "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat"
        )

        fun isKnownVoice(voice: String) = voice in PREBUILT_VOICES || voice.startsWith("voice_") || voice.startsWith("voicekey_")
    }
}
