package com.example.feature.devotional

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.devotional.ArchiveItem
import com.example.core.devotional.DevotionalRepository
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash
import com.example.ui.theme.FaithGold
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LineSoft
import com.example.ui.theme.LocalMMColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class DevotionalArchiveViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DevotionalRepository(app)
    private val _items = MutableStateFlow<List<ArchiveItem>?>(null)
    val items: StateFlow<List<ArchiveItem>?> = _items
    init { refresh() }
    fun refresh() = viewModelScope.launch { _items.value = runCatching { repo.archive() }.getOrDefault(emptyList()) }
}

/** Filters across the archive: everything, favourites, evenings, a format or a series. */
sealed class ArchiveFilter(val label: String) {
    data object All : ArchiveFilter("All")
    data object Favourites : ArchiveFilter("Favourites")
    data object Evenings : ArchiveFilter("Evenings")
    data class Format(val format: com.example.core.devotional.DevotionalFormat) : ArchiveFilter(format.label)
    data class Series(val title: String) : ArchiveFilter(title)

    fun accepts(i: ArchiveItem) = when (this) {
        All -> true
        Favourites -> i.favourite
        Evenings -> i.evening
        is Format -> i.format == format
        is Series -> i.series == title
    }
}

@Composable
fun DevotionalArchiveScreen(onNavigateBack: () -> Unit, onOpen: (String) -> Unit, vm: DevotionalArchiveViewModel = viewModel()) {
    val items by vm.items.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refresh() }
    DevotionalArchive(items, onNavigateBack, onOpen)
}

@Composable
fun DevotionalArchive(items: List<ArchiveItem>?, onNavigateBack: () -> Unit, onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<ArchiveFilter>(ArchiveFilter.All) }
    var calendar by remember { mutableStateOf(false) }
    val all = items.orEmpty()
    val filters = remember(all) {
        buildList<ArchiveFilter> {
            add(ArchiveFilter.All); add(ArchiveFilter.Favourites)
            if (all.any { it.evening }) add(ArchiveFilter.Evenings)
            all.mapNotNull { it.format }.distinct().forEach { add(ArchiveFilter.Format(it)) }
            all.mapNotNull { it.series }.distinct().forEach { add(ArchiveFilter.Series(it)) }
        }
    }
    val shown = all.filter { filter.accepts(it) && it.matches(query) }
    val background = LocalMMColors.current.background

    Column(Modifier.fillMaxSize().background(background).statusBarsPadding().testTag("devotional_archive_screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
            Column(Modifier.weight(1f)) {
                Text("Past devotionals", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, fontFamily = FontFamily.Serif)
                Text(if (items == null) "Loading…" else "${all.size} saved", fontSize = 12.sp, color = InkMuted)
            }
            Row(Modifier.padding(end = 10.dp).clip(RoundedCornerShape(50)).border(1.dp, LineSoft, RoundedCornerShape(50))) {
                listOf(false to "List", true to "Calendar").forEach { (c, label) ->
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (calendar == c) Ink else InkMuted,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (calendar == c) AccentWash else background).clickable { calendar = c }.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        // Search: title, passage, series, format or words.
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, LineSoft, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search titles, passages, words…", color = InkFaint, fontSize = 14.sp)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, color = Ink), cursorBrush = SolidColor(Accent), modifier = Modifier.fillMaxWidth().testTag("archive_search"))
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            filters.forEach { f ->
                val on = f == filter
                Text(f.label, fontSize = 13.sp, color = if (on) Ink else InkSecondary, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) AccentWash else background).border(1.dp, if (on) Accent.copy(alpha = 0.5f) else LineSoft, RoundedCornerShape(50))
                        .clickable { filter = f }.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            items == null -> Unit
            all.isEmpty() -> Empty("No devotionals yet. Each one you read is kept here.")
            calendar -> CalendarView(shown, onOpen)
            shown.isEmpty() -> Empty("Nothing matches.")
            else -> ArchiveList(shown, onOpen)
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, color = InkMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp))
}

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DAY = DateTimeFormatter.ofPattern("EEE d")

@Composable
private fun ArchiveList(items: List<ArchiveItem>, onOpen: (String) -> Unit) {
    val byMonth = items.groupBy { runCatching { YearMonth.from(LocalDate.parse(it.day)) }.getOrNull() }
    LazyColumn(Modifier.fillMaxSize()) {
        byMonth.forEach { (month, rows) ->
            item(key = "m-$month") {
                Text(month?.format(MONTH)?.uppercase() ?: "UNDATED", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp))
            }
            items(rows, key = { it.noteId }) { ArchiveRow(it, onOpen) }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun ArchiveRow(i: ArchiveItem, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onOpen(i.noteId) }.padding(horizontal = 20.dp)) {
        Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
            Text(runCatching { LocalDate.parse(i.day).format(DAY) }.getOrDefault(i.day) + if (i.evening) "\nevening" else "",
                fontSize = 12.sp, lineHeight = 16.sp, color = InkMuted, modifier = Modifier.width(58.dp))
            Column(Modifier.weight(1f)) {
                Text(i.title, fontSize = 16.sp, lineHeight = 21.sp, color = Ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(i.passage, i.format?.label, i.series).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, fontSize = 12.5.sp, color = InkSecondary, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (i.favourite) Icon(Icons.Filled.Star, contentDescription = "Favourite", tint = FaithGold, modifier = Modifier.padding(start = 8.dp).size(16.dp))
        }
        HorizontalDivider(color = LineSoft, thickness = 0.5.dp)
    }
}

@Composable
private fun CalendarView(items: List<ArchiveItem>, onOpen: (String) -> Unit) {
    val byDay = items.groupBy { it.day }
    var month by remember { mutableStateOf(items.firstOrNull()?.day?.let { runCatching { YearMonth.from(LocalDate.parse(it)) }.getOrNull() } ?: YearMonth.now()) }
    var picked by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month", tint = Ink) }
                Text(month.format(MONTH), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next month", tint = Ink) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, fontSize = 11.sp, color = InkMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
            }
            val first = month.atDay(1)
            val lead = first.dayOfWeek.value - 1
            val cells = lead + month.lengthOfMonth()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                for (week in 0 until (cells + 6) / 7) {
                    Row(Modifier.fillMaxWidth()) {
                        for (dow in 0 until 7) {
                            val n = week * 7 + dow - lead + 1
                            Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                if (n in 1..month.lengthOfMonth()) {
                                    val iso = month.atDay(n).toString()
                                    val has = byDay[iso].orEmpty()
                                    val on = picked == iso
                                    Column(
                                        Modifier.size(40.dp).clip(CircleShape).background(if (on) AccentWash else LocalMMColors.current.background)
                                            .clickable(enabled = has.isNotEmpty()) { picked = iso },
                                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                                    ) {
                                        Text("$n", fontSize = 14.sp, color = if (has.isNotEmpty()) Ink else InkFaint, fontWeight = if (has.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal)
                                        if (has.isNotEmpty()) Box(Modifier.padding(top = 2.dp).size(4.dp).clip(CircleShape).background(if (has.any { it.favourite }) FaithGold else Accent))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val list = picked?.let { byDay[it] } ?: items.filter { runCatching { YearMonth.from(LocalDate.parse(it.day)) }.getOrNull() == month }
        items(list, key = { it.noteId }) { ArchiveRow(it, onOpen) }
    }
}
