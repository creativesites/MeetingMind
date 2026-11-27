package com.craftflowtechnologies.meetingmind.feature.learn.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.repository.LearningRepository
import com.craftflowtechnologies.meetingmind.core.ui.AppBottomNavigationBar
import com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination
import com.craftflowtechnologies.meetingmind.core.ui.rememberBarSlots
import com.craftflowtechnologies.meetingmind.core.navigation.BarSlot
import com.craftflowtechnologies.meetingmind.feature.importing.LocalYouTubeSheet
import com.craftflowtechnologies.meetingmind.feature.importing.YouTubeSheetController
import com.craftflowtechnologies.meetingmind.feature.learn.StudyHomeViewModel
import com.craftflowtechnologies.meetingmind.feature.learn.ui.course.CourseScreen
import com.craftflowtechnologies.meetingmind.feature.learn.ui.home.LearningHomeScreen
import com.craftflowtechnologies.meetingmind.feature.learn.ui.home.NewSessionSheet
import com.craftflowtechnologies.meetingmind.feature.learn.ui.home.StartWay
import com.craftflowtechnologies.meetingmind.feature.learn.ui.home.dayLine
import com.craftflowtechnologies.meetingmind.feature.learning.LearnViewModel
import com.craftflowtechnologies.meetingmind.feature.learning.CourseChoice
import com.craftflowtechnologies.meetingmind.feature.learning.space.AskLearningSheet
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Where the Learning home and course page send the learner. The host (MainActivity) owns the
 * routes; the screens only call these. Each callback is called after the host has done the
 * Learning work for it (a recording is prepared for its course, a note is created, the YouTube
 * sheet is open), so the route only has to navigate.
 *
 * - [onBack]: the course page's back arrow. Pop the back stack.
 * - [onOpenLesson] (noteId): open a lecture's lesson page (Study's STUDY_LESSON route).
 * - [onOpenNote] (noteId): open a note in the note editor. Used for plain study notes, and for the
 *   new note a "Write a study note" creates (the host creates it and passes its id).
 * - [onStartRound]: practise every card due today (Study's all-due practice route). Also fired by
 *   Ask Learning's "practise" line.
 * - [onPractiseCourse] (courseId): practise one course's cards.
 * - [onOpenCourse] (courseId): open the course page (route to [CourseHost]).
 * - [onOpenExamPlan] (courseId): open the exam plan for the course (Release 2 surface).
 * - [onRecordLecture]: the recording screen. The host has already attached the course chosen in
 *   the new-session sheet, or none from the home tile.
 * - [onImportFile]: the import screen (audio, video or PDF).
 * - [onOpenYouTube]: called after the host has opened the YouTube link sheet. Usually a no-op.
 * - [onOpenAllIdeas]: the sky's "All N ideas" link. Opens the practice of every idea.
 * - [onOpenSettings]: the avatar. Opens Settings.
 *
 * The app menu and Ask Learning need no callback: the menu is [com.craftflowtechnologies.meetingmind.core.navigation.AppMenuButton]
 * and Ask Learning opens its own sheet.
 */
data class LearningHomeActions(
    val onBack: () -> Unit = {},
    val onOpenLesson: (noteId: String) -> Unit,
    val onOpenNote: (noteId: String) -> Unit,
    val onStartRound: () -> Unit,
    val onPractiseCourse: (courseId: String) -> Unit,
    val onOpenCourse: (courseId: String) -> Unit,
    val onOpenExamPlan: (courseId: String) -> Unit,
    val onRecordLecture: () -> Unit,
    val onImportFile: () -> Unit,
    val onOpenYouTube: () -> Unit = {},
    val onOpenAllIdeas: () -> Unit = {},
    val onOpenSettings: () -> Unit = {}
)

/**
 * The Learning home, as the app's Home ([asHome] true) or as the Learning destination (false).
 * Draws the global bar when [onNavigateBottomNav] is set, with Home current, or Learning current
 * when the bar has a Learning place. It draws no bar of its own and no tabs.
 */
@Composable
fun LearningHomeHost(
    onNavigateBottomNav: ((BottomNavDestination) -> Unit)?,
    actions: LearningHomeActions,
    asHome: Boolean = true
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val activity = context as ComponentActivity
    // The Learning view model the rest of the space shares (Ask, recording, notes, courses).
    val learn: LearnViewModel = viewModel(viewModelStoreOwner = activity)
    val study: StudyHomeViewModel = viewModel(
        key = "learning-home-study",
        factory = viewModelFactory { initializer { StudyHomeViewModel(app) } }
    )
    val home: LearningHomeViewModel = viewModel(
        key = "learning-home",
        factory = viewModelFactory {
            initializer {
                LearningHomeViewModel(study.state, RepositoryLearningHomeSource(LearningRepository(app)))
            }
        }
    )
    LaunchedEffect(Unit) { study.refresh() }

    val state by home.state.collectAsState()
    val courses by learn.courses.collectAsState()
    val concepts by learn.allConcepts.collectAsState()
    val youTube = LocalYouTubeSheet.current
    val slots = rememberBarSlots()
    var asking by rememberSaveable { mutableStateOf(false) }
    var newSession by rememberSaveable { mutableStateOf(false) }
    val start = remember(learn, actions, youTube) { startNewSession(learn, actions, youTube) }
    val dayText = remember {
        dayLine(SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()), Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    }
    val current = when {
        asHome -> BottomNavDestination.HOME
        BarSlot.LEARN in slots -> BottomNavDestination.LEARN
        else -> BottomNavDestination.NONE
    }

    Scaffold(
        containerColor = MM.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (onNavigateBottomNav != null) AppBottomNavigationBar(
                current = current,
                onNavigate = { if (it != current) onNavigateBottomNav(it) }
            )
        }
    ) { padding ->
        LearningHomeScreen(
            state = state,
            dayLine = dayText,
            onAsk = { learn.openAsk(null); asking = true },
            onStartRound = actions.onStartRound,
            onPractiseAnyway = actions.onStartRound,
            onOpenLesson = actions.onOpenLesson,
            onOpenNote = actions.onOpenNote,
            onOpenCourse = actions.onOpenCourse,
            onAddCourse = { newSession = true },
            onRecordLecture = { start(StartWay.RECORD, CourseChoice.None) },
            onOpenYouTube = { start(StartWay.VIDEO, CourseChoice.None) },
            onImportFile = { start(StartWay.IMPORT, CourseChoice.None) },
            onWriteStudyNote = { start(StartWay.WRITE, CourseChoice.None) },
            onOpenAllIdeas = actions.onOpenAllIdeas,
            onOpenSettings = actions.onOpenSettings,
            modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding()
        )
    }

    if (asking) {
        AskLearningSheet(
            viewModel = learn,
            sessionId = null,
            hasCourse = false,
            concepts = concepts,
            onDismiss = { asking = false },
            onStartPractice = actions.onStartRound,
            onShowInTranscript = null
        )
    }
    if (newSession) {
        NewSessionSheet(
            courses = courses,
            presetCourseId = null,
            onDismiss = { newSession = false },
            onStart = { way, choice -> newSession = false; start(way, choice) }
        )
    }
}

