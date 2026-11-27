package com.craftflowtechnologies.meetingmind.feature.learn.ui.course

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.craftflowtechnologies.meetingmind.feature.learn.home.CourseLecture
import com.craftflowtechnologies.meetingmind.feature.learn.home.CourseUiState
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme

/** Sample content for the course page, from design/study/Course.dc.html. */
internal object CourseSamples {
    val biochemistry = CourseUiState(
        loaded = true,
        found = true,
        id = "bio201",
        code = "BIO 201",
        name = "Biochemistry",
        lectureCount = 7,
        ideaCount = 31,
        solid = 12,
        growing = 9,
        new = 6,
        worth = 4,
        examInDays = 12,
        dueCount = 4,
        lectures = listOf(
            CourseLecture("note-07", isLesson = true, number = 7, title = "Enzyme kinetics", dateLabel = "Tue 12 Nov", ideaCount = 12, stateWord = "2 worth a look"),
            CourseLecture("note-06", isLesson = true, number = 6, title = "Protein folding", dateLabel = "Thu 7 Nov", ideaCount = 9, stateWord = "Solid"),
            CourseLecture("note-05", isLesson = true, number = 5, title = "Amino acids", dateLabel = "Tue 5 Nov", ideaCount = 10, stateWord = "Getting there")
        )
    )
}

@Preview(name = "Course, Paper", widthDp = 390, heightDp = 1100, showBackground = true)
@Composable
private fun CoursePreview() {
    MeetMindTheme(darkTheme = false) {
        CourseScreen(
            state = CourseSamples.biochemistry,
            onBack = {}, onPractise = {}, onOpenExamPlan = {}, onOpenLecture = {}, onAddLecture = {}
        )
    }
}

@Preview(name = "Course, Midnight", widthDp = 390, heightDp = 1100, showBackground = true)
@Composable
private fun CourseDarkPreview() {
    MeetMindTheme(darkTheme = true) {
        CourseScreen(
            state = CourseSamples.biochemistry,
            onBack = {}, onPractise = {}, onOpenExamPlan = {}, onOpenLecture = {}, onAddLecture = {}
        )
    }
}
