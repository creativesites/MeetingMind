package com.craftflowtechnologies.meetingmind.feature.learning.space

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.learning.CourseChoice
import com.craftflowtechnologies.meetingmind.feature.learning.CreateStage
import com.craftflowtechnologies.meetingmind.feature.learning.LearnViewModel
import com.craftflowtechnologies.meetingmind.ui.theme.LearningTheme
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** Where a new study session comes from (step 1). */
enum class StudySource { RECORD, EXISTING, YOUTUBE, IMPORT, WRITE }

/**
 * Create a study session in three steps (design spec §12.1): what you study from, which course,
 * then build. Recording and writing open their own screens after step 2; an existing recording or
 * note is built here with honest progress, and the source is never changed.
 */
@Composable
fun CreateSessionFlow(
    viewModel: LearnViewModel,
    presetCourseId: String?,
    onClose: () -> Unit,
    onRecord: () -> Unit,
    onImport: () -> Unit,
    onOpenNote: (noteId: String) -> Unit,
    onOpenSession: (noteId: String) -> Unit
) {
    val courses by viewModel.courses.collectAsState()
    val lectures by viewModel.lectureNotes.collectAsState()
    val stage by viewModel.createStage.collectAsState()
    val created by viewModel.createdNoteId.collectAsState()
    val error by viewModel.createError.collectAsState()

    var step by rememberSaveable { mutableStateOf(1) }
    var source by rememberSaveable { mutableStateOf<StudySource?>(null) }
    val youTube = com.craftflowtechnologies.meetingmind.feature.importing.LocalYouTubeSheet.current
    var pickedNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var courseId by rememberSaveable { mutableStateOf(presetCourseId) }
    var newCourse by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var newCode by rememberSaveable { mutableStateOf("") }
    var noCourse by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.resetCreate() }

    val choice: CourseChoice? = when {
        newCourse -> newName.trim().takeIf { it.isNotEmpty() }?.let { CourseChoice.New(it, newCode) }
        noCourse -> CourseChoice.None
        courseId != null -> CourseChoice.Existing(courseId!!)
        else -> null
    }

    fun back() {
        when {
            step == 3 && stage != null && stage != CreateStage.READY && error == null -> Unit // building: stay
            step > 1 -> step -= 1
            else -> onClose()
        }
    }
    BackHandler { back() }

    Column(Modifier.fillMaxSize().background(MM.colors.background).statusBarsPadding().testTag("learn_create_flow")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = MM.space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::back) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = if (step == 1) "Close" else "Back", tint = MM.colors.ink)
            }
            Text("Step $step of 3", style = MM.type.caption, color = MM.colors.inkSecondary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = LearningTheme.readingGutter, vertical = MM.space.m),
            verticalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            when (step) {
                1 -> {
                    item { StepTitle("What are you studying from?", "Your recordings and notes are saved on this phone.") }
                    item {
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            SourceOption(Icons.Rounded.Mic, "Record a lecture", "Start recording now. The study guide is built when it's done.", source == StudySource.RECORD) { source = StudySource.RECORD }
                            SourceOption(Icons.Rounded.LibraryBooks, "A recording or note you have", "Turn something you already have into a study session.", source == StudySource.EXISTING) { source = StudySource.EXISTING }
                            SourceOption(Icons.Rounded.SmartDisplay, "From a YouTube video", "Paste a link to a public lecture; notes and practice cards are made from it.", source == StudySource.YOUTUBE) { source = StudySource.YOUTUBE }
                            SourceOption(Icons.Rounded.FileUpload, "Import an audio file", "A lecture recorded on another device.", source == StudySource.IMPORT) { source = StudySource.IMPORT }
                            SourceOption(Icons.AutoMirrored.Rounded.NoteAdd, "Write study notes", "Type your own notes; questions come from them.", source == StudySource.WRITE) { source = StudySource.WRITE }
                        }
                    }
                    if (source == StudySource.EXISTING) {
                        val candidates = lectures.sortedByDescending { it.updatedAt }
                        item { Text("CHOOSE ONE", style = MM.type.overline, color = MM.colors.inkMuted) }
                        if (candidates.isEmpty()) item {
                            Text("Nothing recorded or written yet.", style = MM.type.body, color = MM.colors.inkSecondary)
                        }
                        items(candidates, key = { "cand_${it.noteId}" }) { l ->
                            CandidateRow(l, selected = pickedNoteId == l.noteId) { pickedNoteId = l.noteId }
                        }
                    }
                }
                2 -> {
                    item { StepTitle("Which course is it for?", "Courses group your sessions and show progress across them.") }
                    item {
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            courses.forEach { c ->
                                val meta = listOfNotNull(c.properties.courseCode.takeIf { it.isNotBlank() }, if (c.sessionCount == 1) "1 session" else "${c.sessionCount} sessions").joinToString(" · ")
                                ChoiceRow(c.notebook.name, meta, selected = !newCourse && !noCourse && courseId == c.notebook.id) {
                                    courseId = c.notebook.id; newCourse = false; noCourse = false
                                }
                            }
                            ChoiceRow("New course", "Name it now; add details later.", selected = newCourse, tag = "create_new_course") {
                                newCourse = true; noCourse = false
                            }
                            if (newCourse) {
                                Field(newName, { newName = it }, "Course name, e.g. Human Biochemistry", "create_course_name")
                                Field(newCode, { newCode = it }, "Course code (optional)", "create_course_code")
                            }
                            ChoiceRow("No course for now", "You can file it later from the Library.", selected = noCourse, tag = "create_no_course") {
                                noCourse = true; newCourse = false
                            }
                        }
                    }
                }
                else -> {
                    item { StepTitle(if (stage == CreateStage.READY) "Your study session is ready" else "Building your study session", "Your recording and notes are not changed.") }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(MM.space.s), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                            StageRow("Add to course", stateOf(CreateStage.COURSE, stage), skipped = choice is CourseChoice.None)
                            StageRow("Find key concepts and write questions", stateOf(CreateStage.GUIDE, stage))
                            StageRow("Ready to study", stateOf(CreateStage.READY, stage))
                        }
                    }
                    if (error != null) item {
                        StatusLine(StatusKind.Error, error!!, actionLabel = "Try again", onAction = {
                            val id = pickedNoteId; val ch = choice
                            if (id != null && ch != null) viewModel.buildStudySession(id, ch)
                        })
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = LearningTheme.readingGutter, vertical = MM.space.m).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            when (step) {
                1 -> PrimaryButton(
                    text = when (source) {
                        StudySource.IMPORT -> "Choose a file"
                        StudySource.YOUTUBE -> "Paste a link"
                        else -> "Continue"
                    },
                    enabled = source != null && (source != StudySource.EXISTING || pickedNoteId != null),
                    onClick = {
                        when (source) {
                            StudySource.IMPORT -> { onClose(); onImport() }
                            // The YouTube sheet makes the lecture; it is pre-set to Lecture from Learning.
                            StudySource.YOUTUBE -> { onClose(); youTube.open(from = com.craftflowtechnologies.meetingmind.core.model.NotebookSpace.LEARNING) }
                            else -> step = 2
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("create_continue")
                )
                2 -> PrimaryButton(
                    text = when (source) {
                        StudySource.RECORD -> "Start recording"
                        StudySource.WRITE -> "Start writing"
                        else -> "Build study session"
                    },
                    enabled = choice != null,
                    onClick = {
                        val ch = choice ?: return@PrimaryButton
                        when (source) {
                            StudySource.RECORD -> viewModel.prepareRecording(ch) { onClose(); onRecord() }
                            StudySource.WRITE -> viewModel.createNotesSession(ch) { id -> onClose(); onOpenNote(id) }
                            else -> pickedNoteId?.let { id -> step = 3; viewModel.buildStudySession(id, ch) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("create_continue")
                )
                else -> if (stage == CreateStage.READY && created != null) {
                    PrimaryButton("Open study guide", onClick = { val id = created!!; onClose(); onOpenSession(id) }, modifier = Modifier.fillMaxWidth().testTag("create_open"))
                } else if (error != null) {
                    TextAction("Close", onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

private enum class StageState { DONE, NOW, WAITING }

private fun stateOf(row: CreateStage, current: CreateStage?): StageState = when {
    current == null -> if (row == CreateStage.COURSE) StageState.NOW else StageState.WAITING
    row.ordinal < current.ordinal -> StageState.DONE
    row == current -> if (current == CreateStage.READY) StageState.DONE else StageState.NOW
    else -> StageState.WAITING
}

@Composable
private fun StepTitle(title: String, sub: String) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
        Text(title, style = MM.type.title, color = MM.colors.ink, modifier = Modifier.semantics { heading() })
        Text(sub, style = MM.type.secondary, color = MM.colors.inkSecondary)
    }
}

@Composable
private fun SourceOption(icon: ImageVector, title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MM.radius.card)
            .background(if (selected) MM.colors.accentWash else MM.colors.surface)
            .border(if (selected) 2.dp else MMSize.hairline, if (selected) MM.colors.accent else MM.colors.line, MM.radius.card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(MM.space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Icon(icon, contentDescription = null, tint = MM.colors.accent)
        Column(Modifier.weight(1f)) {
            Text(title, style = MM.type.bodyStrong, color = MM.colors.ink)
            Text(sub, style = MM.type.caption, color = MM.colors.inkSecondary)
        }
    }
}

@Composable
private fun ChoiceRow(title: String, sub: String?, selected: Boolean, tag: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MM.radius.card)
            .background(if (selected) MM.colors.accentWash else MM.colors.surface)
            .border(if (selected) 2.dp else MMSize.hairline, if (selected) MM.colors.accent else MM.colors.line, MM.radius.card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(MM.space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Box(
            Modifier.size(20.dp).clip(MM.radius.pill)
                .border(2.dp, if (selected) MM.colors.accent else MM.colors.inkMuted, MM.radius.pill)
                .padding(4.dp)
                .clip(MM.radius.pill)
                .background(if (selected) MM.colors.accent else MM.colors.surface)
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MM.type.bodyStrong, color = MM.colors.ink)
            if (!sub.isNullOrBlank()) Text(sub, style = MM.type.caption, color = MM.colors.inkSecondary)
        }
    }
}

@Composable
private fun CandidateRow(l: LearningRepository.LectureNoteSummary, selected: Boolean, onClick: () -> Unit) {
    val meta = listOfNotNull(
        if (l.hasRecording) "Recording" else "Notes",
        l.courseName?.takeIf { it.isNotBlank() }?.let { "in $it" },
        if (l.activityCount > 0) "has a study guide" else null
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MM.radius.card)
            .background(if (selected) MM.colors.accentWash else MM.colors.surface)
            .border(if (selected) 2.dp else MMSize.hairline, if (selected) MM.colors.accent else MM.colors.line, MM.radius.card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(MM.space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(l.title, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MM.type.caption, color = MM.colors.inkSecondary)
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MM.colors.accent)
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, tag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label, style = MM.type.secondary) },
        textStyle = MM.type.body.copy(color = MM.colors.ink),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MM.colors.accent, unfocusedBorderColor = MM.colors.line, cursorColor = MM.colors.accent),
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}

@Composable
private fun StageRow(label: String, state: StageState, skipped: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MM.space.m)
    ) {
        Box(
            Modifier.size(24.dp).clip(MM.radius.pill).background(
                when (state) {
                    StageState.DONE -> MM.colors.accent
                    StageState.NOW -> MM.colors.accentWash
                    StageState.WAITING -> MM.colors.track
                }
            ),
            contentAlignment = Alignment.Center
        ) {
            if (state == StageState.DONE) Icon(Icons.Rounded.Check, contentDescription = null, tint = MM.colors.onAccent, modifier = Modifier.size(16.dp))
            if (state == StageState.NOW) Box(Modifier.size(8.dp).clip(MM.radius.pill).background(MM.colors.accent))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MM.type.bodyStrong, color = if (state == StageState.WAITING) MM.colors.inkMuted else MM.colors.ink)
            Text(
                when {
                    skipped && state == StageState.DONE -> "Skipped: no course"
                    state == StageState.DONE -> "Done"
                    state == StageState.NOW -> "Working on it"
                    else -> "Waiting"
                },
                style = MM.type.caption, color = MM.colors.inkSecondary
            )
        }
    }
}
