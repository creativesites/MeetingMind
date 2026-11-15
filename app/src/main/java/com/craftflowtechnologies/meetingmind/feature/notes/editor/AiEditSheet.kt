package com.craftflowtechnologies.meetingmind.feature.notes.editor

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiCopyButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiRecoveryBanner
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiThinkingSkeleton
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

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
    onStageInline: () -> Unit = {},
    onInsertBelow: () -> Unit,
    onCancel: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var custom by remember { mutableStateOf("") }
    var applying by remember(edit.result) { mutableStateOf(false) }
    val c = MM.colors

    fun submitCustom() {
        val trimmed = custom.trim()
        if (trimmed.isNotEmpty() && !edit.busy) onRun("Custom", trimmed)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .animateContentSize()
                .padding(horizontal = MM.space.xl)
                .padding(bottom = MM.space.xl)
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = c.accentWash,
                    border = BorderStroke(MMSize.hairline, c.lineSoft),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            com.craftflowtechnologies.meetingmind.ui.icons.AiMark,
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(MMSize.iconSmall)
                        )
                    }
                }
                Spacer(Modifier.width(MM.space.s))
                Text(
                    "Edit with AI",
                    style = MM.type.title,
                    color = c.ink,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    onClick = onDismiss,
                    shape = MM.radius.pill,
                    color = c.surfaceSunk,
                    border = BorderStroke(MMSize.hairline, c.lineSoft),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = c.inkMuted,
                            modifier = Modifier.size(MMSize.iconSmall)
                        )
                    }
                }
            }

            Surface(
                shape = MM.radius.card,
                color = c.surfaceSunk,
                border = BorderStroke(MMSize.hairline, c.lineSoft),
                modifier = Modifier
                    .padding(top = MM.space.m)
                    .fillMaxWidth()
            ) {
                Text(
                    edit.original,
                    style = MM.type.secondary,
                    color = c.inkMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(MM.space.m)
                )
            }

            when {
                edit.busy && !edit.partial.isNullOrBlank() -> {
                    Column(Modifier.padding(top = MM.space.m).semantics { liveRegion = LiveRegionMode.Polite }) {
                        Row(Modifier.fillMaxWidth().padding(bottom = MM.space.xs), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${edit.action ?: "Editing"}…",
                                style = MM.type.caption,
                                color = c.accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = MMSize.minTouch)) {
                                Text("Stop", style = MM.type.caption, color = c.inkMuted)
                            }
                        }
                        Surface(
                            shape = MM.radius.card,
                            color = c.surfaceRaised,
                            border = BorderStroke(MMSize.hairline, c.lineSoft),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                        ) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(MM.space.m)) {
                                MarkdownTextPreview(edit.partial)
                            }
                        }
                    }
                }
                edit.busy -> {
                    AiThinkingSkeleton(
                        label = "${edit.action ?: "Editing"}…",
                        modifier = Modifier.padding(top = MM.space.m),
                        onStop = onCancel
                    )
                }
                edit.result != null -> {
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = MM.space.m, bottom = MM.space.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                        ) {
                            Text(edit.action ?: "Result", style = MM.type.caption, color = c.accent, fontWeight = FontWeight.SemiBold)
                            edit.provider?.let { Text("by $it", style = MM.type.overline, color = c.inkMuted) }
                            Spacer(Modifier.weight(1f))
                            AiCopyButton(text = edit.result)
                        }
                        Surface(
                            shape = MM.radius.card,
                            color = c.surfaceRaised,
                            border = BorderStroke(MMSize.hairline, c.lineSoft),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                        ) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(MM.space.m)) {
                                MarkdownTextPreview(edit.result)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = MM.space.m),
                            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
                        ) {
                            Surface(
                                onClick = { if (!applying) { applying = true; onInsertBelow() } },
                                enabled = !applying,
                                shape = MM.radius.pill,
                                color = c.surface,
                                border = BorderStroke(MMSize.hairline, c.line),
                                modifier = Modifier.weight(1f).heightIn(min = MMSize.minTouch).semantics { role = Role.Button }
                            ) {
                                Text(
                                    "Below",
                                    style = MM.type.bodyStrong,
                                    color = if (applying) c.inkMuted else c.ink,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = MM.space.m)
                                )
                            }
                            Surface(
                                onClick = { if (!applying) { applying = true; onStageInline() } },
                                enabled = !applying,
                                shape = MM.radius.pill,
                                color = c.accentWash,
                                border = BorderStroke(MMSize.hairline, c.accent.copy(alpha = 0.35f)),
                                modifier = Modifier.weight(1.1f).heightIn(min = MMSize.minTouch).semantics { role = Role.Button }
                            ) {
                                Text(
                                    "Review inline",
                                    style = MM.type.bodyStrong,
                                    color = if (applying) c.inkMuted else c.accent,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = MM.space.m)
                                )
                            }
                            Surface(
                                onClick = { if (!applying) { applying = true; onReplace() } },
                                enabled = !applying,
                                shape = MM.radius.pill,
                                color = if (applying) c.track else c.ink,
                                modifier = Modifier.weight(1f).heightIn(min = MMSize.minTouch).semantics { role = Role.Button }
                            ) {
                                Text(
                                    "Replace",
                                    style = MM.type.bodyStrong,
                                    color = if (applying) c.inkMuted else c.onInk,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = MM.space.m)
                                )
                            }
                        }
                        Text(
                            "Try something else",
                            style = MM.type.caption,
                            color = c.inkMuted,
                            modifier = Modifier.padding(top = MM.space.l, bottom = MM.space.s)
                        )
                    }
                }
                else -> Spacer(Modifier.size(MM.space.m))
            }

            edit.error?.let { err ->
                AiRecoveryBanner(
                    message = err,
                    modifier = Modifier.padding(top = MM.space.m, bottom = MM.space.s),
                    onRetry = if (edit.action != null && edit.instruction.isNotBlank()) {
                        { onRun(edit.action, edit.instruction) }
                    } else null
                )
            }

            if (!edit.busy) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                    verticalArrangement = Arrangement.spacedBy(MM.space.s)
                ) {
                    AI_EDIT_ACTIONS.forEach { (label, instruction) ->
                        val selected = edit.action == label
                        Surface(
                            onClick = { onRun(label, instruction) },
                            shape = MM.radius.pill,
                            color = if (selected) c.accentWash else c.surfaceSunk,
                            border = BorderStroke(MMSize.hairline, if (selected) c.accent else c.lineSoft),
                            modifier = Modifier.semantics { role = Role.Button }
                        ) {
                            Text(
                                label,
                                style = MM.type.caption,
                                color = if (selected) c.accent else c.ink,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s)
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = MM.space.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        textStyle = MM.type.body,
                        shape = MM.radius.pill,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Or say what to do… e.g. make it warmer", style = MM.type.body, color = c.inkMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitCustom() }),
                        trailingIcon = if (custom.isNotEmpty()) {
                            {
                                IconButton(onClick = { custom = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear instruction", tint = c.inkMuted, modifier = Modifier.size(MMSize.iconSmall))
                                }
                            }
                        } else null
                    )
                    IconButton(
                        onClick = ::submitCustom,
                        enabled = custom.isNotBlank(),
                        modifier = Modifier.padding(start = MM.space.xs)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Run",
                            tint = if (custom.isNotBlank()) c.accent else c.inkMuted,
                            modifier = Modifier.size(MMSize.iconSmall)
                        )
                    }
                }
            }
        }
    }
}
