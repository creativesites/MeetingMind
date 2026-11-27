package com.craftflowtechnologies.meetingmind.feature.personal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.navigation.AppMenuButton
import com.craftflowtechnologies.meetingmind.feature.importing.LocalYouTubeSheet
import com.craftflowtechnologies.meetingmind.ui.theme.rememberSystemAnimationScale
import kotlinx.coroutines.launch

/** Where Personal's screens send the person. Kept in one place so the garden and its lists agree. */
data class PersonalNav(
    val onAnswerAloud: (question: String) -> Unit,
    val onOpenNote: (String) -> Unit,
    val onOpenSeedJar: () -> Unit,
    val onCaptureIdea: () -> Unit,
    val onOpenSky: () -> Unit,
    val onOpenPractices: () -> Unit,
    val onBack: () -> Unit
)

/** The Plant sheet's opening: blank, from a note's line, or a practice planted again. */
private data class PlantDraft(val title: String, val source: PlantSource?)

/** Personal's home, "Your garden", with its view model and sheets, for the nav graph. */
@Composable
fun PersonalHost(nav: PersonalNav, vm: PersonalViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sheets = rememberPersonalSheets()
    val scope = rememberCoroutineScope()
    val youTube = LocalYouTubeSheet.current
    PersonalHomeScreen(
        state = state,
        animate = rememberSystemAnimationScale() > 0f,
        onAnswerAloud = { nav.onAnswerAloud(state.question) },
        onWriteAnswer = { scope.launch { nav.onOpenNote(vm.startWrittenAnswer(state.question)) } },
        onOpenPlant = { sheets.openId = it },
        onPlantNew = { sheets.plant("") },
        onOpenSeedJar = nav.onOpenSeedJar,
        onCaptureIdea = nav.onCaptureIdea,
        onOpenSky = nav.onOpenSky,
        onOpenPractices = nav.onOpenPractices,
        onOpenNote = nav.onOpenNote,
        onPlantSeed = { noteId, noteTitle, line -> sheets.plant(line, PlantSource(noteId, noteTitle)) },
        onTalkNotes = { youTube.open(NotebookSpace.PERSONAL) },
        menuButton = { tint -> PersonalMenuButton(tint) }
    )
    PersonalSheetsHost(sheets, state, vm, nav.onOpenNote)
}

/** Every practice, growing, bloomed and let go, with the same sheets as the garden. */
@Composable
fun PracticesHost(nav: PersonalNav, vm: PersonalViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sheets = rememberPersonalSheets()
    PracticesScreen(
        state = state,
        onBack = nav.onBack,
        onOpenPlant = { sheets.openId = it },
        onPlantNew = { sheets.plant("") },
        onPlantAgain = { item -> sheets.plant(item.title, item.sourceNoteId?.let { PlantSource(it, item.sourceTitle ?: "") }) }
    )
    PersonalSheetsHost(sheets, state, vm, nav.onOpenNote)
}

/** The app menu, opened on Personal's group first, sized to sit in the garden's glass button. */
@Composable
fun PersonalMenuButton(tint: Color, modifier: Modifier = Modifier) {
    AppMenuButton(focus = NotebookSpace.PERSONAL, modifier = modifier, tint = tint)
}

/** Which sheet is open. The open and adjusting plant survive rotation; a half-typed draft does not. */
private class PersonalSheets(open: MutableState<String?>, adjust: MutableState<String?>) {
    var draft by mutableStateOf<PlantDraft?>(null)
    var openId by open
    var adjustId by adjust
    fun plant(title: String, source: PlantSource? = null) { draft = PlantDraft(title, source) }
}

@Composable
private fun rememberPersonalSheets(): PersonalSheets {
    val open = rememberSaveable { mutableStateOf<String?>(null) }
    val adjust = rememberSaveable { mutableStateOf<String?>(null) }
    return remember { PersonalSheets(open, adjust) }
}

@Composable
private fun PersonalSheetsHost(sheets: PersonalSheets, state: PersonalUiState, vm: PersonalViewModel, onOpenNote: (String) -> Unit) {
    sheets.draft?.let { d ->
        PlantSheet(
            initialTitle = d.title, source = d.source,
            onDismiss = { sheets.draft = null },
            onPlant = { vm.plant(it); sheets.draft = null }
        )
    }
    state.plants.firstOrNull { it.id == sheets.adjustId }?.let { plant ->
        AdjustSheet(plant, onDismiss = { sheets.adjustId = null }, onSave = { r, n -> vm.adjust(plant.id, r, n); sheets.adjustId = null })
        return
    }
    state.plants.firstOrNull { it.id == sheets.openId }?.let { plant ->
        PracticeSheet(
            plant = plant,
            onDismiss = { sheets.openId = null },
            onTend = { vm.tend(plant.id, it) },
            onKeepGoing = { vm.keepGoing(plant.id) },
            onBloom = { vm.bloom(plant.id) },
            onLetGo = { vm.letGo(plant.id); sheets.openId = null },
            onOpenSource = { sheets.openId = null; onOpenNote(it) },
            onAdjust = { sheets.openId = null; sheets.adjustId = plant.id }
        )
    }
}
