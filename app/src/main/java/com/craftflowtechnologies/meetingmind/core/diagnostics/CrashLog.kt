package com.craftflowtechnologies.meetingmind.core.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash so the next launch can show it (and let the person copy it), because a phone
 * has no other easy way to get a stack trace off it. Nothing leaves the phone: it is one small text
 * file in the app's own storage, and the person chooses whether to copy it anywhere.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    private const val MAX_CHARS = 12_000
    private var installed = false

    fun install(context: Context, versionName: String) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(app.filesDir, FILE).writeText(report(thread, error, versionName)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved crash, if the last run ended in one. */
    fun pending(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }?.takeIf { it.isNotBlank() }

    fun clear(context: Context) { File(context.filesDir, FILE).delete() }

    internal fun report(thread: Thread, error: Throwable, versionName: String, now: Long = System.currentTimeMillis()): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))
        return buildString {
            appendLine("MeetingMind $versionName — crash at $stamp")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(trace)
        }.take(MAX_CHARS)
    }
}
