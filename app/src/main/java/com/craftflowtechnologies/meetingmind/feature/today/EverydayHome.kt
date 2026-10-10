package com.craftflowtechnologies.meetingmind.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvent
import com.craftflowtechnologies.meetingmind.core.calendar.UpNext
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.identity.AppIdentity
import com.craftflowtechnologies.meetingmind.core.identity.Avatar
import com.craftflowtechnologies.meetingmind.core.model.NotebookSpace
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.model.Workflows
import com.craftflowtechnologies.meetingmind.core.timeline.DeepTarget
import com.craftflowtechnologies.meetingmind.core.timeline.Greetings
import com.craftflowtechnologies.meetingmind.core.timeline.ItemKind
import com.craftflowtechnologies.meetingmind.core.ui.AppBottomNavigationBar
import com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination
import com.craftflowtechnologies.meetingmind.core.ui.mm.EmptyState
import com.craftflowtechnologies.meetingmind.core.ui.mm.HeroCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.HomeHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.HomeScaffold
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.NoteRowModel
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionVisible
import com.craftflowtechnologies.meetingmind.feature.tasks.TasksViewModel
import com.craftflowtechnologies.meetingmind.feature.tasks.describeDue
import com.craftflowtechnologies.meetingmind.feature.work.noteTimeLabel
import com.craftflowtechnologies.meetingmind.feature.work.previewOf
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

// ---------------------------------------------------------------------------------------------
// The Everyday home (MVP_PLAN §2, DESIGN_SYSTEM §9): greeting and companion, Next, Needs you,
// Recent notes, Today. Built only from the design system's home scaffold and components.
// ---------------------------------------------------------------------------------------------

/** The one hero: the next event, or the latest note when nothing is coming up. */
@Immutable
data class EverydayNext(
    val eyebrow: String,
    val title: String,
    val subtitle: String?,
    val primaryLabel: String,
    val onPrimary: () -> Unit,
    val secondaryLabel: String? = null,
    val onSecondary: (() -> Unit)? = null,
    val onClick: () -> Unit
)

enum class NeedsKind { Processing, Task, Devotional }

/** One line under "Needs you". */
@Immutable
data class EverydayNeed(
    val key: String,
    val kind: NeedsKind,
    val title: String,
    val subtitle: String?,
    val meta: String? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val onClick: () -> Unit
)

@Immutable
data class EverydayNote(val id: String, val row: NoteRowModel)

@Immutable
data class EverydayAgendaRow(val key: String, val title: String, val subtitle: String?, val time: String, val onClick: () -> Unit)

/** Everything the Everyday home shows; empty lists render nothing. */
@Immutable
data class EverydayModel(
    val identity: AppIdentity,
    val greeting: String,
    val contextLine: String?,
    val next: EverydayNext? = null,
    /** Everything that needs the person; the home shows the first [NEEDS_LIMIT]. */
    val needs: List<EverydayNeed> = emptyList(),
    val recent: List<EverydayNote> = emptyList(),
    val agenda: List<EverydayAgendaRow> = emptyList(),
    val lateNight: Boolean = false
)

const val NEEDS_LIMIT = 4
const val EVERYDAY_RECENT = 5
private const val AGENDA_LIMIT = 5

class EverydayActions(
    val onSearch: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenNote: (String) -> Unit = {},
    val onAllNotes: () -> Unit = {},
    val onAllTasks: () -> Unit = {},
    val onRecord: () -> Unit = {}
)

private val CompanionSize = 56.dp
private val EmptyCompanionSize = 96.dp
private val AvatarSize = 40.dp

