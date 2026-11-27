package com.craftflowtechnologies.meetingmind.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/** When a note gets the lesson entry. */
object LessonEntry {
    /** Recording types whose notes open as a Study lesson. */
    fun isLesson(type: RecordingType?): Boolean = type == RecordingType.LECTURE || type == RecordingType.RESEARCH

    /** Lecture and Research notes, and any note kept in a Learning notebook, get one lesson entry. */
    fun shows(type: RecordingType?, notebookSpace: NotebookSpace?): Boolean =
        isLesson(type) || notebookSpace == NotebookSpace.LEARNING
}

/**
 * The one way into a lecture's lesson from its note: experience first, the note stays below.
 * It replaces the Learning studio strip and the earlier Study banner, so a note shows exactly one entry.
 */
@Composable
fun LessonEntryBanner(onOpenLesson: () -> Unit, modifier: Modifier = Modifier) {
    StudyTheme {
        HeroCard(modifier = modifier.fillMaxWidth().testTag("note_open_lesson"), onClick = onOpenLesson) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.m)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    Text("Study this lecture", style = MM.type.bodyStrong, color = MM.colors.ink)
                    Text("What mattered, quick checks and a practice round.", style = MM.type.secondary, color = MM.colors.inkSecondary)
                }
                PrimaryButton("Open lesson", onClick = onOpenLesson, leadingIcon = Icons.Filled.School)
            }
        }
    }
}
