package com.craftflowtechnologies.meetingmind.feature.work

import com.craftflowtechnologies.meetingmind.ui.theme.Briefing
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.calendar.CalendarEvent
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.work.FindingKind
import com.craftflowtechnologies.meetingmind.core.work.WorkProfile
import com.craftflowtechnologies.meetingmind.core.work.WorkTask
import com.craftflowtechnologies.meetingmind.feature.today.TodayViewModel
import com.craftflowtechnologies.meetingmind.feature.today.UpNextTile
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.LocalMMColors
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk

/** A way to begin a work note. [recordable] ones start a recording; the rest open a written note. */
internal data class WorkStart(val type: RecordingType, val title: String, val line: String, val icon: ImageVector)

internal val WorkStarts = listOf(
    WorkStart(RecordingType.MEETING, "Meeting notes", "Agenda, decisions, next steps", Icons.Filled.Groups),
    WorkStart(RecordingType.ONE_ON_ONE, "1:1", "Updates, blockers, feedback", Icons.Filled.Person),
    WorkStart(RecordingType.CLIENT_CALL, "Client call", "What they need, what's next", Icons.Filled.Handshake),
    WorkStart(RecordingType.STANDUP, "Standup", "Person by person", Icons.Filled.ViewDay),
    WorkStart(RecordingType.INTERVIEW, "Interview", "Questions and answers", Icons.Filled.RecordVoiceOver),
    WorkStart(RecordingType.CONSULTATION, "Consultation", "As said, kept on your phone", Icons.Filled.MedicalServices),
    WorkStart(RecordingType.PROJECT_BRIEF, "Project brief", "Goal, scope, people, dates", Icons.Filled.Description),
    WorkStart(RecordingType.DECISION_RECORD, "Decision record", "What, why, and what else", Icons.Filled.Rule),
    WorkStart(RecordingType.WEEKLY_REVIEW, "Weekly review", "Done, slipping, next", Icons.AutoMirrored.Filled.EventNote)
)

/** The order tiles appear in for a kind of work: its own meeting types first. */
internal fun startsFor(profile: WorkProfile): List<WorkStart> {
    val first = profile.recordTypes
    val hideConsultation = profile != WorkProfile.CLINICAL && profile != WorkProfile.LEGAL
    return WorkStarts.filter { !(hideConsultation && it.type == RecordingType.CONSULTATION) }
        .sortedBy { s -> first.indexOf(s.type).let { if (it < 0) 99 else it } }
}

