package com.craftflowtechnologies.meetingmind.core.setup

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiCredentialStore
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.AsrModelResolver
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.LocalModelStorage
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelCatalog
import com.craftflowtechnologies.meetingmind.ai.modelmanagement.ModelDownloadWorker
import com.craftflowtechnologies.meetingmind.core.common.DeviceCapabilityDetector
import com.craftflowtechnologies.meetingmind.core.datastore.UserPreferencesManager
import com.craftflowtechnologies.meetingmind.core.model.AiModelInfo
import com.craftflowtechnologies.meetingmind.core.model.ModelCapability
import com.craftflowtechnologies.meetingmind.core.model.ProcessingProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart

/**
 * The three things the app needs to work offline, in the words a person would use. People don't
 * know that "Parakeet" hears, a diarization model tells voices apart and a language model writes
 * the summary — they download one LLM and think they're done. So setup is framed as jobs, not
 * model names, and each job says what breaks without it.
 */
enum class SetupPart(val title: String, val job: String, val without: String) {
    HEAR(
        "Hearing", "Turns speech into text, on this phone.",
        "Without it, recordings can't be transcribed offline."
    ),
    SPEAKERS(
        "Who spoke", "Tells voices apart, so transcripts say who said what.",
        "Without it, everything is one speaker."
    ),
    THINK(
        "Thinking", "Writes summaries, action items and answers, and devotionals offline.",
        "Without it, there are no summaries unless Internet mode is on."
    )
}

data class PartStatus(
    val part: SetupPart,
    val models: List<AiModelInfo>,
    val installed: Boolean,
    val downloading: Boolean = false,
    /** 0–1 over this part's missing models. */
    val progress: Float = 0f,
    val failed: Boolean = false
) {
    /** What's still to fetch for this part. */
    val remainingBytes: Long get() = if (installed) 0 else models.sumOf { it.sizeBytes }
    val modelNames: String get() = models.joinToString(" + ") { it.name }
}

data class SetupState(
    val parts: List<PartStatus>,
    val profile: ProcessingProfile = ProcessingProfile.OFFLINE,
    val hasGeminiKey: Boolean = false
) {
    fun part(p: SetupPart) = parts.first { it.part == p }
    val offlineReady: Boolean get() = parts.all { it.installed }
    /** Internet mode with a cloud route (the person's key or MeetingMind's own service) does all three jobs. */
    val internetReady: Boolean get() = profile.requiresNetwork && hasGeminiKey

    /**
     * The app can turn speech into text: any installed speech model (with the voice helper), or Internet
     * mode. This, and only this, is what Home and the record screen remind about. Who spoke and Thinking
     * are extras: offered on the setup page, never nagged.
     */
    val canTranscribe: Boolean get() = part(SetupPart.HEAR).installed || internetReady

    /** What it costs to become able to transcribe offline: the Hearing part only. */
    val essentialBytes: Long get() = part(SetupPart.HEAR).remainingBytes
    val ready: Boolean get() = offlineReady || internetReady
    val downloading: Boolean get() = parts.any { it.downloading }
    val doneCount: Int get() = parts.count { it.installed }
    val remainingBytes: Long get() = parts.sumOf { it.remainingBytes }
    val progress: Float get() = if (parts.isEmpty()) 1f else parts.sumOf { (if (it.installed) 1f else it.progress).toDouble() }.toFloat() / parts.size

    /**
     * The classic mistake: a language model (or two) downloaded, but nothing to hear with. Said
     * plainly, because the app otherwise looks set up and recordings quietly fail.
     */
    val thinkingOnly: Boolean get() = part(SetupPart.THINK).installed && !part(SetupPart.HEAR).installed && !internetReady

    /** One line for the card. */
    val headline: String get() = when {
        offlineReady -> "You're all set"
        downloading -> "Setting up… ${(progress * 100).toInt()}%"
        thinkingOnly -> "One more step: MeetingMind can't hear yet"
        part(SetupPart.HEAR).installed -> "Ready to transcribe on this phone"
        internetReady -> "Ready with Internet mode"
        else -> "Get the speech model to transcribe offline"
    }

    val detail: String get() = when {
        offlineReady -> "Everything runs privately on this phone."
        downloading -> "Downloading in the background — you can keep using the app."
        thinkingOnly -> "You have a language model, but transcription needs a speech model too " +
            "(${SetupGuide.formatBytes(essentialBytes)})."
        part(SetupPart.HEAR).installed -> "Who spoke and offline summaries are optional extras on the setup page."
        internetReady -> "Recordings are processed with Gemini. Add a speech model to work without a connection."
        else -> "Recording works now. To transcribe privately on this phone, get a speech model (${SetupGuide.formatBytes(essentialBytes)})."
    }

    /**
     * Whether the reminder should show (snoozing is checked by the caller): only when nothing can
     * transcribe. A phone with any speech model, or Internet mode, is never nagged for more models.
     */
    val needsAttention: Boolean get() = !canTranscribe
}

