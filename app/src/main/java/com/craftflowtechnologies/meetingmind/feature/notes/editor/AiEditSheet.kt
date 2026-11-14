package com.craftflowtechnologies.meetingmind.feature.notes.editor

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiCopyButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiRecoveryBanner
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiThinkingSkeleton
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk

/** The quick actions offered on selected text, with the instruction each sends. */
internal val AI_EDIT_ACTIONS = listOf(
    "Rewrite" to "Rewrite this to read more clearly. Keep the meaning and the writer's voice.",
    "Shorten" to "Make this shorter. Keep every important point.",
    "Expand" to "Expand this with more detail and explanation, in the same voice.",
    "Make professional" to "Rewrite this in a clear, confident, professional tone.",
    "Fix grammar" to "Fix spelling, grammar and punctuation only. Change nothing else.",
    "Turn into bullets" to "Turn this into a concise bulleted list, one idea per item.",
    "Key takeaways" to "Pull out the 3 to 5 most important takeaways as concise bullet points.",
    "Extract action items" to "Pull out every task, commitment or next step as a Markdown checklist ('- [ ] ...'). Only include items stated in the text.",
    "Turn into table" to "Organise this information into a clean GitHub-flavoured Markdown table. Keep every detail.",
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
    onCancel: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var custom by remember { mutableStateOf("") }
    var applying by remember(edit.result) { mutableStateOf(false) }

    fun submitCustom() {
        val trimmed = custom.trim()
        if (trimmed.isNotEmpty() && !edit.busy) onRun("Custom", trimmed)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = SurfaceBase) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .animateContentSize()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(com.craftflowtechnologies.meetingmind.ui.icons.AiMark, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Edit with AI", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            }
            Text(
                edit.original, fontSize = 13.sp, lineHeight = 19.sp, color = InkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceSunk).padding(12.dp)
            )

            when {
                edit.busy -> {
                    AiThinkingSkeleton(
                        label = "${edit.action ?: "Editing"}…",
                        modifier = Modifier.padding(top = 14.dp),
                        onStop = onCancel
                    )
                }
                edit.result != null -> {
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(edit.action ?: "Result", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.SemiBold)
                            edit.provider?.let { Text("by $it", fontSize = 12.sp, color = InkMuted) }
                            Spacer(Modifier.weight(1f))
                            AiCopyButton(text = edit.result)
                        }
                        Surface(shape = RoundedCornerShape(14.dp), color = AccentWash, modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
                                MarkdownTextPreview(edit.result)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                onClick = { if (!applying) { applying = true; onInsertBelow() } },
                                enabled = !applying,
                                shape = RoundedCornerShape(50),
                                color = SurfaceBase,
                                border = BorderStroke(1.dp, Line),
                                modifier = Modifier.weight(1f).heightIn(min = MMSize.minTouch).semantics { role = Role.Button }
                            ) {
                                Text("Insert below", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (applying) InkMuted else Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 13.dp))
                            }
                            Surface(
                                onClick = { if (!applying) { applying = true; onReplace() } },
                                enabled = !applying,
                                shape = RoundedCornerShape(50),
                                color = if (applying) InkMuted else Ink,
                                modifier = Modifier.weight(1f).heightIn(min = MMSize.minTouch).semantics { role = Role.Button }
                            ) {
                                Text("Replace", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnInk, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 13.dp))
                            }
                        }
                        Text("Try something else", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                    }
                }
                else -> Spacer(Modifier.size(16.dp))
            }

            edit.error?.let { err ->
                AiRecoveryBanner(
                    message = err,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    onRetry = if (edit.action != null && edit.instruction.isNotBlank()) {
                        { onRun(edit.action, edit.instruction) }
                    } else null
                )
            }

            if (!edit.busy) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AI_EDIT_ACTIONS.forEach { (label, instruction) ->
                        val selected = edit.action == label
                        Surface(
                            onClick = { onRun(label, instruction) },
                            shape = RoundedCornerShape(50),
                            color = if (selected) AccentWash else SurfaceSunk,
                            border = BorderStroke(1.dp, if (selected) Accent else Line),
                            modifier = Modifier.semantics { role = Role.Button }
                        ) {
                            Text(label, fontSize = 13.sp, color = if (selected) Accent else Ink, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Or say what to do… e.g. make it warmer") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitCustom() }),
                        trailingIcon = if (custom.isNotEmpty()) {
                            {
                                IconButton(onClick = { custom = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear instruction", tint = InkMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        } else null
                    )
                    IconButton(onClick = ::submitCustom, enabled = custom.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run", tint = if (custom.isNotBlank()) Accent else InkMuted)
                    }
                }
            }
        }
    }
}
