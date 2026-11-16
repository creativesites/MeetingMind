package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.craftflowtechnologies.meetingmind.core.ui.mm.ScreenHeader
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/** The old Work list tabs. They now live in the Work panes: tasks, Projects (People), and Decisions. */
enum class WorkTab(val label: String) { MINE("Mine"), WAITING("Waiting on"), DECISIONS("Decisions"), QUESTIONS("Open questions"), RISKS("Risks"), PEOPLE("People") }

/**
 * A thin wrapper kept so existing routes still open the right pane: Mine and Waiting on show the Work tasks pane,
 * People shows Work › Projects on its People toggle, and the decision tabs show Work › Decisions on their chip.
 * The content itself lives in [WorkDecisionsPane], [WorkProjectsPane] and [WorkTasksPane].
 */
@Composable
fun WorkAllScreen(
    viewModel: WorkViewModel,
    initial: WorkTab,
    onNavigateBack: () -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenPerson: (String) -> Unit
) {
    val tasks by viewModel.tasks.collectAsState()
    var editing by remember { mutableStateOf<WorkTask?>(null) }

    Scaffold(containerColor = MM.colors.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding().testTag("work_all")) {
            ScreenHeader(
                title = "Work",
                modifier = Modifier.padding(horizontal = MM.space.s),
                leading = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = MM.colors.ink) } }
            )
            Box(Modifier.fillMaxSize()) {
                when (initial) {
                    WorkTab.MINE, WorkTab.WAITING -> WorkTasksPane(
                        onOpenTask = { id -> tasks.firstOrNull { it.id == id }?.let { editing = it } },
                        onOpenList = { },
                        modifier = Modifier.fillMaxSize()
                    )
                    WorkTab.PEOPLE -> WorkProjectsPane(
                        viewModel = viewModel,
                        projectWord = viewModel.settings.value.terms.project,
                        initialTab = ProjectsTab.PEOPLE,
                        now = System.currentTimeMillis(),
                        onOpenProject = { },
                        onOpenPerson = onOpenPerson,
                        onNewProject = { },
                        modifier = Modifier.fillMaxSize()
                    )
                    WorkTab.DECISIONS -> WorkDecisionsPane(viewModel, onOpenMeeting, Modifier.fillMaxSize(), DecisionChip.DECISIONS)
                    WorkTab.QUESTIONS -> WorkDecisionsPane(viewModel, onOpenMeeting, Modifier.fillMaxSize(), DecisionChip.QUESTIONS)
                    WorkTab.RISKS -> WorkDecisionsPane(viewModel, onOpenMeeting, Modifier.fillMaxSize(), DecisionChip.RISKS)
                }
            }
        }
    }
    editing?.let { t ->
        WorkTaskSheet(t, viewModel, onDismiss = { editing = null }, onOpenSource = {
            t.meetingId?.let { m -> editing = null; onOpenMeeting(m, t.startMs) } ?: t.noteId?.let { n -> editing = null; onOpenNote(n) }
        })
    }
}
