package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.circles2.CircleSettings
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesFailure
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesRepository
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesResult
import com.craftflowtechnologies.meetingmind.core.circles2.Comment
import com.craftflowtechnologies.meetingmind.core.circles2.CreatePostRequest
import com.craftflowtechnologies.meetingmind.core.circles2.DataState
import com.craftflowtechnologies.meetingmind.core.circles2.Invite
import com.craftflowtechnologies.meetingmind.core.circles2.InviteCodes
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.PendingPost
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.PostUpdate
import com.craftflowtechnologies.meetingmind.core.circles2.ReactionKind
import com.craftflowtechnologies.meetingmind.core.circles2.Role
import com.craftflowtechnologies.meetingmind.core.circles2.valueOr
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A post as the feed shows it: [mine] is true even for my anonymous posts (known only on this phone). */
@Immutable
data class FeedPost(val post: Post, val mine: Boolean, val prayedByMe: Boolean)

@Immutable
data class CircleHomeUiState(
    val loading: Boolean = true,
    val circle: Circle? = null,
    val me: Member? = null,
    val members: List<Member> = emptyList(),
    val posts: List<FeedPost> = emptyList(),
    val pending: List<PendingPost> = emptyList(),
    /** Ids of pending requests I sent from this phone (so I can see "waiting for approval" without knowing who wrote what). */
    val myPendingCount: Int = 0,
    val failure: CirclesFailure? = null,
    /** I'm no longer in this circle (removed, left on another phone, or it was closed). */
    val gone: Boolean = false
) {
    val isAdmin: Boolean get() = me?.role?.isAdmin == true
    val myUid: String? get() = me?.uid
}

@Immutable
data class ComposerState(
    val type: PostType? = null,
    val body: String = "",
    val verse: String = "",
    val anonymous: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
    /** Set when "Turn into testimony" starts a draft from an answered request. */
    val fromAnswered: Boolean = false
) {
    val open: Boolean get() = type != null
}

@Immutable
data class ThreadUiState(
    val postId: String,
    val comments: List<Comment> = emptyList(),
    val updates: List<PostUpdate> = emptyList(),
    val loading: Boolean = true,
    val failure: CirclesFailure? = null
)

@Immutable
data class InviteUiState(val busy: Boolean = false, val invite: Invite? = null, val shareText: String? = null, val error: String? = null)

