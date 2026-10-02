package com.craftflowtechnologies.meetingmind.ai.notes

/**
 * Formats small-group discussion guide sections into clean, readable text ready for
 * messaging apps such as WhatsApp, Telegram, or SMS.
 */
object StudyGuideFormatter {

    fun toWhatsApp(title: String?, sections: List<SectionDraft>): String = buildString {
        val heading = (title?.ifBlank { "Small-Group Discussion Guide" } ?: "Small-Group Discussion Guide").uppercase()
        appendLine("📖 *$heading*")
        appendLine("───────────────")
        sections.forEach { section ->
            if (section.items.isNotEmpty()) {
                appendLine()
                val emoji = when (section.key.lowercase()) {
                    "scripture" -> "📜 "
                    "main_idea" -> "💡 "
                    "icebreaker" -> "☕ "
                    "questions" -> "❓ "
                    "application" -> "🎯 "
                    "prayer" -> "🙏 "
                    else -> "• "
                }
                appendLine("$emoji*${section.title.uppercase()}*")
                section.items.forEachIndexed { idx, item ->
                    if (section.key.contains("question") || section.key.contains("points")) {
                        appendLine("${idx + 1}. ${item.text}")
                    } else {
                        appendLine("• ${item.text}")
                    }
                }
            }
        }
        appendLine()
        appendLine("───────────────")
        appendLine("_Generated with MeetingMind_")
    }.trim()
}
