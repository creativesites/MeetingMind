package com.example.feature.meetingdetail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.tools.ToolOutcome
import com.example.ai.tools.ToolRunResult
import com.example.core.common.Formatters
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line
import com.example.ui.theme.LineFaint
import com.example.ui.theme.LineSoft
import com.example.ui.theme.SurfaceSunk

/**
 * Shows what an AI tool produced.
 *
 * One sheet for every tool, rendering by outcome shape rather than by tool, because the shapes are
 * what differ: a revision needs accept/reject, findings need citations that jump to the audio, a
 * document needs to be readable and copyable, a title needs one tap to apply.
 *
 * Nothing here applies itself. A tool proposes; the user decides. That is the same discipline the
 * cleanup review flow already follows, and it is what makes running a tool on a real recording a
 * safe thing to try.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolResultSheet(
    result: ToolRunResult,
    onDismiss: () -> Unit,
    onApplyRevision: (ToolOutcome.TranscriptRevision) -> Unit,
    onApplyTitle: (String) -> Unit,
    onJumpTo: (segmentId: String, startMs: Long?) -> Unit,
    onCopyDocument: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White
    ) {
        Column(modifier = Modifier.testTag("tool_result_sheet")) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(result.tool.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, color = Ink)
                Text(result.scopeDescription, fontSize = 13.sp, color = InkMuted)
            }
            // Which engine actually produced this. A result the user is being asked to accept
            // should say what made it.
            Text(
                text = "Produced by ${result.engine}",
                fontSize = 11.5.sp,
                color = InkMuted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
            )
            HorizontalDivider(color = LineSoft, modifier = Modifier.padding(top = 14.dp))

            when (val outcome = result.outcome) {
                is ToolOutcome.TranscriptRevision -> RevisionBody(outcome, onApplyRevision, onDismiss)
                is ToolOutcome.Findings -> FindingsBody(outcome, onJumpTo)
                is ToolOutcome.TextDocument -> DocumentBody(outcome, onCopyDocument)
                is ToolOutcome.TitleSuggestion -> TitleBody(outcome, onApplyTitle, onDismiss)
                is ToolOutcome.ContextExpansion -> ContextBody(outcome, onJumpTo)
            }
        }
    }
}

@Composable
private fun RevisionBody(
    outcome: ToolOutcome.TranscriptRevision,
    onApply: (ToolOutcome.TranscriptRevision) -> Unit,
    onDismiss: () -> Unit
) {
    if (outcome.edits.isEmpty()) {
        EmptyNote(
            if (outcome.rejectedCount > 0) {
                "Nothing was changed. ${outcome.rejectedCount} suggested change${if (outcome.rejectedCount == 1) "" else "s"} " +
                    "did not pass the fidelity check — a rewrite that drops a number or a name is rejected rather than shown."
            } else {
                "Nothing needed changing here."
            }
        )
        return
    }

    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
        items(outcome.edits.size) { index ->
            val edit = outcome.edits[index]
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)) {
                // The existing word-diff rendering, so a proposal reads the same way here as it
                // does in the cleanup review the user has already seen: changed runs underlined,
                // and the original available underneath rather than hidden.
                DiffProposedText(
                    runs = com.example.core.common.WordDiff.diffRuns(edit.before, edit.after)
                        .map { DiffRun(it.text, it.changed) },
                    baseStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 23.sp, color = Ink)
                )
                Text(
                    text = edit.before,
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = InkMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (index != outcome.edits.lastIndex) HorizontalDivider(color = LineFaint)
        }
    }

    if (outcome.rejectedCount > 0) {
        Text(
            text = "${outcome.rejectedCount} further suggestion${if (outcome.rejectedCount == 1) " was" else "s were"} rejected by the fidelity check and are not shown.",
            fontSize = 12.sp,
            lineHeight = 19.sp,
            color = InkMuted,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = { onApply(outcome) },
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).testTag("tool_result_apply")
        ) {
            Text("Apply ${outcome.edits.size} change${if (outcome.edits.size == 1) "" else "s"}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Line)
        ) {
            Text("Discard", fontSize = 14.sp, color = InkSecondary)
        }
    }
}

@Composable
private fun FindingsBody(outcome: ToolOutcome.Findings, onJumpTo: (String, Long?) -> Unit) {
    if (outcome.items.isEmpty()) {
        EmptyNote("Nothing found. An empty result is a real answer — it means the transcript does not support any.")
        return
    }
    LazyColumn(modifier = Modifier.heightIn(max = 460.dp).padding(bottom = 18.dp)) {
        items(outcome.items.size) { index ->
            val finding = outcome.items[index]
            val citation = finding.sourceSegmentIds.firstOrNull()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (citation != null) {
                            Modifier.clickable { onJumpTo(citation, finding.startMs) }
                        } else Modifier
                    )
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text(finding.text, fontSize = 15.sp, lineHeight = 22.sp, color = Ink)
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    finding.detail?.let { Text(it, fontSize = 12.5.sp, color = InkMuted) }
                    if (citation != null && finding.startMs != null) {
                        Surface(shape = RoundedCornerShape(6.dp), color = AccentWash) {
                            Text(
                                text = Formatters.formatDurationHms(finding.startMs!!),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Accent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        // Said plainly rather than left blank: the finding is real, but nothing in
                        // the transcript was cited for it, so it cannot be checked against the audio.
                        Text("No citation", fontSize = 11.5.sp, color = InkMuted)
                    }
                }
            }
            if (index != outcome.items.lastIndex) HorizontalDivider(color = LineFaint)
        }
    }
}

@Composable
private fun DocumentBody(outcome: ToolOutcome.TextDocument, onCopy: (String) -> Unit) {
    LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceSunk,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = outcome.markdown,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = InkSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Button(
            onClick = { onCopy(outcome.markdown) },
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("tool_result_copy")
        ) {
            Text("Copy", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TitleBody(outcome: ToolOutcome.TitleSuggestion, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    Text(
        text = outcome.title,
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        color = Ink,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = { onApply(outcome.title) },
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).testTag("tool_result_apply_title")
        ) {
            Text("Use this title", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Line)) {
            Text("Keep current", fontSize = 14.sp, color = InkSecondary)
        }
    }
}

@Composable
private fun ContextBody(outcome: ToolOutcome.ContextExpansion, onJumpTo: (String, Long?) -> Unit) {
    val first = outcome.segmentIds.firstOrNull()
    EmptyNote(
        if (first == null) "Nothing to expand." else
            "Widened to ${outcome.segmentIds.size} paragraphs. Tap to jump to the first."
    )
    if (first != null) {
        Row(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Button(
                onClick = { onJumpTo(first, null) },
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show in transcript", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text = text,
        fontSize = 13.5.sp,
        lineHeight = 21.sp,
        color = InkSecondary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp)
    )
}
