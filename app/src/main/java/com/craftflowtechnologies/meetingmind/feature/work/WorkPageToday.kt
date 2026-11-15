package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.rounded.Inbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRowModel
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
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
internal class WorkCircleItem(val id: String, val name: String, val groupWord: String, val memberCount: Int, val onClick: () -> Unit)

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
    val glance: TodayGlance? = null,
    val circles: List<WorkCircleItem> = emptyList(),
    val onStartCircle: (() -> Unit)? = null,
    val onAllCircles: (() -> Unit)? = null,
    val inboxCount: Int = 0,
    val latestInboxTitle: String? = null,
    val latestInboxSource: String? = null,
    val latestInboxTranscribed: Boolean = false,
    val onOpenInbox: (() -> Unit)? = null,
    val onOpenWeeklyReview: (() -> Unit)? = null,
    val onOpenBrief: (() -> Unit)? = null,
    val onOpenPeople: (() -> Unit)? = null,
    val onOpenAutopilot: (() -> Unit)? = null
) {
    val isEmpty get() = hero == null && needs.isEmpty() && upcoming.isEmpty() && earlier.isEmpty() && recent.isEmpty() && circles.isEmpty() && inboxCount == 0
}

private const val NEEDS_SHOWN = 4

/**
 * The Today segment: the next meeting, the quick capture actions (record, note, task, inbox, ask),
 * live Work Inbox triage spotlight, what needs the person, executive glance bento, today's schedule,
 * executive rhythm tools, team circles, and recent notes.
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
                    illustration = { ZuriSlot(CompanionPage.EMPTY, EmptyCompanionSize) },
                    title = "Your meeting notes live here",
                    body = "Record a meeting, import a WhatsApp voice note into your Inbox, or write a note — we'll keep decisions and tasks linked to what was said.",
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            PrimaryButton("Record", onRecord)
                            SecondaryButton("Write note", onWriteNote)
                            model.onOpenInbox?.let { openInbox ->
                                SecondaryButton("Inbox", openInbox)
                            }
                        }
                    }
                )
            }
            return@LazyColumn
        }
        item(key = "actions") {
            QuickActions(
                onRecord = onRecord,
                onWriteNote = onWriteNote,
                onAddTask = onAddTask,
                onAsk = onAsk,
                inboxCount = model.inboxCount,
                onOpenInbox = model.onOpenInbox
            )
        }
        if (model.inboxCount > 0 && model.onOpenInbox != null) {
            item(key = "inbox_spotlight") {
                InboxSpotlightCard(
                    count = model.inboxCount,
                    latestTitle = model.latestInboxTitle,
                    latestSource = model.latestInboxSource,
                    latestTranscribed = model.latestInboxTranscribed,
                    onClick = model.onOpenInbox
                )
            }
        }
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
        if (model.onOpenWeeklyReview != null || model.onOpenBrief != null || model.onOpenPeople != null) {
            item(key = "executive_rhythm") {
                ExecutiveRhythmRow(
                    inboxCount = model.inboxCount,
                    onOpenInbox = model.onOpenInbox,
                    onOpenWeeklyReview = model.onOpenWeeklyReview,
                    onOpenBrief = model.onOpenBrief,
                    onOpenPeople = model.onOpenPeople,
                    onOpenAutopilot = model.onOpenAutopilot
                )
            }
        }
        if (model.circles.isNotEmpty() || model.onStartCircle != null) item(key = "circles") {
            Column {
                SectionHeader(
                    "Team Circles",
                    count = if (model.circles.isNotEmpty()) model.circles.size else null,
                    actionLabel = if (model.circles.isNotEmpty() && model.onAllCircles != null) "All circles" else if (model.onStartCircle != null) "New circle" else null,
                    onAction = if (model.circles.isNotEmpty() && model.onAllCircles != null) model.onAllCircles else model.onStartCircle
                )
                if (model.circles.isEmpty()) {
                    MMCard(contentPadding = MM.space.m) {
                        Text("Collaborate with your team", style = MM.type.bodyStrong, color = MM.colors.ink)
                        Text(
                            "Create team circles to share meeting updates, decisions, and keep everyone aligned.",
                            style = MM.type.secondary,
                            color = MM.colors.inkSecondary,
                            modifier = Modifier.padding(top = MM.space.xs, bottom = MM.space.m)
                        )
                        model.onStartCircle?.let { onStart ->
                            SecondaryButton("Start a team circle", onStart)
                        }
                    }
                } else {
                    MMCard(contentPadding = MM.space.m) {
                        model.circles.forEach { c ->
                            ListRow(
                                title = c.name,
                                subtitle = "${c.memberCount} ${if (c.memberCount == 1) "member" else "members"} · ${c.groupWord}",
                                onClick = c.onClick
                            )
                        }
                    }
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
private fun QuickActions(
    onRecord: () -> Unit,
    onWriteNote: () -> Unit,
    onAddTask: () -> Unit,
    onAsk: () -> Unit,
    inboxCount: Int = 0,
    onOpenInbox: (() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        QuickAction("Record", Icons.Rounded.Mic, primary = true, onClick = onRecord, modifier = Modifier.weight(1f))
        QuickAction("Note", Icons.AutoMirrored.Filled.NoteAdd, primary = false, onClick = onWriteNote, modifier = Modifier.weight(1f))
        QuickAction("Task", Icons.Filled.AddTask, primary = false, onClick = onAddTask, modifier = Modifier.weight(1f))
        if (onOpenInbox != null) {
            QuickAction(
                label = if (inboxCount > 0) "Inbox ($inboxCount)" else "Inbox",
                icon = Icons.Rounded.Inbox,
                primary = false,
                onClick = onOpenInbox,
                modifier = Modifier.weight(1f)
            )
        }
        QuickAction("Ask", Icons.Rounded.Search, primary = false, onClick = onAsk, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MM.radius.card)
            .background(MM.colors.surface)
            .border(1.dp, if (primary) MM.colors.accent.copy(alpha = 0.25f) else MM.colors.lineSoft, MM.radius.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = MM.space.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        Box(
            Modifier.size(MMSize.minTouch).background(if (primary) MM.colors.accent else MM.colors.accentWash, MM.radius.pill),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (primary) MM.colors.onAccent else MM.colors.accent)
        }
        Text(label, style = MM.type.caption, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Spotlight banner when items are waiting in the Work Inbox (e.g. imported WhatsApp voice notes). */
