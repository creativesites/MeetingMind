package com.example.ai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.ai.cloud.GeminiInteractions
import com.example.ai.common.AiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
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
        chunks.forEachIndexed { i, text ->
            var result: AiResult<com.example.ai.cloud.GeneratedMedia> = AiResult.Failed("not tried")
            for (model in MODELS) {
                result = interactions.media(GeminiInteractions.speechRequest(model, text, settings.deliveryStyle, settings.voiceName), "audio")
                if (result is AiResult.Success) break
            }
            val media = (result as? AiResult.Success)?.value ?: return result as AiResult<Pcm>
            val pcm = if (media.mimeType.contains("l16")) Wav.fromL16(media.bytes, 24_000) else Wav.read(media.bytes)
            clips += pcm ?: return AiResult.Failed("Gemini's audio couldn't be read.")
            onProgress((i + 1f) / chunks.size)
        }
        label = "Gemini voice · ${settings.voiceName}"
        return Wav.concat(clips)?.let { AiResult.Success(it) } ?: AiResult.Failed("No audio.")
    }

    companion object {
        val MODELS = listOf("gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts")
    }
}

/** The phone's own text-to-speech: offline, free, always there. */
class DeviceSpeech(private val context: Context, private val locale: Locale = Locale.getDefault()) : SpeechEngine {
    override val label = "Your phone's voice"

    override suspend fun synthesize(segments: List<SpeechSegment>, settings: VoiceSettings, onProgress: (Float) -> Unit): AiResult<Pcm> {
        val ready = CompletableDeferred<Int>()
        val tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status) }
        try {
            if (withTimeoutOrNull(10_000) { ready.await() } != TextToSpeech.SUCCESS) return AiResult.ModelUnavailable("device-tts", "Text-to-speech isn't available on this phone.")
            if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
            tts.setSpeechRate(settings.rate)
            val pending = mutableMapOf<String, CompletableDeferred<Boolean>>()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { pending[id]?.complete(true) }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { pending[id]?.complete(false) }
                override fun onError(id: String?, errorCode: Int) { pending[id]?.complete(false) }
            })
            val dir = File(context.cacheDir, "tts").apply { mkdirs() }
            val clips = mutableListOf<Pcm>()
            segments.forEachIndexed { i, s ->
                val file = File(dir, "seg_$i.wav")
                val id = "seg_$i"
                val done = CompletableDeferred<Boolean>().also { pending[id] = it }
                if (tts.synthesizeToFile(s.text, null, file, id) != TextToSpeech.SUCCESS) return AiResult.Failed("The phone's voice couldn't read this.")
                val ok = withTimeoutOrNull(120_000) { done.await() } == true
                val pcm = if (ok) Wav.read(file) else null
                file.delete()
                if (pcm != null) {
                    clips += pcm
                    if (s.pauseAfterMs > 0) clips += Wav.silence(s.pauseAfterMs, pcm.sampleRate)
                }
                onProgress((i + 1f) / segments.size)
            }
            return Wav.concat(clips)?.let { AiResult.Success(it) } ?: AiResult.Failed("The phone's voice produced no audio.")
        } finally {
            tts.shutdown()
        }
    }
}
