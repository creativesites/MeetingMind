package com.example.feature.notes.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.Formatters
import com.example.core.export.ExportFormat
import com.example.core.model.Note
import com.example.core.model.NoteBlockType
import com.example.core.model.Notebook
import com.example.core.model.Tag
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line
import com.example.ui.theme.SurfaceSunk

/** What the insert sheet can add. */
internal enum class InsertAction(val label: String, val icon: ImageVector) {
    TEXT("Text", Icons.Filled.Notes),
    HEADING("Heading", Icons.Filled.Title),
    BULLETS("Bulleted list", Icons.AutoMirrored.Filled.FormatListBulleted),
    NUMBERS("Numbered list", Icons.Filled.FormatListNumbered),
    CHECKLIST("Checklist", Icons.Filled.CheckBox),
    QUOTE("Quote", Icons.Filled.FormatQuote),
    DIVIDER("Divider", Icons.Filled.HorizontalRule),
    PHOTO("Photo or video", Icons.Filled.PhotoLibrary),
    CAMERA("Take photo", Icons.Filled.PhotoCamera),
    VIDEO("Record video", Icons.Filled.Videocam),
    AUDIO("Audio file", Icons.Filled.AudioFile),
    SCRIPTURE("Bible verse", Icons.Outlined.Book),
    RECORD("Record here", Icons.Filled.Mic),
    EXCERPT("Quote the recording", Icons.Filled.RecordVoiceOver),
    NOTE_LINK("Link a note", Icons.Filled.Link)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun InsertSheet(hasRecordings: Boolean, onPick: (InsertAction) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            SheetTitle("Insert")
            SectionLabel("Write")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(InsertAction.TEXT, InsertAction.HEADING, InsertAction.BULLETS, InsertAction.NUMBERS, InsertAction.CHECKLIST, InsertAction.QUOTE, InsertAction.DIVIDER)
                    .forEach { InsertTile(it) { onPick(it) } }
            }
            SectionLabel("Add")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                buildList {
                    add(InsertAction.SCRIPTURE)
                    add(InsertAction.PHOTO); add(InsertAction.CAMERA); add(InsertAction.VIDEO); add(InsertAction.AUDIO)
                    add(InsertAction.RECORD)
                    if (hasRecordings) add(InsertAction.EXCERPT)
                    add(InsertAction.NOTE_LINK)
                }.forEach { InsertTile(it) { onPick(it) } }
            }
        }
    }
}

@Composable
private fun InsertTile(action: InsertAction, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = SurfaceSunk, border = BorderStroke(1.dp, Line), modifier = Modifier.width(100.dp)) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(action.icon, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(8.dp))
            Text(action.label, fontSize = 12.sp, color = InkSecondary, maxLines = 2, lineHeight = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
internal fun SheetTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink, letterSpacing = (-0.3).sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = InkMuted, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 16.dp, bottom = 10.dp))
}

@Composable
internal fun LinkDialog(initial: String?, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(initial.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(if (initial == null) "Add link" else "Edit link") },
        text = {
            OutlinedTextField(value = url, onValueChange = { url = it.trim() }, singleLine = true, placeholder = { Text("example.com") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onSave(url) }, enabled = url.isNotBlank()) { Text("Save") } },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onSave(null) }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TagSheet(tags: List<Tag>, allTags: List<Tag>, onAdd: (String) -> Unit, onRemove: (Tag) -> Unit, onDismiss: () -> Unit) {
    var entry by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            SheetTitle("Tags")
            OutlinedTextField(
                value = entry, onValueChange = { entry = it.replace("\n", "") }, singleLine = true,
                placeholder = { Text("Add a tag") },
                trailingIcon = { if (entry.isNotBlank()) TextButton(onClick = { onAdd(entry); entry = "" }) { Text("Add") } },
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (entry.isNotBlank()) { onAdd(entry); entry = "" } }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
            if (tags.isNotEmpty()) {
                SectionLabel("On this note")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag -> TagChip(tag.name, selected = true, trailing = Icons.Filled.Close) { onRemove(tag) } }
                }
            }
            val suggestions = allTags.filter { t -> tags.none { it.id == t.id } && (entry.isBlank() || t.name.contains(entry, ignoreCase = true)) }
            if (suggestions.isNotEmpty()) {
                SectionLabel("Your tags")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.take(30).forEach { tag -> TagChip(tag.name, selected = false) { onAdd(tag.name) } }
                }
            }
        }
    }
}

