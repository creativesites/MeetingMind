package com.craftflowtechnologies.meetingmind.feature.work.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.companion.CompanionPage
import com.craftflowtechnologies.meetingmind.core.companion.CompanionState
import com.craftflowtechnologies.meetingmind.core.timeline.TimeOfDaySky
import com.craftflowtechnologies.meetingmind.core.timeline.TimelineItem
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.LocalCompanionVolume
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.ZuriSlot
import com.craftflowtechnologies.meetingmind.core.ui.mm.companion.rememberCompanionVisible
import com.craftflowtechnologies.meetingmind.feature.today.InterFamily
import com.craftflowtechnologies.meetingmind.feature.today.OutfitFamily
import com.craftflowtechnologies.meetingmind.feature.today.itemIcon
import com.craftflowtechnologies.meetingmind.feature.today.home.Reveal
import com.craftflowtechnologies.meetingmind.feature.today.home.SceneTile
import com.craftflowtechnologies.meetingmind.feature.today.home.TileHeader
import com.craftflowtechnologies.meetingmind.feature.today.home.WorkGridScene
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.BentoColors
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.LocalMMColors
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import java.text.DateFormat

private val WorkHeroCompanionSize = 132.dp

/**
 * The Work home's hero: a sky card in the Today hero's language (the sky follows the clock, the
 * companion sits in the corner). It leads with the next meeting, or with the most urgent item.
 */
@Composable
internal fun WorkHeroCard(
    sky: TimeOfDaySky,
    lateNight: Boolean,
    overline: String,
    title: String,
    line: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: () -> Unit,
    index: Int
) {
    val top = Color(sky.skyTop)
    val bottom = Color(sky.skyBottom)
    val companionShown = rememberCompanionVisible(CompanionPage.HOME)
    Reveal(index) {
        Box(Modifier.fillMaxWidth().heightIn(min = 196.dp).clip(RoundedCornerShape(28.dp)).testTag("work_home_hero")) {
            Canvas(Modifier.matchParentSize()) {
                drawRect(Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset(size.width * 0.6f, size.height)))
            }
            // A light wash from the left keeps the text readable over the sky.
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(SurfaceRaised.copy(alpha = 0.9f), SurfaceRaised.copy(alpha = 0.45f)))))
            if (companionShown) {
                Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 6.dp)) {
                    CompositionLocalProvider(LocalCompanionVolume provides true) {
                        ZuriSlot(
                            CompanionPage.HOME, WorkHeroCompanionSize,
                            state = if (lateNight) CompanionState.SLEEPY else CompanionState.IDLE,
                            offerShow = true
                        )
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth(if (companionShown) 0.6f else 0.9f).padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 18.dp)
            ) {
                Text(overline, color = InkSecondary, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(title, color = Ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 23.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                if (line != null) {
                    Text(line, color = InkSecondary, fontFamily = InterFamily, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroButton(primaryLabel, primary = true, onClick = onPrimary)
                    if (secondaryLabel != null) HeroButton(secondaryLabel, primary = false, onClick = onSecondary)
                }
            }
        }
    }
}

