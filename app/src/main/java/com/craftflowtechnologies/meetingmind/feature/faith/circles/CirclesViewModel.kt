package com.craftflowtechnologies.meetingmind.feature.faith.circles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.model.Circle
import com.craftflowtechnologies.meetingmind.core.model.CircleMember
import com.craftflowtechnologies.meetingmind.core.model.CirclePrayer
import com.craftflowtechnologies.meetingmind.core.model.CircleSermon
import com.craftflowtechnologies.meetingmind.core.model.CircleTestimony
import com.craftflowtechnologies.meetingmind.core.repository.CircleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CirclesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CircleRepository(application)

    val circles: StateFlow<List<Circle>> = repository.observeCircles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observeCircle(circleId: String): StateFlow<Circle?> =
        repository.observeCircle(circleId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun observeMembers(circleId: String): StateFlow<List<CircleMember>> =
        repository.observeMembers(circleId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observePrayers(circleId: String): StateFlow<List<CirclePrayer>> =
        repository.observePrayers(circleId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observeTestimonies(circleId: String): StateFlow<List<CircleTestimony>> =
        repository.observeTestimonies(circleId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observeSermons(circleId: String): StateFlow<List<CircleSermon>> =
        repository.observeSermons(circleId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createCircle(
        name: String,
        description: String = "",
        myDisplayName: String = "Me",
        avatarEmoji: String = "🕊️",
        onCreated: (Circle) -> Unit = {}
    ) {
        viewModelScope.launch {
            val created = repository.createCircle(name, description, myDisplayName, avatarEmoji)
            onCreated(created)
        }
    }

    fun joinCircle(
        inviteUriOrCode: String,
        myDisplayName: String = "Me",
        onJoined: (Circle?) -> Unit = {}
    ) {
        viewModelScope.launch {
            val circle = repository.joinCircle(inviteUriOrCode, myDisplayName)
            onJoined(circle)
        }
    }

    fun postPrayer(
        circleId: String,
        requestText: String,
        isUrgent: Boolean = false,
        authorName: String = "Me"
    ) {
        viewModelScope.launch {
            repository.postPrayer(circleId, requestText, isUrgent, authorName)
        }
    }

    fun prayFor(prayerId: String) {
        viewModelScope.launch {
            repository.prayFor(prayerId)
        }
    }

    fun markPrayerAnswered(prayerId: String) {
        viewModelScope.launch {
            repository.markPrayerAnswered(prayerId)
        }
    }

    fun postTestimony(
        circleId: String,
        title: String,
        storyText: String,
        scriptureRef: String? = null,
        prayerRequestId: String? = null,
        authorName: String = "Me"
    ) {
        viewModelScope.launch {
            repository.postTestimony(
                circleId = circleId,
                title = title,
                storyText = storyText,
                scriptureRef = scriptureRef,
                prayerRequestId = prayerRequestId,
                authorName = authorName
            )
        }
    }

    fun praiseTestimony(testimonyId: String) {
        viewModelScope.launch {
            repository.praiseTestimony(testimonyId)
        }
    }

    fun shareSermon(
        circleId: String,
        title: String,
        preacher: String? = null,
        scripturePassage: String? = null,
        sermonDate: String? = null,
        discussionGuideJson: String = "",
        transcriptSummary: String = "",
        audioDurationSec: Long = 0L
    ) {
        viewModelScope.launch {
            repository.shareSermon(
                circleId = circleId,
                title = title,
                preacher = preacher,
                scripturePassage = scripturePassage,
                sermonDate = sermonDate,
                discussionGuideJson = discussionGuideJson,
                transcriptSummary = transcriptSummary,
                audioDurationSec = audioDurationSec
            )
        }
    }

    fun leaveCircle(circleId: String) {
        viewModelScope.launch {
            repository.leaveCircle(circleId)
        }
    }
}
