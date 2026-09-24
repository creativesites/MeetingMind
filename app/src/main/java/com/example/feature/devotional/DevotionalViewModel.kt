package com.example.feature.devotional

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.core.devotional.DailyDevotional
import com.example.core.devotional.DevotionalProfile
import com.example.core.devotional.DevotionalRepository
import com.example.core.devotional.DevotionalScheduler
import com.example.core.devotional.LiturgicalCalendar
import com.example.core.devotional.LiturgicalDay
import com.example.core.devotional.LocalDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DevotionalUiState(
    val loading: Boolean = true,
    val today: DailyDevotional? = null,
    val profile: DevotionalProfile = DevotionalProfile(),
    val writing: Boolean = false,
    /** Why the last on-demand write failed, for a "Try again" card. */
    val writeError: String? = null,
    /** Every devotional of the day, oldest first, when there's more than one. */
    val all: List<DailyDevotional> = emptyList(),
    /** Who can write a new one right now. */
    val writers: Set<com.example.ai.devotional.DevotionalWriter> = setOf(com.example.ai.devotional.DevotionalWriter.AUTO, com.example.ai.devotional.DevotionalWriter.CLASSIC),
    val season: LiturgicalDay? = null,
    val date: LocalDate = LocalDate.now(),
    val voice: VoiceUi = VoiceUi()
)

/** The Listen button's state. */
data class VoiceUi(
    /** Recording the voice version now; [progress] 0–1. */
    val preparing: Boolean = false,
    val progress: Float = 0f,
    val hasAudio: Boolean = false,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val voiceLabel: String? = null,
    val failed: Boolean = false,
    /** Sections recorded, with their start times — for jumping and for "pray it aloud". */
    val marks: List<Pair<com.example.ai.voice.VoiceSection, Long>> = emptyList(),
    /** The section playing now. */
    val current: com.example.ai.voice.VoiceSection? = null
)

class DevotionalViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DevotionalRepository(app)
    private val day = LocalDay.today()
    private val requested = MutableStateFlow(false)

    private val writingFlow = runCatching { DevotionalScheduler.observeWriting(app) }.getOrNull()
        ?.map { infos ->
            val busy = infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            if (!busy) requested.value = false
            busy to (if (busy) null else DevotionalScheduler.lastError(app))
        }
        ?.catch { emit(false to null) } ?: flowOf(false to null)

    private val voiceWork = runCatching { com.example.core.devotional.DevotionalVoiceWorker.observe(app) }.getOrNull()
        ?.map { infos ->
            val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            Triple(running != null, running?.progress?.getFloat(com.example.core.devotional.DevotionalVoiceWorker.KEY_PROGRESS, 0f) ?: 0f,
                infos.any { it.state == WorkInfo.State.FAILED })
        }?.catch { emit(Triple(false, 0f, false)) } ?: flowOf(Triple(false, 0f, false))

    /** The devotional the person picked from the day's list; null follows the day's current one. */
    private val selected = MutableStateFlow<String?>(null)
    private val errorDismissed = MutableStateFlow<String?>(null)
    private val writers = MutableStateFlow(setOf(com.example.ai.devotional.DevotionalWriter.AUTO, com.example.ai.devotional.DevotionalWriter.CLASSIC))

    private val shown = combine(repo.observeDay(day), selected) { all, pick ->
        val current = all.lastOrNull { it.note.metadata[com.example.core.devotional.DevotionalNotes.META_KEY] == com.example.core.devotional.DevotionalNotes.key(day) }
        all to (all.firstOrNull { it.note.id == pick } ?: current ?: all.lastOrNull())
    }

    private val base = combine(shown, repo.profile, writingFlow, requested, combine(errorDismissed, writers) { d, w -> d to w }) { (all, today), profile, (writing, error), asked, (dismissed, available) ->
        DevotionalUiState(
            loading = false,
            today = today,
            profile = profile,
            writing = writing || (asked && today == null),
            writeError = error.takeIf { !writing && !asked && it != dismissed },
            all = all,
            writers = available,
            season = LiturgicalCalendar.dayOf(day.date, profile.tradition),
            date = day.date
        )
    }

    val state: StateFlow<DevotionalUiState> = combine(base, voiceWork, com.example.core.audio.PlaybackController.state) { s, (preparing, progress, failed), playback ->
        val id = s.today?.note?.id?.let { com.example.core.devotional.DevotionalVoice.playbackId(it) }
        val mine = id != null && playback.recordingId == id
        s.copy(voice = VoiceUi(
            preparing = preparing, progress = progress,
            hasAudio = s.today?.note?.metadata?.get(com.example.core.devotional.DevotionalVoice.META_AUDIO) != null,
            playing = mine && playback.phase == com.example.core.audio.PlaybackPhase.PLAYING,
            positionMs = if (mine) playback.positionMs else 0, durationMs = if (mine) playback.durationMs else 0,
            voiceLabel = s.today?.note?.metadata?.get(com.example.core.devotional.DevotionalVoice.META_AUDIO_VOICE),
            failed = failed && !preparing,
            marks = com.example.ai.voice.VoiceSection.decode(s.today?.note?.metadata?.get(com.example.core.devotional.DevotionalVoice.META_AUDIO_MARKS)),
            current = if (mine) com.example.ai.voice.VoiceSection.decode(s.today?.note?.metadata?.get(com.example.core.devotional.DevotionalVoice.META_AUDIO_MARKS))
                .lastOrNull { it.second <= playback.positionMs }?.first else null
        ))
    }.catch { emit(DevotionalUiState(loading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevotionalUiState())

    /** Opening the page is asking for today's devotional: write it if it isn't there yet. */
    fun ensureToday() {
        viewModelScope.launch {
            if (repo.find(day) == null) { requested.value = true; DevotionalScheduler.writeNow(getApplication()) }
        }
    }

    private var playWhenReady = false
    private var pendingSection: com.example.ai.voice.VoiceSection? = null
    private var pendingOnly = false

    /**
     * Plays today's devotional aloud — from [section] if given, and with [only] just that section
     * (the prayer, prayed on its own). Records the voice first if needed; tapping again pauses.
     */
    fun listen(section: com.example.ai.voice.VoiceSection? = null, only: Boolean = false) = viewModelScope.launch {
        val s = state.value
        val today = s.today ?: run { pendingSection = section; pendingOnly = only; playWhenReady = true; return@launch }
        if (s.voice.playing && section == null) { com.example.core.audio.PlaybackController.pause(); return@launch }
        val file = com.example.core.devotional.DevotionalVoice(getApplication()).audioOf(today)
        if (file == null) {
            playWhenReady = true; pendingSection = section; pendingOnly = only
            com.example.core.devotional.DevotionalVoiceWorker.enqueue(getApplication(), day)
            return@launch
        }
        val id = com.example.core.devotional.DevotionalVoice.playbackId(today.note.id)
        val range = section?.let { com.example.ai.voice.VoiceSection.range(s.voice.marks, it) }
        if (range != null) com.example.core.audio.PlaybackController.playRange(getApplication(), id, today.devotional.title, file, range.first, if (only) range.second else null)
        else com.example.core.audio.PlaybackController.play(getApplication(), id, today.devotional.title, file)
    }

    init {
        // When a requested recording lands (or today's devotional arrives), start it.
        viewModelScope.launch {
            state.collect { s ->
                if (playWhenReady && s.today != null && s.voice.hasAudio && !s.voice.preparing) {
                    playWhenReady = false
                    listen(pendingSection, pendingOnly)
                } else if (playWhenReady && s.today != null && !s.voice.hasAudio && !s.voice.preparing && !s.voice.failed) {
                    // Arrived without a voice yet: record it.
                    com.example.core.devotional.DevotionalVoiceWorker.enqueue(getApplication(), day)
                }
            }
        }
    }

    private val paths = mutableMapOf<String, String?>()

    /** The devotional's recorded voice, if any (resolved in the background, cached). */
    fun audioPath(t: DailyDevotional): String? = attachmentPath(t, com.example.core.devotional.DevotionalVoice.META_AUDIO)
    fun coverPath(t: DailyDevotional): String? = attachmentPath(t, com.example.core.repository.NoteRepository.COVER_KEY)
        ?: com.example.core.share.BackgroundLibrary.forDay(getApplication(), day.date.toEpochDay())?.file?.path

    private fun attachmentPath(t: DailyDevotional, key: String): String? {
        val id = t.note.metadata[key] ?: return null
        return t.document.attachments.firstOrNull { it.id == id }?.path?.takeIf { java.io.File(it).exists() }
    }

    /**
     * A new devotional for today, by the writer the person chose. It's added to the day — the one
     * being read is kept — and shown once written. Nothing is ever written without being asked,
     * except the day's first.
     */
    fun rewrite(ask: com.example.ai.devotional.DevotionalAsk? = null) {
        requested.value = true
        selected.value = null
        errorDismissed.value = null
        DevotionalScheduler.writeNow(getApplication(), replace = true, ask = ask)
    }

    /** Shows one of the day's devotionals. */
    fun select(daily: DailyDevotional) { selected.value = daily.note.id }

    /** Makes the one being read the day's devotional (for stories, widgets and Home). */
    fun makeCurrent(daily: DailyDevotional) = viewModelScope.launch { runCatching { repo.makeCurrent(daily) }; selected.value = null }

    fun dismissError() { errorDismissed.value = state.value.writeError }

    fun refreshWriters() = viewModelScope.launch { writers.value = runCatching { repo.writersAvailable() }.getOrDefault(writers.value) }

    fun opened(daily: DailyDevotional) = viewModelScope.launch {
        runCatching { repo.markOpened(daily) }
        // Written offline earlier? Paint the picture now that we may be online.
        val profile = state.value.profile
        if (profile.autoImage && daily.devotional.origin != com.example.core.devotional.DevotionalOrigin.CARE) runCatching { repo.paint(daily, profile) }
    }

    fun feedback(daily: DailyDevotional, value: String?) = viewModelScope.launch { repo.setFeedback(daily, value) }

    fun saveResponse(daily: DailyDevotional, text: String) = viewModelScope.launch { repo.saveResponse(daily, text) }

    /** Saving settings shapes tomorrow's devotional; a new one today is always the person's call. */
    fun saveProfile(profile: DevotionalProfile) = viewModelScope.launch { repo.setProfile(profile) }
}
