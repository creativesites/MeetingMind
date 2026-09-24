package com.example.core.setup

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.ai.cloud.GeminiCredentialStore
import com.example.ai.modelmanagement.LocalModelStorage
import com.example.ai.modelmanagement.ModelCatalog
import com.example.ai.modelmanagement.ModelDownloadWorker
import com.example.core.common.DeviceCapabilityDetector
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.AiModelInfo
import com.example.core.model.ModelCapability
import com.example.core.model.ProcessingProfile
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
    /** Internet mode with a key does all three jobs in the cloud. */
    val internetReady: Boolean get() = profile.requiresNetwork && hasGeminiKey
    val ready: Boolean get() = offlineReady || internetReady
    val downloading: Boolean get() = parts.any { it.downloading }
    val doneCount: Int get() = parts.count { it.installed }
    val remainingBytes: Long get() = parts.sumOf { it.remainingBytes }
    val progress: Float get() = if (parts.isEmpty()) 1f else parts.sumOf { (if (it.installed) 1f else it.progress).toDouble() }.toFloat() / parts.size

    /**
     * The classic mistake: a language model (or two) downloaded, but nothing to hear with. Said
     * plainly, because the app otherwise looks set up and recordings quietly fail.
     */
    val thinkingOnly: Boolean get() = part(SetupPart.THINK).installed && !part(SetupPart.HEAR).installed

    /** One line for the card. */
    val headline: String get() = when {
        offlineReady -> "You're all set"
        downloading -> "Setting up… ${(progress * 100).toInt()}%"
        thinkingOnly -> "One more step: MeetingMind can't hear yet"
        internetReady -> "Ready with Internet mode"
        doneCount == 0 -> "Finish setting up MeetingMind"
        else -> "${doneCount} of ${parts.size} ready — finish setting up"
    }

    val detail: String get() = when {
        offlineReady -> "Everything runs privately on this phone."
        downloading -> "Downloading in the background — you can keep using the app."
        thinkingOnly -> "You have a language model, but transcription needs the speech model too. " +
            "Add Hearing and Who spoke (${SetupGuide.formatBytes(remainingBytes)})."
        internetReady -> "Recordings are processed with Gemini. Add the offline pack to work without a connection."
        else -> "Recording works now. To transcribe and summarise privately, get the offline pack (${SetupGuide.formatBytes(remainingBytes)})."
    }

    /** Whether the reminder should show (snoozing is checked by the caller). Internet mode with a
     * key works without the pack, so it isn't nagged — only offered in the setup page. */
    val needsAttention: Boolean get() = !offlineReady && !internetReady
}

object SetupGuide {

    /** The language model that suits this phone: 1.5B with enough memory, otherwise 0.5B. */
    fun recommendedThinker(totalRamGb: Float): AiModelInfo =
        ModelCatalog.entries.firstOrNull { it.id == DeviceCapabilityDetector.recommendedLlmModelId(totalRamGb) } ?: ModelCatalog.qwen25_0_5bInstruct

    fun modelsFor(part: SetupPart, totalRamGb: Float): List<AiModelInfo> = when (part) {
        SetupPart.HEAR -> listOf(ModelCatalog.sileroVad, ModelCatalog.parakeetTdtV3Int8)
        SetupPart.SPEAKERS -> listOf(ModelCatalog.speakerDiarization)
        SetupPart.THINK -> listOf(recommendedThinker(totalRamGb))
    }

    private val thinkers: List<AiModelInfo>
        get() = ModelCatalog.entries.filter { ModelCapability.SUMMARIZATION in it.capability }

    /**
     * Pure: works out each part from what's installed and what's downloading. THINK counts as done
     * with *any* language model, not just the recommended one.
     */
    fun compute(
        totalRamGb: Float,
        isInstalled: (String) -> Boolean,
        downloads: Map<String, DownloadProgress> = emptyMap(),
        profile: ProcessingProfile = ProcessingProfile.OFFLINE,
        hasGeminiKey: Boolean = false
    ): SetupState {
        val parts = SetupPart.entries.map { part ->
            val models = modelsFor(part, totalRamGb)
            val installed = if (part == SetupPart.THINK) thinkers.any { isInstalled(it.id) } else models.all { isInstalled(it.id) }
            val missing = models.filterNot { isInstalled(it.id) }
            val active = missing.mapNotNull { downloads[it.id] }
            val total = missing.sumOf { it.sizeBytes }.coerceAtLeast(1)
            val done = missing.sumOf { m -> downloads[m.id]?.let { if (it.total > 0) it.done * m.sizeBytes / it.total else 0L } ?: 0L }
            // A thinker downloading that isn't the recommended one still counts as progress here.
            val otherThinker = if (part == SetupPart.THINK && !installed) thinkers.mapNotNull { downloads[it.id] }.firstOrNull { it.running } else null
            PartStatus(
                part, models, installed,
                downloading = !installed && (active.any { it.running } || otherThinker != null),
                progress = if (installed) 1f else otherThinker?.fraction ?: (done.toFloat() / total).coerceIn(0f, 1f),
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
            compute(ram, storage::isInstalled, progressOf(infos), prefs.processingProfile, key != null)
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
