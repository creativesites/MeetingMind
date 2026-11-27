package com.craftflowtechnologies.meetingmind.core.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * Pure-Kotlin inspector and repair utility for raw AAC ADTS (`capture.aac`) streams.
 *
 * Unlike monolithic MPEG-4 containers (whose `moov` index atom is written only on a clean
 * `MediaRecorder.stop()`), every ADTS frame carries its own 7/9-byte sync header and frame
 * length. If the device loses power or kills the process mid-recording, valid frames flushed to
 * flash before power loss remain independently decodable, and any truncated trailing frame can
 * be trimmed cleanly at the byte boundary.
 */
object AdtsAudioInspector {

    private val SAMPLE_RATES = intArrayOf(
        96_000, 88_200, 64_000, 48_000, 44_100, 32_000,
        24_000, 22_050, 16_000, 12_000, 11_025, 8_000, 7_350
    )

    private const val SAMPLES_PER_AAC_FRAME = 1024L
    private const val MAX_ADTS_FRAME_BYTES = 8192

    data class Inspection(
        val validStartOffset: Long,
        val validEndOffset: Long,
        val validFrameCount: Long,
        val sampleRate: Int,
        val durationMs: Long,
        val hadTrailingPartialFrame: Boolean
    ) {
        val validByteCount: Long get() = (validEndOffset - validStartOffset).coerceAtLeast(0L)
        val isPlayable: Boolean get() = validFrameCount > 0 && validByteCount > 0
    }

    /** Inspects an in-memory ADTS byte sequence. Useful for unit tests and small buffers. */
    fun inspectBytes(data: ByteArray): Inspection {
        if (data.size < 7) {
            return Inspection(0L, 0L, 0L, 0, 0L, hadTrailingPartialFrame = data.isNotEmpty())
        }
        val firstOffset = findFirstSyncOffset(data, data.size) ?: return Inspection(
            0L, 0L, 0L, 0, 0L, hadTrailingPartialFrame = data.isNotEmpty()
        )
        var offset = firstOffset
        var frameCount = 0L
        var sampleRate = 0
        var partial = false

        while (offset + 7 <= data.size) {
            val header = parseHeader(data, offset)
            if (header == null) {
                partial = offset < data.size
                break
            }
            if (offset + header.frameLength > data.size) {
                partial = true
                break
            }
            if (sampleRate == 0) sampleRate = header.sampleRate
            offset += header.frameLength
            frameCount++
        }
        if (offset < data.size) partial = true

        val durationMs = if (sampleRate > 0 && frameCount > 0) {
            (frameCount * SAMPLES_PER_AAC_FRAME * 1000L) / sampleRate
        } else {
            0L
        }
        return Inspection(
            validStartOffset = firstOffset.toLong(),
            validEndOffset = offset.toLong(),
            validFrameCount = frameCount,
            sampleRate = sampleRate,
            durationMs = durationMs,
            hadTrailingPartialFrame = partial
        )
    }

    /** Inspects an ADTS file on disk using streaming reads without loading the whole file into RAM. */
    fun inspectFile(file: File): Inspection {
        if (!file.exists() || !file.isFile) {
            return Inspection(0L, 0L, 0L, 0, 0L, hadTrailingPartialFrame = false)
        }
        val fileLen = file.length()
        if (fileLen < 7L) {
            return Inspection(0L, 0L, 0L, 0, 0L, hadTrailingPartialFrame = fileLen > 0L)
        }
        val headerBuf = ByteArray(7)
        val probeSize = minOf(fileLen, 16_384L).toInt()
        val probe = ByteArray(probeSize)

        RandomAccessFile(file, "r").use { raf ->
            raf.readFully(probe)
            val firstOffset = findFirstSyncOffset(probe, probeSize) ?: return Inspection(
                0L, 0L, 0L, 0, 0L, hadTrailingPartialFrame = true
            )
            var offset = firstOffset.toLong()
            var frameCount = 0L
            var sampleRate = 0
            var partial = false

            while (offset + 7L <= fileLen) {
                raf.seek(offset)
                raf.readFully(headerBuf)
                val header = parseHeader(headerBuf, 0)
                if (header == null) {
                    partial = true
                    break
                }
                if (offset + header.frameLength > fileLen) {
                    partial = true
                    break
                }
                if (sampleRate == 0) sampleRate = header.sampleRate
                offset += header.frameLength
                frameCount++
            }
            if (offset < fileLen) partial = true

            val durationMs = if (sampleRate > 0 && frameCount > 0) {
                (frameCount * SAMPLES_PER_AAC_FRAME * 1000L) / sampleRate
            } else {
                0L
            }
            return Inspection(
                validStartOffset = firstOffset.toLong(),
                validEndOffset = offset,
                validFrameCount = frameCount,
                sampleRate = sampleRate,
                durationMs = durationMs,
                hadTrailingPartialFrame = partial
            )
        }
    }

    /**
     * Trims any leading garbage or incomplete trailing ADTS frame in place so downstream extractors
     * and players see a clean, frame-aligned ADTS stream.
     */
    fun trimToValidFrames(file: File): Inspection {
        val inspection = inspectFile(file)
        if (!inspection.isPlayable) return inspection
        val fileLen = file.length()
        if (inspection.validStartOffset == 0L && inspection.validEndOffset == fileLen) {
            return inspection
        }
        if (inspection.validStartOffset == 0L && inspection.validEndOffset < fileLen) {
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(inspection.validEndOffset)
                raf.fd.sync()
            }
            return inspectFile(file)
        }
        val tmp = File(file.parentFile, "${file.name}.trimmed.tmp")
        RandomAccessFile(file, "r").use { src ->
            src.seek(inspection.validStartOffset)
            var remaining = inspection.validByteCount
            val buf = ByteArray(16_384)
            tmp.outputStream().use { out ->
                while (remaining > 0L) {
                    val toRead = minOf(buf.size.toLong(), remaining).toInt()
                    val n = src.read(buf, 0, toRead)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    remaining -= n
                }
                out.fd.sync()
            }
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        return inspectFile(file)
    }

    private data class AdtsHeader(val sampleRate: Int, val frameLength: Int)

    private fun parseHeader(data: ByteArray, offset: Int): AdtsHeader? {
        if (offset + 7 > data.size) return null
        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        val b2 = data[offset + 2].toInt() and 0xFF
        val b3 = data[offset + 3].toInt() and 0xFF
        val b4 = data[offset + 4].toInt() and 0xFF
        val b5 = data[offset + 5].toInt() and 0xFF

        if (b0 != 0xFF || (b1 and 0xF6) != 0xF0) return null
        val protectionAbsent = b1 and 0x01
        val minHeaderBytes = if (protectionAbsent == 1) 7 else 9
        val sampleRateIndex = (b2 ushr 2) and 0x0F
        if (sampleRateIndex !in SAMPLE_RATES.indices) return null

        val frameLength = ((b3 and 0x03) shl 11) or (b4 shl 3) or ((b5 ushr 5) and 0x07)
        if (frameLength < minHeaderBytes || frameLength > MAX_ADTS_FRAME_BYTES) return null

        return AdtsHeader(sampleRate = SAMPLE_RATES[sampleRateIndex], frameLength = frameLength)
    }

    private fun findFirstSyncOffset(data: ByteArray, limit: Int): Int? {
        val searchLimit = limit - 7
        for (i in 0..searchLimit) {
            val header = parseHeader(data, i) ?: continue
            val next = i + header.frameLength
            if (next == limit) return i
            if (next + 7 <= limit && parseHeader(data, next) != null) return i
        }
        return null
    }
}
