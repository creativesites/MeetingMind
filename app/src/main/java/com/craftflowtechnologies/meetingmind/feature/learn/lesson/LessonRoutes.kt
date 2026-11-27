package com.craftflowtechnologies.meetingmind.feature.learn.lesson

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.collectAsState
import com.craftflowtechnologies.meetingmind.core.billing.Billing
import com.craftflowtechnologies.meetingmind.core.billing.PaywallTrigger
import com.craftflowtechnologies.meetingmind.core.learning.plansAction
import com.craftflowtechnologies.meetingmind.feature.learn.ListenViewModel
import com.craftflowtechnologies.meetingmind.feature.learn.ListenVoice
import com.craftflowtechnologies.meetingmind.feature.learn.LessonViewModel
import com.craftflowtechnologies.meetingmind.feature.learn.ui.listen.ListenScreen
import com.craftflowtechnologies.meetingmind.feature.learn.ui.ask.LessonAskUi
import com.craftflowtechnologies.meetingmind.feature.learn.ui.ask.askSuggestions
import com.craftflowtechnologies.meetingmind.feature.learn.ui.lesson.LessonScreen
import com.craftflowtechnologies.meetingmind.feature.learn.ui.gate.StudyGate
import com.craftflowtechnologies.meetingmind.feature.learn.ui.share.StudyPackShareSheet
import com.craftflowtechnologies.meetingmind.ai.automation.AutopilotPreferences
import com.craftflowtechnologies.meetingmind.core.billing.Capability
import com.craftflowtechnologies.meetingmind.feature.learn.ui.share.studyPackMarkdown
import com.craftflowtechnologies.meetingmind.feature.readtome.ReadToMeSheet

/**
 * Where the lesson's actions go. The navigation graph builds one of these and passes it to [LessonHost]; the
 * lesson itself never touches a NavController, so it can be placed by whoever wires the app.
 *
 * - [onBack]: leave the lesson (the back arrow).
 * - [onOpenNote] (noteId): the lecture's full note: the overflow's "Open full notes", and the note offer when the
 *   lesson cannot be made yet.
 * - [onListen] (noteId): Listen, the lecture replay with read-along ([ListenHost]).
 * - [onPractise] (sessionId): the practice round for the whole lecture: the bar's Practice pill, and "Quiz me on this"
 *   after an Ask answer.
 * - [onQuizConcept] (sessionId, conceptId): a practice round for one concept, from the concept sheet's "Quiz me on this".
 * - [onOpenRecording] (meetingId, startAtMs): the recording's detail, at a moment, when the lesson cannot play it in
 *   place (the "Listen and fix" line; null start for the top of the recording).
 * - [onCreateCircle]: the share sheet's "Create a study circle" (the host opens the Circle form).
 */
data class LessonActions(
    val onBack: () -> Unit,
    val onOpenNote: (noteId: String) -> Unit,
    val onListen: (noteId: String) -> Unit,
    val onPractise: (sessionId: String) -> Unit,
    val onQuizConcept: (sessionId: String, conceptId: String) -> Unit,
    val onOpenRecording: (meetingId: String, startAtMs: Long?) -> Unit,
    val onCreateCircle: () -> Unit = {},
    /** Opens Plans from the allowance panel (the trigger says why). The default does nothing. */
    val onOpenPlans: (PaywallTrigger) -> Unit = {},
    /** "Explain it aloud" for one concept of this lecture. */
    val onExplainAloud: (sessionId: String, conceptId: String) -> Unit = { _, _ -> }
)

/**
 * The Study lesson for one lecture: joins [LessonViewModel] to [LessonScreen], the concept sheet, the Ask sheet and
 * Read to Me. Everything that leaves the page goes through [actions].
 */
