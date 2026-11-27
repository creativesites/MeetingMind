package com.craftflowtechnologies.meetingmind.ai.faith

/**
 * The one place that decides whether a sentence claims to speak for God in the assistant's own
 * voice ("God is telling you…") rather than reporting someone else's words ("your note says God
 * told him…", "the preacher said…").
 *
 * Before this existed, the Faith assistant ([com.craftflowtechnologies.meetingmind.ai.assistant.FaithGuard])
 * and the sermon "Ask" grounding ([AskSermon]) each carried their own copy of this check, and they
 * had already drifted apart (one caught "God told me", the other didn't). Both now call this.
 */
object FaithAuthorityGuard {
    /** Voice-of-God phrasing in the answer's own voice. Reporting ("he said God told him") is fine. */
    private val AUTHORITY = listOf(
        Regex("\\bthus says the lord\\b", RegexOption.IGNORE_CASE),
        Regex("\\bGod is (telling|saying to|calling) you\\b", RegexOption.IGNORE_CASE),
        Regex("\\bthe Lord (is )?(says|saying) to you\\b", RegexOption.IGNORE_CASE),
        Regex("\\bI (prophesy|declare) (over|that|to) you\\b", RegexOption.IGNORE_CASE),
        Regex("\\bGod (has revealed|wants me to tell you)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bGod told me\\b", RegexOption.IGNORE_CASE)
    )

    /** "He said God told him…", "your note says…" and "Matthew Henry writes…" report someone else's
     * words; "Thus says the Lord" in the assistant's own voice never does. */
    private fun reported(sentence: String) =
        !Regex("\\bthus says\\b", RegexOption.IGNORE_CASE).containsMatchIn(sentence) &&
            Regex("\\b(he|she|they|the (preacher|speaker|pastor)|your note|[A-Z][a-z]+) (said|says|told|shared|described|explained|wrote)\\b")
                .containsMatchIn(sentence)

    private fun sentences(text: String): List<String> =
        Regex("[^.!?\\n]*(?:[.!?]+|\\n|$)").findAll(text).map { it.value }.filter { it.isNotEmpty() }.toList()

    /** Sentences in the answer's own voice that claim to speak for God. */
    fun violations(text: String): List<String> =
        sentences(text).filter { s -> AUTHORITY.any { it.containsMatchIn(s) } && !reported(s) }.map { it.trim() }

    /** [text] with every [violations] sentence removed, whitespace tidied. */
    fun clean(text: String): String {
        val bad = violations(text).toSet()
        if (bad.isEmpty()) return text
        return sentences(text).filterNot { it.trim() in bad }.joinToString("").replace(Regex("[ \\t]{2,}"), " ").trim()
    }
}
