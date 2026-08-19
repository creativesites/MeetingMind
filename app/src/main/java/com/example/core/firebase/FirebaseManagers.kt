package com.example.core.firebase

import android.content.Context
import android.util.Log
import com.example.core.model.Meeting
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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

class FirebaseAuthManager(private val context: Context) {
    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w("FirebaseAuthManager", "Firebase Auth not initialized or google-services.json missing; operating in local mode", e)
            null
        }
    }

    private val _currentUser = MutableStateFlow<FirebaseUserModel?>(null)
    val currentUser: StateFlow<FirebaseUserModel?> = _currentUser.asStateFlow()

    init {
        auth?.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.value = user?.toUserModel()
        }
        _currentUser.value = auth?.currentUser?.toUserModel()
    }

    private fun FirebaseUser.toUserModel(): FirebaseUserModel {
        return FirebaseUserModel(
            uid = uid,
            displayName = displayName ?: "Offline User",
            email = email,
            photoUrl = photoUrl?.toString(),
            isAnonymous = isAnonymous
        )
    }

    fun isUserSignedIn(): Boolean = auth?.currentUser != null

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            auth?.signOut()
            _currentUser.value = null
        } catch (e: Exception) {
            Log.e("FirebaseAuthManager", "Error during sign out", e)
        }
    }

    /**
     * Local profile mockup when operating strictly offline or without Google Services configuration
     */
    fun setLocalMockProfile(name: String, email: String) {
        _currentUser.value = FirebaseUserModel(
            uid = "local_user_${System.currentTimeMillis()}",
            displayName = name,
            email = email,
            photoUrl = null,
            isAnonymous = false
        )
    }
}

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
