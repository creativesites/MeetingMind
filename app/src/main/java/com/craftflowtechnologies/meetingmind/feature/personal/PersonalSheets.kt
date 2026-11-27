package com.craftflowtechnologies.meetingmind.feature.personal

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.personal.CustomChallengeConfig
import com.craftflowtechnologies.meetingmind.core.personal.GrowthEnrollment
import com.craftflowtechnologies.meetingmind.core.personal.GrowthEnrollmentStatus
import com.craftflowtechnologies.meetingmind.core.personal.GrowthStepProgress
import com.craftflowtechnologies.meetingmind.core.personal.LifeChapter
import com.craftflowtechnologies.meetingmind.core.personal.PersonalGrowthCatalog
import com.craftflowtechnologies.meetingmind.core.personal.PersonalRoutine
import com.craftflowtechnologies.meetingmind.core.personal.PersonalSoundscape
import com.craftflowtechnologies.meetingmind.core.personal.RoutineStep
import com.craftflowtechnologies.meetingmind.core.personal.RoutineStepKind
import com.craftflowtechnologies.meetingmind.core.personal.RoutineTimeWindow
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.work.previewOf
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import kotlinx.coroutines.flow.Flow

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * Full Routine Editor sheet supporting both creating new routines and editing existing ones:
 * title, why, time window, anchor cue, days of week, opt-in reminder & time, ambient soundscape,
 * dynamic steps (add / edit / reorder / remove / kind), and archive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineEditorSheet(
    existing: PersonalRoutine?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        why: String,
        window: RoutineTimeWindow,
        cue: String,
        soundscape: PersonalSoundscape,
        steps: List<RoutineStep>,
        reminderEnabled: Boolean,
        scheduledMinuteOfDay: Int?,
        daysOfWeekMask: Int
    ) -> Unit,
    onArchive: ((PersonalRoutine) -> Unit)? = null
) {
    val c = MM.colors
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var why by remember(existing?.id) { mutableStateOf(existing?.whyDescription.orEmpty()) }
    var cue by remember(existing?.id) { mutableStateOf(existing?.anchorCue.orEmpty()) }
    var window by remember(existing?.id) { mutableStateOf(existing?.timeWindow ?: RoutineTimeWindow.MORNING) }
    var soundscape by remember(existing?.id) { mutableStateOf(existing?.soundscape ?: PersonalSoundscape.NONE) }
    var reminderEnabled by remember(existing?.id) { mutableStateOf(existing?.reminderEnabled ?: false) }
    var scheduledMinute by remember(existing?.id) {
        mutableIntStateOf(existing?.scheduledMinuteOfDay ?: window.defaultMinute)
    }
    var daysMask by remember(existing?.id) {
        mutableIntStateOf(existing?.daysOfWeekMask ?: PersonalRoutine.ALL_DAYS_MASK)
    }

    val steps = remember(existing?.id) {
        mutableStateListOf<RoutineStep>().apply {
            if (existing != null && existing.steps.isNotEmpty()) {
                addAll(existing.steps)
            } else {
                add(
                    RoutineStep(
                        id = "",
                        routineId = "",
                        position = 0,
                        kind = RoutineStepKind.BREATHING,
                        title = "Take 3 slow arrival breaths",
                        minEffortTitle = "30-second pause",
                        durationSeconds = 90,
                        minEffortDurationSeconds = 30
                    )
                )
                add(
                    RoutineStep(
                        id = "",
                        routineId = "",
                        position = 1,
                        kind = RoutineStepKind.QUIET_WRITE,
                        title = "Write or speak one honest sentence",
                        minEffortTitle = "One sentence",
                        durationSeconds = 120,
                        minEffortDurationSeconds = 30,
                        promptText = "What feels most important right now?"
                    )
                )
            }
        }
    }

    var newStepKind by remember { mutableStateOf(RoutineStepKind.CHECK) }
    var newStepTitle by remember { mutableStateOf("") }
    var confirmArchive by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceRaised) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MM.space.l)
                .padding(bottom = MM.space.xl)
                .testTag("routine_editor_sheet"),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Text(
                if (existing != null) "Edit Routine" else "Create Custom Routine",
                style = MM.type.title,
                color = c.ink
            )

            InsetPanel {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    textStyle = MM.type.bodyStrong.copy(color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().testTag("routine_editor_title"),
                    decorationBox = { inner ->
                        Box {
                            if (title.isEmpty()) Text("Routine name (e.g. Afternoon Reset)", style = MM.type.body, color = c.inkMuted)
                            inner()
                        }
                    }
                )
            }

            InsetPanel {
                BasicTextField(
                    value = cue,
                    onValueChange = { cue = it },
                    textStyle = MM.type.body.copy(color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (cue.isEmpty()) Text("Anchor cue (e.g. After lunch walk)", style = MM.type.body, color = c.inkMuted)
                            inner()
                        }
                    }
                )
            }

            Text("TIME WINDOW", style = MM.type.overline, color = c.inkMuted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                items(RoutineTimeWindow.entries, key = { it.name }) { w ->
                    FilterChip(
                        selected = window == w,
                        onClick = {
                            window = w
                            if (existing == null) scheduledMinute = w.defaultMinute
                        },
                        label = { Text(w.label, style = MM.type.caption) }
                    )
                }
            }

            Text("DAYS OF WEEK", style = MM.type.overline, color = c.inkMuted)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
            ) {
                DAY_LABELS.forEachIndexed { idx, lbl ->
                    val bit = 1 shl idx
                    val active = (daysMask and bit) != 0
                    FilterChip(
                        selected = active,
                        onClick = {
                            val toggled = daysMask xor bit
                            if (toggled != 0) daysMask = toggled
                        },
                        label = { Text(lbl, style = MM.type.caption) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text("AMBIENT SOUNDSCAPE", style = MM.type.overline, color = c.inkMuted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                items(PersonalSoundscape.entries, key = { it.name }) { sc ->
                    FilterChip(
                        selected = soundscape == sc,
                        onClick = { soundscape = sc },
                        label = { Text(sc.label, style = MM.type.caption) }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Gentle daily reminder", style = MM.type.bodyStrong, color = c.ink)
                    if (reminderEnabled) {
                        Text(formatMinuteOfDay(scheduledMinute), style = MM.type.caption, color = c.accent)
                    }
                }
                if (reminderEnabled) {
                    IconButton(onClick = { scheduledMinute = (scheduledMinute - 15 + 1440) % 1440 }) {
                        Icon(Icons.Rounded.Remove, contentDescription = "15 minutes earlier", tint = c.inkSecondary)
                    }
                    IconButton(onClick = { scheduledMinute = (scheduledMinute + 15) % 1440 }) {
                        Icon(Icons.Rounded.Add, contentDescription = "15 minutes later", tint = c.inkSecondary)
                    }
                }
                Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
            }

            SectionHeader(title = "Steps", count = steps.size)
            steps.forEachIndexed { idx, step ->
                MMCard(contentPadding = MM.space.m) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${idx + 1}. ${step.kind.label.uppercase()} · ${(step.durationSeconds / 60).coerceAtLeast(1)}m",
                                style = MM.type.overline,
                                color = c.accent
                            )
                            Spacer(Modifier.height(MM.space.xs))
                            BasicTextField(
                                value = step.title,
                                onValueChange = { updated -> steps[idx] = step.copy(title = updated) },
                                textStyle = MM.type.body.copy(color = c.ink),
                                cursorBrush = SolidColor(c.accent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (idx > 0) {
                            IconButton(onClick = {
                                val moving = steps.removeAt(idx)
                                steps.add(idx - 1, moving)
                            }) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move step up", tint = c.inkSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (idx < steps.lastIndex) {
                            IconButton(onClick = {
                                val moving = steps.removeAt(idx)
                                steps.add(idx + 1, moving)
                            }) {
                                Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move step down", tint = c.inkSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (steps.size > 1) {
                            IconButton(onClick = { steps.removeAt(idx) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove step", tint = c.inkMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Quick add-step composer
            MMCard(contentPadding = MM.space.m) {
                Text("ADD A STEP", style = MM.type.overline, color = c.inkMuted)
                Spacer(Modifier.height(MM.space.xs))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    items(
                        listOf(
                            RoutineStepKind.CHECK,
                            RoutineStepKind.BREATHING,
                            RoutineStepKind.QUIET_WRITE,
                            RoutineStepKind.VOICE_REFLECTION,
                            RoutineStepKind.INTENTION,
                            RoutineStepKind.TIMER,
                            RoutineStepKind.MEMORY
                        ),
                        key = { it.name }
                    ) { kind ->
                        FilterChip(
                            selected = newStepKind == kind,
                            onClick = { newStepKind = kind },
                            label = { Text(kind.label, style = MM.type.caption) }
                        )
                    }
                }
                Spacer(Modifier.height(MM.space.s))
                InsetPanel {
                    BasicTextField(
                        value = newStepTitle,
                        onValueChange = { newStepTitle = it },
                        textStyle = MM.type.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box {
                                if (newStepTitle.isEmpty()) Text("Step description…", style = MM.type.body, color = c.inkMuted)
                                inner()
                            }
                        }
                    )
                }
                Spacer(Modifier.height(MM.space.s))
                SecondaryButton(
                    text = "Add Step",
                    onClick = {
                        val trimmed = newStepTitle.trim()
                        if (trimmed.isNotBlank()) {
                            steps.add(
                                RoutineStep(
                                    id = "",
                                    routineId = existing?.id.orEmpty(),
                                    position = steps.size,
                                    kind = newStepKind,
                                    title = trimmed,
                                    minEffortTitle = "Gentle: ${trimmed.take(36)}",
                                    durationSeconds = if (newStepKind == RoutineStepKind.TIMER) 300 else 120,
                                    minEffortDurationSeconds = 30,
                                    promptText = if (newStepKind.capturesJournal) trimmed else ""
                                )
                            )
                            newStepTitle = ""
                        }
                    },
                    enabled = newStepTitle.isNotBlank(),
                    leadingIcon = Icons.Rounded.Add
                )
            }

            PrimaryButton(
                text = if (existing != null) "Save Changes" else "Save Routine",
                onClick = {
                    onSave(
                        title,
                        why,
                        window,
                        cue,
                        soundscape,
                        steps.toList(),
                        reminderEnabled,
                        scheduledMinute,
                        daysMask
                    )
                },
                enabled = title.isNotBlank() && steps.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().testTag("routine_editor_save")
            )

            if (existing != null && onArchive != null) {
                if (!confirmArchive) {
                    TextAction(
                        text = "Archive this routine",
                        onClick = { confirmArchive = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MM.space.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Archive \"${existing.title}\"?", style = MM.type.caption, color = c.inkSecondary, modifier = Modifier.weight(1f))
                        TextAction("Cancel", onClick = { confirmArchive = false })
                        TextAction("Archive", onClick = { onArchive(existing) })
                    }
                }
            }
        }
    }
}

/**
 * Interactive Life Chapter detail sheet: browse notes in this chapter, assign unfiled journals/ideas
 * into it, or archive the chapter when the season closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LifeChapterDetailSheet(
    chapter: LifeChapter,
    notesFlow: Flow<List<Note>>,
    candidateNotes: List<Note>,
    onOpenNote: (String) -> Unit,
    onAssignNote: (noteId: String, chapterId: String?, chapterTitle: String?) -> Unit,
    onArchiveChapter: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = MM.colors
    val notes by notesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceRaised) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MM.space.l)
                .padding(bottom = MM.space.xl)
                .testTag("life_chapter_detail_sheet"),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Text("LIFE CHAPTER", style = MM.type.overline, color = c.speaker3)
            Text(chapter.title, style = MM.type.title, color = c.ink)
            if (chapter.subtitle.isNotBlank()) {
                Text(chapter.subtitle, style = MM.type.secondary, color = c.inkSecondary)
            }

            SectionHeader(
                title = "Notes in this Chapter",
                count = notes.size,
                actionLabel = if (showAddPicker) "Done" else "Add note",
                onAction = { showAddPicker = !showAddPicker }
            )

            if (showAddPicker) {
                val available = candidateNotes.filter { it.notebookId != chapter.id }.take(8)
                if (available.isEmpty()) {
                    Text("All your recent journals and sparks are already in this chapter.", style = MM.type.caption, color = c.inkMuted)
                } else {
                    available.forEach { n ->
                        MMCard(onClick = { onAssignNote(n.id, chapter.id, chapter.title) }, contentPadding = MM.space.m) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(n.title.ifBlank { "Untitled note" }, style = MM.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(n.workflow.displayName, style = MM.type.caption, color = c.inkMuted)
                                }
                                Icon(Icons.Rounded.Add, contentDescription = "Add to chapter", tint = c.accent)
                            }
                        }
                    }
                }
            }

            if (notes.isEmpty()) {
                EmptyState(
                    title = "No notes in this chapter yet",
                    body = "Tap Add note above to gather journal entries, reflections, or sparks from this season."
                )
            } else {
                notes.forEach { note ->
                    MMCard(onClick = { onOpenNote(note.id) }, contentPadding = MM.space.m) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(note.title.ifBlank { "Journal entry" }, style = MM.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (note.plainText.isNotBlank()) {
                                    Text(previewOf(note.plainText), style = MM.type.caption, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            TextAction("Remove", onClick = { onAssignNote(note.id, null, null) })
                        }
                    }
                }
            }

            Spacer(Modifier.height(MM.space.s))
            TextAction(
                text = "Archive this Life Chapter",
                onClick = onArchiveChapter,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

/**
 * Interactive Journey / Challenge Detail sheet: inspect Day-1 "Why" Anchor Note, view completed steps,
 * log today's step, or pause / resume / leave the enrollment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EnrollmentDetailSheet(
    enrollment: GrowthEnrollment,
    loadStepProgress: suspend (String) -> List<GrowthStepProgress>,
    loadNote: suspend (String) -> Note?,
    onCheckIn: () -> Unit,
    onOpenNote: (String) -> Unit,
    onUpdateStatus: (GrowthEnrollmentStatus) -> Unit,
    onDismiss: () -> Unit
) {
    val c = MM.colors
    var progressList by remember(enrollment.id) { mutableStateOf<List<GrowthStepProgress>>(emptyList()) }
    var whyNote by remember(enrollment.anchorWhyNoteId) { mutableStateOf<Note?>(null) }

    LaunchedEffect(enrollment.id) {
        progressList = loadStepProgress(enrollment.id)
        whyNote = enrollment.anchorWhyNoteId?.let { loadNote(it) }
    }

    val journeyDef = PersonalGrowthCatalog.journeyById(enrollment.catalogId)
    val challengeDef = PersonalGrowthCatalog.challengeById(enrollment.catalogId)
    val customCfg = CustomChallengeConfig.fromJson(enrollment.customConfigJson)
    val subtitle = journeyDef?.tagline ?: challengeDef?.tagline ?: customCfg?.dailyAction.orEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceRaised) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MM.space.l)
                .padding(bottom = MM.space.xl)
                .testTag("enrollment_detail_sheet"),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Text(
                "${enrollment.kind.name} · ${enrollment.currentStepOrDay} OF ${enrollment.targetStepsOrDays} STEPS",
                style = MM.type.overline,
                color = c.speaker3
            )
            Text(enrollment.title, style = MM.type.title, color = c.ink)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MM.type.secondary, color = c.inkSecondary)
            }

            whyNote?.let { anchor ->
                MMCard(onClick = { onOpenNote(anchor.id) }, contentPadding = MM.space.m) {
                    Text("YOUR DAY 1 WHY", style = MM.type.overline, color = c.gold)
                    Spacer(Modifier.height(MM.space.xs))
                    Text(
                        previewOf(anchor.plainText).ifBlank { anchor.title },
                        style = MM.type.body,
                        color = c.ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (enrollment.status == GrowthEnrollmentStatus.ACTIVE) {
                PrimaryButton(
                    text = "Log Today's Step",
                    onClick = onCheckIn,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (enrollment.status == GrowthEnrollmentStatus.PAUSED) {
                PrimaryButton(
                    text = "Resume ${enrollment.kind.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    onClick = { onUpdateStatus(GrowthEnrollmentStatus.ACTIVE) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (progressList.isNotEmpty()) {
                SectionHeader(title = "Steps Kept", count = progressList.size)
                progressList.forEach { prog ->
                    val stepTitle = journeyDef?.steps?.getOrNull(prog.stepIndex)?.title ?: "Day ${prog.stepIndex + 1}"
                    MMCard(
                        onClick = prog.reflectionNoteId?.let { nId -> { onOpenNote(nId) } },
                        contentPadding = MM.space.m
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Completed", tint = c.speaker3, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(MM.space.s))
                            Column(Modifier.weight(1f)) {
                                Text("Day ${prog.stepIndex + 1} · $stepTitle", style = MM.type.bodyStrong, color = c.ink)
                                if (prog.usedMinEffort) {
                                    Text("Gentle minimum version", style = MM.type.caption, color = c.inkMuted)
                                }
                            }
                            if (prog.reflectionNoteId != null) {
                                Text("Open note", style = MM.type.caption, color = c.accent)
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (enrollment.status == GrowthEnrollmentStatus.ACTIVE) {
                    TextAction("Pause for now", onClick = { onUpdateStatus(GrowthEnrollmentStatus.PAUSED) })
                }
                TextAction("Leave quietly", onClick = { onUpdateStatus(GrowthEnrollmentStatus.ABANDONED) })
            }
        }
    }
}

internal fun formatMinuteOfDay(minuteOfDay: Int): String {
    val clamped = ((minuteOfDay % 1440) + 1440) % 1440
    val h24 = clamped / 60
    val m = clamped % 60
    val amPm = if (h24 < 12) "AM" else "PM"
    val h12 = when {
        h24 == 0 -> 12
        h24 > 12 -> h24 - 12
        else -> h24
    }
    return "%d:%02d %s".format(h12, m, amPm)
}
