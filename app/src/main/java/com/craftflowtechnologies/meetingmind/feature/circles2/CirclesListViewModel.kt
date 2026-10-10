package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.circles2.CircleSettings
import com.craftflowtechnologies.meetingmind.core.circles2.CircleTemplate
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesListState
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesRepository
import com.craftflowtechnologies.meetingmind.core.circles2.InviteCodes
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.WhoCanInvite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Join sheet: paste anything, see the code it contains, add a name, join. */
data class JoinUiState(
    val input: String = "",
    val name: String = "",
    val busy: Boolean = false,
    val error: String? = null
) {
    /** The code found in [input], shown back to the person so they can see what will be used. */
    val code: String? get() = InviteCodes.extract(input)
    val canJoin: Boolean get() = code != null && !busy
}

class CirclesListViewModel(private val repo: CirclesRepository) : ViewModel() {
    val list: StateFlow<CirclesListState> = repo.circles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), if (repo.isConfigured) CirclesListState.Loading else CirclesListState.NotConnected)

    private val _join = MutableStateFlow(JoinUiState(name = repo.displayName))
    val join: StateFlow<JoinUiState> = _join

    init { viewModelScope.launch { repo.refresh() } }

    fun refresh() { viewModelScope.launch { repo.refresh() } }

    fun resetJoin() { _join.value = JoinUiState(name = repo.displayName) }
    fun onPaste(text: String) = _join.update { it.copy(input = text.take(4000), error = null) }
    fun onJoinName(text: String) = _join.update { it.copy(name = text.take(40), error = null) }

    /** Joins with whatever was pasted. Every failure becomes an inline message. */
    fun join(onJoined: (String) -> Unit) {
        val s = _join.value
        if (s.busy) return
        if (s.code == null) { _join.update { it.copy(error = "Paste the code or the whole invite message. A code looks like GRACE-7K2Q.") }; return }
        _join.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repo.join(s.input, s.name).fold(
                onOk = { id -> _join.update { JoinUiState(name = it.name) }; onJoined(id) },
                onErr = { f -> _join.update { it.copy(busy = false, error = f.message) } }
            )
        }
    }

    class Factory(private val repo: CirclesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CirclesListViewModel(repo) as T
    }
}

data class CreateUiState(
    val template: CircleTemplate = CircleTemplate.all.first(),
    val name: String = "",
    val vocab: String = CircleTemplate.all.first().vocab.first(),
    val displayName: String = "",
    val types: List<PostType> = CircleTemplate.all.first().types,
    val prayerApproval: Boolean = true,
    val whoCanInvite: WhoCanInvite = WhoCanInvite.Admins,
    val busy: Boolean = false,
    val error: String? = null
) {
    val canCreate: Boolean get() = name.isNotBlank() && displayName.isNotBlank() && types.isNotEmpty() && !busy
}

class CreateCircleViewModel(private val repo: CirclesRepository) : ViewModel() {
    private val _state = MutableStateFlow(CreateUiState(displayName = repo.displayName))
    val state: StateFlow<CreateUiState> = _state

    fun pickTemplate(t: CircleTemplate) = _state.update { it.copy(template = t, vocab = t.vocab.first(), types = t.types, error = null) }
    fun setName(v: String) = _state.update { it.copy(name = v.take(60), error = null) }
    fun setVocab(v: String) = _state.update { it.copy(vocab = v.take(40)) }
    fun setDisplayName(v: String) = _state.update { it.copy(displayName = v.take(40), error = null) }
    fun toggleType(t: PostType) = _state.update { s ->
        val next = if (t in s.types) s.types - t else s.types + t
        s.copy(types = PostType.entries.filter { it in next })
    }
    fun setApproval(on: Boolean) = _state.update { it.copy(prayerApproval = on) }
    fun setWhoCanInvite(w: WhoCanInvite) = _state.update { it.copy(whoCanInvite = w) }

    fun create(onCreated: (String) -> Unit) {
        val s = _state.value
        if (s.busy) return
        if (s.name.isBlank()) { _state.update { it.copy(error = "Give your ${s.vocab.ifBlank { "circle" }.lowercase()} a name.") }; return }
        if (s.displayName.isBlank()) { _state.update { it.copy(error = "Add the name people will see.") }; return }
        if (s.types.isEmpty()) { _state.update { it.copy(error = "Choose at least one kind of post.") }; return }
        _state.update { it.copy(busy = true, error = null) }
        val settings = CircleSettings(allowedTypes = s.types, whoCanInvite = s.whoCanInvite, prayerApproval = s.prayerApproval)
        viewModelScope.launch {
            repo.createCircle(s.name, s.template.id, s.vocab.ifBlank { s.template.vocab.first() }, s.displayName, settings).fold(
                onOk = { id -> _state.update { it.copy(busy = false) }; onCreated(id) },
                onErr = { f -> _state.update { it.copy(busy = false, error = f.message) } }
            )
        }
    }

    class Factory(private val repo: CirclesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CreateCircleViewModel(repo) as T
    }
}
