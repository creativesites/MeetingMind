package com.craftflowtechnologies.meetingmind.feature.learn

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.learning.StudyServices
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.share.ShareCardContent
import com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination
import com.craftflowtechnologies.meetingmind.feature.learn.home.CourseHost
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningHomeActions
import com.craftflowtechnologies.meetingmind.feature.learn.home.LearningHomeHost
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.LessonActions
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.LessonHost
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.ListenHost
import com.craftflowtechnologies.meetingmind.feature.learn.practice.PracticeActions
import com.craftflowtechnologies.meetingmind.feature.learn.practice.PracticeHost
import com.craftflowtechnologies.meetingmind.feature.navigation.Routes
import com.craftflowtechnologies.meetingmind.feature.share.ShareRequest
import com.craftflowtechnologies.meetingmind.feature.share.ShareRequests
import kotlinx.coroutines.launch

/**
 * Learning, all in one place (design/study/, founder 2026-11-27): the home (also the Learning home
 * style), a course, a lecture's lesson, its replay and a practice round. One way in to each:
 *
 * Learning home → course → lesson → practice. Lecture notes and recordings open the same lesson
 * (Routes.LEARNING_SESSION). Exam prep (Routes.LEARN_EXAM) is reached from a course's "Exam plan".
 */
fun NavGraphBuilder.learnGraph(
    navController: NavController,
    onSearch: () -> Unit,
    onNavigateBottomNav: (BottomNavDestination) -> Unit
) {
    composable(Routes.LEARN) {
        LearningHomeHost(
            onNavigateBottomNav = onNavigateBottomNav,
            actions = learningHomeActions(navController),
            asHome = false
        )
    }

    composable(Routes.LEARN_COURSE, arguments = listOf(navArgument("courseId") { type = NavType.StringType })) { entry ->
        CourseHost(
            courseId = entry.arguments?.getString("courseId").orEmpty(),
            actions = learningHomeActions(navController)
        )
    }

    composable(Routes.LEARNING_SESSION, arguments = listOf(navArgument("noteId") { type = NavType.StringType })) { entry ->
        LessonHost(
            noteId = entry.arguments?.getString("noteId").orEmpty(),
            actions = LessonActions(
                onBack = { navController.popBackStack() },
                onOpenNote = { navController.navigate(Routes.noteRoute(it)) },
                onListen = { navController.navigate(Routes.learnListenRoute(it)) },
                onPractise = { sessionId -> navController.navigate(Routes.learnPracticeRoute(sessionId = sessionId)) },
                onQuizConcept = { sessionId, conceptId -> navController.navigate(Routes.learnPracticeRoute(sessionId = sessionId, conceptId = conceptId)) },
                onOpenRecording = { meetingId, at -> navController.navigate(Routes.meetingDetailRoute(meetingId, at)) },
                onOpenPlans = { trigger -> navController.navigate(Routes.paywallRoute(trigger.name)) }
            )
        )
    }

    composable(Routes.LEARN_LISTEN, arguments = listOf(navArgument("noteId") { type = NavType.StringType })) { entry ->
        ListenHost(noteId = entry.arguments?.getString("noteId").orEmpty(), onClose = { navController.popBackStack() })
    }

    composable(
        Routes.LEARN_PRACTICE,
        arguments = listOf(
            navArgument("sessionId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("activityId") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("course") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("concept") { type = NavType.StringType; nullable = true; defaultValue = null }
        )
    ) { entry ->
        val session = entry.arguments?.getString("sessionId")
        val course = entry.arguments?.getString("course")
        val concept = entry.arguments?.getString("concept")
        val context = LocalContext.current
        // A course is a Learning notebook; practice keys courses by name.
        var courseName by remember(course) { mutableStateOf<String?>(null) }
        if (course != null) LaunchedEffect(course) {
            val app = context.applicationContext as Application
            courseName = NoteRepository(app, MeetMindDatabase.getInstance(app)).getNotebook(course)?.name ?: ""
        }
        if (course != null && courseName == null) return@composable
        val scope = when {
            session != null && concept != null -> PracticeScope.OneConcept(session, concept)
            session != null -> PracticeScope.OneSession(session)
            !courseName.isNullOrBlank() -> PracticeScope.OneCourse(courseName!!)
            else -> PracticeScope.AllDue
        }
        val cs = rememberCoroutineScope()
        PracticeHost(
            scope = scope,
            actions = PracticeActions(
                onClose = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
                onOpenLesson = { sessionId ->
                    cs.launch {
                        val noteId = StudyServices.forApp(context.applicationContext as Application).learning.getSession(sessionId)?.noteId
                        if (noteId != null) navController.navigate(Routes.learningSessionRoute(noteId)) {
                            popUpTo(Routes.LEARN_PRACTICE) { inclusive = true }
                        }
                    }
                },
                onMakeStudyWin = {
                    ShareRequests.pending = ShareRequest(
                        content = ShareCardContent(eyebrow = "Study win", text = "Another round done. It's sticking.", quoted = false),
                        theme = "a calm night sky with a few bright stars, celebrating learning"
                    )
                    navController.navigate(Routes.SHARE)
                }
            )
        )
    }
}

/** Where everything on the Learning home and a course page leads. Shared by the Learning place and the Learning home style. */
fun learningHomeActions(navController: NavController) = LearningHomeActions(
    onBack = { navController.popBackStack() },
    onOpenLesson = { noteId -> navController.navigate(Routes.learningSessionRoute(noteId)) },
    onOpenNote = { noteId -> navController.navigate(Routes.noteRoute(noteId)) },
    onStartRound = { navController.navigate(Routes.learnPracticeRoute()) },
    onPractiseCourse = { courseId -> navController.navigate(Routes.learnPracticeRoute(courseId = courseId)) },
    onOpenCourse = { courseId -> navController.navigate(Routes.learnCourseRoute(courseId)) },
    onOpenExamPlan = { courseId -> navController.navigate(Routes.learnExamRoute(courseId = courseId)) },
    onRecordLecture = { navController.navigate(Routes.recordTypeRoute(RecordingType.LECTURE)) },
    onImportFile = { navController.navigate(Routes.IMPORT) },
    onOpenAllIdeas = { navController.navigate(Routes.learnPracticeRoute()) },
    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
    onOpenPlans = { trigger -> navController.navigate(Routes.paywallRoute(trigger.name)) }
)
