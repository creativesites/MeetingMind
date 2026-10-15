package com.craftflowtechnologies.meetingmind.core.circles2

import android.content.Context
import android.util.Log
import com.craftflowtechnologies.meetingmind.BuildConfig
import com.craftflowtechnologies.meetingmind.core.net.Net
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.util.concurrent.CancellationException

/** Wires the real implementations. Screens get the repository from here; tests build their own with fakes. */
object Circles2 {
    @Volatile private var instance: CirclesRepository? = null
    @Volatile private var tokens: PushTokenManager? = null

    fun repository(context: Context): CirclesRepository = instance ?: synchronized(this) {
        instance ?: build(context.applicationContext).also { instance = it }
    }

    /** The push token manager, for the messaging service (a refreshed token can arrive with no screen open). */
    fun pushTokens(context: Context): PushTokenManager {
        repository(context)
        return checkNotNull(tokens)
    }

    private fun build(app: Context): CirclesRepository {
        val auth = FirebaseCirclesAuth(app)
        val api: CirclesApi = if (BuildConfig.CIRCLES_API_URL.isBlank()) NotConfiguredCirclesApi
        else HttpCirclesApi(BuildConfig.CIRCLES_API_URL, auth::idToken, Net.client(connectS = 10, readS = 25, writeS = 25))
        val manager = PushTokenManager(api, SharedPrefsPushTokenStore(app)) { currentFcmToken(app) }
        tokens = manager
        CirclesNotifier.ensureChannel(app)
        return CirclesRepository(api, FirestoreCirclesData(app), auth, SharedPrefsLocalCircleStore(app), push = manager)
    }

    private suspend fun currentFcmToken(app: Context): String? = try {
        if (FirebaseApp.getApps(app).isEmpty()) FirebaseApp.initializeApp(app)
        FirebaseMessaging.getInstance().token.await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Circles2", "No push token yet: ${e.message}")
        null
    }
}