/** The course page. Back is [LearningHomeActions.onBack]; the bar is not drawn here. */
@Composable
fun CourseHost(courseId: String, actions: LearningHomeActions) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val activity = context as ComponentActivity
    val learn: LearnViewModel = viewModel(viewModelStoreOwner = activity)
    val youTube = LocalYouTubeSheet.current
    val vm: CourseViewModel = viewModel(
        key = "course-$courseId",
        factory = viewModelFactory {
            initializer { CourseViewModel(courseId, RepositoryLearningHomeSource(LearningRepository(app))) }
        }
    )
    val state by vm.state.collectAsState()
    val courses by learn.courses.collectAsState()
    var newSession by rememberSaveable { mutableStateOf(false) }
    val start = remember(learn, actions, youTube) { startNewSession(learn, actions, youTube) }

    CourseScreen(
        state = state,
        onBack = actions.onBack,
        onPractise = { actions.onPractiseCourse(courseId) },
        onOpenExamPlan = { actions.onOpenExamPlan(courseId) },
        onOpenLecture = { lecture ->
            if (lecture.isLesson) actions.onOpenLesson(lecture.noteId) else actions.onOpenNote(lecture.noteId)
        },
        onAddLecture = { newSession = true },
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    )

    if (newSession) {
        NewSessionSheet(
            courses = courses,
            presetCourseId = courseId,
            onDismiss = { newSession = false },
            onStart = { way, choice -> newSession = false; start(way, choice) }
        )
    }
}

/**
 * Runs the Learning work for a way into a study session, then calls the route. The course choice
 * comes from the new-session sheet (or [CourseChoice.None] from a home tile).
 */
internal fun startNewSession(
    learn: LearnViewModel,
    actions: LearningHomeActions,
    youTube: YouTubeSheetController
): (StartWay, CourseChoice) -> Unit = { way, choice ->
    when (way) {
        StartWay.RECORD -> learn.prepareRecording(choice) { actions.onRecordLecture() }
        StartWay.WRITE -> learn.createNotesSession(choice) { noteId -> actions.onOpenNote(noteId) }
        StartWay.IMPORT -> actions.onImportFile()
        StartWay.VIDEO -> {
            // The link sheet is global and is not told the course; a session from it lands in no course yet.
            youTube.open(from = NotebookSpace.LEARNING)
            actions.onOpenYouTube()
        }
    }
}
