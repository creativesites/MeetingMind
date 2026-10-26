package com.craftflowtechnologies.meetingmind.core.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

data class ImportedMediaInfo(
    val fileName: String,
    val durationMs: Long,
    val mimeType: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val outputFile: File
)

data class MediaInspection(
    val isVideo: Boolean,
    val durationUs: Long,
    val audioTrackIndex: Int,
    val audioMime: String?
)

class NoAudioTrackException(message: String = "This video does not contain an audio track.") : Exception(message)

class MediaExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Returns true if the codec can be losslessly remuxed into an MP4/M4A container via MediaMuxer
 * on the current Android version without re-encoding.
 */
fun canDirectRemux(audioMime: String, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    val lower = audioMime.lowercase()
    if (lower == MediaFormat.MIMETYPE_AUDIO_AAC || lower == "audio/mp4a-latm" || lower == "audio/aac") {
        return true
    }
    if (sdkInt >= Build.VERSION_CODES.Q) {
        if (lower == MediaFormat.MIMETYPE_AUDIO_OPUS || lower == "audio/opus" ||
            lower == MediaFormat.MIMETYPE_AUDIO_VORBIS || lower == "audio/vorbis") {
            return true
        }
    }
    return false
}

interface MediaExtractorEngine {
    fun inspectMedia(uri: Uri): MediaInspection
    suspend fun extractAudioFromVideo(
        uri: Uri,
        audioTrackIndex: Int,
        durationUs: Long,
        targetFile: File,
        onProgress: ((Float) -> Unit)? = null
    )
}

class AndroidMediaExtractorEngine(private val context: Context) : MediaExtractorEngine {

    override fun inspectMedia(uri: Uri): MediaInspection {
        val resolver = context.contentResolver
        var isVideo = false
        var durationUs = 0L
        var audioTrackIndex = -1
        var audioMime: String? = null

        val mimeType = try { resolver.getType(uri) } catch (_: Exception) { null }
        if (mimeType?.startsWith("video/") == true) {
            isVideo = true
        }

        var afd: AssetFileDescriptor? = null
        val extractor = MediaExtractor()
        try {
            afd = try {
                resolver.openAssetFileDescriptor(uri, "r")
            } catch (e: Exception) {
                Log.w("AudioExtractor", "Could not open AssetFileDescriptor for $uri: ${e.message}")
                null
            }

            if (afd != null) {
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            } else {
                extractor.setDataSource(context, uri, null)
            }

            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (trackMime.startsWith("video/")) {
                    isVideo = true
                    if (durationUs <= 0L && format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    }
                } else if (trackMime.startsWith("audio/") && audioTrackIndex == -1) {
                    audioTrackIndex = i
                    audioMime = trackMime
                    if (durationUs <= 0L && format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AudioExtractor", "Failed to inspect media with MediaExtractor: ${e.message}")
        } finally {
            try { extractor.release() } catch (_: Exception) {}
            try { afd?.close() } catch (_: Exception) {}
        }

        if (durationUs <= 0L) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durMs = durStr?.toLongOrNull() ?: 0L
                if (durMs > 0L) {
                    durationUs = durMs * 1000L
                }
                val hasVideoStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                if (hasVideoStr == "yes" || hasVideoStr == "true") {
                    isVideo = true
                }
                retriever.release()
            } catch (e: Exception) {
                Log.w("AudioExtractor", "Failed to inspect media with MediaMetadataRetriever: ${e.message}")
            }
        }

        return MediaInspection(
            isVideo = isVideo,
            durationUs = durationUs,
            audioTrackIndex = audioTrackIndex,
            audioMime = audioMime
        )
    }

