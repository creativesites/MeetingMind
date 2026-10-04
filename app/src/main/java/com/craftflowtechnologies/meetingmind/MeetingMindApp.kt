package com.craftflowtechnologies.meetingmind

import android.app.Application
import com.craftflowtechnologies.meetingmind.core.diagnostics.CrashLog

/**
 * Installs the crash log before anything else runs, so a crash in a reminder, a widget, a worker
 * or the recording service is kept too, not only one in the main screen.
 */
class MeetingMindApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this, BuildConfig.VERSION_NAME)
        runCatching {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
        }
    }
}
