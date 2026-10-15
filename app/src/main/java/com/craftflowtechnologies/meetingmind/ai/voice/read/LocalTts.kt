package com.craftflowtechnologies.meetingmind.ai.voice.read

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** One installed voice, as the engine reports it. */
data class LocalVoice(
    val name: String,
    val locale: Locale,
    /** The voice fetches its audio online: not allowed in explicit offline mode. */
    val requiresNetwork: Boolean,
    val quality: Int = 0
)

enum class LanguageStatus { AVAILABLE, MISSING_DATA, NOT_SUPPORTED }

/** How one piece of text came out of the engine. */
sealed interface SynthOutcome {
    data object Done : SynthOutcome
    /** The engine's own error code (a [TextToSpeech] `ERROR_*` constant). */
    data class Error(val code: Int) : SynthOutcome
    /** `stop()` interrupted it. */
    data object Stopped : SynthOutcome
    data object Timeout : SynthOutcome
}

/**
 * The phone's text-to-speech as the reading engine needs it, behind an interface so everything above
 * it can be tested without a device. One client is one engine connection.
 */
interface LocalTtsClient {
    /** Connects to the engine; false if it can't (no engine installed, service refused). */
    suspend fun init(timeoutMs: Long): Boolean
    /** The most characters one [synthesize] call may be given. */
    val maxInputLength: Int
    fun languageStatus(locale: Locale): LanguageStatus
    fun setLanguage(locale: Locale): Boolean
    /** Empty when the engine won't list its voices. */
    fun voices(): List<LocalVoice>
    fun setVoice(name: String): Boolean
    fun setRate(rate: Float)
    fun setPitch(pitch: Float)
    suspend fun synthesize(text: String, file: File, timeoutMs: Long): SynthOutcome
    /** Interrupts whatever is being synthesised. */
    fun stop()
    fun shutdown()
}

/** [LocalTtsClient] over Android's [TextToSpeech]. Device behaviour of this class is not covered by unit tests. */
class AndroidTtsClient(private val context: Context) : LocalTtsClient {
    private var tts: TextToSpeech? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<SynthOutcome>>()

    override suspend fun init(timeoutMs: Long): Boolean {
        val ready = CompletableDeferred<Int>()
        val engine = TextToSpeech(context.applicationContext) { status -> ready.complete(status) }
        tts = engine
        val ok = withTimeoutOrNull(timeoutMs) { ready.await() } == TextToSpeech.SUCCESS
        if (!ok) { engine.shutdown(); tts = null; return false }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { pending.remove(utteranceId)?.complete(SynthOutcome.Done) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { pending.remove(utteranceId)?.complete(SynthOutcome.Error(TextToSpeech.ERROR)) }
            override fun onError(utteranceId: String?, errorCode: Int) { pending.remove(utteranceId)?.complete(SynthOutcome.Error(errorCode)) }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { pending.remove(utteranceId)?.complete(SynthOutcome.Stopped) }
        })
        return true
    }

    override val maxInputLength: Int get() = TextToSpeech.getMaxSpeechInputLength()

    override fun languageStatus(locale: Locale): LanguageStatus = when (tts?.isLanguageAvailable(locale)) {
        TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> LanguageStatus.AVAILABLE
        TextToSpeech.LANG_MISSING_DATA -> LanguageStatus.MISSING_DATA
        else -> LanguageStatus.NOT_SUPPORTED
    }

    override fun setLanguage(locale: Locale): Boolean = tts?.setLanguage(locale)?.let { it >= TextToSpeech.LANG_AVAILABLE } == true

    override fun voices(): List<LocalVoice> = runCatching {
        tts?.voices.orEmpty().map { LocalVoice(it.name, it.locale, it.isNetworkConnectionRequired, it.quality) }
    }.getOrDefault(emptyList())

    override fun setVoice(name: String): Boolean {
        val engine = tts ?: return false
        val voice = runCatching { engine.voices.firstOrNull { it.name == name } }.getOrNull() ?: return false
        return engine.setVoice(voice) == TextToSpeech.SUCCESS
    }

    override fun setRate(rate: Float) { tts?.setSpeechRate(rate) }
    override fun setPitch(pitch: Float) { tts?.setPitch(pitch) }

    override suspend fun synthesize(text: String, file: File, timeoutMs: Long): SynthOutcome {
        val engine = tts ?: return SynthOutcome.Error(TextToSpeech.ERROR_SERVICE)
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<SynthOutcome>().also { pending[id] = it }
        try {
            if (engine.synthesizeToFile(text, Bundle(), file, id) != TextToSpeech.SUCCESS) {
                pending.remove(id)
                return SynthOutcome.Error(TextToSpeech.ERROR)
            }
            return withTimeoutOrNull(timeoutMs) { done.await() } ?: SynthOutcome.Timeout.also { engine.stop() }
        } catch (e: CancellationException) {
            engine.stop()
            throw e
        } finally {
            pending.remove(id)
        }
    }

    override fun stop() { tts?.stop() }

    override fun shutdown() {
        pending.values.forEach { it.complete(SynthOutcome.Stopped) }
        pending.clear()
        tts?.shutdown()
        tts = null
    }
}
