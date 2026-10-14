package com.craftflowtechnologies.meetingmind.core.circles

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Production transport using Firebase Anonymous Authentication and Cloud Firestore.
 * Conforms to the E2EE architecture: Firestore only stores {uid, ts, iv, ciphertext}.
 * Safely guards all Firebase initialization so that an uninitialized or offline state
 * never crashes the application.
 */
class FirestoreCircleTransport(
    private val context: Context
) : CircleTransport {

    companion object {
        private const val TAG = "FirestoreTransport"
        private const val COLLECTION_CIRCLES = "circles"
        private const val COLLECTION_EVENTS = "events"
        private const val PREFS = "firestore_circle_transport"
    }

    private val localFallbackId: String by lazy {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString("local_uid", null)
        if (existing != null) {
            existing
        } else {
            val fresh = UUID.randomUUID().toString()
            prefs.edit().putString("local_uid", fresh).apply()
            fresh
        }
    }

    private val auth: FirebaseAuth? by lazy {
        try {
            ensureFirebaseApp(context)
            FirebaseAuth.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "Firebase Auth not available: ${e.message}")
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            ensureFirebaseApp(context)
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.w(TAG, "Firebase Firestore not available: ${e.message}")
            null
        }
    }

    private fun ensureFirebaseApp(ctx: Context) {
        runCatching {
            if (FirebaseApp.getApps(ctx).isEmpty()) {
                FirebaseApp.initializeApp(ctx)
            }
        }
    }

    override val currentUserId: String
        get() = auth?.currentUser?.uid ?: localFallbackId

    override suspend fun ensureAuthenticated(): String {
        val a = auth ?: return localFallbackId
        val existing = a.currentUser
        if (existing != null) return existing.uid

        return try {
            val result = a.signInAnonymously().await()
            val user = result.user
            if (user != null) {
                Log.i(TAG, "Authenticated anonymously as ${user.uid}")
                user.uid
            } else {
                localFallbackId
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Anonymous sign-in failed, using local ID: ${e.message}")
            localFallbackId
        }
    }

    override suspend fun publishEvent(circleId: String, iv: String, ciphertext: String): Result<String> = runCatching {
        val fs = firestore ?: error("Firestore is not available on this device")
        val uid = ensureAuthenticated()
        val eventsColl = fs.collection(COLLECTION_CIRCLES)
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

    override fun observeEvents(circleId: String): Flow<List<CircleTransportEvent>> {
        val fs = firestore ?: return emptyFlow()
        return callbackFlow {
            val eventsColl = fs.collection(COLLECTION_CIRCLES)
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
}
