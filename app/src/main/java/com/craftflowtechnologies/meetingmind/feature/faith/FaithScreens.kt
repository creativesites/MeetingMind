package com.craftflowtechnologies.meetingmind.feature.faith

import com.craftflowtechnologies.meetingmind.ui.theme.forTheme
import com.craftflowtechnologies.meetingmind.ui.theme.OnInk
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceBase
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.material.icons.filled.DeleteOutline
import com.craftflowtechnologies.meetingmind.ui.theme.Danger
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
import com.craftflowtechnologies.meetingmind.core.common.Formatters
import com.craftflowtechnologies.meetingmind.core.model.AttachmentKind
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.model.RecordingType
import com.craftflowtechnologies.meetingmind.core.scripture.BibleBooks
import com.craftflowtechnologies.meetingmind.core.scripture.PassageResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReference
import com.craftflowtechnologies.meetingmind.core.scripture.toReference
import com.craftflowtechnologies.meetingmind.feature.scripture.VerseSheet
import com.craftflowtechnologies.meetingmind.ui.theme.Accent
import com.craftflowtechnologies.meetingmind.ui.theme.AccentWash
import com.craftflowtechnologies.meetingmind.ui.theme.Ink
import com.craftflowtechnologies.meetingmind.ui.theme.InkFaint
import com.craftflowtechnologies.meetingmind.ui.theme.InkMuted
import com.craftflowtechnologies.meetingmind.ui.theme.InkSecondary
import com.craftflowtechnologies.meetingmind.ui.theme.Line
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceRaised
import com.craftflowtechnologies.meetingmind.ui.theme.SurfaceSunk
import java.io.File

/** The Faith space's warm accent, used only here and on Faith notes' markers. */
private val Gold: Color @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable get() = com.craftflowtechnologies.meetingmind.ui.theme.FaithGold
private val GoldWash: Color @androidx.compose.runtime.Composable @androidx.compose.runtime.ReadOnlyComposable get() = Color(0x14B7791F).forTheme()

