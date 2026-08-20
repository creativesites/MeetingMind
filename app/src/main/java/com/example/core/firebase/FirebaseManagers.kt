package com.example.core.firebase

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.core.model.Meeting
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class FirebaseUserModel(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val isAnonymous: Boolean
)

/** Whether real Google/Firebase sign-in can be attempted on this build. */
sealed interface AuthAvailability {
    data object Available : AuthAvailability
    /** No google-services.json and/or no web client ID configured. Recording/local AI still work fully. */
    data class NotConfigured(val reason: String) : AuthAvailability
}

/**
 * Real Firebase Authentication with real Google Sign-In via Android Credential Manager:
 *
 * ```
 * Credential Manager -> Google ID credential -> Google ID token -> FirebaseAuth.signInWithCredential
 * ```
 *
 * Authentication is entirely optional and never blocks local recording/processing — see
 * docs/ARCHITECTURE.md "Local-First Authentication Policy". If Firebase is not configured
 * (no google-services.json) or no web client ID has been set (see
 * res/values/strings.xml:google_sign_in_web_client_id), sign-in honestly reports
 * [AuthAvailability.NotConfigured] instead of fabricating a signed-in user.
 */
class FirebaseAuthManager(private val context: Context) {
    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Auth not initialized or google-services.json missing; operating in local mode", e)
            null
        }
    }

    private val webClientId: String by lazy {
        try {
            context.getString(R.string.google_sign_in_web_client_id)
        } catch (e: Exception) {
            ""
        }
    }

    val authAvailability: AuthAvailability
        get() = when {
            auth == null -> AuthAvailability.NotConfigured(
                "Firebase is not configured for this build (no google-services.json)."
            )
            webClientId.isBlank() -> AuthAvailability.NotConfigured(
                "Google Sign-In is not configured (no web client ID set)."
            )
            else -> AuthAvailability.Available
        }

    private val _currentUser = MutableStateFlow<FirebaseUserModel?>(null)
    val currentUser: StateFlow<FirebaseUserModel?> = _currentUser.asStateFlow()

    init {
        auth?.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser?.toUserModel()
        }
        _currentUser.value = auth?.currentUser?.toUserModel()
    }

    private fun FirebaseUser.toUserModel(): FirebaseUserModel = FirebaseUserModel(
        uid = uid,
        displayName = displayName,
        email = email,
        photoUrl = photoUrl?.toString(),
        isAnonymous = isAnonymous
    )

    fun isUserSignedIn(): Boolean = auth?.currentUser != null

    /**
     * Launches the real Credential Manager Google Sign-In flow and completes Firebase Auth.
     * [activityContext] must be an Activity context (required to present the Credential
     * Manager UI) — an Application context will not work here.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUserModel> = withContext(Dispatchers.Main) {
        val firebaseAuth = auth
            ?: return@withContext Result.failure(IllegalStateException("Firebase is not configured for this build."))
        if (webClientId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Google Sign-In is not configured (no web client ID)."))
        }

        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(activityContext)
            val response = credentialManager.getCredential(activityContext, request)
            val credential = response.credential

            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return@withContext Result.failure(IllegalStateException("Unexpected credential type from Credential Manager."))
            }

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user?.toUserModel()
                ?: return@withContext Result.failure(IllegalStateException("Sign-in succeeded but no user was returned."))
            Result.success(user)
        } catch (e: NoCredentialException) {
            // The common case: no Google account is set up on this device, or the user
            // dismissed the account picker. Distinguished from other Credential Manager
            // failures so the caller can show "add a Google account" rather than a generic error.
            Log.i(TAG, "No Google credential available for sign-in", e)
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager sign-in failed", e)
            Result.failure(e)
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "Failed to parse Google ID token", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Error during sign out", e)
        }
    }

    private companion object {
        const val TAG = "FirebaseAuthManager"
    }
}

/**
 * Syncs only lightweight, non-content meeting metadata to Firestore — never audio or transcript
 * text. See docs/AUDIT.md privacy findings. This is opt-in and only called when a signed-in user
 * has enabled cloud sync (see UserPreferencesManager.cloudSyncEnabled).
 */
class FirestoreSyncManager {
    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w("FirestoreSyncManager", "Firestore not available; running offline-only", e)
            null
        }
    }

    suspend fun syncMeetingMetadata(userId: String, meeting: Meeting): Boolean = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext false
        try {
            val metadata = hashMapOf(
                "title" to meeting.title,
                "createdAt" to meeting.createdAt,
                "durationMs" to meeting.durationMs,
                "participantCount" to meeting.participantCount,
                "source" to meeting.source.name,
                "updatedAt" to System.currentTimeMillis()
            )

            db.collection("users")
                .document(userId)
                .collection("meetingMetadata")
                .document(meeting.id)
                .set(metadata, SetOptions.merge())
                .await()

            true
        } catch (e: Exception) {
            Log.e("FirestoreSyncManager", "Failed to sync metadata to cloud", e)
            false
        }
    }
}