/**
 * The Feed, Members and Settings tabs of one circle. The Chat tab has its own view model.
 * Writes go through [CirclesRepository]; the anonymity flag only ever travels as a boolean.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CircleHomeViewModel(val circleId: String, private val repo: CirclesRepository) : ViewModel() {
    private val myUid = MutableStateFlow<String?>(null)
    private val prayed = MutableStateFlow(repo.store.prayedIds(circleId))
    private val mine = MutableStateFlow(repo.store.myPostIds(circleId))
    private val sentPending = MutableStateFlow(0)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** One-line notices for a snackbar. */
    val messages: SharedFlow<String> = _messages

    val composer = MutableStateFlow(ComposerState())
    val invite = MutableStateFlow(InviteUiState())

    init { viewModelScope.launch { myUid.value = repo.uid().okOrNull } }

    private val circleState = repo.data.observeCircle(circleId)
    private val membersState = repo.data.observeMembers(circleId)
    private val postsState = repo.data.observePosts(circleId)

    private val me: Flow<Member?> = combine(membersState, myUid) { m, uid -> m.valueOr(emptyList()).firstOrNull { it.uid == uid } }

    private val pendingState: Flow<List<PendingPost>> = me.flatMapLatest { m ->
        if (m?.role?.isAdmin == true) repo.data.observePending(circleId).map { it.valueOr(emptyList()) } else flowOf(emptyList())
    }

    val state: StateFlow<CircleHomeUiState> = combine(
        circleState, membersState, postsState, pendingState, combine(myUid, prayed, mine, sentPending) { u, p, m, s -> Quad(u, p, m, s) }
    ) { c, ms, ps, pend, q ->
        val members = ms.valueOr(emptyList())
        val circle = (c as? DataState.Ready)?.value
        val failure = listOf(c, ms, ps).filterIsInstance<DataState.Failed>().firstOrNull()?.failure
        val removed = (c is DataState.Ready && c.value == null) || failure?.code == "permission_denied"
        CircleHomeUiState(
            loading = c is DataState.Loading && circle == null,
            circle = circle,
            me = members.firstOrNull { it.uid == q.uid },
            members = members,
            posts = ps.valueOr(emptyList()).map { FeedPost(it, mine = (q.uid != null && it.authorUid == q.uid) || it.id in q.mine, prayedByMe = it.id in q.prayed) },
            pending = pend,
            myPendingCount = q.pending,
            failure = if (removed) null else failure,
            gone = removed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CircleHomeUiState())

    private data class Quad(val uid: String?, val prayed: Set<String>, val mine: Set<String>, val pending: Int)

    private fun say(text: String) { _messages.tryEmit(text) }
    private fun fail(f: CirclesFailure) = say(f.message)

    // ---------- composer ----------
    fun openComposer(type: PostType) {
        val circle = state.value.circle
        if (circle != null && type !in circle.settings.allowedTypes) return
        composer.value = ComposerState(type = type)
    }
    fun closeComposer() { composer.value = ComposerState() }
    fun setBody(v: String) = composer.update { it.copy(body = v.take(4000), error = null) }
    fun setVerse(v: String) = composer.update { it.copy(verse = v.take(64), error = null) }
    /** Only prayer requests can be anonymous; for any other type the flag is forced off. */
    fun setAnonymous(v: Boolean) = composer.update { it.copy(anonymous = v && it.type == PostType.Prayer) }

    /** "Turn into testimony" after Answered: a testimony draft that starts from the request (blank for anonymous ones). */
    fun startTestimonyFrom(post: Post) {
        val circle = state.value.circle ?: return
        if (PostType.Testimony !in circle.settings.allowedTypes) return
        // An anonymous request's words are never copied into a named testimony: the person writes it fresh.
        val body = if (post.anonymous) "" else "God answered this prayer:\n\n${post.body}\n\n"
        composer.value = ComposerState(type = PostType.Testimony, body = body, fromAnswered = true)
    }

    fun submitPost() {
        val c = composer.value
        val type = c.type ?: return
        if (c.sending) return
        val body = c.body.trim()
        if (body.isEmpty()) { composer.update { it.copy(error = "Write something first.") }; return }
        composer.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            // No uid, no name: the Worker takes the caller from the token and, for an anonymous post, never stores it on the post.
            val req = CreatePostRequest(circleId, type, body, c.verse.trim().ifBlank { null }, anonymous = c.anonymous && type == PostType.Prayer)
            repo.createPost(req).fold(
                onOk = { created ->
                    mine.value = repo.store.myPostIds(circleId)
                    if (created.pending) sentPending.update { it + 1 }
                    composer.value = ComposerState()
                    say(if (created.pending) "Sent to the admins. It will appear once they approve it." else "Posted.")
                },
                onErr = { f -> composer.update { it.copy(sending = false, error = f.message) } }
            )
        }
    }

    // ---------- feed actions ----------
    fun pray(post: Post) {
        if (post.id in prayed.value) return
        prayed.update { it + post.id }
        viewModelScope.launch {
            repo.prayed(circleId, post.id).fold(onOk = { }, onErr = { f -> prayed.update { it - post.id }; fail(f) })
        }
    }

    fun react(post: Post, kind: ReactionKind?) {
        viewModelScope.launch { repo.api.react(circleId, post.id, kind).failureOrNull?.let(::fail) }
    }

    fun markAnswered(post: Post, onDone: (Post) -> Unit = {}) {
        viewModelScope.launch {
            repo.api.markAnswered(circleId, post.id).fold(onOk = { say("Marked answered. Thank God!"); onDone(post) }, onErr = ::fail)
        }
    }

    /** A named, answered request: tell the chat, cheered on by my companion. The Worker refuses anonymous ones. */
    fun celebrateAnswered(post: Post, companionId: String?) {
        if (post.anonymous) return
        viewModelScope.launch {
            repo.api.celebrate(circleId, com.craftflowtechnologies.meetingmind.core.circles2.CelebrationKind.Answered, "", companionId?.takeIf { it in com.craftflowtechnologies.meetingmind.core.circles2.COMPANION_IDS }, post.id)
                .fold(onOk = { say("Shared in the chat.") }, onErr = ::fail)
        }
    }

    fun addUpdate(post: Post, text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        viewModelScope.launch { repo.api.addUpdate(circleId, post.id, t).fold(onOk = { say("Update posted.") }, onErr = ::fail) }
    }

    fun editPost(post: Post, text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        viewModelScope.launch { repo.api.editPost(circleId, post.id, t).fold(onOk = { say("Saved.") }, onErr = ::fail) }
    }

    fun deletePost(post: Post) {
        viewModelScope.launch { repo.api.deletePost(circleId, post.id).fold(onOk = { say("Removed.") }, onErr = ::fail) }
    }

    fun report(post: Post, reason: String) {
        viewModelScope.launch { repo.api.report(circleId, post.id, reason.trim()).fold(onOk = { say("Reported. The admins will take a look.") }, onErr = ::fail) }
    }

    // ---------- comments ----------
    private val openThreadId = MutableStateFlow<String?>(null)
    val thread: StateFlow<ThreadUiState?> = openThreadId.flatMapLatest<String?, ThreadUiState?> { id ->
        if (id == null) flowOf(null)
        else combine(repo.data.observeComments(circleId, id), repo.data.observeUpdates(circleId, id)) { c, u ->
            val failure = listOf(c, u).filterIsInstance<DataState.Failed>().firstOrNull()?.failure
            ThreadUiState(id, c.valueOr(emptyList()), u.valueOr(emptyList()), loading = c is DataState.Loading, failure = failure)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openThread(postId: String) { openThreadId.value = postId }
    fun closeThread() { openThreadId.value = null }

    fun comment(postId: String, text: String, parentId: String? = null) {
        val t = text.trim(); if (t.isEmpty()) return
        val name = state.value.me?.displayName ?: repo.displayName.ifBlank { "Member" }
        viewModelScope.launch {
            val uid = when (val u = repo.uid()) {
                is CirclesResult.Ok -> u.value
                is CirclesResult.Err -> { fail(u.failure); return@launch }
            }
            when (val r = repo.data.addComment(circleId, postId, uid, name, t.take(2000), parentId)) {
                is CirclesResult.Ok -> repo.api.syncCounts(circleId, postId) // keeps the comment counter honest; failure is harmless
                is CirclesResult.Err -> fail(r.failure)
            }
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        viewModelScope.launch { repo.data.deleteComment(circleId, postId, commentId).failureOrNull?.let(::fail) }
    }

    // ---------- admin: pending ----------
    fun approve(p: PendingPost) { viewModelScope.launch { repo.api.approvePost(circleId, p.id).fold(onOk = { say("Shared with the circle.") }, onErr = ::fail) } }
    fun reject(p: PendingPost) { viewModelScope.launch { repo.api.rejectPost(circleId, p.id).fold(onOk = { say("Not shared.") }, onErr = ::fail) } }

    // ---------- members ----------
    fun setRole(m: Member, role: Role) { viewModelScope.launch { repo.api.setRole(circleId, m.uid, role).fold(onOk = { say("${m.displayName} is now ${role.label.lowercase()}.") }, onErr = ::fail) } }
    fun remove(m: Member) { viewModelScope.launch { repo.api.removeMember(circleId, m.uid).fold(onOk = { say("${m.displayName} was removed.") }, onErr = ::fail) } }

    // ---------- settings ----------
    fun saveSettings(name: String, vocab: String, settings: CircleSettings) {
        val c = state.value.circle ?: return
        viewModelScope.launch {
            repo.api.updateCircle(circleId, name.trim().takeIf { it != c.name }, vocab.trim().takeIf { it != c.vocab }, settings)
                .fold(onOk = { say("Saved.") }, onErr = ::fail)
        }
    }
    fun setMuted(muted: Boolean) { viewModelScope.launch { repo.api.setMute(circleId, muted).fold(onOk = { say(if (muted) "Muted. No notifications from this circle." else "Notifications on.") }, onErr = ::fail) } }
    fun leave(onLeft: () -> Unit) {
        viewModelScope.launch {
            repo.leave(circleId).fold(onOk = { onLeft() }, onErr = ::fail)
        }
    }

    // ---------- invites ----------
    fun createInvite() {
        val c = state.value.circle ?: return
        invite.value = InviteUiState(busy = true)
        viewModelScope.launch {
            repo.api.createInvite(circleId).fold(
                onOk = { inv -> invite.value = InviteUiState(invite = inv, shareText = InviteCodes.shareText(c.name, c.groupWord, inv.code)) },
                onErr = { f -> invite.value = InviteUiState(error = f.message) }
            )
        }
    }
    fun closeInvite() { invite.value = InviteUiState() }

    class Factory(private val circleId: String, private val repo: CirclesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CircleHomeViewModel(circleId, repo) as T
    }
}
