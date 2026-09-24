package com.example.feature.faith

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.common.Formatters
import com.example.core.model.AttachmentKind
import com.example.core.model.Note
import com.example.core.model.RecordingType
import com.example.core.scripture.BibleBooks
import com.example.core.scripture.PassageResult
import com.example.core.scripture.ScriptureReference
import com.example.core.scripture.toReference
import com.example.feature.scripture.VerseSheet
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line
import com.example.ui.theme.SurfaceSunk
import java.io.File

/** The Faith space's warm accent, used only here and on Faith notes' markers. */
private val Gold = Color(0xFFB7791F)
private val GoldWash = Color(0x14B7791F)

fun faithIcon(type: RecordingType): ImageVector = when (type) {
    RecordingType.SERMON -> Icons.Filled.Church
    RecordingType.BIBLE_STUDY -> Icons.Filled.School
    RecordingType.DEVOTIONAL -> Icons.Filled.WbSunny
    RecordingType.PRAYER -> Icons.Filled.VolunteerActivism
    RecordingType.PRAYER_REQUEST -> Icons.Filled.Favorite
    RecordingType.TESTIMONY -> Icons.Filled.AutoAwesome
    RecordingType.GRATITUDE -> Icons.Filled.EmojiEmotions
    RecordingType.REFLECTION -> Icons.Filled.SelfImprovement
    else -> Icons.Filled.MenuBook
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FaithScreen(
    viewModel: FaithViewModel,
    onNavigateBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onRecordSermon: () -> Unit,
    onOpenJourney: () -> Unit,
    onOpenScripture: () -> Unit,
    onOpenBible: () -> Unit = {},
    onSearchBible: () -> Unit = {},
    onReadPassage: (ScriptureReference) -> Unit = {},
    onOpenDevotional: () -> Unit = {},
    onShare: (com.example.feature.share.ShareRequest) -> Unit = {},
    onPrayWithMe: () -> Unit = {}
) {
    val votd by viewModel.verseOfTheDay.collectAsState()
    val devotional by viewModel.todayDevotional.collectAsState()
    val requests by viewModel.openRequests.collectAsState()
    val answered by viewModel.answeredCount.collectAsState()
    val onThisDay by viewModel.onThisDay.collectAsState()
    val journey by viewModel.journey.collectAsState()
    val themes by viewModel.themes.collectAsState()
    val notes by viewModel.faithNotes.collectAsState()
    val allScripture by viewModel.allScripture.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val media by viewModel.media.collectAsState()
    var verseSheet by remember { mutableStateOf<ScriptureReference?>(null) }
    var themeSheet by remember { mutableStateOf<String?>(null) }

    Scaffold(containerColor = Color.White) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 20.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
                    Column {
                        Text("Faith", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink)
                        Text("Sermons, prayer and your walk, in one place", fontSize = 13.sp, color = InkMuted)
                    }
                }
            }

            // Today's devotional, written for the day (PLAN_V2 F2).
            item { TodayDevotionalCard(devotional?.devotional, onOpenDevotional) }

            // Today: the Verse of the Day, leading into a devotional.
            item {
                VerseOfTheDayCard(
                    votd = votd,
                    onRead = { votd?.let { onReadPassage(it.reference) } },
                    onShare = {
                        val v = votd
                        val found = v?.passage as? PassageResult.Found
                        if (v != null && found != null) onShare(com.example.feature.share.ShareRequest(
                            com.example.core.share.ShareCardContent("Verse of the day", found.passage.text, "${v.reference.display()} · ${found.passage.versionAbbreviation}", found.passage.attribution),
                            theme = found.passage.text.take(200)
                        ))
                    },
                    onStartDevotional = { votd?.let { v -> viewModel.startDevotional(v.reference, onOpenNote) } }
                )
            }

            // The Bible itself: read, and search.
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(onClick = onOpenBible, shape = RoundedCornerShape(16.dp), color = GoldWash, modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
                            Column(Modifier.padding(start = 10.dp)) {
                                Text("Bible", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text("Read any chapter", fontSize = 12.sp, color = InkSecondary)
                            }
                        }
                    }
                    Surface(onClick = onSearchBible, shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.weight(1f)) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
                            Column(Modifier.padding(start = 10.dp)) {
                                Text("Search", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text("Words or references", fontSize = 12.sp, color = InkSecondary)
                            }
                        }
                    }
                }
            }

            // Begin: one wide card to record a sermon, then the ways to write, as tinted tiles.
            item {
                Column(Modifier.padding(top = 28.dp)) {
                    SectionTitle("Begin")
                    RecordSermonCard(onRecordSermon)
                    Spacer(Modifier.height(10.dp))
                    PrayWithMeCard(onPrayWithMe)
                    Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StartKinds.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { kind -> StartTile(kind, Modifier.weight(1f)) { viewModel.create(kind.type, onOpenNote) } }
                            }
                        }
                    }
                }
            }

            // What you're praying for.
            if (requests.isNotEmpty() || answered > 0) {
                item {
                    SectionTitle("Praying for", trailing = if (answered > 0) "$answered answered" else null, top = 28.dp)
                }
                items(requests, key = { "req-" + it.id }) { r ->
                    PrayerRequestRow(r, onOpen = { onOpenNote(r.id) }, onAnswered = { viewModel.markAnswered(r, onOpenNote) })
                }
            }

            if (onThisDay.isNotEmpty()) {
                item { SectionTitle("On this day", top = 28.dp) }
                items(onThisDay, key = { "otd-" + it.id }) { n -> FaithNoteRow(n, showYear = true) { onOpenNote(n.id) } }
            }

            // Journey at a glance.
            if (journey.isNotEmpty()) {
                item {
                    SectionTitle("Your journey", trailing = "See all", onTrailing = onOpenJourney, top = 28.dp)
                    val thisMonth = journey.first()
                    Surface(shape = RoundedCornerShape(18.dp), color = SurfaceSunk, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable(onClick = onOpenJourney)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(thisMonth.label, fontSize = 13.sp, color = InkMuted)
                            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                thisMonth.counts.entries.sortedByDescending { it.value }.forEach { (type, count) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(faithIcon(type), contentDescription = null, tint = Gold, modifier = Modifier.size(15.dp))
                                        Text(" $count ${type.displayName.lowercase()}${if (count > 1 && !type.displayName.endsWith("y")) "s" else ""}", fontSize = 14.sp, color = Ink)
                                    }
                                }
                            }
                            Text("${notes.size} entries in all", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                }
            }

            if (themes.isNotEmpty()) {
                item {
                    SectionTitle("Themes", top = 28.dp)
                    FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        themes.take(16).forEach { t ->
                            Surface(onClick = { themeSheet = t.name }, shape = RoundedCornerShape(50), color = Color.White, border = BorderStroke(1.dp, Line)) {
                                Text("${t.name} · ${t.count}", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
            }

            // Scripture you've heard and saved.
            item {
                SectionTitle("Scripture", trailing = "Open", onTrailing = onOpenScripture, top = 28.dp)
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("${allScripture.map { it.bookUsfm to it.chapter }.distinct().size}", "passages in your notes", Modifier.weight(1f), onOpenScripture)
                    StatCard("${collections.size}", if (collections.size == 1) "collection" else "collections", Modifier.weight(1f), onOpenScripture)
                }
            }

            if (media.isNotEmpty()) {
                item {
                    SectionTitle("Photos, videos & voice", trailing = "${media.size}", top = 28.dp)
                    val titles = remember(notes) { notes.associate { it.id to it.title.ifBlank { it.workflow.displayName } } }
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(media.take(24), key = { it.id }) { a -> FaithMediaCard(a, titles[a.noteId]) { onOpenNote(a.noteId) } }
                    }
                }
            }
            item {
                var showBackgrounds by remember { mutableStateOf(false) }
                Surface(onClick = { showBackgrounds = true }, shape = RoundedCornerShape(16.dp), color = SurfaceSunk, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Wallpaper, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("Backgrounds", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text("Pictures for your stories, devotional and share cards — add your own", fontSize = 12.sp, color = InkSecondary)
                        }
                    }
                }
                if (showBackgrounds) com.example.feature.share.BackgroundsSheet(onDismiss = { showBackgrounds = false })
            }

            item { SectionTitle("Recent", top = 28.dp) }
            if (notes.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet. Record a sermon, or start with today's verse — everything you write here stays on your phone.",
                        fontSize = 14.sp, color = InkSecondary, modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
            items(notes.take(12), key = { "recent-" + it.id }) { n -> FaithNoteRow(n) { onOpenNote(n.id) } }
        }
    }

    verseSheet?.let { VerseSheet(reference = it, onDismiss = { verseSheet = null }) }
    themeSheet?.let { theme ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { themeSheet = null },
            containerColor = Color.White,
            title = { Text(theme) },
            text = {
                Column {
                    val matching = viewModel.notesMatching(theme)
                    if (matching.isEmpty()) Text("Tagged notes and recordings about this appear here.", color = InkSecondary)
                    matching.take(20).forEach { n ->
                        Text(n.title.ifBlank { n.workflow.displayName }, fontSize = 15.sp, color = Accent,
                            modifier = Modifier.fillMaxWidth().clickable { themeSheet = null; onOpenNote(n.id) }.padding(vertical = 8.dp))
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { themeSheet = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun TodayDevotionalCard(today: com.example.core.devotional.Devotional?, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(26.dp), color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp).testTagSafe("faith_today_devotional")
    ) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFFE8C7), Color(0xFFFFF6E9), Color(0xFFF3E8FF))))) {
            Box(Modifier.align(Alignment.TopEnd).size(150.dp).offset(x = 40.dp, y = (-50).dp)
                .background(Brush.radialGradient(listOf(Color(0xFFFFC266).copy(alpha = 0.7f), Color.Transparent)), CircleShape))
            Column(Modifier.padding(20.dp)) {
                Text("TODAY'S DEVOTIONAL", fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = Gold)
                Text(
                    today?.title ?: "A word for your day",
                    fontSize = 21.sp, lineHeight = 27.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    today?.let { t -> t.scripture.firstOrNull()?.display() ?: t.engine?.takeIf { t.origin == com.example.core.devotional.DevotionalOrigin.CLASSIC } }
                        ?: "Scripture, a reflection and a prayer — written for you, or a classic.",
                    fontSize = 13.sp, lineHeight = 18.sp, color = InkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)
                )
                Row(Modifier.padding(top = 14.dp).clip(RoundedCornerShape(50)).background(Ink).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WbSunny, contentDescription = null, tint = Color(0xFFF6D365), modifier = Modifier.size(15.dp))
                    Text(if (today != null) "  Read today's" else "  Begin", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun VerseOfTheDayCard(votd: VerseOfTheDay?, onRead: () -> Unit, onStartDevotional: () -> Unit, onShare: () -> Unit = {}) {
    Surface(shape = RoundedCornerShape(24.dp), color = Ink, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp)) {
        Column(Modifier.clickable(enabled = votd != null, onClick = onRead).padding(20.dp)) {
            Text("VERSE OF THE DAY", fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE9C46A))
            when {
                votd == null -> Text("Today's verse appears here when you're online.", fontSize = 15.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 10.dp))
                else -> {
                    val found = votd.passage as? PassageResult.Found
                    if (found != null) {
                        Text("“${found.passage.text}”", fontSize = 19.sp, lineHeight = 29.sp, fontFamily = FontFamily.Serif, color = Color.White,
                            maxLines = 7, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
                    }
                    Text(
                        votd.reference.display() + (found?.let { " · ${it.passage.versionAbbreviation}" } ?: ""),
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(top = 10.dp)
                    )
                    found?.let { Text(it.passage.attribution, fontSize = 9.sp, lineHeight = 12.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }
                    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(onClick = onStartDevotional, shape = RoundedCornerShape(50), color = Color.White) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.WbSunny, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
                                Text("  Write on it", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            }
                        }
                        if (found != null) Surface(onClick = onShare, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.14f), modifier = Modifier.padding(start = 10.dp)) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Text("  Share", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A way to begin: the kind of note, what it's for, and its own colour. */
private data class StartKind(val type: RecordingType, val line: String, val tint: Color)

private val StartKinds = listOf(
    StartKind(RecordingType.DEVOTIONAL, "Begin with a verse", Color(0xFFB7791F)),
    StartKind(RecordingType.PRAYER, "Talk to God, in your words", Color(0xFF7C3AED)),
    StartKind(RecordingType.PRAYER_REQUEST, "Hold on to what you're asking", Color(0xFFDB2777)),
    StartKind(RecordingType.GRATITUDE, "Count today's blessings", Color(0xFF059669)),
    StartKind(RecordingType.TESTIMONY, "Tell what God has done", Color(0xFFEA580C)),
    StartKind(RecordingType.REFLECTION, "Sit with a thought", Color(0xFF2563EB)),
    StartKind(RecordingType.BIBLE_STUDY, "Observe, reflect, apply", Color(0xFF0F766E))
)

@Composable
private fun PrayWithMeCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(26.dp), color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(104.dp).testTagSafe("faith_pray_with_me")
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF0E1A3A), Color(0xFF3B2F6B), Color(0xFF7C5CA8))))) {
            Box(Modifier.align(Alignment.CenterEnd).size(150.dp).offset(x = 30.dp)
                .background(Brush.radialGradient(listOf(Color(0xFFDBEAFE).copy(alpha = 0.45f), Color.Transparent)), CircleShape))
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pray with me", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Color.White)
                    Text("Someone to pray with, out loud — your way", fontSize = 13.sp, color = Color.White.copy(alpha = 0.72f), modifier = Modifier.padding(top = 4.dp))
                }
                Box(Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFDBEAFE), Color(0xFF818CF8)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.VolunteerActivism, contentDescription = null, tint = Color(0xFF1E1B4B), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun RecordSermonCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(26.dp), color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(118.dp).testTagSafe("faith_record_sermon")
    ) {
        Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B1530), Color(0xFF3A2A1A))))
        ) {
            // Warm light from the right.
            Box(Modifier.align(Alignment.CenterEnd).size(170.dp).offset(x = 40.dp)
                .background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.45f), Color.Transparent)), CircleShape))
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Record a sermon", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Color.White)
                    Text("Scripture, key points and notes — gathered for you", fontSize = 13.sp, lineHeight = 18.sp, color = Color.White.copy(alpha = 0.72f), modifier = Modifier.padding(top = 4.dp))
                }
                Box(Modifier.size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFF6D365), Gold))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Mic, contentDescription = null, tint = Color(0xFF1B1530), modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
