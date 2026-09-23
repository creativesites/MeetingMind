package com.example.feature.scripture

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.MeetMindDatabase
import com.example.core.repository.NoteRepository
import com.example.core.scripture.BibleVersions
import com.example.core.scripture.PassageResult
import com.example.core.scripture.ScriptureReference
import com.example.core.scripture.ScriptureService
import com.example.core.scripture.webLink
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentWash
import com.example.ui.theme.Ink
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.Line
import com.example.ui.theme.Speaker3
import com.example.ui.theme.SurfaceSunk
import kotlinx.coroutines.launch

/** Loads [reference] in [versionId] (the person's default when null), re-loading when either changes. */
@Composable
fun rememberPassage(reference: ScriptureReference, versionId: Int? = null): PassageResult? {
    val context = LocalContext.current
    val service = remember { ScriptureService(context) }
    val result by produceState<PassageResult?>(null, reference, versionId) { value = service.passage(reference, versionId) }
    return result
}

/**
 * A scripture passage shown inline: reference, live text in serif, and its attribution. Offline
 * or unconfigured, the reference still shows and still plays where it was said.
 */
@Composable
fun ScriptureCard(
    reference: ScriptureReference,
    heardAtMs: Long?,
    onOpen: () -> Unit,
    onPlay: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val result = rememberPassage(reference)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceSunk)
            .clickable(onClick = onOpen)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Speaker3, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(reference.display(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
            (result as? PassageResult.Found)?.let { Text(it.passage.versionAbbreviation, fontSize = 11.sp, color = InkMuted, fontWeight = FontWeight.Medium) }
            if (onPlay != null && heardAtMs != null) {
                Spacer(Modifier.width(8.dp))
                Surface(onClick = onPlay, shape = RoundedCornerShape(50), color = AccentWash) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play where it was read", tint = Accent, modifier = Modifier.size(14.dp))
                        Text(com.example.core.common.Formatters.formatDurationHms(heardAtMs), fontSize = 11.sp, color = Accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        when (result) {
            null -> Text("Loading…", fontSize = 13.sp, color = InkMuted, modifier = Modifier.padding(top = 8.dp))
            is PassageResult.Found -> Text(
                result.passage.text, fontSize = 15.sp, lineHeight = 23.sp, color = InkSecondary, fontFamily = FontFamily.Serif,
                maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)
            )
            is PassageResult.Unavailable -> Text(result.message, fontSize = 13.sp, color = InkMuted, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/**
 * Everything about one passage: its text in any translation, where it was read, and what you
 * can do with it. Attribution is always shown with the text (YouVersion's licence).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseSheet(
    reference: ScriptureReference,
    heardAtMs: Long? = null,
    onPlay: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { ScriptureService(context) }
    var versionId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { versionId = service.defaultVersionId() }
    val result = versionId?.let { rememberPassage(reference, it) }
    var showCollections by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 22.dp).navigationBarsPadding()) {
            Text(reference.display(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink, letterSpacing = (-0.4).sp)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BibleVersions.common.forEach { v ->
                    val selected = v.id == versionId
                    Surface(
                        onClick = { versionId = v.id }, shape = RoundedCornerShape(50),
                        color = if (selected) Ink else Color.White, border = if (selected) null else BorderStroke(1.dp, Line)
                    ) {
                        Text(v.abbreviation, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else InkSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 360.dp).padding(top = 14.dp)) {
                when (result) {
                    null -> CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    is PassageResult.Found -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(result.passage.text, fontSize = 18.sp, lineHeight = 29.sp, color = Ink, fontFamily = FontFamily.Serif)
                        Text(result.passage.attribution, fontSize = 11.sp, lineHeight = 15.sp, color = InkMuted, modifier = Modifier.padding(top = 12.dp))
                    }
                    is PassageResult.Unavailable -> Text(
                        when (result.reason) {
                            PassageResult.Reason.NOT_CONFIGURED -> "Verse text isn't set up in this build yet. The reference still works, and still plays where it was read."
                            PassageResult.Reason.OFFLINE -> "Verse text is unavailable offline. It will load when you're back online."
                            else -> result.message
                        },
                        fontSize = 14.sp, color = InkSecondary
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onPlay != null && heardAtMs != null) {
                    SheetAction("Play where read", Icons.Filled.PlayArrow, primary = true, modifier = Modifier.weight(1f)) { onPlay(); onDismiss() }
                }
                SheetAction("Save", Icons.Filled.BookmarkAdd, modifier = Modifier.weight(1f)) { showCollections = true }
                SheetAction("Copy", Icons.Filled.ContentCopy, modifier = Modifier.weight(1f)) {
                    val found = result as? PassageResult.Found
                    val text = if (found != null) "“${found.passage.text}” — ${reference.display()} (${found.passage.versionAbbreviation})" else reference.display()
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(reference.display(), text))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
                SheetAction("Bible app", Icons.AutoMirrored.Filled.OpenInNew, modifier = Modifier.weight(1f)) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(reference.webLink(versionId ?: BibleVersions.DEFAULT_ID)))) }
                }
            }
        }
    }
    if (showCollections) {
        CollectionPicker(
            onPick = { collectionId ->
                scope.launch {
                    NoteRepository(context, MeetMindDatabase.getInstance(context))
                        .addToCollection(collectionId, reference.usfm, reference.chapter, reference.verseStart, reference.verseEnd, versionId)
                    Toast.makeText(context, "Saved to collection", Toast.LENGTH_SHORT).show()
                }
                showCollections = false
            },
            onDismiss = { showCollections = false }
        )
    }
}

@Composable
private fun SheetAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(14.dp), color = if (primary) Ink else SurfaceSunk,
        border = if (primary) null else BorderStroke(1.dp, Line), modifier = modifier
    ) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = if (primary) Color.White else Ink, modifier = Modifier.size(19.dp))
            Text(label, fontSize = 11.sp, color = if (primary) Color.White else InkSecondary, modifier = Modifier.padding(top = 3.dp), maxLines = 1)
        }
    }
}

/** Pick one of the person's scripture collections, or start a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { NoteRepository(context, MeetMindDatabase.getInstance(context)) }
    val collections by repo.observeScriptureCollections().collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 22.dp).navigationBarsPadding()) {
            Text("Save to collection", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            collections.forEach { c ->
                Text(
                    c.name, fontSize = 16.sp, color = Ink,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(c.id) }.padding(vertical = 12.dp, horizontal = 4.dp)
                )
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it.replace("\n", "") }, singleLine = true,
                placeholder = { Text(if (collections.isEmpty()) "Name your first collection, e.g. Promises" else "New collection") },
                trailingIcon = {
                    TextButton(enabled = name.isNotBlank(), onClick = {
                        scope.launch { onPick(repo.createScriptureCollection(name).id) }
                    }) { Text("Create") }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}
