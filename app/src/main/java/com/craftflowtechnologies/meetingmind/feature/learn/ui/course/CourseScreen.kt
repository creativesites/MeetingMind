@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.craftflowtechnologies.meetingmind.feature.learn.ui.course

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.HomeScaffold
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.learn.StarState
import com.craftflowtechnologies.meetingmind.feature.learn.StudyTheme
import com.craftflowtechnologies.meetingmind.feature.learn.home.CourseLecture
import com.craftflowtechnologies.meetingmind.feature.learn.home.CourseUiState
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/**
 * A course (design/study/Course.dc.html): its counts in words, how well the learner knows it,
 * one practice button, the exam plan, and the lectures in the course. Stateless.
 */
@Composable
fun CourseScreen(
    state: CourseUiState,
    onBack: () -> Unit,
    onPractise: () -> Unit,
    onOpenExamPlan: () -> Unit,
    onOpenLecture: (CourseLecture) -> Unit,
    onAddLecture: () -> Unit,
    modifier: Modifier = Modifier
) {
    StudyTheme {
        HomeScaffold(
            modifier = modifier.fillMaxSize().background(MM.colors.background).testTag("course_screen"),
            header = { Back(onBack) },
            sections = {
                if (!state.loaded) return@HomeScaffold
                if (!state.found) {
                    item(key = "gone") {
                        EmptyState(title = "This course is not here any more", body = "It may have been removed. Go back to Learning to pick another.")
                    }
                    return@HomeScaffold
                }
                item(key = "title") { Title(state) }
                item(key = "status") { Status(state, onOpenExamPlan) }
                item(key = "knowing") { Knowing(state) }
                item(key = "actions") { Actions(onPractise, onOpenExamPlan) }
                item(key = "lectures_header") { LecturesHeader(onAddLecture) }
                if (state.lectures.isEmpty()) item(key = "no_lectures") {
                    Text("No lectures yet. Add one to start this course.", style = MM.type.secondary, color = MM.colors.inkSecondary)
                } else item(key = "lectures") {
                    MMCard(contentPadding = MM.space.xs, modifier = Modifier.testTag("course_lectures")) {
                        state.lectures.forEach { lecture -> LectureRow(lecture, onClick = { onOpenLecture(lecture) }) }
                    }
                }
            }
        )
    }
}

@Composable
private fun Back(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = MMSize.minTouch), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("course_back")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MM.colors.ink)
        }
    }
}

@Composable
private fun Title(state: CourseUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
        state.code?.let { Text(it, style = MM.type.caption, color = MM.colors.accent) }
        Text(
            state.name,
            style = MM.type.display,
            color = MM.colors.ink,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            countsLine(state),
            style = MM.type.secondary,
            color = MM.colors.inkSecondary
        )
    }
}

/** "7 lectures · 31 ideas · 12 solid": every count carries its word. */
internal fun countsLine(state: CourseUiState): String {
    val lectures = "${state.lectureCount} ${if (state.lectureCount == 1) "lecture" else "lectures"}"
    val ideas = "${state.ideaCount} ${if (state.ideaCount == 1) "idea" else "ideas"}"
    return "$lectures · $ideas · ${state.solid} solid"
}

@Composable
private fun Status(state: CourseUiState, onOpenExamPlan: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.s), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        state.examInDays?.let { days ->
            Surface(shape = MM.radius.pill, color = MM.colors.goldWash, modifier = Modifier.testTag("course_exam_chip")) {
                Row(Modifier.padding(horizontal = MM.space.m, vertical = MM.space.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = MM.colors.goldInk, modifier = Modifier.size(MMSize.iconSmall))
                    Spacer(Modifier.width(MM.space.xs))
                    Text(
                        if (days == 0) "Exam today" else if (days == 1) "Exam in 1 day" else "Exam in $days days",
                        style = MM.type.caption,
                        color = MM.colors.goldInk
                    )
                }
            }
        }
        if (state.dueCount > 0) {
            Text(
                "${state.dueCount} ${if (state.dueCount == 1) "card" else "cards"} due",
                style = MM.type.caption,
                color = MM.colors.inkSecondary,
                modifier = Modifier.padding(vertical = MM.space.xs)
            )
        }
    }
}

/** "How well you know it": a stacked bar, then the four counts as words. */
@Composable
private fun Knowing(state: CourseUiState) {
    val c = MM.colors
    MMCard(Modifier.testTag("course_knowing")) {
        Text("How well you know it", style = MM.type.heading, color = c.ink, modifier = Modifier.semantics { heading() })
        Row(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(MM.radius.pill)
                .semantics {
                    contentDescription = "${state.solid} solid, ${state.growing} getting there, ${state.new} new, ${state.worth} worth a look"
                }
        ) {
            Segment(state.solid, c.accent)
            Segment(state.growing, c.accentWash)
            Segment(state.new, c.track)
            Segment(state.worth, c.gold)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
            Legend("${state.solid}", StarState.SHINING.label)
            Legend("${state.growing}", StarState.GROWING.label)
            Legend("${state.new}", StarState.NEW.label)
            Legend("${state.worth}", StarState.WORTH_A_LOOK.label)
        }
    }
}

@Composable
private fun RowScope.Segment(count: Int, color: Color) {
    if (count > 0) Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(color))
}

@Composable
private fun Legend(count: String, word: String) {
    Text(
        "$count ${word.lowercase()}",
        style = MM.type.secondary,
        color = MM.colors.inkSecondary
    )
}

@Composable
private fun Actions(onPractise: () -> Unit, onOpenExamPlan: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        PrimaryButton("Practise this course", onPractise, modifier = Modifier.fillMaxWidth().testTag("course_practise"))
        SecondaryButton("Exam plan", onOpenExamPlan, modifier = Modifier.fillMaxWidth().testTag("course_exam_plan"))
    }
}

@Composable
private fun LecturesHeader(onAddLecture: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Lectures", style = MM.type.heading, color = MM.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
        TextAction("Add", onAddLecture, modifier = Modifier.testTag("course_add"))
    }
}

@Composable
private fun LectureRow(lecture: CourseLecture, onClick: () -> Unit) {
    val c = MM.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MMSize.minTouch)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MM.space.s, vertical = MM.space.xs)
            .testTag("course_lecture_${lecture.noteId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "%02d".format(lecture.number),
            style = MM.type.caption,
            color = c.inkMuted,
            modifier = Modifier.width(32.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(lecture.title, style = MM.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(lecture.dateLabel, "${lecture.ideaCount} ideas").joinToString(" · "),
                style = MM.type.caption,
                color = c.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(MM.space.s))
        Text(lecture.stateWord, style = MM.type.caption, color = if (lecture.stateWord.contains("worth")) c.goldInk else c.inkSecondary)
    }
}
