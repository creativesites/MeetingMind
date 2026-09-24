package com.example.core.devotional

import com.example.core.scripture.ScriptureReference

/** Who wrote a devotional, which decides the label it always carries. */
enum class DevotionalOrigin { CLOUD_AI, DEVICE_AI, CLASSIC, MINE, CARE }

/**
 * One day's devotional, before it becomes a note. Scripture is references only — verse text is
 * always fetched from the Bible, never taken from a model (PLAN_V2 F2, the labelled-AI boundary).
 */
data class Devotional(
    val day: LocalDay,
    val origin: DevotionalOrigin,
    val title: String,
    val scripture: List<ScriptureReference>,
    /** For a classic, the words the author set at its head, printed as they wrote them. */
    val keyText: String? = null,
    val reflection: List<String>,
    val application: List<String> = emptyList(),
    val prayer: String? = null,
    val motivation: String? = null,
    val insight: Quote? = null,
    val question: String? = null,
    /** Always shown: "AI-written devotional", "From Morning and Evening…". */
    val label: String,
    /** What wrote it, for the honest "written by" line ("gemini-…", "qwen…"). */
    val engine: String? = null,
    val season: LiturgicalDay? = null
)

/** A calendar date as "yyyy-MM-dd", the key a day's devotional is filed under. */
@JvmInline
value class LocalDay(val iso: String) {
    val date: java.time.LocalDate get() = java.time.LocalDate.parse(iso)
    companion object {
        fun of(date: java.time.LocalDate) = LocalDay(date.toString())
        fun today() = of(java.time.LocalDate.now())
    }
}

object DevotionalLabels {
    const val CLOUD = "AI-written devotional"
    const val DEVICE = "AI-written devotional · written on this phone"
    const val MINE = "Your devotional"
    const val CARE = "A gentle word for today"
}
