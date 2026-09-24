package com.example.feature.faith

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.datastore.UserPreferencesManager
import com.example.core.faith.FaithStore
import com.example.core.faith.PlanProgress
import com.example.core.faith.PrayerPerson
import com.example.core.faith.PrayerRotation
import com.example.core.faith.PrayerTime
import com.example.core.faith.ReadingPlan
import com.example.core.faith.ReadingPlans
import com.example.core.faith.ReminderSettings
import com.example.core.scripture.ScriptureReference
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val Gold = Color(0xFFB7791F)
private val Rose = Color(0xFFDB2777)

/** Reading plans and the prayer list (PLAN_V2 F6). */
class FaithExtrasViewModel(app: Application) : AndroidViewModel(app) {
    private val store = FaithStore.get(app)
    private val prefs = UserPreferencesManager(app)
    val today: Long get() = LocalDate.now().toEpochDay()

    private val _plans = MutableStateFlow<List<PlanProgress>>(emptyList())
    val plans: StateFlow<List<PlanProgress>> = _plans.asStateFlow()
    private val _people = MutableStateFlow<List<PrayerPerson>>(emptyList())
    val people: StateFlow<List<PrayerPerson>> = _people.asStateFlow()
    private val _prayedDays = MutableStateFlow<Set<Long>>(emptySet())
    val prayedDays: StateFlow<Set<Long>> = _prayedDays.asStateFlow()
    val reminders: StateFlow<ReminderSettings> = prefs.reminderSettings.stateIn(viewModelScope, SharingStarted.Eagerly, ReminderSettings())

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            _plans.value = store.active()
            _people.value = store.people()
            _prayedDays.value = store.prayedDays()
        }
    }

    fun start(plan: ReadingPlan) = io { store.start(plan.id, today) }
    fun stop(plan: ReadingPlan) = io { store.stop(plan.id) }
    fun setDone(plan: ReadingPlan, day: Int, done: Boolean) = io { store.setDone(plan.id, day, done) }
    /** Catch up: mark every missed day before today as read (for someone who read elsewhere). */
    fun catchUp(p: PlanProgress) = io { (0 until p.dayFor(today)).filter { it !in p.done }.forEach { store.setDone(p.plan.id, it, true) } }
    fun addPerson(name: String, note: String) = io { if (name.isNotBlank()) store.addPerson(name, note) }
    fun removePerson(p: PrayerPerson) = io { store.removePerson(p.id) }
    fun prayed(p: PrayerPerson) = io { store.markPrayed(p.id) }
    fun setReminders(s: ReminderSettings) = viewModelScope.launch { prefs.setReminderSettings(s) }

    private fun io(block: () -> Unit) = viewModelScope.launch { withContext(Dispatchers.IO) { block() }; refresh() }
}

// ---------------------------------------------------------------- cards for the Faith page

