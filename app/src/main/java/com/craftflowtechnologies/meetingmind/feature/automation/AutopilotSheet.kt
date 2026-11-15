package com.craftflowtechnologies.meetingmind.feature.automation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.craftflowtechnologies.meetingmind.ai.automation.AutopilotContext
import com.craftflowtechnologies.meetingmind.ai.automation.AutopilotPreferences
import com.craftflowtechnologies.meetingmind.ai.automation.AutopilotRecipe
import com.craftflowtechnologies.meetingmind.ai.automation.AutopilotRunResult
import com.craftflowtechnologies.meetingmind.ai.automation.AutopilotWorkspace
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.core.billing.PaywallTrigger
import com.craftflowtechnologies.meetingmind.core.ui.mm.AiCopyButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.feature.notes.editor.MarkdownTextPreview
import com.craftflowtechnologies.meetingmind.ui.icons.AiMark
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.launch

sealed interface AutopilotTarget {
    data class Meeting(val meetingId: String) : AutopilotTarget
    data class Note(val noteId: String) : AutopilotTarget
    data object LatestSession : AutopilotTarget
}

private sealed interface AutopilotUiState {
    data object Loading : AutopilotUiState
    data class NoSession(val message: String) : AutopilotUiState
    data class ReadyToConfigure(val gathered: AutopilotContext) : AutopilotUiState
    data class Running(val gathered: AutopilotContext, val recipe: AutopilotRecipe) : AutopilotUiState
    data class Completed(val gathered: AutopilotContext, val result: AutopilotRunResult) : AutopilotUiState
    data class Failed(val gathered: AutopilotContext, val recipe: AutopilotRecipe, val message: String) : AutopilotUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutopilotSheet(
    target: AutopilotTarget,
    onDismiss: () -> Unit,
    onOpenPaywall: (PaywallTrigger) -> Unit,
    onOpenNote: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MM.colors

    var uiState by remember { mutableStateOf<AutopilotUiState>(AutopilotUiState.Loading) }
    var selectedRecipe by remember { mutableStateOf(AutopilotRecipe.EXECUTIVE_DEBRIEF) }
    var autoRunEnabled by remember { mutableStateOf(AutopilotPreferences.isAutoRunEnabled(context)) }
    var autoSaveNote by remember { mutableStateOf(AutopilotPreferences.shouldAutoSaveNote(context)) }
    var autoCreateTasks by remember { mutableStateOf(AutopilotPreferences.shouldAutoCreateTasks(context)) }
    var tasksCommitted by remember { mutableStateOf<Int?>(null) }
    var savingNote by remember { mutableStateOf(false) }

    LaunchedEffect(target) {
        uiState = AutopilotUiState.Loading
        val gathered = when (target) {
            is AutopilotTarget.Meeting -> AutopilotWorkspace.gatherForMeeting(context, target.meetingId)
            is AutopilotTarget.Note -> AutopilotWorkspace.gatherForNote(context, target.noteId)
            AutopilotTarget.LatestSession -> AutopilotWorkspace.gatherLatestSession(context)
        }
        if (gathered == null || gathered.sourceText.isBlank()) {
            uiState = AutopilotUiState.NoSession(
                "Record a meeting or write a note first — Autopilot will extract outcomes, tasks, and follow-up drafts automatically."
            )
            return@LaunchedEffect
        }
        selectedRecipe = gathered.preview.recommendedRecipe
        uiState = AutopilotUiState.ReadyToConfigure(gathered)
    }

    fun triggerRun(gathered: AutopilotContext, recipe: AutopilotRecipe) {
        if (!gathered.unlocked) {
            onOpenPaywall(PaywallTrigger.PREMIUM_ACTION)
            return
        }
        tasksCommitted = null
        uiState = AutopilotUiState.Running(gathered, recipe)
        scope.launch {
            uiState = when (val res = AutopilotWorkspace.runRecipe(context, gathered, recipe)) {
                is AiResult.Success -> AutopilotUiState.Completed(gathered, res.value)
                is AiResult.Failed -> AutopilotUiState.Failed(gathered, recipe, res.message)
                is AiResult.ModelUnavailable -> AutopilotUiState.Failed(gathered, recipe, res.message)
                is AiResult.DeviceUnsupported -> AutopilotUiState.Failed(gathered, recipe, res.describeFailure() ?: res.reason)
                is AiResult.InsufficientMemory -> AutopilotUiState.Failed(gathered, recipe, res.describeFailure() ?: "Insufficient memory")
                is AiResult.InsufficientStorage -> AutopilotUiState.Failed(gathered, recipe, res.describeFailure() ?: "Insufficient storage")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MM.space.xl)
                .padding(bottom = MM.space.xl)
                .verticalScroll(rememberScrollState())
                .animateContentSize()
                .testTag("autopilot_sheet"),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Icon(AiMark, contentDescription = null, tint = c.accent, modifier = Modifier.size(MMSize.icon))
                Column(Modifier.weight(1f)) {
                    Text("Autopilot Workflows", style = MM.type.title, color = c.ink)
                    Text(
                        "One-tap recipes & post-recording automation",
                        style = MM.type.caption,
                        color = c.inkSecondary
                    )
                }
            }

            when (val state = uiState) {
                AutopilotUiState.Loading -> {
                    MMCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MM.space.m)
                        ) {
                            CircularProgressIndicator(
                                color = c.accent,
                                strokeWidth = MM.space.xs / 2,
                                modifier = Modifier.size(MMSize.icon)
                            )
                            Text("Inspecting session details…", style = MM.type.body, color = c.inkSecondary)
                        }
                    }
                }

