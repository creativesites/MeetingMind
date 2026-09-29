package com.craftflowtechnologies.meetingmind.ai.transcription

import com.craftflowtechnologies.meetingmind.ai.transcript.CanonicalWord
import com.craftflowtechnologies.meetingmind.ai.transcript.TranscriptSource
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

/** Which engine transcribed a stretch of a recording. */
enum class TranscriptionRoute(val label: String) { GEMINI("Gemini"), LOCAL("this phone") }

/**
 * A stretch of the recording that is finished, whichever engine did it. Regions are what make
 * switching engines possible: the engine that takes over asks "what is not done yet?" and
 * transcribes only that. Words carry the engine's own speaker ids and absolute times.
 */
data class DoneRegion(val startMs: Long, val endMs: Long, val route: TranscriptionRoute, val words: List<CanonicalWord>)

/**
 * What is finished, and what isn't. Regions may overlap (Gemini parts deliberately re-read the
 * end of the one before); a stretch counts as done when any region covers it.
 */
object Coverage {
    /** Gaps shorter than this aren't worth an engine run (and can't hold speech worth keeping). */
    const val MIN_GAP_MS = 2_000L

    fun gaps(totalMs: Long, done: List<DoneRegion>, minGapMs: Long = MIN_GAP_MS): List<LongRange> {
        if (totalMs <= 0) return emptyList()
        val merged = mutableListOf<LongRange>()
        for (r in done.sortedBy { it.startMs }) {
            val last = merged.lastOrNull()
            if (last != null && r.startMs <= last.last) { if (r.endMs > last.last) merged[merged.lastIndex] = last.first..r.endMs }
            else merged += r.startMs..r.endMs
        }
        val out = mutableListOf<LongRange>()
        var cursor = 0L
        for (m in merged) {
            if (m.first - cursor >= minGapMs) out += cursor until m.first
            cursor = maxOf(cursor, m.last)
        }
        if (totalMs - cursor >= minGapMs) out += cursor until totalMs
        return out
    }

    /** The parts of [speech] that fall inside [gaps]. */
    fun restrict(speech: List<com.craftflowtechnologies.meetingmind.ai.transcript.SpeechRegion>, gaps: List<LongRange>): List<com.craftflowtechnologies.meetingmind.ai.transcript.SpeechRegion> {
        // No voice detector result at all: the gaps themselves are decoded, end to end.
        if (speech.isEmpty()) return gaps.map { com.craftflowtechnologies.meetingmind.ai.transcript.SpeechRegion(it.first, it.last + 1) }
        return speech.flatMap { r ->
            gaps.mapNotNull { g ->
                val s = maxOf(r.startMs, g.first); val e = minOf(r.endMs, g.last + 1)
                if (e > s) com.craftflowtechnologies.meetingmind.ai.transcript.SpeechRegion(s, e, r.confidence) else null
            }
        }
    }

    /** Milliseconds of the recording that are done, and the share of the total. */
    fun doneMs(totalMs: Long, done: List<DoneRegion>): Long = totalMs - gaps(totalMs, done, minGapMs = 1).sumOf { it.last - it.first + 1 }
}

/**
 * One small append-only file per recording, holding the regions finished so far. The first line
 * fingerprints the audio (its size and length); if the file changes, the old regions no longer
 * describe it and are ignored. A line cut short by the process dying fails to parse and that
 * region is simply done again.
 */
class TranscriptionCheckpoints(private val directory: File) {
    private fun fileFor(meetingId: String) = File(directory, "${meetingId.replace(Regex("[^A-Za-z0-9_-]"), "_")}.regions")

    fun fingerprint(audio: File, totalMs: Long) = "v1|${audio.length()}|$totalMs"

    fun load(meetingId: String, fingerprint: String): List<DoneRegion> {
        val lines = runCatching { fileFor(meetingId).takeIf { it.isFile }?.readLines() }.getOrNull() ?: return emptyList()
        if (lines.firstOrNull() != fingerprint) return emptyList()
        return lines.drop(1).mapNotNull { decode(it) }
    }

    fun append(meetingId: String, fingerprint: String, region: DoneRegion) {
        runCatching {
            directory.mkdirs()
            val file = fileFor(meetingId)
            if (!file.isFile || file.bufferedReader().use { it.readLine() } != fingerprint) file.writeText(fingerprint + "\n")
            file.appendText(encode(region) + "\n")
        }
    }

    fun clear(meetingId: String) { fileFor(meetingId).delete() }

    internal fun encode(r: DoneRegion): String =
        "${r.route.name}\t${r.startMs}\t${r.endMs}\t" + r.words.joinToString(" ") { w ->
            "${w.startMs},${w.endMs},${w.speakerId?.let { URLEncoder.encode(it, "UTF-8") } ?: ""},${URLEncoder.encode(w.text, "UTF-8")}"
        } + "\t$END"

    internal fun decode(line: String): DoneRegion? = runCatching {
        val p = line.split('\t')
        if (p.size != 5 || p[4] != END) return null
        val route = TranscriptionRoute.valueOf(p[0])
        val words = if (p[3].isEmpty()) emptyList() else p[3].split(' ').mapIndexed { i, token ->
            val (s, e, spk, text) = token.split(',', limit = 4)
            CanonicalWord(
                id = "ckpt_${p[1]}_$i", text = URLDecoder.decode(text, "UTF-8"), startMs = s.toLong(), endMs = e.toLong(),
                speakerId = spk.takeIf { it.isNotEmpty() }?.let { URLDecoder.decode(it, "UTF-8") },
                source = if (route == TranscriptionRoute.GEMINI) TranscriptSource.GEMINI_VERBATIM else TranscriptSource.LOCAL_ASR
            )
        }
        DoneRegion(p[1].toLong(), p[2].toLong(), route, words)
    }.getOrNull()

    private companion object { const val END = "ok" }
}
