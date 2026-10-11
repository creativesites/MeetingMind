package com.craftflowtechnologies.meetingmind.core.circles2

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Firestore implementation. The SDK's persistent cache gives offline reads; listeners report errors as [DataState.Failed]. */
class FirestoreCirclesData(private val context: Context) : CirclesData {
    private val db: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Firestore unavailable: ${e.message}")
            null
        }
    }

    private fun circle(id: String) = db?.collection("circles")?.document(id)

    private fun failure(e: Throwable): CirclesFailure = when ((e as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> CirclesFailure(FailureKind.Rejected, "You don't have access to that, or you're no longer in this circle.", "permission_denied")
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> CirclesFailure.offline
        FirebaseFirestoreException.Code.NOT_FOUND -> CirclesFailure(FailureKind.NotFound, "That wasn't found.")
        else -> CirclesFailure.server()
    }

    private fun <T> listenDoc(ref: DocumentReference?, map: (String, Map<String, Any?>?) -> T): Flow<DataState<T>> {
        if (ref == null) return flowOf(DataState.Failed(CirclesFailure.notConnected))
        return callbackFlow {
            trySend(DataState.Loading)
            val reg = ref.addSnapshotListener { snap, err ->
                if (err != null) trySend(DataState.Failed(failure(err)))
                else if (snap != null) trySend(DataState.Ready(map(snap.id, snap.data), snap.metadata.isFromCache))
            }
            awaitClose { reg.remove() }
        }
    }

    private fun <T> listenQuery(query: Query?, map: (QuerySnapshot) -> T): Flow<DataState<T>> {
        if (query == null) return flowOf(DataState.Failed(CirclesFailure.notConnected))
        return callbackFlow {
            trySend(DataState.Loading)
            val reg = query.addSnapshotListener { snap, err ->
                if (err != null) trySend(DataState.Failed(failure(err)))
                else if (snap != null) trySend(DataState.Ready(map(snap), snap.metadata.isFromCache))
            }
            awaitClose { reg.remove() }
        }
    }

    private fun <T> QuerySnapshot.mapDocs(f: (String, Map<String, Any?>) -> T?): List<T> =
        documents.mapNotNull { d -> d.data?.let { f(d.id, it) } }

    override fun observeCircle(circleId: String) = listenDoc(circle(circleId)) { id, d -> CirclesParsing.circle(id, d) }
    override fun observeMembers(circleId: String) = listenQuery(circle(circleId)?.collection("members")) { s ->
        s.mapDocs { id, d -> CirclesParsing.member(id, d) }.sortedWith(compareBy({ it.role.ordinal }, { it.displayName.lowercase() }))
    }
    override fun observePosts(circleId: String) = listenQuery(circle(circleId)?.collection("posts")?.orderBy("createdAt", Query.Direction.DESCENDING)?.limit(100)) { s ->
        s.mapDocs { id, d -> CirclesParsing.post(id, d) }.filter { !it.deleted }
    }
    override fun observePending(circleId: String) = listenQuery(circle(circleId)?.collection("pending")?.orderBy("createdAt", Query.Direction.ASCENDING)?.limit(50)) { s ->
        s.mapDocs { id, d -> CirclesParsing.pending(id, d) }
    }
    override fun observeUpdates(circleId: String, postId: String) = listenQuery(circle(circleId)?.collection("posts")?.document(postId)?.collection("updates")?.orderBy("createdAt")) { s ->
        s.mapDocs { id, d -> CirclesParsing.update(id, d) }
    }
    override fun observeComments(circleId: String, postId: String) = listenQuery(circle(circleId)?.collection("posts")?.document(postId)?.collection("comments")?.orderBy("createdAt")?.limit(200)) { s ->
        s.mapDocs { id, d -> CirclesParsing.comment(id, d) }
    }
    override fun observeMessages(circleId: String, limit: Int) = listenQuery(circle(circleId)?.collection("messages")?.orderBy("createdAt", Query.Direction.DESCENDING)?.limit(limit.toLong())) { s ->
        s.mapDocs { id, d -> CirclesParsing.message(id, d) }.reversed()
    }

    override fun observeMessageReactions(circleId: String, messageId: String, myUid: String): Flow<List<MessageReaction>> =
        listenQuery(circle(circleId)?.collection("messages")?.document(messageId)?.collection("reactions")) { s ->
            CirclesParsing.reactionChips(s.documents.mapNotNull { d -> (d.getString("emoji"))?.let { d.id to it } }.toMap(), myUid)
        }.map { it.valueOr(emptyList()) }

    override fun observePoll(circleId: String, pollId: String, myUid: String): Flow<DataState<PollState>> {
        val pollRef = circle(circleId)?.collection("polls")?.document(pollId)
        val polls = listenDoc(pollRef) { id, d -> d?.let { CirclesParsing.poll(id, it) } }
        val votes = listenQuery(pollRef?.collection("votes")) { s -> CirclesParsing.votes(s.documents.mapNotNull { d -> d.data?.let { d.id to it } }) }
        return combine(polls, votes) { p, v ->
            when {
                p is DataState.Failed -> p
                p is DataState.Ready && p.value != null -> DataState.Ready(PollState(p.value, v.valueOr(emptyMap()), myUid), p.fromCache)
                p is DataState.Ready -> DataState.Failed(CirclesFailure(FailureKind.NotFound, "This poll was removed."))
                else -> DataState.Loading
            }
        }
    }

    override fun observeChain(circleId: String, chainId: String, myUid: String): Flow<DataState<ChainState>> {
        val ref = circle(circleId)?.collection("chains")?.document(chainId)
        val chains = listenDoc(ref) { id, d -> d?.let { CirclesParsing.chain(id, it) } }
        val slots = listenQuery(ref?.collection("slots")) { s -> s.mapDocs { id, d -> CirclesParsing.slot(id, d) } }
        return combine(chains, slots) { c, s ->
            when {
                c is DataState.Failed -> c
                c is DataState.Ready && c.value != null -> DataState.Ready(ChainState(c.value, s.valueOr(emptyList()), myUid, System.currentTimeMillis()), c.fromCache)
                c is DataState.Ready -> DataState.Failed(CirclesFailure(FailureKind.NotFound, "This prayer chain was removed."))
                else -> DataState.Loading
            }
        }.catch { emit(DataState.Failed(failure(it))) }
    }

    override suspend fun hasPrayed(circleId: String, postId: String): Boolean {
        val uid = currentUid() ?: return false
        return try {
            circle(circleId)?.collection("posts")?.document(postId)?.collection("prayers")?.document(uid)
                ?.get(Source.CACHE)?.await()?.exists() == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private fun currentUid(): String? = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid } catch (e: Exception) { null }

    /** Runs a write. A write that is still waiting for the network after [WRITE_WAIT_MS] is queued by the SDK and sends later; we report success for it. */
    private suspend fun write(block: suspend (FirebaseFirestore) -> Unit): CirclesResult<Unit> {
        val d = db ?: return CirclesFailure.notConnected.asResult()
        return try {
            withTimeoutOrNull(WRITE_WAIT_MS) { block(d) }
            CirclesResult.Ok(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CirclesResult.Err(failure(e))
        }
    }

    private fun ref(d: FirebaseFirestore, id: String) = d.collection("circles").document(id)

    private fun ts() = FieldValue.serverTimestamp()

    override suspend fun addComment(circleId: String, postId: String, uid: String, name: String, body: String, parentId: String?) = write { d ->
        val data = hashMapOf<String, Any?>("authorUid" to uid, "displayName" to name.take(40), "body" to body, "createdAt" to ts())
        if (parentId != null) data["parentId"] = parentId
        ref(d, circleId).collection("posts").document(postId).collection("comments").document().set(data).await()
    }

    override suspend fun deleteComment(circleId: String, postId: String, commentId: String) = write { d ->
        ref(d, circleId).collection("posts").document(postId).collection("comments").document(commentId).delete().await()
    }

    override suspend fun sendMessage(circleId: String, uid: String, name: String, draft: MessageDraft) = write { d ->
        val data = hashMapOf<String, Any?>("authorUid" to uid, "displayName" to name.take(40), "createdAt" to ts(), "deleted" to false)
        when (draft) {
            is MessageDraft.Text -> { data["kind"] = "text"; data["text"] = draft.text }
            is MessageDraft.Reply -> { data["kind"] = "reply"; data["text"] = draft.text; data["replyTo"] = draft.replyTo }
            is MessageDraft.Card -> {
                data["kind"] = "card"; data["text"] = ""
                data["card"] = hashMapOf<String, Any?>("templateId" to draft.card.templateId, "text" to draft.card.text)
                    .apply { draft.card.mood?.let { put("mood", it) }; draft.card.verseRef?.let { put("verseRef", it.take(64)) }; draft.card.verseText?.let { put("verseText", it.take(600)) } }
            }
        }
        ref(d, circleId).collection("messages").document().set(data).await()
    }

    override suspend fun editMessage(circleId: String, messageId: String, text: String) = write { d ->
        ref(d, circleId).collection("messages").document(messageId).update(mapOf("text" to text, "editedAt" to ts())).await()
    }

    override suspend fun deleteMessage(circleId: String, messageId: String) = write { d ->
        ref(d, circleId).collection("messages").document(messageId).update(mapOf("deleted" to true, "text" to "", "card" to null)).await()
    }

    override suspend fun setMessageReaction(circleId: String, messageId: String, uid: String, emoji: String?) = write { d ->
        val docRef = ref(d, circleId).collection("messages").document(messageId).collection("reactions").document(uid)
        if (emoji == null) { docRef.delete().await(); return@write }
        val exists = try { docRef.get(Source.CACHE).await().exists() } catch (e: Exception) { false }
        // The rules allow a change of emoji only as an update of that one field.
        if (exists) docRef.update("emoji", emoji).await()
        else docRef.set(mapOf("authorUid" to uid, "emoji" to emoji, "createdAt" to ts())).await()
    }

    override suspend fun vote(circleId: String, pollId: String, uid: String, choices: List<String>) = write { d ->
        val docRef = ref(d, circleId).collection("polls").document(pollId).collection("votes").document(uid)
        if (choices.isEmpty()) docRef.delete().await()
        else docRef.set(mapOf("authorUid" to uid, "choices" to choices.distinct(), "updatedAt" to ts())).await()
    }

    override suspend fun claimSlot(circleId: String, chainId: String, uid: String, name: String, hour: Int) = write { d ->
        ref(d, circleId).collection("chains").document(chainId).collection("slots").document(hour.toString())
            .set(mapOf("authorUid" to uid, "displayName" to name.take(40), "createdAt" to ts())).await()
    }

    override suspend fun releaseSlot(circleId: String, chainId: String, hour: Int) = write { d ->
        ref(d, circleId).collection("chains").document(chainId).collection("slots").document(hour.toString()).delete().await()
    }

    private companion object {
        const val TAG = "FirestoreCircles"
        const val WRITE_WAIT_MS = 6_000L
    }
}