/**
 * The Work space (docs/PLAN_PROFESSIONAL.md §3, §7): meetings, clients and follow-through in one
 * place, the way Faith is for sermons and prayer. Everything here works before the first
 * recording — templates, projects, people, tasks — and fills in as recordings arrive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkSpaceScreen(
    viewModel: WorkViewModel,
    today: TodayViewModel,
    onNavigateBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenMeeting: (String, Long?) -> Unit,
    onRecordType: (RecordingType) -> Unit,
    onRecordEvent: (noteId: String, type: RecordingType, title: String, speakers: Int?) -> Unit,
    onOpenWrapUp: (String, Boolean) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenAll: (WorkTab) -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
    /** "✨ Create brief" for the week. */
    onOpenBrief: (com.craftflowtechnologies.meetingmind.core.work.BriefTarget) -> Unit = {}
) {
    var savedFilter by remember { mutableStateOf<com.craftflowtechnologies.meetingmind.core.work.SavedFilter?>(null) }
    val settings by viewModel.settings.collectAsState()
    val myTasks by viewModel.myTasks.collectAsState()
    val waitingOn by viewModel.waitingOn.collectAsState()
    val toReview by viewModel.toReview.collectAsState()
    val followUps by viewModel.followUps.collectAsState()
    val decisions by viewModel.decisions.collectAsState()
    val questions by viewModel.openQuestions.collectAsState()
    val people by viewModel.everyone.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val recent by viewModel.recent.collectAsState()
    val workNotes by viewModel.workNotes.collectAsState()
    val titles by viewModel.titles.collectAsState()
    val introDismissed by viewModel.introDismissed.collectAsState()
    val upNext by today.upNext.collectAsState()
    val now by today.now.collectAsState()
    val terms = settings.terms
    var newProject by remember { mutableStateOf(false) }
    var newPerson by remember { mutableStateOf(false) }
    var nudging by remember { mutableStateOf<WorkTask?>(null) }
    var editing by remember { mutableStateOf<WorkTask?>(null) }
    var adding by remember { mutableStateOf("") }
    var recordPicker by remember { mutableStateOf(false) }
    val nothingYet = myTasks.isEmpty() && waitingOn.isEmpty() && recent.isEmpty() && workNotes.isEmpty() && projects.isEmpty()

    Scaffold(containerColor = SurfaceBase) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("work_space"), contentPadding = PaddingValues(bottom = 48.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                    Column(Modifier.weight(1f)) {
                        Text("Work", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = (-0.5).sp)
                        Text("Meetings, ${terms.organisations.lowercase()} and follow-through, in one place", fontSize = 13.sp, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search", tint = Ink) }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("work_settings")) { Icon(Icons.Filled.Tune, "Work settings", tint = InkSecondary) }
                }
            }

            // How it works: the promise, in three steps, until the person has seen it or lived it.
            if (!introDismissed) item { IntroCard(settings.profile, onTry = { viewModel.newNote(RecordingType.MEETING, null, onOpenNote) }, onRecord = { recordPicker = true }, onDismiss = viewModel::dismissIntro) }

            // Up next, with what to remember.
            item { UpNextCard(upNext, now, today, onOpenNote, onRecordEvent, onRecord = { recordPicker = true }) }

            // The week at a glance.
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${myTasks.size}", "My tasks", Accent, Modifier.weight(1f)) { onOpenAll(WorkTab.MINE) }
                    Stat("${waitingOn.size}", "Waiting on", LocalMMColors.current.speaker3, Modifier.weight(1f)) { onOpenAll(WorkTab.WAITING) }
                    Stat("${decisions.size}", "Decisions", DecisionTint, Modifier.weight(1f)) { onOpenAll(WorkTab.DECISIONS) }
                    Stat("${questions.size}", "Open", QuestionTint, Modifier.weight(1f)) { onOpenAll(WorkTab.QUESTIONS) }
                }
            }

            // Answers from the record, one tap each (D5.5).
            item { SavedFilterRow { savedFilter = it } }
            item {
                Row(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp)) { Pill("✨ Create brief") { onOpenBrief(com.craftflowtechnologies.meetingmind.core.work.BriefTarget.weekly) } }
            }

            // What needs you: findings to confirm, follow-ups to send.
            if (toReview.isNotEmpty()) {
                item { WorkSectionTitle("To review", "${toReview.size}") }
                items(toReview.take(4), key = { "r-" + it.meetingId }) { r ->
                    ActionCard(r.title, "${r.type.displayName} · ${dayLabel(r.at)} · ready to wrap up", "Wrap up") { onOpenWrapUp(r.meetingId, false) }
                }
            }
            if (followUps.isNotEmpty()) {
                item { WorkSectionTitle("Follow-ups to send", "${followUps.size}") }
                items(followUps.take(4), key = { "f-" + it.meetingId }) { r ->
                    ActionCard(r.title, "${r.type.displayName} · ${dayLabel(r.at)}", "Send") { onOpenWrapUp(r.meetingId, true) }
                }
            }

            // Begin: record, or write from a template.
            item {
                WorkSectionTitle("Begin")
                RecordCard(onClick = { recordPicker = true })
                val starts = startsFor(settings.profile)
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    starts.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { s ->
                                StartTile(s.icon, s.title, s.line, tintFor(s.type), Modifier.weight(1f).testTag("work_start_${s.type.name.lowercase()}")) {
                                    viewModel.newNote(s.type, null, onOpenNote)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Text("Templates open a note to write in. Record adds a transcript, then a Wrap-up and a follow-up.",
                    fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }

            // What you owe.
            item { WorkSectionTitle("My tasks", if (myTasks.size > 5) "All ${myTasks.size}" else if (myTasks.isNotEmpty()) "All" else null, onTrailing = { onOpenAll(WorkTab.MINE) }) }
            item {
                OutlinedTextField(
                    value = adding, onValueChange = { adding = it }, placeholder = { Text("Add a task — “Send the proposal Friday”") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("work_add_task"),
                    leadingIcon = { Icon(Icons.Filled.Add, null, tint = InkMuted) }, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.addTask(adding); adding = "" })
                )
            }
            if (myTasks.isEmpty()) item { EmptyLine("Nothing on your plate. Tasks you add, and ones you agree to in meetings, land here with the day they're due.") }
            items(myTasks.take(5), key = { "t-" + it.id }) { t ->
                TaskLine(t, t.meetingId?.let { titles[it] }, onToggle = { viewModel.toggle(t) }, onClick = { editing = t },
                    onPlay = t.meetingId?.let { m -> { onOpenMeeting(m, t.startMs) } })
            }

            // What you're owed.
            if (waitingOn.isNotEmpty()) {
                item { WorkSectionTitle("Waiting on", if (waitingOn.size > 4) "All ${waitingOn.size}" else "All", onTrailing = { onOpenAll(WorkTab.WAITING) }) }
                items(waitingOn.take(4), key = { "w-" + it.id }) { t ->
                    TaskLine(t, null, onToggle = { viewModel.toggle(t) }, onClick = { editing = t },
                        action = { TextButton(onClick = { nudging = t }) { Text("Nudge") } })
                }
            }

            // Clients and projects.
            item {
                WorkSectionTitle(terms.projects, "New", onTrailing = { newProject = true })
                if (projects.isEmpty()) {
                    WorkCard(onClick = { newProject = true }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AccentWash), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Work, null, tint = Accent)
                            }
                            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                                Text("Start a ${terms.project.lowercase()}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text("One place for its meetings, notes, people, tasks and decisions", fontSize = 12.sp, color = InkSecondary)
                            }
                        }
                    }
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(projects, key = { "p-" + it.notebook.id }) { p -> ProjectTile(p) { onOpenProject(p.notebook.id) } }
                        item { AddTile("New ${terms.project.lowercase()}") { newProject = true } }
                    }
                }
            }

            // People you work with.
            item {
                WorkSectionTitle(if (terms.person == "Contact") "People" else terms.people, if (people.isNotEmpty()) "All" else "Add", onTrailing = { if (people.isNotEmpty()) onOpenAll(WorkTab.PEOPLE) else newPerson = true })
                if (people.isEmpty()) {
                    EmptyLine("People appear here on their own when you name a speaker or record from a calendar invite. You can add someone now, too.")
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(people.take(16), key = { "pp-" + it.id }) { p ->
                            Column(Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpenPerson(p.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                PersonAvatar(p, 52.dp)
                                Text(p.firstName, fontSize = 12.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                        item {
                            Column(Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).clickable { newPerson = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(52.dp).clip(CircleShape).border(1.dp, LineSoft, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, "Add person", tint = InkSecondary) }
                                Text("Add", fontSize = 12.sp, color = InkSecondary, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }

            // Decided, and still open.
            if (decisions.isNotEmpty()) {
                item { WorkSectionTitle("Decided", "Decision log", onTrailing = { onOpenAll(WorkTab.DECISIONS) }) }
                items(decisions.take(4), key = { "d-" + it.id }) { d ->
                    FindingLine(FindingKind.DECISION, d.text, "${d.meetingTitle} · ${dayLabel(d.createdAt)}", onClick = { onOpenMeeting(d.meetingId, null) })
                }
            }
            if (questions.isNotEmpty()) {
                item { WorkSectionTitle("Still open", "All ${questions.size}", onTrailing = { onOpenAll(WorkTab.QUESTIONS) }) }
                items(questions.take(3), key = { "q-" + it.id }) { q ->
                    FindingLine(FindingKind.QUESTION, q.text, q.meetingTitle, onClick = { onOpenMeeting(q.meetingId, null) },
                        trailing = { TextButton(onClick = { viewModel.resolveQuestion(q, null) }) { Text("Answered") } })
                }
            }

            // Everything recent in Work.
            item { WorkSectionTitle("Recent") }
            if (recent.isEmpty() && workNotes.isEmpty()) item {
                EmptyLine("Your meetings and work notes will gather here. Start with a template above — nothing leaves your phone unless you turn on Internet mode.")
            }
            val merged = (recent.map { Triple(it.at, it.title, { onOpenMeeting(it.meetingId, null) } as () -> Unit) to it.type.displayName } +
                workNotes.filter { n -> recent.none { it.noteId == n.id } }.map { n -> Triple(n.eventDate ?: n.updatedAt, n.title.ifBlank { n.workflow.displayName }, { onOpenNote(n.id) } as () -> Unit) to n.workflow.displayName })
                .sortedByDescending { it.first.first }.take(12)
            items(merged.size, key = { "rec-$it" }) { i ->
                val (t, kind) = merged[i]
                Row(Modifier.fillMaxWidth().clickable(onClick = t.third).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(SurfaceSunk), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Description, null, tint = InkSecondary, modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(t.second, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$kind · ${dayLabel(t.first)}", fontSize = 12.sp, color = InkMuted)
                    }
                }
            }
            if (nothingYet) item {
                Text("Tip: in Settings → Look and home, the Professional home puts all of this on your home screen.",
                    fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
            }
        }
    }

    if (recordPicker) RecordTypeSheet(startsFor(settings.profile).filter { com.craftflowtechnologies.meetingmind.core.model.Workflows.isRecordable(it.type) },
        onPick = { recordPicker = false; onRecordType(it) }, onDismiss = { recordPicker = false })
    if (newProject) NewProjectSheet(terms.project, terms.organisation, settings.keepOnDevice, onDismiss = { newProject = false }) { name, org, conf ->
        newProject = false; viewModel.newProject(name, org, conf, onOpenProject)
    }
    if (newPerson) NewPersonSheet(terms.organisation, onDismiss = { newPerson = false }) { name, role, email, phone, org ->
        newPerson = false; viewModel.addPerson(name, role, email, phone, org, onOpenPerson)
    }
    savedFilter?.let { f ->
        val answer by androidx.compose.runtime.produceState<com.craftflowtechnologies.meetingmind.core.work.SavedAnswer?>(null, f) { value = viewModel.savedViews.answer(f) }
        SavedAnswerSheet(answer, f.label, viewModel, onOpenMeeting, onOpenPerson, onDismiss = { savedFilter = null })
    }
    nudging?.let { NudgeSheet(it, viewModel) { nudging = null } }
    editing?.let { t -> WorkTaskSheet(t, viewModel, onDismiss = { editing = null }, onOpenSource = { t.meetingId?.let { m -> editing = null; onOpenMeeting(m, t.startMs) } ?: t.noteId?.let { n -> editing = null; onOpenNote(n) } }) }
}

@Composable
internal fun tintFor(type: RecordingType): Color {
    val c = LocalMMColors.current
    return when (type) {
        RecordingType.MEETING, RecordingType.STANDUP -> c.accent
        RecordingType.ONE_ON_ONE, RecordingType.INTERVIEW -> c.speaker3
        RecordingType.CLIENT_CALL, RecordingType.CONSULTATION -> c.speaker4
        RecordingType.PROJECT_BRIEF, RecordingType.WEEKLY_REVIEW -> c.speaker2
        else -> c.inkSecondary
    }
}

/** The promise, in three steps (the user-confidence card). */
@Composable
private fun IntroCard(profile: WorkProfile, onTry: () -> Unit, onRecord: () -> Unit, onDismiss: () -> Unit) {
    val dark = LocalMMColors.current.isDark
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Briefing.Indigo, Briefing.IndigoDeep, Briefing.Slate)))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MEETINGMIND FOR ${profile.label.uppercase()}", fontSize = 10.5.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = Briefing.Lavender, modifier = Modifier.weight(1f))
            Text("✕", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(6.dp))
        }
        Text("From conversation to done", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Briefing.OnBrief, modifier = Modifier.padding(top = 4.dp))
        listOf(
            "1" to "Record or write — tap ⭐ ✓ ? to flag what matters as it happens.",
            "2" to "Wrap up in a minute: decisions, your tasks and what others owe you, already found.",
            "3" to "Send the follow-up on WhatsApp or email, and track everything until it's done."
        ).forEach { (n, line) ->
            Row(Modifier.padding(top = 10.dp)) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Text(n, fontSize = 11.sp, color = Briefing.OnBrief, fontWeight = FontWeight.Bold)
                }
                Text(line, fontSize = 13.5.sp, lineHeight = 19.sp, color = Color.White.copy(alpha = 0.88f), modifier = Modifier.padding(start = 10.dp))
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(Briefing.OnBrief).clickable(onClick = onRecord).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Record a meeting", color = Briefing.Indigo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onTry).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("Write meeting notes", color = Briefing.OnBrief, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        }
        Text(if (profile.sensitive) "Your work stays on this phone." else "Private by default: nothing leaves your phone unless you turn on Internet mode.",
            fontSize = 11.5.sp, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(top = 12.dp))
        if (dark) Spacer(Modifier.height(0.dp))
    }
}

