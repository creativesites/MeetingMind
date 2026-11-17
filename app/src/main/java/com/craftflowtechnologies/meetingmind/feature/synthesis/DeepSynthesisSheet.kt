package com.craftflowtechnologies.meetingmind.feature.synthesis

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import com.craftflowtechnologies.meetingmind.ai.common.AiResult
import com.craftflowtechnologies.meetingmind.ai.common.describeFailure
import com.craftflowtechnologies.meetingmind.ai.synthesis.CommitmentDriftItem
import com.craftflowtechnologies.meetingmind.ai.synthesis.CrossSessionInsight
import com.craftflowtechnologies.meetingmind.ai.synthesis.DeepSynthesisDossier
import com.craftflowtechnologies.meetingmind.ai.synthesis.DeepSynthesisMode
import com.craftflowtechnologies.meetingmind.ai.synthesis.DeepSynthesisPreview
import com.craftflowtechnologies.meetingmind.ai.synthesis.DeepSynthesisSource
import com.craftflowtechnologies.meetingmind.ai.synthesis.DeepSynthesisWorkspace
import com.craftflowtechnologies.meetingmind.ai.synthesis.GatheredDeepContext
import com.craftflowtechnologies.meetingmind.core.billing.PaywallTrigger
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

sealed interface DeepSynthesisTarget {
    data class Meeting(val meetingId: String) : DeepSynthesisTarget
    data class Note(val noteId: String) : DeepSynthesisTarget
    data class Notebook(val notebookId: String) : DeepSynthesisTarget
}

