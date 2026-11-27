package com.craftflowtechnologies.meetingmind.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdtsAudioInspectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** Builds one valid 7-byte ADTS header + payload bytes for 16 kHz mono AAC-LC (freq index 8). */
    private fun adtsFrame(payloadSize: Int = 25, sampleRateIndex: Int = 8): ByteArray {
        val frameLen = 7 + payloadSize
        val frame = ByteArray(frameLen)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xF1.toByte() // MPEG-4, layer 0, protection_absent = 1
        frame[2] = ((1 shl 6) or ((sampleRateIndex and 0x0F) shl 2)).toByte() // AAC-LC
        frame[3] = (((frameLen shr 11) and 0x03) or (1 shl 6)).toByte() // mono + top 2 length bits
        frame[4] = ((frameLen shr 3) and 0xFF).toByte()
        frame[5] = (((frameLen and 0x07) shl 5) or 0x1F).toByte()
        frame[6] = 0xFC.toByte()
        return frame
    }

    @Test
    fun `inspectBytes counts complete ADTS frames and computes exact duration`() {
        val frames = ByteArray(0) + adtsFrame(25) + adtsFrame(30) + adtsFrame(20)
        val inspection = AdtsAudioInspector.inspectBytes(frames)

        assertTrue(inspection.isPlayable)
        assertEquals(0L, inspection.validStartOffset)
        assertEquals(frames.size.toLong(), inspection.validEndOffset)
        assertEquals(3L, inspection.validFrameCount)
        assertEquals(16_000, inspection.sampleRate)
        assertEquals((3L * 1024L * 1000L) / 16_000L, inspection.durationMs)
        assertFalse(inspection.hadTrailingPartialFrame)
    }

    @Test
    fun `trimToValidFrames salvages complete frames and strips truncated power-cut tail`() {
        val f1 = adtsFrame(25)
        val f2 = adtsFrame(25)
        val truncatedTail = adtsFrame(40).copyOf(18) // power died mid-frame
        val corrupted = ByteArray(0) + f1 + f2 + truncatedTail

        val file = tempFolder.newFile("capture.aac")
        file.writeBytes(corrupted)

        val trimmed = AdtsAudioInspector.trimToValidFrames(file)

        assertTrue(trimmed.isPlayable)
        assertEquals(2L, trimmed.validFrameCount)
        assertEquals((f1.size + f2.size).toLong(), file.length())
        assertFalse(trimmed.hadTrailingPartialFrame)
    }

    @Test
    fun `inspectBytes rejects pure noise without ADTS sync headers`() {
        val junk = ByteArray(256) { (it * 13).toByte() }
        val inspection = AdtsAudioInspector.inspectBytes(junk)

        assertFalse(inspection.isPlayable)
        assertEquals(0L, inspection.validFrameCount)
        assertEquals(0L, inspection.durationMs)
    }
}