@Composable
internal fun TagChip(name: String, selected: Boolean, trailing: ImageVector? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) AccentWash else Color.White,
        border = if (selected) null else BorderStroke(1.dp, Line)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#$name", fontSize = 13.sp, color = if (selected) Accent else InkSecondary, fontWeight = FontWeight.Medium)
            trailing?.let {
                Spacer(Modifier.width(4.dp))
                Icon(it, contentDescription = "Remove", tint = Accent, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotebookSheet(notebooks: List<Notebook>, currentId: String?, onPick: (Notebook) -> Unit, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            SheetTitle("Move to notebook")
            notebooks.forEach { nb ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(nb) }.padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NotebookDot(nb.colorHex)
                    Spacer(Modifier.width(12.dp))
                    Text(nb.name, fontSize = 16.sp, color = Ink, modifier = Modifier.weight(1f))
                    Text(nb.space.displayName, fontSize = 12.sp, color = InkMuted)
                    if (nb.id == currentId) Text("  ✓", color = Accent, fontWeight = FontWeight.Bold)
                }
            }
            if (creating) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.replace("\n", "") }, singleLine = true,
                    placeholder = { Text("Notebook name") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    trailingIcon = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") } }
                )
            } else {
                TextButton(onClick = { creating = true }, modifier = Modifier.padding(top = 4.dp)) { Text("+ New notebook") }
            }
        }
    }
}

@Composable
internal fun NotebookDot(colorHex: String?, size: Int = 12) {
    val color = colorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: Accent
    Box(Modifier.size(size.dp).clip(CircleShape).then(Modifier.padding(0.dp))) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(size.dp)) {}
    }
}

/** Export and share: pick a format, choose whether private content goes along, then save or send. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportSheet(
    isPrivate: Boolean,
    busy: Boolean,
    onSave: (ExportFormat, includePrivate: Boolean) -> Unit,
    onShare: (ExportFormat, includePrivate: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var includePrivate by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            SheetTitle("Export & share")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormatOption("PDF", "Ready to print", Icons.Filled.PictureAsPdf, format == ExportFormat.PDF, Modifier.weight(1f)) { format = ExportFormat.PDF }
                FormatOption("Word", "Keep editing", Icons.Filled.Description, format == ExportFormat.DOCX, Modifier.weight(1f)) { format = ExportFormat.DOCX }
                FormatOption("Markdown", "Plain text", Icons.AutoMirrored.Filled.InsertDriveFile, format == ExportFormat.MARKDOWN, Modifier.weight(1f)) { format = ExportFormat.MARKDOWN }
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Include private sections", fontSize = 15.sp, color = Ink)
                    Text(
                        if (isPrivate) "This note is private. Only what you choose leaves the app." else "Prayers and personal reflections stay out unless you turn this on.",
                        fontSize = 12.sp, color = InkMuted
                    )
                }
                Switch(checked = includePrivate, onCheckedChange = { includePrivate = it }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
            }
            Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = { onSave(format, includePrivate) }, enabled = !busy,
                    shape = RoundedCornerShape(50), color = Color.White, border = BorderStroke(1.dp, Line), modifier = Modifier.weight(1f)
                ) {
                    Text("Save to device", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(vertical = 14.dp))
                }
                Surface(onClick = { onShare(format, includePrivate) }, enabled = !busy, shape = RoundedCornerShape(50), color = Ink, modifier = Modifier.weight(1f)) {
                    Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (busy) "Preparing…" else "Share", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatOption(title: String, subtitle: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp),
        color = if (selected) AccentWash else Color.White,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Accent else Line)
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = if (selected) Accent else InkSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(subtitle, fontSize = 11.sp, color = InkMuted)
        }
    }
}

/** Pick a line from one of the note's recordings to quote. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExcerptPickerSheet(load: suspend () -> List<ExcerptCandidate>, onPick: (ExcerptCandidate) -> Unit, onDismiss: () -> Unit) {
    var all by remember { mutableStateOf<List<ExcerptCandidate>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { all = load() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            SheetTitle("Quote the recording")
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Find words in the transcript") }, modifier = Modifier.fillMaxWidth())
            val shown = all?.filter { query.isBlank() || it.text.contains(query, ignoreCase = true) || it.speaker?.contains(query, ignoreCase = true) == true }
            when {
                shown == null -> Text("Loading…", color = InkMuted, modifier = Modifier.padding(20.dp))
                shown.isEmpty() -> Text(if (all.isNullOrEmpty()) "This note's recordings have no transcript yet." else "No lines match.", color = InkMuted, modifier = Modifier.padding(vertical = 20.dp))
                else -> LazyColumn(Modifier.heightIn(max = 520.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.segmentId }) { c ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(c) }.padding(vertical = 10.dp, horizontal = 6.dp)) {
                            Text(
                                listOfNotNull(c.speaker, Formatters.formatDurationHms(c.startMs)).joinToString(" · "),
                                fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Medium
                            )
                            Text(c.text, fontSize = 14.sp, color = Ink, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteLinkPickerSheet(search: suspend (String) -> List<Note>, onPick: (Note) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Note>>(emptyList()) }
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(150)
        results = search(query)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            SheetTitle("Link a note")
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Search notes") }, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 520.dp).padding(top = 8.dp)) {
                items(results, key = { it.id }) { n ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(n) }.padding(vertical = 12.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Book, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.title.ifBlank { "Untitled note" }, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Formatters.formatDateRelative(n.updatedAt), fontSize = 12.sp, color = InkMuted)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** The per-block menu from the drag handle. */
