package com.craftflowtechnologies.meetingmind.core.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records how earlier processes of this app ended, as Android saw it. A native crash or a kill for
 * low memory never reaches the Java crash handler, so this is the only record of those. Android
 * keeps the exit reasons and, for native crashes, the native trace. Only exits not seen before are
 * written, and the write happens off the main thread.
 *
 * Needs Android 11 (API 30). On older devices it does nothing.
 */
object ExitHistory {
    private const val FILE = "exits.log"
    private const val PREFS = "diagnostics"
    private const val KEY_SEEN_UNTIL = "exits_seen_until_ms"
    private const val MAX_TRACE_CHARS = 16_000
    private const val MAX_EXITS = 16

    fun record(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val app = context.applicationContext
        val am = app.getSystemService(ActivityManager::class.java) ?: return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seenUntil = prefs.getLong(KEY_SEEN_UNTIL, 0L)
        val exits = runCatching { am.getHistoricalProcessExitReasons(app.packageName, 0, MAX_EXITS) }.getOrNull() ?: return

        val fresh = exits.filter { it.timestamp > seenUntil }.sortedBy { it.timestamp }
        if (fresh.isEmpty()) return

        val out = StringBuilder()
        for (exit in fresh) {
            out.append(describe(exit))
            if (exit.reason in TRACE_REASONS) {
                exit.traceInputStream?.use { stream ->
                    val bytes = stream.readBytes()
                    out.append(String(bytes, Charsets.UTF_8).take(MAX_TRACE_CHARS)).append('\n')
                }
            }
            out.append('\n')
        }
        runCatching { File(app.filesDir, "diagnostics").also { it.mkdirs() }.resolve(FILE).appendText(out.toString()) }
        prefs.edit().putLong(KEY_SEEN_UNTIL, fresh.last().timestamp).apply()
        Breadcrumbs.log("exit", "recorded ${fresh.size} earlier process exit(s)")
    }

    fun text(context: Context): String =
        runCatching { File(context.filesDir, "diagnostics/$FILE").readText() }.getOrDefault("")

    fun clear(context: Context) {
        File(context.filesDir, "diagnostics/$FILE").delete()
    }

    private fun describe(exit: ApplicationExitInfo): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(exit.timestamp))
        return buildString {
            appendLine("$stamp  reason=${reasonName(exit.reason)}  status=${exit.status}  importance=${exit.importance}")
            if (!exit.description.isNullOrBlank()) appendLine("  description: ${exit.description}")
            append("  memory: rss ${exit.rss / 1024} MB, pss ${exit.pss / 1024} MB")
        }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH (Java)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY (killed by the system)"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        else -> "REASON_$reason"
    }

    private val TRACE_REASONS = setOf(
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_CRASH
    )
}
