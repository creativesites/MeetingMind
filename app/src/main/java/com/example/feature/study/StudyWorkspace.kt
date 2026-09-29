package com.example.feature.study

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.scene.SceneMap
import com.example.core.common.Formatters
import com.example.core.database.MeetMindDatabase
import com.example.core.faith.PassageLinks
import com.example.core.repository.NoteRepository
import com.example.core.scripture.BibleStore
import com.example.core.scripture.ChapterContent
import com.example.core.scripture.ChapterResult
import com.example.core.scripture.ScriptureReference
import com.example.core.scripture.ScriptureService
import com.example.feature.notes.editor.NoteEditorScreen
import com.example.feature.notes.editor.NoteEditorViewModel
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash
import com.example.ui.theme.FaithGold
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LineSoft
import com.example.ui.theme.LocalMMColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A sermon's moments for the study pane: its scenes, its cited points, its transcript. */
data class SermonStudy(
    val meetingId: String,
    val title: String,
    val audioPath: String?,
    val scenes: SceneMap?,
    val points: List<Pair<String, Long>>,
    val scripture: List<Pair<ScriptureReference, Long?>>,
    val transcript: List<Pair<Long, String>>
)

class StudyWorkspaceViewModel(app: Application) : AndroidViewModel(app) {
    private val db = MeetMindDatabase.getInstance(app)
    private val notes = NoteRepository(app, db)
    private val scripture = ScriptureService(app)
    private val store = BibleStore.get(app)

    private val _chapter = MutableStateFlow<ChapterContent?>(null)
    val chapter: StateFlow<ChapterContent?> = _chapter
    private val _chapterError = MutableStateFlow<String?>(null)
    val chapterError: StateFlow<String?> = _chapterError
    private val _highlights = MutableStateFlow<Map<Int, String>>(emptyMap())
    val highlights: StateFlow<Map<Int, String>> = _highlights
    private val _links = MutableStateFlow(PassageLinks())
    val links: StateFlow<PassageLinks> = _links
    private val _crossRefs = MutableStateFlow<List<com.example.core.scripture.CrossRef>>(emptyList())
    val crossRefs: StateFlow<List<com.example.core.scripture.CrossRef>> = _crossRefs
    private val _sermon = MutableStateFlow<SermonStudy?>(null)
    val sermon: StateFlow<SermonStudy?> = _sermon
    private val _ref = MutableStateFlow<ScriptureReference?>(null)
    val ref: StateFlow<ScriptureReference?> = _ref
    var noteId: String = ""

    /** Opens a passage in the reading pane: its chapter, highlights, the notes that touch it. */
    fun openPassage(r: ScriptureReference) = viewModelScope.launch {
        _ref.value = r
        _chapter.value = null; _chapterError.value = null
        val library = ScriptureService.library(getApplication())
        when (val c = runCatching { library.chapter(scripture.defaultVersionId(), r.book, r.chapter) }.getOrNull()) {
            is ChapterResult.Found -> _chapter.value = c.content
            is ChapterResult.Unavailable -> _chapterError.value = c.message
            null -> _chapterError.value = "Couldn't open this chapter."
        }
        _highlights.value = withContext(Dispatchers.IO) { runCatching { store.highlights(r.book, r.chapter) }.getOrDefault(emptyMap()) }
        _links.value = runCatching { notes.notesOnPassage(r, excludeNoteId = noteId) }.getOrDefault(PassageLinks())
        _crossRefs.value = runCatching { library.crossRefs(r.book, r.chapter) }.getOrNull().orEmpty()
    }

    fun toggleHighlight(verse: Int) = viewModelScope.launch {
        val r = _ref.value ?: return@launch
        val on = _highlights.value.containsKey(verse)
        withContext(Dispatchers.IO) { store.setHighlight(r.book, r.chapter, verse..verse, if (on) null else "gold") }
        _highlights.value = if (on) _highlights.value - verse else _highlights.value + (verse to "gold")
    }