object SetupGuide {

    /** The language model that suits this phone: 1.5B with enough memory, otherwise 0.5B. */
    fun recommendedThinker(totalRamGb: Float): AiModelInfo =
        ModelCatalog.entries.firstOrNull { it.id == DeviceCapabilityDetector.recommendedLlmModelId(totalRamGb) } ?: ModelCatalog.qwen25_0_5bInstruct

    /** The speech model that suits this phone and [language]: Parakeet, Whisper or Omnilingual. */
    fun recommendedHearer(totalRamGb: Float, language: String? = null): AiModelInfo =
        AsrModelResolver.recommended(AsrModelResolver.ramMbOf(totalRamGb), language)

    fun modelsFor(part: SetupPart, totalRamGb: Float, language: String? = null): List<AiModelInfo> = when (part) {
        SetupPart.HEAR -> listOf(ModelCatalog.sileroVad, recommendedHearer(totalRamGb, language))
        SetupPart.SPEAKERS -> listOf(ModelCatalog.speakerDiarization)
        SetupPart.THINK -> listOf(recommendedThinker(totalRamGb))
    }

    private val thinkers: List<AiModelInfo>
        get() = ModelCatalog.entries.filter { ModelCapability.SUMMARIZATION in it.capability }

    /**
     * HEAR needs the voice-activity helper and *a* speech model — any of them. With one installed,
     * that one is listed (nothing more to fetch for it); with none, the recommended one is.
     */
    private fun hearingModels(totalRamGb: Float, language: String?, isInstalled: (String) -> Boolean): List<AiModelInfo> {
        val installed = AsrModelResolver.speechModels.filter { isInstalled(it.id) }.map { it.id }.toSet()
        val ramMb = AsrModelResolver.ramMbOf(totalRamGb)
        val speech = AsrModelResolver.resolve(installed, null, language, ramMb)
            ?: AsrModelResolver.resolve(installed, null, null, ramMb)
            ?: recommendedHearer(totalRamGb, language)
        return listOf(ModelCatalog.sileroVad, speech)
    }

