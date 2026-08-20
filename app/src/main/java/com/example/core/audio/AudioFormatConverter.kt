package com.example.core.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder

/** Decoded audio, always normalized to [AudioFormatConverter.TARGET_SAMPLE_RATE] Hz, mono, in [-1, 1]. */
data class DecodedAudio(val samples: FloatArray, val sampleRate: Int)

/**
 * Real audio preparation layer:
 *
 * ```
 * Recorded/imported file -> MediaExtractor+MediaCodec decode -> mono downmix
 *   -> linear resample to 16 kHz -> normalized Float32 PCM
 * ```
 *
 * Every local AI model in this app (VAD, ASR) expects 16 kHz mono PCM. Rather than assume
 * recordings/imports are already in that format, this class explicitly decodes and converts
 * whatever container/codec/channel-layout/sample-rate the source file actually has. The
 * original recording file is never modified — this only ever reads it and produces new,
 * separate in-memory sample data.
 */
object AudioFormatConverter {
    const val TARGET_SAMPLE_RATE = 16000
    private const val DEQUEUE_TIMEOUT_US = 10_000L

    fun decodeToMono16k(audioFile: File): DecodedAudio {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(audioFile.absolutePath)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            val trackFormat = requireNotNull(format) { "No audio track found in ${audioFile.name}" }
            extractor.selectTrack(trackIndex)

            val mime = requireNotNull(trackFormat.getString(MediaFormat.KEY_MIME))
            val sourceSampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannelCount = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val interleaved = decodePcm(extractor, trackFormat, mime)
            val mono = downmixToMono(interleaved, sourceChannelCount)
            val resampled = if (sourceSampleRate == TARGET_SAMPLE_RATE) {
                mono
            } else {
                linearResample(mono, sourceSampleRate, TARGET_SAMPLE_RATE)
            }

            val floatSamples = FloatArray(resampled.size) { resampled[it] / 32768f }
            return DecodedAudio(floatSamples, TARGET_SAMPLE_RATE)
        } finally {
            extractor.release()
        }
    }

    private fun decodePcm(extractor: MediaExtractor, trackFormat: MediaFormat, mime: String): ShortArray {
        val codec = MediaCodec.createDecoderByType(mime)
        val chunks = mutableListOf<ShortArray>()
        try {
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = requireNotNull(codec.getInputBuffer(inputIndex))
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                if (outputIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        val outputBuffer = requireNotNull(codec.getOutputBuffer(outputIndex))
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuffer = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val chunk = ShortArray(shortBuffer.remaining())
                        shortBuffer.get(chunk)
                        chunks.add(chunk)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEos = true
                    }
                }
                // INFO_OUTPUT_FORMAT_CHANGED / INFO_TRY_AGAIN_LATER need no handling here: we
                // already read channel count/sample rate from the track's input format, which
                // does not change mid-stream for the container types this app produces/imports.
            }
        } finally {
            codec.stop()
            codec.release()
        }

        val total = chunks.sumOf { it.size }
        val interleaved = ShortArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(interleaved, offset)
            offset += chunk.size
        }
        return interleaved
    }

    internal fun downmixToMono(interleaved: ShortArray, channelCount: Int): ShortArray {
        if (channelCount <= 1) return interleaved
        val frameCount = interleaved.size / channelCount
        val mono = ShortArray(frameCount)
        for (frame in 0 until frameCount) {
            var sum = 0
            for (channel in 0 until channelCount) {
                sum += interleaved[frame * channelCount + channel]
            }
            mono[frame] = (sum / channelCount).toInt().toShort()
        }
        return mono
    }

    /** Simple, real linear-interpolation resampler — adequate for VAD/ASR feature extraction. */
    internal fun linearResample(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (input.isEmpty() || fromRate == toRate) return input
        val ratio = toRate.toDouble() / fromRate.toDouble()
        val outputLength = (input.size * ratio).toInt().coerceAtLeast(1)
        val output = ShortArray(outputLength)
        for (i in output.indices) {
            val sourcePosition = i / ratio
            val sourceIndex = sourcePosition.toInt()
            val fraction = sourcePosition - sourceIndex
            val sample0 = input[sourceIndex.coerceIn(0, input.size - 1)]
            val sample1 = input[(sourceIndex + 1).coerceIn(0, input.size - 1)]
            output[i] = (sample0 + (sample1 - sample0) * fraction).toInt().toShort()
        }
        return output
    }
}
