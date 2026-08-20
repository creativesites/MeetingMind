package com.example.core.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * All sharing goes through the standard Android Sharesheet (ACTION_SEND) — never a hardcoded
 * integration with a specific app. Whatever the user has installed that can handle text or audio
 * shows up automatically; MeetMind never claims a particular app will always appear, and never
 * shares anything without the user explicitly choosing to. This is an intentional privacy
 * boundary: nothing leaves the device except through this user-initiated flow.
 */
object ShareHelper {

    fun shareText(context: Context, subject: String, text: String, chooserTitle: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        launchChooser(context, sendIntent, chooserTitle)
    }

    /** Shares the recording's own audio file via a FileProvider content:// URI — the file never
     * leaves app-private storage except through this explicit, user-initiated share. */
    fun shareAudio(context: Context, audioFile: File, title: String) {
        if (!audioFile.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", audioFile)
        val mimeType = context.contentResolver.getType(uri) ?: guessAudioMimeType(audioFile.name)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchChooser(context, sendIntent, "Share Recording Audio")
    }

    private fun guessAudioMimeType(fileName: String): String = when {
        fileName.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
        fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
        fileName.endsWith(".wav", ignoreCase = true) -> "audio/wav"
        else -> "audio/*"
    }

    private fun launchChooser(context: Context, sendIntent: Intent, chooserTitle: String) {
        val chooser = Intent.createChooser(sendIntent, chooserTitle)
        if (context !is android.app.Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
