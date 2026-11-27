package com.craftflowtechnologies.meetingmind.feature.learn.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.craftflowtechnologies.meetingmind.feature.learn.ContinueLesson
import com.craftflowtechnologies.meetingmind.feature.learn.CourseTile
import com.craftflowtechnologies.meetingmind.feature.learn.RoundSummary
import com.craftflowtechnologies.meetingmind.feature.learn.SkyLink
import com.craftflowtechnologies.meetingmind.feature.learn.SkyState
import com.craftflowtechnologies.meetingmind.feature.learn.SkyStar
import com.craftflowtechnologies.meetingmind.feature.learn.StarState
import com.craftflowtechnologies.meetingmind.feature.learn.StudyHomeState
import com.craftflowtechnologies.meetingmind.ui.theme.MeetMindTheme

/**
 * Sample content for the Study home. The mockup's words (design/study/Main.dc.html), built by hand.
 * Shared by the previews below and by the screenshot and behaviour tests.
 */
internal object StudyHomeSamples {
    /** Star positions from the mockup's 352 x 190 box, as fractions. */
    private fun at(px: Float, py: Float) = px / 352f to py / 190f

    private fun star(id: String, label: String, state: StarState, p: Pair<Float, Float>) =
        SkyStar(id, label, state, p.first, p.second)

    val sky = SkyState(
        stars = listOf(
            star("km", "Km", StarState.SHINING, at(52f, 58f)),
            star("vmax", "Vmax", StarState.SHINING, at(110f, 40f)),
            star("inhibition", "Inhibition", StarState.SHINING, at(150f, 86f)),
            star("elasticity", "Elasticity", StarState.SHINING, at(280f, 92f)),
            star("supply", "Supply shock", StarState.SHINING, at(232f, 120f)),
            star("allostery", "Allostery", StarState.GROWING, at(96f, 118f)),
            star("demand", "Demand curve", StarState.GROWING, at(206f, 70f)),
            star("ph", "pH", StarState.GROWING, at(318f, 128f)),
            star("buffers", "Buffers", StarState.GROWING, at(60f, 150f)),
            star("rate", "Rate law", StarState.NEW, at(300f, 44f)),
            star("cofactors", "Cofactors", StarState.NEW, at(232f, 172f)),
            star("isotherms", "Isotherms", StarState.NEW, at(20f, 104f)),
            star("competitive", "Competitive", StarState.WORTH_A_LOOK, at(150f, 138f)),
            star("cooperativity", "Cooperativity", StarState.WORTH_A_LOOK, at(222f, 22f))
        ),
        links = listOf(
            SkyLink("km", "vmax"), SkyLink("vmax", "inhibition"),
            SkyLink("inhibition", "allostery"), SkyLink("allostery", "km"),
            SkyLink("inhibition", "demand"), SkyLink("demand", "elasticity"),
            SkyLink("supply", "elasticity"), SkyLink("elasticity", "ph")
        ),
        totalIdeas = 31
    )

    private val waveform: List<Float> = listOf(
        0.42f, 0.7f, 0.5f, 0.9f, 0.6f, 1f, 0.38f, 0.8f, 1f, 0.65f, 0.3f, 0.75f, 0.55f, 0.9f,
        0.35f, 0.5f, 0.25f, 0.95f, 0.45f, 0.65f, 0.85f, 0.4f, 0.6f, 0.75f, 0.5f, 0.8f
    )

    val continueLesson = ContinueLesson(
        sessionId = "bio201-l7",
        title = "Enzyme kinetics",
        courseName = "Biochemistry",
        stoppedAt = "2 · Inhibition",
        progress = 0.48f,
        waveform = waveform
    )

    val recent = listOf(
        ContinueLesson("econ101-l4", "Supply shocks", "Microeconomics", "1 · Shifts", 0.2f),
        ContinueLesson("chem110-l3", "Acids and bases", "General chemistry", null, 0f)
    )

    val courses = listOf(
        CourseTile("Biochemistry", "BIO 201", "2 worth a look", needsAttention = true),
        CourseTile("Microeconomics", "ECON 101", "All caught up", needsAttention = false),
        CourseTile("General chemistry", "CHEM 110", "Exam in 12 days", needsAttention = false)
    )

    /** The due state: the mockup's Wednesday evening. */
    val due = StudyHomeState(
        round = RoundSummary(dueCount = 8, minutes = 5, sourceTitles = listOf("Enzyme kinetics", "Supply shocks", "Acids and bases")),
        sky = sky,
        continueLesson = continueLesson,
        courses = courses,
        studiedDays = listOf(true, true, false, true, true, false, false),
        todayIndex = 2,
        companionLine = "8 cards are due. A quick round?",
        hasAnyLesson = true,
        recentLessons = recent
    )

    /** Nothing due: the round card becomes "Practise anyway". */
    val caughtUp = due.copy(
        round = null,
        companionLine = "All caught up.",
        courses = courses.map { if (it.name == "Biochemistry") it.copy(status = "All caught up", needsAttention = false) else it }
    )

    /** No lecture yet. */
    val firstUse = StudyHomeState(hasAnyLesson = false)

    val loading = StudyHomeState(loading = true)
}

@Preview(name = "Study home, Paper", widthDp = 390, heightDp = 1240, showBackground = true)
@Composable
private fun StudyHomePaperPreview() {
    MeetMindTheme(darkTheme = false) {
        StudyHomeScreen(
            state = StudyHomeSamples.due,
            onStartRound = {}, onPractiseAnyway = {}, onLater = {}, onOpenLesson = {}, onOpenCourse = {},
            onOpenAllIdeas = {}, onSearch = {}, onRecordLecture = {}, onImportLecture = {}
        )
    }
}

@Preview(name = "Study home, Midnight", widthDp = 390, heightDp = 1240, showBackground = true)
@Composable
private fun StudyHomeMidnightPreview() {
    MeetMindTheme(darkTheme = true) {
        StudyHomeScreen(
            state = StudyHomeSamples.due,
            onStartRound = {}, onPractiseAnyway = {}, onLater = {}, onOpenLesson = {}, onOpenCourse = {},
            onOpenAllIdeas = {}, onSearch = {}, onRecordLecture = {}, onImportLecture = {}
        )
    }
}

@Preview(name = "Caught up, Paper", widthDp = 390, heightDp = 1240, showBackground = true)
@Composable
private fun StudyHomeCaughtUpPreview() {
    MeetMindTheme(darkTheme = false) {
        StudyHomeScreen(
            state = StudyHomeSamples.caughtUp,
            onStartRound = {}, onPractiseAnyway = {}, onLater = {}, onOpenLesson = {}, onOpenCourse = {},
            onOpenAllIdeas = {}, onSearch = {}, onRecordLecture = {}, onImportLecture = {}
        )
    }
}

@Preview(name = "First use, Midnight", widthDp = 390, heightDp = 800, showBackground = true)
@Composable
private fun StudyHomeFirstUsePreview() {
    MeetMindTheme(darkTheme = true) {
        StudyHomeScreen(
            state = StudyHomeSamples.firstUse,
            onStartRound = {}, onPractiseAnyway = {}, onLater = {}, onOpenLesson = {}, onOpenCourse = {},
            onOpenAllIdeas = {}, onSearch = {}, onRecordLecture = {}, onImportLecture = {}
        )
    }
}
