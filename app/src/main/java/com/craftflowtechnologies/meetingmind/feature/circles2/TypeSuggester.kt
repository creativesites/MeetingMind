package com.craftflowtechnologies.meetingmind.feature.circles2

import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser

/**
 * Suggests a better-fitting post type while someone writes (the type follows the words, spec §9.4).
 *
 * Pure keyword heuristics, case-insensitive, on word boundaries. Each rule scores how many of its patterns
 * match; a type is suggested only when it beats the current type's score, so a post that already fits its
 * type never gets a suggestion. Ties go to the earlier rule. Nothing here touches Android, so it is unit-tested on the JVM.
 */
object PostTypeSuggester {

    /** Shorter text is too little to judge. */
    private const val MIN_LENGTH = 12

    private class Rule(val type: PostType, val patterns: List<Regex>)

    private fun rx(vararg p: String): List<Regex> = p.map { Regex(it, RegexOption.IGNORE_CASE) }

    // Faith & general. Order matters only for ties: "God answered our prayer" is a testimony, not a request.
    private val faithRules = listOf(
        Rule(PostType.Testimony, rx(
            """\bthank(?:s)?\s+God\b""",
            """\bGod\s+answered\b""",
            """\btestimon(?:y|ies)\b""",
            """\bpraise\s+report\b"""
        )),
        Rule(PostType.Prayer, rx(
            """\bpray(?:ing|ed)?\s+for\b""",
            """\bplease\s+pray\b""",
            """\bprayers?\b"""
        )),
        Rule(PostType.Devotional, rx(
            """\bdevotionals?\b"""
        )),
        Rule(PostType.Reading, rx(
            """\breading\s+plan\b""",
            """\bchapters?\b""",
            """\btoday['’]s\s+reading\b"""
        )),
        Rule(PostType.Encouragement, rx(
            """\bencourag(?:e|es|ed|ing|ement)\b""",
            """\byou['’]ve\s+got\s+this\b"""
        ))
    )

    // Work. Only used in Work circles.
    private val workRules = listOf(
        Rule(PostType.Decision, rx(
            """\bdecided\b""",
            """\bwe\s+agreed\b""",
            """\bdecisions?\b"""
        )),
        Rule(PostType.Blocker, rx(
            """\bblocked\b""",
            """\bwaiting\s+on\b""",
            """\bcan['’]t\s+proceed\b"""
        )),
        Rule(PostType.Task, rx(
            """\baction:""",
            """@\w+\s+will\b""",
            """\bby\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""",
            """\btodos?\b"""
        )),
        Rule(PostType.Brief, rx(
            """\bsummary\s+of\s+the\s+meeting\b""",
            """\bbrief\b""",
            """\brecap\b"""
        )),
        Rule(PostType.Standup, rx(
            """\byesterday\b""",
            """\btoday\s+I\b""",
            """\bstand-?up\b"""
        ))
    )

    /**
     * Returns the allowed type the body fits better than [current], or null.
     *
     * Never returns [current]. Never returns a type outside [allowed]. Returns an admin-only type only when
     * [isAdmin] is true. [isWork] selects the Work rules; otherwise the Faith & general rules apply.
     */
    fun suggest(
        body: String,
        current: PostType,
        allowed: List<PostType>,
        isWork: Boolean,
        isAdmin: Boolean = false
    ): PostType? {
        val text = body.trim()
        if (text.length < MIN_LENGTH) return null
        val scores = scoresFor(text, isWork)
        val currentScore = scores.firstOrNull { it.first == current }?.second ?: 0
        return scores
            .filter { (type, score) ->
                score > 0 &&
                    score > currentScore &&
                    type != current &&
                    type in allowed &&
                    (!type.adminOnly || isAdmin)
            }
            .maxByOrNull { it.second }
            ?.first
    }

    /** The label used in the composer chip, e.g. "a prayer request" in "Looks like a prayer request". */
    fun describe(type: PostType): String = when (type) {
        PostType.Prayer -> "a prayer request"
        PostType.Testimony -> "a testimony"
        PostType.Devotional -> "a devotional"
        PostType.Reading -> "a reading plan post"
        PostType.Encouragement -> "an encouragement"
        PostType.Task -> "an action item"
        PostType.Decision -> "a decision"
        PostType.Blocker -> "a blocker"
        PostType.Brief -> "a meeting brief"
        PostType.Standup -> "a status update"
        else -> "a ${type.label.lowercase()} post"
    }

    /** Per-type hit counts in rule order. A verse reference counts as a Devotional hit in Faith circles. */
    private fun scoresFor(text: String, isWork: Boolean): List<Pair<PostType, Int>> {
        if (isWork) {
            return workRules.map { rule -> rule.type to rule.patterns.count { it.containsMatchIn(text) } }
        }
        val verse = ScriptureReferenceParser.findAll(text).isNotEmpty()
        return faithRules.map { rule ->
            val keywordHits = rule.patterns.count { it.containsMatchIn(text) }
            val verseHit = if (rule.type == PostType.Devotional && verse) 1 else 0
            rule.type to (keywordHits + verseHit)
        }
    }
}
