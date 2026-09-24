package com.example.core.devotional

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.ai.cloud.GeminiCredentialStore
import com.example.ai.cloud.GeminiInteractions
import com.example.ai.common.AiResult
import com.example.ai.voice.DeviceSpeech
import com.example.ai.voice.GeminiSpeech
import com.example.ai.voice.Pcm
import com.example.ai.voice.SpeechEngine
import com.example.ai.voice.SpeechScript
import com.example.ai.voice.SpeechSegment
import com.example.ai.voice.VoiceSettings
import com.example.ai.voice.Wav
import com.example.core.audio.AacEncoder
import com.example.core.database.MeetMindDatabase
import com.example.core.datastore.UserPreferencesManager
import com.example.core.model.Attachment
import com.example.core.model.AttachmentKind
import com.example.core.model.ProcessingProfile
import com.example.core.repository.NoteRepository
import com.example.core.scripture.PassageResult
import com.example.core.scripture.ScriptureService
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * The preacher (PLAN_V2 F3): reads a devotional aloud and keeps the recording on its note.
 * Gemini's voice when Internet mode is on and a key is set; the phone's own voice otherwise, or
 * if Gemini fails — so "Listen" always works.
 */
class DevotionalVoice(
    private val context: Context,
    private val notes: NoteRepository = NoteRepository(context, MeetMindDatabase.getInstance(context)),
    private val prefs: UserPreferencesManager = UserPreferencesManager(context)
) {
    companion object {
        const val META_AUDIO = "devotionalAudio"
        const val META_AUDIO_VOICE = "devotionalAudioVoice"
        /** Where each section starts in the recording: [com.example.ai.voice.VoiceSection.encode]. */
        const val META_AUDIO_MARKS = "devotionalAudioMarks"

        fun playbackId(noteId: String) = "devotional:$noteId"
    }

    /** Engines to try, best first. */
    private suspend fun engines(): List<SpeechEngine> {
        val cloud = prefs.preferencesFlow.first().processingProfile == ProcessingProfile.INTERNET
        val interactions = GeminiInteractions(GeminiCredentialStore(context))
        return listOfNotNull(
            if (cloud && interactions.isConfigured()) GeminiSpeech(interactions) else null,
            DeviceSpeech(context)
        )
    }

    private suspend fun synthesize(segments: List<SpeechSegment>, settings: VoiceSettings, onProgress: (Float) -> Unit): Pair<Pcm, String>? {
        for (engine in engines()) {
            val result = runCatching { engine.synthesize(segments, settings, onProgress) }.getOrNull()
            if (result is AiResult.Success) return result.value to engine.label
        }
        return null
    }

    /**
     * Records section by section, so each section's start is known exactly and a listener can
     * jump to it — or hear only the prayer. One engine reads the whole thing, so the voice never
     * changes mid-way.
     */
    private suspend fun synthesizeSections(segments: List<SpeechSegment>, settings: VoiceSettings, onProgress: (Float) -> Unit): Triple<Pcm, String, List<Pair<com.example.ai.voice.VoiceSection, Long>>>? {
        val sections = mutableListOf<Pair<com.example.ai.voice.VoiceSection, MutableList<SpeechSegment>>>()
        segments.forEach { s -> if (sections.lastOrNull()?.first == s.kind.section) sections.last().second += s else sections += s.kind.section to mutableListOf(s) }
        for (engine in engines()) {
            val clips = mutableListOf<Pcm>()
            val marks = mutableListOf<Pair<com.example.ai.voice.VoiceSection, Long>>()
            var at = 0L
            var ok = true
            for ((i, section) in sections.withIndex()) {
                val result = runCatching { engine.synthesize(section.second, settings) { p -> onProgress((i + p) / sections.size) } }.getOrNull()
                val pcm = (result as? AiResult.Success)?.value
                if (pcm == null) { ok = false; break }
                marks += section.first to at
                clips += pcm
                at += pcm.durationMs
            }
            if (ok) Wav.concat(clips)?.let { return Triple(it, engine.label, marks) }
        }
        return null
    }

    /** Records [daily] aloud and attaches it. Returns the audio file, or null if no voice could read it. */
    suspend fun record(daily: DailyDevotional, onProgress: (Float) -> Unit = {}): File? {
        val profile = prefs.devotionalProfile.first()
        val scripture = ScriptureService(context)
        val verses = daily.devotional.scripture.associateWith { ref ->
            (runCatching { scripture.passage(ref) }.getOrNull() as? PassageResult.Found)?.passage?.text
        }.filterValues { it != null }.mapValues { it.value!! }
        val name = prefs.preferencesFlow.first().identity.displayName?.substringBefore(' ')
        val segments = SpeechScript.build(daily.devotional, verses, profile.voice, name)
        val (pcm, label, marks) = synthesizeSections(segments, profile.voice, onProgress) ?: return null

        // Replace an earlier recording of the same devotional (first: it may share the file name).
        daily.note.metadata[META_AUDIO]?.let { old -> runCatching { notes.deleteAttachment(old) } }
        val dir = File(context.filesDir, "devotional_audio").apply { mkdirs() }
        val m4a = File(dir, "${daily.note.id}.m4a")
        val file = if (AacEncoder.encode(pcm, m4a)) m4a else File(dir, "${daily.note.id}.wav").also { Wav.write(pcm, it) }

        val attachment = notes.addAttachment(
            Attachment(
                id = NoteRepository.newId("att"), noteId = daily.note.id, kind = AttachmentKind.AUDIO, path = file.path,
                mimeType = if (file.extension == "m4a") "audio/mp4" else "audio/wav", sizeBytes = file.length(),
                durationMs = pcm.durationMs, caption = "Listen: ${daily.devotional.title}", createdAt = System.currentTimeMillis()
            )
        )
        val fresh = notes.getNote(daily.note.id) ?: return file
        notes.updateNote(fresh.copy(metadata = fresh.metadata + (META_AUDIO to attachment.id) + (META_AUDIO_VOICE to label) +
            (META_AUDIO_MARKS to com.example.ai.voice.VoiceSection.encode(marks))))
        return file
    }

    /** A few seconds in the chosen voice, for the settings sheet's "Hear it". */
    suspend fun preview(settings: VoiceSettings): File? {
        val segments = listOf(SpeechSegment(SpeechSegment.Kind.INTRO, "Grace and peace to you. This is how I'll read your devotional each morning.", 0))
        val (pcm, _) = synthesize(segments, settings) {} ?: return null
        val file = File(context.cacheDir, "voice_preview_${settings.voiceName}_${settings.rate}.wav")
        Wav.write(pcm, file)
        return file
    }

    /** The saved recording of [daily], if there is one and its file still exists. */
    suspend fun audioOf(daily: DailyDevotional): File? {
        val id = daily.note.metadata[META_AUDIO] ?: return null
        return notes.getAttachment(id)?.path?.let(::File)?.takeIf { it.exists() }
    }
}

/** Records a devotional's voice in the background, so leaving the screen doesn't stop it. */
class DevotionalVoiceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = DevotionalRepository(applicationContext)
        val day = inputData.getString(KEY_DAY)?.let { LocalDay(it) } ?: LocalDay.today()
        val daily = repo.find(day) ?: return Result.failure()
        val file = runCatching {
            DevotionalVoice(applicationContext).record(daily) { setProgressAsync(workDataOf(KEY_PROGRESS to it)) }
        }.getOrNull()
        return if (file != null) Result.success() else if (runAttemptCount < 1) Result.retry() else Result.failure()
    }

    companion object {
        const val KEY_DAY = "day"
        const val KEY_PROGRESS = "progress"
        const val UNIQUE = "devotional-voice"

        fun enqueue(context: Context, day: LocalDay = LocalDay.today()) {
            val request = OneTimeWorkRequestBuilder<DevotionalVoiceWorker>().setInputData(workDataOf(KEY_DAY to day.iso)).build()
            runCatching { WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, request) }
        }

        fun observe(context: Context) = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UNIQUE)
    }
}
