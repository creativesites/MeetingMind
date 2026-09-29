package com.craftflowtechnologies.meetingmind.ai.cloud

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What happened on every Gemini call, for testers to copy and send: when, which step, which
 * model, how long, the HTTP result and Gemini's own error message. Never a key, a prompt,
 * transcript text or audio — only the facts of the exchange. Kept on the phone, last 300 lines.
 */
object GeminiLog {
    private const val MAX_LINES = 300
    private val lines = ArrayDeque<String>()
    @Volatile private var file: File? = null
    private val time = SimpleDateFormat("MMM d HH:mm:ss", Locale.US)

    fun attach(context: Context) {
        if (file != null) return
        synchronized(this) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, "gemini_log.txt")
            runCatching { if (f.exists()) f.readLines().takeLast(MAX_LINES).forEach { lines.addLast(it) } }
            file = f
        }
    }

    fun add(message: String) {
        val line = "${synchronized(time) { time.format(Date()) }}  ${redact(message).replace('\n', ' ').take(600)}"
        synchronized(this) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
            runCatching { file?.writeText(lines.joinToString("\n") + "\n") }
        }
    }

    /** Newest first. */
    fun entries(): List<String> = synchronized(this) { lines.toList().asReversed() }

    fun clear() = synchronized(this) {
        lines.clear()
        runCatching { file?.delete() }
    }

    /** Everything, oldest first, headed with the app version — ready to paste into a message. */
    fun report(appVersion: String): String = buildString {
        appendLine("MeetingMind $appVersion — Gemini log")
        synchronized(this@GeminiLog) { lines.forEach { appendLine(it) } }
    }

    /** API keys never reach the log, even inside a URL or an echoed error. */
    private fun redact(s: String) = s.replace(Regex("AIza[0-9A-Za-z_\\-]{20,}"), "AIza…").replace(Regex("key=[^&\\s]+"), "key=…")
}
