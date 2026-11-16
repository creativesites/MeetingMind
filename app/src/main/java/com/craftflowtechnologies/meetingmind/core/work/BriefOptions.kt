package com.craftflowtechnologies.meetingmind.core.work

import com.craftflowtechnologies.meetingmind.ai.tools.TranscriptToolPrompts

/*
 * The Brief's controls (docs/work-vertical-design-spec.md §R5). Audience, tone and language change
 * only the two AI paragraphs: they reach the prompt as a style block, and the fidelity contract and
 * the citation rule are unchanged. Length and sections change only which deterministic lines print.
 * Nothing here adds a fact.
 */

enum class BriefAudience(val label: String, val guidance: String) {
    CLIENT_SPONSOR("Client sponsor", "a client sponsor who needs the picture and the decisions they must make, without internal detail"),
    INTERNAL_TEAM("Internal team", "colleagues on the same project, who know the context and need the next actions"),
    EXECUTIVE("Executive", "an executive with little time: lead with the decision, the risk and the ask"),
    JUST_ME("Just me", "the author, as a private reminder of where things stand")
}

enum class BriefTone(val label: String, val guidance: String) {
    PLAIN("Plain", "plain, neutral sentences with no jargon"),
    FORMAL("Formal", "formal, measured sentences in the third person"),
    WARM("Warm", "warm and encouraging, while still stating the facts plainly"),
    DIRECT("Direct", "direct and brief, short sentences that lead with the point")
}

/** [linesPerSection] caps each deterministic section (null: every line). [showRecommended] keeps the second paragraph. */
enum class BriefLength(val label: String, val linesPerSection: Int?, val showRecommended: Boolean) {
    HEADLINE("Headline", 2, false),
    ONE_PAGE("One page", 5, true),
    DETAILED("Detailed", null, true)
}

/** The nine parts a brief can show. [EVIDENCE] also controls the quotes and playback behind the lines. */
enum class BriefSection(val label: String) {
    EXECUTIVE("Executive picture"),
    CHANGES("What changed"),
    DECISIONS("Decisions"),
    COMMITMENTS("Commitments"),
    RISKS("Risks"),
    QUESTIONS("Open questions"),
    NEXT_7_DAYS("Next 7 days"),
    RECOMMENDED("Recommended next conversation"),
    EVIDENCE("Evidence appendix")
}

data class BriefOptions(
    val audience: BriefAudience = BriefAudience.CLIENT_SPONSOR,
    val tone: BriefTone = BriefTone.PLAIN,
    val length: BriefLength = BriefLength.ONE_PAGE,
    val sections: Set<BriefSection> = BriefSection.entries.toSet(),
    val language: String = DEFAULT_LANGUAGE,
    val footer: Boolean = true
) {
    /** The fields that reach the prompt. Two options with the same style write the same prose. */
    val styleKey: String get() = "${audience.name}|${tone.name}|$language"

    /** Every field, so the screen can tell two option sets apart. */
    val optionsKey: String
        get() = "$styleKey|${length.name}|${sections.map { it.name }.sorted().joinToString(",")}|$footer"

    companion object {
        const val DEFAULT_LANGUAGE = "English"
        val LANGUAGES = listOf(DEFAULT_LANGUAGE, "French", "Spanish", "Portuguese", "Swahili")
        val DEFAULT = BriefOptions()
    }
}

/** The text that asks the model for a style. The fidelity contract and citation rule are always included. */
object BriefPrompt {

    fun styleBlock(options: BriefOptions): String = buildString {
        appendLine("Style for this brief. It changes wording only; the facts, dates, names and decisions stay exactly as given.")
        appendLine("- Write for ${options.audience.guidance}.")
        appendLine("- Tone: ${options.tone.guidance}.")
        append("- Write in ${options.language}.")
    }

    fun build(kindLabel: String, title: String, options: BriefOptions, facts: String): String = buildString {
        appendLine(TranscriptToolPrompts.FIDELITY_CONTRACT.trim())
        appendLine()
        appendLine(styleBlock(options))
        appendLine()
        appendLine("Write two short pieces for a ${kindLabel.lowercase()} about \"$title\", using ONLY the facts below.")
        appendLine("1. \"executive\": 2 to 4 sentences giving the picture: where things stand, what changed, what matters most.")
        appendLine("2. \"recommended\": 1 to 3 sentences on what the next conversation should cover.")
        appendLine("Every sentence must cite the ids, in square brackets in the facts, of the items or quotes it rests on. A sentence with no citation will be thrown away. Never state a date, name, number or decision that is not in the facts.")
        appendLine("Answer with JSON only: {\"executive\":[{\"text\":string,\"cites\":[string]}],\"recommended\":[{\"text\":string,\"cites\":[string]}]}")
        appendLine()
        appendLine("Facts:")
        append(facts)
    }
}