/** The stateless page, so screenshots and previews can feed it a model directly. */
@Composable
fun EverydayHomeContent(
    model: EverydayModel,
    actions: EverydayActions,
    modifier: Modifier = Modifier
) {
    val companionShown = rememberCompanionVisible(CompanionPage.HOME)
    val needs = model.needs.take(NEEDS_LIMIT)
    HomeScaffold(
        modifier = modifier.testTag("everyday_home"),
        header = {
            HomeHeader(
                greeting = model.greeting,
                subtitle = model.contextLine,
                leading = if (companionShown) {
                    { ZuriSlot(CompanionPage.HOME, CompanionSize, state = if (model.lateNight) CompanionState.SLEEPY else CompanionState.IDLE) }
                } else null,
                actions = {
                    IconButton(onClick = actions.onSearch, modifier = Modifier.size(MMSize.minTouch).testTag("everyday_search")) {
                        Icon(Icons.Rounded.Search, "Search", tint = MM.colors.ink)
                    }
                    Box(Modifier.size(MMSize.minTouch).testTag("everyday_avatar"), contentAlignment = Alignment.Center) {
                        Avatar(model.identity, AvatarSize, onClick = actions.onOpenSettings)
                    }
                }
            )
        },
        hero = model.next?.let { next -> { NextHero(next) } }
    ) {
        if (needs.isNotEmpty()) item(key = "needs") {
            val hasTasks = model.needs.any { it.kind == NeedsKind.Task }
            Column {
                SectionHeader(
                    "Needs you", count = model.needs.size,
                    actionLabel = if (hasTasks) "All tasks" else null,
                    onAction = if (hasTasks) actions.onAllTasks else null
                )
                needs.forEach { NeedRow(it) }
            }
        }
        if (model.recent.isNotEmpty()) item(key = "recent") {
            Column {
                SectionHeader("Recent notes", actionLabel = "All notes", onAction = actions.onAllNotes)
                model.recent.forEach { n -> NoteRow(n.row, onClick = { actions.onOpenNote(n.id) }) }
            }
        } else if (model.next == null && needs.isEmpty() && model.agenda.isEmpty()) item(key = "empty") {
            EmptyState(
                title = "Nothing here yet",
                body = "Record a conversation or write a note, and it shows up here.",
                illustration = { ZuriSlot(CompanionPage.EMPTY, EmptyCompanionSize) },
                action = { PrimaryButton("Record", actions.onRecord) }
            )
        }
        if (model.agenda.isNotEmpty()) item(key = "today") {
            Column {
                SectionHeader("Today", count = model.agenda.size)
                model.agenda.forEach { a -> ListRow(a.title, subtitle = a.subtitle, meta = a.time, onClick = a.onClick) }
            }
        }
    }
}

