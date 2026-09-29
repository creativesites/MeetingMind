package com.example.core.audio

import org.json.JSONObject
import java.io.File
import java.util.Locale

/** A stretch of a recording a person labelled by ear. */
data class LabelledSegment(val startMs: Long, val endMs: Long, val activity: AcousticActivity)

/** What one scored recording came to. */
data class BenchmarkResult(
    val name: String,
    val seconds: Int,
    /** Share of seconds where the detector agreed with the labels. */
    val accuracy: Double,
    /** Per activity: precision, recall, and how many labelled seconds there were. */
    val perActivity: Map<AcousticActivity, ActivityScore>,
    /** Seconds labelled X that the detector called Y — where it goes wrong. */
    val confusions: Map<Pair<AcousticActivity, AcousticActivity>, Int>
)

data class ActivityScore(val precision: Double, val recall: Double, val labelledSeconds: Int) {
    val f1 get() = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
}

/**
 * Scores the on-device acoustic detector against labels made by ear (docs: testdata/audio/README.md).
 * The comparison is per second, over the labelled part of the recording; unlabelled stretches are
 * not counted for or against. Pure, so the arithmetic is tested without audio; the audio side is
 * [AcousticAnalyzer.analyze] on a real file, run from the developer benchmark on a phone.
 */
object AudioBenchmark {

    fun parseLabels(json: String): List<LabelledSegment> {
        val arr = JSONObject(json).getJSONArray("segments")
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val activity = runCatching { AcousticActivity.valueOf(o.getString("activity").uppercase()) }.getOrNull() ?: return@mapNotNull null
            LabelledSegment(mmss(o.get("start")), mmss(o.get("end")), activity)
        }
    }

    /** 90, "90", "1:30" and "01:01:30" all mean what they say. */
    fun mmss(v: Any): Long {
        if (v is Number) return (v.toDouble() * 1000).toLong()
        val parts = v.toString().split(':').map { it.toDouble() }
        return (parts.fold(0.0) { acc, p -> acc * 60 + p } * 1000).toLong()
    }

    fun score(name: String, predicted: List<AcousticSegment>, labels: List<LabelledSegment>): BenchmarkResult {
        fun predictedAt(s: Int) = predicted.firstOrNull { s * 1000L + 500 in it.startMs until it.endMs }?.activity ?: AcousticActivity.SPEECH
        val pairs = mutableListOf<Pair<AcousticActivity, AcousticActivity>>() // (label, predicted)
        for (l in labels) for (s in (l.startMs / 1000).toInt() until (l.endMs / 1000).toInt()) pairs += l.activity to predictedAt(s)
        val correct = pairs.count { it.first == it.second }
        val per = AcousticActivity.entries.associateWith { a ->
            val tp = pairs.count { it.first == a && it.second == a }
            val predictedCount = pairs.count { it.second == a }
            val labelled = pairs.count { it.first == a }
            ActivityScore(if (predictedCount == 0) 0.0 else tp.toDouble() / predictedCount, if (labelled == 0) 0.0 else tp.toDouble() / labelled, labelled)
        }.filterValues { it.labelledSeconds > 0 || it.precision > 0 }
        val confusions = pairs.filter { it.first != it.second }.groupingBy { it }.eachCount()
        return BenchmarkResult(name, pairs.size, if (pairs.isEmpty()) 0.0 else correct.toDouble() / pairs.size, per, confusions)
    }

    fun report(results: List<BenchmarkResult>): String = buildString {
        appendLine("# Audio benchmark\n")
        if (results.isEmpty()) { appendLine("No recordings were scored."); return@buildString }
        val total = results.sumOf { it.seconds }
        val weighted = results.sumOf { it.accuracy * it.seconds } / total.coerceAtLeast(1)
        appendLine("${results.size} recording(s), $total labelled seconds. Overall agreement with the labels: ${pct(weighted)}.\n")
        appendLine("| Recording | Seconds | Agreement | " + AcousticActivity.entries.joinToString(" | ") { "${it.name.lowercase()} P/R" } + " |")
        appendLine("|---|---|---|" + AcousticActivity.entries.joinToString("|") { "---" } + "|")
        results.forEach { r ->
            appendLine("| ${r.name} | ${r.seconds} | ${pct(r.accuracy)} | " + AcousticActivity.entries.joinToString(" | ") { a -> r.perActivity[a]?.let { "${pct(it.precision)}/${pct(it.recall)}" } ?: "—" } + " |")
        }
        val all = results.flatMap { it.confusions.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }.take(6)
        if (all.isNotEmpty()) {
            appendLine("\nMost common mistakes (labelled → detected): " + all.joinToString("; ") { "${it.key.first.name.lowercase()} → ${it.key.second.name.lowercase()} (${it.value}s)" })
        }
    }

    private fun pct(d: Double) = String.format(Locale.US, "%.0f%%", d * 100)

    /** Scores every `name.<audio>` in [dir] that has a `name.labels.json` beside it. Returns the report, also written to [dir]/report.md. */
    fun runFolder(dir: File, analyze: (File) -> List<AcousticSegment>? = AcousticAnalyzer::analyze): String {
        val audio = dir.listFiles { f -> f.isFile && f.extension.lowercase() in setOf("m4a", "mp3", "wav", "ogg", "opus", "aac", "flac") }.orEmpty().sortedBy { it.name }
        val results = audio.mapNotNull { f ->
            val labels = File(dir, f.nameWithoutExtension + ".labels.json").takeIf { it.isFile } ?: return@mapNotNull null
            val predicted = analyze(f) ?: return@mapNotNull null
            score(f.name, predicted, parseLabels(labels.readText()))
        }
        return report(results).also { runCatching { File(dir, "report.md").writeText(it) } }
    }
}
