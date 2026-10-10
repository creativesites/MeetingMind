package com.craftflowtechnologies.meetingmind.core.circles2

import android.content.Context
import com.craftflowtechnologies.meetingmind.BuildConfig
import com.craftflowtechnologies.meetingmind.core.net.Net

/** Wires the real implementations. Screens get the repository from here; tests build their own with fakes. */
object Circles2 {
    @Volatile private var instance: CirclesRepository? = null

    fun repository(context: Context): CirclesRepository = instance ?: synchronized(this) {
        instance ?: build(context.applicationContext).also { instance = it }
    }

    private fun build(app: Context): CirclesRepository {
        val auth = FirebaseCirclesAuth(app)
        val api: CirclesApi = if (BuildConfig.CIRCLES_API_URL.isBlank()) NotConfiguredCirclesApi
        else HttpCirclesApi(BuildConfig.CIRCLES_API_URL, auth::idToken, Net.client(connectS = 10, readS = 25, writeS = 25))
        return CirclesRepository(api, FirestoreCirclesData(app), auth, SharedPrefsLocalCircleStore(app))
    }
}
