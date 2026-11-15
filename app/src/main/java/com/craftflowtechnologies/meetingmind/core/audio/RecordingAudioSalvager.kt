package com.craftflowtechnologies.meetingmind.core.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Repairs and remuxes auto-saved capture checkpoints after normal stop or unexpected power loss.
 *
 * Recordings capture into a streamed ADTS file (`capture.aac`) whose kernel buffers are synced to
 * flash every few seconds. On clean stop—and during crash/reboot recovery—this utility trims any
 * incomplete trailing frame and wraps the stream losslessly in a standard `audio.m4a` container.
 */
object RecordingAudioSalvager {

    private const val TAG = "RecordingAudioSalvager"
    private const val MIN_AUDIO_BYTES = 1024L

    data class SalvagedAudio(
        val file: File,
        val durationMs: Long,
        val repaired: Boolean
    )

    /**
     * Remuxes an ADTS `.aac` file into [targetM4a] with zero re-encoding.
     *
     * Cleans any trailing partial ADTS frame first so power-cut files don't trip `MediaExtractor`,
     * writes to a temporary `.m4a.tmp`, validates container readability, and atomically replaces
     * [targetM4a]. Leaves [sourceAac] untouched when remuxing fails so callers can keep `.aac`.
     */
    fun remuxAdtsToM4a(sourceAac: File, targetM4a: File): Boolean {
        if (!sourceAac.exists() || sourceAac.length() < 64L) return false
        val inspection = runCatching { AdtsAudioInspector.trimToValidFrames(sourceAac) }.getOrNull()
        if (inspection != null && !inspection.isPlayable) return false

        targetM4a.parentFile?.mkdirs()
        val tempOut = File(targetM4a.parentFile, "${targetM4a.name}.tmp")
        tempOut.delete()

        val ex = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            ex.setDataSource(sourceAac.absolutePath)
            val track = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return false
            val format = ex.getTrackFormat(track)
            ex.selectTrack(track)

            muxer = MediaMuxer(tempOut.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(format)
            muxer.start()
            muxerStarted = true

            val maxInput = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024)
            } else {
                256 * 1024
            }
            val buffer = ByteBuffer.allocate(maxInput)
            val info = android.media.MediaCodec.BufferInfo()
            var writtenSamples = 0
            var fallbackPtsUs = 0L
            val frameDurationUs = inspection?.sampleRate?.takeIf { it > 0 }?.let { (1024L * 1_000_000L) / it } ?: 64_000L
            var lastPtsUs = -1L

            while (true) {
                val sampleSize = ex.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val rawTimeUs = ex.sampleTime
                val ptsUs = if (rawTimeUs > lastPtsUs) {
                    rawTimeUs
                } else {
                    fallbackPtsUs
                }
                lastPtsUs = ptsUs
                fallbackPtsUs = ptsUs + frameDurationUs
                val flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                info.set(0, sampleSize, ptsUs, flags)
                muxer.writeSampleData(outTrack, buffer, info)
                writtenSamples++
                ex.advance()
            }
            muxer.stop()
            muxerStarted = false