private fun StartTile(kind: StartKind, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(22.dp), color = Color.White,
        border = BorderStroke(1.dp, kind.tint.copy(alpha = 0.18f)), shadowElevation = 1.dp,
        modifier = modifier.height(122.dp)
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(kind.tint.copy(alpha = 0.10f), Color.White)))) {
            Column(Modifier.padding(14.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(kind.tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(faithIcon(kind.type), contentDescription = null, tint = kind.tint, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(kind.type.displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink, maxLines = 1)
                Text(kind.line, fontSize = 12.sp, lineHeight = 16.sp, color = InkSecondary, maxLines = 2)
            }
        }
    }
}

private fun Modifier.testTagSafe(tag: String) = this.then(Modifier.testTag(tag))


@Composable
private fun SectionTitle(text: String, trailing: String? = null, onTrailing: (() -> Unit)? = null, top: androidx.compose.ui.unit.Dp = 0.dp) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = top, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = InkMuted, modifier = Modifier.weight(1f))
        trailing?.let {
            Text(it, fontSize = 13.sp, color = if (onTrailing != null) Accent else InkMuted, fontWeight = FontWeight.Medium,
                modifier = if (onTrailing != null) Modifier.clickable(onClick = onTrailing) else Modifier)
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = SurfaceSunk, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink, fontFamily = FontFamily.Serif)
            Text(label, fontSize = 12.sp, color = InkSecondary)
        }
    }
}

