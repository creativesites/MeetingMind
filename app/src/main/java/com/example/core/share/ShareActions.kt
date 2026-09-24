package com.example.core.share

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Where a card can go. */
enum class ShareTarget(val label: String) { ANY("Share…"), WHATSAPP("WhatsApp"), INSTAGRAM_STORY("Instagram Story"), SAVE("Save to photos") }

/**
 * Sends a finished card to other apps (PLAN_V2 F4). Everything goes through a FileProvider URI,
 * so no other app gets more than the one image it was handed.
 */
object ShareActions {

    fun saveToCache(context: Context, bitmap: Bitmap, name: String = "meetingmind_${System.currentTimeMillis()}"): File {
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        // Old cards are only needed until the target app has read them.
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Returns a message for the person when the target isn't available, else null. */
    fun send(context: Context, file: File, target: ShareTarget, caption: String? = null, mime: String = "image/png"): String? {
        val uri = uriFor(context, file)
        return when (target) {
            ShareTarget.SAVE -> if (saveToGallery(context, file)) "Saved to your photos." else "Couldn't save to your photos."
            ShareTarget.INSTAGRAM_STORY -> {
                val intent = Intent("com.instagram.share.ADD_TO_STORY")
                    .setDataAndType(uri, mime)
                    .setPackage("com.instagram.android")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                context.grantUriPermission("com.instagram.android", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try { context.startActivity(intent); null } catch (e: ActivityNotFoundException) { chooser(context, uri, mime, caption); null }
            }
            ShareTarget.WHATSAPP -> {
                val intent = send(uri, mime, caption).setPackage("com.whatsapp")
                try { context.startActivity(intent); null } catch (e: ActivityNotFoundException) {
                    try { context.startActivity(send(uri, mime, caption).setPackage("com.whatsapp.w4b")); null } catch (e2: ActivityNotFoundException) { chooser(context, uri, mime, caption); null }
                }
            }
            ShareTarget.ANY -> { chooser(context, uri, mime, caption); null }
        }
    }

    private fun send(uri: Uri, mime: String, caption: String?) = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .apply { caption?.let { putExtra(Intent.EXTRA_TEXT, it) } }
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun chooser(context: Context, uri: Uri, mime: String, caption: String?) {
        context.startActivity(Intent.createChooser(send(uri, mime, caption), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun saveToGallery(context: Context, file: File): Boolean = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MeetingMind")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return false
        true
    }.getOrDefault(false)

    /** Shares an audio file (a devotional's voice) — to WhatsApp it arrives as an audio message. */
    fun shareAudio(context: Context, file: File, caption: String?, target: ShareTarget = ShareTarget.ANY): String? {
        val shareable = File(File(context.cacheDir, "shares").apply { mkdirs() }, file.name).also { file.copyTo(it, overwrite = true) }
        val mime = if (file.extension == "m4a") "audio/mp4" else "audio/wav"
        return send(context, shareable, if (target == ShareTarget.SAVE || target == ShareTarget.INSTAGRAM_STORY) ShareTarget.ANY else target, caption, mime)
    }
}
