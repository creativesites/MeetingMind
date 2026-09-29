package com.craftflowtechnologies.meetingmind.ai.scene

import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.core.audio.AcousticActivity
import com.craftflowtechnologies.meetingmind.core.audio.AcousticSegment
import com.craftflowtechnologies.meetingmind.core.model.TranscriptSegment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What was happening, not just how it sounded. */
enum class SemanticActivity(val label: String) {
    SERMON("Sermon"), PRAYER("Prayer"), SONG("Worship"), SCRIPTURE("Scripture reading"), ANNOUNCEMENT("Announcements"),
    DISCUSSION("Discussion"), RESPONSE("Response"), QA("Q&A"), PRESENTATION("Presentation"), OTHER("Other");

    companion object {
        fun parse(s: String?): SemanticActivity? = when (s?.trim()?.lowercase()) {
            "sermon", "preaching", "teaching" -> SERMON
            "prayer" -> PRAYER
            "song", "worship", "music", "singing" -> SONG
            "scripture", "scripture reading", "reading" -> SCRIPTURE
            "announcement", "announcements" -> ANNOUNCEMENT
            "discussion" -> DISCUSSION
            "response", "responsive reading", "applause" -> RESPONSE
            "qa", "q&a", "questions" -> QA
            "presentation" -> PRESENTATION
            "other" -> OTHER
            else -> null
        }
    }
}

/**
 * One part of a recording: when, how it sounded, what it was. Derived — rebuilt from the audio
 * and transcript whenever needed — so it lives in a file beside the recording, not in tables.
 */
data class Scene(
    val startMs: Long,
    val endMs: Long,
    val acoustic: AcousticActivity,
    val activity: SemanticActivity,
    val confidence: Double,
    /** "Opening prayer", "Reading from John 15". Never lyrics. */
    val label: String? = null,
    /** Only when the title is sung or said in the transcript of this part. */
    val songTitle: String? = null,
    /** "device" when labelled by on-device rules, "ai" when an AI model confirmed it. */
    val labelledBy: String = "device"
) {
    val durationMs get() = endMs - startMs
    fun contains(ms: Long) = ms in startMs until endMs
}

data class SceneMap(val scenes: List<Scene>, val version: Int = VERSION, val createdAt: Long = System.currentTimeMillis()) {
    fun at(ms: Long) = scenes.firstOrNull { it.contains(ms) }

    fun toJson(): String = JSONObject().put("version", version).put("createdAt", createdAt).put("scenes", JSONArray(scenes.map { s ->
        JSONObject().put("start", s.startMs).put("end", s.endMs).put("acoustic", s.acoustic.name).put("activity", s.activity.name)
            .put("confidence", s.confidence).put("label", s.label ?: JSONObject.NULL).put("song", s.songTitle ?: JSONObject.NULL).put("by", s.labelledBy)
    })).toString()

    companion object {
        const val VERSION = 1
        fun fromJson(raw: String): SceneMap? = runCatching {
            val o = JSONObject(raw)
            val a = o.getJSONArray("scenes")
            SceneMap((0 until a.length()).map { i ->
                val s = a.getJSONObject(i)
                Scene(s.getLong("start"), s.getLong("end"), AcousticActivity.valueOf(s.getString("acoustic")), SemanticActivity.valueOf(s.getString("activity")),
                    s.optDouble("confidence", 0.5), s.optString("label").takeIf { it.isNotBlank() && it != "null" },
                    s.optString("song").takeIf { it.isNotBlank() && it != "null" }, s.optString("by", "device"))
            }, o.optInt("version", VERSION), o.optLong("createdAt"))
        }.getOrNull()

        fun fileFor(audio: File) = File(audio.parentFile, "scenes.json")
        fun load(audio: File): SceneMap? = fileFor(audio).takeIf { it.exists() }?.let { fromJson(it.readText()) }
        fun save(audio: File, map: SceneMap) = runCatching { fileFor(audio).writeText(map.toJson()) }
    }
}

/** A stretch worth labelling: its sound, its words, and what the on-device rules think it is. */
data class SceneWindow(val id: Int, val startMs: Long, val endMs: Long, val acoustic: AcousticActivity, val text: String, val guess: SemanticActivity, val guessConfidence: Double)

/**
 * Builds a recording's scenes in three steps: the acoustic detector's segments, cut again where
 * the transcript turns (a prayer begins, a reading starts); on-device rules for a first label;
 * then only those candidate windows — compact excerpts, never the whole recording — go to an AI
 * to confirm. Without an AI, the on-device labels stand, marked as such.
 */
object SceneBuilder {

    private val PRAYER_START = Regex("\\b(let us pray|let's pray|let us bow|bow (our|your) heads|close (our|your) eyes|father,? we (come|thank)|dear lord|heavenly father|lord,? we (come|thank))\\b", RegexOption.IGNORE_CASE)
    private val PRAYER_END = Regex("\\b(in jesus'? name|amen)\\b", RegexOption.IGNORE_CASE)
    private val READING = Regex("\\b(turn (with me )?to|open your bibles?|the (reading|scripture) (is|comes) from|reading from|let'?s read|we read in|the word of the lord|chapter \\d+,? (verse|starting))\\b", RegexOption.IGNORE_CASE)
    private val ANNOUNCE = Regex("\\b(announcements?|next (sunday|week)|this (week|wednesday|saturday)|sign[- ]up|offering|tithes?|youth (group|meeting)|welcome to all (our )?visitors|notices)\\b", RegexOption.IGNORE_CASE)
    private val QUESTION = Regex("\\?\\s")

