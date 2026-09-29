package com.example.feature.notes.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.notes.MarkdownImport
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash
import com.example.ui.theme.Danger
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line
import com.example.ui.theme.OnInk
import com.example.ui.theme.SurfaceBase
import com.example.ui.theme.SurfaceSunk

/** The quick actions offered on selected text, with the instruction each sends. */
internal val AI_EDIT_ACTIONS = listOf(
    "Rewrite" to "Rewrite this to read more clearly. Keep the meaning and the writer's voice.",
    "Shorten" to "Make this shorter. Keep every important point.",
    "Expand" to "Expand this with more detail and explanation, in the same voice.",
    "Make professional" to "Rewrite this in a clear, confident, professional tone.",
    "Fix grammar" to "Fix spelling, grammar and punctuation only. Change nothing else.",
    "Turn into bullets" to "Turn this into a concise bulleted list, one idea per item.",
    "Simplify" to "Rewrite this in simple, plain words anyone can follow.",
    "Summarise" to "Summarise this in one or two sentences."
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AiEditSheet(
    edit: NoteEditorViewModel.AiEdit,
    onRun: (label: String, instruction: String) -> Unit,
    onReplace: () -> Unit,
    onInsertBelow: () -> Unit,
    onDismiss: () -> Unit
) {
    var custom by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = SurfaceBase) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(com.example.ui.icons.AiMark, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Edit with AI", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            }
            Text(
                edit.original, fontSize = 13.sp, lineHeight = 19.sp, color = InkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceSunk).padding(12.dp)
            )

            when {
                edit.busy -> Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("${edit.action ?: "Working"}…", color = InkSecondary, fontSize = 14.sp)
                }
                edit.result != null -> {
                    Text(edit.action ?: "Result", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = AccentWash, modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        Text(
                            remember(edit.result) { MarkdownImport.parse(edit.result, "preview").joinToString("\n") { b ->
                                (if (b.type.isListItem) "•  " else "") + b.content.text.ifEmpty { com.example.core.notes.NoteText.plain(listOf(b)) }
                            } },
                            fontSize = 15.sp, lineHeight = 23.sp, color = Ink,
                            modifier = Modifier.verticalScroll(rememberScrollState()).padding(14.dp)
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(onClick = onInsertBelow, shape = RoundedCornerShape(50), color = SurfaceBase, border = BorderStroke(1.dp, Line), modifier = Modifier.weight(1f)) {
                            Text("Insert below", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 13.dp))
                        }
                        Surface(onClick = onReplace, shape = RoundedCornerShape(50), color = Ink, modifier = Modifier.weight(1f)) {
                            Text("Replace", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnInk, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 13.dp))
                        }
                    }
                    Text("Try something else", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                }
                else -> Spacer(Modifier.size(16.dp))
            }

            edit.error?.let { Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) }

            if (!edit.busy) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AI_EDIT_ACTIONS.forEach { (label, instruction) ->
                        Surface(onClick = { onRun(label, instruction) }, shape = RoundedCornerShape(50), color = if (edit.action == label) AccentWash else SurfaceSunk, border = BorderStroke(1.dp, Line)) {
                            Text(label, fontSize = 13.sp, color = if (edit.action == label) Accent else Ink, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        custom, { custom = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Or say what to do… e.g. make it warmer") }, maxLines = 3
                    )
                    IconButton(onClick = { if (custom.isNotBlank()) onRun("Custom", custom.trim()) }, enabled = custom.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run", tint = if (custom.isNotBlank()) Accent else InkMuted)
                    }
                }
            }
        }
    }
}
