package com.craftflowtechnologies.meetingmind.ai.voice.read.smart

/**
 * The only change made to a note's wording when no model may be used (offline, private, or the cloud
 * voice isn't allowed): shorthand that reads badly aloud is spelled out. Nothing is added or removed
 * that carries meaning, and numbers, names and references are never touched.
 */
object SpokenForm {
    private val rules = listOf(
        Regex("\\be\\.g\\.,?", RegexOption.IGNORE_CASE) to "for example,",
        Regex("\\bi\\.e\\.,?", RegexOption.IGNORE_CASE) to "that is,",
        Regex("\\betc\\.", RegexOption.IGNORE_CASE) to "and so on.",
        Regex("\\bvs\\.?(?=\\s)", RegexOption.IGNORE_CASE) to "versus",
        Regex("\\bw/o\\b", RegexOption.IGNORE_CASE) to "without",
        Regex("\\bw/(?=\\s)", RegexOption.IGNORE_CASE) to "with",
        Regex("(?<=\\d)\\s?%") to " percent",
        Regex("\\s&\\s") to " and ",
        Regex("\\s{2,}") to " "
    )

    fun apply(text: String): String = rules.fold(text) { acc, (re, to) -> re.replace(acc, to) }.trim()
}
