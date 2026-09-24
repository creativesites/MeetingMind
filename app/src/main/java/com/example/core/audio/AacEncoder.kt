package com.example.core.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.example.ai.voice.Pcm
import java.io.File
import java.nio.ByteOrder

/**
 * Encodes mono 16-bit PCM to AAC in an .m4a — a tenth of the WAV's size, and the format WhatsApp
 * and every player accept. Returns false if the phone's encoder isn't willing; the caller keeps WAV.
 */
object AacEncoder {
    fun encode(pcm: Pcm, target: File, bitRate: Int = 64_000): Boolean = runCatching {
        target.parentFile?.mkdirs()
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, pcm.sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var track = -1
            var muxing = false
            var offset = 0
            var inputDone = false
            var outputDone = false
            val samples = pcm.samples
            while (!outputDone) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                        buffer.clear()
                        val count = minOf(buffer.remaining() / 2, samples.size - offset)
                        for (k in 0 until count) buffer.putShort(samples[offset + k])
                        val timeUs = offset * 1_000_000L / pcm.sampleRate
                        offset += count
                        if (offset >= samples.size) {
                            codec.queueInputBuffer(index, 0, count * 2, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else codec.queueInputBuffer(index, 0, count * 2, timeUs, 0)
                    }
                }
                when (val out = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start(); muxing = true }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (out >= 0) {
                        val data = codec.getOutputBuffer(out)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxing) {
                            data.position(info.offset); data.limit(info.offset + info.size)
                            muxer.writeSampleData(track, data, info)
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            muxing
        } finally {
            runCatching { codec.stop() }; codec.release()
            runCatching { muxer.stop() }; runCatching { muxer.release() }
        }
    }.getOrDefault(false).also { ok -> if (!ok) target.delete() }
}
