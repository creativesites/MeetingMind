package com.craftflowtechnologies.meetingmind.feature.learn.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.craftflowtechnologies.meetingmind.feature.learn.RoundSummary
import com.craftflowtechnologies.meetingmind.feature.learn.StudyHomeState
import com.craftflowtechnologies.meetingmind.feature.learn.home.HomeCourse
import com.craftflowtechnologies.meetingmind.feature.learn.home.HomeLecture
import com.craftflowtechnologies.meetingmind.feature.learn.home.HomeLesson
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningHomeUiState
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme

/**
 * Sample content for the Learning home, from design/study/LearningHome.dc.html. Shared by the
 * previews below and by the screenshot and behaviour tests.
 */
internal object LearningHomeSamples {

    /** Wednesday evening, with a round due and the mockup's courses, lectures and week. */
    val due = LearningHomeUiState(
        study = StudyHomeState(
            loading = false,
            round = RoundSummary(dueCount = 8, minutes = 5, sourceTitles = listOf("Enzyme kinetics", "Supply shocks", "Acids and bases")),
            sky = StudyHomeSamples.sky,
            studiedDays = listOf(true, true, false, true, true, false, false),
            todayIndex = 2,
            companionLine = "8 cards are due. A quick round?",
            hasAnyLesson = true
        ),
        continueLesson = HomeLesson(
            noteId = "note-bio201-l7",
            title = "Enzyme kinetics",
            courseName = "BIO 201 · Lecture 7",
            stoppedAt = "2 · Inhibition",
            progress = 0.45f
        ),
        courses = listOf(
            HomeCourse(id = "bio201", code = "BIO 201", name = "Biochemistry", solid = 12, total = 20, dueCount = 4, examInDays = 12),
            HomeCourse(id = "econ101", code = "ECON 101", name = "Economics", solid = 16, total = 20, dueCount = 0, examInDays = null),
            HomeCourse(id = "chem110", code = "CHEM 110", name = "Chemistry", solid = 6, total = 20, dueCount = 2, examInDays = null)
        ),
        recent = listOf(
            HomeLecture("note-econ-supply", "Supply shocks", "ECON 101 · Mon · 52 min", isLesson = true, ready = true),
            HomeLecture("note-chem-acids", "Acids and bases", "CHEM 110 · Today · 1 h 02 min", isLesson = true, ready = false),
            HomeLecture("note-bio-cell", "Study note: cell signalling", "BIO 201 · Sun · written", isLesson = false, ready = true)
        ),
        doneBy = "9:20 PM",
        planMinutes = 5
    )

    /** Nothing due: the caught-up card replaces the round. */
    val caughtUp = due.copy(
        study = due.study.copy(round = null, companionLine = "All caught up. Nice work."),
        doneBy = null
    )

    /** First use: no lectures yet, so the sky, the continue card and the lists are left out. */
    val firstUse = LearningHomeUiState(
        study = StudyHomeState(loading = false, hasAnyLesson = false),
        courses = emptyList(),
        recent = emptyList()
    )
}

@Preview(name = "Learning home, Paper", widthDp = 390, heightDp = 1560, showBackground = true)
@Composable
private fun LearningHomePreview() {
    MeetMindTheme(darkTheme = false) {
        LearningHomeScreen(state = LearningHomeSamples.due, dayLine = "Wednesday evening", cb = LearningHomeCallbacks())
    }
}

@Preview(name = "Learning home, Midnight", widthDp = 390, heightDp = 1560, showBackground = true)
@Composable
private fun LearningHomeDarkPreview() {
    MeetMindTheme(darkTheme = true) {
        LearningHomeScreen(state = LearningHomeSamples.due, dayLine = "Wednesday evening", cb = LearningHomeCallbacks())
    }
}

@Preview(name = "Learning home, first use", widthDp = 390, heightDp = 900, showBackground = true)
@Composable
private fun LearningHomeFirstUsePreview() {
    MeetMindTheme(darkTheme = false) {
        LearningHomeScreen(state = LearningHomeSamples.firstUse, dayLine = "Wednesday evening", cb = LearningHomeCallbacks())
    }
}

/** The screen's callbacks grouped, so previews and tests can pass them in one value. */
internal data class LearningHomeCallbacks(
    val onAsk: () -> Unit = {},
    val onStartRound: () -> Unit = {},
    val onPractiseAnyway: () -> Unit = {},
    val onOpenLesson: (String) -> Unit = {},
    val onOpenNote: (String) -> Unit = {},
    val onOpenCourse: (String) -> Unit = {},
    val onAddCourse: () -> Unit = {},
    val onRecordLecture: () -> Unit = {},
    val onOpenYouTube: () -> Unit = {},
    val onImportFile: () -> Unit = {},
    val onWriteStudyNote: () -> Unit = {},
    val onOpenAllIdeas: () -> Unit = {},
    val onOpenSettings: () -> Unit = {}
)

/** The screen with its callbacks in one value, for previews and tests. */
@Composable
internal fun LearningHomeScreen(state: LearningHomeUiState, dayLine: String, cb: LearningHomeCallbacks) {
    LearningHomeScreen(
        state = state,
        dayLine = dayLine,
        onAsk = cb.onAsk,
        onStartRound = cb.onStartRound,
        onPractiseAnyway = cb.onPractiseAnyway,
        onOpenLesson = cb.onOpenLesson,
        onOpenNote = cb.onOpenNote,
        onOpenCourse = cb.onOpenCourse,
        onAddCourse = cb.onAddCourse,
        onRecordLecture = cb.onRecordLecture,
        onOpenYouTube = cb.onOpenYouTube,
        onImportFile = cb.onImportFile,
        onWriteStudyNote = cb.onWriteStudyNote,
        onOpenAllIdeas = cb.onOpenAllIdeas,
        onOpenSettings = cb.onOpenSettings
    )
}
