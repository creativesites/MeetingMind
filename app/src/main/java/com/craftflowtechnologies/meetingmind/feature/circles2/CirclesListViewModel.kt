package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.circles2.CircleCovers
import com.craftflowtechnologies.meetingmind.core.circles2.CircleSettings
import com.craftflowtechnologies.meetingmind.core.circles2.CoverChoice
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesFailure
import com.craftflowtechnologies.meetingmind.core.circles2.FailureKind
import com.craftflowtechnologies.meetingmind.core.circles2.InvitePreview
import com.craftflowtechnologies.meetingmind.core.circles2.CircleTemplate
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesListState
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesRepository
import com.craftflowtechnologies.meetingmind.core.circles2.InviteCodes
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.ReactionKind
import com.craftflowtechnologies.meetingmind.core.circles2.WhoCanInvite
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.craftflowtechnologies.meetingmind.core.circles2.fold

/** The Join sheet: paste anything, see the code it contains, add a name, join. */
@Immutable
data class CircleStreakUi(val days: Int = 0, val checkedInToday: Boolean = false)

data class JoinUiState(
    val input: String = "",
    val name: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    /** The request is with an admin. Shown instead of opening the circle. */
    val pending: Boolean = false,
    /** What the code leads to, once the Worker has said. Null while unknown; never blocks Join. */
    val preview: InvitePreview? = null,
    /** A friendly line when the preview could not be shown. Null for quiet failures. */
    val previewError: String? = null
) {
    /** The code found in [input], shown back to the person so they can see what will be used. */
    val code: String? get() = InviteCodes.extract(input)
    val canJoin: Boolean get() = code != null && !busy
}

/** A short wait after the last keystroke before looking up a pasted code. */
internal const val PREVIEW_DEBOUNCE_MS = 400L

/** A friendly line for a failed preview. Null means say nothing: Join still works and shows its own errors. */
internal fun previewErrorLine(f: CirclesFailure): String? = when {
    f.kind == FailureKind.RateLimited || f.code == "rate_limited" -> "You're checking codes too fast. Wait a bit and try again."
    f.code == "not_found" -> "We couldn't find a circle for that code. Check it and try again."
    f.code == "expired" -> "That code has expired. Ask for a new one."
    f.code == "revoked" -> "That code was turned off. Ask for a new one."
    f.code == "full" -> "That code has reached its limit. Ask for a new one."
    else -> null
}

/** The space as people read it on the card. */
internal fun spaceLabel(space: String): String = when (space.lowercase()) {
    "work" -> "Work"
    "learning" -> "Learning"
    "general" -> "General"
    else -> "Faith"
}

