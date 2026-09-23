package com.example.feature.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.Formatters
import com.example.core.model.Note
import com.example.core.model.Notebook
import com.example.core.model.NotebookSpace
import com.example.core.model.RecordingType
import com.example.core.ui.AppBottomNavigationBar
import com.example.core.ui.BottomNavDestination
import com.example.feature.notes.editor.NotebookDot
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line
import com.example.ui.theme.SurfaceSunk

/** Colours a notebook can take; picked in the notebook dialog. */
val NotebookColors = listOf("#6366F1", "#A855F7", "#EC4899", "#F43F5E", "#F59E0B", "#10B981", "#06B6D4", "#0F172A")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    onOpenNote: (String) -> Unit,
    onOpenNotebook: (String) -> Unit,
    onOpenArchive: () -> Unit,
    onNavigateBack: (() -> Unit)?,
    onNavigateBottomNav: (BottomNavDestination) -> Unit,
    /** Opens the Faith space; shown on the library root. */
    onOpenFaith: (() -> Unit)? = null
) {
    val notes by viewModel.visibleNotes.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()
    val counts by viewModel.notebookCounts.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val space by viewModel.space.collectAsState()
    val tagFilter by viewModel.tagFilter.collectAsState()
    val query by viewModel.query.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val archivedCount by viewModel.archivedCount.collectAsState()
    val currentNotebook by viewModel.currentNotebook.collectAsState()

    val isRoot = viewModel.scope == NotesScope.All
    val isArchive = viewModel.scope == NotesScope.Archived
    var searching by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var notebookDialog by remember { mutableStateOf<NotebookDialogState?>(null) }
    var notebookMenu by remember { mutableStateOf<Notebook?>(null) }
    var noteMenu by remember { mutableStateOf<Note?>(null) }
    var confirmDeleteNotebook by remember { mutableStateOf<Notebook?>(null) }
    val notebookById = remember(notebooks) { notebooks.associateBy { it.id } }

    val title = when (viewModel.scope) {
        NotesScope.All -> "Notes"
        NotesScope.Archived -> "Archived"
        is NotesScope.InNotebook -> currentNotebook?.name ?: "Notebook"
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            if (isRoot) AppBottomNavigationBar(current = BottomNavDestination.NOTES, onNavigate = onNavigateBottomNav)
        },
        floatingActionButton = {
            if (!isRoot && !isArchive) {
                Surface(onClick = { viewModel.createNote(onOpenNote) }, shape = RoundedCornerShape(50), color = Ink, shadowElevation = 6.dp) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EditNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("New note", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp)) {
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = if (onNavigateBack != null) 6.dp else 22.dp, end = 16.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
                    }
                    currentNotebook?.let { NotebookDot(it.colorHex, 14); Spacer(Modifier.width(10.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (notes.size == 1) "1 note" else "${notes.size} notes",
                            fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    CircleAction(if (searching) Icons.Filled.Close else Icons.Filled.Search, if (searching) "Close search" else "Search notes") {
                        searching = !searching
                        if (!searching) viewModel.query.value = ""
                    }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        CircleAction(Icons.AutoMirrored.Filled.Sort, "Sort") { showSort = true }
                        DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }, containerColor = Color.White) {
                            NoteSort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label, fontWeight = if (s == sort) FontWeight.SemiBold else FontWeight.Normal, color = if (s == sort) Accent else Ink) },
                                    onClick = { viewModel.sort.value = s; showSort = false }
                                )
                            }
                        }
                    }
                    currentNotebook?.let { nb ->
                        Spacer(Modifier.width(8.dp))
                        CircleAction(Icons.Filled.MoreVert, "Notebook options") { notebookMenu = nb }
                    }
                }
            }
            if (searching) {
                item(key = "search") {
                    Surface(shape = RoundedCornerShape(50), color = SurfaceSunk, border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 14.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) Text("Search titles and text", color = InkFaint, fontSize = 15.sp)
                                BasicTextField(
                                    value = query, onValueChange = { viewModel.query.value = it }, singleLine = true,
                                    textStyle = TextStyle(fontSize = 15.sp, color = Ink), cursorBrush = SolidColor(Accent), modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
            if (isRoot) {
                item(key = "spaces") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 22.dp), modifier = Modifier.padding(top = 18.dp)) {
                        item { Pill("All", space == null) { viewModel.space.value = null } }
                        items(NotebookSpace.entries.toList()) { s -> Pill(s.displayName, space == s) { viewModel.space.value = if (space == s) null else s } }
                    }
                }
                if (onOpenFaith != null && (space == null || space == NotebookSpace.FAITH)) {
                    item(key = "faith") {
                        Surface(
                            onClick = onOpenFaith, shape = RoundedCornerShape(18.dp), color = Ink,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 16.dp).testTag("notes_open_faith")
                        ) {
                            Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = Color(0xFFE9C46A), modifier = Modifier.size(22.dp))
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text("Faith", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                                    Text("Bible, verse of the day, prayer and your journey", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.7f))
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                item(key = "notebooks") {
                    Column(Modifier.padding(top = 22.dp)) {
                        SectionHeader("Notebooks")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 22.dp)) {
                            items(notebooks.filter { space == null || it.space == space }, key = { it.id }) { nb ->
                                NotebookCard(nb, counts[nb.id] ?: 0, onClick = { onOpenNotebook(nb.id) }, onLongClick = { notebookMenu = nb })
                            }
                            item(key = "new-notebook") {
                                Surface(
                                    onClick = { notebookDialog = NotebookDialogState(null, "", space ?: NotebookSpace.PERSONAL, NotebookColors.first()) },
                                    shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Line),
                                    modifier = Modifier.width(120.dp).height(96.dp)
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.Center) {
                                        Icon(Icons.Filled.Add, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.height(10.dp))
                                        Text("New notebook", fontSize = 13.sp, color = InkSecondary, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (tags.isNotEmpty() && !isArchive) {
                item(key = "tags") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 22.dp), modifier = Modifier.padding(top = 18.dp)) {
                        items(tags, key = { it.id }) { tag ->
                            Pill("#${tag.name}", tagFilter?.id == tag.id, accent = true) { viewModel.tagFilter.value = if (tagFilter?.id == tag.id) null else tag }
                        }
                    }
                }
            }

            if (loaded && notes.isEmpty()) {
                item(key = "empty") {
                    EmptyNotes(
                        filtered = query.isNotBlank() || tagFilter != null || space != null,
                        archive = isArchive,
                        onCreate = { viewModel.createNote(onOpenNote) }
                    )
                }
            } else {
                val pinned = notes.filter { it.pinned }
                val rest = notes.filter { !it.pinned }
                if (pinned.isNotEmpty()) {
                    item(key = "pinned-h") { SectionHeader("Pinned", top = 26.dp) }
                    items(pinned, key = { "p-" + it.id }) { n ->
                        NoteRow(n, notebookById[n.notebookId], showNotebook = isRoot, onClick = { onOpenNote(n.id) }, onLongClick = { noteMenu = n })
                    }
                }
                val groups = if (sort == NoteSort.TITLE) mapOf("All notes" to rest) else rest.groupBy { Formatters.formatDateHeader(if (sort == NoteSort.CREATED) it.eventDate ?: it.createdAt else it.updatedAt) }
                groups.forEach { (header, list) ->
                    item(key = "h-$header") { SectionHeader(header, top = 26.dp) }
                    items(list, key = { it.id }) { n ->
                        NoteRow(n, notebookById[n.notebookId], showNotebook = isRoot, onClick = { onOpenNote(n.id) }, onLongClick = { noteMenu = n })
                    }
                }
            }
            if (isRoot && archivedCount > 0) {
                item(key = "archive-link") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 28.dp).combinedClickable(onClick = onOpenArchive).padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Archive, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Archived notes · $archivedCount", fontSize = 14.sp, color = InkSecondary)
                    }
                }
            }
        }
    }

    notebookDialog?.let { state ->
        NotebookDialog(
            state = state,
            onSave = { s ->
                if (s.existing == null) viewModel.createNotebook(s.name.trim(), s.space, s.color)
                else viewModel.updateNotebook(s.existing.copy(name = s.name.trim(), space = s.space, colorHex = s.color))
                notebookDialog = null
            },
            onDismiss = { notebookDialog = null }
        )
    }
    notebookMenu?.let { nb ->
        AlertDialog(
            onDismissRequest = { notebookMenu = null },
            containerColor = Color.White,
            title = { Text(nb.name) },
            text = {
                Column {
                    MenuText("Edit name, space and colour") { notebookMenu = null; notebookDialog = NotebookDialogState(nb, nb.name, nb.space, nb.colorHex ?: NotebookColors.first()) }
                    MenuText("Archive notebook") { notebookMenu = null; viewModel.archiveNotebook(nb.id); onNavigateBack?.takeIf { !isRoot }?.invoke() }
                    if (nb.id != com.example.core.database.MeetMindDatabase.DEFAULT_NOTEBOOK_ID) {
                        MenuText("Delete notebook", destructive = true) { notebookMenu = null; confirmDeleteNotebook = nb }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { notebookMenu = null }) { Text("Close") } }
        )
    }
    confirmDeleteNotebook?.let { nb ->
        AlertDialog(
            onDismissRequest = { confirmDeleteNotebook = null },
            containerColor = Color.White,
            title = { Text("Delete “${nb.name}”?") },
            text = { Text("The notebook is removed. Its notes are kept and stay in All notes.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteNotebook(nb.id); confirmDeleteNotebook = null; if (!isRoot) onNavigateBack?.invoke() }) { Text("Delete", color = Color(0xFFDC2626)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteNotebook = null }) { Text("Cancel") } }
        )
    }
    noteMenu?.let { n ->
        AlertDialog(
            onDismissRequest = { noteMenu = null },
            containerColor = Color.White,
            title = { Text(n.title.ifBlank { "Untitled note" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    MenuText(if (n.pinned) "Unpin" else "Pin to top") { viewModel.togglePin(n); noteMenu = null }
                    if (isArchive) MenuText("Restore") { viewModel.unarchive(n); noteMenu = null }
                    else MenuText("Archive") { viewModel.archive(n); noteMenu = null }
                    MenuText("Delete", destructive = true) { viewModel.delete(n); noteMenu = null }
                }
            },
            confirmButton = { TextButton(onClick = { noteMenu = null }) { Text("Close") } }
        )
    }
}

private data class NotebookDialogState(val existing: Notebook?, val name: String, val space: NotebookSpace, val color: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookDialog(state: NotebookDialogState, onSave: (NotebookDialogState) -> Unit, onDismiss: () -> Unit) {
    var s by remember { mutableStateOf(state) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(if (state.existing == null) "New notebook" else "Edit notebook") },
        text = {
            Column {
                OutlinedTextField(value = s.name, onValueChange = { s = s.copy(name = it.replace("\n", "")) }, singleLine = true, placeholder = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Text("Space", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NotebookSpace.entries.forEach { sp -> Pill(sp.displayName, s.space == sp) { s = s.copy(space = sp) } }
                }
                Text("Colour", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NotebookColors.forEach { hex ->
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .combinedClickable { s = s.copy(color = hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (s.color == hex) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(s) }, enabled = s.name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuText(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    Text(
        label, fontSize = 15.sp, color = if (destructive) Color(0xFFDC2626) else Ink,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp)
    )
}

@Composable
private fun CircleAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.size(42.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, tint = Ink, modifier = Modifier.size(19.dp)) }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, accent: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = when { selected && accent -> AccentWash; selected -> Ink; else -> Color.White },
        border = if (selected) null else BorderStroke(1.dp, Line)
    ) {
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = when { selected && accent -> Accent; selected -> Color.White; else -> InkSecondary },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SectionHeader(text: String, top: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = top, bottom = 10.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookCard(notebook: Notebook, count: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    val color = runCatching { Color(android.graphics.Color.parseColor(notebook.colorHex ?: "#6366F1")) }.getOrDefault(Accent)
    Surface(shape = RoundedCornerShape(18.dp), color = color.copy(alpha = 0.08f), modifier = Modifier.width(140.dp).height(96.dp)) {
        Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(14.dp)) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(color))
            Spacer(Modifier.weight(1f))
            Text(notebook.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (count == 1) "1 note" else "$count notes", fontSize = 12.sp, color = InkSecondary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(note: Note, notebook: Notebook?, showNotebook: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (note.pinned) { Icon(Icons.Filled.PushPin, contentDescription = "Pinned", tint = Accent, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) }
            Text(
                note.title.ifBlank { "Untitled note" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = if (note.title.isBlank()) InkMuted else Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            if (note.isPrivate) Icon(Icons.Filled.Lock, contentDescription = "Private", tint = InkMuted, modifier = Modifier.size(14.dp))
        }
        // A private note's text isn't previewed in the list; someone glancing at the phone sees only its title.
        val preview = if (note.isPrivate) "Private" else note.plainText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(" · ")
        if (preview.isNotEmpty()) {
            Text(preview, fontSize = 14.sp, lineHeight = 20.sp, color = InkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showNotebook && notebook != null) {
                NotebookDot(notebook.colorHex, 8)
                Text(" ${notebook.name}", fontSize = 12.sp, color = InkMuted)
                Text("  ·  ", fontSize = 12.sp, color = InkFaint)
            }
            if (note.workflow != RecordingType.GENERAL) {
                Text(note.workflow.displayName, fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Medium)
                Text("  ·  ", fontSize = 12.sp, color = InkFaint)
            }
            Text(Formatters.formatDateRelative(note.updatedAt), fontSize = 12.sp, color = InkMuted)
        }
    }
}

@Composable
private fun EmptyNotes(filtered: Boolean, archive: Boolean, onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = AccentWash, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.EditNote, contentDescription = null, tint = Accent, modifier = Modifier.size(30.dp)) }
        }
        Text(
            when { archive -> "Nothing archived"; filtered -> "No notes match"; else -> "Your notes live here" },
            fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            when {
                archive -> "Archived notes wait here, out of the way, until you restore them."
                filtered -> "Try another word, or clear the filters."
                else -> "Write something, add photos, or record — every recording gets a note of its own."
            },
            fontSize = 14.sp, color = InkSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
        )
        if (!filtered && !archive) {
            Surface(onClick = onCreate, shape = RoundedCornerShape(50), color = Ink, modifier = Modifier.padding(top = 20.dp)) {
                Text("Write a note", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
            }
        }
    }
}
