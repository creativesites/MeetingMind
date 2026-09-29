package com.craftflowtechnologies.meetingmind.feature.notes.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.notes.PasteTool
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk

/** The quiet line under a Markdown block: where it came from, its AI tools, and Step back. */
@Composable
internal fun MarkdownFooter(block: NoteBlock, canStepBack: Boolean, onTools: () -> Unit, onStepBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        block.payload[NoteBlock.PAYLOAD_PASTE_SOURCE]?.let { Text("From $it", fontSize = 12.sp, color = InkMuted) }
        Row(Modifier.clip(RoundedCornerShape(50)).background(AccentWash).clickable(onClick = onTools).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(com.craftflowtechnologies.meetingmind.ui.icons.AiMark, contentDescription = null, tint = Accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text("AI tools", fontSize = 12.5.sp, color = Accent, fontWeight = FontWeight.SemiBold)
        }
        if (canStepBack) Row(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onStepBack).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = InkSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Step back", fontSize = 12.5.sp, color = InkSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PasteToolsSheet(
    state: NoteEditorViewModel.PasteToolState,
    block: NoteBlock?,
    history: List<Pair<String, String>>,
    onRun: (PasteTool) -> Unit,
    onTidy: () -> Unit,
    onAccept: (replace: Boolean) -> Unit,
    onStepBack: () -> Unit,
    onOriginal: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = SurfaceBase) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(com.craftflowtechnologies.meetingmind.ui.icons.AiMark, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(state.tool?.label ?: "AI tools", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
            }
            val words = block?.content?.text?.split(Regex("\\s+"))?.count { it.isNotBlank() } ?: 0
            Text("$words words" + (block?.payload?.get(NoteBlock.PAYLOAD_PASTE_SOURCE)?.let { " · from $it" } ?: ""), fontSize = 12.5.sp, color = InkMuted, modifier = Modifier.padding(top = 2.dp))

            when {
                state.busy -> Row(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(state.progress ?: "Working…", color = InkSecondary, fontSize = 14.sp)
                }
                state.result != null -> Preview(state, block?.content?.text.orEmpty(), onAccept)
                else -> Unit
            }
            state.error?.let { Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp)) }

            if (!state.busy) {
                if (state.result == null) {
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuietAction(Icons.Filled.CleaningServices, "Tidy (no AI)", onTidy)
                        if (history.isNotEmpty()) QuietAction(Icons.AutoMirrored.Filled.Undo, "Step back", onStepBack)
                        if (block?.payload?.containsKey(NoteBlock.PAYLOAD_RAW) == true) QuietAction(Icons.Filled.History, "Original", onOriginal)
                    }
                    if (history.isNotEmpty()) Text("Applied: " + history.joinToString(" → ") { it.first }, fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 8.dp))
                }
                PasteTool.Group.entries.forEach { group ->
                    Text(group.title, fontSize = 12.sp, color = InkMuted, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                    if (group == PasteTool.Group.PRESET) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PasteTool.entries.filter { it.group == group }.forEach { t ->
                                Surface(onClick = { onRun(t) }, shape = RoundedCornerShape(16.dp), color = if (state.tool == t) AccentWash else SurfaceSunk, border = BorderStroke(1.dp, if (state.tool == t) Accent else Line), modifier = Modifier.width(150.dp)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(t.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                        Text(t.hint, fontSize = 12.sp, color = InkSecondary, lineHeight = 16.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PasteTool.entries.filter { it.group == group }.forEach { t ->
                                Surface(onClick = { onRun(t) }, shape = RoundedCornerShape(50), color = if (state.tool == t) AccentWash else SurfaceSunk, border = BorderStroke(1.dp, Line)) {
                                    Text(t.label, fontSize = 13.sp, color = if (state.tool == t) Accent else Ink, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Preview(state: NoteEditorViewModel.PasteToolState, before: String, onAccept: (Boolean) -> Unit) {
    var showBefore by remember { mutableStateOf(false) }
    val below = state.tool?.output == PasteTool.Output.BELOW
    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to "After", true to "Before").forEach { (b, label) ->
            Surface(onClick = { showBefore = b }, shape = RoundedCornerShape(50), color = if (showBefore == b) Ink else SurfaceSunk) {
                Text(label, fontSize = 13.sp, color = if (showBefore == b) OnInk else InkSecondary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
        state.provider?.let { Text("by $it", fontSize = 12.sp, color = InkMuted, modifier = Modifier.align(Alignment.CenterVertically)) }
    }
    val shown = if (showBefore) before else state.result.orEmpty()
    val delta = state.result.orEmpty().length - before.length
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceSunk, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(max = 380.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
            val items = remember(shown) { com.craftflowtechnologies.meetingmind.core.notes.MarkdownImport.parse(shown, "preview") }
            MarkdownPreview(items)
        }
    }
    if (!below && before.isNotEmpty()) Text(
        if (delta < 0) "${-delta * 100 / before.length}% shorter" else if (delta > 0) "${delta * 100 / before.length}% longer" else "Same length",
        fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 6.dp)
    )
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!below) Surface(onClick = { onAccept(false) }, shape = RoundedCornerShape(50), color = SurfaceBase, border = BorderStroke(1.dp, Line), modifier = Modifier.weight(1f)) {
            Text("Add below", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 13.dp))
        }
        Surface(onClick = { onAccept(true) }, shape = RoundedCornerShape(50), color = Ink, modifier = Modifier.weight(1f)) {
            Text(if (below) "Add below" else "Use this", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnInk, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 13.dp))
        }
    }
}

@Composable
private fun QuietAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(SurfaceSunk).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = InkSecondary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, color = Ink)
    }
}
