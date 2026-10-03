package com.craftflowtechnologies.meetingmind.ai.notes

/**
 * The ways a small-group discussion guide leaves the app. The WhatsApp text lives in
 * [StudyGuideFormatter]; this adds a Markdown file (for notes apps, email, a church's shared
 * folder) and the helpers both formats share. Pure, so every format is unit-tested.
 */
object GroupGuideExport {

    const val DEFAULT_TITLE = "Small-Group Discussion Guide"

    fun title(raw: String?): String = raw?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_TITLE

    /** Questions and points read as a numbered list; everything else as bullets. */
    fun isNumbered(sectionKey: String): Boolean = sectionKey.lowercase().let { it.contains("question") || it.contains("points") }

    fun toMarkdown(title: String?, sections: List<SectionDraft>): String = buildString {
        appendLine("# ${title(title)}")
        appendLine()
        appendLine("_Small-group discussion guide_")
        sections.filter { s -> s.items.any { it.text.isNotBlank() } }.forEach { section ->
            appendLine()
            appendLine("## ${section.title}")
            appendLine()
            section.items.filter { it.text.isNotBlank() }.forEachIndexed { i, item ->
                if (isNumbered(section.key)) appendLine("${i + 1}. ${item.text.trim()}") else appendLine("- ${item.text.trim()}")
            }
        }
        appendLine()
        appendLine("---")
        appendLine("_Drafted with MeetingMind. Review before sharing._")
    }.trim()

    /** A file name that is safe on every platform and still recognisable. */
    fun fileName(title: String?): String {
        val cleaned = title(title).replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(Regex("\\s+"), "_").take(60)
        return cleaned.ifEmpty { "Discussion_Guide" }
    }
}