    /** Loads a sermon: scenes from beside its audio, points and Scripture from its note, and the transcript. */
    fun openSermon(meetingId: String) = viewModelScope.launch {
        val s = withContext(Dispatchers.IO) {
            val meeting = db.meetingDao().getMeetingById(meetingId) ?: return@withContext null
            val doc = meeting.noteId?.let { notes.getDocument(it) }
            val points = doc?.blocks.orEmpty().sortedBy { it.position }
                .filter { it.source == com.example.core.model.BlockSource.AI && it.type.isText && it.content.text.isNotBlank() }
                .mapNotNull { b -> b.payload[com.example.core.model.NoteBlock.PAYLOAD_START_MS]?.toLongOrNull()?.let { b.content.text to it } }
            val refs = doc?.scriptureRefs.orEmpty().mapNotNull { r ->
                com.example.core.scripture.BibleBooks.byUsfm(r.bookUsfm)?.let { ScriptureReference(it, r.chapter, r.verseStart, r.verseEnd) to r.startMs }
            }.distinctBy { it.first }
            val transcript = db.transcriptDao().getSegmentsForMeetingDirect(meetingId).map { it.startMs to it.text }
            SermonStudy(meetingId, meeting.title, meeting.audioFilePath, meeting.audioFilePath?.let { SceneMap.load(java.io.File(it)) }, points, refs, transcript)
        }
        _sermon.value = s
    }
}

