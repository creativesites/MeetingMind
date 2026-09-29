package com.craftflowtechnologies.meetingmind.feature.devotional

import com.craftflowtechnologies.meetingmind.ui.theme.FaithGoldInk
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.forTheme
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalProfile
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalSource
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalTone
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalTopics
import com.craftflowtechnologies.meetingmind.core.devotional.Tradition
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary

/** The pages of devotional settings. */
private enum class SettingsPage(val title: String) {
    TRADITION("Tradition"), DELIVERY("When it arrives"), STYLE("Style & format"), SERIES("Series"),
    FOCUS("Focus"), INCLUDED("What's included"), VOICE("Read aloud"), PICTURE("Picture"), PRIVACY("Privacy")
}

/**
 * Everything about the daily devotional, as a settings page: sections with a summary of what's
 * chosen, each opening its own page. Nothing here is required; a preset sets a coherent starting
 * point and everything it sets stays editable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevotionalSettingsSheet(profile: DevotionalProfile, onSave: (DevotionalProfile, rewriteToday: Boolean) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) { DevotionalSettingsContent(profile, onSave, onDismiss) }
}

/** The settings page itself; [startPage] opens a section directly (screenshots, deep links). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevotionalSettingsContent(profile: DevotionalProfile, onSave: (DevotionalProfile, rewriteToday: Boolean) -> Unit, onDismiss: () -> Unit, startPage: String? = null) {
    var p by remember { mutableStateOf(profile) }
    var page by remember { mutableStateOf(startPage?.let { n -> SettingsPage.entries.firstOrNull { it.name == n } }) }
    androidx.activity.compose.BackHandler(enabled = page != null) { page = null }
    run {
        val bg = com.craftflowtechnologies.meetingmind.ui.theme.LocalMMColors.current.background
        Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding().testTag("devotional_settings")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = { if (page != null) page = null else onDismiss() }) {
                    androidx.compose.material3.Icon(
                        if (page != null) androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack else androidx.compose.material.icons.Icons.Filled.Close,
                        contentDescription = if (page != null) "Back" else "Close", tint = Ink
                    )
                }
                Text(page?.title ?: "Devotional", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { onSave(p, false) }, modifier = Modifier.testTag("devotional_settings_save")) {
                    Text("Save", color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
                when (page) {
                    null -> Overview(p) { page = it }
                    SettingsPage.TRADITION -> TraditionPage(p) { p = it }
                    SettingsPage.DELIVERY -> DeliveryPage(p) { p = it }
                    SettingsPage.STYLE -> StylePage(p) { p = it }
                    SettingsPage.SERIES -> SeriesPage(p) { p = it }
                    SettingsPage.FOCUS -> FocusPage(p) { p = it }
                    SettingsPage.INCLUDED -> IncludedPage(p) { p = it }
                    SettingsPage.VOICE -> VoiceSection(p.voice) { p = p.copy(voice = it) }
                    SettingsPage.PICTURE -> {
                        ToggleRow("Paint a picture for each day", "Made by Gemini when Internet mode is on, labelled as AI-generated; used for stories and sharing", p.autoImage) { p = p.copy(autoImage = it) }
                        if (p.autoImage) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            com.craftflowtechnologies.meetingmind.core.share.ImageStyle.entries.forEach { s -> Chip(s.label, p.imageStyle == s.name) { p = p.copy(imageStyle = s.name) } }
                        }
                    }
                    SettingsPage.PRIVACY -> ToggleRow(
                        "Let my prayer requests and journal shape it",
                        "When written by Gemini or DeepSeek, a few of their lines are sent with the request. Written on your phone, they never leave it. This only affects what the app sends on its own — the notes assistant works with any note you ask it to.",
                        p.sharePrivateWithCloud
                    ) { p = p.copy(sharePrivateWithCloud = it) }
                }
                Spacer(Modifier.height(24.dp))
                if (page == null) {
                    val changedContent = p.copy(enabled = profile.enabled, deliveryMinutes = profile.deliveryMinutes, lessOf = profile.lessOf, moreOf = profile.moreOf) != profile
                    if (changedContent) PillButton("Save and write a new one", filled = false) { onSave(p, true) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun Overview(p: DevotionalProfile, open: (SettingsPage) -> Unit) {
    Text("Make it yours", fontSize = 26.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 8.dp))
    Text("Everything is optional. A tradition sets a starting point; you can change any of it.", fontSize = 14.sp, lineHeight = 20.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
    val formats = if (p.rotateFormats) "${p.formats.size} formats take turns" else (p.fixedFormat ?: p.formats.firstOrNull())?.label ?: "Reflection"
    SectionRow(SettingsPage.TRADITION.title, listOfNotNull(p.preset?.label, p.tradition.label).distinct().joinToString(" · "), open, SettingsPage.TRADITION)
    SectionRow(SettingsPage.DELIVERY.title, if (p.enabled) "Every morning at %d:%02d".format(p.deliveryMinutes / 60, p.deliveryMinutes % 60) + if (p.eveningExamen) " · evening Examen" else "" else "When you open it", open, SettingsPage.DELIVERY)
    SectionRow(SettingsPage.STYLE.title, "${p.source.label} · $formats · ${p.minutes} min · ${p.tone.label}", open, SettingsPage.STYLE)
    SectionRow(SettingsPage.SERIES.title, p.series?.let { s -> "${s.title} · day ${(s.dayIndex(java.time.LocalDate.now().toEpochDay()) + 1).coerceAtMost(s.passages.size)} of ${s.passages.size}" } ?: "None — a new passage each day", open, SettingsPage.SERIES)
    SectionRow(SettingsPage.FOCUS.title, listOfNotNull(p.topics.take(3).joinToString(", ").ifBlank { null }, p.season, "no passage twice in ${p.passageExclusionDays} days").joinToString(" · "), open, SettingsPage.FOCUS)
    SectionRow(SettingsPage.INCLUDED.title, listOfNotNull("prayer".takeIf { p.includePrayer }, "a word for today".takeIf { p.includeMotivation }, "quote".takeIf { p.includeInsight }, "question".takeIf { p.includeQuestion }).joinToString(", ").ifBlank { "Just the reflection" }, open, SettingsPage.INCLUDED)
    SectionRow(SettingsPage.VOICE.title, "${p.voice.style.label} · ${p.voice.gender.label}", open, SettingsPage.VOICE)
    SectionRow(SettingsPage.PICTURE.title, if (p.autoImage) "A picture each day" else "Off", open, SettingsPage.PICTURE)
    SectionRow(SettingsPage.PRIVACY.title, if (p.sharePrivateWithCloud) "Prayer lines may shape it" else "Prayer and journal stay out", open, SettingsPage.PRIVACY)
}

@Composable
private fun SectionRow(title: String, summary: String, open: (SettingsPage) -> Unit, page: SettingsPage) {
    Column(Modifier.fillMaxWidth().clickable { open(page) }) {
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Medium)
                Text(summary, fontSize = 13.sp, lineHeight = 18.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp))
            }
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.ChevronRight, contentDescription = null, tint = InkMuted)
        }
        androidx.compose.material3.HorizontalDivider(color = com.craftflowtechnologies.meetingmind.ui.theme.LineSoft, thickness = 0.5.dp)
    }
}

/** A choice with a line of explanation — presets, formats, series. */
@Composable
private fun OptionCard(title: String, description: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = if (selected) Gold.copy(alpha = 0.10f) else SurfaceBase,
        border = BorderStroke(if (selected) 1.5.dp else 0.5.dp, if (selected) Gold else Line), modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (selected) FaithGoldInk else Ink)
            Text(description, fontSize = 12.5.sp, lineHeight = 17.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TraditionPage(p: DevotionalProfile, set: (DevotionalProfile) -> Unit) {
    Label("Start from a tradition")
    Text("Sets the tradition, formats and voice together. Change anything afterwards.", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(bottom = 10.dp))
    com.craftflowtechnologies.meetingmind.core.devotional.TraditionPreset.entries.forEach { preset ->
        OptionCard(preset.label, preset.description, p.preset == preset, Modifier.fillMaxWidth().padding(vertical = 4.dp)) { set(preset.applyTo(p)) }
    }
    Label("Tradition")
    Text("Shapes wording and the church calendar — never doctrine you haven't chosen.", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(bottom = 10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Tradition.entries.forEach { t -> Chip(t.label, p.tradition == t) { set(p.copy(tradition = t)) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeliveryPage(p: DevotionalProfile, set: (DevotionalProfile) -> Unit) {
    ToggleRow("Every morning", "Have it ready and send a gentle notification", p.enabled) { set(p.copy(enabled = it)) }
    if (p.enabled) {
        Label("Arrives at")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val presets = listOf(5 * 60, 5 * 60 + 30, 6 * 60, 6 * 60 + 30, 7 * 60, 8 * 60, 9 * 60)
            presets.forEach { m -> Chip("%d:%02d".format(m / 60, m % 60), p.deliveryMinutes == m) { set(p.copy(deliveryMinutes = m)) } }
            val context = androidx.compose.ui.platform.LocalContext.current
            val custom = p.deliveryMinutes !in presets
            Chip(if (custom) "%d:%02d".format(p.deliveryMinutes / 60, p.deliveryMinutes % 60) else "Other time…", custom) {
                android.app.TimePickerDialog(context, { _, h, min -> set(p.copy(deliveryMinutes = h * 60 + min)) },
                    p.deliveryMinutes / 60, p.deliveryMinutes % 60, android.text.format.DateFormat.is24HourFormat(context)).show()
            }
        }
        DeliveryHealth()
    }
    Spacer(Modifier.height(10.dp))
    ToggleRow("Evening Examen", "In the evening, look back on the day with God — it remembers this morning's passage and what it asked", p.eveningExamen) { set(p.copy(eveningExamen = it)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StylePage(p: DevotionalProfile, set: (DevotionalProfile) -> Unit) {
    Label("Where it comes from")
    DevotionalSource.entries.forEach { s -> OptionCard(s.label, s.description, p.source == s, Modifier.fillMaxWidth().padding(vertical = 4.dp)) { set(p.copy(source = s)) } }
    if (p.source != DevotionalSource.AI && p.source != DevotionalSource.MIX) return
    Label("Format")
    ToggleRow("Mix it up", "Formats take turns — never the same one two days running", p.rotateFormats) { set(p.copy(rotateFormats = it)) }
    Text(if (p.rotateFormats) "Choose the formats that take turns" else "Choose one format for every day", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
    com.craftflowtechnologies.meetingmind.core.devotional.DevotionalFormat.entries.filter { it != com.craftflowtechnologies.meetingmind.core.devotional.DevotionalFormat.DAILY_EXAMEN }.forEach { f ->
        val on = if (p.rotateFormats) f in p.formats else (p.fixedFormat ?: p.formats.firstOrNull()) == f
        OptionCard(f.label, f.description + (f.tradition?.let { " · $it" } ?: ""), on, Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            set(if (p.rotateFormats) p.copy(formats = if (on && p.formats.size > 1) p.formats - f else p.formats + f) else p.copy(fixedFormat = f))
        }
    }
    Label("Length")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(3, 7, 12).forEach { m -> Chip("$m min", p.minutes == m) { set(p.copy(minutes = m)) } } }
    Label("Voice")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { DevotionalTone.entries.forEach { t -> Chip(t.label, p.tone == t) { set(p.copy(tone = t)) } } }
    Label("Written for")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.craftflowtechnologies.meetingmind.core.devotional.DevotionalAudience.entries.forEach { a -> Chip(a.label, p.audience == a) { set(p.copy(audience = a)) } }
    }
    Label("Reading level")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { com.craftflowtechnologies.meetingmind.core.devotional.ReadingLevel.entries.forEach { r -> Chip(r.label, p.readingLevel == r) { set(p.copy(readingLevel = r)) } } }
    Label("Language")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("" to "App language", "English" to "English", "French" to "French", "Portuguese" to "Portuguese", "Spanish" to "Spanish", "Swahili" to "Swahili", "Bemba" to "Bemba", "Nyanja" to "Nyanja").forEach { (v, l) ->
            Chip(l, p.language == v) { set(p.copy(language = v)) }
        }
    }
}

@Composable
private fun SeriesPage(p: DevotionalProfile, set: (DevotionalProfile) -> Unit) {
    val today = java.time.LocalDate.now().toEpochDay()
    p.series?.let { s ->
        Label("Following")
        val day = (s.dayIndex(today) + 1).coerceAtMost(s.passages.size)
        Text(s.title, fontSize = 20.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(if (s.finished(today)) "Finished — well done" else "Day $day of ${s.passages.size} · today: ${s.passageFor(today)}", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 2.dp))
        androidx.compose.material3.LinearProgressIndicator(progress = { day.toFloat() / s.passages.size }, color = Gold, trackColor = com.craftflowtechnologies.meetingmind.ui.theme.LineSoft,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(4.dp))
        PillButton("Stop this series", filled = false) { set(p.copy(series = null)) }
    }
    Label(if (p.series == null) "Start a series" else "Or start another")
    Text("One passage a day, each day building on the last.", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(bottom = 8.dp))
    com.craftflowtechnologies.meetingmind.core.devotional.DevotionalSeries.catalog.forEach { s ->
        OptionCard(s.title, "${s.description} · ${s.passages.size} days", p.series?.seriesId == s.id, Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            set(p.copy(series = com.craftflowtechnologies.meetingmind.core.devotional.SeriesProgress(s.id, s.title, s.passages, today)))
        }
    }
    Label("Through a book")
    var book by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(book, { book = it }, singleLine = true, placeholder = { Text("e.g. Mark, Ruth, James") }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        val parsed = book.trim().takeIf { it.isNotEmpty() }?.let { com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser.parse("$it 1")?.book }
        PillButton("Start", filled = parsed != null) {
            parsed?.let { b -> val s = com.craftflowtechnologies.meetingmind.core.devotional.DevotionalSeries.throughBook(b.name, b.chapterCount); set(p.copy(series = com.craftflowtechnologies.meetingmind.core.devotional.SeriesProgress(s.id, s.title, s.passages, today))) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusPage(p: DevotionalProfile, set: (DevotionalProfile) -> Unit) {
    Label("What you'd like to grow in")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DevotionalTopics.all.forEach { t -> Chip(t, t in p.topics) { set(p.copy(topics = if (t in p.topics) p.topics - t else p.topics + t)) } }
    }
    Label("Where life is right now (optional)")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DevotionalTopics.seasons.forEach { s -> Chip(s, p.season == s) { set(p.copy(season = if (p.season == s) null else s)) } }
    }
    Label("About you (optional)")
    OutlinedTextField(value = p.aboutMe, onValueChange = { set(p.copy(aboutMe = it.take(400))) }, minLines = 2,
        placeholder = { Text("e.g. Nurse on night shifts, mum of two, learning to rest") }, modifier = Modifier.fillMaxWidth())
    Text("Used quietly in the background — it shapes what's chosen, but won't be repeated back to you.", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 6.dp))
    Label("Passage variety")
    Text("A passage won't come back within…", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(bottom = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(14, 30, 60, 90).forEach { d -> Chip("$d days", p.passageExclusionDays == d) { set(p.copy(passageExclusionDays = d)) } } }
}

@Composable
private fun IncludedPage(p: DevotionalProfile, set: (DevotionalProfile) -> Unit) {
    ToggleRow("A prayer", null, p.includePrayer) { set(p.copy(includePrayer = it)) }
    ToggleRow("A word for today", "One line of encouragement to carry with you", p.includeMotivation) { set(p.copy(includeMotivation = it)) }
    ToggleRow("A quote", "From the great Christian writers and hymns", p.includeInsight) { set(p.copy(includeInsight = it)) }
    ToggleRow("A question to sit with", null, p.includeQuestion) { set(p.copy(includeQuestion = it)) }
}

/**
 * What could stop the devotional arriving on time — notifications switched off, or Android
 * holding back exact alarms — with the one tap that fixes it. Shows nothing when all is well.
 */
@Composable
private fun DeliveryHealth() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableStateOf(0) }
    // Re-checked when the person comes back from the system settings page.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val notificationsOn = remember(tick) { androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exact = remember(tick) { com.craftflowtechnologies.meetingmind.core.notify.DailyAlarms.exactAllowed(context) }
    if (!notificationsOn) {
        HealthRow("Notifications are off for MeetingMind", "Turn on") {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    }
    if (!exact && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        HealthRow("Android may deliver it a few minutes late", "Allow exact time") {
            runCatching {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(android.net.Uri.parse("package:" + context.packageName)))
            }
        }
    }
}

@Composable
private fun HealthRow(text: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 12.5.sp, lineHeight = 17.sp, color = InkSecondary, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Chip(action, true, onClick)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50), color = if (selected) Gold.copy(alpha = 0.14f) else com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase,
        border = BorderStroke(1.dp, if (selected) Gold else Line)
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) FaithGoldInk else InkSecondary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, fontSize = 12.5.sp, lineHeight = 17.sp, color = InkSecondary) }
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Gold))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceSection(voice: com.craftflowtechnologies.meetingmind.ai.voice.VoiceSettings, onChange: (com.craftflowtechnologies.meetingmind.ai.voice.VoiceSettings) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var previewing by remember { mutableStateOf(false) }
    var previewFailed by remember { mutableStateOf(false) }
    Label("Read aloud by")
    com.craftflowtechnologies.meetingmind.ai.voice.PreacherStyle.entries.forEach { s ->
        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = voice.style == s, onClick = { onChange(voice.copy(style = s)) }, colors = RadioButtonDefaults.colors(selectedColor = Gold))
            Column(Modifier.padding(top = 10.dp)) {
                Text(s.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
                Text(s.line, fontSize = 12.5.sp, color = InkSecondary)
            }
        }
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.craftflowtechnologies.meetingmind.ai.voice.VoiceGender.entries.forEach { g -> Chip(g.label, voice.gender == g) { onChange(voice.copy(gender = g)) } }
    }
    Label("Pace")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.85f to "Slower", 1.0f to "Natural", 1.15f to "Brisk").forEach { (r, l) -> Chip(l, kotlin.math.abs(voice.rate - r) < 0.01f) { onChange(voice.copy(rate = r)) } }
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton(if (previewing) "Preparing…" else "Hear it", filled = false) {
            if (previewing) return@PillButton
            previewing = true; previewFailed = false
            scope.launch {
                val file = runCatching { com.craftflowtechnologies.meetingmind.core.devotional.DevotionalVoice(context).preview(voice) }.getOrNull()
                previewing = false
                if (file != null) com.craftflowtechnologies.meetingmind.core.audio.PlaybackController.play(context, "preview:${voice.voiceName}", "Voice preview", file) else previewFailed = true
            }
        }
        if (previewFailed) Text("  No voice is available right now.", fontSize = 12.sp, color = InkMuted)
    }
    Text("Gemini's voices need Internet mode and your key; otherwise your phone's own voice reads it.", fontSize = 12.sp, lineHeight = 17.sp, color = InkMuted, modifier = Modifier.padding(top = 6.dp))
    ToggleRow("Pray the prayer aloud", null, voice.speakPrayer) { onChange(voice.copy(speakPrayer = it)) }
    ToggleRow("Have the voice ready each morning", "Records it with the devotional, so Listen starts instantly", voice.autoVoice) { onChange(voice.copy(autoVoice = it)) }
}
