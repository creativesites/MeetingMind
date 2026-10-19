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
import com.craftflowtechnologies.meetingmind.core.circles2.MediaKind
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.MessageKind
import com.craftflowtechnologies.meetingmind.core.circles2.MessageReaction
import com.craftflowtechnologies.meetingmind.core.circles2.PollState
import com.craftflowtechnologies.meetingmind.core.circles2.TypingUser
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.craftflowtechnologies.meetingmind.core.circles2.fold

@Immutable
data class ChatUiState(
    val loading: Boolean = true,
    val messages: List<ChatMessage> = emptyList(),
    val myUid: String? = null,
    val myName: String = "",
    val isAdmin: Boolean = false,
    val canModerate: Boolean = false,
    val unread: Int = 0,
    /** When I last read this chat (epoch ms). Messages after it are new to me. */
    val readAt: Long = 0L,
    val failure: CirclesFailure? = null,
    val replyTo: ChatMessage? = null,
    val editing: ChatMessage? = null,
    /** Members of the circle: names for @mentions, and read receipts from their last-read times. */
    val members: List<Member> = emptyList(),
    /** The message pinned to the top of the chat, if any. */
    val pinned: ChatMessage? = null,
    /** Names of people typing right now (not me). */
    val typing: List<String> = emptyList(),
    /** An upload is in progress. */
    val uploading: Boolean = false,
    /** The message I'm forwarding, while the composer holds its text. */
    val forwarding: ChatMessage? = null,
    val circleId: String = ""
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

    private val uploading = MutableStateFlow(false)
    private val forward = MutableStateFlow<ChatMessage?>(null)
    private var uploadJob: Job? = null
    private var lastTyping = 0L

    private data class Core(val ms: DataState<List<ChatMessage>>, val uid: String?, val read: Long, val reply: ChatMessage?, val edit: ChatMessage?)
    private data class Extras(val members: List<Member>, val pinnedId: String?, val typing: List<TypingUser>, val uploading: Boolean, val forwarding: ChatMessage?)

    private val core: Flow<Core> = combine(repo.data.observeMessages(circleId), myUid, readAt, reply, edit) { ms, uid, read, r, e ->
        Core(ms, uid, read, r, e)
    }
    private val extras: Flow<Extras> = combine(repo.data.observeMembers(circleId), repo.data.observeCircle(circleId), repo.data.observeTyping(circleId), uploading, forward) { members, circle, typing, up, fwd ->
        Extras(members.valueOr(emptyList()), (circle as? DataState.Ready)?.value?.pinnedMessageId, typing.valueOr(emptyList()), up, fwd)
    }
    /** Ticks every two seconds so a typing indicator goes away on its own when its owner stops. */
    private val ticker: Flow<Long> = flow { while (true) { emit(System.currentTimeMillis()); delay(2_000L) } }

    val state: StateFlow<ChatUiState> = combine(core, extras, ticker) { c, x, now ->
        val list = c.ms.valueOr(emptyList())
        val meRole = x.members.firstOrNull { it.uid == c.uid }?.role
        ChatUiState(
            loading = c.ms is DataState.Loading,
            messages = list,
            myUid = c.uid,
            myName = repo.displayName,
            isAdmin = meRole?.isAdmin == true,
            canModerate = meRole?.canModerate == true,
            unread = list.count { it.createdAt > c.read && it.authorUid != c.uid && !it.deleted },
            readAt = c.read,
            failure = (c.ms as? DataState.Failed)?.failure,
            replyTo = c.reply, editing = c.edit,
            circleId = circleId,
            members = x.members,
            pinned = x.pinnedId?.let { id -> list.firstOrNull { it.id == id && !it.deleted } },
            typing = x.typing.filter { it.uid != c.uid && now - it.updatedAt < 10_000L }.map { it.name },
            uploading = x.uploading,
            forwarding = x.forwarding
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    private fun say(text: String) { _messages.tryEmit(text) }
    /** A one-line notice, such as a microphone or file problem. */
    fun notice(text: String) = say(text)
    private fun fail(f: CirclesFailure) = say(f.message)

    /** Called while the Chat tab is on screen: everything up to now counts as read. */
    fun markRead() {
        val latest = state.value.messages.maxOfOrNull { it.createdAt } ?: return
        if (latest > readAt.value) {
            readAt.value = latest; repo.store.setLastRead(circleId, latest)
            // Read receipts: my last-read time on my member document. A failure costs nothing visible.
            val uid = myUid.value ?: return
            viewModelScope.launch { repo.data.markRead(circleId, uid) }
        }
    }

    // ---------- sending ----------
    fun setDraft(v: String) { draft.value = v.take(2000); typingPing(v.isNotBlank()) }

    /** Tells the chat I'm typing, at most every four seconds. Clearing the draft tells it I've stopped. */
    private fun typingPing(on: Boolean) {
        val uid = myUid.value ?: return
        val now = System.currentTimeMillis()
        if (on && now - lastTyping < 4_000L) return
        lastTyping = if (on) now else 0L
        viewModelScope.launch { repo.data.setTyping(circleId, uid, repo.displayName.ifBlank { "Member" }, on) }
    }

    /** Forwarding copies the text into the composer; the new message records where it came from. */
    fun startForward(m: ChatMessage) {
        reply.value = null; edit.value = null
        forward.value = m
        draft.value = (m.card?.text ?: m.text).take(2000)
    }
    fun cancelForward() { forward.value = null; draft.value = "" }

    fun pin(m: ChatMessage) {
        viewModelScope.launch { repo.api.pinMessage(circleId, m.id).fold(onOk = { say("Pinned to the top of the chat.") }, onErr = ::fail) }
    }
    fun unpin() {
        viewModelScope.launch { repo.api.pinMessage(circleId, null).fold(onOk = { say("Unpinned.") }, onErr = ::fail) }
    }

    /**
     * Uids of members named with @Name in [text], matched by display name without regard to case.
     * Capped at 20 entries and 128 characters each, the limits the Worker and rules enforce.
     */
    private fun mentionsIn(text: String, members: List<Member>): List<String> =
        members.filter { m -> m.displayName.isNotBlank() && text.contains("@" + m.displayName, ignoreCase = true) }
            .map { it.uid }
            .filter { it.length <= 128 }
            .take(20)

    /**
     * Sends a photo, video, voice note or file. The file is uploaded through the Worker and the Worker writes the
     * message with a deterministic [requestId] so retries never duplicate the message.
     */
    fun sendAttachment(kind: MediaKind, file: PickedFile) {
        if (uploading.value) { say("One upload at a time."); return }
        val caption = draft.value.trim()
        val replyTo = reply.value?.id
        val requestId = java.util.UUID.randomUUID().toString()
        uploading.value = true
        uploadJob = viewModelScope.launch {
            try {
                when (val up = repo.uploadAttachment(circleId, kind, file.bytes, file.mime, file.name, file.durationMs)) {
                    is CirclesResult.Err -> fail(up.failure)
                    is CirclesResult.Ok -> when (val sent = repo.api.sendMessage(circleId, kind, up.value.path, caption, replyTo, file.durationMs, file.name, requestId)) {
                        is CirclesResult.Ok -> { draft.value = ""; reply.value = null; typingPing(false) }
                        is CirclesResult.Err -> fail(sent.failure)
                    }
                }
            } finally {
                uploading.value = false
            }
        }
    }

    fun cancelUpload() {
        uploadJob?.cancel(); uploadJob = null
        uploading.value = false
        say("Upload cancelled.")
    }
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
        val forwarded = forward.value
        draft.value = ""; edit.value = null; reply.value = null; forward.value = null
        typingPing(false)
        val mentions = mentionsIn(text, state.value.members)
        viewModelScope.launch {
            val result = if (editing != null) repo.data.editMessage(circleId, editing.id, text)
            else {
                val uid = (repo.uid() as? CirclesResult.Ok)?.value ?: run { restore(text, replyTo, editing); forward.value = forwarded; fail(CirclesFailure.signedOut); return@launch }
                val toSend = when {
                    replyTo != null -> MessageDraft.Reply(text, replyTo.id, mentions)
                    forwarded != null -> MessageDraft.Forward(text, forwarded.id, mentions)
                    else -> MessageDraft.Text(text, mentions)
                }
                repo.data.sendMessage(circleId, uid, repo.displayName.ifBlank { "Member" }, toSend)
            }
            if (result is CirclesResult.Err) { restore(text, replyTo, editing); forward.value = forwarded; fail(result.failure) }
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
        val mediaPath = m.attachment?.path
        viewModelScope.launch {
            repo.data.deleteMessage(circleId, m.id).fold(
                onOk = {
                    if (mediaPath != null) {
                        repo.api.deleteMedia(circleId, mediaPath)
                    }
                },
                onErr = ::fail
            )
        }
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