/**
 * Study beside your notes (Faith spec §9, §19): a reading pane — a Bible passage or a sermon —
 * and the study note. Side by side on a tablet or unfolded phone; on a phone the note pulls up
 * from below. Tapping a verse puts it in the note; a long press highlights it; every passage
 * shows the notes, sermons and devotionals that touch it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StudyWorkspaceScreen(
    editor: NoteEditorViewModel,
    vm: StudyWorkspaceViewModel,
    noteId: String,
    meetingId: String?,
    initialRef: ScriptureReference?,
    onNavigateBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenRecording: (String, Long?) -> Unit
) {
    LaunchedEffect(noteId, meetingId, initialRef) {
        vm.noteId = noteId
        meetingId?.let { vm.openSermon(it) }
        initialRef?.let { vm.openPassage(it) }
    }
    var tab by remember { mutableStateOf(if (meetingId != null) StudyTab.TIMELINE else StudyTab.PASSAGE) }
    val context = LocalContext.current
    val sermon by vm.sermon.collectAsState()
    val play: (Long) -> Unit = { ms ->
        val s = sermon
        val file = s?.audioPath?.let { java.io.File(it) }
        if (s != null && file != null && file.exists()) com.example.core.audio.PlaybackController.playAt(context, s.meetingId, s.title, file, ms)
        else meetingId?.let { onOpenRecording(it, ms) }
    }
    val reading: @Composable (Modifier) -> Unit = { m ->
        Column(m.background(LocalMMColors.current.background)) {
            if (meetingId != null) StudyTabs(tab) { tab = it }
            when (tab) {
                StudyTab.PASSAGE -> PassagePane(vm, onInsert = { r -> editor.insertScriptures(listOf(r)) }, onOpenNote = onOpenNote, onOpenRecording = onOpenRecording,
                    onOpenPassage = { vm.openPassage(it) })
                StudyTab.TIMELINE -> SermonTimeline(sermon, play)
                StudyTab.SCRIPTURE -> SermonScripture(sermon, onRead = { vm.openPassage(it); tab = StudyTab.PASSAGE }, onPlay = play)
                StudyTab.TRANSCRIPT -> SermonTranscript(sermon, play)
            }
        }
    }
    val noteView: @Composable (Modifier) -> Unit = { m ->
        Box(m) { NoteEditorScreen(viewModel = editor, onNavigateBack = onNavigateBack, onOpenRecording = onOpenRecording, onOpenNote = onOpenNote, onRecordHere = {}) }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(LocalMMColors.current.background).testTag("study_workspace")) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxSize().statusBarsPadding()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
                        Text("Study", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    reading(Modifier.weight(1f))
                }
                Box(Modifier.width(0.5.dp).fillMaxSize().background(LineSoft))
                noteView(Modifier.weight(1f).fillMaxSize())
            }
        } else {
            // Phone: the reading pane above, the note below; the handle moves the split.
            var split by remember { mutableStateOf(1) } // 0 reading, 1 half, 2 note
            Column(Modifier.fillMaxSize()) {
                if (split < 2) Column(Modifier.weight(if (split == 0) 1f else 0.55f).statusBarsPadding()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
                        Text("Study", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    }
                    reading(Modifier.weight(1f))
                }
                Row(
                    Modifier.fillMaxWidth().background(LocalMMColors.current.surface).clickable { split = (split + 1) % 3 }.padding(vertical = 6.dp).testTag("study_handle"),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (split == 2) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp))
                    Text(when (split) { 0 -> "Show my notes"; 1 -> "Notes · tap to expand"; else -> "Show the reading" }, fontSize = 12.sp, color = InkSecondary)
                }
                if (split > 0) noteView(Modifier.weight(if (split == 2) 1f else 0.45f).fillMaxWidth())
            }
        }
    }
}

enum class StudyTab(val label: String) { TIMELINE("Timeline"), SCRIPTURE("Scripture"), TRANSCRIPT("Transcript"), PASSAGE("Passage") }

@Composable
private fun StudyTabs(tab: StudyTab, onTab: (StudyTab) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StudyTab.entries.forEach { t ->
            val on = t == tab
            Text(t.label, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) Ink else InkSecondary,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) AccentWash else LocalMMColors.current.background).clickable { onTab(t) }.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PassagePane(
    vm: StudyWorkspaceViewModel,
    onInsert: (ScriptureReference) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenRecording: (String, Long?) -> Unit,
    onOpenPassage: (ScriptureReference) -> Unit
) {
    val ref by vm.ref.collectAsState()
    val chapter by vm.chapter.collectAsState()
    val error by vm.chapterError.collectAsState()
    val highlights by vm.highlights.collectAsState()
    val links by vm.links.collectAsState()
    val cross by vm.crossRefs.collectAsState()
    var showLinks by remember { mutableStateOf(false) }
    val r = ref ?: run { Text("Choose a passage from the sermon's Scripture, or open one from the Bible.", color = InkMuted, modifier = Modifier.padding(24.dp)); return }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                IconButton(onClick = { if (r.chapter > 1) onOpenPassage(ScriptureReference(r.book, r.chapter - 1)) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous chapter", tint = InkSecondary) }
                Text("${r.book.name} ${r.chapter}", fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (r.chapter < r.book.chapterCount) onOpenPassage(ScriptureReference(r.book, r.chapter + 1)) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next chapter", tint = InkSecondary) }
            }
            chapter?.let { Text("${it.abbreviation} · tap a verse to add it to your notes · long-press to highlight", fontSize = 11.5.sp, color = InkMuted) }
            // Notes on this passage: first-class, right under the heading.
            if (!links.isEmpty) {
                Text(
                    "${links.total} ${if (links.total == 1) "note touches" else "notes touch"} this passage ${if (showLinks) "▴" else "▾"}",
                    fontSize = 13.sp, color = Accent, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 10.dp).clickable { showLinks = !showLinks }.testTag("passage_links")
                )
                if (showLinks) {
                    LinkGroup("My notes", links.myNotes) { onOpenNote(it.noteId) }
                    LinkGroup("Sermons", links.sermons) { l -> l.meetingId?.let { onOpenRecording(it, l.startMs) } ?: onOpenNote(l.noteId) }
                    LinkGroup("Devotionals", links.devotionals) { onOpenNote(it.noteId) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (chapter == null) item {
            if (error != null) Text(error!!, color = InkMuted, modifier = Modifier.padding(vertical = 20.dp))
            else Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Accent) }
        }
        chapter?.let { c ->
            items(c.verses, key = { it.number }) { v ->
                val inFocus = r.verseStart?.let { s -> v.number in s..(r.verseEnd ?: s) } == true
                val lit = highlights.containsKey(v.number)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)) { append("${v.number} ") }
                        append(v.text)
                    },
                    fontSize = 17.sp, lineHeight = 27.sp, fontFamily = FontFamily.Serif, color = Ink,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .background(when { lit -> FaithGold.copy(alpha = 0.18f); inFocus -> AccentWash; else -> LocalMMColors.current.background })
                        .combinedClickable(onClick = { onInsert(ScriptureReference(r.book, r.chapter, v.number, null)) }, onLongClick = { vm.toggleHighlight(v.number) })
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                )
            }
            if (cross.isNotEmpty()) item {
                Text("CROSS REFERENCES", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
                val focused = r.verseStart?.let { s -> cross.filter { it.fromVerse in s..(r.verseEnd ?: s) } }?.ifEmpty { null } ?: cross
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    focused.sortedByDescending { it.score }.take(12).forEach { x ->
                        Text("${x.fromVerse} → ${x.to.display()}", fontSize = 13.sp, color = Ink,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(LocalMMColors.current.surfaceSunk).clickable { onOpenPassage(x.to) }.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
            }
            item { chapter?.attribution?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, color = InkFaint, modifier = Modifier.padding(vertical = 18.dp)) } }
        }
    }
}

@Composable
private fun LinkGroup(title: String, items: List<com.example.core.faith.LinkedNote>, onOpen: (com.example.core.faith.LinkedNote) -> Unit) {
    if (items.isEmpty()) return
    Text(title.uppercase(), fontSize = 10.5.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
    items.forEach { l ->
        Row(Modifier.fillMaxWidth().clickable { onOpen(l) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(l.title, fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f), maxLines = 1)
            Text(l.reference + (l.startMs?.let { " · " + Formatters.formatDurationHms(it) } ?: ""), fontSize = 12.sp, color = InkSecondary)
        }
    }
}

@Composable
private fun Citation(ms: Long, onPlay: (Long) -> Unit) {
    Text("[${Formatters.formatDurationHms(ms)}]", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Accent, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable { onPlay(ms) }.padding(end = 8.dp))
}

@Composable
private fun SermonTimeline(s: SermonStudy?, onPlay: (Long) -> Unit) {
    if (s == null) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }; return }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        val scenes = s.scenes?.scenes.orEmpty()
        if (scenes.isNotEmpty()) {
            item { Text("THE SERVICE", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
            items(scenes) { sc ->
                Row(Modifier.fillMaxWidth().clickable { onPlay(sc.startMs) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Citation(sc.startMs, onPlay)
                    Column(Modifier.weight(1f)) {
                        Text(sc.activity.label, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Medium)
                        (sc.songTitle?.let { "Song: $it" } ?: sc.label)?.let { Text(it, fontSize = 12.5.sp, color = InkSecondary) }
                    }
                    Text(Formatters.formatDurationHms(sc.durationMs), fontSize = 12.sp, color = InkMuted)
                }
                HorizontalDivider(color = LineSoft, thickness = 0.5.dp)
            }
        }
        if (s.points.isNotEmpty()) {
            item { Text("WHAT WAS SAID", fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)) }
            items(s.points) { (text, ms) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                    Citation(ms, onPlay)
                    Text(text, fontSize = 14.5.sp, lineHeight = 21.sp, color = Ink, modifier = Modifier.weight(1f))
                }
            }
        }
        if (scenes.isEmpty() && s.points.isEmpty()) item { Text("No timeline yet — it appears once the sermon has been processed.", color = InkMuted, modifier = Modifier.padding(vertical = 20.dp)) }
    }
}

@Composable
private fun SermonScripture(s: SermonStudy?, onRead: (ScriptureReference) -> Unit, onPlay: (Long) -> Unit) {
    val refs = s?.scripture.orEmpty()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        if (refs.isEmpty()) item { Text("No Scripture was found in this sermon.", color = InkMuted, modifier = Modifier.padding(vertical = 20.dp)) }
        items(refs) { (r, ms) ->
            Row(Modifier.fillMaxWidth().clickable { onRead(r) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                ms?.let { Citation(it, onPlay) }
                Text(r.display(), fontSize = 16.sp, fontFamily = FontFamily.Serif, color = Ink, modifier = Modifier.weight(1f))
                Text("Read", fontSize = 12.5.sp, color = Accent)
            }
            HorizontalDivider(color = LineSoft, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun SermonTranscript(s: SermonStudy?, onPlay: (Long) -> Unit) {
    val lines = s?.transcript.orEmpty()
    val scenes = s?.scenes
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        items(lines.filterIndexed { i, (ms, _) ->
            // Worship lyrics stay folded here too: only the first line of a song part shows.
            val sc = scenes?.at(ms)
            sc?.activity != com.example.ai.scene.SemanticActivity.SONG || i == 0 || scenes.at(lines[i - 1].first) != sc
        }) { (ms, text) ->
            val sc = scenes?.at(ms)
            Row(Modifier.fillMaxWidth().clickable { onPlay(ms) }.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Citation(ms, onPlay)
                Text(if (sc?.activity == com.example.ai.scene.SemanticActivity.SONG) "Worship — lyrics folded" else text,
                    fontSize = 14.sp, lineHeight = 21.sp, color = if (sc?.activity == com.example.ai.scene.SemanticActivity.SONG) InkMuted else Ink, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Suppress("unused") private val dot = CircleShape

/** Choose a study method; each makes an ordinary Bible-study note. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StudyTemplateSheet(passage: ScriptureReference?, onPick: (com.example.core.faith.StudyTemplate) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LocalMMColors.current.surface) {
        LazyColumn(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            item {
                Text(passage?.let { "Study ${it.display()}" } ?: "Start a Bible study", fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, color = Ink)
                Text("Choose a method. It becomes a normal note you can change freely, with the passage open beside it.", fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            }
            items(com.example.core.faith.StudyTemplate.entries) { t ->
                Column(Modifier.fillMaxWidth().clickable { onPick(t) }.padding(vertical = 12.dp).testTag("study_template_${t.name}")) {
                    Text(t.label, fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Medium)
                    Text(t.description, fontSize = 13.sp, color = InkSecondary)
                }
                HorizontalDivider(color = LineSoft, thickness = 0.5.dp)
            }
        }
    }
}
