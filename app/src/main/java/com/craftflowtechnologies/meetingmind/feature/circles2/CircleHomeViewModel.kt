package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.craftflowtechnologies.meetingmind.core.circles2.ChecklistItem
import com.craftflowtechnologies.meetingmind.core.circles2.CircleEvent
import com.craftflowtechnologies.meetingmind.core.circles2.CircleSettings
import com.craftflowtechnologies.meetingmind.core.circles2.ComposerDraft
import com.craftflowtechnologies.meetingmind.core.circles2.EventDraft
import com.craftflowtechnologies.meetingmind.core.circles2.JoinRequest
import com.craftflowtechnologies.meetingmind.core.circles2.Rsvp
import com.craftflowtechnologies.meetingmind.core.circles2.RsvpAnswer
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesFailure
import com.craftflowtechnologies.meetingmind.core.circles2.FailureKind
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesRepository
import com.craftflowtechnologies.meetingmind.core.circles2.CirclesResult
import com.craftflowtechnologies.meetingmind.core.circles2.Comment
import com.craftflowtechnologies.meetingmind.core.circles2.CreatePostRequest
import com.craftflowtechnologies.meetingmind.core.circles2.DataState
import com.craftflowtechnologies.meetingmind.core.circles2.Invite
import com.craftflowtechnologies.meetingmind.core.circles2.MediaAttachment
import com.craftflowtechnologies.meetingmind.core.circles2.MediaKind
import com.craftflowtechnologies.meetingmind.core.circles2.InviteCodes
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.PendingPost
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.circles2.PostMeta
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.PostUpdate
import com.craftflowtechnologies.meetingmind.core.circles2.ReactionKind
import com.craftflowtechnologies.meetingmind.core.circles2.Role
import com.craftflowtechnologies.meetingmind.core.circles2.WorkStatus
import com.craftflowtechnologies.meetingmind.core.circles2.valueOr
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import com.craftflowtechnologies.meetingmind.core.circles2.fold

/** A post as the feed shows it: [mine] is true even for my anonymous posts (known only on this phone). */
@Immutable
data class FeedPost(
    val post: Post,
    val mine: Boolean,
    val prayedByMe: Boolean,
    val myReaction: ReactionKind? = null,
    val saved: Boolean = false
)

@Immutable
data class CircleHomeUiState(
    val loading: Boolean = true,
    val circle: Circle? = null,
    val me: Member? = null,
    val members: List<Member> = emptyList(),
    val posts: List<FeedPost> = emptyList(),
    val pending: List<PendingPost> = emptyList(),
    /** People waiting to be let in (admins only; empty for everyone else). */
    val joinRequests: List<JoinRequest> = emptyList(),
    /** Upcoming and past events, soonest first. Cancelled ones are kept so people can see them. */
    val events: List<CircleEvent> = emptyList(),
    /** The post pinned to the top of the feed, if any. */
    val pinnedPostId: String? = null,
    /** Ids of pending requests I sent from this phone (so I can see "waiting for approval" without knowing who wrote what). */
    val myPendingCount: Int = 0,
    val failure: CirclesFailure? = null,
    /** I'm no longer in this circle (removed, left on another phone, or it was closed). */
    val gone: Boolean = false,
    val unreadFeedCount: Int = 0,
    val savedCount: Int = 0,
    val streakDays: Int = 0,
    val checkedInToday: Boolean = false,
    val promptDismissedToday: Boolean = false
) {
    val isAdmin: Boolean get() = me?.role?.isAdmin == true
    val canModerate: Boolean get() = me?.role?.canModerate == true
    val myUid: String? get() = me?.uid
}

