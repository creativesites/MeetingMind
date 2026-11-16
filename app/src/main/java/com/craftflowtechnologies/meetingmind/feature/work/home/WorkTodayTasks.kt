package com.craftflowtechnologies.meetingmind.feature.work.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.ui.work.SwipeActionRow
import com.craftflowtechnologies.meetingmind.core.ui.work.TaskCheck
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.feature.today.InterFamily
import com.craftflowtechnologies.meetingmind.feature.work.EmptyLine
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import java.text.DateFormat

/** Most today's tasks the card lists before "All N". */
const val TODAY_TASKS_CAP = 5

/** Most waiting-on rows the card lists. */
const val WAITING_ROWS_CAP = 3

/**
 * Today's tasks: what is due today or overdue, each with its check and a swipe (right to complete,
 * left to schedule). Below it, what you are waiting on, with Nudge. "All" opens Tasks.
 */
@Composable
internal fun WorkTodayTasksCard(
    today: List<WorkTask>,
    totalMine: Int,
    waiting: List<WorkTask>,
    now: Long,
    timeFormat: DateFormat,
    contextOf: (WorkTask) -> String?,
    onToggle: (WorkTask) -> Unit,
    onOpen: (WorkTask) -> Unit,
    onNudge: (WorkTask) -> Unit,
    onAll: () -> Unit,
    index: Int
) {
    WorkListCard(
        title = "Today's tasks",
        trailing = if (totalMine > 0) "All $totalMine" else null,
        onTrailing = if (totalMine > 0) onAll else null,
        tag = "work_home_tasks",
        index = index
    ) {
        if (today.isEmpty()) EmptyLine("Nothing due today. What you agree to in meetings lands here with its date.")
        today.take(TODAY_TASKS_CAP).forEach { t ->
            TodayTaskRow(
                task = t, now = now, timeFormat = timeFormat, context = contextOf(t),
                onToggle = { onToggle(t) }, onOpen = { onOpen(t) }
            )
        }
        if (waiting.isNotEmpty()) {
            Text(
                "WAITING ON", color = InkMuted, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp)
            )
            waiting.take(WAITING_ROWS_CAP).forEach { t ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title.ifBlank { "Untitled" }, color = Ink, fontFamily = InterFamily, fontWeight = FontWeight.Medium,
                            fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            listOfNotNull(t.ownerName, contextOf(t)).joinToString(" · ").ifBlank { "Waiting on someone" },
                            color = InkMuted, fontFamily = InterFamily, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { onNudge(t) }) { Text("Nudge", color = InkSecondary) }
                }
            }
        }
    }
}

@Composable
private fun TodayTaskRow(
    task: WorkTask,
    now: Long,
    timeFormat: DateFormat,
    context: String?,
    onToggle: () -> Unit,
    onOpen: () -> Unit
) {
    SwipeActionRow(
        onSwipeRight = onToggle,
        onSwipeLeft = onOpen,
        completeLabel = "Done",
        scheduleLabel = "Schedule"
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskCheck(checked = task.done, priority = if (task.isOverdue(now)) 1 else 3, onToggle = { onToggle() })
            Column(Modifier.weight(1f).padding(start = 4.dp).clickable(onClick = onOpen)) {
                Text(
                    task.title.ifBlank { "Untitled" }, color = Ink, fontFamily = InterFamily, fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val due = when {
                    task.isOverdue(now) -> "Overdue since " + WorkTodayTaskFormat.day(task.dueAt ?: now, now)
                    task.dueAt != null && WorkTodayTaskFormat.isTimed(task.dueAt) -> "Due " + timeFormat.format(java.util.Date(task.dueAt))
                    else -> "Due today"
                }
                Text(
                    listOfNotNull(due, context).joinToString(" · "), color = InkMuted, fontFamily = InterFamily,
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Small date helpers for task rows, kept out of the composable so they read as one line each. */
internal object WorkTodayTaskFormat {
    fun isTimed(ms: Long): Boolean {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return cal.get(java.util.Calendar.HOUR_OF_DAY) != 0 || cal.get(java.util.Calendar.MINUTE) != 0
    }

    fun day(ms: Long, now: Long): String = com.craftflowtechnologies.meetingmind.feature.work.dayLabel(ms, now)
}
