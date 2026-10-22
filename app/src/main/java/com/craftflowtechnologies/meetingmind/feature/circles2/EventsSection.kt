package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.circles2.CircleEvent
import com.craftflowtechnologies.meetingmind.core.circles2.EventDraft
import com.craftflowtechnologies.meetingmind.core.circles2.Rsvp
import com.craftflowtechnologies.meetingmind.core.circles2.RsvpAnswer
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val HOUR_MS = 3_600_000L

/** Shows an event's time in the zone it was made in, so everyone in the circle reads the same time. */
private fun whenText(startMs: Long, zone: String): String =
    SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone(zone) }.format(Date(startMs))

private fun dayPart(ms: Long, zone: String, pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone(zone) }.format(Date(ms))

/** The "Coming up" block on the Circle home: upcoming events, then a button to add one (admins). */
@Composable
fun EventsSection(events: List<CircleEvent>, now: Long, onOpen: (CircleEvent) -> Unit, onNew: (() -> Unit)?) {
    val upcoming = events.filter { !it.cancelled && it.endsAt >= now }.sortedBy { it.startsAt }
    Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
        SectionHeader("Coming up", count = upcoming.size)
        if (upcoming.isEmpty()) {
            MMCard {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MM.space.m)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MM.colors.surfaceSunk,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CalendarMonth, null, tint = MM.colors.inkMuted, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("No events planned yet", style = MM.type.bodyStrong, color = MM.colors.ink)
                        Text("Schedule a prayer session, bible study or team sync.", style = MM.type.caption, color = MM.colors.inkSecondary)
                    }
                }
            }
        } else {
            MMCard {
                upcoming.take(5).forEachIndexed { idx, e ->
                    if (idx > 0) HorizontalDivider(color = MM.colors.line.copy(alpha = 0.6f))
                    EventRow(e, onOpen)
                }
            }
        }
        if (onNew != null) SecondaryButton("New event", onNew, leadingIcon = Icons.Rounded.CalendarMonth, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun EventRow(e: CircleEvent, onOpen: (CircleEvent) -> Unit) {
    val where = e.location.takeIf { it.isNotBlank() } ?: e.onlineUrl?.takeIf { it.isNotBlank() }?.let { "Online" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpen(e) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DayTile(e)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                e.title,
                style = MM.type.bodyStrong.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = MM.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                whenText(e.startsAt, e.timeZone),
                style = MM.type.caption,
                color = MM.colors.inkSecondary
            )
            if (where != null) {
                Text(
                    where,
                    style = MM.type.caption.copy(fontSize = 11.5.sp),
                    color = MM.colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (e.cancelled) {
            Surface(shape = RoundedCornerShape(999.dp), color = CirclesPalette.RoseBg) {
                Text("Cancelled", style = MM.type.caption.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold), color = CirclesPalette.RoseInk, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        } else {
            Icon(Icons.Rounded.ChevronRight, null, tint = MM.colors.inkMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DayTile(e: CircleEvent) {
    val isDark = MM.colors.isDark
    Column(
        modifier = Modifier
            .size(width = 50.dp, height = 54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) MM.colors.surfaceSunk else CirclesPalette.AccentSoft)
            .border(1.dp, if (isDark) MM.colors.line else Color.Transparent, RoundedCornerShape(14.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            dayPart(e.startsAt, e.timeZone, "MMM").uppercase(),
            style = MM.type.overline.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
            color = if (isDark) MM.colors.accent else CirclesPalette.Accent
        )
        Text(
            dayPart(e.startsAt, e.timeZone, "d"),
            style = MM.type.heading.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            color = if (isDark) MM.colors.ink else CirclesPalette.AccentDeep
        )
    }
}

/** Details, who is coming, the person's own answer, and admin edit or cancel. */
@Composable
fun EventSheet(
    event: CircleEvent,
    rsvps: List<Rsvp>,
    myUid: String?,
    isAdmin: Boolean,
    onRsvp: (RsvpAnswer) -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val mine = rsvps.firstOrNull { it.uid == myUid }?.answer
    var confirmCancel by remember { mutableStateOf(false) }
    CircleSheet(onDismiss) {
        Text(event.title, style = MM.type.title, color = MM.colors.ink)
        Text(whenText(event.startsAt, event.timeZone), style = MM.type.secondary, color = MM.colors.inkSecondary)
        if (event.location.isNotBlank()) Text(event.location, style = MM.type.body, color = MM.colors.ink)
        event.onlineUrl?.let { Text("Online: $it", style = MM.type.secondary, color = MM.colors.inkSecondary) }
        if (event.description.isNotBlank()) Text(event.description, style = MM.type.body, color = MM.colors.ink)
        if (event.cancelled) {
            StatusLine(StatusKind.Warning, "This event was cancelled.")
        } else {
            val going = rsvps.count { it.answer == RsvpAnswer.Going }
            val maybe = rsvps.count { it.answer == RsvpAnswer.Maybe }
            Text("$going going · $maybe maybe", style = MM.type.secondary, color = MM.colors.inkSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                RsvpAnswer.entries.forEach { a -> MMChip(a.label, selected = mine == a, onClick = { onRsvp(a) }) }
            }
            if (isAdmin) {
                SecondaryButton("Edit event", onEdit, modifier = Modifier.fillMaxWidth())
                TextAction("Cancel event", { confirmCancel = true })
            }
        }
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel this event?", style = MM.type.heading, color = MM.colors.ink) },
            text = { Text("Everyone in the circle will see it as cancelled.", style = MM.type.body, color = MM.colors.inkSecondary) },
            confirmButton = { TextAction("Cancel event", { confirmCancel = false; onCancel() }) },
            dismissButton = { TextAction("Keep it", { confirmCancel = false }) }
        )
    }
}

/** Create or edit an event. Times come from quick choices; the Worker checks the link and the zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventFormSheet(existing: CircleEvent?, nowMs: Long, onSave: (EventDraft) -> Unit, onDismiss: () -> Unit) {
    val zone = TimeZone.getDefault().id
    val presets = remember(nowMs) {
        listOf(Calendar.WEDNESDAY to 19, Calendar.SUNDAY to 10, Calendar.SATURDAY to 9).map { (day, hour) -> nextAt(nowMs, zone, day, hour) }
    }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var location by remember { mutableStateOf(existing?.location.orEmpty()) }
    var link by remember { mutableStateOf(existing?.onlineUrl.orEmpty()) }
    var startAt by remember { mutableStateOf(existing?.startsAt ?: presets.first()) }
    val duration = existing?.let { it.endsAt - it.startsAt }?.takeIf { it > 0 } ?: HOUR_MS
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = startAt)
    val timeState = rememberTimePickerState(initialHour = dayPartInt(startAt, zone, "H"), initialMinute = dayPartInt(startAt, zone, "m"), is24Hour = false)
    if (showDate) {
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { showDate = false; dateState.selectedDateMillis?.let { startAt = withDate(startAt, it, zone) } }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = dateState) }
    }
    if (showTime) {
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = { showTime = false; startAt = withTime(startAt, timeState.hour, timeState.minute, zone) }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = timeState) }
        )
    }

    CircleSheet(onDismiss) {
        Text(if (existing == null) "New event" else "Edit event", style = MM.type.title, color = MM.colors.ink)
        CircleField(title, { title = it.take(80) }, "Title", placeholder = "Breakfast at Café Lumen")
        CircleField(description, { description = it.take(1000) }, "Details (optional)", minLines = 2, maxLines = 5)
        CircleField(location, { location = it.take(200) }, "Where (optional)", placeholder = "214 Maple St")
        CircleField(link, { link = it.take(300) }, "Online link (optional)", placeholder = "https://")
        Text("When", style = MM.type.bodyStrong, color = MM.colors.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            presets.forEachIndexed { i, at ->
                MMChip(presetLabel(i), selected = startAt == at, onClick = { startAt = at })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            SecondaryButton("Pick date", { showDate = true }, modifier = Modifier.weight(1f))
            SecondaryButton("Pick time", { showTime = true }, modifier = Modifier.weight(1f))
        }
        Text("${whenText(startAt, zone)} · shown in ${zone.replace('_', ' ')}", style = MM.type.secondary, color = MM.colors.inkSecondary)
        PrimaryButton(
            if (existing == null) "Add event" else "Save event",
            { onSave(EventDraft(title.trim(), description.trim(), location.trim(), link.trim().ifBlank { null }, startAt, startAt + duration, zone)) },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun presetLabel(i: Int): String = listOf("Wednesday 7 pm", "Sunday 10 am", "Saturday 9 am")[i]

/** The next occurrence of [dayOfWeek] at [hour] in [zone], strictly after [nowMs]. */
private fun nextAt(nowMs: Long, zone: String, dayOfWeek: Int, hour: Int): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    while (cal.timeInMillis <= nowMs || cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek) cal.add(Calendar.DAY_OF_YEAR, 1)
    return cal.timeInMillis
}

private fun hourMinute(ms: Long, zone: String): Calendar = Calendar.getInstance(TimeZone.getTimeZone(zone)).apply { timeInMillis = ms }

private fun dayPartInt(ms: Long, zone: String, part: String): Int {
    val cal = hourMinute(ms, zone)
    return if (part == "H") cal.get(Calendar.HOUR_OF_DAY) else cal.get(Calendar.MINUTE)
}

/** Keeps the time of day from [atMs] and takes the calendar date from [dateUtcMs] (the date picker reports UTC midnight). */
private fun withDate(atMs: Long, dateUtcMs: Long, zone: String): Long {
    val date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateUtcMs }
    val time = hourMinute(atMs, zone)
    return Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
        set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH), time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE), 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** Keeps the date from [atMs] and sets the time of day. */
private fun withTime(atMs: Long, hour: Int, minute: Int, zone: String): Long {
    val day = hourMinute(atMs, zone)
    return Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
        set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH), hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