                is AutopilotUiState.NoSession -> {
                    MMCard {
                        Text(state.message, style = MM.type.body, color = c.inkSecondary)
                    }
                    SecondaryButton("Close", onDismiss, modifier = Modifier.fillMaxWidth())
                }

                is AutopilotUiState.ReadyToConfigure -> {
                    val preview = state.gathered.preview
                    SessionPreviewBanner(
                        title = preview.sessionTitle,
                        words = preview.wordCount,
                        speakers = preview.speakerCount,
                        openTasks = preview.existingOpenTasks,
                        stayOnDeviceOnly = preview.stayOnDeviceOnly
                    )

                    RecipeSelectorRow(
                        recommended = preview.recommendedRecipe,
                        selected = selectedRecipe,
                        onSelect = { selectedRecipe = it }
                    )

                    MMCard {
                        Text(selectedRecipe.label, style = MM.type.heading, color = c.ink)
                        Spacer(Modifier.height(MM.space.xs))
                        Text(selectedRecipe.subtitle, style = MM.type.body, color = c.inkSecondary)
                        if (!state.gathered.unlocked) {
                            Spacer(Modifier.height(MM.space.m))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
                                Text(
                                    "Included with Pro & Church plans (online or offline local LLM)",
                                    style = MM.type.caption,
                                    color = c.accent
                                )
                            }
                        }
                    }

