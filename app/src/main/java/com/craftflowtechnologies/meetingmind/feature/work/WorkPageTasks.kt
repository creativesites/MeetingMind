package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.craftflowtechnologies.meetingmind.feature.tasks.TaskScope
import com.craftflowtechnologies.meetingmind.feature.tasks.TasksPane

/**
 * Work › Tasks is the app-wide task manager, scoped to Work (Mine, Waiting on, quick add, grouping by due date).
 * The screen lives in feature/tasks/TasksPane.kt; this file only names the scope the Work pane shows.
 */
@Composable
internal fun WorkTasksPane(
    onOpenTask: (String) -> Unit,
    onOpenList: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TasksPane(scope = TaskScope.WORK, onOpenTask = onOpenTask, onOpenList = onOpenList, modifier = modifier)
}
