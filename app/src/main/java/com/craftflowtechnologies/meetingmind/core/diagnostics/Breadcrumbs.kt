package com.craftflowtechnologies.meetingmind.core.diagnostics

import android.app.ActivityManager
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A short, rolling trail of what the app was doing: pipeline steps, model loads, progress marks.
 * Each line is written to disk when it happens, so a native crash or a kill by the system still
 * leaves the last steps behind. It also records heap and free system memory, because a memory
 * kill is the usual reason a crash leaves no stack trace.
 *
 * Nothing leaves the phone. The person sends it only through Settings → Diagnostics.
 */
object Breadcrumbs {
    private const val DIR = "diagnostics"
    private const val FILE = "trail.log"
    private const val ROTATED = "trail.log.old"
    private const val MAX_BYTES = 256L * 1024
    private const val MB = 1024L * 1024

    private val lock = Any()
    private var dir: File? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        dir = File(app.filesDir, DIR).also { it.mkdirs() }
    }

    fun log(tag: String, message: String) {
        val d = dir ?: return
        val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$time [$tag] $message | ${memoryNote()}\n"
        synchronized(lock) {
            runCatching {
                val file = File(d, FILE)
                if (file.length() > MAX_BYTES) file.renameTo(File(d, ROTATED))
                file.appendText(line)
            }
        }
    }

    /** The newest [maxLines] lines, oldest first, across the rotated and current file. */
    fun tail(maxLines: Int): String {
        val d = dir ?: return ""
        val text = synchronized(lock) {
            listOf(File(d, ROTATED), File(d, FILE))
                .filter { it.isFile }
                .joinToString("") { runCatching { it.readText() }.getOrDefault("") }
        }
        return text.lines().filter { it.isNotBlank() }.takeLast(maxLines).joinToString("\n")
    }

    fun clear() {
        val d = dir ?: return
        synchronized(lock) {
            File(d, FILE).delete()
            File(d, ROTATED).delete()
        }
    }

    private fun memoryNote(): String {
        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / MB
        val heapMax = runtime.maxMemory() / MB
        val avail = appContext?.getSystemService(ActivityManager::class.java)?.let { am ->
            ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem / MB
        }
        return "heap ${heapUsed}/${heapMax} MB, system free ${avail ?: "?"} MB"
    }
}