fun faithIcon(type: RecordingType): ImageVector = when (type) {
    RecordingType.SERMON -> Icons.Filled.Church
    RecordingType.BIBLE_STUDY -> Icons.Filled.School
    RecordingType.DEVOTIONAL -> Icons.Filled.WbSunny
    RecordingType.PRAYER -> Icons.Filled.VolunteerActivism
    RecordingType.PRAYER_REQUEST -> Icons.Filled.Favorite
    RecordingType.TESTIMONY -> Icons.Filled.Star
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
    onReadPassage: (ScriptureReference) -> Unit = {},
    onGuideToday: (ScriptureReference) -> Unit = {},
    onOpenDevotional: () -> Unit = {},
    onShare: (com.craftflowtechnologies.meetingmind.feature.share.ShareRequest) -> Unit = {},
    onPrayWithMe: () -> Unit = {},
    onOpenPlans: () -> Unit = {},
    onOpenPrayerList: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenTestimonies: () -> Unit = {},
    onOpenFellowship: () -> Unit = {}
) {
    var assistantOpen by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    val create = com.craftflowtechnologies.meetingmind.feature.create.rememberCreateController()
    com.craftflowtechnologies.meetingmind.feature.create.CreateHost(create)
    if (assistantOpen) {
        val lib: com.craftflowtechnologies.meetingmind.feature.assistant.LibraryAssistantViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        com.craftflowtechnologies.meetingmind.feature.assistant.AssistantSheet(lib.faith, onOpenNote = { assistantOpen = false; onOpenNote(it) }, onOpenTasks = { assistantOpen = false; onOpenTasks() }, onDismiss = { assistantOpen = false })
    }
    val extras: FaithExtrasViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val bibleVm: com.craftflowtechnologies.meetingmind.feature.bible.BibleHomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { extras.refresh(); bibleVm.refresh() }
    val bible by bibleVm.state.collectAsState()
    val plans by extras.plans.collectAsState()
    val people by extras.people.collectAsState()
    val votd by viewModel.verseOfTheDay.collectAsState()
    val devotional by viewModel.todayDevotional.collectAsState()
    val devotionalProfile by viewModel.devotionalProfile.collectAsState()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val requests by viewModel.openRequests.collectAsState()
    val answered by viewModel.answeredCount.collectAsState()
    val onThisDay by viewModel.onThisDay.collectAsState()
    val journey by viewModel.journey.collectAsState()
    val themes by viewModel.themes.collectAsState()
    val notes by viewModel.faithNotes.collectAsState()
    val allScripture by viewModel.allScripture.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val media by viewModel.media.collectAsState()
    val memory by viewModel.memoryVerse.collectAsState()
    var themeSheet by remember { mutableStateOf<String?>(null) }

    val startOfToday = remember { java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val state = FaithHomeState(
        place = bible.place, start = bible.start, today = bible.today,
        devotional = devotional?.devotional?.let { t ->
            FaithDevotionalLine(
                t.title,
                t.scripture.firstOrNull()?.display()
                    ?: t.engine?.takeIf { t.origin == com.craftflowtechnologies.meetingmind.core.devotional.DevotionalOrigin.CLASSIC },
                written = true
            )
        },
        reading = devotionalProfile.readingName.takeIf { devotionalProfile.source != com.craftflowtechnologies.meetingmind.core.devotional.DevotionalSource.MINE },
        verse = votd?.let { v ->
            (v.passage as? PassageResult.Found)?.passage?.let { FaithVerseLine(v.reference.display(), it.versionAbbreviation, it.text, it.attribution) }
        },
        versePending = votd != null && votd?.passage == null,
        plan = plans.firstOrNull { !it.finished }?.let { p ->
            val day = p.nextUnread ?: p.dayFor(extras.today)
            val readings = p.plan.days[day]
            FaithPlanLine(p.plan.name, com.craftflowtechnologies.meetingmind.core.faith.ReadingPlans.describe(readings), day + 1, p.plan.length, p.percent, p.behind(extras.today), readings.first())
        },
        prayerPeople = com.craftflowtechnologies.meetingmind.core.faith.PrayerRotation.today(people, extras.today),
        prayedToday = people.filter { (it.lastPrayedAt ?: 0) >= startOfToday }.map { it.id }.toSet(),
        requests = requests, answered = answered,
        journeyLine = journey.firstOrNull()?.counts?.entries?.sortedByDescending { it.value }?.take(3)
            ?.joinToString(" · ") { "${it.value} ${it.key.displayName.lowercase()}" },
        entryCount = notes.size,
        scripturePassages = allScripture.map { it.bookUsfm to it.chapter }.distinct().size,
        collections = collections.size,
        recent = notes.take(8)
    )
    val actions = FaithHomeActions(
        onBack = onNavigateBack,
        onAssistant = { assistantOpen = true },
        onOpenBible = onOpenBible,
        onRead = { r -> if (r != null) onReadPassage(r) else onOpenBible() },
        onGuideToday = onGuideToday,
        onOpenDevotional = onOpenDevotional,
        onWriteDevotionalWithAi = {
            // Starts the AI write, then opens the page, which shows it being written (or why it couldn't be).
            com.craftflowtechnologies.meetingmind.core.devotional.DevotionalScheduler.writeNow(
                appContext, replace = true, ask = com.craftflowtechnologies.meetingmind.ai.devotional.DevotionalAsk(aiOnly = true)
            )
            onOpenDevotional()
        },
        onPray = onPrayWithMe,
        onCircles = onOpenFellowship,
        onReadVerse = { votd?.let { onReadPassage(it.reference) } },
        onWriteOnVerse = { votd?.let { v -> viewModel.startDevotional(v.reference, onOpenNote) } },
        onShareVerse = {
            val v = votd
            val found = v?.passage as? PassageResult.Found
            if (v != null && found != null) onShare(com.craftflowtechnologies.meetingmind.feature.share.ShareRequest(
                com.craftflowtechnologies.meetingmind.core.share.ShareCardContent("Verse of the day", found.passage.text, "${v.reference.display()} · ${found.passage.versionAbbreviation}", found.passage.attribution),
                theme = found.passage.text.take(200)
            ))
        },
        onTestimonies = onOpenTestimonies,
        onWrite = { type -> viewModel.create(type, onOpenNote) },
        onRecordSermon = onRecordSermon,
        onOpenJourney = onOpenJourney,
        onOpenScripture = onOpenScripture,
        onOpenPrayerList = onOpenPrayerList,
        onPrayed = { extras.prayed(it) },
        onAnswered = { viewModel.markAnswered(it, onOpenNote) },
        onOpenNote = onOpenNote,
        onDeleteNote = { noteToDelete = it },
        onOpenPlans = onOpenPlans,
        onMarkPlanRead = { plans.firstOrNull { !it.finished }?.let { p -> extras.setDone(p.plan, p.nextUnread ?: p.dayFor(extras.today), true) } }
    )

    FaithHomeContent(
        state, actions,
        createRow = {
            // A card suggested for today's verse (BIBLE_DEVOTIONAL_V2 §5).
            val found = (votd?.passage as? PassageResult.Found)?.passage
            val verse = found?.let { com.craftflowtechnologies.meetingmind.core.create.ResolvedScripture(it.reference.display(), it.text.trim(), it.versionId, it.versionAbbreviation, it.attribution) }
            val mood = remember(verse?.text) {
                com.craftflowtechnologies.meetingmind.core.create.CreateVibePolicy.suggestFromText(com.craftflowtechnologies.meetingmind.core.create.CreateSourceKind.VERSE, verse?.text.orEmpty())
            }
            com.craftflowtechnologies.meetingmind.feature.create.CreateRow(
                verse = verse, mood = mood,
                onOpen = {
                    create.open(
                        votd?.let { com.craftflowtechnologies.meetingmind.core.create.CreateSeed(com.craftflowtechnologies.meetingmind.core.create.CreateSourceKind.VERSE, reference = it.reference.display(), vibe = mood) }
                            ?: com.craftflowtechnologies.meetingmind.core.create.CreateSeed()
                    )
                },
                onSeeMore = create::openGallery
            )
        },
        hasMore = true,
        more = {
            item(key = "more_memory") {
                MemoryVerseCard(memory, onRead = { memory?.first?.reference?.let(onReadPassage) }, onMemorise = { votd?.reference?.let { viewModel.memorise(it) } }, hasVotd = votd != null)
            }
            item(key = "more_tasks") {
                com.craftflowtechnologies.meetingmind.feature.tasks.TasksPeekCard(
                    "To live out",
                    setOf(com.craftflowtechnologies.meetingmind.core.tasks.TaskKind.APPLY, com.craftflowtechnologies.meetingmind.core.tasks.TaskKind.PRAYER, com.craftflowtechnologies.meetingmind.core.tasks.TaskKind.FOLLOW_UP),
                    onOpenTasks, horizontalPadding = 0.dp
                )
            }
            if (onThisDay.isNotEmpty()) item(key = "more_otd") {
                Column {
                    com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader("On this day")
                    onThisDay.forEach { n ->
                        com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow(
                            title = n.title.ifBlank { n.workflow.displayName },
                            subtitle = n.workflow.displayName + " · " + java.text.SimpleDateFormat("yyyy", java.util.Locale.getDefault()).format(java.util.Date(n.eventDate ?: n.createdAt)),
                            onClick = { onOpenNote(n.id) }
                        )
                    }
                }
            }
            if (themes.isNotEmpty()) item(key = "more_themes") {
                Column(verticalArrangement = Arrangement.spacedBy(com.craftflowtechnologies.meetingmind.ui.theme.MM.space.xs)) {
                    com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader("Themes")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(com.craftflowtechnologies.meetingmind.ui.theme.MM.space.s)) {
                        themes.take(16).forEach { t -> com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip("${t.name} · ${t.count}", selected = false, onClick = { themeSheet = t.name }) }
                    }
                }
            }
            if (media.isNotEmpty()) item(key = "more_media") {
                Column {
                    com.craftflowtechnologies.meetingmind.core.ui.mm.SectionHeader("Photos, videos & voice", count = media.size)
                    val titles = remember(notes) { notes.associate { it.id to it.title.ifBlank { it.workflow.displayName } } }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(media.take(24), key = { it.id }) { a -> FaithMediaCard(a, titles[a.noteId]) { onOpenNote(a.noteId) } }
                    }
                }
            }
            item(key = "more_backgrounds") {
                var showBackgrounds by remember { mutableStateOf(false) }
                com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard(contentPadding = com.craftflowtechnologies.meetingmind.ui.theme.MM.space.s) {
                    com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow(
                        title = "Backgrounds", subtitle = "Pictures for your stories, devotional and share cards",
                        modifier = Modifier.padding(horizontal = com.craftflowtechnologies.meetingmind.ui.theme.MM.space.s),
                        onClick = { showBackgrounds = true }
                    )
                }
                if (showBackgrounds) com.craftflowtechnologies.meetingmind.feature.share.BackgroundsSheet(onDismiss = { showBackgrounds = false })
            }
        }
    )

    noteToDelete?.let { note ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { noteToDelete = null },
            containerColor = SurfaceBase,
            title = { Text("Delete ${note.workflow.displayName}?") },
            text = { Text("“${note.title.ifBlank { note.workflow.displayName }}” will be moved to Trash. You can restore it anytime.", color = InkSecondary) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.trashNote(note.id)
                        noteToDelete = null
                    }
                ) { Text("Move to Trash", color = Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { noteToDelete = null }) { Text("Cancel", color = InkSecondary) }
            }
        )
    }

    themeSheet?.let { theme ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { themeSheet = null },
            containerColor = SurfaceBase,
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
private fun FaithNoteRow(note: Note, showYear: Boolean = false, onDelete: (() -> Unit)? = null, onClick: () -> Unit) {
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
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = InkMuted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = InkFaint, modifier = Modifier.size(16.dp))
    }
}

