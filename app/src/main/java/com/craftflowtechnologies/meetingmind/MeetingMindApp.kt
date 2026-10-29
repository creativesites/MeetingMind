package com.craftflowtechnologies.meetingmind

import android.app.Application
import android.os.Build
import com.craftflowtechnologies.meetingmind.core.diagnostics.Breadcrumbs
import com.craftflowtechnologies.meetingmind.core.diagnostics.CrashLog
import com.craftflowtechnologies.meetingmind.core.diagnostics.ExitHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import com.craftflowtechnologies.meetingmind.core.billing.Billing
import com.craftflowtechnologies.meetingmind.core.billing.DisabledBillingGateway
import com.craftflowtechnologies.meetingmind.core.billing.RevenueCatBillingGateway
import com.craftflowtechnologies.meetingmind.core.billing.SimulatedBillingGateway

/**
 * Installs the crash log before anything else runs, so a crash in a reminder, a widget, a worker
 * or the recording service is kept too, not only one in the main screen.
 */
class MeetingMindApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this, BuildConfig.VERSION_NAME)
        Breadcrumbs.init(this)
        Breadcrumbs.log("app", "start ${BuildConfig.VERSION_NAME}")
        // The app runs in more than one process (speaker separation has its own). Only the main
        // process records exits, so the same exit is not written twice.
        if (isMainProcess()) CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { ExitHistory.record(this@MeetingMindApp) }
        runCatching {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
        }
        removeTeachBackFiles()
        setUpReadToMe()
        // Billing is set up once per process. With a RevenueCat key the Play store is used. A debug build
        // without one gets the simulated store, so the paywall can be tried on a device. A release build
        // without a key is simply free: nothing is offered and nothing breaks.
        val key = BuildConfig.REVENUECAT_API_KEY
        Billing.gateway = when {
            key.isNotBlank() -> runCatching { RevenueCatBillingGateway(this, key) as com.craftflowtechnologies.meetingmind.core.billing.BillingGateway }
                .getOrDefault(DisabledBillingGateway)
            BuildConfig.DEBUG -> SimulatedBillingGateway(this)
            else -> DisabledBillingGateway
        }
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

    /** True in the app's own process; false in the speaker-separation process. */
    private fun isMainProcess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P || getProcessName() == packageName
}
