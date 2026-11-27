package com.craftflowtechnologies.meetingmind.feature.personal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.personal.JournalMoodVibe
import com.craftflowtechnologies.meetingmind.core.personal.PersonalNoteMeta
import com.craftflowtechnologies.meetingmind.core.personal.PersonalRepository
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal data class CalendarCell(
    val dayOfMonth: Int?,
    val epochDay: Long?
)

internal data class MonthGrid(
    val year: Int,
    val monthZeroBased: Int,
    val title: String,
    val weeks: List<List<CalendarCell>>
)

/**
 * Pure minSdk-24-safe Mon..Sun month grid builder for the Living Journal Calendar view.
 */
internal fun buildMonthGrid(year: Int, monthZeroBased: Int): MonthGrid {
    val cal = Calendar.getInstance().apply {
        clear()
        set(year, monthZeroBased, 1, 12, 0, 0)
    }
    val title = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        else -> 6
    }

    val cells = mutableListOf<CalendarCell>()
    repeat(firstDayOfWeek) { cells += CalendarCell(null, null) }
    for (day in 1..daysInMonth) {
        cal.set(Calendar.DAY_OF_MONTH, day)
        val epochDay = PersonalRepository.epochDayOf(cal.timeInMillis)
        cells += CalendarCell(dayOfMonth = day, epochDay = epochDay)
    }
    while (cells.size % 7 != 0) {
        cells += CalendarCell(null, null)
    }
    return MonthGrid(
        year = year,
        monthZeroBased = monthZeroBased,
        title = title,
        weeks = cells.chunked(7)
    )
}

/**
 * Interactive Mon..Sun Calendar grid for [JournalViewMode.MOSAIC] with mood-colored dots,
 * month navigation, mood legend, and tap-to-filter day selection.
 */
@Composable
internal fun JournalCalendarGridCard(
    year: Int,
    monthZeroBased: Int,
    todayEpochDay: Long,
    selectedEpochDay: Long?,
    notesByEpochDay: Map<Long, List<Note>>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (Long?) -> Unit
) {
    val c = MM.colors
    val grid = remember(year, monthZeroBased) { buildMonthGrid(year, monthZeroBased) }
    val weekdayHeaders = remember { listOf("M", "T", "W", "T", "F", "S", "S") }

    MMCard(modifier = Modifier.testTag("journal_calendar_grid")) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month", tint = c.inkSecondary)
            }
            Text(
                grid.title,
                style = MM.type.heading,
                color = c.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month", tint = c.inkSecondary)
            }
        }

        Spacer(Modifier.height(MM.space.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            weekdayHeaders.forEach { h ->
                Text(
                    h,
                    style = MM.type.caption,
                    color = c.inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(MM.space.xs))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            grid.weeks.forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { cell ->
                        if (cell.dayOfMonth == null || cell.epochDay == null) {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val dayNotes = notesByEpochDay[cell.epochDay].orEmpty()
                            val hasNotes = dayNotes.isNotEmpty()
                            val isSelected = selectedEpochDay == cell.epochDay
                            val isToday = todayEpochDay == cell.epochDay
                            val vibe = dayNotes.firstNotNullOfOrNull {
                                JournalMoodVibe.fromNameOrNull(it.metadata[PersonalNoteMeta.MOOD_VIBE])
                            }
                            val dotColor = when (vibe) {
                                JournalMoodVibe.JOYFUL -> c.gold
                                JournalMoodVibe.GRATEFUL -> c.speaker3
                                JournalMoodVibe.CELEBRATORY -> c.speaker2
                                else -> c.accent
                            }
                            Surface(
                                shape = MM.radius.small,
                                color = when {
                                    isSelected -> c.accentWash
                                    hasNotes -> dotColor.copy(alpha = 0.14f)
                                    else -> c.surface
                                },
                                border = when {
                                    isSelected -> BorderStroke(1.dp, c.accent)
                                    isToday -> BorderStroke(1.dp, c.line)
                                    else -> null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clickable {
                                        onSelectDay(if (isSelected) null else cell.epochDay)
                                    }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        "${cell.dayOfMonth}",
                                        style = if (isToday || isSelected) MM.type.bodyStrong else MM.type.caption,
                                        color = if (isSelected) c.accent else c.ink
                                    )
                                    if (hasNotes) {
                                        Spacer(Modifier.height(2.dp))
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(MM.space.m))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MM.space.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(color = c.gold, label = "Joyful")
            LegendDot(color = c.speaker3, label = "Grateful")
            LegendDot(color = c.accent, label = "Reflective")
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(MM.space.xs))
        Text(label, style = MM.type.caption, color = MM.colors.inkMuted)
    }
}