    override suspend fun extractAudioFromVideo(
        uri: Uri,
        audioTrackIndex: Int,
        durationUs: Long,
        targetFile: File,
        onProgress: ((Float) -> Unit)?
    ) {
        val inspection = inspectMedia(uri)
        val audioMime = inspection.audioMime
        val canRemux = audioMime != null && canDirectRemux(audioMime)

        if (canRemux) {
            try {
                remuxAudioTrack(uri, audioTrackIndex, durationUs, targetFile, onProgress)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("AudioExtractor", "Fast path direct remux failed, falling back to transcode: ${e.message}")
                if (targetFile.exists()) targetFile.delete()
            }
        }

        transcodeAudioTrack(uri, audioTrackIndex, durationUs, targetFile, onProgress)
    }

    private suspend fun remuxAudioTrack(
        uri: Uri,
        audioTrackIndex: Int,
        durationUs: Long,
        targetFile: File,
        onProgress: ((Float) -> Unit)?
    ) {
        val resolver = context.contentResolver
        var afd: AssetFileDescriptor? = null
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            afd = resolver.openAssetFileDescriptor(uri, "r")
                ?: throw MediaExtractionException("Cannot open descriptor for $uri")
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            extractor.selectTrack(audioTrackIndex)
            val trackFormat = extractor.getTrackFormat(audioTrackIndex)

            targetFile.parentFile?.mkdirs()
            muxer = MediaMuxer(targetFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(trackFormat)
            muxer.start()
            muxerStarted = true

            val bufferSize = 64 * 1024
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                coroutineContext.ensureActive()
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTime = extractor.sampleTime
                val flags = extractor.sampleFlags
                bufferInfo.set(0, sampleSize, sampleTime, flags)

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)

                if (durationUs > 0) {
                    val progress = (sampleTime.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
                    onProgress?.invoke(progress)
                }

                extractor.advance()
            }
            onProgress?.invoke(1f)
        } finally {
            if (muxerStarted && muxer != null) {
                try { muxer.stop() } catch (_: Exception) {}
            }
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { afd?.close() } catch (_: Exception) {}
        }
    }

    private suspend fun transcodeAudioTrack(
        uri: Uri,
        audioTrackIndex: Int,
        durationUs: Long,
        targetFile: File,
        onProgress: ((Float) -> Unit)?
    ) {
        val resolver = context.contentResolver
        var afd: AssetFileDescriptor? = null
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var muxerAudioTrack = -1

        try {
            afd = resolver.openAssetFileDescriptor(uri, "r")
                ?: throw MediaExtractionException("Cannot open descriptor for $uri")
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            extractor.selectTrack(audioTrackIndex)
            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            val sourceMime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw MediaExtractionException("Track has no MIME type")

            val sourceSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val sourceChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            val targetChannels = minOf(sourceChannels, 2)
            val targetSampleRate = sourceSampleRate
            val outputFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, targetSampleRate, targetChannels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
            }

            decoder = MediaCodec.createDecoderByType(sourceMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            targetFile.parentFile?.mkdirs()
            muxer = MediaMuxer(targetFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val timeoutUs = 10_000L
            val decoderBufferInfo = MediaCodec.BufferInfo()
            val encoderBufferInfo = MediaCodec.BufferInfo()

            var extractorDone = false
            var decoderDone = false
            var encoderDone = false

            while (!encoderDone) {
                coroutineContext.ensureActive()

                // 1. Feed input from extractor into decoder
                if (!extractorDone) {
                    val decInputIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (decInputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(decInputIndex)!!
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(decInputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            extractorDone = true
                        } else {
                            val sampleTime = extractor.sampleTime
                            decoder.queueInputBuffer(decInputIndex, 0, sampleSize, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // 2. Pull decoded PCM from decoder and feed into encoder
                if (!decoderDone) {
                    val decOutputIndex = decoder.dequeueOutputBuffer(decoderBufferInfo, timeoutUs)
                    when (decOutputIndex) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                        else -> if (decOutputIndex >= 0) {
                            val decOutputBuffer = decoder.getOutputBuffer(decOutputIndex)!!
                            val isEos = (decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                            if (decoderBufferInfo.size > 0) {
                                val encInputIndex = encoder.dequeueInputBuffer(timeoutUs)
                                if (encInputIndex >= 0) {
                                    val encInputBuffer = encoder.getInputBuffer(encInputIndex)!!
                                    encInputBuffer.clear()

                                    decOutputBuffer.position(decoderBufferInfo.offset)
                                    decOutputBuffer.limit(decoderBufferInfo.offset + decoderBufferInfo.size)

                                    if (sourceChannels > targetChannels) {
                                        downmixPcm(decOutputBuffer, encInputBuffer, sourceChannels, targetChannels)
                                    } else {
                                        encInputBuffer.put(decOutputBuffer)
                                    }

                                    val flags = if (isEos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                                    encoder.queueInputBuffer(
                                        encInputIndex,
                                        0,
                                        encInputBuffer.position(),
                                        decoderBufferInfo.presentationTimeUs,
                                        flags
                                    )
                                    if (isEos) decoderDone = true

                                    if (durationUs > 0) {
                                        val progress = (decoderBufferInfo.presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
                                        onProgress?.invoke(progress)
                                    }
                                }
                            } else if (isEos) {
                                val encInputIndex = encoder.dequeueInputBuffer(timeoutUs)
                                if (encInputIndex >= 0) {
                                    encoder.queueInputBuffer(encInputIndex, 0, 0, decoderBufferInfo.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    decoderDone = true
                                }
                            }
                            decoder.releaseOutputBuffer(decOutputIndex, false)
                        }
                    }
                }

                // 3. Pull encoded AAC from encoder and write to muxer
                val encOutputIndex = encoder.dequeueOutputBuffer(encoderBufferInfo, timeoutUs)
                when (encOutputIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            muxerAudioTrack = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                    else -> if (encOutputIndex >= 0) {
                        val encodedData = encoder.getOutputBuffer(encOutputIndex)!!
                        if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            encoderBufferInfo.size = 0
                        }

                        if (encoderBufferInfo.size > 0 && muxerStarted) {
                            encodedData.position(encoderBufferInfo.offset)
                            encodedData.limit(encoderBufferInfo.offset + encoderBufferInfo.size)
                            muxer.writeSampleData(muxerAudioTrack, encodedData, encoderBufferInfo)
                        }

                        encoder.releaseOutputBuffer(encOutputIndex, false)
                        if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                        }
                    }
                }
            }
            onProgress?.invoke(1f)
        } finally {
            if (decoder != null) {
                try { decoder.stop() } catch (_: Exception) {}
                try { decoder.release() } catch (_: Exception) {}
            }
            if (encoder != null) {
                try { encoder.stop() } catch (_: Exception) {}
                try { encoder.release() } catch (_: Exception) {}
            }
            if (muxerStarted && muxer != null) {
                try { muxer.stop() } catch (_: Exception) {}
            }
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { afd?.close() } catch (_: Exception) {}
        }
    }

    private fun downmixPcm(
        src: ByteBuffer,
        dst: ByteBuffer,
        sourceChannels: Int,
        targetChannels: Int
    ) {
        val srcShorts = src.asShortBuffer()
        val dstShorts = dst.asShortBuffer()

        val frames = srcShorts.remaining() / sourceChannels
        val maxFrames = dstShorts.remaining() / targetChannels
        val framesToProcess = minOf(frames, maxFrames)

        if (targetChannels == 2) {
            for (f in 0 until framesToProcess) {
                val base = f * sourceChannels
                val l = srcShorts.get(base)
                val r = srcShorts.get(base + 1)
                dstShorts.put(l)
                dstShorts.put(r)
            }
            dst.position(dst.position() + framesToProcess * 2 * 2)
        } else if (targetChannels == 1) {
            for (f in 0 until framesToProcess) {
                val base = f * sourceChannels
                var sum = 0
                for (c in 0 until sourceChannels) {
                    sum += srcShorts.get(base + c)
                }
                val mono = (sum / sourceChannels).coerceIn(-32768, 32767).toShort()
                dstShorts.put(mono)
            }
            dst.position(dst.position() + framesToProcess * 2)
        }
    }
}

class AudioExtractor(
    private val context: Context,
    private val engine: MediaExtractorEngine = AndroidMediaExtractorEngine(context)
) {

    suspend fun importAndExtract(
        uri: Uri,
        meetingId: String,
        onProgress: ((Float) -> Unit)? = null
    ): ImportedMediaInfo = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        var fileName = "imported_meeting_${System.currentTimeMillis()}"
        var sizeBytes = 0L

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            Log.w("AudioExtractor", "Failed to query OpenableColumns for $uri: ${e.message}")
        }

        val inspection = engine.inspectMedia(uri)
        val meetingDir = File(context.filesDir, "meetings/$meetingId").apply { mkdirs() }

        if (inspection.isVideo) {
            if (inspection.audioTrackIndex < 0 || inspection.audioMime == null) {
                throw NoAudioTrackException("This video does not contain an audio track.")
            }

            val targetAudioFile = File(meetingDir, "audio.m4a")
            if (targetAudioFile.exists()) targetAudioFile.delete()

            try {
                engine.extractAudioFromVideo(
                    uri = uri,
                    audioTrackIndex = inspection.audioTrackIndex,
                    durationUs = inspection.durationUs,
                    targetFile = targetAudioFile,
                    onProgress = onProgress
                )
            } catch (e: CancellationException) {
                if (targetAudioFile.exists()) targetAudioFile.delete()
                throw e
            } catch (e: Exception) {
                if (targetAudioFile.exists()) targetAudioFile.delete()
                throw e
            }

            val durationMs = if (inspection.durationUs > 0) {
                inspection.durationUs / 1000L
            } else {
                getDurationMs(targetAudioFile)
            }

            ImportedMediaInfo(
                fileName = fileName,
                durationMs = if (durationMs > 0) durationMs else 60000L,
                mimeType = "audio/mp4",
                isVideo = true,
                sizeBytes = targetAudioFile.length(),
                outputFile = targetAudioFile
            )
        } else {
            val incoming = File(meetingDir, "audio.import")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(incoming).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Could not open input stream from selected URI")

                val sniffed = CloudAudio.sniffMime(incoming)
                val extension = CloudAudio.extensionFor(sniffed)
                    ?: fileName.substringAfterLast('.', "").lowercase().takeIf {
                        it in setOf("wav", "mp3", "aac", "ogg", "opus", "flac", "m4a", "mp4", "webm", "amr", "3gp")
                    } ?: "m4a"

                val targetAudioFile = File(meetingDir, "audio.$extension")
                targetAudioFile.delete()
                if (!incoming.renameTo(targetAudioFile)) {
                    incoming.copyTo(targetAudioFile, overwrite = true)
                    incoming.delete()
                }

                val durationMs = if (inspection.durationUs > 0) {
                    inspection.durationUs / 1000L
                } else {
                    getDurationMs(targetAudioFile)
                }

                onProgress?.invoke(1f)

                ImportedMediaInfo(
                    fileName = fileName,
                    durationMs = if (durationMs > 0) durationMs else 60000L,
                    mimeType = sniffed ?: "audio/*",
                    isVideo = false,
                    sizeBytes = targetAudioFile.length(),
                    outputFile = targetAudioFile
                )
            } catch (e: CancellationException) {
                if (incoming.exists()) incoming.delete()
                throw e
            } catch (e: Exception) {
                if (incoming.exists()) incoming.delete()
                throw e
            }
        }
    }

    private fun getDurationMs(file: File): Long {
        var durationMs = 0L
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.fromFile(file))
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L
            retriever.release()
        } catch (e: Exception) {
            Log.w("AudioExtractor", "Failed to retrieve exact duration via MediaMetadataRetriever: ${e.message}")
        }
        if (durationMs <= 0L) {
            durationMs = CloudAudio.durationMs(file) ?: 0L
        }
        return durationMs
    }
}
