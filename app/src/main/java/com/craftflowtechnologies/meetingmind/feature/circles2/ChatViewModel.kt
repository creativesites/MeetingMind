package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.circles2.COMPANION_IDS
import com.craftflowtechnologies.meetingmind.core.circles2.CardPayload
import com.craftflowtechnologies.meetingmind.core.circles2.CelebrationKind
import com.craftflowtechnologies.meetingmind.core.circles2.ChainState
import com.craftflowtechnologies.meetingmind.core.circles2.ChatMessage
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesFailure
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesRepository
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesResult
import com.craftflowtechnologies.meetingmind.core.circles2.DataState
import com.craftflowtechnologies.meetingmind.core.circles2.MessageDraft
import com.craftflowtechnologies.meetingmind.core.circles2.MessageKind
import com.craftflowtechnologies.meetingmind.core.circles2.MessageReaction
import com.craftflowtechnologies.meetingmind.core.circles2.PollState
import com.craftflowtechnologies.meetingmind.core.circles2.valueOr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ChatUiState(
    val loading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val myUid: String? = null,
    val myName: String = "",
    val isAdmin: Boolean = false,
    val unread: Int = 0,
    val failure: CirclesFailure? = null,
    val replyTo: ChatMessage? = null,
    val editing: ChatMessage? = null
) {
    fun byId(id: String?): ChatMessage? = id?.let { i -> messages.firstOrNull { it.id == i } }
}

@Immutable
data class PollDraft(val question: String = "", val options: List<String> = listOf("", ""), val multi: Boolean = false, val busy: Boolean = false, val error: String? = null) {
    val cleanOptions: List<String> get() = options.map { it.trim() }.filter { it.isNotEmpty() }
    val canCreate: Boolean get() = question.isNotBlank() && cleanOptions.size >= 2 && !busy
}

/** Chat, polls, shared cards, celebrations and prayer chains for one circle. */
class ChatViewModel(val circleId: String, private val repo: CirclesRepository) : ViewModel() {
    private val myUid = MutableStateFlow<String?>(null)
    private val readAt = MutableStateFlow(repo.store.lastRead(circleId))
    private val reply = MutableStateFlow<ChatMessage?>(null)
    private val edit = MutableStateFlow<ChatMessage?>(null)

