package com.craftflowtechnologies.meetingmind.feature.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.work.Channel
import com.craftflowtechnologies.meetingmind.core.work.GreetingStyle
import com.craftflowtechnologies.meetingmind.core.work.TabSlot
import com.craftflowtechnologies.meetingmind.core.work.WorkWindow
import com.craftflowtechnologies.meetingmind.core.work.Terms
import com.craftflowtechnologies.meetingmind.core.work.Tone
import com.craftflowtechnologies.meetingmind.core.work.WorkProfile
import com.craftflowtechnologies.meetingmind.core.work.WorkSection
import com.craftflowtechnologies.meetingmind.core.work.WorkSettings
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary

/**
 * Personalisation for work (docs/PLAN_PROFESSIONAL.md §8): the kind of work, its words, what Home
 * shows, how follow-ups read, and what stays on the phone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkSettingsScreen(viewModel: WorkViewModel, onNavigateBack: () -> Unit) {
    val s by viewModel.settings.collectAsState()
    fun set(change: (WorkSettings) -> WorkSettings) = viewModel.updateSettings(change)

    Scaffold(containerColor = com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 48.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink) }
                    Text("Work", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
            }

            item { WorkSectionTitle("What kind of work?") }
            item {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    WorkProfile.entries.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                // A new profile brings its defaults; the name and signature stay.
                                set { old -> WorkSettings.forProfile(p).copy(signOff = old.signOff, signature = old.signature, tabSlot = old.tabSlot, greeting = old.greeting,
                                    workDays = old.workDays, workStartMinute = old.workStartMinute, workEndMinute = old.workEndMinute, prepLeadMinutes = old.prepLeadMinutes,
                                    weeklyReviewDay = old.weeklyReviewDay, notifyMorning = old.notifyMorning, morningMinute = old.morningMinute, notifyPrep = old.notifyPrep,
                                    notifyStartNow = old.notifyStartNow, notifyWeekly = old.notifyWeekly, weeklyReviewMinute = old.weeklyReviewMinute) }
                            }.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(selected = s.profile == p, onClick = null)
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(p.label, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium)
                                Text(p.description + if (p.sensitive) " · confidential by default" else "", fontSize = 12.sp, color = InkMuted)
                            }
                        }
                    }
                }
            }

            item { WorkSectionTitle("Your words") }
            item {
                var org by remember(s.terms) { mutableStateOf(s.terms.organisation) }
                var project by remember(s.terms) { mutableStateOf(s.terms.project) }
                var person by remember(s.terms) { mutableStateOf(s.terms.person) }
                fun save() = set { it.copy(termsOverride = Terms(org.trim().ifEmpty { it.profile.terms.organisation }, project.trim().ifEmpty { it.profile.terms.project }, person.trim().ifEmpty { it.profile.terms.person }).takeIf { t -> t != it.profile.terms }) }
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What you call the people and work in your meetings. Used on Home, in the Wrap-up and in follow-ups.", fontSize = 13.sp, color = InkSecondary)
                    WordField("Organisations", org, listOf("Client", "Customer", "Account", "Company", "Patient", "Team")) { org = it; save() }
                    WordField("Work", project, listOf("Project", "Matter", "Case", "Deal", "Engagement", "Story")) { project = it; save() }
                    WordField("People", person, listOf("Contact", "Client", "Patient", "Candidate", "Source", "Stakeholder")) { person = it; save() }
                }
            }

            item { WorkSectionTitle("Home") }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("Sections, in order. Empty ones hide on their own.", fontSize = 13.sp, color = InkSecondary)
                    s.sections.forEachIndexed { index, section ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (section == WorkSection.PEOPLE) s.terms.people else section.label, fontSize = 15.sp, color = if (section in s.hiddenSections) InkMuted else Ink, modifier = Modifier.weight(1f))
                            IconButton(onClick = { set { it.copy(sections = it.sections.toMutableList().apply { if (index > 0) add(index - 1, removeAt(index)) }) } }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, "Move up", tint = InkSecondary)
                            }
                            IconButton(onClick = { set { it.copy(sections = it.sections.toMutableList().apply { if (index < size - 1) add(index + 1, removeAt(index)) }) } }, enabled = index < s.sections.size - 1) {
                                Icon(Icons.Filled.KeyboardArrowDown, "Move down", tint = InkSecondary)
                            }
                            Switch(checked = section !in s.hiddenSections, onCheckedChange = { on ->
                                set { it.copy(hiddenSections = if (on) it.hiddenSections - section else it.hiddenSections + section) }
                            })
                        }
                    }
                    Text("Greeting", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GreetingStyle.entries.forEach { g -> Chip(g.label, s.greeting == g, Ink) { set { it.copy(greeting = g) } } }
                    }
                    Text("Bottom bar", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TabSlot.entries.forEach { t -> Chip(t.label, s.tabSlot == t, Ink) { set { it.copy(tabSlot = t) } } }
                    }
                    Text("With Work in the bar, Search is still at the top of Home.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp))
                }
            }

            item { WorkSectionTitle("Rhythm") }
            item {
                val days = listOf(2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat", 1 to "Sun")
                fun hours(m: Int) = "%d:%02d".format(m / 60, m % 60)
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("Nudges only arrive on your working days, inside your working hours.", fontSize = 13.sp, color = InkSecondary)
                    Text("Working days", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        days.forEach { (n, label) -> Chip(label, n in s.workDays, Ink) { set { it.copy(workDays = if (n in it.workDays) it.workDays - n else it.workDays + n) } } }
                    }
                    Text("Working hours", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(7 * 60 to 16 * 60, 8 * 60 + 30 to 17 * 60 + 30, 9 * 60 to 18 * 60, 10 * 60 to 19 * 60).forEach { (a, b) ->
                            Chip("${hours(a)}–${hours(b)}", s.workStartMinute == a && s.workEndMinute == b, Ink) { set { it.copy(workStartMinute = a, workEndMinute = b) } }
                        }
                    }
                    ToggleRow("Morning line", "One line at ${hours(s.morningMinute)}: today's meetings and what needs you", s.notifyMorning) { on -> set { it.copy(notifyMorning = on) } }
                    ToggleRow("Prep before meetings", "${s.prepLeadMinutes} minutes ahead, for meetings with people you know", s.notifyPrep) { on -> set { it.copy(notifyPrep = on) } }
                    ToggleRow("“Starting now — record?”", "Asks when a meeting begins. Off unless you turn it on.", s.notifyStartNow) { on -> set { it.copy(notifyStartNow = on) } }
                    ToggleRow("Weekly review", "On ${days.firstOrNull { it.first == s.weeklyReviewDay }?.second ?: "Fri"} at ${hours(WorkWindow.clampToHours(s, s.weeklyReviewMinute))}", s.notifyWeekly) { on -> set { it.copy(notifyWeekly = on) } }
                    Text("Review day", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        days.forEach { (n, label) -> Chip(label, s.weeklyReviewDay == n, Ink) { set { it.copy(weeklyReviewDay = n) } } }
                    }
                    if (s.profile.sensitive) Text("For ${s.profile.label.lowercase()} work, notifications show counts only: no names, no titles.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 8.dp))
                }
            }

            item { WorkSectionTitle("Follow-ups") }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("Tone", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tone.entries.forEach { t -> Chip(t.label, s.tone == t, Ink) { set { it.copy(tone = t) } } }
                    }
                    Text("Send on", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Adaptive", s.defaultChannel == null, Ink) { set { it.copy(defaultChannel = null) } }
                        listOf(Channel.WHATSAPP, Channel.EMAIL, Channel.SMS).forEach { c -> Chip(c.label, s.defaultChannel == c, Ink) { set { it.copy(defaultChannel = c) } } }
                    }
                    Text(
                        if (s.defaultChannel == null) "Offers what worked with each person before; otherwise WhatsApp or email, whichever you have for them and is usual where you are."
                        else "Always offers ${s.defaultChannel!!.label} first.",
                        fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 4.dp)
                    )
                    var signOff by remember(s.signOff) { mutableStateOf(s.signOff) }
                    var signature by remember(s.signature) { mutableStateOf(s.signature) }
                    OutlinedTextField(signOff, { signOff = it; set { o -> o.copy(signOff = it) } }, label = { Text("Sign-off") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    OutlinedTextField(signature, { signature = it; set { o -> o.copy(signature = it) } }, label = { Text("Email signature") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2)
                    ToggleRow("“Notes by MeetingMind” at the end", if (s.profile.sensitive) "Always off for ${s.profile.label.lowercase()} work" else null, s.showFooter, enabled = !s.profile.sensitive) { on -> set { it.copy(footer = on) } }
                }
            }

            item { WorkSectionTitle("Privacy") }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    ToggleRow(
                        "Keep work on this phone",
                        if (s.profile.sensitive) "Always on for ${s.profile.label.lowercase()} work. Recordings are transcribed and summarised on the phone, even in Internet mode."
                        else "Work recordings are transcribed and summarised on the phone, even in Internet mode.",
                        s.keepOnDevice, enabled = !s.profile.sensitive
                    ) { on -> set { it.copy(onDeviceOnly = on) } }
                    ToggleRow("Remind me to tell people I'm recording", null, s.consentReminder) { on -> set { it.copy(consentReminder = on) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordField(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    Column {
        Text(label, fontSize = 13.sp, color = InkMuted)
        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf(value) + options).distinct().forEach { o -> Chip(o, o == value, Ink) { onChange(o) } }
        }
        var custom by remember { mutableStateOf("") }
        OutlinedTextField(custom, { custom = it }, placeholder = { Text("Your own word") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            trailingIcon = { if (custom.isNotBlank()) androidx.compose.material3.TextButton(onClick = { onChange(custom.trim()); custom = "" }) { Text("Use") } })
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.sp, color = Ink)
            subtitle?.let { Text(it, fontSize = 12.sp, color = InkMuted) }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
