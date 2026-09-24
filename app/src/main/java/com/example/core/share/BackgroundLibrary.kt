package com.example.core.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** A picture in the library: one of the built-in photos, or one the person added. */
data class LibraryImage(
    val id: String,
    val name: String,
    /** A file on the phone, ready for drawing and for Coil. */
    val file: File,
    val thumb: File,
    val credit: String?,
    val userAdded: Boolean
)

/**
 * The backgrounds used across Faith (PLAN_V2 F4): the built-in photos (Unsplash licence, credited
 * in THIRD_PARTY_NOTICES) and the person's own. Used for share cards, stories and the devotional
 * when there's no picture of its own.
 */
object BackgroundLibrary {
    private data class BuiltIn(val id: String, val name: String, val asset: String, val thumb: String, val author: String)

    @Volatile private var builtIns: List<BuiltIn>? = null

    private fun builtIns(context: Context): List<BuiltIn> = builtIns ?: runCatching {
        val a = JSONArray(context.assets.open("backgrounds/index.json").bufferedReader().readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            BuiltIn(o.getString("id"), o.getString("name"), o.getString("file"), o.getString("thumb"), o.optString("author"))
        }
    }.getOrDefault(emptyList()).also { builtIns = it }

    private fun builtInDir(context: Context) = File(context.filesDir, "backgrounds/builtin").apply { mkdirs() }
    private fun userDir(context: Context) = File(context.filesDir, "backgrounds/mine").apply { mkdirs() }

    /** Copies an asset out once, so everything can treat it as an ordinary file. */
    private fun materialise(context: Context, asset: String): File {
        val target = File(builtInDir(context), asset.substringAfterLast('/'))
        if (!target.exists() || target.length() == 0L) runCatching {
            context.assets.open(asset).use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        return target
    }

    fun builtIn(context: Context): List<LibraryImage> = builtIns(context).map { b ->
        LibraryImage(b.id, b.name, materialise(context, b.asset), materialise(context, b.thumb), "Photo: ${b.author} (Unsplash)", userAdded = false)
    }

    fun mine(context: Context): List<LibraryImage> = userDir(context).listFiles { f -> f.extension == "jpg" && !f.name.endsWith("_thumb.jpg") }
        .orEmpty().sortedByDescending { it.lastModified() }
        .map { f -> LibraryImage("mine_${f.nameWithoutExtension}", "Your picture", f, File(f.parentFile, "${f.nameWithoutExtension}_thumb.jpg").takeIf { it.exists() } ?: f, null, userAdded = true) }

    /** Everything, the person's own first. */
    fun all(context: Context): List<LibraryImage> = mine(context) + builtIn(context)

    /** Adds a picture from the photo picker, scaled to a sensible size. Null if it can't be read. */
    fun add(context: Context, uri: Uri): LibraryImage? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1920) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val name = UUID.randomUUID().toString().take(12)
        val file = File(userDir(context), "$name.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        val scale = 360f / maxOf(bitmap.width, bitmap.height)
        val thumb = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        File(userDir(context), "${name}_thumb.jpg").outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        bitmap.recycle(); thumb.recycle()
        mine(context).firstOrNull { it.file == file }
    }.getOrNull()

    fun remove(image: LibraryImage) {
        if (!image.userAdded) return
        image.file.delete(); image.thumb.delete()
    }

    /** A photo for a day and a slot — varied across days, stable within one. */
    fun forDay(context: Context, epochDay: Long, salt: Int = 0): LibraryImage? {
        val pool = all(context).ifEmpty { return null }
        return pool[Math.floorMod(epochDay * 5 + salt * 7, pool.size.toLong()).toInt()]
    }
}
