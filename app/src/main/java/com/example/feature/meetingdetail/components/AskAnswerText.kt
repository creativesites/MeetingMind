package com.example.feature.meetingdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash

private val CITATION_REGEX = Regex("\\[(\\d{1,2}:\\d{2}(?::\\d{2})?)\\]")

/** One piece of an Ask AI answer: prose, or a `[MM:SS]` citation marker. */
private sealed interface AskAnswerPart {
    data class Prose(val text: String) : AskAnswerPart
    data class Citation(val label: String) : AskAnswerPart
}

private fun parseAskAnswer(content: String): List<AskAnswerPart> {
    val parts = mutableListOf<AskAnswerPart>()
    var cursor = 0
    for (match in CITATION_REGEX.findAll(content)) {
        if (match.range.first > cursor) {
            parts += AskAnswerPart.Prose(content.substring(cursor, match.range.first))
        }
        parts += AskAnswerPart.Citation(match.groupValues[1])
        cursor = match.range.last + 1
    }
    if (cursor < content.length) parts += AskAnswerPart.Prose(content.substring(cursor))
    return parts
}

/**
 * Renders an Ask AI answer as prose with inline `[MM:SS]` citation chips
 * (docs/recording-page-implementation.md §2.8) — mono, Accent, on [AccentWash]. Only markers the
 * backend already validated against a real segment reach here (see
 * RealMeetingIntelligenceEngine.parseCitedTimestamps), so every chip shown is real.
 */
@Composable
fun AskAnswerText(
    content: String,
    onCitationClick: (label: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val parts = parseAskAnswer(content)
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        parts.forEach { part ->
            when (part) {
                is AskAnswerPart.Prose -> BasicText(text = part.text, style = RecordingPageType.askAnswer)
                is AskAnswerPart.Citation -> BasicText(
                    text = part.label,
                    style = RecordingPageType.askAnswer.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent
                    ),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .background(AccentWash, RoundedCornerShape(5.dp))
                        .clickable { onCitationClick(part.label) }
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}
