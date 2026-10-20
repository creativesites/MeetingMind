package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRowModel
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** One thing that needs the person, with its one verb. */
internal class NeedsItem(val key: String, val title: String, val line: String, val verb: String, val onClick: () -> Unit)

internal sealed interface TodayHero {
    /** The next meeting. [lastTime] is "Last time: … · 2 open items", from what Work already knows. */
    class Meeting(
        val whenLabel: String, val title: String, val withLine: String?, val lastTime: String?,
        val onPrepare: () -> Unit, val onRecord: () -> Unit
    ) : TodayHero
    /** No meeting: the most urgent thing that needs the person. */
    class Attention(val item: NeedsItem) : TodayHero
}

internal class ScheduleRow(val key: String, val title: String, val subtitle: String?, val time: String, val onClick: () -> Unit)
internal class RecentNote(val id: String, val model: NoteRowModel, val onClick: () -> Unit)

/** Counts that are also ways in: each one opens the list it counts. */
internal class TodayGlance(val openTasks: Int, val decisionsThisWeek: Int, val openQuestions: Int)

/** Everything the Today segment shows. An empty part renders nothing. */
internal class TodayModel(
    val hero: TodayHero? = null,
    val needs: List<NeedsItem> = emptyList(),
    /** All of the needs-you items, including one promoted to the hero. */
    val needsTotal: Int = needs.size,
    val upcoming: List<ScheduleRow> = emptyList(),
    val earlier: List<ScheduleRow> = emptyList(),
    val recent: List<RecentNote> = emptyList(),
    val glance: TodayGlance? = null
) {
    val isEmpty get() = hero == null && needs.isEmpty() && upcoming.isEmpty() && earlier.isEmpty() && recent.isEmpty()
}

private const val NEEDS_SHOWN = 4

/**
 * The Today segment: the next meeting, the four things everyone reaches for (record, note, task, ask), what needs
 * the person, the figures that open their lists, the schedule, and what was worked on last.
 */
@Composable
internal fun TodayContent(
    model: TodayModel,
    listState: LazyListState,
    onAllNotes: () -> Unit,
    onRecord: () -> Unit,
    onWriteNote: () -> Unit,
    onAddTask: () -> Unit = {},
    onShowTasks: () -> Unit = {},
    onAsk: () -> Unit = {},
    onOpenList: (WorkTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var allNeeds by rememberSaveable { mutableStateOf(false) }
    var showEarlier by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier.fillMaxSize().testTag("work_today_list"), state = listState,
        contentPadding = PaddingValues(start = MM.space.l, end = MM.space.l, top = MM.space.s, bottom = MM.space.xxl),
        verticalArrangement = Arrangement.spacedBy(MM.space.xl)
    ) {
        if (model.isEmpty) {
            item(key = "empty") {
                EmptyState(
                    title = "Your meeting notes live here",
                    body = "Record a meeting or write a note — we'll keep decisions and tasks linked to what was said.",
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            PrimaryButton("Record", onRecord)
                            SecondaryButton("Write note", onWriteNote)
                        }
                    }
                )
            }
            return@LazyColumn
        }
        item(key = "actions") { QuickActions(onRecord, onWriteNote, onAddTask, onAsk) }
        model.hero?.let { hero -> item(key = "hero") { NextUp(hero) } }
        if (model.needs.isNotEmpty()) item(key = "needs") {
            val shown = if (allNeeds) model.needs else model.needs.take(NEEDS_SHOWN)
            Column {
                SectionHeader(
                    "Needs you", count = model.needsTotal,
                    actionLabel = if (model.needs.size > NEEDS_SHOWN) (if (allNeeds) "Fewer" else "All ${model.needsTotal}") else null,
                    onAction = if (model.needs.size > NEEDS_SHOWN) ({ allNeeds = !allNeeds }) else null
                )
                MMCard(contentPadding = MM.space.m) {
                    shown.forEach { n ->
                        ListRow(n.title, subtitle = n.line, onClick = n.onClick, trailing = { VerbPill(n.verb, n.onClick) })
                    }
                }
            }
        }
        model.glance?.let { g -> item(key = "glance") { GlanceRow(g, onShowTasks, onOpenList) } }
        if (model.upcoming.isNotEmpty() || model.earlier.isNotEmpty()) item(key = "schedule") {
            Column {
                SectionHeader("Today's schedule", count = model.upcoming.size + model.earlier.size)
                MMCard(contentPadding = MM.space.m) {
                    if (model.earlier.isNotEmpty()) {
                        QuietLink(if (showEarlier) "Hide earlier today" else "Earlier today (${model.earlier.size})", { showEarlier = !showEarlier })
                        if (showEarlier) model.earlier.forEach { ScheduleLine(it) }
                    }
                    model.upcoming.forEach { ScheduleLine(it) }
                }
            }
        }
        if (model.recent.isNotEmpty()) item(key = "recent") {
            Column {
                SectionHeader("Continue where you left off", actionLabel = "All notes", onAction = onAllNotes)
                model.recent.forEach { n -> NoteRow(n.model, onClick = n.onClick) }
            }
        }
    }
}

