package com.craftflowtechnologies.meetingmind.feature.learn.practice

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeScope
import com.craftflowtechnologies.meetingmind.feature.learn.PracticeViewModel
import com.craftflowtechnologies.meetingmind.feature.learn.ui.common.SkyView
import com.craftflowtechnologies.meetingmind.feature.learn.ui.practice.PracticeScreen

/**
 * What a practice round asks of the screen around it. Each callback is one thing the host does
 * with the learner's next step. Defaults do nothing, so a test or preview passes only what it reads.
 */
data class PracticeActions(
    /** The learner left the round from its header (the round is saved item by item). */
    val onClose: () -> Unit = {},
    /** "Done for today" on the round-complete screen. */
    val onDone: () -> Unit = {},
    /** The round's next step, "Revisit inhibition": open that lesson. Given the learning session id. */
    val onOpenLesson: (sessionId: String) -> Unit = {},
    /** "Make a card" on the round-complete screen: the caller builds the study-win card. */
    val onMakeStudyWin: () -> Unit = {}
)

/**
 * A practice round for [scope], drawn with [PracticeScreen]. Creates the [PracticeViewModel] (one per scope,
 * so leaving and coming back to the same scope keeps its own round), plays the source clip of a
 * "Hear your lecturer" pill, and draws the sky on the round-complete screen from the round's result. The sky
 * is internal: it is always the round's own sky, not something the caller passes in.
 */
@Composable
fun PracticeHost(
    scope: PracticeScope,
    actions: PracticeActions,
    modifier: Modifier = Modifier
) {
    val app = LocalContext.current.applicationContext as Application
    val vm: PracticeViewModel = viewModel(
        key = "study-practice-$scope",
        factory = viewModelFactory { initializer { PracticeViewModel(app, scope) } }
    )
    val state by vm.state.collectAsState()
    PracticeScreen(
        state = state,
        onAnswerChoice = vm::answerChoice,
        onAnswer = vm::answer,
        onNotSure = vm::notSure,
        onReveal = vm::reveal,
        onRate = vm::rate,
        onContinue = vm::continueNext,
        onClose = actions.onClose,
        onPlaySource = vm::playSource,
        onDone = actions.onDone,
        onOpenNextStep = { step -> actions.onOpenLesson(step.sessionId) },
        onMakeCard = actions.onMakeStudyWin,
        sky = { ignite ->
            state.result?.let { result -> SkyView(sky = result.sky, ignite = ignite) }
        },
        modifier = modifier
    )
}
