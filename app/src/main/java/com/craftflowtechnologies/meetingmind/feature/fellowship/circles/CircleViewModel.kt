package com.craftflowtechnologies.meetingmind.feature.fellowship.circles

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.model.Circle
import com.craftflowtechnologies.meetingmind.core.model.CircleMember
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayer
import com.craftflowtechnologies.meetingmind.core.model.CircleSermon
import com.craftflowtechnologies.meetingmind.core.model.CircleTestimony
import com.craftflowtechnologies.meetingmind.core.repository.CircleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CircleViewModel(
    application: Application,
    private val repository: CircleRepository = CircleRepository(application)
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("fellowship_circles", Context.MODE_PRIVATE)

    private val _cachedDisplayName = MutableStateFlow(prefs.getString("display_name", "").orEmpty())
    val cachedDisplayName: StateFlow<String> = _cachedDisplayName.asStateFlow()

    fun saveDisplayName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) {
            prefs.edit().putString("display_name", trimmed).apply()
            _cachedDisplayName.value = trimmed
        }
    }

    val circles: StateFlow<List<Circle>> = repository.observeCircles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun circle(id: String): StateFlow<Circle?> = repository.observeCircle(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun members(id: String): StateFlow<List<CircleMember>> = repository.observeMembers(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun prayers(id: String): StateFlow<List<CirclePrayer>> = repository.observePrayers(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun testimonies(id: String): StateFlow<List<CircleTestimony>> = repository.observeTestimonies(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sermons(id: String): StateFlow<List<CircleSermon>> = repository.observeSermons(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createCircle(
        name: String,
        description: String = "",
        displayName: String,
        emoji: String = "🕊️",
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            saveDisplayName(displayName)
            val created = repository.createCircle(name, description, displayName, emoji)
            onCreated(created.id)
        }
    }

    fun joinCircle(
        inviteUriOrCode: String,
        displayName: String,
        onJoined: (String?) -> Unit
    ) {
        viewModelScope.launch {
            saveDisplayName(displayName)
            val circle = repository.joinCircle(inviteUriOrCode, displayName)
            onJoined(circle?.id)
        }
    }

    fun postPrayer(
        circleId: String,
        requestText: String,
        isUrgent: Boolean,
        displayName: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            saveDisplayName(displayName)
            repository.postPrayer(circleId, requestText, isUrgent, displayName)
            onDone()
        }
    }

    fun prayFor(circleId: String, prayerId: String) {
        viewModelScope.launch {
            repository.prayFor(circleId, prayerId)
        }
    }

    fun markPrayerAnswered(circleId: String, prayerId: String) {
        viewModelScope.launch {
            repository.markPrayerAnswered(circleId, prayerId)
        }
    }

    fun postTestimony(
        circleId: String,
        title: String,
        storyText: String,
        scriptureRef: String? = null,
        prayerRequestId: String? = null,
        displayName: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            saveDisplayName(displayName)
            repository.postTestimony(circleId, title, storyText, scriptureRef, prayerRequestId, displayName)
            onDone()
        }
    }

    fun praiseTestimony(circleId: String, testimonyId: String) {
        viewModelScope.launch {
            repository.praiseTestimony(circleId, testimonyId)
        }
    }

    fun shareSermon(
        circleId: String,
        title: String,
        preacher: String?,
        scripturePassage: String?,
        sermonDate: String?,
        discussionGuideJson: String,
        transcriptSummary: String,
        audioDurationSec: Long,
        displayName: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            saveDisplayName(displayName)
            repository.shareSermon(
                circleId, title, preacher, scripturePassage, sermonDate,
                discussionGuideJson, transcriptSummary, audioDurationSec, displayName
            )
            onDone()
        }
    }

    fun leaveCircle(circleId: String, onLeft: () -> Unit) {
        viewModelScope.launch {
            repository.leaveCircle(circleId)
            onLeft()
        }
    }
}
