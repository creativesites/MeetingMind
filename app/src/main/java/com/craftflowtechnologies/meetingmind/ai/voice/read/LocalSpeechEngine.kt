package com.craftflowtechnologies.meetingmind.ai.voice.read

import android.speech.tts.TextToSpeech
import com.craftflowtechnologies.meetingmind.ai.voice.Pcm
import com.craftflowtechnologies.meetingmind.ai.voice.SpeechChunk
import com.craftflowtechnologies.meetingmind.ai.voice.Wav
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.Locale
import java.util.UUID

enum class LocalFailure {
    INIT_FAILED, LANGUAGE_UNSUPPORTED, LANGUAGE_DATA_MISSING, VOICE_UNAVAILABLE,
    SYNTHESIS_FAILED, TIMEOUT, SERVICE_LOST,
    /** Something else (another app, the system) stopped the speech part-way. */
    INTERRUPTED
}

data class LocalSpeechRequest(
    val chunks: List<SpeechChunk>,
    val locale: Locale = Locale.getDefault(),
    /** A voice the person chose; null lets the engine (or the best offline voice) decide. */
    val voiceName: String? = null,
    val rate: Float = 1f,
    val pitch: Float = 1f,
    /** Explicit offline mode: voices that fetch audio online are never used. */
    val requireOffline: Boolean = false
)

sealed interface LocalSpeechResult {
    data class Done(val audio: Pcm) : LocalSpeechResult

    /**
     * Nothing is skipped silently: a failure stops the reading and says which input chunk it reached
     * ([chunkIndex], an index into [LocalSpeechRequest.chunks]) and what had already been made.
     */
    data class Failed(
        val kind: LocalFailure,
        val message: String,
        val chunkIndex: Int,
        val completed: List<Pcm>,
        /** Offer the system's "install voice data" screen. */
        val canInstallVoiceData: Boolean = false
    ) : LocalSpeechResult
}

/**
 * Reads text with the phone's own voice: offline, free, always there. Long text is split to the
 * engine's limit with [TextChunker], a failed piece is reported rather than skipped, cancellation
 * stops the engine and cleans up, and a lost engine connection is retried once.
 */
