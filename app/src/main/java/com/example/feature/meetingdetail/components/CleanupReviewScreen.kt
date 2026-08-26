package com.example.feature.meetingdetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.WordDiff
import com.example.core.common.Formatters
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line

/** One paragraph's before/after, for [CleanupReviewScreen]. */
data class CleanupReviewSegment(val startMs: Long, val speakerName: String?, val original: String, val proposed: String)

/**
 * The AI-tools result review screen (docs/recording-page-implementation.md §2.7): "nothing is
 * applied until the user keeps it." Every rewrite tool in this redesign lands here — see
 * docs/recording-page-implementation.md §3.4 item 21 ("never auto-apply") — so this is a shared
 * component, not something specific to the one tool currently wired to it (Clean transcript).
 */
@Composable
fun CleanupReviewScreen(
    toolLabel: String,
    segments: List<CleanupReviewSegment>,
    engineLabel: String,
    elapsedMs: Long,
    showOriginal: Boolean,
    onToggleShowOriginal: () -> Unit,
    onBack: () -> Unit,
    onDiscard: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier
) {
    val changedWordCount = segments.sumOf { WordDiff.changedWordCount(it.original, it.proposed) }

    Column(modifier = modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 18.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = InkSecondary)
            }
            Column {
                Text(text = toolLabel, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Text(text = "${segments.size} paragraphs · not applied yet", fontSize = 11.5.sp, color = InkMuted)
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 30.dp, bottom = 16.dp)) {
            items(segments) { seg ->
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
                    Text(
                        text = "${Formatters.formatDurationHms(seg.startMs)} · ${(seg.speakerName ?: "UNKNOWN").uppercase()}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp,
                        color = InkMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (showOriginal) {
                        DiffOriginalText(text = seg.original, baseStyle = RecordingPageType.transcriptBodyEditing)
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    DiffProposedText(
                        runs = WordDiff.diffRuns(seg.original, seg.proposed).map { DiffRun(it.text, it.changed) },
                        baseStyle = RecordingPageType.transcriptBodyEditing
                    )
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                    Text(
                        text = if (showOriginal) "Hide original" else "Show original",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Accent,
                        modifier = Modifier.clickable(onClick = onToggleShowOriginal)
                    )
                    Spacer(modifier = Modifier.height(26.dp))
                    SunkenPanel {
                        Text(
                            text = "$changedWordCount word${if (changedWordCount == 1) "" else "s"} changed across ${segments.size} paragraph${if (segments.size == 1) "" else "s"}. Nothing outside what's shown here was touched.",
                            fontSize = 13.5.sp,
                            lineHeight = 22.sp,
                            color = InkSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${engineLabel.uppercase()} · ON DEVICE · ${formatElapsed(elapsedMs)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            color = InkMuted
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Line, RoundedCornerShape(16.dp))
                    .clickable(onClick = onDiscard)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Discard", fontSize = 14.sp, color = InkSecondary)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Ink, RoundedCornerShape(16.dp))
                    .clickable(onClick = onKeep)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Keep changes", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

private fun formatElapsed(elapsedMs: Long): String {
    val seconds = (elapsedMs / 1000.0)
    return if (seconds < 1) "<1s" else "${seconds.toInt()}s"
}
