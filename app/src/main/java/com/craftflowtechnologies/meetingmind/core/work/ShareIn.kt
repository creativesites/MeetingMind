package com.craftflowtechnologies.meetingmind.core.work

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity
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
    private val WHATSAPP_PTT = Regex("""^(PTT-\d{8}-WA\d+|WhatsApp\s+Ptt\b).*""", RegexOption.IGNORE_CASE)
    private val WHATSAPP_AUDIO = Regex("""^(AUD-\d{8}-WA\d+|WhatsApp\s+Audio\b).*""", RegexOption.IGNORE_CASE)
    private val WHATSAPP_VIDEO = Regex("""^(VID-\d{8}-WA\d+|WhatsApp\s+Video\b).*""", RegexOption.IGNORE_CASE)
    private val WHATSAPP_MEDIA = Regex("""^(IMG-\d{8}-WA\d+|DOC-\d{8}-WA\d+|STK-\d{8}-WA\d+|WhatsApp\b).*""", RegexOption.IGNORE_CASE)
    private val TELEGRAM_MEDIA = Regex("""^(TG-\d+|Telegram\b|audio_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}).*""", RegexOption.IGNORE_CASE)

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
        @Suppress("DEPRECATION")
        val referrerUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_REFERRER)
        val rawSource = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
            ?: intent.getStringExtra("android.intent.extra.REFERRER_NAME")
            ?: referrerUri?.host?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra("calling_package")
            ?: intent.`package`
            ?: streams.firstOrNull()?.authority?.takeIf { it.isNotBlank() }
            ?: "Shared"
        val parts = ShareIntentParser.parse(
            action = intent.action,
            mime = intent.type,
            text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
            streams = streams.map { it.toString() to resolver.getType(it) },
            sourceApp = rawSource
        )
        if (parts.isEmpty()) return@withContext 0
        val added = InboxRepository(MeetMindDatabase.getInstance(context)).add(parts) { copy(context, it) }
        if (added.isNotEmpty()) DeepLinks.open(DeepLink.Inbox)
        added.size
    }

    /** Imports a single URI picked inside the app directly into the Work Inbox. */
    suspend fun importUriToInbox(
        context: Context,
        uri: Uri,
        sourceHint: String? = null
    ): List<InboxItemEntity> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        val kind = ShareIntentParser.kindOf(mime)
        val part = SharedPart(
            kind = kind,
            uri = uri.toString(),
            mime = mime,
            source = sourceHint
        )
        InboxRepository(MeetMindDatabase.getInstance(context)).add(listOf(part)) { copy(context, it, mime) }
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
        val isMedia = mimeType?.startsWith("audio/") == true || mimeType?.startsWith("video/") == true ||
            ext in setOf("mp3", "m4a", "ogg", "opus", "wav", "mp4", "mov", "webm", "mkv")
        val durationMs = if (isMedia) probeDurationMs(out.absolutePath) else null
        val inferredSource = inferSource(uri = uri, fileName = name, mime = mimeType, rawSource = null)
        CopiedFile(out.absolutePath, name, durationMs = durationMs, inferredSource = inferredSource)
    }.getOrNull()

    /**
     * Infers the human-friendly origin of an imported or shared item from its URI authority,
     * filename conventions (e.g. WhatsApp `PTT-YYYYMMDD-WAxxxx.opus`), MIME type, and raw source package.
     */
    internal fun inferSource(
        uri: String? = null,
        fileName: String? = null,
        mime: String? = null,
        rawSource: String? = null
    ): String? {
        val cleanName = fileName?.substringAfterLast('/')?.trim().orEmpty()
        val combined = listOfNotNull(uri, rawSource, cleanName).joinToString(" ")
        return when {
            WHATSAPP_PTT.matches(cleanName) -> "WhatsApp Voice Note"
            WHATSAPP_AUDIO.matches(cleanName) -> "WhatsApp Audio"
            WHATSAPP_VIDEO.matches(cleanName) -> "WhatsApp Video"
            WHATSAPP_MEDIA.matches(cleanName) -> "WhatsApp"
            combined.contains("whatsapp", ignoreCase = true) -> {
                when {
                    mime?.startsWith("audio/") == true || cleanName.endsWith(".opus", true) || cleanName.endsWith(".ogg", true) -> "WhatsApp Voice Note"
                    mime?.startsWith("video/") == true -> "WhatsApp Video"
                    else -> "WhatsApp"
                }
            }
            TELEGRAM_MEDIA.matches(cleanName) || combined.contains("telegram", ignoreCase = true) -> {
                if (mime?.startsWith("audio/") == true || cleanName.endsWith(".ogg", true)) "Telegram Voice Note" else "Telegram"
            }
            combined.contains("slack", ignoreCase = true) -> "Slack"
            combined.contains("com.google.android.gm", ignoreCase = true) || combined.contains("gmail", ignoreCase = true) -> "Gmail"
            combined.contains("com.microsoft.teams", ignoreCase = true) || combined.contains("teams", ignoreCase = true) -> "Microsoft Teams"
            combined.contains("com.google.android.apps.docs", ignoreCase = true) || combined.contains("drive", ignoreCase = true) -> "Google Drive"
            combined.contains("voicerecorder", ignoreCase = true) || combined.contains("soundrecorder", ignoreCase = true) -> "Voice Recorder"
            else -> rawSource?.takeIf { it.isNotBlank() }
        }
    }

    private fun probeDurationMs(path: String): Long? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0L }
        } finally {
            runCatching { retriever.release() }
        }
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
