package com.craftflowtechnologies.meetingmind.ai.voice

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiErrorKind
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiInteractions
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiResult
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiSpeechClient
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.voice.read.AndroidTtsClient
import com.craftflowtechnologies.meetingmind.ai.voice.read.LocalFailure
import com.craftflowtechnologies.meetingmind.ai.voice.read.LocalSpeechEngine
import com.craftflowtechnologies.meetingmind.ai.voice.read.LocalSpeechRequest
import com.craftflowtechnologies.meetingmind.ai.voice.read.LocalSpeechResult
import java.io.File
import java.util.Locale

/** Turns a script into speech. */
interface SpeechEngine {
    /** Shown with the audio: "Gemini voice · Algieba", "Your phone's voice". */
    val label: String
    suspend fun synthesize(segments: List<SpeechSegment>, settings: VoiceSettings, onProgress: (Float) -> Unit = {}): AiResult<Pcm>
}

/**
 * Gemini's speech models (PLAN_V2 F3): the preacher style becomes `speech_metadata.style`, pauses are
 * real silence joined in on the phone. Each request goes through [GeminiSpeechClient] (validated,
 * retried for errors that can pass, typed errors); once a model has answered, the rest of the script
 * stays on it so the voice doesn't change part-way.
 */
class GeminiSpeech(private val interactions: GeminiInteractions) : SpeechEngine {
    override var label: String = "Gemini voice"
        private set

    override suspend fun synthesize(segments: List<SpeechSegment>, settings: VoiceSettings, onProgress: (Float) -> Unit): AiResult<Pcm> {
        val chunks = SpeechScript.chunks(segments)
        if (chunks.isEmpty()) return AiResult.Failed("Nothing to read.")
        val client = GeminiSpeechClient(interactions)
        val clips = mutableListOf<Pcm>()
        var pinned: String? = null
        chunks.forEachIndexed { i, chunk ->
            when (val r = client.speak(chunk.text, settings.deliveryStyle, settings.voiceName, onlyModel = pinned)) {
                is GeminiResult.Success -> {
                    pinned = r.value.model
                    val pcm = r.value.audio
                    clips += pcm
                    if (chunk.pauseAfterMs > 0) clips += Wav.silence(chunk.pauseAfterMs, pcm.sampleRate)
                }
                is GeminiResult.Failure ->
                    return if (r.error.kind == GeminiErrorKind.NO_KEY) AiResult.ModelUnavailable(pinned ?: GeminiSpeechClient.DEFAULT_MODELS.first(), r.error.message)
                    else AiResult.Failed(r.error.legacyMessage)
            }
            onProgress((i + 1f) / chunks.size)
        }
        label = "Gemini voice · ${settings.voiceName}"
        return Wav.concat(clips)?.let { AiResult.Success(it) } ?: AiResult.Failed("No audio.")
    }

    companion object {
        val MODELS = GeminiSpeechClient.DEFAULT_MODELS
    }
}

/**
 * The phone's own text-to-speech: offline, free, always there. Reads through [LocalSpeechEngine],
 * which splits long text to the engine's limit and reports a failed piece instead of skipping it.
 */
class DeviceSpeech(private val context: Context, private val locale: Locale = Locale.getDefault()) : SpeechEngine {
    override val label = "Your phone's voice"

    override suspend fun synthesize(segments: List<SpeechSegment>, settings: VoiceSettings, onProgress: (Float) -> Unit): AiResult<Pcm> {
        val chunks = segments.map { SpeechChunk(SpeechScript.stripTags(it.text), it.pauseAfterMs) }.filter { it.text.isNotBlank() }
        if (chunks.isEmpty()) return AiResult.Failed("Nothing to read.")
        val engine = LocalSpeechEngine({ AndroidTtsClient(context.applicationContext) }, File(context.cacheDir, "tts"))
        return when (val result = engine.synthesize(LocalSpeechRequest(chunks, locale, rate = settings.rate), onProgress)) {
            is LocalSpeechResult.Done -> AiResult.Success(result.audio)
            is LocalSpeechResult.Failed ->
                if (result.kind == LocalFailure.INIT_FAILED) AiResult.ModelUnavailable("device-tts", result.message)
                else AiResult.Failed(result.message)
        }
    }
}
