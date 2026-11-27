package com.craftflowtechnologies.meetingmind.core.circles2

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** What the Circles list shows. */
sealed interface CirclesListState {
    /** No Worker URL in this build: "Circles isn't connected yet". */
    data object NotConnected : CirclesListState
    data object Loading : CirclesListState
    /** [earlier] counts circles this phone knew under an earlier sign-in and can't read now (see LocalCircleStore). */
    data class Ready(val circles: List<Circle>, val note: CirclesFailure? = null, val earlier: Int = 0) : CirclesListState
}

/**
 * Glue between the Worker ([api]), live reads ([data]), identity ([auth]) and what's local ([store]).
 * Every function returns a [CirclesResult]; nothing here throws to a screen.
 */
class CirclesRepository(
    val api: CirclesApi,
    val data: CirclesData,
    val auth: CirclesAuth,
    val store: LocalCircleStore,
    val clock: () -> Long = System::currentTimeMillis,
    /** Keeps this phone's push token registered with the Worker. Null in tests that don't care. */
    val push: PushRegistrar? = null
) {
    val isConfigured: Boolean get() = api.isConfigured

    private val ids = MutableStateFlow(store.circleIds())
    private val note = MutableStateFlow<CirclesFailure?>(null)
    private val earlier = MutableStateFlow(store.earlierCircleIds().size)

    suspend fun uid(): CirclesResult<String> = auth.ensureSignedIn()

    /** My own display name for new circles. Empty until the person has typed one. */
    var displayName: String
        get() = store.displayName
        set(value) { store.displayName = value }

    /**
     * Asks the Worker which circles I'm in (finds them again on a linked account) and merges with the local list.
     * [activatePush] is false for the Home tile: opening Home must not turn pushes on for someone with no circle.
     */
    suspend fun refresh(activatePush: Boolean = true) {
        if (!api.isConfigured) return
        when (val r = api.myCircles()) {
            is CirclesResult.Ok -> {
                r.value.forEach { store.addCircle(it); store.clearEarlierCircle(it) }
                ids.value = store.circleIds(); earlier.value = store.earlierCircleIds().size; note.value = null
            }
            is CirclesResult.Err -> note.value = r.failure.takeIf { it.kind != FailureKind.NotConnected }
        }
        // Opening Circles (or already being in one) is when pushes become useful.
        if (activatePush || store.circleIds().isNotEmpty()) push?.activate()
    }

    /**
     * Syncs FCM registration on app startup/foreground for users who already belong to a circle,
     * without waking the Worker or registering tokens before the person joins/opens Circles.
     */
    suspend fun syncPushOnStartup() {
        if (!api.isConfigured) return
        if (store.circleIds().isNotEmpty()) push?.activate()
        else (push as? PushTokenManager)?.syncIfWanted()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun circles(): Flow<CirclesListState> {
        if (!api.isConfigured) return flowOf(CirclesListState.NotConnected)
        val rows: Flow<Pair<List<Circle>, CirclesFailure?>> = ids.flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyList<Circle>() to null)
            else combine(list.map { id -> data.observeCircle(id) }) { states ->
                val out = ArrayList<Circle>(states.size)
                states.forEachIndexed { i, s ->
                    when {
                        s is DataState.Ready && s.value != null -> out += s.value
                        // Closed or deleted, as the server says. A "doesn't exist" read only from this phone's cache
                        // (offline, or a cleared cache) proves nothing, so the circle is kept until the server answers.
                        s is DataState.Ready && !s.fromCache -> store.removeCircle(list[i])
                        // This account can't read it: usually a new sign-in after a reinstall. Kept aside, not lost,
                        // so the list can offer the way back.
                        s is DataState.Failed && s.failure.code == "permission_denied" -> {
                            store.removeCircle(list[i]); store.addEarlierCircle(list[i]); earlier.value = store.earlierCircleIds().size
                        }
                        else -> Unit
                    }
                }
                if (list.any { it !in store.circleIds() }) ids.value = store.circleIds()
                // A listener that failed is reported, not left to spin on Loading.
                val failed = states.firstNotNullOfOrNull { (it as? DataState.Failed)?.failure?.takeIf { f -> f.code != "permission_denied" } }
                out.toList() to failed
            }
        }
        return combine(rows, note, ids, earlier) { (circles, listenerFailure), n, idList, earlierCount ->
            val shown = n ?: listenerFailure?.takeIf { circles.isEmpty() }
            if (circles.isEmpty() && idList.isNotEmpty() && shown == null) CirclesListState.Loading
            else CirclesListState.Ready(circles, shown, earlierCount)
        }
    }

    suspend fun createCircle(name: String, template: String, vocab: String, displayName: String, settings: CircleSettings, requestId: String, description: String = "", space: String = "faith"): CirclesResult<String> {
        if (!api.isConfigured) return CirclesFailure.notConnected.asResult()
        val signedIn = uid()
        if (signedIn is CirclesResult.Err) return signedIn
        val r = api.createCircle(CreateCircleRequest(name.trim(), template, vocab.trim(), displayName.trim(), settings, requestId, description.trim(), space))
        if (r is CirclesResult.Ok) { store.displayName = displayName.trim(); remember(r.value) }
        return r
    }

    suspend fun createEmailInvite(circleId: String, email: String, expiresInDays: Int = 7, maxUses: Int = 50): CirclesResult<Invite> {
        if (!api.isConfigured) return CirclesFailure.notConnected.asResult()
        return api.createEmailInvite(circleId, email, expiresInDays, maxUses)
    }

    suspend fun createPost(
        circleId: String,
        body: String,
        type: PostType = PostType.Note,
        anonymous: Boolean = false,
        attachments: List<MediaAttachment> = emptyList(),
        verseRef: String? = null,
        requestId: String = java.util.UUID.randomUUID().toString(),
        meta: PostMeta? = null
    ): CirclesResult<CreatedPost> {
        if (!api.isConfigured) return CirclesFailure.notConnected.asResult()
        val signedIn = uid()
        if (signedIn is CirclesResult.Err) return CirclesResult.Err(signedIn.failure)
        return api.createPost(
            CreatePostRequest(
                circleId = circleId,
                type = type,
                body = body,
                verseRef = verseRef,
                anonymous = anonymous,
                attachments = attachments,
                requestId = requestId,
                meta = meta
            )
        )
    }

    /** [pasted] is anything: a code, a whole shared message, a link. Garbage gives an inline "doesn't look like a code" message, never a crash. */
    /**
     * Uploads one file through the Worker: a ticket, then the bytes. The caller then posts or sends it.
     * Size and type are checked here first, so a big file is refused before any upload starts.
     */
    suspend fun uploadAttachment(circleId: String, kind: MediaKind, bytes: ByteArray, mime: String, name: String?, durationMs: Long?): CirclesResult<MediaAttachment> {
        if (!api.isConfigured) return CirclesFailure.notConnected.asResult()
        if (!kind.accepts(mime)) return CirclesFailure(FailureKind.Rejected, "That kind of file isn't supported here.", "bad_mime").asResult()
        if (bytes.size > kind.maxBytes) return CirclesFailure(FailureKind.Rejected, "That file is too large. The limit is ${kind.maxBytes / 1_000_000} MB.", "too_large").asResult()
        val ticket = when (val t = api.createUploadTicket(circleId, kind, mime, bytes.size.toLong(), durationMs, name?.take(120))) {
            is CirclesResult.Ok -> t.value
            is CirclesResult.Err -> return t
        }
        val path = when (val p = api.uploadMedia(ticket.ticketId, mime, bytes)) {
            is CirclesResult.Ok -> p.value
            is CirclesResult.Err -> return p
        }
        return CirclesResult.Ok(MediaAttachment(path, mime, bytes.size.toLong(), durationMs, name?.take(120)))
    }

    /**
     * Sets a circle's cover. A pack picture is stored as a reference, so nothing is uploaded; a photo is uploaded
     * first, then saved. Admins only; the Worker checks it. A failure leaves the old cover as it was.
     */
    suspend fun setCover(circleId: String, choice: CoverChoice): CirclesResult<Unit> {
        if (!api.isConfigured) return CirclesFailure.notConnected.asResult()
        val path = when (choice) {
            is CoverChoice.Pack -> CircleCovers.packRef(choice.photoId)
            is CoverChoice.Photo -> when (val up = uploadAttachment(circleId, MediaKind.Image, choice.bytes, choice.mime, choice.name, null)) {
                is CirclesResult.Ok -> up.value.path
                is CirclesResult.Err -> return CirclesResult.Err(up.failure)
            }
        }
        return api.updateCircle(circleId, null, null, null, null, coverPath = path)
    }

    /**
     * Circles shows the app's profile photo until the person picks one inside Circles. [key] names the app photo (its
     * path and time), so it is sent once per change; [jpeg] reads it as a small square JPEG. Only for someone in a
     * circle: nobody else needs a Circles photo. Returns null when there was nothing to do.
     */
    suspend fun syncAppAvatar(key: String?, jpeg: suspend () -> ByteArray?): CirclesResult<Unit>? {
        if (!api.isConfigured || key == null || store.circlesAvatarOwn || store.circleIds().isEmpty()) return null
        if (store.appAvatarSynced == key) return null
        val bytes = jpeg() ?: return null
        return when (val r = api.uploadAvatar(bytes, "image/jpeg")) {
            is CirclesResult.Ok -> { store.appAvatarSynced = key; CirclesResult.Ok(Unit) }
            is CirclesResult.Err -> CirclesResult.Err(r.failure)
        }
    }

    /** My profile photo, as a JPEG the caller has already made square and small (see avatarJpeg). */
    suspend fun setProfilePhoto(jpeg: ByteArray): CirclesResult<Long> {
        if (!api.isConfigured) return CirclesFailure.notConnected.asResult()
        return api.uploadAvatar(jpeg, "image/jpeg").also { if (it is CirclesResult.Ok) store.circlesAvatarOwn = true }
    }

    suspend fun removeProfilePhoto(): CirclesResult<Unit> {
        if (!api.isConfigured) return CirclesFailure.notConnected.asResult()
        return api.removeAvatar().also { if (it is CirclesResult.Ok) store.circlesAvatarOwn = true }
    }

    suspend fun join(pasted: String, displayName: String): CirclesResult<JoinOutcome> {
        if (!api.isConfigured) return CirclesFailure.notConnected.asResult()
        val code = InviteCodes.extract(pasted)
        if (code == null) {
            return CirclesResult.Err(CirclesFailure(FailureKind.Rejected, "That doesn't look like a Circles code. It looks like GRACE-7K2Q.", "bad_code"))
        }
        val signedIn = uid()
        if (signedIn is CirclesResult.Err) return signedIn
        // Only the clean code goes out, not the whole pasted message.
        val r = api.join(code, displayName.trim().ifBlank { "Member" })
        // A pending request is not membership yet: nothing is remembered until an admin lets the person in.
        if (r is CirclesResult.Ok) { store.displayName = displayName.trim(); if (!r.value.pending) remember(r.value.circleId) }
        return r
    }

    private suspend fun remember(circleId: String) {
        store.addCircle(circleId); store.clearEarlierCircle(circleId); ids.value = store.circleIds(); earlier.value = store.earlierCircleIds().size
        push?.activate()
    }

    /** True when this is the person's first circle: the moment to ask for notification permission, in context. */
    fun isFirstCircle(): Boolean = store.circleIds().size == 1

    suspend fun leave(circleId: String): CirclesResult<Unit> {
        val r = api.leave(circleId)
        if (r is CirclesResult.Ok) {
            store.removeCircle(circleId); ids.value = store.circleIds()
            if (store.circleIds().isEmpty()) push?.deactivate()
        }
        return r
    }

    /**
     * Switches this phone to the existing Google account the person chose, keeping every circle made before sign-in.
     * While still the guest, it takes the guest's token and checks the Worker can carry the circles over; if it can't,
     * nothing switches (switching then would leave those circles behind with the guest account). After switching, the
     * Worker moves them, and the list loads from the account.
     */
    suspend fun switchAccountKeepingCircles(): LinkResult {
        val guest = auth.guestToken()
        if (guest != null && store.circleIds().isNotEmpty()) {
            // As the guest, this call is a no-op on the Worker: it only proves the carry-over exists and is reachable.
            val probe = api.adoptGuestCircles(guest)
            if (probe is CirclesResult.Err) return LinkResult.Failed(carryOverBlockedLine(probe.failure))
        }
        val r = auth.switchToLinkedAccount()
        if (r is LinkResult.Linked) onAccountSwitched(guest)
        return r
    }

    /** After the account changed (switched to an existing Google account): its circles, plus the guest's, make the list. */
    suspend fun onAccountSwitched(guestToken: String? = null) {
        val guestCircles = store.circleIds()
        if (guestToken != null && guestCircles.isNotEmpty()) {
            val carried = api.adoptGuestCircles(guestToken).let { first -> if (first is CirclesResult.Err) api.adoptGuestCircles(guestToken) else first }
            // Still not carried: keep them aside, visible on the list, never dropped quietly.
            if (carried is CirclesResult.Err) guestCircles.forEach(store::addEarlierCircle)
        }
        guestCircles.forEach(store::removeCircle)
        ids.value = store.circleIds(); earlier.value = store.earlierCircleIds().size
        push?.onAccountChanged()
        refresh()
    }

    suspend fun createPost(req: CreatePostRequest): CirclesResult<CreatedPost> {
        val r = api.createPost(req)
        if (r is CirclesResult.Ok) {
            store.addMyPost(req.circleId, r.value.postId)
            // Remembered on this phone only: it tells us later to comment through the Worker so the uid never reaches the comment.
            if (req.anonymous && req.type == PostType.Prayer) store.addAnonymousPost(req.circleId, r.value.postId)
        }
        return r
    }

    /**
     * Comments on a post. When I'm the hidden author of an anonymous request, a client-written comment would carry my
     * uid, so it goes through the Worker instead (`commentAsAuthor`, written with no uid). Everyone else writes directly.
     */
    suspend fun comment(circleId: String, postId: String, name: String, body: String, parentId: String?): CirclesResult<Unit> {
        val text = body.trim().take(2000)
        if (text.isEmpty()) return CirclesFailure(FailureKind.Rejected, "Write something first.").asResult()
        val myUid = when (val u = uid()) {
            is CirclesResult.Ok -> u.value
            is CirclesResult.Err -> return u
        }
        val r = if (postId in store.anonymousPostIds(circleId)) api.commentAsAuthor(circleId, postId, text, parentId)
        else data.addComment(circleId, postId, myUid, name, text, parentId)
        if (r is CirclesResult.Ok) api.syncCounts(circleId, postId) // keeps the comment counter honest; a failure is harmless
        return r
    }

    suspend fun prayed(circleId: String, postId: String): CirclesResult<Boolean> {
        val r = api.prayed(circleId, postId)
        if (r is CirclesResult.Ok) store.addPrayed(circleId, postId)
        return r
    }

    suspend fun toggleChecklistItem(circleId: String, postId: String, itemId: String, done: Boolean? = null): CirclesResult<Unit> =
        if (!api.isConfigured) CirclesFailure.notConnected.asResult() else api.toggleChecklistItem(circleId, postId, itemId, done)

    /** Owner and admins only, work circles only. */
    suspend fun setExternal(circleId: String, uid: String, external: Boolean): CirclesResult<Unit> =
        if (!api.isConfigured) CirclesFailure.notConnected.asResult() else api.setExternal(circleId, uid, external)

    suspend fun setDecisionStatus(circleId: String, postId: String, status: DecisionStatus): CirclesResult<Unit> =
        if (!api.isConfigured) CirclesFailure.notConnected.asResult() else api.setDecisionStatus(circleId, postId, status)

    /**
     * Looks at an invite before joining. Needs the same sign-in as join. [code] is anything the person pasted (a bare
     * code, a whole message); garbage gets the same inline message as join. Changes nothing on the server.
     */
    suspend fun previewInvite(code: String): CirclesResult<InvitePreview> {
        if (!api.isConfigured) return CirclesFailure.notConnected.asResult()
        val bare = InviteCodes.extract(code)
            ?: return CirclesResult.Err(CirclesFailure(FailureKind.Rejected, "That doesn't look like a Circles code. It looks like GRACE-7K2Q.", "bad_code"))
        val signedIn = uid()
        if (signedIn is CirclesResult.Err) return CirclesResult.Err(signedIn.failure)
        return api.previewInvite(bare)
    }

    /** The Table: claim an open item, or release it. */
    suspend fun claimBringItem(circleId: String, eventId: String, itemId: String, claim: Boolean): CirclesResult<Unit> =
        if (!api.isConfigured) CirclesFailure.notConnected.asResult() else api.claimBringItem(circleId, eventId, itemId, claim)
}

/** Why switching accounts was held back. Nothing changed: the person stays on this phone's account with all its circles. */
internal fun carryOverBlockedLine(f: CirclesFailure): String = when (f.kind) {
    FailureKind.Offline, FailureKind.NotConnected -> "You're offline. Connect, then sign in again so the circles on this phone come with you."
    else -> "The circles you made on this phone can't move to that account yet, so nothing was switched. Try again after the next update."
}