    val draft = MutableStateFlow("")
    val pollDraft = MutableStateFlow<PollDraft?>(null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices: SharedFlow<String> = _messages

    init { viewModelScope.launch { myUid.value = repo.uid().okOrNull } }

    val state: StateFlow<ChatUiState> = combine(
        repo.data.observeMessages(circleId), myUid, readAt, reply, edit
    ) { ms, uid, read, r, e ->
        val list = ms.valueOr(emptyList())
        ChatUiState(
            loading = ms is DataState.Loading,
            messages = list,
            myUid = uid,
            myName = repo.displayName,
            unread = list.count { it.createdAt > read && it.authorUid != uid && !it.deleted },
            failure = (ms as? DataState.Failed)?.failure,
            replyTo = r, editing = e
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    private fun say(text: String) { _messages.tryEmit(text) }
    private fun fail(f: CirclesFailure) = say(f.message)

    /** Called while the Chat tab is on screen: everything up to now counts as read. */
    fun markRead() {
        val latest = state.value.messages.maxOfOrNull { it.createdAt } ?: return
        if (latest > readAt.value) { readAt.value = latest; repo.store.setLastRead(circleId, latest) }
    }

    // ---------- sending ----------
    fun setDraft(v: String) { draft.value = v.take(2000) }
    fun startReply(m: ChatMessage) { edit.value = null; reply.value = m }
    fun cancelReply() { reply.value = null }
    fun startEdit(m: ChatMessage) {
        if (m.kind != MessageKind.Text && m.kind != MessageKind.Reply) return
        reply.value = null; edit.value = m; draft.value = m.text
    }
    fun cancelEdit() { edit.value = null; draft.value = "" }

    fun send() {
        val text = draft.value.trim()
        if (text.isEmpty()) return
        val editing = edit.value
        val replyTo = reply.value
        draft.value = ""; edit.value = null; reply.value = null
        viewModelScope.launch {
            val result = if (editing != null) repo.data.editMessage(circleId, editing.id, text)
            else {
                val uid = (repo.uid() as? CirclesResult.Ok)?.value ?: run { restore(text, replyTo, editing); fail(CirclesFailure.signedOut); return@launch }
                repo.data.sendMessage(circleId, uid, repo.displayName.ifBlank { "Member" }, if (replyTo != null) MessageDraft.Reply(text, replyTo.id) else MessageDraft.Text(text))
            }
            if (result is CirclesResult.Err) { restore(text, replyTo, editing); fail(result.failure) }
        }
    }

    /** A failed send puts the words back so nothing typed is lost. */
    private fun restore(text: String, replyTo: ChatMessage?, editing: ChatMessage?) {
        if (draft.value.isEmpty()) draft.value = text
        reply.value = replyTo; edit.value = editing
    }

    fun shareCard(card: CardPayload) {
        val text = card.text.trim(); if (text.isEmpty()) return
        viewModelScope.launch {
            val uid = (repo.uid() as? CirclesResult.Ok)?.value ?: return@launch fail(CirclesFailure.signedOut)
            repo.data.sendMessage(circleId, uid, repo.displayName.ifBlank { "Member" }, MessageDraft.Card(card.copy(text = text.take(600)))).failureOrNull?.let(::fail)
        }
    }

    fun delete(m: ChatMessage) {
        viewModelScope.launch { repo.data.deleteMessage(circleId, m.id).fold(onOk = { }, onErr = ::fail) }
    }

    fun reportMessage(m: ChatMessage, reason: String = "") {
        viewModelScope.launch { repo.api.reportMessage(circleId, m.id, reason).fold(onOk = { say("Reported. The admins will take a look.") }, onErr = ::fail) }
    }

    // ---------- reactions ----------
    fun reactionsFor(messageId: String): Flow<List<MessageReaction>> = flow {
        val uid = (repo.uid() as? CirclesResult.Ok)?.value
        if (uid == null) emit(emptyList()) else emitAll(repo.data.observeMessageReactions(circleId, messageId, uid))
    }

    /** Tap a reaction: sets it, or removes it if it's already mine. */
    fun toggleReaction(messageId: String, emoji: String, current: List<MessageReaction>) {
        val mine = current.firstOrNull { it.mine }?.emoji
        viewModelScope.launch {
            val uid = (repo.uid() as? CirclesResult.Ok)?.value ?: return@launch fail(CirclesFailure.signedOut)
            repo.data.setMessageReaction(circleId, messageId, uid, if (mine == emoji) null else emoji).failureOrNull?.let(::fail)
        }
    }

    // ---------- polls ----------
    fun pollFor(pollId: String): Flow<DataState<PollState>> = flow {
        val uid = (repo.uid() as? CirclesResult.Ok)?.value
        if (uid == null) emit(DataState.Failed(CirclesFailure.signedOut)) else emitAll(repo.data.observePoll(circleId, pollId, uid))
    }

    fun openPollDraft() { pollDraft.value = PollDraft() }
    fun closePollDraft() { pollDraft.value = null }
    fun setPollQuestion(v: String) = pollDraft.update { it?.copy(question = v.take(200), error = null) }
    fun setPollOption(i: Int, v: String) = pollDraft.update { d -> d?.copy(options = d.options.mapIndexed { j, o -> if (i == j) v.take(80) else o }, error = null) }
    fun addPollOption() = pollDraft.update { d -> if (d != null && d.options.size < 6) d.copy(options = d.options + "") else d }
    fun removePollOption(i: Int) = pollDraft.update { d -> if (d != null && d.options.size > 2) d.copy(options = d.options.filterIndexed { j, _ -> j != i }) else d }
    fun setPollMulti(v: Boolean) = pollDraft.update { it?.copy(multi = v) }

    fun createPoll() {
        val d = pollDraft.value ?: return
        if (!d.canCreate) { pollDraft.update { it?.copy(error = "Add a question and at least two different options.") }; return }
        pollDraft.update { it?.copy(busy = true, error = null) }
        viewModelScope.launch {
            repo.api.createPoll(circleId, d.question.trim(), d.cleanOptions, d.multi).fold(
                onOk = { pollDraft.value = null },
                onErr = { f -> pollDraft.update { it?.copy(busy = false, error = f.message) } }
            )
        }
    }

    /** Taps an option. Single choice replaces the vote; multi toggles; tapping my only choice again removes the vote. */
    fun vote(state: PollState, optionId: String) {
        if (state.poll.closed) return
        val mine = state.mine
        val next = when {
            state.poll.multi -> if (optionId in mine) mine - optionId else mine + optionId
            optionId in mine -> emptyList()
            else -> listOf(optionId)
        }
        viewModelScope.launch {
            val uid = (repo.uid() as? CirclesResult.Ok)?.value ?: return@launch fail(CirclesFailure.signedOut)
            repo.data.vote(circleId, state.poll.id, uid, next).failureOrNull?.let(::fail)
        }
    }

    fun closePoll(pollId: String) { viewModelScope.launch { repo.api.closePoll(circleId, pollId).fold(onOk = { say("Poll closed.") }, onErr = ::fail) } }

    // ---------- prayer chain ----------
    fun chainFor(chainId: String): Flow<DataState<ChainState>> = flow {
        val uid = (repo.uid() as? CirclesResult.Ok)?.value
        if (uid == null) emit(DataState.Failed(CirclesFailure.signedOut)) else emitAll(repo.data.observeChain(circleId, chainId, uid))
    }

    fun startChain(title: String, postId: String? = null) {
        val t = title.trim(); if (t.isEmpty()) return
        viewModelScope.launch { repo.api.startChain(circleId, t, postId).fold(onOk = { say("Prayer chain started.") }, onErr = ::fail) }
    }

    fun claimSlot(chainId: String, hour: Int) {
        viewModelScope.launch {
            val uid = (repo.uid() as? CirclesResult.Ok)?.value ?: return@launch fail(CirclesFailure.signedOut)
            repo.data.claimSlot(circleId, chainId, uid, repo.displayName.ifBlank { "Member" }, hour).fold(onOk = { say("Hour ${hourLabel(hour)} is yours.") }, onErr = ::fail)
        }
    }

    fun releaseSlot(chainId: String, hour: Int) {
        viewModelScope.launch { repo.data.releaseSlot(circleId, chainId, hour).failureOrNull?.let(::fail) }
    }

    // ---------- celebrations ----------
    /** Celebrate something about myself, cheered on by my companion. */
    fun celebrate(kind: CelebrationKind, text: String, companionId: String?, postId: String? = null) {
        val id = companionId?.takeIf { it in COMPANION_IDS }
        viewModelScope.launch { repo.api.celebrate(circleId, kind, text.trim(), id, postId).fold(onOk = { }, onErr = ::fail) }
    }

    class Factory(private val circleId: String, private val repo: CirclesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(circleId, repo) as T
    }
}

fun hourLabel(hour: Int): String = "%d:00".format(hour)
