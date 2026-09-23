package com.example.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.model.RecordingType
import com.example.core.timeline.ItemKind
import com.example.core.timeline.TimelineDays
import com.example.core.timeline.TimelineItem
import com.example.core.timeline.TimelineLayer
import java.io.File
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private val InkNavy = Color(0xFF0F172A)
private val Slate = Color(0xFF64748B)
private val Hairline = Color(0xFFE2E8F0)
private val Sunk = Color(0xFFF6F7FB)

fun layerColor(layer: TimelineLayer) = Color(layer.color)

fun itemIcon(item: TimelineItem): ImageVector = when {
    item.kind == ItemKind.EVENT -> Icons.Filled.Event
    item.kind == ItemKind.MEMORY -> Icons.Filled.History
    item.kind == ItemKind.ANSWERED_PRAYER -> Icons.Filled.Favorite
    item.workflow == RecordingType.SERMON -> Icons.Filled.Church
    item.workflow == RecordingType.DEVOTIONAL -> Icons.Filled.WbSunny
    item.layer == TimelineLayer.FAITH -> Icons.Filled.AutoStories
    item.kind == ItemKind.RECORDING -> Icons.Filled.Mic
    else -> Icons.Filled.EditNote
}

@Composable
fun timeFormat(): DateFormat {
    val context = LocalContext.current
    return remember { android.text.format.DateFormat.getTimeFormat(context) }
}

fun timeLabel(item: TimelineItem, fmt: DateFormat): String = when {
    item.allDay -> "All day"
    item.end != null && item.end > item.start && item.kind == ItemKind.EVENT -> fmt.format(Date(item.start)) + " – " + fmt.format(Date(item.end))
    else -> fmt.format(Date(item.start))
}

/**
 * A timeline card: the note's photo as its background when there is one, else a gradient in the
 * layer's (or the event's calendar) colour. Tall cards are for the river; compact ones for lists.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineCard(
    item: TimelineItem,
    onOpen: (TimelineItem) -> Unit,
    onLongPress: (TimelineItem) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 104.dp,
    showDate: Boolean = false
) {
    val fmt = timeFormat()
    val accent = Color(item.accent)
    val hasCover = item.coverPath != null && File(item.coverPath).exists()
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.08f), lerp(accent, InkNavy, 0.55f))))
            .combinedClickable(onClick = { onOpen(item) }, onLongClick = { onLongPress(item) })
            .testTag("timeline_card")
    ) {
        if (hasCover) {
            AsyncImage(model = File(item.coverPath!!), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // Scrim so text reads on any photo.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (hasCover) 0.15f else 0f), Color.Black.copy(alpha = if (hasCover) 0.62f else 0.18f)))))
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(itemIcon(item), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Text(
                    (if (showDate) java.text.SimpleDateFormat("EEE d MMM · ", java.util.Locale.getDefault()).format(Date(item.start)) else "") + timeLabel(item, fmt),
                    color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontFamily = InterFamily, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 1
                )
                item.badge?.let {
                    Text(it, color = Color.White, fontSize = 11.sp, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 9.dp, vertical = 3.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Text(item.title, color = Color.White, fontSize = if (height > 130.dp) 20.sp else 16.sp, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold,
                maxLines = if (height > 130.dp) 2 else 1, overflow = TextOverflow.Ellipsis)
            item.subtitle?.let {
                Text(it, color = Color.White.copy(alpha = 0.78f), fontSize = 12.5.sp, fontFamily = InterFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The week strip: seven days, today ringed, the selected day filled, dots per layer. */
