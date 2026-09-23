package com.example.feature.bible

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.database.MeetMindDatabase
import com.example.core.repository.NoteRepository
import com.example.core.scripture.BibleBook
import com.example.core.scripture.BibleBooks
import com.example.core.scripture.BibleInfo
import com.example.core.scripture.ChapterContent
import com.example.core.scripture.ChapterVerse
import com.example.core.scripture.ScriptureReference
import com.example.core.scripture.SearchHit
import com.example.feature.scripture.CollectionPicker
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line
import com.example.ui.theme.SurfaceSunk
import kotlinx.coroutines.launch

private val Gold = Color(0xFFB7791F)
private val SelectedWash = Color(0x33F2C94C)

private enum class Page { READ, BOOKS, SEARCH }

/**
 * The Bible: read any chapter in any available translation, search it, and take verses into notes.
 *
 * [onInsert] turns on "pick" mode (opened from a note): the main action inserts the selected
 * verses into that note. Without it, the main action starts a new devotional from them
 * ([onStartNote]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleScreen(
    viewModel: BibleViewModel,
    initialReference: ScriptureReference? = null,
    onNavigateBack: () -> Unit,
    onInsert: ((ScriptureReference, Int) -> Unit)? = null,
    onStartNote: ((ScriptureReference) -> Unit)? = null,
    startInSearch: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(initialReference) { viewModel.start(initialReference) }

    val current by viewModel.current.collectAsState()
    val book by viewModel.book.collectAsState()
    val chapter by viewModel.chapter.collectAsState()
    val content by viewModel.content.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val download by viewModel.download.collectAsState()
    val offline by viewModel.offline.collectAsState()

    var page by remember { mutableStateOf(if (startInSearch) Page.SEARCH else Page.READ) }
    var versionSheet by remember { mutableStateOf(false) }
    var collectionFor by remember { mutableStateOf<ScriptureReference?>(null) }

    BackHandler(enabled = page != Page.READ) { page = Page.READ }

    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        // Top bar: back · "John 3 ▾" · translation · search
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (page != Page.READ) page = Page.READ else onNavigateBack() }) {
                Icon(if (onInsert != null && page == Page.READ) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { page = if (page == Page.BOOKS) Page.READ else Page.BOOKS }
                    .padding(horizontal = 6.dp, vertical = 6.dp).testTag("bible_location"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (page) { Page.SEARCH -> "Search"; else -> "${bookLabel(book)} $chapter" },
                    fontSize = 21.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif, color = Ink, maxLines = 1
                )
                if (page != Page.SEARCH) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Choose book", tint = InkSecondary, modifier = Modifier.size(20.dp))
            }
            Surface(
                onClick = { versionSheet = true }, shape = RoundedCornerShape(50), color = Color.White, border = BorderStroke(1.dp, Line),
                modifier = Modifier.testTag("bible_version")
            ) {
                Text(current?.abbreviation ?: "…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            IconButton(onClick = { page = if (page == Page.SEARCH) Page.READ else Page.SEARCH }, modifier = Modifier.testTag("bible_search")) {
                Icon(Icons.Filled.Search, contentDescription = "Search the Bible", tint = if (page == Page.SEARCH) Accent else Ink)
            }
        }
        HorizontalDivider(color = Line)

        Box(Modifier.weight(1f)) {
            when (page) {
                Page.READ -> Reader(viewModel, content, selection)
                Page.BOOKS -> BookPicker(current, book, onPick = { b, c -> viewModel.open(b, c); page = Page.READ })
                Page.SEARCH -> SearchPage(viewModel, download, onOpen = { ref ->
                    viewModel.open(ref.book, ref.chapter, ref.verseStart, ref.verseEnd); page = Page.READ
                })
            }
        }

        // Selection actions
        val ref = if (page == Page.READ) viewModel.selectedReference() else null
        if (ref != null && selection != null) {
            val ready = content as? ChapterState.Ready
            SelectionBar(
                reference = ref,
                insertLabel = if (onInsert != null) "Insert into note" else "New note",
                onPrimary = {
                    val id = current?.id ?: return@SelectionBar
                    if (onInsert != null) onInsert(ref, id) else onStartNote?.invoke(ref)
                    viewModel.clearSelection()
                },
                onSave = { collectionFor = ref },
                onCopy = {
                    val text = ready?.result?.content?.let { quote(it, ref) } ?: ref.display()
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(ref.display(), text))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                },
                onShare = {
                    val text = ready?.result?.content?.let { quote(it, ref) } ?: ref.display()
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share ${ref.display()}"))
                    }
                },
                onClear = { viewModel.clearSelection() }
            )
        }
    }

    if (versionSheet) {
        VersionSheet(
            viewModel = viewModel,
            current = current,
            offlineIds = offline.associateBy { it.id },
            download = download,
            onDismiss = { versionSheet = false }
        )
    }
    collectionFor?.let { r ->
        CollectionPicker(
            onPick = { collectionId ->
                scope.launch {
                    NoteRepository(context, MeetMindDatabase.getInstance(context))
                        .addToCollection(collectionId, r.usfm, r.chapter, r.verseStart, r.verseEnd, current?.id)
                    Toast.makeText(context, "Saved to collection", Toast.LENGTH_SHORT).show()
                }
                collectionFor = null
                viewModel.clearSelection()
            },
            onDismiss = { collectionFor = null }
        )
    }
}

/** The reader as a full-screen dialog, for opening from a note or a verse sheet. */
@Composable
fun BibleDialog(
    initialReference: ScriptureReference? = null,
    onDismiss: () -> Unit,
    onInsert: ((ScriptureReference, Int) -> Unit)? = null,
    startInSearch: Boolean = false
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val vm: BibleViewModel = viewModel(key = "bible-dialog")
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            BibleScreen(
                viewModel = vm, initialReference = initialReference, onNavigateBack = onDismiss,
                onInsert = onInsert?.let { insert -> { r, v -> insert(r, v); onDismiss() } },
                startInSearch = startInSearch
            )
        }
    }
}