@Composable
private fun InboxSpotlightCard(
    count: Int,
    latestTitle: String?,
    latestSource: String?,
    latestTranscribed: Boolean,
    onClick: () -> Unit
) {
    MMCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(MM.colors.accentWash, MM.radius.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Inbox, null, tint = MM.colors.accent)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    Text(
                        "WORK INBOX · $count ${if (count == 1) "ITEM" else "ITEMS"}",
                        style = MM.type.overline,
                        color = MM.colors.accent
                    )
                    if (latestTranscribed) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MM.colors.success, modifier = Modifier.size(12.dp))
                    }
                }
                Text(
                    latestTitle ?: "$count ${if (count == 1) "item" else "items"} ready to review",
                    style = MM.type.bodyStrong,
                    color = MM.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                val sub = listOfNotNull(
                    latestSource?.let { "From $it" },
                    if (latestTranscribed) "Transcribed & summarized" else "Tap to transcribe or file"
                ).joinToString(" · ")
                Text(
                    sub,
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            VerbPill("Open", onClick)
        }
    }
}

/** 3-column executive bento row for tasks, decisions, and open questions. */
@Composable
private fun GlanceRow(g: TodayGlance, onShowTasks: () -> Unit, onOpenList: (WorkTab) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
        GlanceBentoTile(
            count = g.openTasks,
            label = "${g.openTasks} open ${if (g.openTasks == 1) "task" else "tasks"}",
            shortSub = if (g.openTasks == 1) "Open task" else "Open tasks",
            accent = MM.colors.accent,
            onClick = onShowTasks,
            modifier = Modifier.weight(1f)
        )
        GlanceBentoTile(
            count = g.decisionsThisWeek,
            label = "${g.decisionsThisWeek} ${if (g.decisionsThisWeek == 1) "decision" else "decisions"} this week",
            shortSub = "Decisions this wk",
            accent = MM.colors.speaker3,
            onClick = { onOpenList(WorkTab.DECISIONS) },
            modifier = Modifier.weight(1f)
        )
        GlanceBentoTile(
            count = g.openQuestions,
            label = "${g.openQuestions} open ${if (g.openQuestions == 1) "question" else "questions"}",
            shortSub = if (g.openQuestions == 1) "Open question" else "Open questions",
            accent = MM.colors.speaker4,
            onClick = { onOpenList(WorkTab.QUESTIONS) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GlanceBentoTile(
    count: Int,
    label: String,
    shortSub: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .heightIn(min = MMSize.minTouch)
            .clip(MM.radius.card)
            .background(MM.colors.surface)
            .border(1.dp, MM.colors.lineSoft, MM.radius.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MM.space.m, vertical = MM.space.m),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).background(accent, MM.radius.pill))
            Text(count.toString(), style = MM.type.heading, color = MM.colors.ink)
        }
        Text(
            shortSub,
            style = MM.type.caption,
            color = MM.colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExecutiveRhythmRow(
    inboxCount: Int,
    onOpenInbox: (() -> Unit)?,
    onOpenWeeklyReview: (() -> Unit)?,
    onOpenBrief: (() -> Unit)?,
    onOpenPeople: (() -> Unit)?,
    onOpenAutopilot: (() -> Unit)? = null
) {
    Column {
        SectionHeader(
            "Work intelligence & rhythm",
            actionLabel = if (onOpenAutopilot != null) "Autopilot" else null,
            onAction = onOpenAutopilot
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            if (onOpenWeeklyReview != null) {
                RhythmCard(
                    icon = Icons.Filled.EventNote,
                    title = "Weekly review",
                    subtitle = "Close open loops",
                    tint = MM.colors.accent,
                    onClick = onOpenWeeklyReview,
                    modifier = Modifier.weight(1f)
                )
            }
            if (onOpenBrief != null) {
                RhythmCard(
                    icon = com.craftflowtechnologies.meetingmind.ui.icons.AiMark,
                    title = "Executive brief",
                    subtitle = "AI week summary",
                    tint = MM.colors.speaker2,
                    onClick = onOpenBrief,
                    modifier = Modifier.weight(1f)
                )
            }
            if (onOpenPeople != null) {
                RhythmCard(
                    icon = Icons.Filled.Groups,
                    title = "People & orgs",
                    subtitle = "Context & history",
                    tint = MM.colors.speaker3,
                    onClick = onOpenPeople,
                    modifier = Modifier.weight(1f)
                )
            } else if (onOpenInbox != null) {
                RhythmCard(
                    icon = Icons.Rounded.Inbox,
                    title = "Work Inbox",
                    subtitle = if (inboxCount > 0) "$inboxCount waiting" else "Voice notes & files",
                    tint = MM.colors.speaker3,
                    onClick = onOpenInbox,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RhythmCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(MM.radius.card)
            .background(MM.colors.surface)
            .border(1.dp, MM.colors.lineSoft, MM.radius.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(MM.space.m),
        verticalArrangement = Arrangement.spacedBy(MM.space.xs)
    ) {
        Box(
            Modifier.size(32.dp).background(tint.copy(alpha = 0.14f), MM.radius.small),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(title, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MM.type.caption, color = MM.colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    Box(Modifier.size(7.dp).background(MM.colors.accent, MM.radius.pill))
                    Text("NEXT UP · ${hero.whenLabel.uppercase()}", style = MM.type.overline, color = MM.colors.accent)
                }
                Text(hero.title, style = MM.type.title, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.xs))
                hero.withLine?.let { Text(it, style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.xs)) }
                hero.lastTime?.let {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = MM.space.m)
                            .clip(MM.radius.small)
                            .background(MM.colors.surface)
                            .padding(horizontal = MM.space.m, vertical = MM.space.s)
                    ) {
                        Text(it, style = MM.type.body, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = MM.space.l), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    PrimaryButton("Record", hero.onRecord, leadingIcon = Icons.Rounded.Mic)
                    SecondaryButton("Prepare", hero.onPrepare)
                }
            }
            is TodayHero.Attention -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.xs)
                ) {
                    Box(Modifier.size(7.dp).background(MM.colors.accent, MM.radius.pill))
                    Text("NEEDS YOU", style = MM.type.overline, color = MM.colors.accent)
                }
                Text(hero.item.title, style = MM.type.title, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = MM.space.xs))
                Text(hero.item.line, style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = MM.space.xs))
                PrimaryButton(hero.item.verb, hero.item.onClick, Modifier.padding(top = MM.space.l))
            }
        }
    }
}