@Composable
internal fun BlockMenu(
    type: NoteBlockType,
    onTurnInto: (NoteBlockType) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column {
                if (type.isText) {
                    Text("Turn into", fontSize = 12.sp, color = InkMuted, modifier = Modifier.padding(bottom = 6.dp))
                    listOf(
                        NoteBlockType.PARAGRAPH to "Text", NoteBlockType.HEADING_1 to "Heading 1", NoteBlockType.HEADING_2 to "Heading 2",
                        NoteBlockType.HEADING_3 to "Heading 3", NoteBlockType.BULLET to "Bulleted list", NoteBlockType.NUMBERED to "Numbered list",
                        NoteBlockType.CHECKLIST to "Checklist", NoteBlockType.QUOTE to "Quote"
                    ).forEach { (t, label) ->
                        MenuRow(label, selected = t == type) { onTurnInto(t) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                MenuRow("Move up") { onMoveUp() }
                MenuRow("Move down") { onMoveDown() }
                MenuRow("Duplicate") { onDuplicate() }
                MenuRow("Delete", destructive = true) { onDelete() }
            }
        }
    )
}

@Composable
private fun MenuRow(label: String, selected: Boolean = false, destructive: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 15.sp,
        color = when { destructive -> Color(0xFFDC2626); selected -> Accent; else -> Ink },
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp)
    )
}

/**
 * Type one reference or many ("John 3:16-18; Rom 8:28, 31-39", one per line, whole chapters):
 * each is checked against the Bible as you type. With a single passage the person can also type
 * or paste its text themselves — useful offline or for a translation the app doesn't carry.
 */
@Composable
internal fun ScriptureEntryDialog(
    onInsert: (refs: List<com.example.core.scripture.ScriptureReference>, userText: String?, label: String?) -> Unit,
    onDismiss: () -> Unit,
    /** Opens the full Bible to browse (false) or search (true), and pick verses from there. */
    onOpenBible: (search: Boolean) -> Unit = {}
) {
    var text by remember { mutableStateOf("") }
    var ownText by remember { mutableStateOf("") }
    var ownLabel by remember { mutableStateOf("") }
    var typeOwn by remember { mutableStateOf(false) }
    val parsed = remember(text) { com.example.core.scripture.ScriptureReferenceParser.parseList(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Add Bible verses") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, minLines = 2, maxLines = 5,
                    placeholder = { Text("John 3:16-21; Ps 23\nRom 8:28, 31-39") }, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    when {
                        text.isBlank() -> "One or many — separate them with ; , or new lines. Whole chapters work too."
                        parsed.isEmpty() -> "That isn't a reference in the Bible yet."
                        parsed.size == 1 -> parsed[0].display()
                        else -> "${parsed.size} passages: " + parsed.joinToString(" · ") { it.display() }
                    },
                    fontSize = 13.sp,
                    color = if (parsed.isNotEmpty()) Accent else InkMuted,
                    fontWeight = if (parsed.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (parsed.size <= 1) {
                    Text(
                        if (typeOwn) "Use the app's Bible text instead" else "Type or paste the verse text yourself",
                        fontSize = 13.sp, color = Accent, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).clickable { typeOwn = !typeOwn }.padding(vertical = 4.dp)
                    )
                    if (typeOwn) {
                        OutlinedTextField(
                            value = ownText, onValueChange = { ownText = it }, minLines = 3, maxLines = 10,
                            label = { Text("Verse text") }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                        OutlinedTextField(
                            value = ownLabel, onValueChange = { ownLabel = it.replace("\n", "") }, singleLine = true,
                            label = { Text("Translation (optional), e.g. NIV") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                }
                Row(Modifier.padding(top = 10.dp)) {
                    TextButton(onClick = { onOpenBible(false) }) { Text("Browse the Bible") }
                    TextButton(onClick = { onOpenBible(true) }) { Text("Search words") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed.isNotEmpty(), onClick = {
                val own = ownText.trim().takeIf { typeOwn && parsed.size == 1 && it.isNotEmpty() }
                onInsert(parsed, own, ownLabel.trim().takeIf { own != null && it.isNotEmpty() })
            }) { Text(if (parsed.size > 1) "Insert ${parsed.size}" else "Insert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun SermonDetailsDialog(speaker: String, church: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var s by remember { mutableStateOf(speaker) }
    var c by remember { mutableStateOf(church) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("Sermon details") },
        text = {
            Column {
                OutlinedTextField(value = s, onValueChange = { s = it.replace("\n", "") }, singleLine = true, label = { Text("Speaker") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = c, onValueChange = { c = it.replace("\n", "") }, singleLine = true, label = { Text("Church or event") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(s, c) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