@Composable
fun ReadingPlanCard(vm: FaithExtrasViewModel, onOpenPlans: () -> Unit, onRead: (ScriptureReference) -> Unit) {
    val plans by vm.plans.collectAsState()
    val p = plans.firstOrNull { !it.finished }
    Surface(onClick = onOpenPlans, shape = RoundedCornerShape(22.dp), color = Color(0xFFF3F7FF), border = BorderStroke(1.dp, Color(0xFFDCE6FA)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("faith_reading_plan")) {
        Column(Modifier.padding(18.dp)) {
            Text("READING PLAN", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2563EB))
            if (p == null) {
                Text("Read through the Bible, a book, or a season", fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 6.dp))
                Text("A year, 90 days, the Gospels, Psalms & Proverbs, Advent or Lent.", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp))
            } else {
                val day = p.nextUnread ?: p.dayFor(vm.today)
                val readings = p.plan.days[day]
                Text(p.plan.name, fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 6.dp))
                Text(ReadingPlans.describe(readings), fontSize = 19.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 2.dp))
                LinearProgressIndicator(progress = { p.percent / 100f }, color = Color(0xFF2563EB), trackColor = Color(0xFFDCE6FA), modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(5.dp).clip(RoundedCornerShape(3.dp)))
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Day ${day + 1} of ${p.plan.length} · ${p.percent}%" + p.behind(vm.today).let { if (it > 0) " · $it behind" else "" }, fontSize = 12.sp, color = InkMuted, modifier = Modifier.weight(1f))
                    Surface(onClick = { onRead(readings.first()) }, shape = RoundedCornerShape(50), color = Color.White, border = BorderStroke(1.dp, Color(0xFFDCE6FA))) {
                        Text("Read", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(onClick = { vm.setDone(p.plan, day, true) }, shape = RoundedCornerShape(50), color = Color(0xFF2563EB), modifier = Modifier.testTag("plan_mark_read")) {
                        Text("Mark read", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PrayingForCard(vm: FaithExtrasViewModel, onOpenList: () -> Unit) {
    val people by vm.people.collectAsState()
    val today = remember(people) { PrayerRotation.today(people, vm.today) }
    val startOfToday = remember { LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
    Surface(onClick = onOpenList, shape = RoundedCornerShape(22.dp), color = Color(0xFFFFF4F7), border = BorderStroke(1.dp, Color(0xFFF9D7E3)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("faith_praying_for")) {
        Column(Modifier.padding(18.dp)) {
            Text("PRAYING FOR", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = Rose)
            if (today.isEmpty()) {
                Text("Carry people by name", fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 6.dp))
                Text("Add the people and things you pray for — three come up each day, in turn.", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp))
            } else today.forEach { p ->
                val done = (p.lastPrayedAt ?: 0) >= startOfToday
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        if (p.note.isNotBlank()) Text(p.note, fontSize = 12.5.sp, color = InkSecondary, maxLines = 1)
                    }
                    Surface(onClick = { if (!done) vm.prayed(p) }, shape = RoundedCornerShape(50), color = if (done) Rose else Color.White, border = BorderStroke(1.dp, if (done) Rose else Color(0xFFF9D7E3))) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text(if (done) " Prayed" else "Prayed", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (done) Color.White else Rose)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- screens

@Composable
fun ReadingPlansScreen(vm: FaithExtrasViewModel, onNavigateBack: () -> Unit, onRead: (ScriptureReference) -> Unit) {
    val plans by vm.plans.collectAsState()
    var open by remember { mutableStateOf<String?>(plans.firstOrNull()?.plan?.id) }
    Scaffold(containerColor = Color.White) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { Header("Reading plans", onNavigateBack) }
            plans.forEach { p ->
                item(key = "active-" + p.plan.id) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFFF3F7FF)).padding(16.dp)) {
                        Text(p.plan.name, fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("${p.done.size} of ${p.plan.length} days · ${p.percent}%" + p.behind(vm.today).let { if (it > 0) " · $it behind" else "" }, fontSize = 13.sp, color = InkSecondary)
                        LinearProgressIndicator(progress = { p.percent / 100f }, color = Color(0xFF2563EB), trackColor = Color(0xFFDCE6FA), modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(5.dp).clip(RoundedCornerShape(3.dp)))
                        Row(Modifier.padding(top = 8.dp)) {
                            TextButton(onClick = { open = if (open == p.plan.id) null else p.plan.id }) { Text(if (open == p.plan.id) "Hide days" else "All days") }
                            if (p.behind(vm.today) > 0) TextButton(onClick = { vm.catchUp(p) }) { Text("I've caught up") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.stop(p.plan) }) { Text("Stop", color = InkMuted) }
                        }
                    }
                }
                if (open == p.plan.id) items(p.plan.length, key = { "d-${p.plan.id}-$it" }) { d ->
                    val done = d in p.done
                    val isToday = d == p.dayFor(vm.today)
                    Row(Modifier.fillMaxWidth().clickable { onRead(p.plan.days[d].first()) }.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(if (done) Color(0xFF2563EB) else Color.White).clickable { vm.setDone(p.plan, d, !done) }
                            .then(if (!done) Modifier.background(Color(0xFFEFF3FB)) else Modifier), contentAlignment = Alignment.Center) {
                            if (done) Icon(Icons.Filled.Check, contentDescription = "Read", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Text("Day ${d + 1}", fontSize = 12.sp, color = if (isToday) Color(0xFF2563EB) else InkMuted, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(start = 12.dp).width(56.dp))
                        Text(ReadingPlans.describe(p.plan.days[d]), fontSize = 15.sp, color = if (done) InkMuted else Ink)
                    }
                }
            }
            item { Text("START A PLAN", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)) }
            items(ReadingPlans.all.filter { a -> plans.none { it.plan.id == a.id } }, key = { "p-" + it.id }) { plan ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF8FAFC)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color(0xFF2563EB))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(plan.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("${plan.line} · ${plan.length} days", fontSize = 12.5.sp, color = InkSecondary)
                    }
                    Surface(onClick = { vm.start(plan); open = plan.id }, shape = RoundedCornerShape(50), color = Ink) {
                        Text("Start", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PrayerListScreen(vm: FaithExtrasViewModel, onNavigateBack: () -> Unit) {
    val people by vm.people.collectAsState()
    val days by vm.prayedDays.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var reminders by remember { mutableStateOf(false) }
    val today = remember(people) { PrayerRotation.today(people, vm.today) }
    Scaffold(containerColor = Color.White) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Header("Praying for", onNavigateBack) {
                    IconButton(onClick = { reminders = true }) { Icon(Icons.Filled.NotificationsNone, contentDescription = "Reminders", tint = Ink) }
                    IconButton(onClick = { adding = true }, modifier = Modifier.testTag("prayer_add")) { Icon(Icons.Filled.Add, contentDescription = "Add", tint = Ink) }
                }
            }
            item {
                // The quiet history: the last four weeks, one dot per day prayed.
                val start = vm.today - 27
                Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text("${(0..27).count { (start + it) in days }} of the last 28 days", fontSize = 13.sp, color = InkSecondary)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0..27).forEach { i -> Box(Modifier.size(9.dp).clip(CircleShape).background(if ((start + i) in days) Rose else Color(0xFFF1E4E9))) }
                    }
                }
            }
            item { Box(Modifier.padding(top = 12.dp)) { PrayingForCard(vm) {} } }
            item { Text("EVERYONE", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 6.dp)) }
            if (people.isEmpty()) item { Text("Add family, friends, your church, your city — anyone and anything you want to keep praying for.", fontSize = 14.sp, color = InkSecondary, modifier = Modifier.padding(horizontal = 20.dp)) }
            items(people, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFFBCFE8), Color(0xFFF9A8D4)))), contentAlignment = Alignment.Center) {
                        Text(p.name.take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF831843))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(p.name + if (p in today) "  · today" else "", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(listOfNotNull(p.note.takeIf { it.isNotBlank() }, if (p.prayedCount == 0) "Not yet prayed for" else "Prayed ${p.prayedCount}×").joinToString(" · "), fontSize = 12.5.sp, color = InkSecondary)
                    }
                    IconButton(onClick = { vm.removePerson(p) }) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove", tint = InkMuted) }
                }
            }
        }
    }
    if (adding) AddPersonDialog(onAdd = { n, note -> vm.addPerson(n, note); adding = false }, onDismiss = { adding = false })
    if (reminders) RemindersSheet(vm) { reminders = false }
}