class LocalSpeechEngine(
    private val newClient: () -> LocalTtsClient,
    private val tempDir: File,
    private val initTimeoutMs: Long = 10_000,
    private val chunkTimeoutMs: Long = 120_000
) {
    suspend fun synthesize(request: LocalSpeechRequest, onProgress: (Float) -> Unit = {}): LocalSpeechResult {
        val chunks = request.chunks.filter { it.text.isNotBlank() }
        if (chunks.isEmpty()) return failed(LocalFailure.SYNTHESIS_FAILED, "Nothing to read.", 0, emptyList())
        tempDir.mkdirs()
        val made = mutableListOf<File>()
        var client = newClient()
        try {
            if (!client.init(initTimeoutMs)) return failed(LocalFailure.INIT_FAILED, "Text-to-speech isn't available on this phone.", 0, emptyList())
            configure(client, request)?.let { return it }

            val clips = mutableListOf<Pcm>()
            var reconnected = false
            for ((i, chunk) in chunks.withIndex()) {
                val limit = (client.maxInputLength - 1).coerceIn(1, MAX_PIECE)
                val pieces = TextChunker.split(chunk.text, limit)
                for ((p, piece) in pieces.withIndex()) {
                    var attempt = 0
                    while (true) {
                        val file = File(tempDir, "read_${UUID.randomUUID()}.wav").also { made += it }
                        when (val outcome = client.synthesize(piece, file, chunkTimeoutMs)) {
                            SynthOutcome.Done -> {
                                val pcm = Wav.read(file)
                                    ?: return failed(LocalFailure.SYNTHESIS_FAILED, "The phone's voice produced no audio.", i, clips)
                                clips += pcm
                                if (p == pieces.lastIndex) {
                                    if (chunk.pauseAfterMs > 0) clips += Wav.silence(chunk.pauseAfterMs, pcm.sampleRate)
                                } else clips += Wav.silence(PIECE_GAP_MS, pcm.sampleRate)
                                break
                            }
                            is SynthOutcome.Error -> {
                                val kind = classify(outcome.code)
                                if (kind == LocalFailure.SERVICE_LOST && !reconnected && attempt == 0) {
                                    reconnected = true; attempt++
                                    client.shutdown()
                                    client = newClient()
                                    if (!client.init(initTimeoutMs)) return failed(LocalFailure.SERVICE_LOST, "The phone's voice stopped responding.", i, clips)
                                    configure(client, request)?.let { return it.copy(chunkIndex = i, completed = clips.toList()) }
                                    continue
                                }
                                return failed(kind, messageFor(kind), i, clips, canInstall = kind == LocalFailure.LANGUAGE_DATA_MISSING)
                            }
                            SynthOutcome.Timeout -> return failed(LocalFailure.TIMEOUT, "The phone's voice took too long.", i, clips)
                            SynthOutcome.Stopped -> return failed(LocalFailure.INTERRUPTED, "The phone's voice was interrupted.", i, clips)
                        }
                    }
                }
                onProgress((i + 1f) / chunks.size)
            }
            val audio = Wav.concat(clips) ?: return failed(LocalFailure.SYNTHESIS_FAILED, "The phone's voice produced no audio.", chunks.lastIndex, clips)
            return LocalSpeechResult.Done(audio)
        } catch (e: CancellationException) {
            client.stop()
            throw e
        } finally {
            client.shutdown()
            made.forEach { it.delete() }
        }
    }

    /** Language, voice, rate and pitch. A failure here is reported before anything is spoken. */
    private fun configure(client: LocalTtsClient, r: LocalSpeechRequest): LocalSpeechResult.Failed? {
        when (client.languageStatus(r.locale)) {
            LanguageStatus.NOT_SUPPORTED -> return failed(LocalFailure.LANGUAGE_UNSUPPORTED, "This phone's voice can't read ${r.locale.displayLanguage}.", 0, emptyList())
            LanguageStatus.MISSING_DATA -> return failed(LocalFailure.LANGUAGE_DATA_MISSING, "${r.locale.displayLanguage} voice data isn't installed.", 0, emptyList(), canInstall = true)
            LanguageStatus.AVAILABLE -> client.setLanguage(r.locale)
        }
        val voices = client.voices()
        val chosen = chooseVoice(voices, r)
        when {
            r.voiceName != null && chosen == null ->
                return failed(LocalFailure.VOICE_UNAVAILABLE, if (r.requireOffline && voices.any { it.name == r.voiceName }) "That voice needs the internet, which offline mode doesn't use." else "That voice isn't installed.", 0, emptyList())
            r.voiceName == null && r.requireOffline && voices.isNotEmpty() && chosen == null ->
                return failed(LocalFailure.VOICE_UNAVAILABLE, "No on-device ${r.locale.displayLanguage} voice is installed.", 0, emptyList(), canInstall = true)
        }
        chosen?.let { client.setVoice(it.name) }
        client.setRate(r.rate)
        client.setPitch(r.pitch)
        return null
    }

    companion object {
        /** Our own ceiling under the engine's limit: engines behave better with shorter requests. */
        const val MAX_PIECE = 3900
        const val PIECE_GAP_MS = 150L

        /** The voice to use, or null when the request names none and the engine's own default should run. */
        fun chooseVoice(voices: List<LocalVoice>, r: LocalSpeechRequest): LocalVoice? {
            val allowed = voices.filter { !r.requireOffline || !it.requiresNetwork }
            r.voiceName?.let { name -> return allowed.firstOrNull { it.name == name } }
            if (!r.requireOffline) return null
            return allowed.filter { it.locale.language == r.locale.language }
                .sortedWith(compareByDescending<LocalVoice> { it.locale == r.locale }.thenByDescending { it.quality })
                .firstOrNull()
        }

        fun classify(code: Int): LocalFailure = when (code) {
            TextToSpeech.ERROR_SERVICE -> LocalFailure.SERVICE_LOST
            TextToSpeech.ERROR_NOT_INSTALLED_YET -> LocalFailure.LANGUAGE_DATA_MISSING
            TextToSpeech.ERROR_NETWORK, TextToSpeech.ERROR_NETWORK_TIMEOUT -> LocalFailure.VOICE_UNAVAILABLE
            else -> LocalFailure.SYNTHESIS_FAILED
        }

        private fun messageFor(kind: LocalFailure) = when (kind) {
            LocalFailure.SERVICE_LOST -> "The phone's voice stopped responding."
            LocalFailure.LANGUAGE_DATA_MISSING -> "The voice data for this language isn't installed yet."
            LocalFailure.VOICE_UNAVAILABLE -> "That voice needs the internet and isn't available."
            else -> "The phone's voice couldn't read this."
        }

        private fun failed(kind: LocalFailure, message: String, index: Int, done: List<Pcm>, canInstall: Boolean = false) =
            LocalSpeechResult.Failed(kind, message, index, done.toList(), canInstall)
    }
}
