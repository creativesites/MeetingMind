package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.devotional.ArchiveItem
import com.craftflowtechnologies.meetingmind.core.devotional.ClassicDevotionals
import com.craftflowtechnologies.meetingmind.core.devotional.DevotionalRepository
import com.craftflowtechnologies.meetingmind.core.model.Note
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** What a picked devotional or study note fills into the post: the words and, when there is one, a verse reference. */
data class PickedShare(val body: String, val verse: String = "")

private const val MAX_BODY = 4000

@Composable
private fun PickRow(title: String, subtitle: String?, preview: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp), color = MM.colors.surfaceSunk,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 2)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MM.type.caption, color = MM.colors.accent)
            if (preview.isNotBlank()) Text(preview.trim(), style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 3)
        }
    }
}

/** Choose a devotional to share: today's reading first, then everything saved. The bundled reading is always there. */
@Composable
fun DevotionalPickerSheet(onPick: (PickedShare) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val today = remember { runCatching { ClassicDevotionals.get(context).forDate(LocalDate.now()) }.getOrNull() }
    val archive by produceState<List<ArchiveItem>?>(null) {
        value = runCatching { DevotionalRepository(context.applicationContext, MeetMindDatabase.getInstance(context.applicationContext)).archive(100) }.getOrDefault(emptyList())
    }
    CircleSheet(onDismiss) {
        Text("Share a devotional", style = MM.type.title, color = MM.colors.ink)
        Text("Pick one and add your own thoughts before you post.", style = MM.type.secondary, color = MM.colors.inkSecondary)
        LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (today != null) item {
                val title = today.heading.ifBlank { "Today's reading" }
                PickRow(title, today.referenceText.ifBlank { null }, today.paragraphs.firstOrNull().orEmpty()) {
                    val body = buildString {
                        if (today.keyText.isNotBlank()) append("“${today.keyText.trim()}”\n\n")
                        append(today.paragraphs.joinToString("\n\n"))
                    }.take(MAX_BODY)
                    onPick(PickedShare(body, today.referenceText))
                }
            }
            items(archive.orEmpty(), key = { it.noteId }) { a ->
                PickRow(a.title.ifBlank { "Devotional" }, listOfNotNull(a.passage, a.day.ifBlank { null }).joinToString(" · ").ifBlank { null }, a.text) {
                    val body = buildString {
                        append(a.text.trim())
                        if (a.hasReflection) append("\n\nMy reflection: ${a.reflection!!.trim()}")
                    }.take(MAX_BODY)
                    onPick(PickedShare(body, a.passage.orEmpty()))
                }
            }
        }
        if (archive != null && archive.orEmpty().isEmpty() && today == null) {
            StatusLine(StatusKind.Info, "No devotionals yet. Open today's devotional first, then come back to share it.")
        }
    }
}

/** Choose one of your own Bible-study, sermon or reflection notes to share. Private notes are left out. */
@Composable
fun StudyNotesPickerSheet(onPick: (PickedShare) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val notes by produceState<List<Note>?>(null) {
        value = runCatching {
            NoteRepository(context.applicationContext, MeetMindDatabase.getInstance(context.applicationContext)).observeNotes().first()
                .filter { !it.isPrivate && it.archivedAt == null && it.deletedAt == null && it.plainText.isNotBlank() }
                .sortedByDescending { it.updatedAt }
                .take(100)
        }.getOrDefault(emptyList())
    }
    CircleSheet(onDismiss) {
        Text("Share from your study", style = MM.type.title, color = MM.colors.ink)
        Text("Choose a note. Private notes never appear here.", style = MM.type.secondary, color = MM.colors.inkSecondary)
        when {
            notes == null -> Text("Loading your notes…", style = MM.type.caption, color = MM.colors.inkMuted)
            notes.orEmpty().isEmpty() -> StatusLine(StatusKind.Info, "You have no shareable notes yet. Write a study or sermon note, then come back.")
            else -> LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notes.orEmpty(), key = { it.id }) { n ->
                    PickRow(n.title.ifBlank { "Untitled note" }, n.workflow.name.lowercase().replaceFirstChar { it.uppercase() }, n.plainText) {
                        onPick(PickedShare("${n.title.trim()}\n\n${n.plainText.trim()}".trim().take(MAX_BODY)))
                    }
                }
            }
        }
    }
}