/** Record is the one filled action; the others are quiet, so the eye lands on Record first. */
@Composable
private fun QuickActions(onRecord: () -> Unit, onWriteNote: () -> Unit, onAddTask: () -> Unit, onAsk: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        QuickAction("Record", Icons.Rounded.Mic, primary = true, onClick = onRecord, modifier = Modifier.weight(1f))
        QuickAction("Note", Icons.AutoMirrored.Filled.NoteAdd, primary = false, onClick = onWriteNote, modifier = Modifier.weight(1f))
        QuickAction("Task", Icons.Filled.AddTask, primary = false, onClick = onAddTask, modifier = Modifier.weight(1f))
        QuickAction("Ask", Icons.Rounded.Search, primary = false, onClick = onAsk, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(MM.radius.card).background(MM.colors.surface).clickable(role = Role.Button, onClick = onClick).padding(vertical = MM.space.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        Box(
            Modifier.size(MMSize.minTouch).background(if (primary) MM.colors.accent else MM.colors.accentWash, MM.radius.pill),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (primary) MM.colors.onAccent else MM.colors.accent)
        }
        Text(label, style = MM.type.caption, color = MM.colors.ink, maxLines = 1)
    }
}

@Composable
private fun GlanceRow(g: TodayGlance, onShowTasks: () -> Unit, onOpenList: (WorkTab) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        GlancePill("${g.openTasks} open ${if (g.openTasks == 1) "task" else "tasks"}", onShowTasks, Modifier.weight(1f))
        GlancePill("${g.decisionsThisWeek} ${if (g.decisionsThisWeek == 1) "decision" else "decisions"} this week", { onOpenList(WorkTab.DECISIONS) }, Modifier.weight(1f))
        GlancePill("${g.openQuestions} open ${if (g.openQuestions == 1) "question" else "questions"}", { onOpenList(WorkTab.QUESTIONS) }, Modifier.weight(1f))
    }
}

@Composable
private fun GlancePill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = MMSize.minTouch).clip(MM.radius.pill).background(MM.colors.surfaceSunk)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = MM.space.s),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MM.type.caption, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun VerbPill(label: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = MMSize.minTouch).clip(MM.radius.pill).background(MM.colors.accentWash)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = MM.space.m),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MM.type.bodyStrong, color = MM.colors.accent)
    }
}

@Composable
private fun ScheduleLine(r: ScheduleRow) = ListRow(r.title, subtitle = r.subtitle, meta = r.time, onClick = r.onClick)

@Composable
private fun NextUp(hero: TodayHero) {
    HeroCard {
        when (hero) {
            is TodayHero.Meeting -> {
                Text("NEXT UP · ${hero.whenLabel.uppercase()}", style = MM.type.overline, color = MM.colors.accent)
                Text(hero.title, style = MM.type.title, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.xs))
                hero.withLine?.let { Text(it, style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.xs)) }
                hero.lastTime?.let { Text(it, style = MM.type.body, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.m)) }
                Row(Modifier.fillMaxWidth().padding(top = MM.space.l), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    PrimaryButton("Record", hero.onRecord, leadingIcon = Icons.Rounded.Mic)
                    SecondaryButton("Prepare", hero.onPrepare)
                }
            }
            is TodayHero.Attention -> {
                Text("NEEDS YOU", style = MM.type.overline, color = MM.colors.accent)
                Text(hero.item.title, style = MM.type.title, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.xs))
                Text(hero.item.line, style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.xs))
                PrimaryButton(hero.item.verb, hero.item.onClick, Modifier.padding(top = MM.space.l))
            }
        }
    }
}
