package com.craftflowtechnologies.meetingmind.ai.voice.read

import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiError
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiResult
import com.craftflowtechnologies.meetingmind.ai.cloud.GeminiSpeechClient
import com.craftflowtechnologies.meetingmind.ai.voice.Pcm
import com.craftflowtechnologies.meetingmind.ai.voice.SpeechChunk
import com.craftflowtechnologies.meetingmind.ai.voice.Wav
import java.io.File
import java.security.MessageDigest

data class GeminiSectionRequest(
    val sectionId: String,
    /** [ReadFingerprint] of the section: a piece made for different content is never reused. */
    val fingerprint: String,
    val chunks: List<SpeechChunk>,
    val style: String,
    val voice: String,
    /** The model this reading is already using, so every section keeps one voice character. */
    val pinnedModel: String? = null,
    val maxChars: Int = 2_400
)

sealed interface GeminiSectionResult {
    data class Done(val audio: Pcm, val model: String?, val reusedPieces: Int) : GeminiSectionResult

    /** The reading stops here. [piecesDone] are saved on disk, so trying again resumes after them. */
    data class Failed(val error: GeminiError, val pieceIndex: Int, val piecesDone: Int) : GeminiSectionResult
}

/**
 * One section of a reading with the Gemini voice. Every finished piece is saved to disk as soon as it
 * arrives, so a failure at piece 4 of 7 costs one piece on retry, not the whole section. It uses the
 * Gemini client and nothing else: on failure it reports, it never reaches for the phone voice.
 */
class GeminiReadingEngine(private val client: GeminiSpeechClient, private val workDir: File) {

    private class Piece(val inputIndex: Int, val text: String, val pauseAfterMs: Long)

    suspend fun synthesize(request: GeminiSectionRequest, onProgress: (Float) -> Unit = {}): GeminiSectionResult {
        val pieces = request.chunks.withIndex().flatMap { (i, chunk) ->
            val parts = TextChunker.split(chunk.text, request.maxChars)
            parts.mapIndexed { p, text -> Piece(i, text, if (p == parts.lastIndex) chunk.pauseAfterMs else GAP_MS) }
        }
        if (pieces.isEmpty()) return GeminiSectionResult.Failed(GeminiError(com.craftflowtechnologies.meetingmind.ai.cloud.GeminiErrorKind.BAD_REQUEST, null, "Nothing to read."), 0, 0)
        val dir = File(workDir, "${request.sectionId}-${request.fingerprint.take(12)}").apply { mkdirs() }
        val clips = mutableListOf<Pcm>()
        var model = request.pinnedModel
        var reused = 0
        for ((j, piece) in pieces.withIndex()) {
            val file = File(dir, "piece_${j}_${hash(piece.text, request.style, request.voice)}.wav")
            val saved = if (file.exists()) Wav.read(file) else null
            val audio = if (saved != null) {
                reused++
                saved
            } else {
                when (val r = client.speak(piece.text, request.style, request.voice, onlyModel = model)) {
                    is GeminiResult.Success -> {
                        model = r.value.model
                        Wav.write(r.value.audio, file)
                        r.value.audio
                    }
                    is GeminiResult.Failure -> return GeminiSectionResult.Failed(r.error, piece.inputIndex, j)
                }
            }
            clips += audio
            if (piece.pauseAfterMs > 0) clips += Wav.silence(piece.pauseAfterMs, audio.sampleRate)
            onProgress((j + 1f) / pieces.size)
        }
        val joined = Wav.concat(clips)
            ?: return GeminiSectionResult.Failed(GeminiError(com.craftflowtechnologies.meetingmind.ai.cloud.GeminiErrorKind.EMPTY_AUDIO, null, "No audio."), pieces.lastIndex, pieces.size)
        return GeminiSectionResult.Done(joined, model, reused)
    }

    /** Forget a section's saved pieces once its audio has been committed, or when it is cancelled. */
    fun discard(sectionId: String) {
        workDir.listFiles { f -> f.isDirectory && f.name.startsWith("$sectionId-") }?.forEach { it.deleteRecursively() }
    }

    private fun hash(vararg parts: String): String =
        MessageDigest.getInstance("SHA-256").digest(parts.joinToString("\u0000").toByteArray()).joinToString("") { "%02x".format(it) }.take(10)

    private companion object {
        const val GAP_MS = 150L
    }
}
