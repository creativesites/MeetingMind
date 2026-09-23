package com.example.ai.asr

import com.example.ai.transcript.AsrWindow
import com.example.ai.transcript.CanonicalWord
import com.example.ai.transcript.TranscriptSource
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Remembers which decode windows of a recording are already transcribed, so a long offline
 * transcription that Android stops half-way (low memory, the phone restarting, the battery
 * saver) picks up where it left off instead of starting again.
 *
 * One small append-only file per recording. The first line fingerprints the model and the exact
 * window layout; if either changed (a different model, a new VAD result), the old results no
 * longer line up and the file is ignored. Each later line is one finished window. A line cut off
 * by the process dying mid-write simply fails to parse and that window is decoded again.
 */
class AsrCheckpointStore(private val directory: File) {

    private fun fileFor(meetingId: String) = File(directory, "${meetingId.replace(Regex("[^A-Za-z0-9_-]"), "_")}.asr")

    /** Windows already decoded for this exact model and window layout, by window index. */
    fun load(meetingId: String, modelId: String, windows: List<AsrWindow>): Map<Int, List<CanonicalWord>> {
        val file = fileFor(meetingId)
        if (!file.isFile) return emptyMap()
        val lines = runCatching { file.readLines() }.getOrNull() ?: return emptyMap()
        if (lines.firstOrNull() != header(modelId, windows)) return emptyMap()
        val result = mutableMapOf<Int, List<CanonicalWord>>()
        for (line in lines.drop(1)) {
            val parts = line.split('\t')
            if (parts.size != 3 || parts[2] != END_MARK) continue
            val index = parts[0].toIntOrNull() ?: continue
            if (index !in windows.indices) continue
            val words = decodeWords(parts[1], index) ?: continue
            result[index] = words
        }
        return result
    }

    /** Records one finished window. Starts a fresh file when the layout or model changed. */
    fun append(meetingId: String, modelId: String, windows: List<AsrWindow>, index: Int, words: List<CanonicalWord>) {
        runCatching {
            directory.mkdirs()
            val file = fileFor(meetingId)
            val header = header(modelId, windows)
            if (!file.isFile || file.bufferedReader().use { it.readLine() } != header) file.writeText(header + "\n")
            file.appendText("$index\t${encodeWords(words)}\t$END_MARK\n")
        }
    }

    fun clear(meetingId: String) {
        fileFor(meetingId).delete()
    }

    private fun header(modelId: String, windows: List<AsrWindow>): String =
        "v1|$modelId|" + windows.joinToString(",") { "${it.startMs}-${it.endMs}" }.hashCode()

    private fun encodeWords(words: List<CanonicalWord>): String =
        words.joinToString(" ") { "${it.startMs},${it.endMs},${URLEncoder.encode(it.text, "UTF-8")}" }

    private fun decodeWords(encoded: String, windowIndex: Int): List<CanonicalWord>? = runCatching {
        if (encoded.isEmpty()) return@runCatching emptyList()
        encoded.split(' ').mapIndexed { i, token ->
            val (start, end, text) = token.split(',', limit = 3)
            CanonicalWord(
                id = "w${windowIndex}_ckpt_$i",
                text = URLDecoder.decode(text, "UTF-8"),
                startMs = start.toLong(),
                endMs = end.toLong(),
                source = TranscriptSource.LOCAL_ASR
            )
        }
    }.getOrNull()

    private companion object {
        /** Written last, so a line without it was cut off mid-write. */
        const val END_MARK = "ok"
    }
}
