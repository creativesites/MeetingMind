package com.craftflowtechnologies.meetingmind.feature.learn

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.craftflowtechnologies.meetingmind.core.learning.StudyServices
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.share.ShareCardContent
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.LessonActions
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.LessonHost
import com.craftflowtechnologies.meetingmind.feature.learn.lesson.ListenHost
import com.craftflowtechnologies.meetingmind.feature.learn.ui.common.SkyView
import com.craftflowtechnologies.meetingmind.feature.learn.ui.home.StudyHomeScreen
import com.craftflowtechnologies.meetingmind.feature.learn.ui.practice.PracticeScreen
import com.craftflowtechnologies.meetingmind.feature.navigation.Routes
import com.craftflowtechnologies.meetingmind.feature.share.ShareRequest
import com.craftflowtechnologies.meetingmind.feature.share.ShareRequests
import kotlinx.coroutines.launch

/**
 * The Study routes (docs/mvp/STUDY_AUDIT.md §3): home, lesson, listen and practice. Screens are
 * stateless; this file joins each to its view model and to navigation. The lesson and listen hosts
 * are in `feature/learn/lesson/LessonRoutes.kt`.
 */
fun NavGraphBuilder.learnGraph(navController: NavController, onSearch: () -> Unit) {

    composable(Routes.STUDY_HOME) {
        val vm: StudyHomeViewModel = viewModel()
        val state by vm.state.collectAsState()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.refresh() }
        StudyHomeScreen(
            state = state,
            onStartRound = { navController.navigate(Routes.studyPracticeRoute()) },
            onPractiseAnyway = { navController.navigate(Routes.studyPracticeRoute()) },
            onLater = { navController.popBackStack() },
            onOpenLesson = { sessionId ->
                scope.launch {
                    val noteId = StudyServices.forApp(context.applicationContext as Application).learning.getSession(sessionId)?.noteId
                    if (noteId != null) navController.navigate(Routes.studyLessonRoute(noteId))
                }
            },
            onOpenCourse = { navController.navigate(Routes.NOTES) },
            onOpenAllIdeas = { navController.navigate(Routes.studyPracticeRoute()) },
            onSearch = onSearch,
            onRecordLecture = { navController.navigate(Routes.recordTypeRoute(RecordingType.LECTURE)) },
            onImportLecture = { navController.navigate(Routes.IMPORT) }
        )
    }

    composable(Routes.STUDY_LESSON, arguments = listOf(navArgument("noteId") { type = NavType.StringType })) { entry ->
        val noteId = entry.arguments?.getString("noteId").orEmpty()
        LessonHost(
            noteId = noteId,
            actions = LessonActions(
                onBack = { navController.popBackStack() },
                onOpenNote = { id -> navController.navigate(Routes.noteRoute(id)) },
                onListen = { id -> navController.navigate(Routes.studyListenRoute(id)) },
                onPractise = { sessionId -> navController.navigate(Routes.studyPracticeRoute(sessionId)) },
                onQuizConcept = { sessionId, conceptId -> navController.navigate(Routes.studyPracticeRoute(sessionId, conceptId)) },
                onOpenRecording = { meetingId, startMs -> navController.navigate(Routes.meetingDetailRoute(meetingId, startMs)) }
            )
        )
    }

    composable(Routes.STUDY_LISTEN, arguments = listOf(navArgument("noteId") { type = NavType.StringType })) { entry ->
        val noteId = entry.arguments?.getString("noteId").orEmpty()
        ListenHost(noteId = noteId, onClose = { navController.popBackStack() })
    }

    composable(
        Routes.STUDY_PRACTICE,
        arguments = listOf(
            navArgument("session") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("concept") { type = NavType.StringType; nullable = true; defaultValue = null }
        )
    ) { entry ->
        val session = entry.arguments?.getString("session")
        val concept = entry.arguments?.getString("concept")
        val scope = when {
            session != null && concept != null -> PracticeScope.OneConcept(session, concept)
            session != null -> PracticeScope.OneSession(session)
            else -> PracticeScope.AllDue
        }
        val app = LocalContext.current.applicationContext as Application
        val vm: PracticeViewModel = viewModel(key = "practice-$session-$concept", factory = viewModelFactory { initializer { PracticeViewModel(app, scope) } })
        val state by vm.state.collectAsState()
        val context = LocalContext.current
        val cs = rememberCoroutineScope()
        PracticeScreen(
            state = state,
            onAnswerChoice = vm::answerChoice,
            onNotSure = vm::notSure,
            onReveal = vm::reveal,
            onRate = vm::rate,
            onContinue = vm::continueNext,
            onClose = { navController.popBackStack() },
            onPlaySource = vm::playSource,
            onDone = { navController.popBackStack() },
            onOpenNextStep = { step ->
                cs.launch {
                    val noteId = StudyServices.forApp(context.applicationContext as Application).learning.getSession(step.sessionId)?.noteId
                    if (noteId != null) navController.navigate(Routes.studyLessonRoute(noteId)) { popUpTo(Routes.STUDY_PRACTICE) { inclusive = true } }
                }
            },
            onMakeCard = {
                val r = state.result ?: return@PracticeScreen
                ShareRequests.pending = ShareRequest(
                    content = ShareCardContent(
                        eyebrow = "Study win",
                        text = "${r.firstTryCorrect} of ${r.total} on the first try" +
                            (if (r.newStars.isNotEmpty()) ". New today: ${r.newStars.joinToString(", ")}." else "."),
                        quoted = false
                    ),
                    theme = "a calm night sky with a few bright stars, celebrating learning"
                )
                navController.navigate(Routes.SHARE)
            },
            sky = { ignite ->
                val result = state.result
                if (result != null) {
                    val ids = result.sky.stars.filter { it.label in ignite || it.conceptId in ignite }.map { it.conceptId }.toSet()
                    SkyView(result.sky, ignite = ids)
                }
            }
        )
    }
}
