package com.craftflowtechnologies.meetingmind.ai.voice

import android.content.Context
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiInteractions
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
 * Gemini's speech models (PLAN_V2 F3): the preacher style becomes `speech_metadata.style`, pauses
 * become inline `<short pause>` / `<long pause>` tags. Long scripts go in a few requests and are
 * joined; the Flash-Lite model is tried if the main one fails.
 */
class GeminiSpeech(private val interactions: GeminiInteractions) : SpeechEngine {
    override var label: String = "Gemini voice"
        private set

    override suspend fun synthesize(segments: List<SpeechSegment>, settings: VoiceSettings, onProgress: (Float) -> Unit): AiResult<Pcm> {
        val chunks = SpeechScript.chunks(segments)
        if (chunks.isEmpty()) return AiResult.Failed("Nothing to read.")
        val clips = mutableListOf<Pcm>()
        chunks.forEachIndexed { i, chunk ->
            var result: AiResult<com.craftflowtechnologies.meetingmind.ai.cloud.GeneratedMedia> = AiResult.Failed("not tried")
            for (model in MODELS) {
                result = interactions.media(GeminiInteractions.speechRequest(model, chunk.text, settings.deliveryStyle, settings.voiceName), "audio")
                if (result is AiResult.Success) break
            }
            val media = (result as? AiResult.Success)?.value ?: return result as AiResult<Pcm>
            val pcm = (if (media.mimeType.contains("l16")) Wav.fromL16(media.bytes, 24_000) else Wav.read(media.bytes))
                ?: return AiResult.Failed("Gemini's audio couldn't be read.")
            clips += pcm
            if (chunk.pauseAfterMs > 0) clips += Wav.silence(chunk.pauseAfterMs, pcm.sampleRate)
            onProgress((i + 1f) / chunks.size)
        }
        label = "Gemini voice · ${settings.voiceName}"
        return Wav.concat(clips)?.let { AiResult.Success(it) } ?: AiResult.Failed("No audio.")
    }

    companion object {
        val MODELS = listOf("gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts")
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
