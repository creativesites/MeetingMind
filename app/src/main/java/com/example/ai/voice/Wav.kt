package com.example.ai.voice

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16-bit mono PCM and its sample rate. */
class Pcm(val samples: ShortArray, val sampleRate: Int) {
    val durationMs: Long get() = samples.size * 1000L / sampleRate
}

/** Reading, joining and writing 16-bit PCM WAV — what both speech engines produce. */
object Wav {

    /** Reads a PCM WAV (any channel count is mixed down to mono). Null when it isn't one. */
    fun read(bytes: ByteArray): Pcm? {
        if (bytes.size < 44 || String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" || String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var rate = 0
        var channels = 1
        var bits = 16
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            var size = buf.getInt(pos + 4)
            val body = pos + 8
            if (id == "fmt ") {
                channels = buf.getShort(body + 2).toInt().coerceAtLeast(1)
                rate = buf.getInt(body + 4)
                bits = buf.getShort(body + 14).toInt()
            } else if (id == "data") {
                if (bits != 16 || rate <= 0) return null
                // Streaming writers leave the size as 0 or -1; take the rest of the file.
                if (size <= 0 || body + size > bytes.size) size = bytes.size - body
                val frames = size / (2 * channels)
                val out = ShortArray(frames)
                for (f in 0 until frames) {
                    var sum = 0
                    for (c in 0 until channels) sum += buf.getShort(body + (f * channels + c) * 2)
                    out[f] = (sum / channels).toShort()
                }
                return Pcm(out, rate)
            }
            pos = body + size + (size and 1)
        }
        return null
    }

    fun read(file: File): Pcm? = runCatching { read(file.readBytes()) }.getOrNull()

    /** Headerless 16-bit little-endian PCM ("audio/l16"). */
    fun fromL16(bytes: ByteArray, sampleRate: Int): Pcm {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return Pcm(ShortArray(bytes.size / 2) { buf.getShort(it * 2) }, sampleRate)
    }

    fun silence(ms: Long, sampleRate: Int) = Pcm(ShortArray((ms * sampleRate / 1000).toInt()), sampleRate)

    /** Joins clips, resampling any that differ to the first clip's rate (linear, good enough for speech). */
    fun concat(clips: List<Pcm>): Pcm? {
        val first = clips.firstOrNull() ?: return null
        val rate = first.sampleRate
        val parts = clips.map { if (it.sampleRate == rate) it.samples else resample(it, rate) }
        val out = ShortArray(parts.sumOf { it.size })
        var at = 0
        parts.forEach { System.arraycopy(it, 0, out, at, it.size); at += it.size }
        return Pcm(out, rate)
    }

    fun resample(pcm: Pcm, rate: Int): ShortArray {
        if (pcm.sampleRate == rate || pcm.samples.isEmpty()) return pcm.samples
        val n = (pcm.samples.size.toLong() * rate / pcm.sampleRate).toInt()
        val ratio = pcm.sampleRate.toDouble() / rate
        return ShortArray(n) { i ->
            val x = i * ratio
            val a = x.toInt().coerceAtMost(pcm.samples.size - 1)
            val b = (a + 1).coerceAtMost(pcm.samples.size - 1)
            val t = x - a
            (pcm.samples[a] * (1 - t) + pcm.samples[b] * t).toInt().toShort()
        }
    }

    fun bytes(pcm: Pcm): ByteArray {
        val data = pcm.samples.size * 2
        val out = ByteArrayOutputStream(44 + data)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
            .put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(pcm.sampleRate).putInt(pcm.sampleRate * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(data)
        out.write(header.array())
        val body = ByteBuffer.allocate(data).order(ByteOrder.LITTLE_ENDIAN)
        pcm.samples.forEach { body.putShort(it) }
        out.write(body.array())
        return out.toByteArray()
    }

    fun write(pcm: Pcm, file: File) { file.parentFile?.mkdirs(); file.writeBytes(bytes(pcm)) }
}
