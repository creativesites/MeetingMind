package com.craftflowtechnologies.meetingmind.core.documents

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device text recognition (ML Kit v2, Latin script) for scanned pages and images. The model is
 * delivered by Play services (the unbundled artifact), so nothing is added to the APK. Nothing is
 * sent off the phone.
 *
 * Create one per read, use it from a background dispatcher, and [close] it. ML Kit v2 exposes no
 * per-word confidence, so results carry null confidence.
 */
class OcrRecognizer : Closeable {
    private var client: TextRecognizer? = null

    @Synchronized
    private fun client(): TextRecognizer =
        client ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { client = it }

    /** Recognises [bitmap]. Boxes are normalised by the bitmap's size. Throws when Play services fails. */
    suspend fun recognise(bitmap: Bitmap): OcrText {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = client().process(image).awaitResult()
        val width = bitmap.width.coerceAtLeast(1).toDouble()
        val height = bitmap.height.coerceAtLeast(1).toDouble()

        val text = StringBuilder()
        val boxes = ArrayList<PageBox>()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.trim()
                if (lineText.isEmpty()) continue
                text.append(lineText).append('\n')
                val rect = line.boundingBox ?: continue
                PageBox.clamped(
                    left = rect.left / width,
                    top = rect.top / height,
                    width = rect.width() / width,
                    height = rect.height() / height,
                    text = lineText
                )?.let { boxes += it }
            }
        }
        return OcrText(text.toString().trimEnd(), boxes, confidence = null)
    }

    @Synchronized
    override fun close() {
        client?.close()
        client = null
    }
}

/** Waits for a Play services task without the coroutines play-services artifact. */
internal suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> continuation.resume(value) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