@Composable
private fun Header(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun AddPersonDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color.White,
        title = { Text("Add to your prayer list") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it.replace("\n", "") }, singleLine = true, label = { Text("Who or what") }, placeholder = { Text("e.g. Mum, our church, Tom's job search") }, modifier = Modifier.fillMaxWidth().testTag("prayer_name"))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("What to pray (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onAdd(name, note) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RemindersSheet(vm: FaithExtrasViewModel, onDismiss: () -> Unit) {
    val current by vm.reminders.collectAsState()
    var s by remember(current) { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = { vm.setReminders(s); onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).navigationBarsPadding()) {
            Text("Reminders", fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("Gentle nudges, only the ones you choose. Your daily devotional has its own setting on its page.", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            Text("Prayer times (Daily Office)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrayerTime.entries.forEach { t ->
                    val on = t in s.prayerTimes
                    Surface(onClick = { s = s.copy(prayerTimes = if (on) s.prayerTimes - t else s.prayerTimes + t) }, shape = RoundedCornerShape(50), color = if (on) Gold.copy(alpha = 0.14f) else Color.White, border = BorderStroke(1.dp, if (on) Gold else Color(0xFFE2E8F0))) {
                        Text("${t.label} · %d:%02d".format(t.defaultMinutes / 60, t.defaultMinutes % 60), fontSize = 13.sp, color = if (on) Color(0xFF7A4E0F) else InkSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
            }
            Toggle("Today's reading", "At 7 pm if you haven't read yet", s.readingNudge) { s = s.copy(readingNudge = it) }
            Toggle("Evening reflection", "A moment at 9 pm to notice the day", s.eveningReflection) { s = s.copy(eveningReflection = it) }
            Toggle("Before meetings", "15 minutes before calendar events", s.meetingPrep) { s = s.copy(meetingPrep = it) }
            Text("Quiet hours: %d:%02d – %d:%02d".format(s.quietStart / 60, s.quietStart % 60, s.quietEnd / 60, s.quietEnd % 60), fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 14.dp))
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(21 * 60 to 7 * 60, 22 * 60 to 7 * 60, 23 * 60 to 6 * 60).forEach { (a, b) ->
                    val on = s.quietStart == a && s.quietEnd == b
                    Surface(onClick = { s = s.copy(quietStart = a, quietEnd = b) }, shape = RoundedCornerShape(50), color = if (on) Ink else Color.White, border = BorderStroke(1.dp, if (on) Ink else Color(0xFFE2E8F0))) {
                        Text("${a / 60}:00–${b / 60}:00", fontSize = 13.sp, color = if (on) Color.White else InkSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
            }
            Surface(onClick = { vm.setReminders(s); onDismiss() }, shape = RoundedCornerShape(50), color = Ink, modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)) {
                Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp))
            }
        }
    }
}

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 12.5.sp, color = InkSecondary)
        }
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Gold))
    }
}
