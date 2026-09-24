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
    val failed: Boolean = false
)

class DevotionalViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DevotionalRepository(app)
    private val day = LocalDay.today()
    private val requested = MutableStateFlow(false)

    private val writingFlow = runCatching { DevotionalScheduler.observeWriting(app) }.getOrNull()
        ?.map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        ?.catch { emit(false) } ?: flowOf(false)

    private val voiceWork = runCatching { com.example.core.devotional.DevotionalVoiceWorker.observe(app) }.getOrNull()
        ?.map { infos ->
            val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            Triple(running != null, running?.progress?.getFloat(com.example.core.devotional.DevotionalVoiceWorker.KEY_PROGRESS, 0f) ?: 0f,
                infos.any { it.state == WorkInfo.State.FAILED })
        }?.catch { emit(Triple(false, 0f, false)) } ?: flowOf(Triple(false, 0f, false))

    private val base = combine(repo.observe(day), repo.profile, writingFlow, requested) { today, profile, writing, asked ->
        DevotionalUiState(
            loading = false,
            today = today,
            profile = profile,
            writing = writing || (asked && today == null),
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
            failed = failed && !preparing
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

    /** Plays today's devotional aloud, recording the voice first if needed; tapping again pauses. */
    fun listen() = viewModelScope.launch {
        val s = state.value
        val today = s.today ?: return@launch
        if (s.voice.playing) { com.example.core.audio.PlaybackController.pause(); return@launch }
        val file = com.example.core.devotional.DevotionalVoice(getApplication()).audioOf(today)
        if (file != null) {
            com.example.core.audio.PlaybackController.play(getApplication(), com.example.core.devotional.DevotionalVoice.playbackId(today.note.id), today.devotional.title, file)
        } else {
            playWhenReady = true
            com.example.core.devotional.DevotionalVoiceWorker.enqueue(getApplication(), day)
        }
    }

    init {
        // When a requested recording lands, start it.
        viewModelScope.launch {
            state.collect { s ->
                if (playWhenReady && s.voice.hasAudio && !s.voice.preparing) {
                    playWhenReady = false
                    listen()
                }
            }
        }
    }

    fun rewrite() {
        requested.value = true
        DevotionalScheduler.writeNow(getApplication(), replace = true)
    }

    fun opened(daily: DailyDevotional) = viewModelScope.launch { runCatching { repo.markOpened(daily) } }

    fun feedback(daily: DailyDevotional, value: String?) = viewModelScope.launch { repo.setFeedback(daily, value) }

    fun saveResponse(daily: DailyDevotional, text: String) = viewModelScope.launch { repo.saveResponse(daily, text) }

    fun saveProfile(profile: DevotionalProfile, rewriteToday: Boolean) = viewModelScope.launch {
        repo.setProfile(profile)
        if (rewriteToday) rewrite()
    }
}
