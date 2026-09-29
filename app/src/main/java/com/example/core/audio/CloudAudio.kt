package com.example.core.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.File
import java.nio.ByteBuffer

/**
 * Getting a recording ready for Gemini without decoding it.
 *
 * A file's name says little about what is in it: WhatsApp voice notes are Ogg Opus, a
 * "recording.m4a" can be anything the user shared, a video is an MP4 with an audio track. So the
 * format is read from the file's first bytes, and a part of a long recording is cut by copying
 * the compressed audio as it is (MediaExtractor into MediaMuxer) — seconds of work and a few
 * megabytes, where decoding an hour to PCM would take minutes and hundreds of megabytes.
 */
object CloudAudio {

    /** The container's MIME type from its first bytes, as Gemini names them; null when unknown. */
    fun sniffMime(file: File): String? {
        val h = ByteArray(16)
        val n = runCatching { file.inputStream().use { it.read(h) } }.getOrDefault(-1)
        if (n < 12) return null
        fun at(i: Int, s: String) = s.indices.all { h[i + it] == s[it].code.toByte() }
        fun u(i: Int) = h[i].toInt() and 0xFF
        return when {
            at(0, "OggS") -> "audio/ogg"
            at(0, "RIFF") && at(8, "WAVE") -> "audio/wav"
            at(4, "ftyp") -> "audio/m4a"
            at(0, "fLaC") -> "audio/flac"
            at(0, "FORM") && at(8, "AIF") -> "audio/aiff"
            u(0) == 0x1A && u(1) == 0x45 && u(2) == 0xDF && u(3) == 0xA3 -> "audio/webm"
            at(0, "ID3") -> "audio/mp3"
            u(0) == 0xFF && (u(1) and 0xF6) == 0xF0 -> "audio/aac"       // ADTS
            u(0) == 0xFF && (u(1) and 0xE0) == 0xE0 -> "audio/mp3"       // MPEG audio frame
            else -> null
        }
    }

    /** The file extension that matches [mime], for keeping imports honest about what they are. */
    fun extensionFor(mime: String?): String? = when (mime) {
        "audio/ogg" -> "ogg"
        "audio/wav" -> "wav"
        "audio/m4a" -> "m4a"
        "audio/flac" -> "flac"
        "audio/aiff" -> "aiff"
        "audio/webm" -> "webm"
        "audio/mp3" -> "mp3"
        "audio/aac" -> "aac"
        else -> null
    }

    /** Whether the file carries a video track (an imported video), which Gemini's transcriber won't take. */
    fun hasVideo(file: File): Boolean = runCatching {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.absolutePath)
            (0 until ex.trackCount).any { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
        } finally { ex.release() }
    }.getOrDefault(false)

    /** The audio's length, read from the container; null when it can't be read. */
    fun durationMs(file: File): Long? = runCatching {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.absolutePath)
            (0 until ex.trackCount).map { ex.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }
                ?.getLong(MediaFormat.KEY_DURATION)?.div(1000)?.takeIf { it > 0 }
        } finally { ex.release() }
    }.getOrNull()

    /**
     * Copies the audio from [startMs] to [endMs] (to the end when [endMs] ≤ 0) into a new file in
     * [dir], compressed as it was. Returns the file and its MIME type, or null when the codec has
     * no container to go into on this phone (the caller then decodes).
     */
    fun cut(source: File, startMs: Long, endMs: Long, dir: File): Pair<File, String>? {
        val ex = MediaExtractor()
        var muxer: MediaMuxer? = null
        var out: File? = null
        try {
            ex.setDataSource(source.absolutePath)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: return null
            val format = ex.getTrackFormat(track)
            val (container, ext, mime) = when (format.getString(MediaFormat.KEY_MIME)) {
                MediaFormat.MIMETYPE_AUDIO_AAC -> Triple(MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, "m4a", "audio/m4a")
                MediaFormat.MIMETYPE_AUDIO_OPUS -> if (Build.VERSION.SDK_INT >= 29) Triple(MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG, "ogg", "audio/ogg") else return null
                MediaFormat.MIMETYPE_AUDIO_VORBIS -> Triple(MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM, "webm", "audio/webm")
                else -> return null
            }
            ex.selectTrack(track)
            val startUs = startMs * 1000
            val endUs = if (endMs > 0) endMs * 1000 else Long.MAX_VALUE
            if (startUs > 0) ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            dir.mkdirs()
            out = File.createTempFile("gemini_part_", ".$ext", dir)
            muxer = MediaMuxer(out.absolutePath, container)
            val outTrack = muxer.addTrack(format)
            muxer.start()
            val size = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024) else 1024 * 1024
            val buffer = ByteBuffer.allocate(size)
            val info = android.media.MediaCodec.BufferInfo()
            var first = -1L
            var written = 0
            while (true) {
                val n = ex.readSampleData(buffer, 0)
                if (n < 0) break
                val t = ex.sampleTime
                if (t >= endUs) break
                if (first < 0) first = t
                info.set(0, n, t - first, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(outTrack, buffer, info)
                written++
                ex.advance()
            }
            muxer.stop()
            return if (written > 0) out to mime else { out.delete(); null }
        } catch (e: Exception) {
            out?.delete()
            return null
        } finally {
            runCatching { muxer?.release() }
            ex.release()
        }
    }
}