private fun bookLabel(book: BibleBook) = if (book.usfm == "PSA") "Psalm" else book.name

private fun quote(content: ChapterContent, ref: ScriptureReference): String =
    "“${content.text(ref.verseStart, ref.verseEnd)}”\n— ${ref.display()} (${content.abbreviation})"

@Composable
private fun Reader(viewModel: BibleViewModel, content: ChapterState, selection: IntRange?) {
    val focus by viewModel.focus.collectAsState()
    when (content) {
        ChapterState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        }
        is ChapterState.Missing -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(content.message, fontSize = 15.sp, lineHeight = 22.sp, color = InkSecondary, textAlign = TextAlign.Center)
            TextButton(onClick = { viewModel.reload() }) { Text("Try again") }
        }
        is ChapterState.Ready -> {
            val c = content.result.content
            val paragraphs = remember(c) { paragraphsOf(c.verses) }
            val listState = rememberLazyListState()
            LaunchedEffect(c, focus) {
                val target = focus ?: return@LaunchedEffect
                val index = paragraphs.indexOfFirst { p -> p.any { it.number >= target } }
                if (index >= 0) listState.scrollToItem(index + 1)
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 24.dp), modifier = Modifier.fillMaxSize().testTag("bible_reader")) {
                item {
                    Text(
                        "${bookLabel(c.book)} ${c.chapter}", fontSize = 28.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
                        color = Ink, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)
                    )
                }
                items(paragraphs) { verses ->
                    Text(
                        text = paragraphText(verses, selection) { viewModel.tapVerse(it) },
                        fontSize = 19.sp, lineHeight = 31.sp, fontFamily = FontFamily.Serif, color = Ink,
                        modifier = Modifier.padding(start = if (verses.first().poetry) 14.dp else 0.dp, bottom = 12.dp)
                    )
                }
                item {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(
                            c.attribution + if (c.fromDevice) " · on this phone" else "",
                            fontSize = 11.sp, lineHeight = 15.sp, color = InkMuted
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            ChapterStep("Previous", Icons.Filled.ChevronLeft, enabled = viewModel.hasPrevious(), leading = true) { viewModel.previous() }
                            ChapterStep("Next", Icons.Filled.ChevronRight, enabled = viewModel.hasNext(), leading = false) { viewModel.next() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterStep(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, leading: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50), color = SurfaceSunk, border = BorderStroke(1.dp, Line)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading) Icon(icon, contentDescription = null, tint = if (enabled) Ink else InkMuted, modifier = Modifier.size(18.dp))
            Text(label, fontSize = 13.sp, color = if (enabled) Ink else InkMuted)
            if (!leading) Icon(icon, contentDescription = null, tint = if (enabled) Ink else InkMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/** Groups verses into the paragraphs (and poetry stanzas) the translation sets them in. */
internal fun paragraphsOf(verses: List<ChapterVerse>): List<List<ChapterVerse>> {
    val out = mutableListOf<MutableList<ChapterVerse>>()
    for (v in verses) {
        val last = out.lastOrNull()
        if (last == null || v.paragraph || v.poetry != last.last().poetry || v.poetry) out += mutableListOf(v) else last += v
    }
    return out
}

private fun paragraphText(verses: List<ChapterVerse>, selection: IntRange?, onTap: (Int) -> Unit): AnnotatedString = buildAnnotatedString {
    verses.forEachIndexed { i, v ->
        val selected = selection != null && v.number in selection
        withLink(
            LinkAnnotation.Clickable(
                tag = "v${v.number}",
                styles = TextLinkStyles(style = SpanStyle(color = Ink, background = if (selected) SelectedWash else Color.Transparent)),
                linkInteractionListener = { onTap(v.number) }
            )
        ) {
            withStyle(SpanStyle(fontSize = 11.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, color = Gold, baselineShift = BaselineShift(0.35f))) {
                append(v.number.toString())
            }
            append(" ")
            append(v.text)
        }
        if (i < verses.lastIndex) append(if (v.poetry) "\n" else " ")
    }
}

@Composable
private fun SelectionBar(
    reference: ScriptureReference,
    insertLabel: String,
    onPrimary: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit
) {
    Surface(color = Color.White, shadowElevation = 10.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(reference.display(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Clear selection", tint = InkSecondary) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(onClick = onPrimary, shape = RoundedCornerShape(12.dp), color = Ink, modifier = Modifier.weight(1.6f).testTag("bible_insert")) {
                    Row(Modifier.padding(vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(insertLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                SmallAction(Icons.Filled.BookmarkAdd, "Save", onSave, Modifier.weight(1f))
                SmallAction(Icons.Filled.ContentCopy, "Copy", onCopy, Modifier.weight(1f))
                SmallAction(Icons.Filled.Share, "Share", onShare, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = SurfaceSunk, border = BorderStroke(1.dp, Line), modifier = modifier) {
        Column(Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
            Text(label, fontSize = 11.sp, color = InkSecondary)
        }
    }
}

@Composable
private fun BookPicker(current: BibleInfo?, selected: BibleBook, onPick: (BibleBook, Int) -> Unit) {
    var open by remember { mutableStateOf<BibleBook?>(selected) }
    val books = BibleBooks.all.filter { current?.has(it) ?: true }
    LazyColumn(Modifier.fillMaxSize().testTag("bible_books"), contentPadding = PaddingValues(bottom = 24.dp)) {
        books.forEachIndexed { index, b ->
            if (index == 0 || b.isNewTestament != books[index - 1].isNewTestament) {
                item(key = "h$index") {
                    Text(
                        if (b.isNewTestament) "NEW TESTAMENT" else "OLD TESTAMENT", fontSize = 11.sp, letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold, color = InkMuted, modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 4.dp)
                    )
                }
            }
            item(key = b.usfm) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { open = if (open == b) null else b }.padding(horizontal = 22.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(b.name, fontSize = 17.sp, color = if (b == selected) Accent else Ink, fontWeight = if (b == selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        Text("${b.chapterCount}", fontSize = 13.sp, color = InkMuted)
                    }
                    if (open == b) {
                        if (b.chapterCount == 1) {
                            LaunchedEffect(b) { onPick(b, 1) }
                        } else {
                            ChapterGrid(b) { onPick(b, it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterGrid(book: BibleBook, onPick: (Int) -> Unit) {
    val rows = (book.chapterCount + 5) / 6
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxWidth().height((rows * 52).dp).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items((1..book.chapterCount).toList()) { n ->
            Surface(onClick = { onPick(n) }, shape = RoundedCornerShape(10.dp), color = SurfaceSunk, modifier = Modifier.height(46.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("$n", fontSize = 15.sp, color = Ink) }
            }
        }
    }
}

@Composable
private fun SearchPage(viewModel: BibleViewModel, download: DownloadState?, onOpen: (ScriptureReference) -> Unit) {
    val state by viewModel.search.collectAsState()
    val canSearch by viewModel.canSearch.collectAsState()
    val current by viewModel.current.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            OutlinedTextField(
                value = state.query, onValueChange = viewModel::onQuery, singleLine = true,
                placeholder = { Text("A word, a phrase, or “John 3:16”") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = InkSecondary) },
                trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { viewModel.onQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { state.reference?.let(onOpen) }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Line),
                modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus).testTag("bible_search_field")
            )
        }
        state.reference?.let { ref ->
            item {
                Surface(onClick = { onOpen(ref) }, shape = RoundedCornerShape(14.dp), color = SurfaceSunk, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Go to ", color = InkSecondary, fontSize = 15.sp)
                        Text(ref.display(), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = InkSecondary)
                    }
                }
            }
        }
        if (!canSearch && state.query.trim().length >= 2 && state.reference == null || (!canSearch && state.query.isBlank())) {
            item { SearchNeedsDownload(viewModel, current, download) }
        }
        if (state.searching) item { LinearProgressIndicator(color = Accent, modifier = Modifier.fillMaxWidth().padding(16.dp)) }
        if (canSearch && state.searched && state.hits.isEmpty() && state.reference == null) {
            item { Text("Nothing in ${current?.abbreviation ?: "this translation"} matches “${state.query.trim()}”.", color = InkSecondary, modifier = Modifier.padding(20.dp)) }
        }
        if (state.hits.isNotEmpty()) {
            item {
                Text(
                    if (state.hits.size >= 300) "300+ verses" else "${state.hits.size} verse${if (state.hits.size == 1) "" else "s"}",
                    fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp)
                )
            }
            items(state.hits, key = { it.reference.passageId() }) { hit -> SearchRow(hit, state.query) { onOpen(hit.reference) } }
        }
    }
}

@Composable
private fun SearchNeedsDownload(viewModel: BibleViewModel, current: BibleInfo?, download: DownloadState?) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceSunk, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            when {
                current == null -> Text("Search needs a translation. Connect to the internet to choose one.", color = InkSecondary)
                !current.offlineAllowed -> Text(
                    "${current.abbreviation} can be read online, but its licence doesn't allow keeping it on the phone, so it can't be searched. Switch to an open translation such as BSB or WEB to search.",
                    color = InkSecondary, fontSize = 14.sp, lineHeight = 20.sp
                )
                download != null -> {
                    Text("Downloading ${current.abbreviation}…", fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(
                        if (download.waiting) "Waiting for a connection. It continues in the background." else "${download.done} of ${download.total} chapters. You can keep reading — search turns on when it's done.",
                        fontSize = 13.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp)
                    )
                    LinearProgressIndicator(
                        progress = { if (download.total > 0) download.done / download.total.toFloat() else 0f },
                        color = Accent, trackColor = Line, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                    TextButton(onClick = { viewModel.pauseDownload() }) { Text("Pause") }
                }
                else -> {
                    Text("Search the whole Bible", fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(
                        "Download ${current.abbreviation} to your phone (about 5 MB) to search every verse — and to read it without a connection.",
                        fontSize = 13.sp, lineHeight = 19.sp, color = InkSecondary, modifier = Modifier.padding(top = 4.dp)
                    )
                    Surface(onClick = { viewModel.downloadCurrent() }, shape = RoundedCornerShape(12.dp), color = Ink, modifier = Modifier.padding(top = 12.dp).testTag("bible_download")) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download ${current.abbreviation}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(hit: SearchHit, query: String, onClick: () -> Unit) {
    val words = remember(query) { Regex("[\\p{L}\\p{N}]+").findAll(query.lowercase()).map { it.value }.filter { it.length > 1 }.toList() }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp)) {
        Text(hit.reference.display(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Gold)
        Text(highlight(hit.text.replace('\n', ' '), words), fontSize = 16.sp, lineHeight = 23.sp, fontFamily = FontFamily.Serif, color = Ink, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Bolds each word of the text that begins with one of the searched words. */
internal fun highlight(text: String, words: List<String>): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    Regex("[\\p{L}\\p{N}]+").findAll(text).forEach { m ->
        append(text.substring(cursor, m.range.first))
        val hit = words.any { m.value.lowercase().startsWith(it) }
        if (hit) withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = SelectedWash)) { append(m.value) } else append(m.value)
        cursor = m.range.last + 1
    }
    append(text.substring(cursor))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionSheet(
    viewModel: BibleViewModel,
    current: BibleInfo?,
    offlineIds: Map<Int, com.example.core.scripture.OfflineBible>,
    download: DownloadState?,
    onDismiss: () -> Unit
) {
    val bibles by viewModel.bibles.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        LazyColumn(Modifier.padding(bottom = 12.dp).navigationBarsPadding()) {
            item {
                Text("Translation", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(horizontal = 22.dp))
                Text(
                    "These are the translations licensed to MeetingMind. Open ones can be kept on your phone for offline reading and search.",
                    fontSize = 13.sp, lineHeight = 18.sp, color = InkSecondary, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
                )
            }
            if (bibles.isEmpty()) item { Text("Connect to the internet to see translations.", color = InkSecondary, modifier = Modifier.padding(22.dp)) }
            items(bibles, key = { it.id }) { b ->
                val stored = offlineIds[b.id]
                val downloading = download?.bibleId == b.id
                Row(
                    Modifier.fillMaxWidth().clickable { viewModel.chooseBible(b); onDismiss() }.padding(start = 22.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b.abbreviation, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (b.id == current?.id) Accent else Ink)
                            if (b.id == current?.id) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = Accent, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                        }
                        Text(b.title, fontSize = 13.sp, color = InkSecondary)
                        Text(
                            when {
                                stored?.complete == true -> "On your phone · searchable"
                                downloading -> "Downloading… ${download?.done ?: 0}/${download?.total ?: 0}"
                                stored != null -> "${stored.chapters} chapters saved as you read"
                                b.offlineAllowed -> "Online · can be downloaded"
                                else -> "Online only"
                            },
                            fontSize = 11.sp, color = InkMuted
                        )
                    }
                    when {
                        stored?.complete == true || (stored != null && !downloading && !b.offlineAllowed) ->
                            IconButton(onClick = { viewModel.removeDownload(b.id) }) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove from phone", tint = InkSecondary) }
                        downloading -> CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.padding(12.dp).size(20.dp))
                        b.offlineAllowed -> IconButton(onClick = { viewModel.download(b) }) { Icon(Icons.Filled.CloudDownload, contentDescription = "Download ${b.abbreviation}", tint = Ink) }
                    }
                }
            }
        }
    }
}