@Composable
private fun NextHero(next: EverydayNext) {
    HeroCard(onClick = next.onClick, modifier = Modifier.testTag("everyday_next")) {
        Text(next.eyebrow, style = MM.type.caption, color = MM.colors.accent)
        Spacer(Modifier.height(MM.space.xs))
        Text(next.title, style = MM.type.title, color = MM.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!next.subtitle.isNullOrBlank()) {
            Text(next.subtitle, style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().padding(top = MM.space.l), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            PrimaryButton(next.primaryLabel, next.onPrimary)
            if (next.secondaryLabel != null && next.onSecondary != null) SecondaryButton(next.secondaryLabel, next.onSecondary)
        }
    }
}

@Composable
private fun NeedRow(need: EverydayNeed) {
    val icon: ImageVector = when (need.kind) {
        NeedsKind.Processing -> Icons.Rounded.Sync
        NeedsKind.Task -> Icons.Rounded.CheckCircleOutline
        NeedsKind.Devotional -> Icons.Rounded.AutoStories
    }
    val tint = if (need.kind == NeedsKind.Devotional) MM.colors.goldInk else MM.colors.accent
    ListRow(
        need.title, subtitle = need.subtitle, meta = need.meta, onClick = need.onClick,
        leading = { Icon(icon, null, tint = tint, modifier = Modifier.size(MMSize.icon)) },
        trailing = if (need.actionLabel != null && need.onAction != null) {
            { TextAction(need.actionLabel, need.onAction) }
        } else null
    )
}

// ---------------------------------------------------------------------------------------------
// The wired page
// ---------------------------------------------------------------------------------------------

@Composable
fun EverydayHome(
    viewModel: TodayViewModel,
    onOpenNote: (String) -> Unit,
    onOpenProcessing: (String) -> Unit,
    onRecord: () -> Unit,
    onRecordEvent: (noteId: String, type: RecordingType, title: String, speakers: Int?) -> Unit,
    onSearch: () -> Unit,
    onOpenDevotional: () -> Unit,
    onOpenTasks: () -> Unit,
    onNavigateBottomNav: (BottomNavDestination) -> Unit
) {
    val identity by viewModel.identity.collectAsState()
    val upNext by viewModel.upNext.collectAsState()
    val devotional by viewModel.devotional.collectAsState()
    val now by viewModel.now.collectAsState()
    val jobs by viewModel.activeJobs.collectAsState()
    val today by viewModel.today.collectAsState()
    val recentNotes by viewModel.recentNotes.collectAsState()
    val contextLine by viewModel.contextLine.collectAsState()
    val tasksVm: TasksViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val tasks by tasksVm.tasks.collectAsState()
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); viewModel.tick() } }

    val fmt = timeFormat()
    val colors = MM.colors
    val model = remember(identity, upNext, devotional, now / 60_000, jobs, today, recentNotes, contextLine, tasks, colors) {
        val zone = ZoneId.systemDefault()
        val localToday = LocalDate.now()
        fun openEvent(e: CalendarEvent) = viewModel.noteForEvent(e) { id, _ -> onOpenNote(id) }
        fun recordEvent(e: CalendarEvent) = viewModel.noteForEvent(e) { id, type -> onRecordEvent(id, type, e.title, UpNext.speakerCount(e)) }
        fun spaceColor(space: NotebookSpace): Color = when (space) {
            NotebookSpace.WORK -> colors.accent
            NotebookSpace.FAITH -> colors.gold
            NotebookSpace.LEARNING -> colors.speaker2
            NotebookSpace.PERSONAL -> colors.inkFaint
        }

        val nextEvent = upNext as? UpNextTile.Event
        // With no event coming up, the hero is the latest note: pick up where you left off.
        val heroNote = if (nextEvent == null) recentNotes.firstOrNull() else null

        val next: EverydayNext? = when {
            nextEvent != null -> {
                val e = nextEvent.event
                val prep = nextEvent.prep
                EverydayNext(
                    eyebrow = "NEXT · " + UpNext.whenLabel(e, now, fmt).uppercase(),
                    title = e.title,
                    subtitle = prep?.let { p -> "Last time: ${p.lastTitle ?: "with " + p.sharedPeople.joinToString()}" }
                        ?: listOfNotNull(e.otherPeople.takeIf { it.isNotEmpty() }?.let { if (it.size <= 2) it.joinToString() else "${it.first()} +${it.size - 1}" }, e.location)
                            .joinToString(" · ").ifBlank { null },
                    primaryLabel = "Record", onPrimary = { recordEvent(e) },
                    secondaryLabel = "Prepare", onSecondary = {
                        if (prep?.lastNoteId != null && e.begin - now > 5 * 60_000) onOpenNote(prep.lastNoteId) else openEvent(e)
                    },
                    onClick = { openEvent(e) }
                )
            }
            heroNote != null -> EverydayNext(
                eyebrow = "PICK UP WHERE YOU LEFT OFF",
                title = heroNote.title.ifBlank { heroNote.workflow.displayName },
                subtitle = previewOf(heroNote.plainText, 120).ifBlank { null },
                primaryLabel = "Open", onPrimary = { onOpenNote(heroNote.id) },
                onClick = { onOpenNote(heroNote.id) }
            )
            else -> null
        }

        val needs = buildList {
            jobs.forEach { j ->
                add(EverydayNeed("job-${j.id}", NeedsKind.Processing, j.meetingTitle.ifBlank { "Recording" }, "Processing · ${j.progressPercent}%", onClick = { onOpenProcessing(j.meetingId) }))
            }
            tasks.filter { t -> !t.done && t.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() <= localToday } == true }
                .sortedBy { it.dueAt }
                .forEach { t -> add(EverydayNeed("task-${t.id}", NeedsKind.Task, t.title, null, t.dueAt?.let { describeDue(it) }, "Done", { tasksVm.toggle(t) }, onOpenTasks)) }
            if (identity.showsFaith) {
                val d = devotional?.devotional
                add(EverydayNeed("devotional", NeedsKind.Devotional, d?.title ?: "Today's devotional", d?.scripture?.firstOrNull()?.display() ?: "Scripture, a reflection and a prayer", onClick = onOpenDevotional))
            }
        }

        val recentList = recentNotes.filter { it.id != heroNote?.id }.take(EVERYDAY_RECENT).map { n ->
            EverydayNote(
                n.id,
                NoteRowModel(
                    title = n.title.ifBlank { n.workflow.displayName }, preview = previewOf(n.plainText),
                    spaceColor = spaceColor(Workflows.space(n.workflow)), timeLabel = noteTimeLabel(n.eventDate ?: n.updatedAt, now),
                    pinned = n.pinned, isPrivate = n.isPrivate
                )
            )
        }

        val agenda = today.filter { it.kind == ItemKind.EVENT && !it.allDay && it.target is DeepTarget.Event && (it.end ?: it.start) >= now }
            .filterNot { item ->
                val e = (item.target as DeepTarget.Event).event
                nextEvent != null && e.eventId == nextEvent.event.eventId && e.begin == nextEvent.event.begin
            }
            .take(AGENDA_LIMIT)
            .map { item ->
                val e = (item.target as DeepTarget.Event).event
                EverydayAgendaRow(item.id, item.title, e.otherPeople.take(3).joinToString(", ").ifBlank { e.location }, timeLabel(item, fmt)) { openEvent(e) }
            }

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        EverydayModel(
            identity = identity, greeting = Greetings.plain(identity), contextLine = contextLine,
            next = next, needs = needs, recent = recentList, agenda = agenda, lateNight = hour >= 22 || hour < 5
        )
    }

    Scaffold(
        containerColor = MM.colors.background,
        bottomBar = {
            AppBottomNavigationBar(
                current = BottomNavDestination.HOME,
                onNavigate = { if (it != BottomNavDestination.HOME) onNavigateBottomNav(it) }
            )
        }
    ) { padding ->
        EverydayHomeContent(
            model,
            EverydayActions(
                onSearch = onSearch,
                onOpenSettings = { onNavigateBottomNav(BottomNavDestination.SETTINGS) },
                onOpenNote = onOpenNote,
                onAllNotes = { onNavigateBottomNav(BottomNavDestination.NOTES) },
                onAllTasks = onOpenTasks,
                onRecord = onRecord
            ),
            Modifier.fillMaxSize().statusBarsPadding().padding(bottom = padding.calculateBottomPadding())
        )
    }
}
