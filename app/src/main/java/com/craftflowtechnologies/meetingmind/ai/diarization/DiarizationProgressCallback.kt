package com.craftflowtechnologies.meetingmind.ai.diarization

import java.util.concurrent.atomic.AtomicBoolean

/**
 * The progress callback handed to sherpa-onnx's native diarization.
 *
 * sherpa-onnx's JNI looks the callback up by name and exact signature, `invoke(IIJ)Ljava/lang/Integer;`,
 * on the object's own class. A Kotlin lambda can't be used here: Kotlin compiles it with invokedynamic,
 * and D8/R8 turn that into a synthetic class that only has the erased `invoke(Object, Object, Object)`.
 * The native lookup then fails with NoSuchMethodError and aborts the whole process. That crashed the
 * app every time a recording reached the speakers step (v46, November 2026).
 *
 * A named class keeps a real `Integer invoke(int, int, long)` method, and proguard-rules.pro keeps it
 * from being renamed or removed in release. DiarizationProgressCallbackTest guards the signature.
 */
internal class DiarizationProgressCallback(
    private val onProgress: (Float) -> Unit,
    private val abandoned: AtomicBoolean,
) : (Int, Int, Long) -> Int {
    override fun invoke(processed: Int, total: Int, arg: Long): Int {
        if (total > 0) onProgress((processed.toFloat() / total).coerceIn(0f, 1f))
        // Best effort: a non-zero return asks sherpa-onnx to stop early.
        return if (abandoned.get()) 1 else 0
    }
}
