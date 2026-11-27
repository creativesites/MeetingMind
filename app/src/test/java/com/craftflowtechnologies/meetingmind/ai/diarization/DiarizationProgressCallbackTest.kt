package com.craftflowtechnologies.meetingmind.ai.diarization

import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Test

class DiarizationProgressCallbackTest {

    @Test
    fun `the callback has the exact method sherpa-onnx looks up natively`() {
        // sherpa-onnx's JNI calls GetMethodID(cls, "invoke", "(IIJ)Ljava/lang/Integer;").
        val method = DiarizationProgressCallback::class.java.getMethod(
            "invoke", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType
        )
        assertEquals(Integer::class.java, method.returnType)
    }

    @Test
    fun `it reports progress and asks to stop once abandoned`() {
        val seen = mutableListOf<Float>()
        val abandoned = AtomicBoolean(false)
        val callback = DiarizationProgressCallback({ seen += it }, abandoned)

        assertEquals(0, callback(1, 4, 0L))
        abandoned.set(true)
        assertEquals(1, callback(4, 4, 0L))
        assertEquals(1, callback(0, 0, 0L)) // no total yet: no progress reported
        assertEquals(listOf(0.25f, 1f), seen)
    }
}
