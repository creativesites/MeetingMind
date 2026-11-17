package com.craftflowtechnologies.meetingmind.ai.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Speech audio from the network can be truncated or corrupt: reading it must never throw or hang. */
class WavSafetyTest {

    private fun header(fmtSize: Int, dataSize: Int, rate: Int = 24_000, extra: ByteArray = ByteArray(0)): ByteArray {
        val b = ByteBuffer.allocate(44 + extra.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + extra.size).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(fmtSize).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(dataSize)
        b.put(extra)
        return b.array()
    }

    @Test
    fun `a good file reads`() {
        val pcm = Wav.read(header(16, 4, extra = byteArrayOf(1, 0, 2, 0)))
        assertNotNull(pcm)
        assertEquals(2, pcm!!.samples.size)
    }

    @Test
    fun `a fmt chunk cut short returns null instead of throwing`() {
        // A 16-byte "junk" chunk, then a "fmt " chunk whose body is past the end of the file.
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36).put("WAVE".toByteArray())
        b.put("junk".toByteArray()).putInt(16).put(ByteArray(16))
        b.put("fmt ".toByteArray()).putInt(16)
        assertNull(Wav.read(b.array()))
    }

    @Test
    fun `a negative chunk size returns null instead of looping`() {
        val bytes = header(16, 0).also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(16, -8) }
        assertNull(Wav.read(bytes))
    }

    @Test
    fun `a zero sample rate gives zero duration, not a crash`() {
        assertEquals(0L, Pcm(ShortArray(10), 0).durationMs)
        assertNull(Wav.read(header(16, 4, rate = 0, extra = byteArrayOf(1, 0, 2, 0))))
    }
}