/**
 * Applies [BriefOptions] to a built brief. Pure: it removes lines and paragraphs, never edits them.
 * A section that is off prints nothing; a length caps how many lines each section shows.
 */
object BriefShaper {

    fun shape(brief: Brief, options: BriefOptions): Brief {
        val on = options.sections
        fun cap(lines: List<BriefLine>): List<BriefLine> = options.length.linesPerSection?.let { lines.take(it) } ?: lines

        val executive = if (BriefSection.EXECUTIVE in on) brief.executive else emptyList()
        val changes = if (BriefSection.CHANGES in on) cap(brief.changes) else emptyList()
        val decisions = if (BriefSection.DECISIONS in on) cap(brief.decisions + brief.decisionsRequired) else emptyList()
        val youOwe = if (BriefSection.COMMITMENTS in on) cap(brief.youOwe) else emptyList()
        val theyOwe = if (BriefSection.COMMITMENTS in on) cap(brief.theyOwe) else emptyList()
        val risks = if (BriefSection.RISKS in on) cap(brief.risks) else emptyList()
        val questions = if (BriefSection.QUESTIONS in on) cap(brief.questions) else emptyList()
        val next7 = if (BriefSection.NEXT_7_DAYS in on) cap(brief.next7) else emptyList()
        val recommended = if (BriefSection.RECOMMENDED in on && options.length.showRecommended) brief.recommended else emptyList()

        val shaped = brief.copy(
            executive = executive, changes = changes, decisions = decisions, decisionsRequired = emptyList(),
            youOwe = youOwe, theyOwe = theyOwe, risks = risks, questions = questions, next7 = next7, recommended = recommended
        )
        // The quotes behind the lines go with the appendix: a line that cites nothing shown has no quote to open.
        val evidence = if (BriefSection.EVIDENCE in on) {
            val cited = (shaped.citedItemIds + executive.flatMap { it.cites } + recommended.flatMap { it.cites }).toSet()
            brief.evidence.filter { e -> e.itemId in cited || e.evidenceId in cited }
        } else {
            emptyList()
        }
        return shaped.copy(evidence = evidence)
    }
}

/** The text of a brief for the clipboard: formatted (HTML, for rich paste) and plain. */
object BriefCopy {

    fun plain(brief: Brief, options: BriefOptions): String = buildString {
        appendLine(brief.title)
        appendLine(brief.status.label + (brief.progress?.let { (done, total) -> " · $done of $total commitments done" }.orEmpty()))
        appendLine()
        brief.executive.forEach { appendLine(it.text) }
        for ((title, lines) in sections(brief)) {
            if (lines.isEmpty()) continue
            appendLine()
            appendLine(title.uppercase())
            lines.forEach { appendLine("- " + it.text + (it.detail?.let { d -> " ($d)" }.orEmpty())) }
        }
        if (brief.recommended.isNotEmpty()) {
            appendLine()
            appendLine("RECOMMENDED NEXT CONVERSATION")
            brief.recommended.forEach { appendLine(it.text) }
        }
        if (options.footer) {
            appendLine()
            append(footer(brief))
        }
    }.trimEnd()

    fun html(brief: Brief, options: BriefOptions): String = buildString {
        append("<h2>").append(esc(brief.title)).append("</h2>")
        append("<p>").append(esc(brief.status.label)).append(brief.progress?.let { (done, total) -> " · $done of $total commitments done" }.orEmpty()).append("</p>")
        brief.executive.forEach { append("<p>").append(esc(it.text)).append("</p>") }
        for ((title, lines) in sections(brief)) {
            if (lines.isEmpty()) continue
            append("<h3>").append(esc(title)).append("</h3><ul>")
            lines.forEach { append("<li>").append(esc(it.text)).append(it.detail?.let { d -> " (${esc(d)})" }.orEmpty()).append("</li>") }
            append("</ul>")
        }
        if (brief.recommended.isNotEmpty()) {
            append("<h3>Recommended next conversation</h3>")
            brief.recommended.forEach { append("<p>").append(esc(it.text)).append("</p>") }
        }
        if (options.footer) append("<p><small>").append(esc(footer(brief))).append("</small></p>")
    }

    /** The sections that print, in the order the screen shows them. */
    fun sections(brief: Brief): List<Pair<String, List<BriefLine>>> = listOf(
        "What changed" to brief.changes,
        "Decisions" to brief.decisions,
        "You owe" to brief.youOwe,
        "They owe" to brief.theyOwe,
        "Risks" to brief.risks,
        "Open questions" to brief.questions,
        "Next 7 days" to brief.next7
    )

    fun footer(brief: Brief): String =
        if (brief.confidential) "Prepared with MeetingMind from confidential records that stay on this phone."
        else "Prepared with MeetingMind from confirmed records."

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
