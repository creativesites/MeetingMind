package com.craftflowtechnologies.meetingmind.feature.personal.seeds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Where the seed jar gets its data: wires [SeedJarViewModel] to [SeedJarScreen]. The route is
 * wired by the main session; this only needs the three ways out.
 */
@Composable
fun SeedJarHost(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onCaptureIdea: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: SeedJarViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(SeedTab.IDEAS) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    SeedJarScreen(
        state = state.copy(tab = tab, selectedIdeaId = selectedId),
        onBack = onBack,
        onOpenNote = onOpenNote,
        onCaptureIdea = onCaptureIdea,
        onTab = { tab = it },
        onSelectIdea = { selectedId = it },
        onMove = { noteId, space -> viewModel.moveTo(noteId, space) },
        onKeep = { viewModel.keepHere(it) },
        onMakeTask = { viewModel.makeTask(it) },
        modifier = modifier
    )
}