@Composable
private fun PrayerRequestRow(note: Note, onOpen: () -> Unit, onAnswered: () -> Unit) {
    val days = ((System.currentTimeMillis() - note.createdAt) / 86_400_000L).toInt()
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Favorite, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(note.title.ifBlank { "Prayer request" }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (days <= 0) "Started today" else "Praying for $days day${if (days == 1) "" else "s"}", fontSize = 12.sp, color = InkMuted)
        }
        Surface(onClick = onAnswered, shape = RoundedCornerShape(50), color = GoldWash) {
            Text("Answered", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Gold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun FaithNoteRow(note: Note, showYear: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(GoldWash), contentAlignment = Alignment.Center) {
            Icon(faithIcon(note.workflow), contentDescription = null, tint = Gold, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(note.title.ifBlank { note.workflow.displayName }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val date = note.eventDate ?: note.createdAt
            Text(
                note.workflow.displayName + " · " + if (showYear) java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault()).format(java.util.Date(date)) else Formatters.formatDateRelative(date),
                fontSize = 12.sp, color = InkMuted
            )
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = InkFaint, modifier = Modifier.size(16.dp))
    }
}

/** The journey: every Faith entry, month by month. */
@Composable
fun FaithJourneyScreen(viewModel: FaithViewModel, onNavigateBack: () -> Unit, onOpenNote: (String) -> Unit) {
    val journey by viewModel.journey.collectAsState()
    Scaffold(containerColor = Color.White) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
                    Text("Your journey", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink)
                }
            }
            if (journey.isEmpty()) item { Text("Your sermons, prayers and reflections will gather here, month by month.", color = InkSecondary, modifier = Modifier.padding(20.dp)) }
            journey.forEach { month ->
                item(key = "m-" + month.label) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp)) {
                        Text(month.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(month.counts.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.value} ${it.key.displayName.lowercase()}" }, fontSize = 12.sp, color = InkMuted)
                    }
                }
                items(month.notes, key = { "j-" + it.id }) { n -> FaithNoteRow(n) { onOpenNote(n.id) } }
            }
        }
    }
}

