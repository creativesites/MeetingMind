package com.craftflowtechnologies.meetingmind.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.work.projectColor
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.launch

/**
 * Plan my day: one card at a time from overdue, promised and due-soon tasks. Today moves a task to
 * today, Not today leaves it, Pick a time opens the date sheet for that card. Ends on a summary.
 */
@Composable
fun PlanMyDayPage(
    vm: TasksViewModel,
    ctx: TaskCtx,
    onBack: () -> Unit,
    onSheet: (TaskSheet) -> Unit,
    onSeeToday: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val session by vm.planState.collectAsState()
    val meetings by vm.todayEvents.collectAsState()

    DisposableEffect(Unit) {
        scope.launch { vm.startPlan() }
        onDispose { vm.endPlan() }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = MM.space.l)) {
        PageHeader(
            title = "Plan my day",
            subtitle = session?.let { s ->
                if (s.finished) "Done" else "${s.index + 1} of ${s.queue.size} · overdue, promised and due soon"
            },
            onBack = onBack
        )
        Spacer(Modifier.height(MM.space.l))
        val s = session
        val id = s?.current
        when {
            s == null -> Unit
            id == null -> PlanFinish(
                total = s.queue.size,
                today = s.todayCount,
                notToday = s.notTodayCount,
                picked = s.pickedCount,
                onSeeToday = onSeeToday
            )
            else -> {
                val task = ctx.taskById(id)
                if (task == null) {
                    // The task was finished or removed elsewhere while the plan ran: skip it.
                    LaunchedEffect(s.index) { vm.decidePlan(PlanChoice.NOT_TODAY) }
                } else {
                    PlanCard(
                        task = task,
                        ctx = ctx,
                        fraction = if (s.queue.isEmpty()) 0f else s.index / s.queue.size.toFloat(),
                        meetingCount = meetings.size,
                        onToday = { scope.launch { vm.decidePlan(PlanChoice.TODAY) } },
                        onNotToday = { scope.launch { vm.decidePlan(PlanChoice.NOT_TODAY) } },
                        onPick = { onSheet(TaskSheet.Date(listOf(task.id), planPick = true)) }
                    )
                }
            }
        }
        Spacer(Modifier.height(MM.space.xxl))
    }
}

@Composable
private fun PlanCard(
    task: Task,
    ctx: TaskCtx,
    fraction: Float,
    meetingCount: Int,
    onToday: () -> Unit,
    onNotToday: () -> Unit,
    onPick: () -> Unit
) {
    val colors = MM.colors
    val list = task.listId?.let { ctx.listById[it] }
    val overdue = TaskViews.dayOf(task, ctx.zone)?.isBefore(ctx.today) == true
    val dueToday = TaskViews.dayOf(task, ctx.zone) == ctx.today
    val who = if (task.sourceItemId != null) task.personId?.let { ctx.people[it] } else null
    val over = when {
        overdue -> "Past due since ${TaskViews.dueLabel(task, ctx.today, ctx.zone) ?: "earlier"}"
        dueToday -> "Due today"
        task.sourceItemId != null -> "A promise"
        else -> "Coming up"
    }

    Box(Modifier.fillMaxWidth().height(4.dp).background(colors.track, MM.radius.pill)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(colors.accent, MM.radius.pill))
    }
    Spacer(Modifier.height(MM.space.xl))
    HeroCard(Modifier.fillMaxWidth(), contentPadding = MM.space.l) {
        Text(over, style = MM.type.overline, color = if (overdue) colors.warning else colors.accent)
        Spacer(Modifier.height(MM.space.s))
        Text(task.title, style = MM.type.title, color = colors.ink)
        Spacer(Modifier.height(MM.space.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (list != null) {
                Box(Modifier.size(8.dp).background(projectColor(list.id), MM.radius.small))
                Spacer(Modifier.width(MM.space.xs))
                Text(list.name, style = MM.type.secondary, color = colors.inkSecondary)
            }
            if (who != null) {
                Text(
                    " · promised to ${who.substringBefore(' ')}",
                    style = MM.type.secondary,
                    color = colors.inkSecondary
                )
            }
        }
        if (task.notes.isNotBlank()) {
            Spacer(Modifier.height(MM.space.m))
            Text(task.notes, style = MM.type.body, color = colors.inkSecondary, maxLines = 4)
        }
        if (meetingCount > 0) {
            Spacer(Modifier.height(MM.space.m))
            Text(
                "$meetingCount ${if (meetingCount == 1) "meeting" else "meetings"} on your calendar today",
                style = MM.type.caption,
                color = colors.inkMuted
            )
        }
    }
    Spacer(Modifier.height(MM.space.xl))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SecondaryButton(text = "Not today", onClick = onNotToday, modifier = Modifier.weight(1f))
        PrimaryButton(text = "Today", onClick = onToday, modifier = Modifier.weight(1f), leadingIcon = Icons.Filled.WbSunny)
    }
    Spacer(Modifier.height(MM.space.s))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextAction("Pick a time", onClick = onPick)
    }
}

@Composable
private fun PlanFinish(total: Int, today: Int, notToday: Int, picked: Int, onSeeToday: () -> Unit) {
    val colors = MM.colors
    Column(Modifier.fillMaxWidth().padding(top = MM.space.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (total == 0) "Nothing needs planning" else "Your day is set",
            style = MM.type.title,
            color = colors.ink
        )
        Spacer(Modifier.height(MM.space.s))
        Text(
            when {
                total == 0 -> "No overdue, promised or due-soon tasks right now."
                else -> "$today moved to today, $picked given a time, $notToday left where they are."
            },
            style = MM.type.secondary,
            color = colors.inkSecondary
        )
        Spacer(Modifier.height(MM.space.xl))
        PrimaryButton(text = "See Today", onClick = onSeeToday)
    }
}
