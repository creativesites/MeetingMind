package com.craftflowtechnologies.meetingmind.feature.today.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesFailure
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesListState
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Every state the Today home's Circles tile can be in. The tile always shows one of them. */
sealed interface HomeCircles {
    /** Waiting for the first answer from the Worker. A quiet placeholder, never a blank. */
    data object Loading : HomeCircles

    /** This build has no Circles service. */
    data object NotConnected : HomeCircles

    /** Nothing loaded, and the reason is one plain sentence. The tile offers a retry. */
    data class Failed(val failure: CirclesFailure) : HomeCircles

    /** Signed in, and in no circle yet. */
    data object Empty : HomeCircles

    /** The person's circles, in the order the repository gives them. [note] is a failure after some loaded. */
    data class Loaded(val circles: List<Circle>, val note: CirclesFailure? = null) : HomeCircles

    companion object {
        /** How many circles the tile lists before the person opens Circles. */
        const val SHOWN = 3

        /**
         * Maps what the repository knows to what the tile shows. [refreshed] is true once the first
         * Worker answer has come back, so "no circles" is never shown before we know.
         */
        fun from(list: CirclesListState, refreshed: Boolean): HomeCircles = when (list) {
            CirclesListState.NotConnected -> NotConnected
            CirclesListState.Loading -> Loading
            is CirclesListState.Ready -> when {
                list.circles.isNotEmpty() -> Loaded(list.circles, list.note)
                list.note != null -> Failed(list.note)
                !refreshed -> Loading
                else -> Empty
            }
        }
    }
}

/** Feeds the Today home's Circles tile. Loads the circles on creation; [retry] asks again. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeCirclesViewModel(private val repo: CirclesRepository) : ViewModel() {
    private val attempts = MutableStateFlow(0)
    private val refreshed = MutableStateFlow(false)

    /** A new attempt starts a fresh listener, so a listener that failed for good is tried again. */
    val state: StateFlow<HomeCircles> = combine(attempts.flatMapLatest { repo.circles() }, refreshed) { list, done ->
        HomeCircles.from(list, done)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeCircles.Loading)

    init { load() }

    fun retry() {
        refreshed.value = false
        attempts.update { it + 1 }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                // Activates push only for someone already in a circle: opening Home is not opting in.
                repo.refresh(activatePush = false)
            } finally {
                refreshed.value = true
            }
        }
    }

    class Factory(private val repo: CirclesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeCirclesViewModel(repo) as T
    }
}