@Composable
private fun HeroButton(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (primary) Accent else Color.White.copy(alpha = 0.75f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(label, color = if (primary) FixedWhite else Ink, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

/**
 * The figures that matter this week, on the Work tile's dot grid. Each figure opens its list;
 * the tile itself opens Work. Every number comes from data the Work home already loads.
 */
@Composable
internal fun WorkGlanceTile(
    dueToday: Int,
    overdue: Int,
    waiting: Int,
    toSend: Int,
    decided: Int,
    onDue: () -> Unit,
    onWaiting: () -> Unit,
    onSend: () -> Unit,
    onDecided: () -> Unit,
    onOpenWork: () -> Unit,
    index: Int
) {
    val colors = LocalMMColors.current
    SceneTile(index, WorkGridScene, height = 150.dp, scrim = Color.Transparent, tag = "work_home_glance", onClick = onOpenWork) {
        TileHeader(Icons.Filled.Work, "At a glance", BentoColors.IndigoInk)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GlanceFigure("$dueToday", "Due today", if (overdue > 0) colors.danger else Accent, Modifier.weight(1f), onDue)
            GlanceFigure("$waiting", "Waiting on", colors.speaker3, Modifier.weight(1f), onWaiting)
            GlanceFigure("$toSend", "To send", colors.speaker4, Modifier.weight(1f), onSend)
            GlanceFigure("$decided", "Decided", colors.speaker2, Modifier.weight(1f), onDecided)
        }
    }
}

@Composable
private fun GlanceFigure(value: String, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 2.dp)) {
        Text(value, color = tint, fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        Text(label, color = InkSecondary, fontFamily = InterFamily, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A structured list card: a title row with an optional "All N" link, then rows. Calmer than a
 * scene tile, so the lists read as lists. Enters with the same stagger as the tiles.
 */
@Composable
internal fun WorkListCard(
    title: String,
    trailing: String?,
    onTrailing: (() -> Unit)?,
    tag: String,
    index: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Reveal(index) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceRaised)
                .border(1.dp, LineSoft, RoundedCornerShape(24.dp)).padding(vertical = 14.dp).testTag(tag)
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (trailing != null) {
                    Text(
                        trailing, color = if (onTrailing != null) Accent else InkMuted, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        modifier = if (onTrailing != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onTrailing).padding(horizontal = 6.dp, vertical = 4.dp) else Modifier
                    )
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 6.dp), content = content)
        }
    }
}

/** One thing that needs the person: what it is, why, and the one verb that handles it. */
@Composable
internal fun NeedLine(title: String, line: String, verb: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, color = InkMuted, fontFamily = InterFamily, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(verb, color = Accent, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

/** One entry in the day: a time column, a dot on a line, and the item. Past items recede; the live one is marked. */
@Composable
internal fun ScheduleAgendaRow(
    item: TimelineItem,
    fmt: DateFormat,
    state: ScheduleState,
    last: Boolean,
    onPrepare: (() -> Unit)?,
    onClick: () -> Unit
) {
    val past = state == ScheduleState.PAST
    val live = state == ScheduleState.LIVE
    val colors = LocalMMColors.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
        Text(
            fmt.format(java.util.Date(item.start)), color = if (past) InkMuted else Ink, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            modifier = Modifier.width(58.dp).padding(top = 12.dp)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(18.dp)) {
            Spacer(Modifier.height(15.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (live) colors.recording else if (past) LineSoft else Color(item.accent)))
            if (!last) Box(Modifier.width(2.dp).height(40.dp).background(LineSoft))
        }
        Row(
            Modifier.weight(1f).padding(start = 8.dp, top = 6.dp, bottom = 6.dp).clip(RoundedCornerShape(14.dp))
                .background(if (live) Accent.copy(alpha = 0.08f) else SurfaceRaised)
                .border(1.dp, LineSoft, RoundedCornerShape(14.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(itemIcon(item), null, tint = if (past) InkMuted else Color(item.accent), modifier = Modifier.size(18.dp))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(item.title.ifBlank { "Untitled" }, color = if (past) InkSecondary else Ink, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.subtitle?.let { Text(it, color = InkMuted, fontFamily = InterFamily, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (live) Text("NOW", color = colors.recording, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
            else if (onPrepare != null) Text(
                "Prepare", color = Accent, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onPrepare).padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/** The four quick captures, one tap each. */
@Composable
internal fun WorkCaptureRow(
    onRecord: () -> Unit,
    onNote: () -> Unit,
    onTask: () -> Unit,
    onImport: () -> Unit,
    onWork: () -> Unit
) {
    val colors = LocalMMColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        WorkQuickAction(Icons.Filled.Mic, "Record", colors.recording, onRecord)
        WorkQuickAction(Icons.AutoMirrored.Filled.NoteAdd, "Note", Accent, onNote)
        WorkQuickAction(Icons.Filled.AddTask, "Task", colors.speaker3, onTask)
        WorkQuickAction(Icons.Filled.FileUpload, "Import", colors.speaker4, onImport)
        WorkQuickAction(Icons.Filled.Work, "Work", colors.speaker2, onWork)
    }
}

@Composable
private fun WorkQuickAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(tint.copy(alpha = if (LocalMMColors.current.isDark) 0.18f else 0.10f)), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(label, color = InkSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