                    if (state.gathered.unlocked) {
                        AutomationTriggersCard(
                            autoRun = autoRunEnabled,
                            onAutoRunChange = {
                                autoRunEnabled = it
                                AutopilotPreferences.setAutoRunEnabled(context, it)
                            },
                            autoSaveNote = autoSaveNote,
                            onAutoSaveNoteChange = {
                                autoSaveNote = it
                                AutopilotPreferences.setAutoSaveNote(context, it)
                            },
                            autoCreateTasks = autoCreateTasks,
                            onAutoCreateTasksChange = {
                                autoCreateTasks = it
                                AutopilotPreferences.setAutoCreateTasks(context, it)
                            }
                        )
                        PrimaryButton(
                            text = "Run ${selectedRecipe.label}",
                            onClick = { triggerRun(state.gathered, selectedRecipe) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        PrimaryButton(
                            text = "Unlock Autopilot Workflows",
                            onClick = { onOpenPaywall(PaywallTrigger.PREMIUM_ACTION) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is AutopilotUiState.Running -> {
                    MMCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MM.space.m),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        ) {
                            CircularProgressIndicator(
                                color = c.accent,
                                strokeWidth = MM.space.xs / 2,
                                modifier = Modifier.size(MMSize.icon)
                            )
                            Column(Modifier.weight(1f)) {
                                Text("Running ${state.recipe.label}…", style = MM.type.bodyStrong, color = c.ink)
                                Text(
                                    if (state.gathered.preview.stayOnDeviceOnly) {
                                        "Processing privately on this device"
                                    } else {
                                        "Generating structured outcomes, task queue & follow-up draft"
                                    },
                                    style = MM.type.caption,
                                    color = c.inkSecondary
                                )
                            }
                        }
                    }
                }

                is AutopilotUiState.Failed -> {
                    MMCard {
                        Text(state.message, style = MM.type.body, color = c.danger)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        SecondaryButton(
                            text = "Configure",
                            onClick = { uiState = AutopilotUiState.ReadyToConfigure(state.gathered) },
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryButton(
                            text = "Retry",
                            onClick = { triggerRun(state.gathered, state.recipe) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is AutopilotUiState.Completed -> {
                    val res = state.result
                    val preview = state.gathered.preview
                    RecipeSelectorRow(
                        recommended = preview.recommendedRecipe,
                        selected = res.recipe,
                        onSelect = { recipe ->
                            selectedRecipe = recipe
                            triggerRun(state.gathered, recipe)
                        }
                    )

                    HeroCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
                            Text(res.recipe.badge, style = MM.type.caption, color = c.accent)
                            Spacer(Modifier.weight(1f))
                            Text(res.engineLabel, style = MM.type.caption, color = c.inkSecondary)
                        }
                        Spacer(Modifier.height(MM.space.xs))
                        Text(res.headline, style = MM.type.heading, color = c.ink)
                        Spacer(Modifier.height(MM.space.xs))
                        Text(res.summary, style = MM.type.body, color = c.inkSecondary)
                    }

                    if (res.keyOutcomes.isNotEmpty()) {
                        MMCard {
                            Text("Key Outcomes", style = MM.type.bodyStrong, color = c.ink)
                            Spacer(Modifier.height(MM.space.s))
                            res.keyOutcomes.forEach { line ->
                                Text("• $line", style = MM.type.body, color = c.inkSecondary)
                                Spacer(Modifier.height(MM.space.xs / 2))
                            }
                        }
                    }

                    if (res.actions.isNotEmpty()) {
                        MMCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Extracted Tasks (${res.actions.size})", style = MM.type.bodyStrong, color = c.ink)
                                Icon(Icons.Filled.AddTask, contentDescription = null, tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
                            }
                            Spacer(Modifier.height(MM.space.s))
                            res.actions.forEach { item ->
                                val meta = listOfNotNull(item.owner, item.dueHint).joinToString(" · ")
                                Column(Modifier.padding(bottom = MM.space.xs)) {
                                    Text("• ${item.title}", style = MM.type.body, color = c.ink)
                                    if (meta.isNotEmpty()) {
                                        Text("  $meta", style = MM.type.caption, color = c.inkSecondary)
                                    }
                                }
                            }
                            Spacer(Modifier.height(MM.space.s))
                            SecondaryButton(
                                text = when (val added = tasksCommitted) {
                                    null -> "Add ${res.actions.size} tasks to My Tasks"
                                    0 -> "Tasks already in My Tasks"
                                    else -> "Added $added tasks"
                                },
                                enabled = tasksCommitted == null,
                                onClick = {
                                    scope.launch {
                                        val count = AutopilotWorkspace.commitTasks(
                                            context = context,
                                            result = res,
                                            noteId = preview.noteId,
                                            meetingId = preview.meetingId
                                        )
                                        tasksCommitted = count
                                        Toast.makeText(
                                            context,
                                            if (count > 0) "Added $count tasks" else "All tasks were already tracked",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    MMCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(res.deliverableTitle, style = MM.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                            AiCopyButton(
                                text = res.deliverableMarkdown,
                                onCopied = {
                                    Toast.makeText(context, "Copied deliverable", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        Spacer(Modifier.height(MM.space.xs))
                        Box(Modifier.heightIn(max = MM.space.xxl * 8)) {
                            MarkdownTextPreview(res.deliverableMarkdown)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s), modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton(
                            text = "Share",
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_SUBJECT, res.deliverableTitle)
                                    .putExtra(Intent.EXTRA_TEXT, res.deliverableMarkdown)
                                context.startActivity(Intent.createChooser(intent, "Share workflow output"))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryButton(
                            text = if (savingNote) "Saving…" else "Save as Note",
                            enabled = !savingNote,
                            onClick = {
                                savingNote = true
                                scope.launch {
                                    val newId = AutopilotWorkspace.saveRecapAsNote(
                                        context = context,
                                        result = res,
                                        workflow = preview.workflow,
                                        notebookId = preview.notebookId
                                    )
                                    savingNote = false
                                    Toast.makeText(context, "Saved Autopilot note", Toast.LENGTH_SHORT).show()
                                    onOpenNote(newId)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionPreviewBanner(
    title: String,
    words: Int,
    speakers: Int,
    openTasks: Int,
    stayOnDeviceOnly: Boolean
) {
    val c = MM.colors
    Surface(
        shape = MM.radius.card,
        color = c.surfaceSunk,
        border = BorderStroke(MMSize.hairline, c.lineSoft)
    ) {
        Column(Modifier.fillMaxWidth().padding(MM.space.m)) {
            Text(title, style = MM.type.bodyStrong, color = c.ink)
            Spacer(Modifier.height(MM.space.xs / 2))
            val parts = buildList {
                add("$words words")
                if (speakers > 0) add("$speakers speakers")
                add("$openTasks open tasks")
                if (stayOnDeviceOnly) add("Private · On-device only")
            }
            Text(parts.joinToString(" · "), style = MM.type.caption, color = c.inkSecondary)
        }
    }
}

@Composable
private fun RecipeSelectorRow(
    recommended: AutopilotRecipe,
    selected: AutopilotRecipe,
    onSelect: (AutopilotRecipe) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        AutopilotRecipe.entries.forEach { recipe ->
            val label = if (recipe == recommended) "${recipe.label} ★" else recipe.label
            MMChip(
                label = label,
                selected = recipe == selected,
                onClick = { onSelect(recipe) }
            )
        }
    }
}

@Composable
private fun AutomationTriggersCard(
    autoRun: Boolean,
    onAutoRunChange: (Boolean) -> Unit,
    autoSaveNote: Boolean,
    onAutoSaveNoteChange: (Boolean) -> Unit,
    autoCreateTasks: Boolean,
    onAutoCreateTasksChange: (Boolean) -> Unit
) {
    val c = MM.colors
    MMCard {
        Text("Automatic Post-Recording Trigger", style = MM.type.bodyStrong, color = c.ink)
        Spacer(Modifier.height(MM.space.xs / 2))
        Text(
            "Automatically run the matching workflow whenever a recording finishes processing.",
            style = MM.type.caption,
            color = c.inkSecondary
        )
        Spacer(Modifier.height(MM.space.s))
        TriggerToggleRow("Auto-run after recordings finish", autoRun, onAutoRunChange)
        if (autoRun) {
            HorizontalDivider(color = c.lineSoft, modifier = Modifier.padding(vertical = MM.space.xs))
            TriggerToggleRow("Auto-save recap note", autoSaveNote, onAutoSaveNoteChange)
            TriggerToggleRow("Auto-create follow-up tasks", autoCreateTasks, onAutoCreateTasksChange)
        }
    }
}

@Composable
private fun TriggerToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val c = MM.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = MM.space.xs / 2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MM.type.body, color = c.ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(MM.space.s))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
