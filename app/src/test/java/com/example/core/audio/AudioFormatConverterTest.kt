package com.example.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the real DSP math (mono downmix, linear resampling) used by [AudioFormatConverter].
 *
 * The full decode path (MediaExtractor/MediaCodec against a real audio file) cannot be
 * meaningfully unit tested on the JVM: Robolectric's MediaCodec/MediaExtractor shadows do not
 * perform real codec decoding, so a "passing" test there would not prove anything about actual
 * decoding correctness. That path requires a real device/emulator — see docs/AI_ARCHITECTURE.md
 * "Known Limitations". These tests instead exercise the actual sample-level algorithms directly,
 * which is real, meaningful coverage on top of them.
 */
class AudioFormatConverterTest {

    @Test
    fun `mono downmix averages interleaved stereo channels`() {
        // L, R pairs: (10, 20) -> 15 ; (100, -100) -> 0 ; (Short.MAX, Short.MAX) -> Short.MAX
        val stereo = shortArrayOf(10, 20, 100, -100, Short.MAX_VALUE, Short.MAX_VALUE)
        val mono = AudioFormatConverter.downmixToMono(stereo, channelCount = 2)

        assertEquals(3, mono.size)
        assertEquals(15, mono[0].toInt())
        assertEquals(0, mono[1].toInt())
        assertEquals(Short.MAX_VALUE, mono[2])
    }

    @Test
    fun `mono input is returned unchanged`() {
        val mono = shortArrayOf(1, 2, 3, 4)
        val result = AudioFormatConverter.downmixToMono(mono, channelCount = 1)
        assertTrue(mono === result || mono.contentEquals(result))
    }

    @Test
    fun `resample to the same rate is a no-op`() {
        val input = shortArrayOf(1, 2, 3, 4, 5)
        val output = AudioFormatConverter.linearResample(input, fromRate = 16000, toRate = 16000)
        assertTrue(input.contentEquals(output))
    }

    @Test
    fun `downsampling halves the sample count for a 2x rate ratio`() {
        val input = ShortArray(3200) { (it % 100).toShort() } // 3200 samples at 32kHz = 100ms
        val output = AudioFormatConverter.linearResample(input, fromRate = 32000, toRate = 16000)

        // Allow +/-1 for rounding at the boundary.
        assertTrue("expected ~1600 samples, got ${output.size}", kotlin.math.abs(output.size - 1600) <= 1)
    }

    @Test
    fun `upsampling preserves endpoint values`() {
        val input = shortArrayOf(1000, 2000, 3000, 4000)
        val output = AudioFormatConverter.linearResample(input, fromRate = 8000, toRate = 16000)

        assertEquals(input.first(), output.first())
        // The interpolated series should stay within the original value range.
        assertTrue(output.all { it in 900..4100 })
    }

    @Test
    fun `empty input produces empty output`() {
        val output = AudioFormatConverter.linearResample(ShortArray(0), fromRate = 44100, toRate = 16000)
        assertTrue(output.isEmpty())
    }
}