/** Scripture across your notes, by book, and your collections. */
@Composable
fun FaithScriptureScreen(viewModel: FaithViewModel, onNavigateBack: () -> Unit, onOpenNote: (String) -> Unit) {
    val refs by viewModel.allScripture.collectAsState()
    val collections by viewModel.collections.collectAsState()
    var sheet by remember { mutableStateOf<ScriptureReference?>(null) }
    val byBook = remember(refs) {
        refs.mapNotNull { r -> r.toReference()?.let { it to r } }
            .groupBy { it.first.book }
            .toSortedMap(compareBy { BibleBooks.all.indexOf(it) })
    }
    Scaffold(containerColor = Color.White) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
                    Text("Scripture", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink)
                }
            }
            if (collections.isNotEmpty()) {
                item { SectionTitle("Collections", top = 10.dp) }
                items(collections, key = { "c-" + it.id }) { c -> CollectionRow(c.id, c.name, viewModel, onVerse = { sheet = it }) }
            }
            item {
                val context = androidx.compose.ui.platform.LocalContext.current
                val highlights by androidx.compose.runtime.produceState(emptyList<Triple<ScriptureReference, String, Long>>()) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { com.example.core.scripture.BibleStore.get(context).allHighlights(60) }.getOrDefault(emptyList()) }
                }
                if (highlights.isNotEmpty()) {
                    SectionTitle("Highlighted", trailing = "${highlights.size}", top = 22.dp)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Consecutive highlighted verses of one colour read as one passage.
                        val merged = highlights.sortedWith(compareBy({ BibleBooks.all.indexOf(it.first.book) }, { it.first.chapter }, { it.first.verseStart })).fold(mutableListOf<Triple<ScriptureReference, String, Long>>()) { acc, h ->
                            val last = acc.lastOrNull()
                            val lr = last?.first
                            if (lr != null && lr.book == h.first.book && lr.chapter == h.first.chapter && last.second == h.second && (lr.verseEnd ?: lr.verseStart!!) + 1 == h.first.verseStart)
                                acc[acc.size - 1] = Triple(lr.copy(verseEnd = h.first.verseStart), last.second, last.third)
                            else acc += h
                            acc
                        }
                        merged.forEach { (ref, color, _) ->
                            Surface(onClick = { sheet = ref }, shape = RoundedCornerShape(50), color = com.example.feature.bible.HighlightColors[color] ?: AccentWash) {
                                Text(ref.display(), fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                        }
                    }
                }
            }
            item { SectionTitle("In your notes", trailing = "${refs.size}", top = 22.dp) }
            if (byBook.isEmpty()) item { Text("References from sermons and notes appear here, book by book.", color = InkSecondary, modifier = Modifier.padding(horizontal = 20.dp)) }
            byBook.forEach { (book, list) ->
                item(key = "b-" + book.usfm) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(book.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            list.distinctBy { it.first }.forEach { (ref, row) ->
                                val count = list.count { it.first == ref }
                                Surface(onClick = { sheet = ref }, shape = RoundedCornerShape(50), color = AccentWash) {
                                    Text(ref.display().removePrefix(book.name + " ").removePrefix("Psalm ") + if (count > 1) " ×$count" else "", fontSize = 12.sp, color = Accent,
                                        fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    sheet?.let { VerseSheet(reference = it, onDismiss = { sheet = null }) }
}

@Composable
private fun CollectionRow(id: String, name: String, viewModel: FaithViewModel, onVerse: (ScriptureReference) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { com.example.core.repository.NoteRepository(context, com.example.core.database.MeetMindDatabase.getInstance(context)) }
    val items by repo.observeCollectionItems(id).collectAsState(initial = emptyList())
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("$name · ${items.size}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { item ->
                val ref = BibleBooks.byUsfm(item.bookUsfm)?.let { ScriptureReference(it, item.chapter, item.verseStart, item.verseEnd) } ?: return@forEach
                Surface(onClick = { onVerse(ref) }, shape = RoundedCornerShape(50), color = GoldWash) {
                    Text(ref.display(), fontSize = 12.sp, color = Gold, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
    }
}

/**
 * One photo, video or voice recording from the Faith notes: what it is, where it's from and how
 * long, at a glance. Videos show a real frame; voice pulses gently while it plays.
 */
@Composable
private fun FaithMediaCard(a: com.example.core.model.Attachment, noteTitle: String?, onOpen: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playback by com.example.core.audio.PlaybackController.state.collectAsState()
    val playing = playback.recordingId == "media:${a.id}" && playback.phase == com.example.core.audio.PlaybackPhase.PLAYING
    val generated = a.caption?.contains("AI-generated") == true
    val (kindLabel, tint) = when (a.kind) {
        AttachmentKind.IMAGE -> (if (generated) "Devotional picture" else "Photo") to Color(0xFF0EA5E9)
        AttachmentKind.VIDEO -> "Video" to Color(0xFFE11D48)
        AttachmentKind.AUDIO -> (if (a.caption?.startsWith("Listen:") == true) "Devotional voice" else "Voice") to Gold
        else -> "File" to InkMuted
    }
    Surface(onClick = {
        if (a.kind == AttachmentKind.AUDIO) {
            if (playing) com.example.core.audio.PlaybackController.pause()
            else com.example.core.audio.PlaybackController.play(context, "media:${a.id}", noteTitle ?: kindLabel, File(a.path))
        } else onOpen()
    }, shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 2.dp, modifier = Modifier.size(width = 148.dp, height = 196.dp)) {
        Box(Modifier.fillMaxSize()) {
            when (a.kind) {
                AttachmentKind.IMAGE -> AsyncImage(File(a.path), contentDescription = a.caption, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                AttachmentKind.VIDEO -> {
                    val frame by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, a.path) {
                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { android.media.MediaMetadataRetriever().run { setDataSource(a.path); getFrameAtTime(1_000_000).also { release() } } }.getOrNull()
                        }
                    }
                    frame?.let { androidx.compose.foundation.Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        ?: Box(Modifier.fillMaxSize().background(Color(0xFF1F2937)))
                    Box(Modifier.align(Alignment.Center).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play video", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
                else -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B1530), Color(0xFF3A2A1A)))), contentAlignment = Alignment.Center) {
                    VoiceBars(playing)
                    Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(34.dp).clip(CircleShape).background(Color(0xFFF6D365)), contentAlignment = Alignment.Center) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = Color(0xFF1B1530), modifier = Modifier.size(20.dp))
                    }
                }
            }
            // What it is, and where it's from.
            Row(Modifier.padding(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
                Text("  $kindLabel", fontSize = 10.5.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                a.durationMs?.takeIf { it > 0 }?.let { Text(" · ${Formatters.formatDurationHms(it)}", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.8f)) }
            }
            if (a.kind != AttachmentKind.AUDIO) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))).padding(10.dp)) {
                MediaCaption(noteTitle, a.createdAt)
            } else Box(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 12.dp, end = 50.dp)) { MediaCaption(noteTitle, a.createdAt) }
        }
    }
}

@Composable
private fun MediaCaption(title: String?, at: Long) {
    Column {
        Text(title ?: "From a note", fontSize = 12.sp, lineHeight = 15.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(at)), fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.75f))
    }
}

/** Gentle bars that breathe — faster while the voice is playing. */
@Composable
private fun VoiceBars(playing: Boolean) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "bars")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(if (playing) 900 else 2600, easing = androidx.compose.animation.core.LinearEasing)), label = "phase")
    Row(Modifier.padding(bottom = 30.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(9) { i ->
            val h = 10f + 26f * ((kotlin.math.sin(phase + i * 0.7f) + 1f) / 2f) * (if (playing) 1f else 0.55f)
            Box(Modifier.size(width = 4.dp, height = h.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFF6D365).copy(alpha = 0.85f)))
        }
    }
}
