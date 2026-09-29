package com.example.core.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** What the room sounds like, second by second — before anyone knows what is being said. */
enum class AcousticActivity { SPEECH, MUSIC, SILENCE, CROWD, MIXED }

data class AcousticSegment(val startMs: Long, val endMs: Long, val activity: AcousticActivity)

/** Per-second measurements, kept so the classifier can be tuned against the corpus. */
data class WindowFeatures(
    val rmsDb: Double,
    /** Share of 32 ms frames well below the window's level: pauses between words. */
    val pauseRatio: Double,
    /** How much frame energy moves within the second (dB standard deviation): syllables. */
    val modulation: Double,
    /** 0 = pure tone, 1 = white noise, over 100 Hz–4 kHz. */
    val flatness: Double,
    /** Strength of a steady pitch (normalised autocorrelation peak, 80–500 Hz). */
    val harmonicity: Double,
    val zcr: Double
)

/**
 * A cheap, local detector for speech, music, silence and crowd noise — the candidate boundaries
 * that decide which stretches are worth asking an AI about. It streams the decoder, so an hour of
 * audio never sits in memory. Heuristic by design; the thresholds are tuned against
 * testdata/sermons and reported by its scorecard.
 */
object AcousticAnalyzer {
    const val WINDOW_MS = 1000L
    private const val FRAME = 256