private sealed interface DeepSynthesisUiState {
    data object Gathering : DeepSynthesisUiState
    data class RequiresPro(val context: GatheredDeepContext) : DeepSynthesisUiState
    data class Generating(val context: GatheredDeepContext, val mode: DeepSynthesisMode) : DeepSynthesisUiState
    data class Ready(val context: GatheredDeepContext, val dossier: DeepSynthesisDossier) : DeepSynthesisUiState
    data class Error(val message: String, val gathered: GatheredDeepContext?, val mode: DeepSynthesisMode) : DeepSynthesisUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepSynthesisSheet(
    target: DeepSynthesisTarget,
    onDismiss: () -> Unit,
    onOpenPaywall: (PaywallTrigger) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenMeeting: (meetingId: String, startMs: Long?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MM.colors

    var selectedMode by remember(target) { mutableStateOf<DeepSynthesisMode?>(null) }
    var uiState by remember(target) { mutableStateOf<DeepSynthesisUiState>(DeepSynthesisUiState.Gathering) }
    var savingNote by remember { mutableStateOf(false) }
    var savedNoteId by remember { mutableStateOf<String?>(null) }
    var savingTasks by remember { mutableStateOf(false) }
    var addedTasksCount by remember { mutableStateOf<Int?>(null) }

    suspend fun executeRun(gathered: GatheredDeepContext, mode: DeepSynthesisMode) {
        selectedMode = mode
        uiState = DeepSynthesisUiState.Generating(gathered, mode)
        // The model can throw (a local engine error, out of memory, a database read): show it, don't crash.
        val result = try {
            DeepSynthesisWorkspace.runSynthesis(context, gathered, mode)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            AiResult.Failed("Deep Synthesis stopped before finishing. Try again.", t)
        }
        when (result) {
            is AiResult.Success -> {
                savedNoteId = null
                addedTasksCount = null
                uiState = DeepSynthesisUiState.Ready(gathered, result.value)
            }
            else -> {
                uiState = DeepSynthesisUiState.Error(
                    message = result.describeFailure() ?: "Deep Synthesis could not complete right now.",
                    gathered = gathered,
                    mode = mode
                )
            }
        }
    }

    LaunchedEffect(target) {
        uiState = DeepSynthesisUiState.Gathering
        val gathered = try {
            when (target) {
                is DeepSynthesisTarget.Meeting -> DeepSynthesisWorkspace.gatherForMeeting(context, target.meetingId)
                is DeepSynthesisTarget.Note -> DeepSynthesisWorkspace.gatherForNote(context, target.noteId)
                is DeepSynthesisTarget.Notebook -> DeepSynthesisWorkspace.gatherForNotebook(context, target.notebookId)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            uiState = DeepSynthesisUiState.Error("Deep Synthesis couldn't read this content right now. Try again.", null, DeepSynthesisMode.EXECUTIVE_PACK)
            return@LaunchedEffect
        }
        if (gathered == null) {
            uiState = DeepSynthesisUiState.Error(
                "Not enough recorded or written content here yet to run Deep Synthesis.",
                null,
                DeepSynthesisMode.EXECUTIVE_PACK
            )
            return@LaunchedEffect
        }
        val initialMode = gathered.preview.suggestedMode
        selectedMode = initialMode
        if (!DeepSynthesisWorkspace.hasPaidAccess(context)) {
            uiState = DeepSynthesisUiState.RequiresPro(gathered)
        } else {
            executeRun(gathered, initialMode)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MM.radius.sheet,
        containerColor = c.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .animateContentSize()
                .testTag("deep_synthesis_sheet")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MM.space.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = AiMark,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(MMSize.icon)
                    )
                    Column {
                        Text("Deep Synthesis Pack", style = MM.type.title, color = c.ink)
                        Text(
                            "Cross-session patterns, commitment drift & ready brief",
                            style = MM.type.caption,
                            color = c.inkSecondary
                        )
                    }
                }
                Surface(
                    shape = MM.radius.small,
                    color = c.accentWash,
                    border = BorderStroke(MMSize.hairline, c.lineSoft)
                ) {
                    Text(
                        "PRO",
                        style = MM.type.overline,
                        color = c.accent,
                        modifier = Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs)
                    )
                }
            }

            Spacer(Modifier.height(MM.space.m))
            HorizontalDivider(color = c.lineSoft)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MM.space.xl, vertical = MM.space.l)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(MM.space.m)
            ) {
                when (val s = uiState) {
                    is DeepSynthesisUiState.Gathering -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MM.space.m),
                            modifier = Modifier.padding(vertical = MM.space.xl)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(MMSize.icon),
                                color = c.accent,
                                strokeWidth = MM.space.xs
                            )
                            Text(
                                "Scanning related recordings, notes & open commitments…",
                                style = MM.type.body,
                                color = c.inkSecondary
                            )
                        }
                    }

                    is DeepSynthesisUiState.RequiresPro -> {
                        FreePreviewUpgradeCard(
                            preview = s.context.preview,
                            onUpgrade = {
                                onDismiss()
                                onOpenPaywall(PaywallTrigger.PREMIUM_ACTION)
                            }
                        )
                    }

                    is DeepSynthesisUiState.Generating -> {
                        ModeSelectorRow(
                            selected = s.mode,
                            enabled = false,
                            onSelect = {}
                        )
                        ScopeSummaryLine(s.context.preview)
                        MMCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MM.space.m)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(MMSize.iconSmall),
                                    color = c.accent
                                )
                                Column {
                                    Text(
                                        "Synthesizing ${s.context.preview.relatedSessionCount} related sessions…",
                                        style = MM.type.bodyStrong,
                                        color = c.ink
                                    )
                                    Text(
                                        s.mode.tagline,
                                        style = MM.type.caption,
                                        color = c.inkSecondary
                                    )
                                }
                            }
                        }
                    }

                    is DeepSynthesisUiState.Error -> {
                        s.gathered?.let { ScopeSummaryLine(it.preview) }
                        MMCard {
                            Text(s.message, style = MM.type.body, color = c.danger)
                            Spacer(Modifier.height(MM.space.m))
                            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                                s.gathered?.let { gathered ->
                                    PrimaryButton(
                                        text = "Try again",
                                        leadingIcon = Icons.Filled.Refresh,
                                        onClick = { scope.launch { executeRun(gathered, s.mode) } }
                                    )
                                }
                                SecondaryButton(text = "Close", onClick = onDismiss)
                            }
                        }
                    }

                    is DeepSynthesisUiState.Ready -> {
                        val dossier = s.dossier
                        ModeSelectorRow(
                            selected = dossier.mode,
                            enabled = true,
                            onSelect = { nextMode ->
                                if (nextMode != dossier.mode) {
                                    scope.launch { executeRun(s.context, nextMode) }
                                }
                            }
                        )
                        ScopeSummaryLine(s.context.preview, engineLabel = dossier.engineLabel)

                        HeroCard {
                            Text(dossier.headline, style = MM.type.heading, color = c.ink)
                            Spacer(Modifier.height(MM.space.xs))
                            Text(dossier.executiveSummary, style = MM.type.body, color = c.inkSecondary)
                        }

                        if (dossier.evolution.isNotEmpty()) {
                            SectionHeader("Evolution across sessions")
                            dossier.evolution.forEach { insight ->
                                InsightCard(
                                    insight = insight,
                                    sourcesById = dossier.sourcesById,
                                    onOpenSource = { src ->
                                        onDismiss()
                                        when {
                                            src.noteId != null -> onOpenNote(src.noteId)
                                            src.meetingId != null -> onOpenMeeting(src.meetingId, src.startMs)
                                        }
                                    }
                                )
                            }
                        }

                        if (dossier.commitmentDrift.isNotEmpty()) {
                            SectionHeader("Commitments & drift")
                            dossier.commitmentDrift.forEach { item ->
                                DriftCard(
                                    item = item,
                                    sourcesById = dossier.sourcesById,
                                    onOpenSource = { src ->
                                        onDismiss()
                                        when {
                                            src.noteId != null -> onOpenNote(src.noteId)
                                            src.meetingId != null -> onOpenMeeting(src.meetingId, src.startMs)
                                        }
                                    }
                                )
                            }
                        }

                        if (dossier.blindSpots.isNotEmpty()) {
                            SectionHeader("Blind spots & open loops")
                            dossier.blindSpots.forEach { insight ->
                                InsightCard(
                                    insight = insight,
                                    sourcesById = dossier.sourcesById,
                                    onOpenSource = { src ->
                                        onDismiss()
                                        when {
                                            src.noteId != null -> onOpenNote(src.noteId)
                                            src.meetingId != null -> onOpenMeeting(src.meetingId, src.startMs)
                                        }
                                    }
                                )
                            }
                        }

                        SectionHeader(dossier.deliverableTitle)
                        Surface(
                            shape = MM.radius.card,
                            color = c.surfaceSunk,
                            border = BorderStroke(MMSize.hairline, c.lineSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(MM.space.l)) {
                                MarkdownTextPreview(dossier.deliverableMarkdown)
                                Spacer(Modifier.height(MM.space.m))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    SecondaryButton(
                                        text = "Copy brief",
                                        leadingIcon = Icons.Filled.ContentCopy,
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(
                                                ClipData.newPlainText(dossier.deliverableTitle, dossier.deliverableMarkdown)
                                            )
                                            Toast.makeText(context, "Brief copied", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(MM.space.xs))

                        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            PrimaryButton(
                                text = when {
                                    savedNoteId != null -> "Open saved dossier note"
                                    savingNote -> "Saving note…"
                                    else -> "Save Pack as Note"
                                },
                                leadingIcon = Icons.Filled.NoteAdd,
                                enabled = !savingNote,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    val existingId = savedNoteId
                                    if (existingId != null) {
                                        onDismiss()
                                        onOpenNote(existingId)
                                    } else {
                                        savingNote = true
                                        scope.launch {
                                            val newId = try {
                                                DeepSynthesisWorkspace.saveAsNote(
                                                    context = context,
                                                    dossier = dossier,
                                                    workflow = s.context.workflow,
                                                    notebookId = s.context.targetNotebookId
                                                )
                                            } catch (e: kotlinx.coroutines.CancellationException) {
                                                throw e
                                            } catch (t: Throwable) {
                                                null
                                            }
                                            savingNote = false
                                            savedNoteId = newId
                                            Toast.makeText(context, if (newId != null) "Saved to your notebook" else "Couldn't save the note. Try again.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )

                            if (dossier.commitmentDrift.isNotEmpty()) {
                                SecondaryButton(
                                    text = when {
                                        addedTasksCount != null -> "Added $addedTasksCount task${if (addedTasksCount == 1) "" else "s"} to Tasks"
                                        savingTasks -> "Adding commitments…"
                                        else -> "Add ${dossier.commitmentDrift.size} commitment${if (dossier.commitmentDrift.size == 1) "" else "s"} to Tasks"
                                    },
                                    leadingIcon = Icons.Filled.AddTask,
                                    enabled = !savingTasks && addedTasksCount == null,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        savingTasks = true
                                        scope.launch {
                                            val count = try {
                                                DeepSynthesisWorkspace.addCommitmentsAsTasks(context, dossier)
                                            } catch (e: kotlinx.coroutines.CancellationException) {
                                                throw e
                                            } catch (t: Throwable) {
                                                null
                                            }
                                            savingTasks = false
                                            addedTasksCount = count
                                            val msg = when (count) {
                                                null -> "Couldn't add them to Tasks. Try again."
                                                0 -> "All commitments already in Tasks"
                                                else -> "Added $count to Tasks"
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSelectorRow(
    selected: DeepSynthesisMode,
    enabled: Boolean,
    onSelect: (DeepSynthesisMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        DeepSynthesisMode.entries.forEach { mode ->
            MMChip(
                label = mode.label,
                selected = mode == selected,
                enabled = enabled,
                onClick = { onSelect(mode) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScopeSummaryLine(preview: DeepSynthesisPreview, engineLabel: String? = null) {
    val c = MM.colors
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
        Text(
            buildString {
                append("${preview.relatedSessionCount} related session${if (preview.relatedSessionCount == 1) "" else "s"}")
                append(" · ${preview.openCommitmentCount} open commitment${if (preview.openCommitmentCount == 1) "" else "s"}")
                if (preview.isConfidential) append(" · On-device privacy")
                if (!engineLabel.isNullOrBlank()) append(" · $engineLabel")
            },
            style = MM.type.caption,
            color = c.inkMuted
        )
        if (preview.recurringThemes.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MM.space.xs),
                verticalArrangement = Arrangement.spacedBy(MM.space.xs)
            ) {
                preview.recurringThemes.forEach { theme ->
                    Surface(shape = MM.radius.small, color = c.surfaceSunk) {
                        Text(
                            theme,
                            style = MM.type.caption,
                            color = c.inkSecondary,
                            modifier = Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FreePreviewUpgradeCard(
    preview: DeepSynthesisPreview,
    onUpgrade: () -> Unit
) {
    val c = MM.colors
    HeroCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            Icon(Icons.Filled.Insights, contentDescription = null, tint = c.accent, modifier = Modifier.size(MMSize.icon))
            Text(
                "Detected ${preview.relatedSessionCount} connected session${if (preview.relatedSessionCount == 1) "" else "s"} around \"${preview.anchorTitle}\"",
                style = MM.type.heading,
                color = c.ink
            )
        }
        Spacer(Modifier.height(MM.space.s))
        Text(
            buildString {
                append("Cross-Session Deep Synthesis connects ${preview.relatedSessionCount} recordings & notes")
                if (preview.openCommitmentCount > 0) {
                    append(" and ${preview.openCommitmentCount} open commitments")
                }
                if (preview.recurringThemes.isNotEmpty()) {
                    append(" around ${preview.recurringThemes.joinToString(", ")}")
                }
                append(" to surface shifted decisions, stalled promises, contradictions, and a ready-to-send brief.")
            },
            style = MM.type.body,
            color = c.inkSecondary
        )
        Spacer(Modifier.height(MM.space.m))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = c.accent, modifier = Modifier.size(MMSize.iconSmall))
            Text(
                "Included with MeetingMind Pro, BYOK & Church plans (works online & offline).",
                style = MM.type.caption,
                color = c.accent
            )
        }
        Spacer(Modifier.height(MM.space.l))
        PrimaryButton(
            text = "Unlock Deep Synthesis",
            modifier = Modifier.fillMaxWidth(),
            onClick = onUpgrade
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MM.type.overline,
        color = MM.colors.inkMuted,
        modifier = Modifier.padding(top = MM.space.xs)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsightCard(
    insight: CrossSessionInsight,
    sourcesById: Map<String, DeepSynthesisSource>,
    onOpenSource: (DeepSynthesisSource) -> Unit
) {
    val c = MM.colors
    var hiddenAfterReport by remember(insight) { mutableStateOf(false) }
    val refs = insight.sourceIds.mapNotNull { sourcesById[it] }
    if (hiddenAfterReport) {
        com.craftflowtechnologies.meetingmind.core.aireport.HiddenAfterReportLine(onShow = { hiddenAfterReport = false })
        return
    }
    MMCard {
        Row(verticalAlignment = Alignment.Top) {
            Text(insight.heading, style = MM.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
            com.craftflowtechnologies.meetingmind.core.aireport.ReportAiAction(
                source = {
                    com.craftflowtechnologies.meetingmind.core.aireport.AiReportSource(
                        com.craftflowtechnologies.meetingmind.core.aireport.AiFeature.DEEP_SYNTHESIS,
                        "${insight.heading}\n${insight.detail}",
                        moreNoteIds = refs.mapNotNull { it.noteId },
                        moreMeetingIds = refs.mapNotNull { it.meetingId }
                    )
                },
                onHide = { hiddenAfterReport = true }
            )
        }
        Spacer(Modifier.height(MM.space.xs))
        Text(insight.detail, style = MM.type.body, color = c.inkSecondary)
        if (refs.isNotEmpty()) {
            Spacer(Modifier.height(MM.space.s))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                refs.forEach { src ->
                    SourceCitationPill(src) { onOpenSource(src) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriftCard(
    item: CommitmentDriftItem,
    sourcesById: Map<String, DeepSynthesisSource>,
    onOpenSource: (DeepSynthesisSource) -> Unit
) {
    val c = MM.colors
    MMCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(item.commitment, style = MM.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(MM.space.s))
            Surface(shape = MM.radius.small, color = c.accentWash) {
                Text(
                    item.statusLabel,
                    style = MM.type.caption,
                    color = c.accent,
                    modifier = Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs)
                )
            }
        }
        if (!item.owner.isNullOrBlank() || item.evidence.isNotBlank()) {
            Spacer(Modifier.height(MM.space.xs))
            Text(
                listOfNotNull(item.owner?.let { "Owner: $it" }, item.evidence.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MM.type.secondary,
                color = c.inkSecondary
            )
        }
        val refs = item.sourceIds.mapNotNull { sourcesById[it] }
        if (refs.isNotEmpty()) {
            Spacer(Modifier.height(MM.space.s))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                refs.forEach { src ->
                    SourceCitationPill(src) { onOpenSource(src) }
                }
            }
        }
    }
}

@Composable
private fun SourceCitationPill(source: DeepSynthesisSource, onClick: () -> Unit) {
    val c = MM.colors
    Surface(
        onClick = onClick,
        shape = MM.radius.small,
        color = c.surfaceSunk,
        border = BorderStroke(MMSize.hairline, c.lineSoft),
        modifier = Modifier.heightIn(min = MMSize.minTouch)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "${source.title} · ${source.dateLabel}",
                style = MM.type.caption,
                color = c.accent,
                modifier = Modifier.padding(horizontal = MM.space.m, vertical = MM.space.s)
            )
        }
    }
}
