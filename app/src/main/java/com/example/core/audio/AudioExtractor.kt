package com.example.core.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class ImportedMediaInfo(
    val fileName: String,
    val durationMs: Long,
    val mimeType: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val outputFile: File
)

class AudioExtractor(private val context: Context) {

    suspend fun importAndExtract(uri: Uri, meetingId: String): ImportedMediaInfo = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        var fileName = "imported_meeting_${System.currentTimeMillis()}"
        var sizeBytes = 0L

        // Query file metadata via ContentResolver
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
            }
        }

        val mimeType = contentResolver.getType(uri) ?: "audio/*"
        val isVideo = mimeType.startsWith("video/")

        val meetingDir = File(context.filesDir, "meetings/$meetingId").apply { mkdirs() }
        val extension = when {
            fileName.endsWith(".wav", ignoreCase = true) -> "wav"
            fileName.endsWith(".mp3", ignoreCase = true) -> "mp3"
            fileName.endsWith(".aac", ignoreCase = true) -> "aac"
            else -> "m4a"
        }
        val targetAudioFile = File(meetingDir, "audio.$extension")

        // Copy source stream safely to app-private storage
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetAudioFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not open input stream from selected URI")

        // Retrieve duration metadata
        var durationMs = 0L
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.fromFile(targetAudioFile))
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L
            retriever.release()
        } catch (e: Exception) {
            Log.w("AudioExtractor", "Failed to retrieve exact duration via MediaMetadataRetriever", e)
            if (durationMs <= 0L && targetAudioFile.length() > 0) {
                // Estimate ~128kbps fallback duration
                durationMs = (targetAudioFile.length() * 8 / 128)
            }
        }

        ImportedMediaInfo(
            fileName = fileName,
            durationMs = if (durationMs > 0) durationMs else 60000L,
            mimeType = mimeType,
            isVideo = isVideo,
            sizeBytes = targetAudioFile.length(),
            outputFile = targetAudioFile
        )
    }
}