    /**
     * Pure: works out each part from what's installed and what's downloading. THINK counts as done
     * with *any* language model, and HEAR with the helper plus *any* speech model, not just the
     * recommended ones.
     */
    fun compute(
        totalRamGb: Float,
        isInstalled: (String) -> Boolean,
        downloads: Map<String, DownloadProgress> = emptyMap(),
        profile: ProcessingProfile = ProcessingProfile.OFFLINE,
        hasGeminiKey: Boolean = false,
        language: String? = null
    ): SetupState {
        val parts = SetupPart.entries.map { part ->
            val models = if (part == SetupPart.HEAR) hearingModels(totalRamGb, language, isInstalled) else modelsFor(part, totalRamGb, language)
            val installed = if (part == SetupPart.THINK) thinkers.any { isInstalled(it.id) } else models.all { isInstalled(it.id) }
            val missing = models.filterNot { isInstalled(it.id) }
            val active = missing.mapNotNull { downloads[it.id] }
            val total = missing.sumOf { it.sizeBytes }.coerceAtLeast(1)
            val done = missing.sumOf { m -> downloads[m.id]?.let { if (it.total > 0) it.done * m.sizeBytes / it.total else 0L } ?: 0L }
            // A thinker (or speech model) downloading that isn't the recommended one still counts as progress here.
            val otherThinker = if (part == SetupPart.THINK && !installed) thinkers.mapNotNull { downloads[it.id] }.firstOrNull { it.running } else null
            val otherHearer = if (part == SetupPart.HEAR && !installed && active.none { it.running }) {
                AsrModelResolver.speechModels.filter { it !in models }.mapNotNull { downloads[it.id] }.firstOrNull { it.running }
            } else null
            PartStatus(
                part, models, installed,
                downloading = !installed && (active.any { it.running } || otherThinker != null || otherHearer != null),
                progress = if (installed) 1f else otherThinker?.fraction ?: otherHearer?.fraction ?: (done.toFloat() / total).coerceIn(0f, 1f),
                failed = !installed && active.any { it.failed } && active.none { it.running }
            )
        }
        return SetupState(parts, profile, hasGeminiKey)
    }

    /** Live setup state: installed files, running downloads, mode and key. */
    fun observe(context: Context): Flow<SetupState> {
        val app = context.applicationContext
        val ram = DeviceCapabilityDetector.detect(app).totalRamGb
        val storage = LocalModelStorage(app)
        val downloads = runCatching { WorkManager.getInstance(app).getWorkInfosByTagFlow(ModelDownloadWorker.TAG_ALL) }.getOrNull()
            ?.onStart { emit(emptyList()) }?.catch { emit(emptyList()) } ?: flowOf(emptyList())
        return combine(
            downloads,
            UserPreferencesManager(app).preferencesFlow,
            GeminiCredentialStore(app).apiKeyFlow.catch { emit(null) }
        ) { infos, prefs, key ->
            // Internet mode works with the person's key or through MeetingMind's own service (the proxy).
            val cloud = key != null || com.craftflowtechnologies.meetingmind.BuildConfig.GEMINI_PROXY_URL.trim().startsWith("https://")
            compute(ram, storage::isInstalled, progressOf(infos), prefs.processingProfile, cloud, prefs.selectedLanguage)
        }
    }

    fun progressOf(infos: List<WorkInfo>): Map<String, DownloadProgress> = buildMap {
        infos.forEach { info ->
            val id = info.tags.firstOrNull { it.startsWith(ModelDownloadWorker.modelTag("")) }?.removePrefix(ModelDownloadWorker.modelTag("")) ?: return@forEach
            val running = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.BLOCKED
            val p = DownloadProgress(
                done = info.progress.getLong(ModelDownloadWorker.KEY_DONE, 0L),
                total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, 0L),
                running = running,
                failed = info.state == WorkInfo.State.FAILED
            )
            // Several runs of one model: the live one wins.
            val prev = get(id)
            if (prev == null || (!prev.running && running)) put(id, p)
        }
    }

    /** Starts every missing piece in one go ("Set up in one tap"). */
    fun downloadMissing(context: Context, state: SetupState, wifiOnly: Boolean, parts: Collection<SetupPart> = SetupPart.entries) {
        val storage = LocalModelStorage(context)
        state.parts.filter { it.part in parts && !it.installed && !it.downloading }.forEach { status ->
            status.models.filterNot { storage.isInstalled(it.id) }.forEach { ModelDownloadWorker.enqueue(context, it.id, wifiOnly) }
        }
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1e9)
        bytes >= 1_000_000L -> "${bytes / 1_000_000L} MB"
        else -> "${(bytes / 1000L).coerceAtLeast(1)} KB"
    }
}

data class DownloadProgress(val done: Long, val total: Long, val running: Boolean, val failed: Boolean = false) {
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}
