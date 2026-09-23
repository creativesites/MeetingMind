package com.example.core.identity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The person's avatar: a photo they pick, centre-cropped to a square, kept in app storage.
 * Never uploaded anywhere.
 */
object AvatarStore {
    private const val SIZE = 512

    /** Saves [uri] as the avatar and returns its file path, or null if it couldn't be read. */
    suspend fun save(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= SIZE && bounds.outHeight / (sample * 2) >= SIZE) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val rotation = resolver.openInputStream(uri)?.use { runCatching {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }.getOrDefault(0) } ?: 0
            val square = centerSquare(decoded, rotation)
            val dir = File(context.filesDir, "profile").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            file.absolutePath
        }.getOrNull()
    }

    fun remove(context: Context) {
        File(context.filesDir, "profile").listFiles()?.forEach { it.delete() }
    }

    internal fun centerSquare(source: Bitmap, rotation: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val matrix = Matrix().apply {
            val scale = SIZE.toFloat() / side
            postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side, matrix, true)
    }
}