    /** Candidate windows: acoustic segments, split where the words mark a new part. */
    fun windows(acoustic: List<AcousticSegment>, segments: List<TranscriptSegment>, totalMs: Long): List<SceneWindow> {
        val base = acoustic.ifEmpty { listOf(AcousticSegment(0, maxOf(totalMs, segments.maxOfOrNull { it.endMs } ?: 0L), AcousticActivity.SPEECH)) }
        val cuts = sortedSetOf<Long>()
        base.forEach { cuts += it.startMs; cuts += it.endMs }
        // Within speech, a prayer or a reading starts a new part.
        segments.forEach { s -> if (PRAYER_START.containsMatchIn(s.text) || READING.containsMatchIn(s.text)) cuts += s.startMs }
        // A reading is the passage announced; what follows is the preaching on it.
        segments.forEach { s -> if (READING.containsMatchIn(s.text)) cuts += s.endMs }
        // A prayer ends at "amen" / "in Jesus' name".
        segments.forEach { s -> if (PRAYER_END.containsMatchIn(s.text)) cuts += s.endMs }
        val points = cuts.toList()
        val out = mutableListOf<SceneWindow>()
        for (i in 0 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]
            if (b - a < 4_000) continue
            val sound = base.firstOrNull { it.startMs <= a && it.endMs > a }?.activity ?: AcousticActivity.SPEECH
            val text = segments.filter { it.startMs < b && it.endMs > a }.joinToString(" ") { it.text }.trim()
            val (guess, conf) = guess(sound, text, b - a)
            out += SceneWindow(out.size, a, b, sound, text, guess, conf)
        }
        return merge(out)
    }

    /** Neighbouring windows with the same sound and guess become one. */
    private fun merge(ws: List<SceneWindow>): List<SceneWindow> {
        val out = mutableListOf<SceneWindow>()
        for (w in ws) {
            val last = out.lastOrNull()
            if (last != null && last.guess == w.guess && last.acoustic == w.acoustic && last.endMs >= w.startMs - 1000 && w.guess != SemanticActivity.PRAYER) {
                out[out.lastIndex] = last.copy(endMs = w.endMs, text = (last.text + " " + w.text).trim(), guessConfidence = maxOf(last.guessConfidence, w.guessConfidence))
            } else out += w.copy(id = out.size)
        }
        return out
    }

    /** The on-device label: sound first, then words. */
    fun guess(sound: AcousticActivity, text: String, durationMs: Long): Pair<SemanticActivity, Double> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val lines = text.split(Regex("[.!?\\n]")).map { it.trim().lowercase() }.filter { it.length > 8 }
        val repeated = if (lines.isEmpty()) 0.0 else 1.0 - lines.toSet().size.toDouble() / lines.size
        return when {
            sound == AcousticActivity.MUSIC -> SemanticActivity.SONG to (if (repeated > 0.2 || words.size < durationMs / 1000) 0.85 else 0.7)
            sound == AcousticActivity.CROWD -> SemanticActivity.RESPONSE to 0.6
            sound == AcousticActivity.SILENCE -> SemanticActivity.OTHER to 0.5
            repeated > 0.35 && lines.size >= 4 -> SemanticActivity.SONG to 0.6
            PRAYER_START.containsMatchIn(text.take(300)) -> SemanticActivity.PRAYER to 0.75
            READING.containsMatchIn(text.take(300)) -> SemanticActivity.SCRIPTURE to 0.6
            ANNOUNCE.findAll(text).count() >= 2 -> SemanticActivity.ANNOUNCEMENT to 0.55
            QUESTION.findAll(text).count() >= 4 && durationMs < 20 * 60_000 -> SemanticActivity.QA to 0.45
            else -> SemanticActivity.SERMON to 0.5
        }
    }

    fun fromWindows(ws: List<SceneWindow>): SceneMap =
        SceneMap(ws.map { Scene(it.startMs, it.endMs, it.acoustic, it.guess, it.guessConfidence) })

    // ---------------------------------------------------------------- AI confirmation

    fun prompt(ws: List<SceneWindow>, recordingType: String): String = buildString {
        appendLine("Recording type: $recordingType")
        appendLine("Windows:")
        appendLine(JSONArray(ws.map { w ->
            JSONObject().put("id", w.id).put("start", mmss(w.startMs)).put("end", mmss(w.endMs)).put("sound", w.acoustic.name.lowercase())
                .put("device_guess", w.guess.name.lowercase())
                .put("excerpt", if (w.text.length <= 700) w.text else w.text.take(450) + " … " + w.text.takeLast(250))
        }).toString())
    }

    /** Applies an AI answer to the windows; anything unreadable or ungrounded is ignored. */
    fun parse(raw: String, ws: List<SceneWindow>): SceneMap {
        val byId = ws.associateBy { it.id }
        val answers = runCatching {
            val o = com.craftflowtechnologies.meetingmind.ai.notes.NoteAiEngine.extractJsonObject(raw) ?: return@runCatching emptyMap<Int, JSONObject>()
            val a = o.optJSONArray("windows") ?: return@runCatching emptyMap()
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.associateBy { it.optInt("id", -1) }
        }.getOrDefault(emptyMap())
        val scenes = ws.map { w ->
            val a = answers[w.id]
            val activity = SemanticActivity.parse(a?.optString("activity"))
            if (a == null || activity == null) Scene(w.startMs, w.endMs, w.acoustic, w.guess, w.guessConfidence)
            else {
                // Music doesn't become a sermon on the AI's word alone, nor speech a song.
                val agreed = when {
                    w.acoustic == AcousticActivity.MUSIC && activity !in setOf(SemanticActivity.SONG, SemanticActivity.PRAYER, SemanticActivity.RESPONSE) -> w.guess
                    else -> activity
                }
                Scene(
                    w.startMs, w.endMs, w.acoustic, agreed, a.optDouble("confidence", 0.6).coerceIn(0.0, 1.0),
                    label = cleanLabel(a.optString("label"), w.text),
                    songTitle = if (agreed == SemanticActivity.SONG) groundedTitle(a.optString("song_title"), w.text) else null,
                    labelledBy = "ai"
                )
            }
        }
        return SceneMap(mergeScenes(scenes))
    }

    fun mergeScenes(scenes: List<Scene>): List<Scene> {
        val out = mutableListOf<Scene>()
        for (s in scenes) {
            val last = out.lastOrNull()
            if (last != null && last.activity == s.activity && s.activity != SemanticActivity.PRAYER && last.songTitle == s.songTitle && last.endMs >= s.startMs - 1000) {
                out[out.lastIndex] = last.copy(endMs = s.endMs, label = last.label ?: s.label, confidence = minOf(last.confidence, s.confidence))
            } else out += s
        }
        return out
    }

    /**
     * A song title survives only if its words are in the transcript of that part — sung or
     * announced. A title the model recognised from style or theme is dropped, never shown.
     */
    fun groundedTitle(title: String?, text: String): String? {
        val t = title?.trim()?.trim('"', '“', '”')?.takeIf { it.isNotBlank() && it.lowercase() != "null" && it.length <= 80 } ?: return null
        val words = t.lowercase().split(Regex("[^\\p{L}']+")).filter { it.length >= 3 }
        // One word ("Grace") is too little evidence to name a song.
        if (words.size < 2) return null
        val hay = text.lowercase()
        val phrase = t.lowercase().replace(Regex("[^\\p{L}' ]"), "").trim()
        return t.takeIf { hay.contains(phrase) || words.all { w -> Regex("\\b${Regex.escape(w)}\\b").containsMatchIn(hay) } && words.size >= 2 }
    }

    /** A label is a short name, never a line of lyrics copied from the transcript. */
    fun cleanLabel(label: String?, text: String): String? {
        val l = label?.trim()?.takeIf { it.isNotBlank() && it.lowercase() != "null" } ?: return null
        val words = l.split(Regex("\\s+"))
        if (words.size > 8) return null
        if (words.size >= 5 && text.lowercase().contains(l.lowercase())) return null
        return l
    }

    fun mmss(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }
}