class CirclesListViewModel(private val repo: CirclesRepository) : ViewModel() {
    val list: StateFlow<CirclesListState> = repo.circles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), if (repo.isConfigured) CirclesListState.Loading else CirclesListState.NotConnected)

    private val _join = MutableStateFlow(JoinUiState(name = repo.displayName))
    val join: StateFlow<JoinUiState> = _join

    private fun currentStreakUi(): CircleStreakUi {
        val now = repo.clock()
        return CircleStreakUi(
            days = repo.store.currentStreak(now),
            checkedInToday = repo.store.checkedInToday(now)
        )
    }

    private val _streak = MutableStateFlow(currentStreakUi())
    val streak: StateFlow<CircleStreakUi> = _streak

    init { viewModelScope.launch { repo.refresh() } }

    fun refreshEngagement() {
        _streak.value = currentStreakUi()
    }

    fun refresh() {
        viewModelScope.launch { repo.refresh() }
        refreshEngagement()
    }

    private var previewJob: Job? = null

    fun resetJoin() {
        previewJob?.cancel()
        _join.value = JoinUiState(name = repo.displayName)
    }

    /**
     * Pasting clears the old preview. When the text holds a code, waits [PREVIEW_DEBOUNCE_MS] after the last change
     * and asks the Worker what the code leads to. The lookup only shows if the code is still the one in the box.
     */
    fun onPaste(text: String) {
        _join.update { it.copy(input = text.take(4000), error = null, preview = null, previewError = null) }
        previewJob?.cancel()
        val code = InviteCodes.extract(text) ?: return
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            repo.previewInvite(code).fold(
                onOk = { p -> _join.update { if (it.code == code) it.copy(preview = p, previewError = null) else it } },
                onErr = { f -> _join.update { if (it.code == code) it.copy(preview = null, previewError = previewErrorLine(f)) else it } }
            )
        }
    }
    fun onJoinName(text: String) = _join.update { it.copy(name = text.take(40), error = null) }

    /** Joins with whatever was pasted. Every failure becomes an inline message. */
    fun join(onJoined: (String) -> Unit) {
        val s = _join.value
        if (s.busy) return
        if (s.code == null) { _join.update { it.copy(error = "Paste the code or the whole invite message. A code looks like GRACE-7K2Q.") }; return }
        _join.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repo.join(s.input, s.name).fold(
                onOk = { outcome ->
                    if (outcome.pending) _join.update { JoinUiState(name = it.name, pending = true) }
                    else { _join.update { JoinUiState(name = it.name) }; onJoined(outcome.circleId) }
                },
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
    val vocab: String = CircleTemplate.forSpace(null).first().vocab.first(),
    val displayName: String = "",
    val types: List<PostType> = CircleTemplate.forSpace(null).first().types,
    val prayerApproval: Boolean = true,
    val whoCanInvite: WhoCanInvite = WhoCanInvite.Admins,
    val spaceTab: String = "work",
    val requestId: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val description: String = "",
    val joinApproval: Boolean = false,
    /** A pack picture the person chose. Null means the picture that fits the type, which follows the template. */
    val coverPack: String? = null,
    /** Their own photo, uploaded once the circle exists. Wins over [coverPack]. */
    val coverPhoto: PickedFile? = null
) {
    val canCreate: Boolean get() = name.isNotBlank() && displayName.isNotBlank() && types.isNotEmpty() && !busy
    /** The pack picture shown when no photo of their own is chosen. */
    val shownCoverPack: String get() = coverPack ?: CircleCovers.defaultFor(template.id, template.space)
}

/** What to save as the new circle's cover. Null when it is the type's own picture: that needs no saving. */
internal fun coverToSave(s: CreateUiState): CoverChoice? = when {
    s.coverPhoto != null -> CoverChoice.Photo(s.coverPhoto.bytes, s.coverPhoto.mime, s.coverPhoto.name)
    s.coverPack != null && s.coverPack != CircleCovers.defaultFor(s.template.id, s.template.space) -> CoverChoice.Pack(s.coverPack)
    else -> null
}

private fun normalizeSpaceArg(raw: String?): String? = when (raw?.trim()?.lowercase()) {
    null, "", "{space}" -> null
    "study", "learn", "learning" -> "learning"
    "community", "general" -> "general"
    "work" -> "work"
    "faith" -> "faith"
    else -> null
}

private fun defaultReactionsForSpace(space: String): List<ReactionKind> = when (space) {
    "work" -> listOf(ReactionKind.Ack, ReactionKind.Approved, ReactionKind.Priority, ReactionKind.Reviewing, ReactionKind.Win)
    "learning", "general" -> listOf(ReactionKind.Ack, ReactionKind.Heart, ReactionKind.Celebrate, ReactionKind.Priority)
    else -> listOf(ReactionKind.Praying, ReactionKind.Amen, ReactionKind.Heart, ReactionKind.Celebrate)
}

class CreateCircleViewModel(private val repo: CirclesRepository, initialSpace: String? = null) : ViewModel() {
    private val normalizedSpace = normalizeSpaceArg(initialSpace)
    private val defaultTemplate = normalizedSpace?.let { sp -> CircleTemplate.forSpace(sp).firstOrNull() }
        ?: CircleTemplate.forSpace("work").firstOrNull()
        ?: CircleTemplate.forSpace("faith").first()

    private val _state = MutableStateFlow(
        CreateUiState(
            template = defaultTemplate,
            vocab = defaultTemplate.vocab.first(),
            types = defaultTemplate.types,
            prayerApproval = defaultTemplate.space == "faith",
            spaceTab = defaultTemplate.space,
            displayName = repo.displayName
        )
    )
    val state: StateFlow<CreateUiState> = _state

    fun setSpaceTab(space: String) = _state.update { s ->
        val normalized = normalizeSpaceArg(space) ?: s.spaceTab
        val candidate = CircleTemplate.forSpace(normalized).firstOrNull() ?: s.template
        s.copy(
            spaceTab = normalized,
            template = candidate,
            vocab = candidate.vocab.first(),
            types = candidate.types,
            prayerApproval = if (candidate.space != "faith") false else s.prayerApproval,
            error = null,
            requestId = null
        )
    }

    fun pickTemplate(t: CircleTemplate) = _state.update {
        it.copy(
            template = t,
            vocab = t.vocab.first(),
            types = t.types,
            prayerApproval = if (t.space != "faith") false else it.prayerApproval,
            spaceTab = t.space,
            error = null,
            requestId = null
        )
    }
    fun setName(v: String) = _state.update { it.copy(name = v.take(60), error = null, requestId = null) }
    fun setVocab(v: String) = _state.update { it.copy(vocab = v.take(40), requestId = null) }
    fun setDescription(v: String) = _state.update { it.copy(description = v.take(300), requestId = null) }
    fun setDisplayName(v: String) = _state.update { it.copy(displayName = v.take(40), error = null, requestId = null) }
    fun toggleType(t: PostType) = _state.update { s ->
        val next = if (t in s.types) s.types - t else s.types + t
        s.copy(types = PostType.entries.filter { it in next }, requestId = null)
    }
    fun setApproval(on: Boolean) = _state.update { it.copy(prayerApproval = on, requestId = null) }
    fun setJoinApproval(on: Boolean) = _state.update { it.copy(joinApproval = on, requestId = null) }
    fun setWhoCanInvite(w: WhoCanInvite) = _state.update { it.copy(whoCanInvite = w, requestId = null) }
    fun pickCoverPack(photoId: String) = _state.update { it.copy(coverPack = photoId, coverPhoto = null) }
    fun pickCoverPhoto(file: PickedFile) = _state.update { it.copy(coverPhoto = file) }

    fun create(onCreated: (String) -> Unit) {
        val s = _state.value
        if (s.busy) return
        if (s.name.isBlank()) { _state.update { it.copy(error = "Give your ${s.vocab.ifBlank { "circle" }.lowercase()} a name.") }; return }
        if (s.displayName.isBlank()) { _state.update { it.copy(error = "Add the name people will see.") }; return }
        if (s.types.isEmpty()) { _state.update { it.copy(error = "Choose at least one kind of post.") }; return }
        val requestId = s.requestId ?: java.util.UUID.randomUUID().toString()
        _state.update { it.copy(busy = true, error = null, requestId = requestId) }
        val settings = CircleSettings(
            allowedTypes = s.types,
            whoCanInvite = s.whoCanInvite,
            prayerApproval = if (s.template.space == "faith") s.prayerApproval else false,
            joinApproval = s.joinApproval,
            reactions = defaultReactionsForSpace(s.template.space),
            space = s.template.space
        )
        viewModelScope.launch {
            repo.createCircle(
                name = s.name,
                template = s.template.id,
                vocab = s.vocab.ifBlank { s.template.vocab.first() },
                displayName = s.displayName,
                settings = settings,
                requestId = requestId,
                description = s.description.trim(),
                space = s.template.space
            ).fold(
                onOk = { id ->
                    // The circle exists now. A cover that doesn't save leaves the type's picture, changeable in Settings.
                    coverToSave(s)?.let { repo.setCover(id, it) }
                    _state.update { it.copy(busy = false, requestId = null) }; onCreated(id)
                },
                onErr = { f ->
                    val friendly = if (f.message.contains("unknown template", ignoreCase = true)) {
                        "\"${s.template.label}\" isn't active on the connected Circles server yet. Redeploy server/circles-api to enable Work and Study templates."
                    } else {
                        f.message
                    }
                    _state.update { it.copy(busy = false, error = friendly) }
                }
            )
        }
    }

    class Factory(private val repo: CirclesRepository, private val initialSpace: String? = null) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CreateCircleViewModel(repo, initialSpace) as T
    }
}
