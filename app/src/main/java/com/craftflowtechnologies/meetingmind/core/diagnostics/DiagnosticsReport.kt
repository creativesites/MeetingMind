package com.craftflowtechnologies.meetingmind.core.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One text report for the person to copy or share: the device, the last Java crash, how earlier
 * processes ended (including native crashes and memory kills), and the recent steps. This is what
 * to send when something goes wrong, so it does not depend on adb or a bug report.
 */
object DiagnosticsReport {
    /** Shares and clipboard both cope with a report this size. */
    private const val MAX_CHARS = 200_000
    private const val TRAIL_LINES = 400
    private const val EXIT_CHARS = 60_000

    fun build(context: Context, versionName: String): String {
        val app = context.applicationContext
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            appendLine("MeetingMind $versionName — diagnostics at $now")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Free memory now: ${freeMemoryMb(app)} MB")
            appendLine()

            appendLine("=== Last Java crash ===")
            appendLine(CrashLog.pending(app) ?: "None recorded.")
            appendLine()

            appendLine("=== How earlier processes ended (Android) ===")
            val exits = ExitHistory.text(app).takeLast(EXIT_CHARS)
            appendLine(exits.ifBlank { "None recorded since this feature was added." })
            appendLine()

            appendLine("=== Recent steps (newest last) ===")
            appendLine(Breadcrumbs.tail(TRAIL_LINES).ifBlank { "No steps recorded yet." })
        }
        return report.take(MAX_CHARS)
    }

    /** Removes everything in the report, so the next report starts fresh. */
    fun clear(context: Context) {
        val app = context.applicationContext
        CrashLog.clear(app)
        ExitHistory.clear(app)
        Breadcrumbs.clear()
    }

    private fun freeMemoryMb(context: Context): Long? =
        context.getSystemService(ActivityManager::class.java)?.let { am ->
            ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem / (1024L * 1024)
        }
}