/** The journey: every Faith entry, month by month. */
@Composable
fun FaithJourneyScreen(viewModel: FaithViewModel, onNavigateBack: () -> Unit, onOpenNote: (String) -> Unit) {
    val journey by viewModel.journey.collectAsState()
    Scaffold(containerColor = SurfaceBase) { padding ->
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
    Scaffold(containerColor = SurfaceBase) { padding ->
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
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { com.craftflowtechnologies.meetingmind.core.scripture.BibleStore.get(context).allHighlights(60) }.getOrDefault(emptyList()) }
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
                            Surface(onClick = { sheet = ref }, shape = RoundedCornerShape(50), color = com.craftflowtechnologies.meetingmind.feature.bible.HighlightColors[color] ?: AccentWash) {
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
    val repo = remember { com.craftflowtechnologies.meetingmind.core.repository.NoteRepository(context, com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase.getInstance(context)) }
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
private fun FaithMediaCard(a: com.craftflowtechnologies.meetingmind.core.model.Attachment, noteTitle: String?, onOpen: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playback by com.craftflowtechnologies.meetingmind.core.audio.PlaybackController.state.collectAsState()
    val playing = playback.recordingId == "media:${a.id}" && playback.phase == com.craftflowtechnologies.meetingmind.core.audio.PlaybackPhase.PLAYING
    val generated = a.caption?.contains("AI-generated") == true
    val (kindLabel, tint) = when (a.kind) {
        AttachmentKind.IMAGE -> (if (generated) "Devotional picture" else "Photo") to Color(0xFF0EA5E9).forTheme()
        AttachmentKind.VIDEO -> "Video" to Color(0xFFE11D48).forTheme()
        AttachmentKind.AUDIO -> (if (a.caption?.startsWith("Listen:") == true) "Devotional voice" else "Voice") to Gold
        else -> "File" to InkMuted
    }
    Surface(onClick = {
        if (a.kind == AttachmentKind.AUDIO) {
            if (playing) com.craftflowtechnologies.meetingmind.core.audio.PlaybackController.pause()
            else com.craftflowtechnologies.meetingmind.core.audio.PlaybackController.play(context, "media:${a.id}", noteTitle ?: kindLabel, File(a.path))
        } else onOpen()
    }, shape = RoundedCornerShape(18.dp), color = SurfaceBase, shadowElevation = 2.dp, modifier = Modifier.size(width = 148.dp, height = 196.dp)) {
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
                        ?: Box(Modifier.fillMaxSize().background(Color(0xFF1F2937).forTheme()))
                    Box(Modifier.align(Alignment.Center).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play video", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
                else -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B1530).forTheme(), Color(0xFF3A2A1A).forTheme()))), contentAlignment = Alignment.Center) {
                    VoiceBars(playing)
                    Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(34.dp).clip(CircleShape).background(Color(0xFFF6D365).forTheme()), contentAlignment = Alignment.Center) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = Ink, modifier = Modifier.size(20.dp))
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
            Box(Modifier.size(width = 4.dp, height = h.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFF6D365).forTheme().copy(alpha = 0.85f)))
        }
    }
}


