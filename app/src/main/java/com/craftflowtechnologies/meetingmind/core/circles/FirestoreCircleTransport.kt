package com.craftflowtechnologies.meetingmind.core.circles

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Production transport using Firebase Anonymous Authentication and Cloud Firestore.
 * Conforms to the E2EE architecture: Firestore only stores {uid, ts, iv, ciphertext}.
 */
class FirestoreCircleTransport(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : CircleTransport {

    companion object {
        private const val TAG = "FirestoreTransport"
        private const val COLLECTION_CIRCLES = "circles"
        private const val COLLECTION_EVENTS = "events"
    }

    override val currentUserId: String
        get() = auth.currentUser?.uid.orEmpty()

    override suspend fun ensureAuthenticated(): String {
        val existing = auth.currentUser
        if (existing != null) return existing.uid

        val result = auth.signInAnonymously().await()
        val user = result.user ?: error("Anonymous Firebase authentication returned null user")
        Log.i(TAG, "Authenticated anonymously as ${user.uid}")
        return user.uid
    }

    override suspend fun publishEvent(circleId: String, iv: String, ciphertext: String): Result<String> = runCatching {
        val uid = ensureAuthenticated()
        val eventsColl = firestore.collection(COLLECTION_CIRCLES)
            .document(circleId)
            .collection(COLLECTION_EVENTS)

        val docRef = eventsColl.document()
        val data = hashMapOf(
            "uid" to uid,
            "ts" to FieldValue.serverTimestamp(),
            "iv" to iv,
            "ciphertext" to ciphertext
        )

        docRef.set(data).await()
        docRef.id
    }

    override fun observeEvents(circleId: String): Flow<List<CircleTransportEvent>> = callbackFlow {
        val eventsColl = firestore.collection(COLLECTION_CIRCLES)
            .document(circleId)
            .collection(COLLECTION_EVENTS)
            .orderBy("ts", Query.Direction.ASCENDING)

        val registration = eventsColl.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Error listening to circle $circleId events: ${error.message}")
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.getString("uid") ?: return@mapNotNull null
                    val iv = doc.getString("iv") ?: return@mapNotNull null
                    val ciphertext = doc.getString("ciphertext") ?: return@mapNotNull null
                    val ts = doc.getTimestamp("ts")?.toDate()?.time ?: System.currentTimeMillis()
                    CircleTransportEvent(
                        id = doc.id,
                        uid = uid,
                        timestamp = ts,
                        iv = iv,
                        ciphertext = ciphertext
                    )
                }
                trySend(list)
            }
        }

        awaitClose { registration.remove() }
    }
}
