package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.notify.DeepLink
import com.craftflowtechnologies.meetingmind.core.notify.DeepLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The share target (docs/PLAN_PROFESSIONAL.md D7): text, links, PDFs, audio and images shared into
 * MeetingMind become Inbox items. Files are copied into the app's own storage first, so nothing
 * depends on the sender's permission. Nothing is filed: the person confirms in the Inbox.
 */
object ShareIn {
    fun isShare(intent: Intent?): Boolean = intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE

    /** Reads the intent, stores what it carries, and asks the app to open the Inbox. Returns how many items were added. */
    suspend fun handle(context: Context, intent: Intent): Int = withContext(Dispatchers.IO) {
        val streams = mutableListOf<Uri>()
        @Suppress("DEPRECATION")
        when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { streams += it }
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { streams += it }
        }
        val resolver = context.contentResolver
        val sourceApp = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
            ?: intent.getStringExtra("android.intent.extra.REFERRER_NAME")
            ?: intent.getStringExtra("calling_package")
            ?: intent.`package`
            ?: "Shared"
        val parts = ShareIntentParser.parse(
            action = intent.action,
            mime = intent.type,
            text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
            streams = streams.map { it.toString() to resolver.getType(it) },
            sourceApp = sourceApp
        )
        if (parts.isEmpty()) return@withContext 0
        val added = InboxRepository(MeetMindDatabase.getInstance(context)).add(parts) { copy(context, it) }
        if (added.isNotEmpty()) DeepLinks.open(DeepLink.Inbox)
        added.size
    }

    /** Fire and forget from an activity, with a confirmation toast. */
    fun handleInBackground(context: Context, intent: Intent, scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            val n = handle(context, intent)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, if (n == 0) "Nothing to add to your inbox" else "Saved to your Work inbox", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun copy(context: Context, uri: String, mime: String? = null): CopiedFile? = runCatching {
        val u = Uri.parse(uri)
        val resolver = context.contentResolver
        val mimeType = mime ?: resolver.getType(u)
        var name = resolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: u.lastPathSegment ?: "shared"
        val ext = extensionFor(name, mimeType)
        if (!name.contains('.') && ext.isNotEmpty()) {
            name = "$name.$ext"
        }
        val dir = File(context.filesDir, "inbox").apply { mkdirs() }
        val out = File(dir, "${UUID.randomUUID()}_${name.replace('/', '_')}")
        resolver.openInputStream(u)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return null
        CopiedFile(out.absolutePath, name)
    }.getOrNull()

    internal fun extensionFor(name: String, mime: String?): String {
        val existing = name.substringAfterLast('.', "").trim().lowercase()
        if (existing.isNotEmpty() && existing.length in 2..5 && existing.all { it.isLetterOrDigit() }) {
            return existing
        }
        return when (mime?.lowercase()) {
            "video/mp4" -> "mp4"
            "video/quicktime" -> "mov"
            "video/webm" -> "webm"
            "video/x-matroska" -> "mkv"
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
            "audio/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/wav", "audio/x-wav" -> "wav"
            "application/pdf" -> "pdf"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> when {
                mime?.startsWith("video/") == true -> "mp4"
                mime?.startsWith("audio/") == true -> "m4a"
                mime?.startsWith("image/") == true -> "png"
                else -> ""
            }
        }
    }
}