@Composable
fun WeekStrip(
    selectedDay: Long,
    activity: Map<Int, List<TimelineLayer>>,
    accent: Color,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val start = TimelineDays.startOfWeek(selectedDay)
    val todayKey = TimelineDays.key(System.currentTimeMillis())
    val dayNames = remember { java.text.DateFormatSymbols.getInstance().shortWeekdays }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(7) { i ->
            val day = TimelineDays.addDays(start, i)
            val key = TimelineDays.key(day)
            val selected = key == TimelineDays.key(selectedDay)
            val cal = Calendar.getInstance().apply { timeInMillis = day }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(44.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (selected) InkNavy else Color.Transparent)
                    .border(if (key == todayKey && !selected) 1.5.dp else 0.dp, if (key == todayKey && !selected) accent else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable { onSelect(day) }
                    .padding(vertical = 8.dp)
                    .testTag("week_day_$i")
            ) {
                Text(dayNames[cal.get(Calendar.DAY_OF_WEEK)].take(2), fontSize = 11.sp, color = if (selected) Color.White.copy(alpha = 0.7f) else Slate, fontFamily = InterFamily)
                Text("${cal.get(Calendar.DAY_OF_MONTH)}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else InkNavy, fontFamily = OutfitFamily)
                Row(Modifier.height(8.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    activity[key].orEmpty().take(4).forEach { l -> Box(Modifier.size(5.dp).clip(CircleShape).background(layerColor(l))) }
                }
            }
        }
    }
}

/** Agenda: day headers, then that day's cards. Empty days are skipped, except the first. */
@Composable
fun AgendaView(items: List<TimelineItem>, from: Long, onOpen: (TimelineItem) -> Unit, onLong: (TimelineItem) -> Unit, onPlan: (Long) -> Unit) {
    val byDay = items.groupBy { it.dayKey }
    val days = (0 until 14).map { TimelineDays.addDays(from, it) }.filter { TimelineDays.key(it) in byDay || it == from }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { day ->
            DayHeader(day)
            val list = byDay[TimelineDays.key(day)].orEmpty()
            if (list.isEmpty()) EmptyDay(day, onPlan)
            list.forEach { TimelineCard(it, onOpen, onLong) }
        }
        if (days.size == 1) Text("Nothing more in the next two weeks.", color = Slate, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun DayHeader(day: Long) {
    val today = TimelineDays.key(System.currentTimeMillis())
    val key = TimelineDays.key(day)
    val label = when (key) {
        today -> "Today"
        TimelineDays.key(TimelineDays.addDays(System.currentTimeMillis(), 1)) -> "Tomorrow"
        TimelineDays.key(TimelineDays.addDays(System.currentTimeMillis(), -1)) -> "Yesterday"
        else -> java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(Date(day))
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = InkNavy, fontFamily = OutfitFamily)
        Text("  " + java.text.SimpleDateFormat("d MMMM", java.util.Locale.getDefault()).format(Date(day)), fontSize = 13.sp, color = Slate, fontFamily = InterFamily)
    }
}

@Composable
private fun EmptyDay(day: Long, onPlan: (Long) -> Unit) {
    val future = day > System.currentTimeMillis() - 86_400_000L
    Surface(onClick = { onPlan(day) }, shape = RoundedCornerShape(18.dp), color = Sunk, border = BorderStroke(1.dp, Hairline), modifier = Modifier.fillMaxWidth()) {
        Text(if (future) "Nothing yet — tap to add a note for this day" else "A quiet day", color = Slate, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
    }
}

/** Day: an hour grid with events as blocks and recordings as bars at their real time. */
@Composable
fun DayView(items: List<TimelineItem>, day: Long, onOpen: (TimelineItem) -> Unit, onLong: (TimelineItem) -> Unit) {
    val hourHeight = 56.dp
    val fmt = timeFormat()
    val allDay = items.filter { it.allDay }
    val timed = items.filter { !it.allDay }
    Column(Modifier.padding(horizontal = 16.dp)) {
        allDay.forEach { TimelineCard(it, onOpen, onLong, height = 64.dp, modifier = Modifier.padding(bottom = 6.dp)) }
        val firstHour = (timed.minOfOrNull { Calendar.getInstance().apply { timeInMillis = it.start }.get(Calendar.HOUR_OF_DAY) } ?: 7).coerceAtMost(7)
        val hours = (firstHour..23).toList()
        BoxWithConstraints(Modifier.fillMaxWidth().height(hourHeight * hours.size)) {
            hours.forEachIndexed { i, h ->
                Row(Modifier.offset(y = hourHeight * i).fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(fmt.format(Calendar.getInstance().apply { timeInMillis = day; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, 0) }.time),
                        fontSize = 10.sp, color = Slate, modifier = Modifier.width(52.dp))
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(Hairline))
                }
            }
            // "Now" line.
            val now = System.currentTimeMillis()
            if (TimelineDays.key(now) == TimelineDays.key(day)) {
                val y = minutesFrom(day, firstHour, now) / 60f
                Box(Modifier.offset(x = 48.dp, y = hourHeight * y).fillMaxWidth().height(2.dp).background(Color(0xFFE11D48)))
            }
            timed.forEachIndexed { index, item ->
                val y = minutesFrom(day, firstHour, item.start) / 60f
                val len = (((item.end ?: (item.start + 30 * 60_000L)) - item.start) / 60_000f / 60f).coerceIn(0.5f, 6f)
                val lane = overlapLane(timed, index)
                val width = (maxWidth - 56.dp) / lane.second
                Surface(
                    onClick = { onOpen(item) }, shape = RoundedCornerShape(12.dp), color = Color(item.accent).copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color(item.accent).copy(alpha = 0.5f)),
                    modifier = Modifier.offset(x = 56.dp + width * lane.first, y = hourHeight * y).width(width - 4.dp).height(hourHeight * len - 3.dp)
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(itemIcon(item), contentDescription = null, tint = Color(item.accent), modifier = Modifier.size(13.dp))
                            Text(" " + item.title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = InkNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (len >= 1f) Text(timeLabel(item, fmt), fontSize = 11.sp, color = Slate, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun minutesFrom(day: Long, firstHour: Int, at: Long): Float = ((at - day) / 60_000f - firstHour * 60f).coerceAtLeast(0f)

/** Side-by-side lanes for overlapping items: (lane, lanes in its group). */
private fun overlapLane(items: List<TimelineItem>, index: Int): Pair<Int, Int> {
    val me = items[index]
    val myEnd = me.end ?: (me.start + 30 * 60_000L)
    val group = items.filter { o -> val e = o.end ?: (o.start + 30 * 60_000L); o.start < myEnd && e > me.start }
    return group.indexOf(me).coerceAtLeast(0) to group.size.coerceAtLeast(1)
}

/** Week: seven columns with a block per item, coloured by layer. Tap a day to open it. */
@Composable
fun WeekView(items: List<TimelineItem>, weekStart: Long, onDay: (Long) -> Unit, onOpen: (TimelineItem) -> Unit) {
    val byDay = items.groupBy { it.dayKey }
    val names = remember { java.text.DateFormatSymbols.getInstance().shortWeekdays }
    Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(7) { i ->
            val day = TimelineDays.addDays(weekStart, i)
            val list = byDay[TimelineDays.key(day)].orEmpty()
            val today = TimelineDays.key(day) == TimelineDays.key(System.currentTimeMillis())
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (today) Color(0x0F4F46E5) else Sunk).clickable { onDay(day) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val cal = Calendar.getInstance().apply { timeInMillis = day }
                Text(names[cal.get(Calendar.DAY_OF_WEEK)].take(1), fontSize = 10.sp, color = Slate)
                Text("${cal.get(Calendar.DAY_OF_MONTH)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = InkNavy)
                list.take(6).forEach { item ->
                    Box(
                        Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(item.accent).copy(alpha = 0.18f))
                            .clickable { onOpen(item) }.padding(3.dp)
                    ) {
                        Text(item.title, fontSize = 9.sp, lineHeight = 11.sp, color = InkNavy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (list.size > 6) Text("+${list.size - 6}", fontSize = 10.sp, color = Slate)
                if (list.isEmpty()) Spacer(Modifier.height(34.dp))
            }
        }
    }
}

/** Month: tiles with a photo from the day when there is one, and dots for everything else. */
@Composable
fun MonthView(items: List<TimelineItem>, gridStart: Long, month: Long, selectedDay: Long, onDay: (Long) -> Unit) {
    val byDay = items.groupBy { it.dayKey }
    val monthNum = Calendar.getInstance().apply { timeInMillis = month }.get(Calendar.MONTH)
    val names = remember { java.text.DateFormatSymbols.getInstance().shortWeekdays }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { i ->
                val c = Calendar.getInstance().apply { timeInMillis = TimelineDays.addDays(gridStart, i) }
                Text(names[c.get(Calendar.DAY_OF_WEEK)].take(2), fontSize = 11.sp, color = Slate, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { d ->
                    val day = TimelineDays.addDays(gridStart, week * 7 + d)
                    val cal = Calendar.getInstance().apply { timeInMillis = day }
                    val inMonth = cal.get(Calendar.MONTH) == monthNum
                    val list = byDay[TimelineDays.key(day)].orEmpty()
                    val cover = list.firstNotNullOfOrNull { it.coverPath?.takeIf { p -> File(p).exists() } }
                    val selected = TimelineDays.key(day) == TimelineDays.key(selectedDay)
                    val today = TimelineDays.key(day) == TimelineDays.key(System.currentTimeMillis())
                    // Busier days glow a little more.
                    val heat = (list.size / 5f).coerceIn(0f, 1f)
                    Box(
                        Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (list.isEmpty()) Sunk else Color(list.first().accent).copy(alpha = 0.10f + 0.25f * heat))
                            .border(if (selected) 2.dp else if (today) 1.dp else 0.dp, if (selected) InkNavy else if (today) Color(0xFF4F46E5) else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { onDay(day) }
                    ) {
                        if (cover != null) {
                            AsyncImage(model = File(cover), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                        }
                        Text(
                            "${cal.get(Calendar.DAY_OF_MONTH)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = when { cover != null -> Color.White; inMonth -> InkNavy; else -> Slate.copy(alpha = 0.5f) },
                            modifier = Modifier.padding(6.dp)
                        )
                        Row(Modifier.align(Alignment.BottomStart).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            list.map { it.layer }.distinct().take(4).forEach { l -> Box(Modifier.size(5.dp).clip(CircleShape).background(if (cover != null) Color.White else layerColor(l))) }
                        }
                    }
                }
            }
        }
    }
}
