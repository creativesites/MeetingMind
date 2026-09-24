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
    val date: LocalDate = LocalDate.now()
)

class DevotionalViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DevotionalRepository(app)
    private val day = LocalDay.today()
    private val requested = MutableStateFlow(false)

    private val writingFlow = runCatching { DevotionalScheduler.observeWriting(app) }.getOrNull()
        ?.map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        ?.catch { emit(false) } ?: flowOf(false)

    val state: StateFlow<DevotionalUiState> = combine(repo.observe(day), repo.profile, writingFlow, requested) { today, profile, writing, asked ->
        DevotionalUiState(
            loading = false,
            today = today,
            profile = profile,
            writing = writing || (asked && today == null),
            season = LiturgicalCalendar.dayOf(day.date, profile.tradition),
            date = day.date
        )
    }.catch { emit(DevotionalUiState(loading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevotionalUiState())

    /** Opening the page is asking for today's devotional: write it if it isn't there yet. */
    fun ensureToday() {
        viewModelScope.launch {
            if (repo.find(day) == null) { requested.value = true; DevotionalScheduler.writeNow(getApplication()) }
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