@Composable
private fun UpNextCard(
    upNext: UpNextTile, now: Long, today: TodayViewModel,
    onOpenNote: (String) -> Unit, onRecordEvent: (String, RecordingType, String, Int?) -> Unit, onRecord: () -> Unit
) {
    val calendarOn by today.calendarOn.collectAsState()
    val event: CalendarEvent? = (upNext as? UpNextTile.Event)?.event
    val prep = (upNext as? UpNextTile.Event)?.prep
    WorkCard(Modifier.padding(top = 14.dp)) {
        Column(Modifier.padding(18.dp)) {
            if (event != null) {
                val whenLabel = com.craftflowtechnologies.meetingmind.core.calendar.UpNext.whenLabel(event, now, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT))
                Text("UP NEXT · ${whenLabel.uppercase()}", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                Text(event.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                val with = event.otherPeople
                if (with.isNotEmpty()) Text("With " + with.take(3).joinToString(", ") + if (with.size > 3) " +${with.size - 3}" else "", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp))
                prep?.let { p ->
                    Row(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceSunk).clickable { p.lastNoteId?.let(onOpenNote) }.padding(12.dp)) {
                        Column {
                            Text("LAST TIME", fontSize = 10.sp, letterSpacing = 1.sp, color = InkMuted, fontWeight = FontWeight.SemiBold)
                            Text(p.lastTitle ?: "With ${p.sharedPeople.joinToString()}", fontSize = 14.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("● Record", filled = true) {
                        today.noteForEvent(event) { id, type -> onRecordEvent(id, type, event.title, com.craftflowtechnologies.meetingmind.core.calendar.UpNext.speakerCount(event)) }
                    }
                    Pill("Notes") { today.noteForEvent(event) { id, _ -> onOpenNote(id) } }
                }
            } else {
                Text(if (calendarOn == true) "NOTHING SCHEDULED" else "YOUR MEETINGS", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted)
                Text(if (calendarOn == true) "A clear run of time" else "See who you're meeting, and what's still open", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 4.dp))
                Text(
                    if (calendarOn == true) "Record a conversation when it happens, or plan ahead with a template."
                    else "Turn on your phone's calendar on Home for meeting prep: the last conversation, what's owed, one tap to record.",
                    fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp)
                )
                Row(Modifier.padding(top = 12.dp)) { Pill("● Record now", filled = true, onClick = onRecord) }
            }
        }
    }
}

