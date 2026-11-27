@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.craftflowtechnologies.meetingmind.feature.learn.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.feature.learn.StudyTheme
import com.craftflowtechnologies.meetingmind.feature.learning.CourseChoice
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** The four ways to start a study session (CreateSessionFlow's sources, in the design's order). */
enum class StartWay { RECORD, VIDEO, IMPORT, WRITE }

/**
 * "Start a study session" (design/study/NewSession.dc.html): the four ways in, and which course
 * the session lands in. [onStart] gets the way and the course choice; the host runs the Learning
 * flow for it (LearnViewModel.prepareRecording, createNotesSession, the YouTube sheet).
 *
 * A course is picked as a chip; "New course" shows a name field. A way is only offered once a
 * course choice is valid, so the session always has a home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionSheet(
    courses: List<LearningRepository.LearningCourse>,
    presetCourseId: String?,
    onDismiss: () -> Unit,
    onStart: (StartWay, CourseChoice) -> Unit
) {
    val initial = presetCourseId?.takeIf { id -> courses.any { it.notebook.id == id } }
        ?: courses.firstOrNull()?.notebook?.id
    var selected by rememberSaveable { mutableStateOf(initial) }
    var newCourse by rememberSaveable { mutableStateOf(initial == null) }
    var newName by rememberSaveable { mutableStateOf("") }
    var newCode by rememberSaveable { mutableStateOf("") }

    val choice: CourseChoice? = when {
        newCourse -> newName.trim().takeIf { it.isNotEmpty() }?.let { CourseChoice.New(it, newCode.trim()) }
        selected != null -> CourseChoice.Existing(selected!!)
        else -> null
    }

    StudyTheme {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MM.colors.surfaceRaised,
            shape = MM.radius.sheet,
            modifier = Modifier.testTag("learning_new_session")
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = MM.space.l).padding(bottom = MM.space.xl),
                verticalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                Text(
                    "Start a study session",
                    style = MM.type.title,
                    color = MM.colors.ink,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    "Anything you add becomes a lesson with quick checks.",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary
                )
                Spacer(Modifier.size(MM.space.s))
                WayRow("Record a lecture", "Put your phone on the desk and start", choice != null, "new_session_record", { RecordDot() }) {
                    onStart(StartWay.RECORD, choice!!)
                }
                WayRow("From a video link", "Paste a YouTube lecture", choice != null, "new_session_video", {
                    Icon(Icons.Rounded.SmartDisplay, contentDescription = null, tint = MM.colors.accent, modifier = Modifier.size(MMSize.icon))
                }) { onStart(StartWay.VIDEO, choice!!) }
                WayRow("Import a recording or slides", "Audio, video or a PDF", choice != null, "new_session_import", {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null, tint = MM.colors.accent, modifier = Modifier.size(MMSize.icon))
                }) { onStart(StartWay.IMPORT, choice!!) }
                WayRow("Write a study note", "Type or paste what you learned", choice != null, "new_session_write", {
                    Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = null, tint = MM.colors.accent, modifier = Modifier.size(MMSize.icon))
                }) { onStart(StartWay.WRITE, choice!!) }

                Spacer(Modifier.size(MM.space.s))
                Text("Which course?", style = MM.type.caption, color = MM.colors.inkSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    courses.forEach { c ->
                        val label = c.properties.courseCode.takeIf { it.isNotBlank() } ?: c.notebook.name
                        MMChip(
                            label = label,
                            selected = !newCourse && selected == c.notebook.id,
                            onClick = { selected = c.notebook.id; newCourse = false },
                            modifier = Modifier.testTag("new_session_course_${c.notebook.id}")
                        )
                    }
                    MMChip(
                        label = "New course",
                        selected = newCourse,
                        onClick = { newCourse = true },
                        modifier = Modifier.testTag("new_session_course_new")
                    )
                }
                if (newCourse) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Course name, e.g. Biochemistry") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_session_course_name")
                    )
                    OutlinedTextField(
                        value = newCode,
                        onValueChange = { newCode = it },
                        label = { Text("Course code (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_session_course_code")
                    )
                }
            }
        }
    }
}

@Composable
private fun WayRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    tag: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val c = MM.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MMSize.minTouch + MM.space.m)
            .testTag(tag)
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .padding(vertical = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(MMSize.minTouch).clip(CircleShape).background(c.accentWash),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.width(MM.space.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MM.type.bodyStrong, color = if (enabled) c.ink else c.inkMuted)
            Text(subtitle, style = MM.type.secondary, color = c.inkSecondary)
        }
    }
}

@Composable
private fun RecordDot() {
    Box(Modifier.size(MMSize.iconSmall).clip(CircleShape).background(MM.colors.recording))
}
