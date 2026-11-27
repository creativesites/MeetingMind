package com.craftflowtechnologies.meetingmind.feature.personal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.ui.theme.rememberSystemAnimationScale
import kotlinx.coroutines.launch

/** Personal's screen with its view model and sheets, for the nav graph. */
@Composable
fun PersonalHost(
    onAnswerAloud: (question: String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenSeedJar: () -> Unit,
    onCaptureIdea: () -> Unit,
    vm: PersonalViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val animate = rememberSystemAnimationScale() > 0f
    val scope = rememberCoroutineScope()
    var planting by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    PersonalHomeScreen(
        state = state,
        animate = animate,
        onAnswerAloud = { onAnswerAloud(state.question) },
        onWriteAnswer = { scope.launch { onOpenNote(vm.startWrittenAnswer(state.question)) } },
        onOpenPlant = { openId = it },
        onPlantNew = { planting = true },
        onOpenSeedJar = onOpenSeedJar,
        onCaptureIdea = onCaptureIdea
    )

    if (planting) {
        PlantSheet(initialTitle = "", onDismiss = { planting = false }, onPlant = { vm.plant(it); planting = false })
    }
    state.plants.firstOrNull { it.id == openId }?.let { plant ->
        PracticeSheet(
            plant = plant,
            onDismiss = { openId = null },
            onTend = { vm.tend(plant.id, it) },
            onKeepGoing = { vm.keepGoing(plant.id) },
            onBloom = { vm.bloom(plant.id) },
            onLetGo = { vm.letGo(plant.id); openId = null },
            onOpenSource = { openId = null; onOpenNote(it) }
        )
    }
}
