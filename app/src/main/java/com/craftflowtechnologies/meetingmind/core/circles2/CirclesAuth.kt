package com.craftflowtechnologies.meetingmind.core.circles2

import android.content.Context
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.craftflowtechnologies.meetingmind.core.firebase.AuthAvailability
import com.craftflowtechnologies.meetingmind.core.firebase.FirebaseAuthManager
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import java.util.concurrent.CancellationException

/** How "Keep your circles on any phone" ended. */
sealed interface LinkResult {
    /** This anonymous account is now a Google account; nothing about the circles changed. */
    data class Linked(val email: String?) : LinkResult
    /** That Google account already has its own Circles account (probably from another phone). Offer to switch to it. */
    data class AlreadyInUse(val email: String?) : LinkResult
    /** The person closed the Google picker. Nothing to say. */
    data object Cancelled : LinkResult
    /** Google sign-in can't work on this build or phone (no web client id, no Google account). One plain line. */
    data class Unavailable(val message: String) : LinkResult
    data class Failed(val message: String) : LinkResult
}

/** Who the caller is. Circles starts anonymous; "Keep your circles on any phone" links that account to Google. */
interface CirclesAuth {
    /** Signs in anonymously if needed and returns the uid. */
    suspend fun ensureSignedIn(): CirclesResult<String>
    /** A fresh ID token for the Worker. */
    suspend fun idToken(): CirclesResult<String>
    /** The uid if already signed in (no network). */
    val currentUid: String?
    /** True when the account is anonymous: a reinstall would lose membership. */
    val isAnonymous: Boolean
    /** The Google account's email when linked, else null. */
    val linkedEmail: String?
    /** Links the anonymous user to a Google account chosen in [activityContext] (an Activity). */
    suspend fun linkGoogle(activityContext: Context): LinkResult
    /** After [LinkResult.AlreadyInUse] and the person agreeing: signs in as that existing account. */
    suspend fun switchToLinkedAccount(): LinkResult
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

    override val linkedEmail: String? get() {
        val user = auth?.currentUser?.takeIf { !it.isAnonymous } ?: return null
        return user.email ?: user.providerData.firstNotNullOfOrNull { it.email }
    }

    private val googleSignIn by lazy { FirebaseAuthManager(context) }
    /** The Google credential that collided with another account, kept only until the person decides to switch. */
    @Volatile private var collided: AuthCredential? = null

    override suspend fun linkGoogle(activityContext: Context): LinkResult {
        val a = auth ?: return LinkResult.Unavailable("Circles isn't connected yet.")
        if (googleSignIn.authAvailability is AuthAvailability.NotConfigured) {
            return LinkResult.Unavailable("Google sign-in isn't set up in this version of the app yet.")
        }
        if (ensureSignedIn() is CirclesResult.Err) return LinkResult.Failed(CirclesFailure.signedOut.message)
        val user = a.currentUser ?: return LinkResult.Failed(CirclesFailure.signedOut.message)
        if (!user.isAnonymous) return LinkResult.Linked(linkedEmail)
        val idToken = googleSignIn.requestGoogleIdToken(activityContext).getOrElse { e ->
            return when (e) {
                is GetCredentialCancellationException -> LinkResult.Cancelled
                is NoCredentialException -> LinkResult.Unavailable("There's no Google account on this phone. Add one in Settings, then try again.")
                else -> LinkResult.Failed("Couldn't open Google sign-in. Try again in a moment.")
            }
        }
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return try {
            val result = user.linkWithCredential(credential).await()
            LinkResult.Linked(result.user?.email ?: linkedEmail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthUserCollisionException) {
            collided = e.updatedCredential ?: credential
            LinkResult.AlreadyInUse(e.email)
        } catch (e: Exception) {
            Log.w(TAG, "Linking failed: ${e.message}")
            LinkResult.Failed("Couldn't link your Google account. Check your connection and try again.")
        }
    }

    override suspend fun switchToLinkedAccount(): LinkResult {
        val a = auth ?: return LinkResult.Unavailable("Circles isn't connected yet.")
        val credential = collided ?: return LinkResult.Failed("Choose your Google account again to switch.")
        return try {
            val result = a.signInWithCredential(credential).await()
            collided = null
            LinkResult.Linked(result.user?.email)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Switching account failed: ${e.message}")
            collided = null
            LinkResult.Failed("Couldn't switch accounts. Choose your Google account again.")
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
