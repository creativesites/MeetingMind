package com.craftflowtechnologies.meetingmind.feature.today.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.timeline.DeepTarget
import com.craftflowtechnologies.meetingmind.core.timeline.TimelineItem
import com.craftflowtechnologies.meetingmind.feature.today.HeroTile
import com.craftflowtechnologies.meetingmind.feature.today.HomeModes
import com.craftflowtechnologies.meetingmind.feature.today.InterFamily
import com.craftflowtechnologies.meetingmind.feature.today.ModeHome
import com.craftflowtechnologies.meetingmind.feature.today.OutfitFamily
import com.craftflowtechnologies.meetingmind.feature.today.TodayFocus
import com.craftflowtechnologies.meetingmind.feature.today.TodayViewModel
import com.craftflowtechnologies.meetingmind.feature.today.UpNextTile
import com.craftflowtechnologies.meetingmind.feature.today.itemIcon
import com.craftflowtechnologies.meetingmind.feature.today.timeFormat
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.LineSoft
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import java.text.DateFormat
import java.util.Date

/**
 * The Calm home: one quiet page. The shared header and mode switch, then the next thing, the
 * day as a plain list, a faith line where faith is shown, and two text actions. No cards, no
 * shadows, so the page has room to breathe.
 */
@Composable
fun CalmHome(
    today: TodayViewModel,
    onRecord: () -> Unit,
    onNewNote: () -> Unit,
    onSearch: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenProcessing: (String) -> Unit,
    onOpenDevotional: () -> Unit,
    onCustomize: () -> Unit,
    onNavigateBottomNav: (com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination) -> Unit
) {
    val identity by today.identity.collectAsState()
    val focus by today.focus.collectAsState()
    val todayItems by today.today.collectAsState()
    val upNext by today.upNext.collectAsState()
    val devotional by today.devotional.collectAsState()
    val offered = HomeModes.offered(ModeHome.CALM, identity.showsFaith)
    val mode = HomeModes.effective(focus, offered)
    val fmt = timeFormat()
    val listState = rememberLazyListState()

    val tile = when (val u = upNext) {
        is UpNextTile.Event -> HeroTile(
            label = "Up next", title = u.event.title.ifBlank { "Untitled" }, subtitle = fmt.format(Date(u.event.begin)),
            icon = Icons.Filled.Event, accent = Accent,
            onClick = { today.noteForEvent(u.event) { id, _ -> onOpenNote(id) } }
        )
        is UpNextTile.Item -> HeroTile(
            label = "Up next", title = u.item.title.ifBlank { "Untitled" }, subtitle = fmt.format(Date(u.item.start)),
            icon = itemIcon(u.item), accent = Color(u.item.accent), onClick = { openItem(u.item, today, onOpenNote, onOpenProcessing) }
        )
        UpNextTile.Nothing -> HeroTile(
            label = "Nothing scheduled", title = "Nothing next", subtitle = "Tap to record",
            icon = Icons.Filled.Mic, accent = Accent, onClick = onRecord
        )
    }

    Scaffold(
        containerColor = SurfaceBase,
        bottomBar = {
            com.craftflowtechnologies.meetingmind.core.ui.AppBottomNavigationBar(
                current = com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.HOME,
                onNavigate = { if (it != com.craftflowtechnologies.meetingmind.core.ui.BottomNavDestination.HOME) onNavigateBottomNav(it) }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("calm_home"), state = listState,
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            item {
                HomeModeHeader(
                    today = today, home = ModeHome.CALM, tile = tile, listState = listState, inboxCount = 0,
                    onSearch = onSearch, onInbox = onOpenInbox, onOpenDevotional = onOpenDevotional
                )
            }

            // The next thing, in large type with nothing around it.
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 28.dp)) {
                    Text("NEXT", color = InkMuted, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp)
                    Text(
                        tile.title, color = Ink, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp,
                        lineHeight = 30.sp, letterSpacing = (-0.4).sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp).clickable(onClick = tile.onClick)
                    )
                    Text(tile.subtitle, color = InkSecondary, fontFamily = InterFamily, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }

            // The day as a plain list, already filtered to the mode.
            item {
                Text(
                    "TODAY", color = InkMuted, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                    letterSpacing = 1.4.sp, modifier = Modifier.padding(start = 24.dp, top = 32.dp, bottom = 6.dp)
                )
            }
            if (todayItems.isEmpty()) item {
                Text("Nothing on your calendar today.", color = InkSecondary, fontFamily = InterFamily, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
            }
            items(todayItems.take(6), key = { "c-" + it.id }) { entry ->
                CalmRow(entry, fmt) { openItem(entry, today, onOpenNote, onOpenProcessing) }
            }

            // Faith, when this mode shows it.
            if (identity.showsFaith && mode != TodayFocus.WORK) item {
                Column(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenDevotional).padding(horizontal = 24.dp).padding(top = 32.dp)
                ) {
                    Text("TODAY'S DEVOTIONAL", color = InkMuted, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp)
                    Text(
                        devotional?.devotional?.title ?: "A word for your day", color = Ink, fontFamily = OutfitFamily,
                        fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            // Two quiet actions.
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 36.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    CalmAction("Record", onRecord)
                    CalmAction("Write a note", onNewNote)
                    Spacer(Modifier.weight(1f))
                    CalmAction("Customize", onCustomize)
                }
            }
        }
    }
}

/** One line of the day: the time, the title, and a hairline below. */
@Composable
private fun CalmRow(item: TimelineItem, fmt: DateFormat, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fmt.format(Date(item.start)), color = InkMuted, fontFamily = InterFamily, fontSize = 13.sp, modifier = Modifier.width(64.dp))
            Text(
                item.title.ifBlank { "Untitled" }, color = Ink, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(1.dp).background(LineSoft))
    }
}

@Composable
private fun CalmAction(label: String, onClick: () -> Unit) {
    Text(
        label, color = Accent, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp)
    )
}

private fun openItem(item: TimelineItem, today: TodayViewModel, onOpenNote: (String) -> Unit, onOpenProcessing: (String) -> Unit) = when (val t = item.target) {
    is DeepTarget.Note -> onOpenNote(t.noteId)
    is DeepTarget.Recording -> onOpenProcessing(t.meetingId)
    is DeepTarget.Event -> { today.noteForEvent(t.event) { id, _ -> onOpenNote(id) }; Unit }
}
