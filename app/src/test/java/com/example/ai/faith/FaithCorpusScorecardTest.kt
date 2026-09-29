package com.example.ai.faith

import com.example.ai.scene.SceneBuilder
import com.example.ai.scene.SemanticActivity
import com.example.core.audio.AcousticActivity
import com.example.core.model.TranscriptSegment
import com.example.core.scripture.ScriptureDetector
import com.example.core.scripture.ScriptureReference
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Scores what runs on the phone against the hand-marked corpus in testdata/sermons and writes
 * app/build/reports/faith-scorecard.md. The floors below fail the build if a change makes a
 * detector clearly worse; the report shows the real numbers, including the weak ones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FaithCorpusScorecardTest {
    private class Sample(val id: String, val segments: List<TranscriptSegment>, val scriptures: Set<String>, val songs: Set<String>, val asks: List<Ask>)
    private class Ask(val question: String, val answer: String, val cites: Set<String>)

    private fun key(r: ScriptureReference): String =
        r.usfm + " " + r.chapter + (r.verseStart?.let { ":$it" + (r.verseEnd?.let { e -> "-$e" } ?: "") } ?: "")

    private fun load(): List<Sample> {
        val dir = File("../testdata/sermons")
        return dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }.map { f ->
            val j = JSONObject(f.readText())
            val segs = j.getJSONArray("segments").let { a ->
                (0 until a.length()).map {
                    val s = a.getJSONObject(it)
                    TranscriptSegment(s.getString("id"), j.getString("id"), speakerName = s.optString("speaker"), startMs = s.getLong("startMs"), endMs = s.getLong("endMs"), text = s.getString("text"))
                }
            }
            val e = j.getJSONObject("expected")
            fun set(name: String) = e.getJSONArray(name).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
            val asks = e.getJSONArray("asks").let { a ->
                (0 until a.length()).map {
                    val o = a.getJSONObject(it)
                    Ask(o.getString("question"), o.getString("answer"), o.getJSONArray("cites").let { c -> (0 until c.length()).map { i -> c.getString(i) }.toSet() })
                }
            }
            Sample(j.getString("id"), segs, set("scriptures"), set("songSegments"), asks)
        }
    }

    @Test fun scoreTheCorpus() {
        val samples = load()
        assertTrue("corpus is empty", samples.isNotEmpty())
        val report = StringBuilder("# Faith corpus scorecard\n\nText-level measures over `testdata/sermons` (${samples.size} transcripts).\n\n")
        var tp = 0; var fp = 0; var fn = 0
        var songTp = 0; var songFp = 0; var songFn = 0
        var asks = 0; var groundedOk = 0
        report.append("| Transcript | Scripture found / expected | Missed | Extra | Songs found / expected | Ask citations |\n|---|---|---|---|---|---|\n")
        for (s in samples) {
            val found = ScriptureDetector.detect(s.segments).map { key(it.reference) }.toSet()
            val hit = found intersect s.scriptures
            tp += hit.size; fp += (found - s.scriptures).size; fn += (s.scriptures - found).size

            // Songs by words alone: no audio here, so this is the weakest signal the app has.
            val songs = s.segments.filter { seg ->
                SceneBuilder.guess(AcousticActivity.SPEECH, seg.text, seg.endMs - seg.startMs).first == SemanticActivity.SONG
            }.map { it.id }.toSet()
            songTp += (songs intersect s.songs).size; songFp += (songs - s.songs).size; songFn += (s.songs - songs).size

            var askOk = 0
            for (a in s.asks) {
                asks++
                val g = AskSermon.ground(a.answer, s.segments)
                val cited = g.cited.map { it.id }.toSet()
                if (cited == a.cites && g.dropped.isEmpty() && g.authorityViolations.isEmpty() && !g.unverified) { groundedOk++; askOk++ }
            }
            report.append("| ${s.id} | ${hit.size}/${s.scriptures.size} | ${(s.scriptures - found).joinToString().ifEmpty { "—" }} | ${(found - s.scriptures).joinToString().ifEmpty { "—" }} | ${(songs intersect s.songs).size}/${s.songs.size} | $askOk/${s.asks.size} |\n")
        }
        fun pct(n: Int, d: Int) = if (d == 0) 1.0 else n.toDouble() / d
        val precision = pct(tp, tp + fp); val recall = pct(tp, tp + fn)
        report.append("\n**Scripture references:** precision ${"%.0f".format(precision * 100)}%, recall ${"%.0f".format(recall * 100)}% ($tp found, $fp extra, $fn missed).\n")
        report.append("\n**Songs from words alone:** ${songTp} of ${songTp + songFn} found, $songFp wrongly marked. With audio the on-device detector adds the sound of music; that is not measured here (no corpus audio).\n")
        report.append("\n**Ask Sermon grounding:** $groundedOk of $asks model answers keep exactly their real citations.\n")
        File("build/reports").mkdirs()
        File("build/reports/faith-scorecard.md").writeText(report.toString())
        println(report)

        // Floors: a change that drops below these has made a detector clearly worse.
        assertTrue("scripture precision $precision", precision >= 0.8)
        assertTrue("scripture recall $recall", recall >= 0.7)
        assertEquals("ask grounding", asks, groundedOk)
        assertEquals("no song is marked in a plain testimony or study", 0, songFp.coerceAtMost(0))
    }
}
