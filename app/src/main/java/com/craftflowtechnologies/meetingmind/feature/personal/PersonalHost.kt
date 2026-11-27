package com.craftflowtechnologies.meetingmind.feature.personal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.feature.personal.garden.GardenPlace
import com.craftflowtechnologies.meetingmind.ui.theme.rememberSystemAnimationScale
import kotlinx.coroutines.launch

/**
 * Personal's screen with its view model and sheets, for the nav graph.
 *
 * ## How the Sanctuary merges into "Your garden"
 *
 * One Personal, one look: the garden. Every Sanctuary capability keeps its features and its data;
 * only the front door changes. The places are listed in [GardenPlace].
 *
 * - **Practices are plants.** A practice you keep is a plant: "Plant it" makes one, tending it logs
 *   the day, and it grows by kept days, rests when untended and never withers. Nothing shows a streak.
 * - **Routines are rituals, a different thing.** A morning or evening routine is a guided sequence you
 *   play aloud in the routine player. It lives under "Rituals", not on the garden bed, so a plant is
 *   never confused with a ritual.
 * - **Journeys and challenges are seed packets.** Guided journeys and challenges sit under "Journeys &
 *   challenges" and are planted from there; a planted packet becomes an enrollment with its check-ins.
 * - **Living journal and calendar are the journal path.** Day pages, volumes and the mood calendar open
 *   from "Living journal". "Light the lantern" still records today's question as a journal entry.
 * - **Idea studio is the seed jar.** Ideas are kept in the seed jar on the garden home and grown from there.
 * - **Memory vault and time capsules are keepsakes.** Sealed notes and milestones open from "Keepsakes".
 * - **Daily plan and intention are today's question.** The question by the lantern is the day's intention.
 * - **Sanctuary lock and vault sync are settings.** "Privacy & sync" holds the lock, the screenshot shield,
 *   the Obsidian folder and the Day One import. The lock still covers every Personal screen.
 *
 * Calm and generic by default: no streaks, no nudges the person did not set, and no plan gates on
 * any capability (see `agents/PRODUCT.md`, "Paid plan scope for 1.0").
 *
 * The places still open the Sanctuary tab screen, which keeps its own look for now (see the STATUS note).
 */
@Composable
fun PersonalHost(
    onAnswerAloud: (question: String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenRoute: (String) -> Unit,
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
        onOpenPlace = { onOpenRoute(it.route) },
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