@Immutable
data class ComposerState(
    val type: PostType? = null,
    val body: String = "",
    val verse: String = "",
    val anonymous: Boolean = false,
    /** Photos already uploaded for this post. */
    val attachments: List<MediaAttachment> = emptyList(),
    val sending: Boolean = false,
    val uploading: Boolean = false,
    val error: String? = null,
    /** Set when "Turn into testimony" starts a draft from an answered request. */
    val fromAnswered: Boolean = false,
    /** True when reopened from a locally auto-saved draft. */
    val draftRestored: Boolean = false,
    val meta: PostMeta? = null
) {
    val open: Boolean get() = type != null
    val hasText: Boolean get() = body.isNotBlank() || verse.isNotBlank()
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
data class InviteUiState(
    val busy: Boolean = false,
    val invite: Invite? = null,
    val shareText: String? = null,
    val error: String? = null,
    val invitedEmail: String? = null
)

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
    val savedDraft = MutableStateFlow(repo.store.composerDraft(circleId)?.takeIf { it.hasContent() })
    val invite = MutableStateFlow(InviteUiState())
    val myReactions = MutableStateFlow<Map<String, ReactionKind>>(emptyMap())

    private fun loadHabitInfo(): HabitInfo {
        val now = repo.clock()
        val today = now / 86_400_000L
        return HabitInfo(
            savedIds = repo.store.savedPostIds(circleId),
            lastVisit = repo.store.lastFeedVisit(circleId),
            streakDays = repo.store.currentStreak(now),
            checkedInToday = repo.store.checkedInToday(now),
            promptDismissedToday = repo.store.dismissedPromptDay(circleId) == today
        )
    }

    private val habitInfo = MutableStateFlow(loadHabitInfo())

    private fun refreshHabits() {
        habitInfo.value = loadHabitInfo()
    }

    init { viewModelScope.launch { myUid.value = repo.uid().okOrNull } }

    private val circleState = repo.data.observeCircle(circleId)
    private val membersState = repo.data.observeMembers(circleId)
    private val postsState = repo.data.observePosts(circleId)

    private val me: Flow<Member?> = combine(membersState, myUid) { m, uid -> m.valueOr(emptyList()).firstOrNull { it.uid == uid } }

    private val eventsState = repo.data.observeEvents(circleId)

    /** Admin-only reads. Non-admins never subscribe, so the rules' refusals never reach them. */
    private val adminData: Flow<AdminData> = me.flatMapLatest { m ->
        if (m?.role?.isAdmin == true) {
            combine(repo.data.observePending(circleId), repo.data.observeJoinRequests(circleId)) { p, j ->
                AdminData(p.valueOr(emptyList()), j.valueOr(emptyList()))
            }
        } else flowOf(AdminData(emptyList(), emptyList()))
    }

    private val extras: Flow<Extras> = combine(eventsState, adminData) { e, a -> Extras(e.valueOr(emptyList()), a) }

    private val rawUserData: Flow<RawUser> = combine(myUid, prayed, mine, sentPending, myReactions) { u, p, m, s, r -> RawUser(u, p, m, s, r) }
    private val userData: Flow<UserData> = combine(rawUserData, habitInfo) { r, h -> UserData(r, h) }

    val state: StateFlow<CircleHomeUiState> = combine(
        circleState, membersState, postsState, extras, userData
    ) { c, ms, ps, ex, q ->
        val members = ms.valueOr(emptyList())
        val circle = (c as? DataState.Ready)?.value
        val failure = listOf(c, ms, ps).filterIsInstance<DataState.Failed>().firstOrNull()?.failure
        val removed = (c is DataState.Ready && c.value == null) || failure?.code == "permission_denied"
        val r = q.raw
        val habit = q.habit
        val feedPosts = ps.valueOr(emptyList()).map { post ->
            FeedPost(
                post = post,
                mine = (r.uid != null && post.authorUid == r.uid) || post.id in r.mine,
                prayedByMe = post.id in r.prayed,
                myReaction = r.reactions[post.id],
                saved = post.id in habit.savedIds
            )
        }
        val now = repo.clock()
        val lastVisit = habit.lastVisit
        val unreadCount = when {
            feedPosts.isEmpty() -> 0
            lastVisit > 0L -> feedPosts.count { !it.mine && it.post.createdAt > lastVisit }
            feedPosts.count { it.post.createdAt >= now - 86_400_000L } >= 2 ->
                feedPosts.count { !it.mine && it.post.createdAt >= now - 86_400_000L }
            else -> 0
        }
        CircleHomeUiState(
            loading = c is DataState.Loading && circle == null,
            circle = circle,
            me = members.firstOrNull { it.uid == r.uid },
            members = members,
            posts = feedPosts,
            pending = ex.admin.pending,
            joinRequests = ex.admin.requests,
            events = ex.events,
            pinnedPostId = circle?.pinnedPostId,
            myPendingCount = r.pending,
            failure = if (removed) null else failure,
            gone = removed,
            unreadFeedCount = unreadCount,
            savedCount = feedPosts.count { it.saved },
            streakDays = habit.streakDays,
            checkedInToday = habit.checkedInToday,
            promptDismissedToday = habit.promptDismissedToday
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CircleHomeUiState())

    private data class RawUser(val uid: String?, val prayed: Set<String>, val mine: Set<String>, val pending: Int, val reactions: Map<String, ReactionKind>)
    private data class HabitInfo(val savedIds: Set<String>, val lastVisit: Long, val streakDays: Int, val checkedInToday: Boolean, val promptDismissedToday: Boolean)
    private data class UserData(val raw: RawUser, val habit: HabitInfo)
    private data class AdminData(val pending: List<PendingPost>, val requests: List<JoinRequest>)
    private data class Extras(val events: List<CircleEvent>, val admin: AdminData)

    private fun say(text: String) { _messages.tryEmit(text) }
    private fun fail(f: CirclesFailure) = say(f.message)

    // ---------- composer ----------
    private fun persistDraft(state: ComposerState) {
        val type = state.type ?: return
        val draft = ComposerDraft(
            typeWire = type.wire,
            body = state.body,
            verse = state.verse,
            anonymous = state.anonymous && type == PostType.Prayer,
            fromAnswered = state.fromAnswered,
            updatedAt = System.currentTimeMillis()
        )
        if (draft.hasContent()) {
            repo.store.setComposerDraft(circleId, draft)
            savedDraft.value = draft
        } else {
            repo.store.setComposerDraft(circleId, null)
            savedDraft.value = null
        }
    }

    fun openComposer(type: PostType) {
        val circle = state.value.circle
        // Free-form is always on: it is not one of the circle's listed kinds.
        if (circle != null && type != PostType.Note && type !in circle.settings.allowedTypes) return
        val existingDraft = repo.store.composerDraft(circleId)?.takeIf { it.hasContent() }
        composer.update { current ->
            if (current.open) {
                current.copy(
                    type = type,
                    anonymous = if (type == PostType.Prayer) current.anonymous else false,
                    error = null
                ).also(::persistDraft)
            } else if (existingDraft != null && (existingDraft.typeWire == type.wire || type == PostType.Note)) {
                ComposerState(
                    type = type,
                    body = existingDraft.body,
                    verse = existingDraft.verse,
                    anonymous = existingDraft.anonymous && type == PostType.Prayer,
                    fromAnswered = existingDraft.fromAnswered,
                    draftRestored = true
                )
            } else {
                ComposerState(type = type)
            }
        }
    }

    fun openWorkComposer(type: PostType, initialBody: String = "", initialMeta: PostMeta? = null) {
        val circle = state.value.circle
        val targetType = if (circle != null && type != PostType.Note && type !in circle.settings.allowedTypes) {
            PostType.Note
        } else {
            type
        }
        composer.value = ComposerState(
            type = targetType,
            body = initialBody.take(4000),
            meta = initialMeta
        )
    }

    fun resumeSavedDraft() {
        val existingDraft = repo.store.composerDraft(circleId)?.takeIf { it.hasContent() } ?: return
        val type = PostType.entries.firstOrNull { it.wire == existingDraft.typeWire } ?: PostType.Note
        composer.value = ComposerState(
            type = type,
            body = existingDraft.body,
            verse = existingDraft.verse,
            anonymous = existingDraft.anonymous && type == PostType.Prayer,
            fromAnswered = existingDraft.fromAnswered,
            draftRestored = true
        )
    }

    /** Auto-saves any typed words instead of wiping them on Back, outside tap, or close icon. */
    fun closeComposer() {
        val current = composer.value
        val hadText = current.hasText
        persistDraft(current)
        composer.value = ComposerState()
        if (hadText) say("Draft saved — your words won't be lost.")
    }

    fun discardDraft() {
        repo.store.setComposerDraft(circleId, null)
        savedDraft.value = null
        composer.value = ComposerState()
    }

    /** A photo, video or document for the post being written. Opens a free-form composer when none is open. */
    fun addPhoto(file: PickedFile) {
        if (!composer.value.open) openComposer(PostType.Note)
        if (composer.value.attachments.size >= 4) {
            composer.update { it.copy(error = "A post can have at most 4 attachments.") }
            return
        }
        viewModelScope.launch {
            val kind = when {
                file.mime.startsWith("video/") -> MediaKind.Video
                file.mime == "application/pdf" || file.name.orEmpty().endsWith(".pdf", ignoreCase = true) || file.mime.startsWith("application/") -> MediaKind.File
                else -> MediaKind.Image
            }
            composer.update { it.copy(uploading = true, error = null) }
            try {
                repo.uploadAttachment(circleId, kind, file.bytes, file.mime, file.name, file.durationMs).fold(
                    onOk = { a -> composer.update { it.copy(attachments = it.attachments + a, uploading = false, error = null) } },
                    onErr = { f -> composer.update { it.copy(uploading = false, error = f.message) } }
                )
            } finally {
                composer.update { it.copy(uploading = false) }
            }
        }
    }
    fun removePhoto(a: MediaAttachment) { composer.update { it.copy(attachments = it.attachments.filter { x -> x.path != a.path }) } }

    /** Sets my profile photo. The Worker writes the new version onto my member doc in every circle I'm in. */
    fun setProfilePhoto(file: PickedFile) {
        viewModelScope.launch {
            val jpeg = withContext(Dispatchers.Default) { avatarJpeg(file.bytes) }
            if (jpeg == null) { fail(CirclesFailure(FailureKind.Rejected, "That photo couldn't be read. Try another.", "bad_image")); return@launch }
            repo.setProfilePhoto(jpeg).fold(onOk = { say("Profile photo updated.") }, onErr = ::fail)
        }
    }

    fun removeProfilePhoto() {
        viewModelScope.launch { repo.removeProfilePhoto().fold(onOk = { say("Profile photo removed.") }, onErr = ::fail) }
    }

    /** The cover photo for the circle's hero. Admins only; the Worker checks it. */
    fun setCover(file: PickedFile) {
        viewModelScope.launch {
            repo.uploadAttachment(circleId, MediaKind.Image, file.bytes, file.mime, file.name, null).fold(
                onOk = { a ->
                    repo.api.updateCircle(circleId, null, null, null, null, coverPath = a.path).fold(onOk = { say("Cover photo saved.") }, onErr = ::fail)
                },
                onErr = ::fail
            )
        }
    }
    fun setBody(v: String) = composer.update { it.copy(body = v.take(4000), error = null).also(::persistDraft) }
    fun setVerse(v: String) = composer.update { it.copy(verse = v.take(64), error = null).also(::persistDraft) }
    /** Only prayer requests can be anonymous; for any other type the flag is forced off. */
    fun setAnonymous(v: Boolean) = composer.update { it.copy(anonymous = v && it.type == PostType.Prayer).also(::persistDraft) }
    fun setMeta(meta: PostMeta?) = composer.update { it.copy(meta = meta, error = null) }

    /** "Turn into testimony" after Answered: a testimony draft that starts from the request (blank for anonymous ones). */
    fun startTestimonyFrom(post: Post) {
        val circle = state.value.circle ?: return
        if (PostType.Testimony !in circle.settings.allowedTypes) return
        // An anonymous request's words are never copied into a named testimony: the person writes it fresh.
        val body = if (post.anonymous) "" else "God answered this prayer:\n\n${post.body}\n\n"
        val next = ComposerState(type = PostType.Testimony, body = body, fromAnswered = true)
        composer.value = next
        persistDraft(next)
    }

    fun submitPost() {
        val c = composer.value
        val type = c.type ?: return
        if (c.sending) return
        val body = c.body.trim()
        if (body.isEmpty() && c.attachments.isEmpty()) { composer.update { it.copy(error = "Write something first.") }; return }
        composer.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            // No uid, no name: the Worker takes the caller from the token and, for an anonymous post, never stores it on the post.
            val effectiveMeta = if (type == PostType.Prayer) null else c.meta?.takeIf { !it.isEmpty }
            val req = CreatePostRequest(
                circleId = circleId,
                type = type,
                body = body,
                verseRef = c.verse.trim().ifBlank { null },
                anonymous = c.anonymous && type == PostType.Prayer,
                attachments = c.attachments,
                meta = effectiveMeta
            )
            repo.createPost(req).fold(
                onOk = { created ->
                    mine.value = repo.store.myPostIds(circleId)
                    if (created.pending) sentPending.update { it + 1 }
                    repo.store.setComposerDraft(circleId, null)
                    savedDraft.value = null
                    composer.value = ComposerState()
                    repo.store.recordActivity(repo.clock())
                    refreshHabits()
                    say(if (created.pending) "Sent to the admins. It will appear once they approve it." else "Posted.")
                },
                onErr = { f -> composer.update { it.copy(sending = false, error = f.message) } }
            )
        }
    }

    // ---------- feed actions ----------
    fun toggleBookmark(post: Post) {
        val added = repo.store.toggleSavedPost(circleId, post.id)
        refreshHabits()
        say(if (added) "Saved for later" else "Removed from saved")
    }

    fun markCatchUpSeen() {
        repo.store.setLastFeedVisit(circleId, repo.clock())
        refreshHabits()
    }

    fun dismissTodayPrompt() {
        repo.store.setDismissedPromptDay(circleId, repo.clock() / 86_400_000L)
        refreshHabits()
    }

    fun pray(post: Post) {
        if (post.id in prayed.value) return
        prayed.update { it + post.id }
        viewModelScope.launch {
            repo.prayed(circleId, post.id).fold(
                onOk = {
                    repo.store.recordActivity(repo.clock())
                    refreshHabits()
                },
                onErr = { f -> prayed.update { it - post.id }; fail(f) }
            )
        }
    }

    fun react(post: Post, kind: ReactionKind?) {
        val current = myReactions.value[post.id]
        val target = if (kind == null || current == kind) null else kind
        myReactions.update { currentMap ->
            if (target == null) currentMap - post.id else currentMap + (post.id to target)
        }
        viewModelScope.launch {
            val res = repo.api.react(circleId, post.id, target)
            val err = res.failureOrNull
            if (err != null) {
                fail(err)
            } else if (target != null) {
                repo.store.recordActivity(repo.clock())
                refreshHabits()
            }
        }
    }

    fun markAnswered(post: Post, onDone: (Post) -> Unit = {}) {
        viewModelScope.launch {
            if (post.type == PostType.Task || post.type == PostType.Blocker) {
                val message = if (post.type == PostType.Blocker) "Marked resolved." else "Marked completed."
                repo.api.setWorkStatus(circleId, post.id, WorkStatus.Done).fold(
                    onOk = {
                        repo.store.recordActivity(repo.clock())
                        refreshHabits()
                        say(message)
                        onDone(post)
                    },
                    onErr = ::fail
                )
            } else {
                repo.api.markAnswered(circleId, post.id).fold(
                    onOk = {
                        repo.store.recordActivity(repo.clock())
                        refreshHabits()
                        say("Marked answered. Thank God!")
                        onDone(post)
                    },
                    onErr = ::fail
                )
            }
        }
    }

    fun setWorkStatus(post: Post, status: WorkStatus) {
        viewModelScope.launch {
            repo.api.setWorkStatus(circleId, post.id, status).fold(
                onOk = {
                    repo.store.recordActivity(repo.clock())
                    refreshHabits()
                    say("Marked ${status.label.lowercase()}.")
                },
                onErr = ::fail
            )
        }
    }

    fun toggleChecklistItem(post: Post, item: ChecklistItem) {
        viewModelScope.launch {
            repo.toggleChecklistItem(circleId, post.id, item.id, !item.done).fold(
                onOk = {
                    repo.store.recordActivity(repo.clock())
                    refreshHabits()
                },
                onErr = { f -> _messages.emit(f.message) }
            )
        }
    }

    /** A named, answered request: tell the chat, cheered on by my companion. The Worker refuses anonymous ones. */
    fun celebrateAnswered(post: Post, companionId: String?) {
        if (post.anonymous) return
        viewModelScope.launch {
            repo.api.celebrate(circleId, com.craftflowtechnologies.meetingmind.core.circles2.CelebrationKind.Answered, "", companionId?.takeIf { it in com.craftflowtechnologies.meetingmind.core.circles2.COMPANION_IDS }, post.id)
                .fold(onOk = {
                    repo.store.recordActivity(repo.clock())
                    refreshHabits()
                    say("Shared in the chat.")
                }, onErr = ::fail)
        }
    }

    fun addUpdate(post: Post, text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        viewModelScope.launch {
            repo.api.addUpdate(circleId, post.id, t).fold(
                onOk = {
                    repo.store.recordActivity(repo.clock())
                    refreshHabits()
                    say("Update posted.")
                },
                onErr = ::fail
            )
        }
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
    val commentDraft = MutableStateFlow("")
    val thread: StateFlow<ThreadUiState?> = openThreadId.flatMapLatest<String?, ThreadUiState?> { id ->
        if (id == null) flowOf(null)
        else combine(repo.data.observeComments(circleId, id), repo.data.observeUpdates(circleId, id)) { c, u ->
            val failure = listOf(c, u).filterIsInstance<DataState.Failed>().firstOrNull()?.failure
            ThreadUiState(id, c.valueOr(emptyList()), u.valueOr(emptyList()), loading = c is DataState.Loading, failure = failure)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openThread(postId: String) {
        openThreadId.value = postId
        commentDraft.value = repo.store.commentDraft(circleId, postId)
    }

    fun closeThread() { openThreadId.value = null }

    fun setCommentDraft(text: String) {
        val clean = text.take(2000)
        commentDraft.value = clean
        openThreadId.value?.let { postId -> repo.store.setCommentDraft(circleId, postId, clean) }
    }

    fun comment(postId: String, text: String, parentId: String? = null) {
        val t = text.trim(); if (t.isEmpty()) return
        val name = state.value.me?.displayName ?: repo.displayName.ifBlank { "Member" }
        commentDraft.value = ""
        repo.store.setCommentDraft(circleId, postId, "")
        viewModelScope.launch {
            val err = repo.comment(circleId, postId, name, t, parentId).failureOrNull
            if (err != null) {
                if (commentDraft.value.isEmpty()) {
                    commentDraft.value = t
                    repo.store.setCommentDraft(circleId, postId, t)
                }
                fail(err)
            } else {
                repo.store.recordActivity(repo.clock())
                refreshHabits()
            }
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        viewModelScope.launch { repo.data.deleteComment(circleId, postId, commentId).failureOrNull?.let(::fail) }
    }

    // ---------- admin: pending ----------
    fun decideJoin(r: JoinRequest, approve: Boolean) {
        viewModelScope.launch {
            repo.api.decideJoin(circleId, r.uid, approve).fold(onOk = { say(if (approve) "${r.displayName} is in." else "Not this time.") }, onErr = ::fail)
        }
    }

    /** Pin one post to the top of the feed, or unpin with null. */
    fun pinFeedPost(post: Post?) {
        viewModelScope.launch {
            repo.api.pinPost(circleId, post?.id).fold(onOk = { say(if (post == null) "Unpinned." else "Pinned to the top.") }, onErr = ::fail)
        }
    }

    fun approve(p: PendingPost) { viewModelScope.launch { repo.api.approvePost(circleId, p.id).fold(onOk = { say("Shared with the circle.") }, onErr = ::fail) } }
    fun reject(p: PendingPost) { viewModelScope.launch { repo.api.rejectPost(circleId, p.id).fold(onOk = { say("Not shared.") }, onErr = ::fail) } }

    // ---------- members ----------
    fun setRole(m: Member, role: Role) { viewModelScope.launch { repo.api.setRole(circleId, m.uid, role).fold(onOk = { say("${m.displayName} is now ${role.label.lowercase()}.") }, onErr = ::fail) } }
    /** A display title such as "Pastor". Blank clears it. The title never changes what the person can do. */
    fun setTitle(m: Member, title: String) {
        viewModelScope.launch {
            repo.api.setTitle(circleId, m.uid, title.trim().ifBlank { null }).fold(onOk = { say("Title saved for ${m.displayName}.") }, onErr = ::fail)
        }
    }

    fun remove(m: Member) { viewModelScope.launch { repo.api.removeMember(circleId, m.uid).fold(onOk = { say("${m.displayName} was removed.") }, onErr = ::fail) } }

    // ---------- settings ----------
    fun saveSettings(name: String, vocab: String, description: String, settings: CircleSettings) {
        val c = state.value.circle ?: return
        viewModelScope.launch {
            repo.api.updateCircle(circleId, name.trim().takeIf { it != c.name }, vocab.trim().takeIf { it != c.vocab }, settings, description.trim().takeIf { it != c.description })
                .fold(onOk = { say("Saved.") }, onErr = ::fail)
        }
    }
    fun setMuted(muted: Boolean) { viewModelScope.launch { repo.api.setMute(circleId, muted).fold(onOk = { say(if (muted) "Muted. No notifications from this circle." else "Notifications on.") }, onErr = ::fail) } }
    fun leave(onLeft: () -> Unit) {
        viewModelScope.launch {
            repo.leave(circleId).fold(onOk = { onLeft() }, onErr = ::fail)
        }
    }

    // ---------- events ----------
    private val openEventId = MutableStateFlow<String?>(null)
    /** Who has said going, maybe or no for the event open on screen. */
    val eventRsvps: StateFlow<List<Rsvp>> = openEventId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList<Rsvp>()) else repo.data.observeRsvps(circleId, id).map { it.valueOr(emptyList()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openEvent(eventId: String?) { openEventId.value = eventId }

    /** [eventId] null creates a new event; otherwise the event is updated. Times are checked by the Worker. */
    fun saveEvent(eventId: String?, draft: EventDraft) {
        viewModelScope.launch {
            val result = if (eventId == null) repo.api.createEvent(circleId, draft).map { } else repo.api.updateEvent(circleId, eventId, draft)
            result.fold(onOk = { say(if (eventId == null) "Event added." else "Event saved.") }, onErr = ::fail)
        }
    }

    fun cancelEvent(eventId: String) {
        viewModelScope.launch { repo.api.cancelEvent(circleId, eventId).fold(onOk = { say("Event cancelled.") }, onErr = ::fail) }
    }

    fun rsvp(eventId: String, answer: RsvpAnswer) {
        viewModelScope.launch {
            repo.api.rsvp(circleId, eventId, answer).fold(
                onOk = {
                    repo.store.recordActivity(repo.clock())
                    refreshHabits()
                    say("Saved: ${answer.label}.")
                },
                onErr = ::fail
            )
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

    fun createEmailInvite(email: String) {
        val clean = email.trim()
        if (clean.isBlank() || "@" !in clean) {
            invite.value = InviteUiState(error = "Enter a valid email address.")
            return
        }
        val c = state.value.circle ?: return
        invite.value = InviteUiState(busy = true)
        viewModelScope.launch {
            repo.createEmailInvite(circleId, clean).fold(
                onOk = { inv ->
                    invite.value = InviteUiState(
                        invite = inv,
                        shareText = InviteCodes.shareText(c.name, c.groupWord, inv.code),
                        invitedEmail = clean
                    )
                },
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
