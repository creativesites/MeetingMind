package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiSpeechClient
import com.craftflowtechnologies.meetingmind.ai.voice.SpeechChunk
import java.util.Locale

/** Local (phone) failures and what the person can do about each. */
object LocalRecovery {
    fun forLocal(kind: LocalFailure, canInstallVoiceData: Boolean): RecoveryPlan = when (kind) {
        LocalFailure.INIT_FAILED -> RecoveryPlan("Text-to-speech isn't available on this phone. Check that a speech engine is installed.", listOf(Recovery.RETRY))
        LocalFailure.LANGUAGE_UNSUPPORTED -> RecoveryPlan("This phone's voice can't read that language.", listOf(Recovery.RETRY))
        LocalFailure.LANGUAGE_DATA_MISSING -> RecoveryPlan("The voice data for this language isn't installed.", if (canInstallVoiceData) listOf(Recovery.INSTALL_VOICE_DATA, Recovery.RETRY) else listOf(Recovery.RETRY))
        LocalFailure.VOICE_UNAVAILABLE -> RecoveryPlan("That voice isn't available. Pick another in the Read to Me sheet.", listOf(Recovery.RETRY))
        LocalFailure.TIMEOUT, LocalFailure.SERVICE_LOST, LocalFailure.INTERRUPTED, LocalFailure.SYNTHESIS_FAILED ->
            RecoveryPlan("The phone's voice stopped part-way.", listOf(Recovery.RETRY))
    }
}

/** The phone's own voice for a part. Offline-safe: it never touches the network. */
class LocalSectionVoice(
    private val engine: LocalSpeechEngine,
    private val locale: Locale,
    private val voiceName: String?,
    private val rate: Float,
    private val requireOffline: Boolean
) : SectionVoice {
    override val provider = SpeechProvider.LOCAL
    override val fingerprintModel = "device-tts"
    override val voiceId: String get() = voiceName ?: "default"

    override suspend fun synthesize(partId: String, fingerprint: String, chunks: List<SpeechChunk>, onProgress: (Float) -> Unit): VoiceResult =
        when (val r = engine.synthesize(LocalSpeechRequest(chunks, locale, voiceName, rate, 1f, requireOffline), onProgress)) {
            is LocalSpeechResult.Done -> VoiceResult.Done(r.audio)
            is LocalSpeechResult.Failed -> VoiceResult.Failed(r.message, LocalRecovery.forLocal(r.kind, r.canInstallVoiceData))
        }

    override fun discard(partId: String) = Unit
}

/** The Gemini voice for a part. One model is kept for the whole reading once it has answered. */
class GeminiSectionVoice(
    private val engine: GeminiReadingEngine,
    private val voice: String,
    private val delivery: DeliverySpec,
    pinnedModel: String? = null
) : SectionVoice {
    override val provider = SpeechProvider.GEMINI
    override val fingerprintModel = "gemini-tts"
    override val voiceId: String get() = voice
    @Volatile private var pinned: String? = pinnedModel

    override suspend fun synthesize(partId: String, fingerprint: String, chunks: List<SpeechChunk>, onProgress: (Float) -> Unit): VoiceResult =
        when (val r = engine.synthesize(GeminiSectionRequest(partId, fingerprint, chunks, delivery.geminiStyle(), voice, pinned), onProgress)) {
            is GeminiSectionResult.Done -> { pinned = r.model ?: pinned; VoiceResult.Done(r.audio, r.model) }
            is GeminiSectionResult.Failed -> VoiceResult.Failed(r.error.message, ReadingRecovery.forGemini(r.error))
        }

    override fun discard(partId: String) = engine.discard(partId)

    companion object {
        val OFFERED_VOICES = listOf("Kore", "Algieba", "Sulafat", "Gacrux", "Charon", "Achird", "Vindemiatrix", "Schedar", "Aoede", "Erinome", "Algenib", "Alnilam", "Fenrir", "Iapetus", "Rasalgethi")
            .filter { GeminiSpeechClient.isKnownVoice(it) }
    }
}
