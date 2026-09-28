package com.example.feature.notes.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.database.NoteVersionSummary
import com.example.core.model.NoteBlock
import com.example.core.notes.DiffLine
import com.example.core.notes.NoteSnapshot
import com.example.core.notes.VersionCodec
import com.example.core.notes.VersionDiff
import com.example.core.notes.VersionReason
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A note's saved versions, newest first and grouped by day (PRD_M0 §4.6). Tapping one previews it
 * against the note as it is now; from there it can be restored or copied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHistorySheet(
    versions: List<NoteVersionSummary>,
    current: List<NoteBlock>,
    load: suspend (String) -> NoteSnapshot?,
    onRestore: (String) -> Unit,
    onSaveNow: () -> Unit,
    onDismiss: () -> Unit
) {
    var previewing by remember { mutableStateOf<NoteVersionSummary?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Version history", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onSaveNow) { Text("Save version") }
            }
            Text(
                "Saved before AI changes and restores, and as you edit. Older versions thin out over time.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (versions.isEmpty()) {
                Text("No versions yet. One is saved the first time you change this note.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 24.dp))
            } else {
                val day = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
                val time = SimpleDateFormat("HH:mm", Locale.getDefault())
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    versions.groupBy { day.format(Date(it.createdAt)) }.forEach { (header, list) ->
                        item(key = "h-$header") {
                            Text(header, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                        }
                        items(list, key = { it.id }) { v ->
                            Row(Modifier.fillMaxWidth().clickable { previewing = v }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(time.format(Date(v.createdAt)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 14.dp))
                                Column(Modifier.weight(1f)) {
                                    val reason = runCatching { VersionReason.valueOf(v.reason) }.getOrDefault(VersionReason.EDIT_SESSION)
                                    Text(listOfNotNull(reason.label, v.label).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                                    Text(v.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    previewing?.let { v ->
        VersionPreview(v, current, load, onRestore = { onRestore(v.id); previewing = null; onDismiss() }, onClose = { previewing = null })
    }
}

@Composable
private fun VersionPreview(
    version: NoteVersionSummary,
    current: List<NoteBlock>,
    load: suspend (String) -> NoteSnapshot?,
    onRestore: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var snapshot by remember(version.id) { mutableStateOf<NoteSnapshot?>(null) }
    LaunchedEffect(version.id) { snapshot = load(version.id) }
    val lines = remember(snapshot, current) {
        snapshot?.let { VersionDiff.lines(from = VersionCodec.lines(current), to = VersionCodec.lines(it.blocks)) }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(version.title.ifBlank { "Untitled" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(
                    "Compared with the note now: green is what restoring brings back, struck through is what it removes.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp)
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(lines.orEmpty()) { line -> DiffRow(line) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onRestore, enabled = snapshot != null) { Text("Restore this version") } },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    snapshot?.let { s ->
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText(s.title, VersionCodec.lines(s.blocks).joinToString("\n")))
                    }
                }, enabled = snapshot != null) { Text("Copy text") }
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
    )
}

@Composable
private fun DiffRow(line: DiffLine) {
    val added = Color(0x3322C55E)
    val removed = Color(0x33EF4444)
    Text(
        line.text.ifEmpty { " " },
        style = MaterialTheme.typography.bodyMedium,
        textDecoration = if (line.change == DiffLine.Change.REMOVED) TextDecoration.LineThrough else null,
        color = if (line.change == DiffLine.Change.REMOVED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).then(
            when (line.change) {
                DiffLine.Change.ADDED -> Modifier.background(added, RoundedCornerShape(4.dp))
                DiffLine.Change.REMOVED -> Modifier.background(removed, RoundedCornerShape(4.dp))
                DiffLine.Change.SAME -> Modifier
            }
        ).padding(horizontal = 4.dp)
    )
}