@Composable
internal fun Pill(label: String, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).then(if (filled) Modifier.background(Ink) else Modifier.border(1.dp, LineSoft, RoundedCornerShape(50)))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp)
    ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (filled) OnInk else Ink) }
}

@Composable
private fun Stat(value: String, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(SurfaceRaised).border(1.dp, LineSoft, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = tint)
        Text(label, fontSize = 11.5.sp, color = InkSecondary, maxLines = 1)
    }
}

@Composable
internal fun ActionCard(title: String, line: String, action: String, onClick: () -> Unit) {
    WorkCard(Modifier.padding(vertical = 4.dp), onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line, fontSize = 12.sp, color = InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(action, fontSize = 14.sp, color = Accent, fontWeight = FontWeight.SemiBold)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Accent, modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
    }
}

@Composable
private fun RecordCard(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(Ink).clickable(onClick = onClick).padding(18.dp).testTag("work_record"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(LocalMMColors.current.recording), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Mic, null, tint = Briefing.OnBrief)
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text("Record a meeting", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = OnInk)
            Text("Mark moments as you go, then wrap up in a minute", fontSize = 13.sp, color = OnInk.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ProjectTile(p: ProjectCard, onClick: () -> Unit) {
    val tint = p.notebook.colorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: Accent
    Column(Modifier.width(168.dp).clip(RoundedCornerShape(18.dp)).background(SurfaceRaised).border(1.dp, LineSoft, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(tint))
            Text(p.notebook.status, fontSize = 11.sp, color = InkMuted, modifier = Modifier.padding(start = 6.dp).weight(1f))
            if (p.notebook.confidential) Text("🔒", fontSize = 11.sp)
        }
        Text(p.notebook.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(listOfNotNull(p.org, "${p.notes} notes").joinToString(" · "), fontSize = 12.sp, color = InkSecondary, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun AddTile(label: String, onClick: () -> Unit) {
    Column(Modifier.width(120.dp).height(92.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, LineSoft, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Add, null, tint = InkSecondary)
        Text(label, fontSize = 12.sp, color = InkSecondary, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Which kind of conversation to record, work types first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordTypeSheet(starts: List<WorkStart>, onPick: (RecordingType) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceBase) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("What are you recording?", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            starts.forEach { s ->
                Row(Modifier.fillMaxWidth().clickable { onPick(s.type) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tintFor(s.type).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(s.icon, null, tint = tintFor(s.type))
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(s.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text(s.line, fontSize = 12.sp, color = InkSecondary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewProjectSheet(projectWord: String, orgWord: String, keepOnDevice: Boolean, onDismiss: () -> Unit, onCreate: (String, String?, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var org by remember { mutableStateOf("") }
    var confidential by remember { mutableStateOf(keepOnDevice) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceBase) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text("New ${projectWord.lowercase()}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("Its meetings, notes, people, tasks and decisions will live together.", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("project_name"),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            OutlinedTextField(org, { org = it }, label = { Text("$orgWord (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Confidential", fontSize = 15.sp, color = Ink)
                    Text("Its recordings and notes are processed only on this phone", fontSize = 12.sp, color = InkMuted)
                }
                Switch(confidential, { confidential = it })
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onCreate(name, org, confidential) }, enabled = name.isNotBlank()) { Text("Create") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewPersonSheet(orgWord: String, onDismiss: () -> Unit, onCreate: (String, String?, String?, String?, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var org by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceBase) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text("Add someone", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("Kept on this phone. Only the name is needed.", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            OutlinedTextField(role, { role = it }, label = { Text("Role (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            OutlinedTextField(org, { org = it }, label = { Text("$orgWord (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            OutlinedTextField(email, { email = it }, label = { Text("Email (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email))
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone, with country code (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { onCreate(name, role.ifBlank { null }, email.ifBlank { null }, phone.ifBlank { null }, org.ifBlank { null }) }, enabled = name.isNotBlank()) { Text("Add") }
            }
        }
    }
}

/** Edits a work task with the app's own task editor, so tasks behave the same everywhere. */
@Composable
internal fun WorkTaskSheet(t: WorkTask, viewModel: WorkViewModel, onDismiss: () -> Unit, onOpenSource: (() -> Unit)?) {
    val tasksVm: com.craftflowtechnologies.meetingmind.feature.tasks.TasksViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val people by tasksVm.people.collectAsState()
    val task by androidx.compose.runtime.produceState<com.craftflowtechnologies.meetingmind.core.tasks.Task?>(null, t.id) {
        value = com.craftflowtechnologies.meetingmind.core.tasks.TaskReminders.repository(viewModel.getApplication()).get(t.id)
    }
    task?.let { full ->
        com.craftflowtechnologies.meetingmind.feature.tasks.TaskEditorSheet(
            task = full, people = people,
            onSave = { saved, newPerson -> tasksVm.save(saved, newPerson); onDismiss() },
            onDelete = { tasksVm.delete(full); onDismiss() },
            onOpenSource = onOpenSource,
            onDismiss = onDismiss
        )
    }
}
