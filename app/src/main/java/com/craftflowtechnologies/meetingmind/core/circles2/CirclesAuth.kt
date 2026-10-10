package com.craftflowtechnologies.meetingmind.core.circles2

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import java.util.concurrent.CancellationException

/** Who the caller is. Circles starts anonymous; linking to Google (keep circles on any phone) comes later. */
interface CirclesAuth {
    /** Signs in anonymously if needed and returns the uid. */
    suspend fun ensureSignedIn(): CirclesResult<String>
    /** A fresh ID token for the Worker. */
    suspend fun idToken(): CirclesResult<String>
    /** The uid if already signed in (no network). */
    val currentUid: String?
    /** True when the account is anonymous: a reinstall would lose membership. */
    val isAnonymous: Boolean
}

class FirebaseCirclesAuth(private val context: Context) : CirclesAuth {
    private val auth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Auth unavailable: ${e.message}")
            null
        }
    }

    override val currentUid: String? get() = auth?.currentUser?.uid
    override val isAnonymous: Boolean get() = auth?.currentUser?.isAnonymous ?: true

    override suspend fun ensureSignedIn(): CirclesResult<String> {
        val a = auth ?: return CirclesFailure.notConnected.asResult()
        a.currentUser?.uid?.let { return CirclesResult.Ok(it) }
        return try {
            val uid = a.signInAnonymously().await().user?.uid
            if (uid != null) CirclesResult.Ok(uid) else CirclesFailure.signedOut.asResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Anonymous sign-in failed: ${e.message}")
            CirclesFailure.signedOut.asResult()
        }
    }

    override suspend fun idToken(): CirclesResult<String> {
        val signedIn = ensureSignedIn()
        if (signedIn is CirclesResult.Err) return signedIn
        return try {
            val token = auth?.currentUser?.getIdToken(false)?.await()?.token
            if (token.isNullOrBlank()) CirclesFailure.signedOut.asResult() else CirclesResult.Ok(token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CirclesFailure.offline.asResult()
        }
    }

    private companion object { const val TAG = "CirclesAuth" }
}