/**
 * A verse being learned by heart: shown whole, then with more words hidden each tap, until only
 * first letters remain. Empty, it says how to add one.
 */
@Composable
private fun MemoryVerseCard(memory: Pair<VerseOfTheDay, Int>?, onRead: () -> Unit, onMemorise: () -> Unit, hasVotd: Boolean) {
    val c = com.craftflowtechnologies.meetingmind.ui.theme.MM.colors
    val t = com.craftflowtechnologies.meetingmind.ui.theme.MM.type
    var level by remember(memory?.first?.reference) { mutableStateOf(0) }
    com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard(modifier = Modifier.testTag("faith_memory_verse")) {
        Text("Memory verse", style = t.caption, color = c.inkSecondary)
        if (memory == null) {
            Text("Learn a verse by heart. Save one to “Memory verses” from the Bible, or start with today's verse.", style = t.secondary, color = c.inkSecondary, modifier = Modifier.padding(top = com.craftflowtechnologies.meetingmind.ui.theme.MM.space.xs))
            if (hasVotd) com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction("Memorise today's verse", onMemorise)
            return@MMCard
        }
        val (v, count) = memory
        val text = (v.passage as? com.craftflowtechnologies.meetingmind.core.scripture.PassageResult.Found)?.passage?.text
        Text(v.reference.display() + if (count > 1) "  ·  $count saved" else "", style = t.bodyStrong, color = c.ink, modifier = Modifier.padding(top = com.craftflowtechnologies.meetingmind.ui.theme.MM.space.xs).clickable(onClick = onRead))
        Text(text?.let { com.craftflowtechnologies.meetingmind.core.faith.MemoryVerses.mask(it, level) } ?: "…", style = t.scripture, color = c.ink, modifier = Modifier.padding(top = com.craftflowtechnologies.meetingmind.ui.theme.MM.space.s))
        Row {
            com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction(if (level < 3) "Hide more" else "Start again", { level = if (level < 3) level + 1 else 0 })
            if (level > 0) com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction("Show all", { level = 0 })
        }
    }
}
