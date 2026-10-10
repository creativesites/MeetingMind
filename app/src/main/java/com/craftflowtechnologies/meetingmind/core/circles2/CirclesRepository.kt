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
    data class Ready(val circles: List<Circle>, val note: CirclesFailure? = null) : CirclesListState
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
    val clock: () -> Long = System::currentTimeMillis
) {
    val isConfigured: Boolean get() = api.isConfigured

    private val ids = MutableStateFlow(store.circleIds())
    private val note = MutableStateFlow<CirclesFailure?>(null)

    suspend fun uid(): CirclesResult<String> = auth.ensureSignedIn()

    /** My own display name for new circles. Empty until the person has typed one. */
    var displayName: String
        get() = store.displayName
        set(value) { store.displayName = value }

    /** Asks the Worker which circles I'm in (finds them again on a linked account) and merges with the local list. */
    suspend fun refresh() {
        if (!api.isConfigured) return
        when (val r = api.myCircles()) {
            is CirclesResult.Ok -> { r.value.forEach(store::addCircle); ids.value = store.circleIds(); note.value = null }
            is CirclesResult.Err -> note.value = r.failure.takeIf { it.kind != FailureKind.NotConnected }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun circles(): Flow<CirclesListState> {
        if (!api.isConfigured) return flowOf(CirclesListState.NotConnected)
        val rows: Flow<List<Circle>> = ids.flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyList())
            else combine(list.map { id -> data.observeCircle(id) }) { states ->
                val out = ArrayList<Circle>(states.size)
                states.forEachIndexed { i, s ->
                    when {
                        s is DataState.Ready && s.value != null -> out += s.value
                        // Gone or no longer a member: forget it locally so the list stays truthful.
                        s is DataState.Ready -> store.removeCircle(list[i])
                        s is DataState.Failed && s.failure.code == "permission_denied" -> store.removeCircle(list[i])
                        else -> Unit
                    }
                }
                if (list.any { it !in store.circleIds() }) ids.value = store.circleIds()
                out.toList()
            }
        }
        return combine(rows, note, ids) { circles, n, idList ->
            if (circles.isEmpty() && idList.isNotEmpty() && n == null) CirclesListState.Loading else CirclesListState.Ready(circles, n)
        }
    }

    suspend fun createCircle(name: String, template: String, vocab: String, displayName: String, settings: CircleSettings): CirclesResult<String> {
        if (!api.isConfigured) return CirclesFailure.notConnected.asResult()
        val signedIn = uid()
        if (signedIn is CirclesResult.Err) return signedIn
        val r = api.createCircle(CreateCircleRequest(name.trim(), template, vocab.trim(), displayName.trim(), settings))
        if (r is CirclesResult.Ok) { store.displayName = displayName.trim(); remember(r.value) }
        return r
    }

    /** [pasted] is anything: a code, a whole shared message, a link. Garbage gives an inline "doesn't look like a code" message, never a crash. */
    suspend fun join(pasted: String, displayName: String): CirclesResult<String> {
        if (!api.isConfigured) return CirclesFailure.notConnected.asResult()
        val code = InviteCodes.extract(pasted)
        if (code == null) {
            return CirclesResult.Err(CirclesFailure(FailureKind.Rejected, "That doesn't look like a Circles code. It looks like GRACE-7K2Q.", "bad_code"))
        }
        val signedIn = uid()
        if (signedIn is CirclesResult.Err) return signedIn
        // Only the clean code goes out, not the whole pasted message.
        val r = api.join(code, displayName.trim().ifBlank { "Member" })
        if (r is CirclesResult.Ok) { store.displayName = displayName.trim(); remember(r.value) }
        return r
    }

    private fun remember(circleId: String) { store.addCircle(circleId); ids.value = store.circleIds() }

    suspend fun leave(circleId: String): CirclesResult<Unit> {
        val r = api.leave(circleId)
        if (r is CirclesResult.Ok) { store.removeCircle(circleId); ids.value = store.circleIds() }
        return r
    }

    suspend fun createPost(req: CreatePostRequest): CirclesResult<CreatedPost> {
        val r = api.createPost(req)
        if (r is CirclesResult.Ok) store.addMyPost(req.circleId, r.value.postId)
        return r
    }

    suspend fun prayed(circleId: String, postId: String): CirclesResult<Boolean> {
        val r = api.prayed(circleId, postId)
        if (r is CirclesResult.Ok) store.addPrayed(circleId, postId)
        return r
    }
}
