package com.example.feature.notes.editor

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.core.model.Attachment
import com.example.core.model.AttachmentKind
import com.example.core.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Brings media into a note.
 *
 * Everything is **copied** into the note's own folder. A note that pointed at a gallery photo
 * would break the day that photo was deleted or the phone was changed, and a private prayer
 * note's photo shouldn't depend on another app's storage.
 */
class MediaImporter(private val context: Context, private val notes: NoteRepository) {

    /** Copies [uri] into the note and records it. Null if the content could not be read. */
    suspend fun import(noteId: String, uri: Uri, captionHint: String? = null): Attachment? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: guessMime(uri)
        val kind = kindOf(mime)
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: extensionFromName(uri) ?: "bin"
        val id = NoteRepository.newId("att")
        val target = File(notes.attachmentsDir(noteId), "$id.$extension")
        val copied = runCatching {
            resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
        }.getOrNull()
        if (copied == null || !target.exists() || target.length() == 0L) {
            target.delete()
            return@withContext null
        }
        record(noteId, id, target, kind, mime, captionHint)
    }

    /** Records a file that was written straight into the note's folder, e.g. by the camera. */
    suspend fun adopt(noteId: String, file: File, mime: String): Attachment? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) { file.delete(); return@withContext null }
        record(noteId, file.nameWithoutExtension, file, kindOf(mime), mime, null)
    }

    /** A fresh file in the note's folder and a content:// Uri the camera app may write to. */
    fun newCaptureTarget(noteId: String, extension: String): Pair<File, Uri> {
        val file = File(notes.attachmentsDir(noteId), "${NoteRepository.newId("att")}.$extension")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareableUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private suspend fun record(noteId: String, id: String, file: File, kind: AttachmentKind, mime: String, caption: String?): Attachment {
        var width: Int? = null
        var height: Int? = null
        var duration: Long? = null
        when (kind) {
            AttachmentKind.IMAGE -> {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, o)
                width = o.outWidth.takeIf { it > 0 }
                height = o.outHeight.takeIf { it > 0 }
            }
            AttachmentKind.VIDEO, AttachmentKind.AUDIO -> runCatching {
                MediaMetadataRetriever().apply {
                    setDataSource(file.path)
                    duration = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    release()
                }
            }
            AttachmentKind.FILE -> Unit
        }
        return notes.addAttachment(
            Attachment(
                id = id,
                noteId = noteId,
                kind = kind,
                path = file.absolutePath,
                mimeType = mime,
                sizeBytes = file.length(),
                width = width,
                height = height,
                durationMs = duration,
                caption = caption,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun guessMime(uri: Uri): String =
        extensionFromName(uri)?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) } ?: "application/octet-stream"

    private fun extensionFromName(uri: Uri): String? {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
        return name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotEmpty() && it.length <= 5 }
    }

    companion object {
        fun kindOf(mime: String): AttachmentKind = when {
            mime.startsWith("image/") -> AttachmentKind.IMAGE
            mime.startsWith("video/") -> AttachmentKind.VIDEO
            mime.startsWith("audio/") -> AttachmentKind.AUDIO
            else -> AttachmentKind.FILE
        }
    }
}
