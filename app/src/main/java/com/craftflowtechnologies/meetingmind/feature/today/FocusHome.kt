package com.craftflowtechnologies.meetingmind.feature.today

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.timeline.DeepTarget
import com.craftflowtechnologies.meetingmind.core.timeline.Greetings
import com.craftflowtechnologies.meetingmind.core.timeline.ItemKind
import com.craftflowtechnologies.meetingmind.core.tasks.Task
import com.craftflowtechnologies.meetingmind.feature.tasks.TasksViewModel
import com.craftflowtechnologies.meetingmind.feature.tasks.describeDue
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
import com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.OnAccent
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

/**
 * The second home: one calm page. What's next, what's due, today's devotional if Faith is on,
 * and one big way to capture — nothing else. Chosen in Settings → Look and home.
 */
@Composable
fun FocusHome(
    viewModel: TodayViewModel,
    onRecord: () -> Unit,
    onNewNote: () -> Unit,
    onSearch: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenProcessing: (String) -> Unit,
    onOpenDevotional: () -> Unit,
    onOpenTasks: () -> Unit,
    onCustomize: () -> Unit,
    /** Calm: the same page, with stories and your recent recordings and notes under it. */
    rich: Boolean = false,
    onOpenStories: (com.craftflowtechnologies.meetingmind.feature.stories.StoryKind?) -> Unit = {},
    onNavigateBottomNav: (com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination) -> Unit
) {
    val identity by viewModel.identity.collectAsState()
    val upNext by viewModel.upNext.collectAsState()
    val devotional by viewModel.devotional.collectAsState()
    val now by viewModel.now.collectAsState()
    val jobs by viewModel.activeJobs.collectAsState()
    val storyKinds by viewModel.stories.collectAsState()
    val items by viewModel.items.collectAsState()
    val tasksVm: TasksViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val tasks by tasksVm.tasks.collectAsState()
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); viewModel.tick() } }

    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val due = tasks.filter { t ->
        !t.done && t.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() <= today } == true
    }.take(4)
    val fmt = timeFormat()

    Scaffold(
        containerColor = SurfaceBase,
        bottomBar = {
            com.craftflowtechnologies.meetingmind.core.ui.AppBottomNavigationBar(
                current = com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.HOME,
                onNavigate = { if (it != com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.HOME) onNavigateBottomNav(it) }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.getDefault()).format(java.util.Date(now)).uppercase(),
                            fontSize = 11.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted
                        )
                        Text(
                            remember(identity, now / 3_600_000) { Greetings.pick(identity) },
                            fontSize = 30.sp, lineHeight = 36.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search", tint = Ink) }
                    IconButton(onClick = onCustomize, modifier = Modifier.testTag("focus_customize")) { Icon(Icons.Filled.Tune, "Customize home", tint = InkSecondary) }
                }
            }

            item {
                val (label, title, sub, go) = when (val u = upNext) {
                    is UpNextTile.Event -> FocusNext(
                        "Up next · " + com.craftflowtechnologies.meetingmind.core.calendar.UpNext.whenLabel(u.event, now, fmt), u.event.title,
                        u.prep?.let { p -> "Last time: ${p.lastTitle ?: "with " + p.sharedPeople.joinToString()}" } ?: u.event.location,
                        { if (u.prep?.lastNoteId != null && u.event.begin - now > 5 * 60_000) onOpenNote(u.prep.lastNoteId) else viewModel.noteForEvent(u.event) { id, _ -> onOpenNote(id) } }
                    )
                    is UpNextTile.Item -> FocusNext("Today", u.item.title, u.item.subtitle ?: timeLabel(u.item, fmt), {
                        when (val t = u.item.target) {
                            is DeepTarget.Note -> onOpenNote(t.noteId)
                            is DeepTarget.Recording -> onOpenProcessing(t.meetingId)
                            is DeepTarget.Event -> viewModel.noteForEvent(t.event) { id, _ -> onOpenNote(id) }
                        }
                    })
                    UpNextTile.Nothing -> FocusNext("Nothing scheduled", "A clear day", "Capture something when it comes to you", onRecord)
                }
                Column(
                    Modifier.padding(top = 26.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SurfaceRaised)
                        .border(1.dp, LineSoft, RoundedCornerShape(22.dp)).clickable(onClick = go).padding(20.dp).testTag("focus_next")
                ) {
                    Text(label.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                    Text(title, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    if (!sub.isNullOrBlank()) Text(sub, fontSize = 14.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            if (jobs.isNotEmpty()) item {
                val j = jobs.first()
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AccentWash)
                        .clickable { onOpenProcessing(j.meetingId) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Processing · ${j.progressPercent}%", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.SemiBold)
                    Text(j.meetingTitle, fontSize = 14.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp).weight(1f))
                }
            }

            if (due.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("DUE", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.weight(1f))
                        Text("All tasks", fontSize = 13.sp, color = Accent, modifier = Modifier.clickable(onClick = onOpenTasks).padding(4.dp))
                    }
                }
                due.forEach { t -> item(key = t.id) { DueRow(t, onToggle = { tasksVm.toggle(t) }, onClick = onOpenTasks) } }
            }

            if (identity.showsFaith) item {
                val d = devotional?.devotional
                Row(
                    Modifier.padding(top = 26.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SurfaceRaised)
                        .border(1.dp, LineSoft, RoundedCornerShape(18.dp)).clickable(onClick = onOpenDevotional).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(4.dp, 36.dp).clip(CircleShape).background(FaithGold))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text("TODAY'S DEVOTIONAL", fontSize = 10.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted)
                        Text(d?.title ?: "A word for your day", fontSize = 16.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                        Text(d?.scripture?.firstOrNull()?.display() ?: "Scripture, a reflection and a prayer", fontSize = 13.sp, color = InkSecondary)
                    }
                }
            }

            item {
                Column(Modifier.fillMaxWidth().padding(top = if (rich) 20.dp else 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RecordOrb(onClick = onRecord, tag = "focus_record")
                    Text("Tap to record", fontSize = 13.sp, color = InkSecondary)
                    Row(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(50)).clickable(onClick = onNewNote).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, null, tint = InkSecondary, modifier = Modifier.size(18.dp))
                        Text("New note", fontSize = 14.sp, color = InkSecondary, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            if (rich) {
                if (storyKinds.isNotEmpty()) item(key = "stories") { StoryRings(storyKinds, onOpenStories) }
                val recent = items.filter { it.start <= now && (it.kind == ItemKind.RECORDING || it.kind == ItemKind.NOTE) }.sortedByDescending { it.start }.take(8)
                if (recent.isNotEmpty()) {
                    item(key = "recent-h") {
                        Text("RECENT", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(top = 28.dp, bottom = 4.dp))
                    }
                    items(recent.size, key = { "recent-" + recent[it].id }) { i ->
                        val it = recent[i]
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                when (val t = it.target) {
                                    is DeepTarget.Note -> onOpenNote(t.noteId)
                                    is DeepTarget.Recording -> onOpenProcessing(t.meetingId)
                                    is DeepTarget.Event -> viewModel.noteForEvent(t.event) { id, _ -> onOpenNote(id) }
                                }
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(AccentWash), contentAlignment = Alignment.Center) {
                                Icon(itemIcon(it), null, tint = Accent, modifier = Modifier.size(20.dp))
                            }
                            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                Text(it.title.ifBlank { "Untitled" }, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(it.workflow?.displayName?.takeIf { w -> w != "General" }, com.craftflowtechnologies.meetingmind.core.common.Formatters.formatDateRelative(it.start), it.badge).joinToString(" · "),
                                    fontSize = 12.5.sp, color = InkMuted, maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class FocusNext(val label: String, val title: String, val sub: String?, val go: () -> Unit)

@Composable
private fun DueRow(t: Task, onToggle: () -> Unit, onClick: () -> Unit) {
    val overdue = t.dueAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() < LocalDate.now() } == true
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).border(1.8.dp, if (overdue) Danger else Accent, CircleShape).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
            if (t.done) Icon(Icons.Filled.Check, null, tint = OnInk, modifier = Modifier.size(14.dp))
        }
        Text(t.title, fontSize = 15.5.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp).weight(1f))
        t.dueAt?.let { Text(describeDue(it), fontSize = 12.sp, color = if (overdue) Danger else InkMuted) }
    }
}