            if (writtenSamples <= 0 || !tempOut.exists() || tempOut.length() < MIN_AUDIO_BYTES) {
                tempOut.delete()
                return false
            }
            if (!tempOut.renameTo(targetM4a)) {
                tempOut.copyTo(targetM4a, overwrite = true)
                tempOut.delete()
            }
            return targetM4a.exists() && targetM4a.length() >= MIN_AUDIO_BYTES
        } catch (e: Exception) {
            Log.w(TAG, "ADTS->M4A remux failed for ${sourceAac.name}: ${e.message}")
            tempOut.delete()
            return false
        } finally {
            if (muxerStarted) {
                runCatching { muxer?.stop() }
            }
            runCatching { muxer?.release() }
            ex.release()
        }
    }

    /**
     * Probes exact audio duration using container metadata first and ADTS frame parsing as fallback
     * so raw `.aac` checkpoints always report accurate durations.
     */
    fun probeDurationMs(file: File): Long {
        if (!file.exists() || file.length() == 0L) return 0L
        CloudAudio.durationMs(file)?.let { return it }
        if (CloudAudio.sniffMime(file) == "audio/aac" || file.extension.equals("aac", ignoreCase = true)) {
            return runCatching { AdtsAudioInspector.inspectFile(file).durationMs }.getOrDefault(0L)
        }
        return 0L
    }

    /**
     * Inspects all candidate files for [meetingId] (both [recordedPath] and auto-saved artifacts in
     * `meetings/<id>/`) and returns a verified, playable audio file—remuxing ADTS checkpoints to
     * `audio.m4a` when possible, or preserving a repaired `.aac` fallback if needed.
     */
    fun salvageForRecovery(
        context: Context,
        meetingId: String,
        recordedPath: String?
    ): SalvagedAudio? {
        val meetingDir = File(context.filesDir, "meetings/$meetingId")
        val recordedFile = recordedPath?.takeIf { it.isNotBlank() }?.let { File(it) }
        val captureAac = File(meetingDir, AudioRecorder.CHECKPOINT_AAC_NAME)
        val canonicalM4a = File(meetingDir, AudioRecorder.FINAL_M4A_NAME)

        val candidates = linkedSetOf<File>().apply {
            recordedFile?.let { add(it) }
            add(canonicalM4a)
            add(captureAac)
            meetingDir.listFiles()
                ?.filter { it.isFile && it.length() >= MIN_AUDIO_BYTES && !it.name.endsWith(".tmp") }
                ?.forEach { add(it) }
        }

        // Prefer a healthy ADTS checkpoint if the M4A target doesn't exist or lacks container metadata.
        val validM4a = candidates
            .filter { it.exists() && it.length() >= MIN_AUDIO_BYTES && CloudAudio.durationMs(it) != null }
            .maxByOrNull { CloudAudio.durationMs(it) ?: 0L }

        val validM4aDuration = validM4a?.let { CloudAudio.durationMs(it) } ?: 0L

        val adtsCandidate = candidates.firstOrNull {
            it.exists() && it.length() >= 64L &&
                (CloudAudio.sniffMime(it) == "audio/aac" || it.extension.equals("aac", ignoreCase = true))
        }
        if (adtsCandidate != null) {
            val inspected = runCatching { AdtsAudioInspector.trimToValidFrames(adtsCandidate) }.getOrNull()
            if (inspected != null && inspected.isPlayable) {
                val adtsDuration = inspected.durationMs
                // If an M4A exists and already holds >=95% of the checkpoint duration, prefer it.
                if (validM4a != null && validM4aDuration > 0L && validM4aDuration >= (adtsDuration * 95L) / 100L) {
                    return SalvagedAudio(validM4a, validM4aDuration, repaired = validM4a != recordedFile)
                }
                val remuxed = remuxAdtsToM4a(adtsCandidate, canonicalM4a)
                if (remuxed) {
                    val m4aDur = CloudAudio.durationMs(canonicalM4a)?.takeIf { it > 0L } ?: adtsDuration
                    if (adtsCandidate == captureAac) captureAac.delete()
                    return SalvagedAudio(canonicalM4a, m4aDur, repaired = true)
                }
                return SalvagedAudio(adtsCandidate, adtsDuration, repaired = inspected.hadTrailingPartialFrame || adtsCandidate != recordedFile)
            }
        }

        if (validM4a != null) {
            return SalvagedAudio(validM4a, validM4aDuration, repaired = validM4a != recordedFile)
        }

        // Also check non-M4A valid formats (WAV/OGG/MP3/etc. from imported recordings).
        val anyReadable = candidates.firstOrNull { candidate ->
            candidate.exists() && candidate.length() >= MIN_AUDIO_BYTES && CloudAudio.sniffMime(candidate) != null &&
                probeDurationMs(candidate) > 0L
        }
        return anyReadable?.let {
            SalvagedAudio(it, probeDurationMs(it), repaired = it != recordedFile)
        }
    }

    /** Deletes temporary and recovered checkpoint files for [meetingId] (used only on explicit user discard). */
    fun deleteMeetingAudioArtifacts(context: Context, meetingId: String, primaryPath: String?) {
        primaryPath?.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
        val meetingDir = File(context.filesDir, "meetings/$meetingId")
        runCatching { File(meetingDir, AudioRecorder.CHECKPOINT_AAC_NAME).delete() }
        runCatching { File(meetingDir, AudioRecorder.FINAL_M4A_NAME).delete() }
    }
}