    /** Streams [file]'s audio as mono float blocks at its own sample rate. */
    fun stream(file: File, onBlock: (FloatArray, Int) -> Unit) {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.absolutePath)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("No audio track")
            val format = ex.getTrackFormat(track)
            ex.selectTrack(track)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inEos = false
                var outEos = false
                while (!outEos) {
                    if (!inEos) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                            if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inEos = true }
                            else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    } else if (o >= 0) {
                        if (info.size > 0) {
                            val buf = codec.getOutputBuffer(o)!!
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            val shorts = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                            val frames = shorts.remaining() / channels
                            val mono = FloatArray(frames)
                            for (f in 0 until frames) {
                                var sum = 0f
                                for (c in 0 until channels) sum += shorts.get(f * channels + c)
                                mono[f] = sum / channels / 32768f
                            }
                            onBlock(mono, rate)
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outEos = true
                    }
                }
            } finally { runCatching { codec.stop() }; codec.release() }
        } finally { ex.release() }
    }

    /** Segments for a whole file, or null when it can't be decoded. */
    fun analyze(file: File): List<AcousticSegment>? = runCatching {
        val windows = mutableListOf<WindowFeatures>()
        var buffer = FloatArray(0)
        stream(file) { block, rate ->
            buffer += block
            val size = (rate * WINDOW_MS / 1000).toInt()
            var start = 0
            while (buffer.size - start >= size) {
                windows += features(buffer.copyOfRange(start, start + size), rate)
                start += size
            }
            if (start > 0) buffer = buffer.copyOfRange(start, buffer.size)
        }
        segments(windows.map { classify(it) })
    }.getOrNull()

    /** The measurements of one second of audio. */
    fun features(input: FloatArray, inputRate: Int): WindowFeatures {
        // About 8 kHz is plenty for these measures and keeps an hour of audio to seconds of work.
        val d = max(1, inputRate / 8000)
        val samples = if (d == 1) input else FloatArray(input.size / d) { i -> var s = 0f; for (k in 0 until d) s += input[i * d + k]; s / d }
        val rate = inputRate / d
        var frameIndex = 0
        val frameDb = mutableListOf<Double>()
        var flatSum = 0.0; var flatN = 0
        var harmSum = 0.0; var harmN = 0
        var crossings = 0
        for (i in 1 until samples.size) if ((samples[i - 1] >= 0) != (samples[i] >= 0)) crossings++
        var total = 0.0
        for (s in samples) total += s * s
        val rms = sqrt(total / max(1, samples.size))
        var off = 0
        while (off + FRAME <= samples.size) {
            val frame = samples.copyOfRange(off, off + FRAME)
            var e = 0.0
            for (s in frame) e += s * s
            val db = 10 * log10(e / FRAME + 1e-12)
            frameDb += db
            if (db > -55 && frameIndex++ % 3 == 0) {
                flatSum += flatness(frame, rate); flatN++
                harmSum += harmonicity(frame, rate); harmN++
            }
            off += FRAME
        }
        val mean = frameDb.average().takeIf { !it.isNaN() } ?: -120.0
        val loudest = frameDb.maxOrNull() ?: -120.0
        val sd = sqrt(frameDb.map { (it - mean) * (it - mean) }.average().takeIf { !it.isNaN() } ?: 0.0)
        return WindowFeatures(
            rmsDb = 20 * log10(rms + 1e-9),
            pauseRatio = frameDb.count { it < loudest - 18 }.toDouble() / max(1, frameDb.size),
            modulation = sd,
            flatness = if (flatN > 0) flatSum / flatN else 1.0,
            harmonicity = if (harmN > 0) harmSum / harmN else 0.0,
            zcr = crossings.toDouble() / max(1, samples.size)
        )
    }

    fun classify(f: WindowFeatures): AcousticActivity = when {
        f.rmsDb < -48 -> AcousticActivity.SILENCE
        // Singing and instruments: a steady pitch, sustained, few pauses.
        f.harmonicity > 0.55 && f.pauseRatio < 0.12 && f.modulation < 7.5 -> AcousticActivity.MUSIC
        // Applause, cheering, murmur: broadband noise, no pitch.
        f.flatness > 0.35 && f.harmonicity < 0.3 && f.pauseRatio < 0.15 -> AcousticActivity.CROWD
        // Speech: pitched in bursts, with gaps between words and phrases.
        f.pauseRatio >= 0.08 && f.modulation >= 5.0 && f.harmonicity > 0.2 -> AcousticActivity.SPEECH
        f.harmonicity > 0.45 -> AcousticActivity.MUSIC
        else -> AcousticActivity.MIXED
    }

    /**
     * Seconds into segments: a 5-second majority smooths flicker, and anything shorter than
     * [minMs] joins its neighbour, so a breath inside a song doesn't split it.
     */
    fun segments(perSecond: List<AcousticActivity>, minMs: Long = 8_000): List<AcousticSegment> {
        if (perSecond.isEmpty()) return emptyList()
        val smooth = perSecond.indices.map { i ->
            perSecond.subList(max(0, i - 2), min(perSecond.size, i + 3)).groupingBy { it }.eachCount().maxBy { it.value }.key
        }
        val runs = mutableListOf<AcousticSegment>()
        var start = 0
        for (i in 1..smooth.size) {
            if (i == smooth.size || smooth[i] != smooth[start]) {
                runs += AcousticSegment(start * WINDOW_MS, i * WINDOW_MS, smooth[start]); start = i
            }
        }
        // Absorb short runs into the longer neighbour, repeatedly.
        var list = runs.toMutableList()
        var changed = true
        while (changed && list.size > 1) {
            changed = false
            val i = list.indices.filter { list[it].endMs - list[it].startMs < minMs }.minByOrNull { list[it].endMs - list[it].startMs } ?: break
            val left = list.getOrNull(i - 1); val right = list.getOrNull(i + 1)
            val into = when {
                left == null -> i + 1
                right == null -> i - 1
                (left.endMs - left.startMs) >= (right.endMs - right.startMs) -> i - 1
                else -> i + 1
            }
            val a = list[min(i, into)]; val b = list[max(i, into)]
            val keep = if (into < i) list[into].activity else list[into].activity
            list[min(i, into)] = AcousticSegment(a.startMs, b.endMs, keep)
            list.removeAt(max(i, into))
            changed = true
        }
        // Neighbours that ended up the same merge.
        val merged = mutableListOf<AcousticSegment>()
        for (s in list) {
            val last = merged.lastOrNull()
            if (last != null && last.activity == s.activity) merged[merged.lastIndex] = last.copy(endMs = s.endMs) else merged += s
        }
        return merged
    }

    // ---------------------------------------------------------------- measurements

    private fun flatness(frame: FloatArray, rate: Int): Double {
        val re = DoubleArray(FRAME) { frame[it] * hann(it) }
        val im = DoubleArray(FRAME)
        fft(re, im)
        val lo = (100.0 * FRAME / rate).toInt().coerceAtLeast(1)
        val hi = (4000.0 * FRAME / rate).toInt().coerceAtMost(FRAME / 2 - 1)
        var logSum = 0.0; var sum = 0.0; var n = 0
        for (k in lo..hi) {
            val p = re[k] * re[k] + im[k] * im[k] + 1e-12
            logSum += ln(p); sum += p; n++
        }
        if (n == 0) return 1.0
        return exp(logSum / n) / (sum / n)
    }

    private fun harmonicity(frame: FloatArray, rate: Int): Double {
        var energy = 0.0
        for (s in frame) energy += s * s
        if (energy < 1e-8) return 0.0
        val minLag = rate / 500; val maxLag = min(FRAME - 1, rate / 80)
        var best = 0.0
        for (lag in minLag..maxLag) {
            var c = 0.0; var e1 = 0.0; var e2 = 0.0
            for (i in 0 until FRAME - lag) { c += frame[i] * frame[i + lag]; e1 += frame[i] * frame[i]; e2 += frame[i + lag] * frame[i + lag] }
            val r = c / (sqrt(e1 * e2) + 1e-12)
            if (r > best) best = r
        }
        return best
    }

    private fun hann(i: Int) = 0.5 - 0.5 * cos(2 * Math.PI * i / (FRAME - 1))

    /** In-place radix-2 FFT. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { re[i] = re[j].also { re[j] = re[i] }; im[i] = im[j].also { im[j] = im[i] } }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * Math.PI / len
            for (i in 0 until n step len) {
                for (k in 0 until len / 2) {
                    val wr = cos(ang * k); val wi = sin(ang * k)
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * wr - im[i + k + len / 2] * wi
                    val vi = re[i + k + len / 2] * wi + im[i + k + len / 2] * wr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                }
            }
            len = len shl 1
        }
    }

    @Suppress("unused") private fun absMax(a: FloatArray) = a.maxOfOrNull { abs(it) } ?: 0f
}