/** Runs the whole thing: acoustic analysis, windows, an AI pass when one is available. */
class SceneEngine(private val transport: com.craftflowtechnologies.meetingmind.ai.cloud.GeminiTransport?) {
    suspend fun build(audio: File, segments: List<TranscriptSegment>, totalMs: Long, recordingType: String): SceneMap {
        val acoustic = com.craftflowtechnologies.meetingmind.core.audio.AcousticAnalyzer.analyze(audio).orEmpty()
        val ws = SceneBuilder.windows(acoustic, segments, totalMs)
        com.craftflowtechnologies.meetingmind.ai.cloud.GeminiLog.add("Scenes: ${acoustic.size} acoustic segments → ${ws.size} candidate windows")
        if (ws.isEmpty()) return SceneMap(emptyList())
        val t = transport
        if (t == null || !t.refreshConfigured()) return SceneBuilder.fromWindows(ws)
        val prompt = com.craftflowtechnologies.meetingmind.ai.faith.Prompts.get("scene_classifier")
        val r = t.execute(com.craftflowtechnologies.meetingmind.ai.cloud.GeminiRequest(
            modelId = com.craftflowtechnologies.meetingmind.ai.routing.DefaultAiModelRouter.GEMINI_INTELLIGENCE_MODEL,
            systemInstruction = com.craftflowtechnologies.meetingmind.ai.faith.Prompts.faithContract + "\n\n" + prompt.system(),
            prompt = SceneBuilder.prompt(ws, recordingType),
            responseSchema = null,
            timeoutMs = 90_000L
        ))
        return if (r is AiResult.Success) SceneBuilder.parse(r.value, ws) else SceneBuilder.fromWindows(ws)
    }
}
