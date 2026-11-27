package com.craftflowtechnologies.meetingmind.feature.learn.explain

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.craftflowtechnologies.meetingmind.core.audio.PlaybackController
import com.craftflowtechnologies.meetingmind.core.learning.StudyServices
import com.craftflowtechnologies.meetingmind.feature.learn.RecallButton
import com.craftflowtechnologies.meetingmind.feature.learn.ui.explain.ExplainAloudScreen
import kotlinx.coroutines.launch
import java.io.File

/**
 * "Explain it aloud" for one concept, wired to the app: loads the concept and its lecture, asks for the
 * microphone at the moment of use, and hands the screen its callbacks. [onDone] leaves the round.
 */
@Composable
fun ExplainAloudHost(sessionId: String, conceptId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val services = remember(app) { StudyServices.forApp(app) }
    val load by produceState<ExplainLoad>(ExplainLoad.Loading, sessionId, conceptId) {
        value = loadExplainContent(services, sessionId, conceptId)?.let { ExplainLoad.Ready(it) } ?: ExplainLoad.Missing
    }
    val loaded = (load as? ExplainLoad.Ready)?.content
    if (loaded == null) {
        ExplainAloudScreen(state = null, missing = load is ExplainLoad.Missing, onClose = onDone)
        return
    }

    val scope = rememberCoroutineScope()
    val vm: ExplainAloudViewModel = viewModel(
        key = "explain-$sessionId-$conceptId",
        factory = viewModelFactory {
            initializer {
                ExplainAloudViewModel(
                    content = loaded,
                    speech = AndroidExplainSpeech(app),
                    assessor = EngineTeachBackAssessor(app),
                    store = StudyExplainAttemptStore(services),
                    playAt = { startMs ->
                        scope.launch { playLecture(app, services, loaded.meetingId, loaded.lectureTitle, startMs) }
                    }
                )
            }
        }
    )
    val state by vm.state.collectAsState()

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onMicPermissionResult(granted)
    }
    fun micGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    ExplainAloudScreen(
        state = state,
        onClose = onDone,
        onStart = { vm.onStartTapped(micGranted()) },
        onAllowMicrophone = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
        onTypeInstead = vm::onTypeInstead,
        onStop = vm::onStop,
        onStartOver = vm::onStartOver,
        onHint = vm::onHint,
        onSubmitTyped = vm::onSubmitTyped,
        onTryAgain = vm::onTryAgain,
        onRate = { button: RecallButton -> vm.onRate(button) },
        onPlayIdea = vm::onPlayIdea,
        onDone = onDone
    )
}

private sealed interface ExplainLoad {
    data object Loading : ExplainLoad
    data object Missing : ExplainLoad
    data class Ready(val content: ExplainContent) : ExplainLoad
}

/** The concept, its lecture and the subject line, or null when the concept no longer exists. */
suspend fun loadExplainContent(services: StudyServices, sessionId: String, conceptId: String): ExplainContent? {
    val session = services.learning.getSession(sessionId) ?: return null
    val concept = services.learning.getConcepts(sessionId).firstOrNull { it.id == conceptId } ?: return null
    val subject = listOfNotNull(session.courseName?.takeIf { it.isNotBlank() }, session.title.takeIf { it.isNotBlank() })
        .joinToString(" · ")
    return ExplainContent(
        sessionId = sessionId,
        concept = concept,
        subjectLine = subject,
        meetingId = session.meetingId ?: services.learning.meetingIdForNote(session.noteId),
        lectureTitle = session.title
    )
}

/** Plays the lecture recording from [startMs]. Quietly does nothing when there is no recording file. */
private suspend fun playLecture(app: Application, services: StudyServices, meetingId: String?, title: String, startMs: Long) {
    val id = meetingId ?: return
    val path = services.meetings.getMeetingByIdDirect(id)?.audioFilePath ?: return
    val file = File(path).takeIf { it.exists() } ?: return
    PlaybackController.playAt(app, id, title, file, startMs)
}