@Composable
fun LessonHost(noteId: String, actions: LessonActions) {
    val app = LocalContext.current.applicationContext as Application
    val vm: LessonViewModel = viewModel(key = "lesson-$noteId", factory = viewModelFactory { initializer { LessonViewModel(app, noteId) } })
    val state by vm.state.collectAsState()
    val explain by vm.explain.collectAsState()
    val reviewsPaused by vm.reviewsPaused.collectAsState()
    val conceptFlags by vm.conceptFlags.collectAsState()
    val askTurns by vm.askTurns.collectAsState()
    val askScope by vm.askScope.collectAsState()
    var openConcept by rememberSaveable { mutableStateOf<String?>(null) }
    var askOpen by rememberSaveable { mutableStateOf(false) }
    var readAloud by remember { mutableStateOf(false) }
    var sharePack by remember { mutableStateOf<String?>(null) }
    // "Study pack after every lecture" is the app's auto-run switch for the lecture recipe, a paid extra.
    val context = LocalContext.current
    var autoPackOn by rememberSaveable { mutableStateOf(AutopilotPreferences.isStudyPackAfterLectureEnabled(context)) }
    val autoPackOpen = StudyGate.open(Capability.AUTOMATION_RECIPES)

    val ask = if (!askOpen) null else LessonAskUi(
        turns = askTurns,
        scope = askScope,
        courseName = state.courseName,
        suggestions = askSuggestions(state.concepts.filter { conceptFlags[it.id]?.hidden != true }.map { it.name }),
        noteId = noteId,
        meetingId = state.meetingId
    )

    LessonScreen(
        state = state,
        explain = explain,
        openConceptId = openConcept,
        onBack = actions.onBack,
        onSeePlans = plansAction(Billing.gateway.configured) { actions.onOpenPlans(PaywallTrigger.USAGE_EXHAUSTED) },
        onPlayFrom = vm::playFrom,
        onTogglePlay = vm::togglePlay,
        onHearSummary = { readAloud = true },
        onOpenConcept = { openConcept = it },
        onCloseConcept = { openConcept = null; vm.clearExplain() },
        onExplain = { id, mode -> vm.explain(id, mode) },
        onQuizConcept = { conceptId ->
            openConcept = null
            if (state.sessionId.isNotBlank()) actions.onQuizConcept(state.sessionId, conceptId)
        },
        onReportConcept = { id -> vm.toggleConceptReported(id) },
        onListen = { actions.onListen(noteId) },
        onPractice = { if (state.sessionId.isNotBlank()) actions.onPractise(state.sessionId) },
        onAsk = { askOpen = true },
        onGenerate = vm::generate,
        onFixAudio = { state.meetingId?.let { actions.onOpenRecording(it, null) } },
        onExplainAloud = { conceptId -> if (state.sessionId.isNotBlank()) actions.onExplainAloud(state.sessionId, conceptId) },
        reviewsPaused = reviewsPaused,
        conceptFlags = conceptFlags,
        onOpenNote = { actions.onOpenNote(noteId) },
        onTogglePauseReviews = vm::toggleReviewsPaused,
        onShareStudyPack = { sharePack = studyPackMarkdown(state) },
        autoPackOn = autoPackOn,
        autoPackOpen = autoPackOpen,
        onToggleAutoPack = { on ->
            autoPackOn = on
            AutopilotPreferences.setStudyPackAfterLectureEnabled(context, on)
        },
        onOpenPremiumPlans = { actions.onOpenPlans(PaywallTrigger.PREMIUM_ACTION) },
        onToggleConceptHidden = { id -> vm.toggleConceptHidden(id) },
        onRenameConcept = vm::renameConcept,
        ask = ask,
        onAskQuestion = vm::ask,
        onAskGeneral = vm::askGenerally,
        onAskScope = vm::setAskScope,
        onAskQuiz = {
            askOpen = false
            if (state.sessionId.isNotBlank()) actions.onPractise(state.sessionId)
        },
        onCloseAsk = { askOpen = false }
    )
    sharePack?.let { markdown ->
        StudyPackShareSheet(
            title = state.title,
            markdown = markdown,
            onDismiss = { sharePack = null },
            onPublished = { circleName ->
                android.widget.Toast.makeText(context, "Shared to $circleName", android.widget.Toast.LENGTH_SHORT).show()
            },
            onNavigateCreateCircle = actions.onCreateCircle,
            onSeePlans = { actions.onOpenPlans(PaywallTrigger.PREMIUM_ACTION) }
        )
    }
    if (readAloud) ReadToMeSheet(noteId, onDismiss = { readAloud = false })
}

private val ListenSpeeds = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 0.75f)

/**
 * Listen: the lecture replay with read-along. [onClose] leaves it. Read to Me opens its sheet over the page.
 */
@Composable
fun ListenHost(noteId: String, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val vm: ListenViewModel = viewModel(key = "listen-$noteId", factory = viewModelFactory { initializer { ListenViewModel(app, noteId) } })
    val state by vm.state.collectAsState()
    var readAloud by remember { mutableStateOf(false) }
    ListenScreen(
        state = state,
        onClose = onClose,
        onTogglePlay = vm::togglePlayPause,
        onSeek = vm::seekTo,
        onSkip = vm::seekBy,
        onNextStressed = vm::nextStressedMoment,
        onSpeed = {
            val i = ListenSpeeds.indexOf(state.speed).let { if (it < 0) 0 else it }
            vm.setSpeed(ListenSpeeds[(i + 1) % ListenSpeeds.size])
        },
        onVoice = { voice -> if (voice == ListenVoice.READ_TO_ME) readAloud = true },
        onToggleStopAtSectionEnd = { vm.setStopAtSectionEnd(!state.stopAtSectionEnd) },
        onToggleQuizAfter = { vm.setQuizAfter(!state.quizAfter) }
    )
    if (readAloud) ReadToMeSheet(noteId, onDismiss = { readAloud = false })
}
