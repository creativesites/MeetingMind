package com.craftflowtechnologies.meetingmind

import android.app.Application
import com.craftflowtechnologies.meetingmind.core.diagnostics.CrashLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

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
        removeTeachBackFiles()
        setUpReadToMe()
    }

    /**
     * Read to Me's saved readings: resume after the process was killed, remember where each reading was
     * left, and keep the cache within its size and age limits. All of it off the main thread.
     */
    private fun setUpReadToMe() {
        val store = com.craftflowtechnologies.meetingmind.ai.voice.read.ReadingStore.get(this)
        com.craftflowtechnologies.meetingmind.core.audio.PlaybackResumption.source = com.craftflowtechnologies.meetingmind.ai.voice.read.ReadingResumptionSource(store)
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        com.craftflowtechnologies.meetingmind.core.audio.PlaybackController.progressListener = { progress ->
            io.launch { runCatching { store.saveProgress(progress) } }
        }
        io.launch {
            runCatching {
                store.purgeExpiredEphemeral(com.craftflowtechnologies.meetingmind.ai.voice.read.ReadingStore.EPHEMERAL_TTL_MS)
                store.trimTo(com.craftflowtechnologies.meetingmind.ai.voice.read.ReadingStore.MAX_BYTES)
            }
        }
    }

    /**
     * The removed Learning feature (Teach-Back) kept audio and explanations in filesDir/teach_back.
     * Delete that directory once, off the main thread; the exists() check makes later launches a single stat.
     */
    private fun removeTeachBackFiles() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val dir = File(filesDir, "teach_back")
                if (dir.exists()) dir.deleteRecursively()
            }
        }
    }
}
