package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.rounded.Videocam
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Send
import com.craftflowtechnologies.meetingmind.core.devotional.ClassicDevotionals
import com.craftflowtechnologies.meetingmind.core.devotional.ClassicReading
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.biblestudy.StudyJournal
import java.time.LocalDate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.Comment
import com.craftflowtechnologies.meetingmind.core.circles2.MediaAttachment
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.scripture.PassageResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.scripture.rememberPassage
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/** Which kind of post to write. Admin-only kinds are hidden from members. */
@Composable
fun PostTypeSheet(circle: Circle, isAdmin: Boolean, onPick: (PostType) -> Unit, onDismiss: () -> Unit) {
    CircleSheet(onDismiss) {
        Text("New post", style = MM.type.title, color = MM.colors.ink)
        // Free-form is always offered; the circle's own kinds follow.
        (listOf(PostType.Note) + circle.settings.allowedTypes.filter { !it.adminOnly || isAdmin }).distinct().forEach { t ->
            ListRow(title = t.verb, subtitle = t.label, onClick = { onPick(t) })
        }
    }
}

/** Picker sheet to browse and select a devotional reading to share. */
@Composable
fun DevotionalPickerSheet(onPick: (ClassicReading) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val classics = remember { ClassicDevotionals.get(context) }
    val today = remember { classics.forDate(LocalDate.now()) ?: classics.allReadings.firstOrNull() }
    var search by remember { mutableStateOf("") }
    val filtered = remember(search, classics) {
        if (search.isBlank()) classics.allReadings.take(25)
        else classics.allReadings.filter {
            it.heading.contains(search, ignoreCase = true) ||
            it.referenceText.contains(search, ignoreCase = true) ||
            it.date.contains(search)
        }.take(25)
    }

    CircleSheet(onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Share Devotional", style = MM.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = MM.colors.ink)
                Text(classics.title, style = MM.type.caption, color = MM.colors.inkSecondary)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close", tint = MM.colors.inkSecondary) }
        }

        // Today's reading highlighted card
        today?.let { t ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CirclesPalette.AccentSoft,
                border = BorderStroke(1.dp, CirclesPalette.Accent.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(t); onDismiss() }
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.AutoStories, contentDescription = null, tint = CirclesPalette.Accent, modifier = Modifier.size(16.dp))
                            Text("Today's Reading", style = MM.type.bodyStrong.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = CirclesPalette.Accent)
                        }
                        Text(t.date, style = MM.type.caption.copy(fontSize = 11.sp), color = MM.colors.inkSecondary)
                    }
                    Text(t.heading.ifBlank { t.referenceText }, style = MM.type.title.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), color = MM.colors.ink)
                    Text("“${t.keyText.take(160)}…”", style = MM.type.scripture.copy(fontSize = 12.5.sp), color = MM.colors.inkSecondary, maxLines = 2)
                    PrimaryButton("Share Today's Devotional", { onPick(t); onDismiss() }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Search field
        CircleField(search, { search = it }, label = "Search devotionals", placeholder = "Search by scripture, title, or date (e.g. John, 10-22)…", maxLines = 1)

        Text("All readings (${filtered.size})", style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = MM.colors.inkSecondary)
        Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            filtered.forEach { r ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MM.colors.surfaceSunk,
                    border = BorderStroke(1.dp, MM.colors.line),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(r); onDismiss() }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(r.heading.ifBlank { r.referenceText }, style = MM.type.bodyStrong.copy(fontSize = 13.5.sp), color = MM.colors.ink, maxLines = 1)
                            Text("${r.date} · ${r.referenceText}", style = MM.type.caption, color = MM.colors.inkSecondary)
                        }
                        Icon(Icons.Rounded.AutoStories, contentDescription = null, tint = CirclesPalette.Accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Picker sheet to browse and select a note from the user's Bible study journal. */
@Composable
fun StudyPickerSheet(onPick: (title: String, reference: String?, excerpt: String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val journal = remember { StudyJournal(NoteRepository(context, MeetMindDatabase.getInstance(context))) }
    val entries by journal.observe().collectAsState(initial = emptyList())

    CircleSheet(onDismiss) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Share from Your Study", style = MM.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = MM.colors.ink)
                Text("Select an entry from your study journal", style = MM.type.caption, color = MM.colors.inkSecondary)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close", tint = MM.colors.inkSecondary) }
        }

        if (entries.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MM.colors.surfaceSunk,
                border = BorderStroke(1.dp, MM.colors.line),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = MM.colors.inkSecondary, modifier = Modifier.size(32.dp))
                    Text("No study notes yet", style = MM.type.bodyStrong, color = MM.colors.ink)
                    Text(
                        "Your Bible study notes and reflections will appear here once created. You can also write your study thoughts directly in the post.",
                        style = MM.type.caption,
                        color = MM.colors.inkSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            SecondaryButton("Write study reflection instead", onDismiss, modifier = Modifier.fillMaxWidth())
        } else {
            Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.forEach { note ->
                    val excerpt = note.plainText.take(300)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MM.colors.surfaceSunk,
                        border = BorderStroke(1.dp, MM.colors.line),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                            onPick(note.title, null, excerpt)
                            onDismiss()
                        }
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(note.title.ifBlank { "Untitled Study Note" }, style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold), color = MM.colors.ink)
                            if (excerpt.isNotBlank()) {
                                Text(excerpt, style = MM.type.caption.copy(lineHeight = 16.sp), color = MM.colors.inkSecondary, maxLines = 3)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComposerSheet(
    circle: Circle,
    state: ComposerState,
    onBody: (String) -> Unit,
    onVerse: (String) -> Unit,
    onAnonymous: (Boolean) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    onRemovePhoto: (MediaAttachment) -> Unit = {},
    onTypeChange: (PostType) -> Unit = {},
    onPhoto: (PickedFile) -> Unit = {},
    onPhotoRefused: () -> Unit = {}
) {
    val context = LocalContext.current
    var videoNotice by remember { mutableStateOf<String?>(null) }
    var showDevotionalPicker by remember { mutableStateOf(false) }
    var showStudyPicker by remember { mutableStateOf(false) }
    var selectedDevotional by remember { mutableStateOf<ClassicReading?>(null) }
    var selectedStudyNote by remember { mutableStateOf<Triple<String, String?, String>?>(null) }

    // Multi-modal media pickers
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) readPicked(context, uri)?.let { onPhoto(it) } ?: onPhotoRefused()
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            val file = readPicked(context, uri)
            if (file != null) {
                if (file.name.orEmpty().endsWith(".mov", ignoreCase = true) || file.name.orEmpty().endsWith(".3gp", ignoreCase = true) || file.mime.contains("quicktime") || file.mime.contains("3gpp")) {
                    videoNotice = "MOV and 3GPP formats are not supported yet. Please select an MP4 or WebM video."
                } else {
                    videoNotice = null
                    onPhoto(file)
                }
            } else onPhotoRefused()
        }
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) readPicked(context, uri)?.let { onPhoto(it) } ?: onPhotoRefused()
    }

    val type = state.type ?: return
    val availableTypes = remember(circle) {
        (listOf(PostType.Note) + circle.settings.allowedTypes).distinct()
    }
    val parsedRef = remember(state.verse) {
        if (state.verse.isNotBlank()) ScriptureReferenceParser.parse(state.verse.trim()) else null
    }
    val passageResult = parsedRef?.let { rememberPassage(it) }

    if (showDevotionalPicker) {
        DevotionalPickerSheet(
            onPick = { reading ->
                selectedDevotional = reading
                onVerse(reading.referenceText)
                val excerpt = reading.paragraphs.joinToString("\n\n").take(600)
                val textToShare = "“${reading.keyText}”\n\n${excerpt}…"
                if (state.body.isBlank()) onBody(textToShare)
            },
            onDismiss = { showDevotionalPicker = false }
        )
    }

    if (showStudyPicker) {
        StudyPickerSheet(
            onPick = { title, ref, excerpt ->
                selectedStudyNote = Triple(title, ref, excerpt)
                ref?.let(onVerse)
                val quote = "Study: $title\n\n“$excerpt”"
                if (state.body.isBlank()) onBody(quote)
            },
            onDismiss = { showStudyPicker = false }
        )
    }

    CircleSheet(onDismiss) {
        // Redesigned modern header with space badge
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (circle.isWork) "WORK POST" else "FAITH POST",
                        style = MM.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = CirclesPalette.Accent
                    )
                }
                Text(
                    type.verb,
                    style = MM.type.title.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    color = MM.colors.ink
                )
                Text(
                    "Posting to ${circle.name}",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, "Cancel", tint = MM.colors.inkSecondary)
            }
        }

        // Type switcher chips
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            availableTypes.forEach { t ->
                MMChip(t.label, selected = t == type, onClick = { onTypeChange(t) })
            }
        }

        if (state.fromAnswered) {
            StatusLine(StatusKind.Info, "Thank God with the group! Edit this testimony however you like.")
        }

        // Devotional selector & preview card
        if (type == PostType.Devotional) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CirclesPalette.WarmBg,
                border = BorderStroke(1.dp, CirclesPalette.WarmBorder),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.AutoStories, contentDescription = null, tint = CirclesPalette.WarmInk, modifier = Modifier.size(16.dp))
                            Text(
                                selectedDevotional?.heading?.ifBlank { "Devotional Reading" } ?: "Devotional Reading",
                                style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                                color = CirclesPalette.WarmInk
                            )
                        }
                        Text(
                            selectedDevotional?.date ?: "Today",
                            style = MM.type.caption.copy(fontSize = 11.sp),
                            color = CirclesPalette.WarmInk.copy(alpha = 0.8f)
                        )
                    }
                    selectedDevotional?.let { d ->
                        Text(
                            "“${d.keyText}”",
                            style = MM.type.scripture.copy(fontSize = 13.sp, lineHeight = 19.sp),
                            color = MM.colors.ink
                        )
                        Text(d.referenceText, style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = CirclesPalette.WarmInk)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SecondaryButton(
                            if (selectedDevotional == null) "Select Devotional" else "Change Devotional",
                            { showDevotionalPicker = true }
                        )
                    }
                }
            }
        }

        // Study selector & preview card
        if (type == PostType.Study) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CirclesPalette.WarmBg,
                border = BorderStroke(1.dp, CirclesPalette.WarmBorder),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = CirclesPalette.WarmInk, modifier = Modifier.size(16.dp))
                            Text(
                                selectedStudyNote?.first ?: "Bible Study Entry",
                                style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                                color = CirclesPalette.WarmInk
                            )
                        }
                    }
                    selectedStudyNote?.let { n ->
                        Text("“${n.third}”", style = MM.type.scripture.copy(fontSize = 13.sp, lineHeight = 19.sp), color = MM.colors.ink, maxLines = 4)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        SecondaryButton(
                            if (selectedStudyNote == null) "Pick from Study Journal" else "Change Study Note",
                            { showStudyPicker = true }
                        )
                    }
                }
            }
        }

        // Redesigned modern text field container
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MM.colors.surface,
            border = BorderStroke(1.dp, MM.colors.line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                CircleField(
                    state.body,
                    { onBody(it.take(4000)) },
                    label = when (type) {
                        PostType.Devotional -> "Your thoughts or reflection on this reading"
                        PostType.Study -> "Your study takeaways or reflection"
                        PostType.Prayer -> "What would you like prayer for?"
                        PostType.Testimony -> "What did God do?"
                        PostType.Encouragement -> "Encouraging word or reflection"
                        PostType.Task -> "Action item & assignee"
                        PostType.Decision -> "Decision & context"
                        PostType.Brief -> "Meeting brief or summary"
                        PostType.Standup -> "Status update"
                        PostType.Blocker -> "What is blocking you?"
                        else -> "Write your post"
                    },
                    placeholder = when (type) {
                        PostType.Devotional -> "Add what God spoke to you through this devotional…"
                        PostType.Study -> "Write your study insights or share with the group…"
                        PostType.Prayer -> "Share your prayer request with details or specific prayer points…"
                        PostType.Testimony -> "Tell the story of how God moved or answered prayer…"
                        PostType.Encouragement -> "Share a thought, verse reflection, or word of encouragement…"
                        PostType.Task -> "e.g. Update API client for work circles (@Jane, by Friday)…"
                        PostType.Decision -> "e.g. Decided to adopt Jetpack Compose for all new flows…"
                        PostType.Brief -> "Summary of meeting key points, next steps and milestones…"
                        PostType.Standup -> "What did you complete? What are you working on next?"
                        PostType.Blocker -> "What's in the way, who can help, and what's needed to unblock?"
                        else -> if (circle.isWork) "Share an update, question, or note with the team…" else "Share an update, question, or note with the circle…"
                    },
                    minLines = 4,
                    maxLines = 10
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        "${state.body.length} / 4000",
                        style = MM.type.caption.copy(fontSize = 11.sp),
                        color = if (state.body.length > 3800) CirclesPalette.RoseInk else MM.colors.inkMuted
                    )
                }
            }
        }

        // Staged attachments preview tray
        if (state.attachments.isNotEmpty()) {
            Text("Attached media (${state.attachments.size})", style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = MM.colors.inkSecondary)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.attachments.forEach { a ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MM.colors.surfaceSunk,
                        border = BorderStroke(1.dp, MM.colors.line),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                when {
                                    a.isVideo -> Icons.Rounded.Videocam
                                    a.isPdf -> Icons.Rounded.PictureAsPdf
                                    else -> Icons.Rounded.Image
                                },
                                contentDescription = null,
                                tint = CirclesPalette.Accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                a.name ?: listOfNotNull(a.durationMs?.takeIf { it > 0 }?.let(::formatDuration), formatBytes(a.bytes)).joinToString(" · "),
                                style = MM.type.caption.copy(fontWeight = FontWeight.Medium),
                                color = MM.colors.ink,
                                maxLines = 1
                            )
                            IconButton(onClick = { onRemovePhoto(a) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Rounded.Close, "Remove attachment", tint = MM.colors.inkSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // Scripture verse section with live preview (for faith circles)
        if (!circle.isWork && type != PostType.Announcement && type != PostType.Devotional) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CircleField(
                    state.verse,
                    onVerse,
                    label = "Scripture verse (optional)",
                    placeholder = "e.g. Romans 8:28, Psalm 23:1, John 3:16"
                )
                if (parsedRef != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CirclesPalette.WarmBg,
                        border = BorderStroke(1.dp, CirclesPalette.WarmBorder),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = CirclesPalette.WarmInk,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        parsedRef.display(),
                                        style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                        color = CirclesPalette.WarmInk
                                    )
                                }
                                (passageResult as? PassageResult.Found)?.let { found ->
                                    Text(
                                        found.passage.versionAbbreviation,
                                        style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                                        color = CirclesPalette.WarmInk.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            when (passageResult) {
                                is PassageResult.Found -> {
                                    Text(
                                        "“${passageResult.passage.text.trim().trim('“', '”')}”",
                                        style = MM.type.scripture.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                                        color = MM.colors.ink,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                    if (passageResult.passage.attribution.isNotBlank()) {
                                        Text(
                                            passageResult.passage.attribution,
                                            style = MM.type.caption.copy(fontSize = 10.5.sp),
                                            color = MM.colors.inkMuted,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                                null -> Text("Looking up scripture passage…", style = MM.type.caption, color = MM.colors.inkMuted, modifier = Modifier.padding(top = 4.dp))
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }

        // Modern multi-modal media attachment tray buttons (Photo, Video, PDF)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryButton(
                "Photo",
                { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                leadingIcon = Icons.Rounded.Image,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                "Video",
                { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                leadingIcon = Icons.Rounded.Videocam,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                "PDF",
                { pickPdf.launch("application/pdf") },
                leadingIcon = Icons.Rounded.PictureAsPdf,
                modifier = Modifier.weight(1f)
            )
        }

        videoNotice?.let { StatusLine(StatusKind.Warning, it) }

        // Anonymous toggle for prayer requests
        if (type == PostType.Prayer) {
            ToggleRow("Post anonymously", "Nobody sees who asked, admins included.", state.anonymous, onAnonymous)
            if (circle.settings.prayerApproval) {
                StatusLine(StatusKind.Info, "An admin reviews prayer requests before they appear in the circle.")
            }
        }

        state.error?.let { StatusLine(StatusKind.Error, it) }

        PrimaryButton(
            if (state.sending) "Posting..."
            else if (type == PostType.Prayer && circle.settings.prayerApproval) "Send for approval"
            else if (circle.isWork) "Share with team"
            else "Share with circle",
            onSend,
            enabled = !state.sending && (state.body.isNotBlank() || state.attachments.isNotEmpty() || selectedDevotional != null || selectedStudyNote != null),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** One text box used for editing a post and for posting an update. */
@Composable
fun TextEditSheet(title: String, initial: String, actionLabel: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    CircleSheet(onDismiss) {
        Text(title, style = MM.type.title, color = MM.colors.ink)
        CircleField(text, { text = it.take(4000) }, "Your words", minLines = 3, maxLines = 8)
        PrimaryButton(actionLabel, { onSave(text); onDismiss() }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ReportSheet(onReport: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    CircleSheet(onDismiss) {
        Text("Report this", style = MM.type.title, color = MM.colors.ink)
        Text("The admins will see the report. They won't be told who sent it.", style = MM.type.secondary, color = MM.colors.inkSecondary)
        CircleField(reason, { reason = it.take(500) }, "What's wrong? (optional)", minLines = 2, maxLines = 5)
        PrimaryButton("Send report", { onReport(reason); onDismiss() }, modifier = Modifier.fillMaxWidth())
    }
}

/** A post with its updates and comments, styled with distinct pastel author bubbles and quick actions. */
@Composable
fun ThreadSheet(
    post: Post?,
    thread: ThreadUiState?,
    myUid: String?,
    isAdmin: Boolean,
    now: Long,
    onSend: (String) -> Unit,
    onDeleteComment: (Comment) -> Unit,
    onDismiss: () -> Unit,
    onMemberClick: ((String) -> Unit)? = null
) {
    var text by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    CircleSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (thread != null && thread.comments.isNotEmpty()) "Comments (${thread.comments.size})" else "Comments",
                style = MM.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                color = MM.colors.ink
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, "Close", tint = MM.colors.inkSecondary)
            }
        }

        if (post != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MM.colors.surfaceSunk,
                border = BorderStroke(1.dp, MM.colors.line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InitialsAvatar(
                            post.authorName ?: "Member",
                            size = 28.dp,
                            modifier = Modifier.clickable { onMemberClick?.invoke(post.authorUid ?: post.authorName.orEmpty()) }
                        )
                        Text(
                            post.authorName ?: "Member",
                            style = MM.type.bodyStrong.copy(fontSize = 13.sp),
                            color = MM.colors.ink,
                            modifier = Modifier.clickable { onMemberClick?.invoke(post.authorUid ?: post.authorName.orEmpty()) }
                        )
                        PostTypeBadge(post.type)
                        Spacer(Modifier.weight(1f))
                        Text(
                            relativeTime(now, post.createdAt),
                            style = MM.type.caption,
                            color = MM.colors.inkSecondary
                        )
                    }
                    SelectionContainer {
                        Text(
                            post.body,
                            style = MM.type.body,
                            color = MM.colors.ink,
                            maxLines = 4
                        )
                    }
                    if (!post.verseRef.isNullOrBlank()) {
                        Text(
                            post.verseRef,
                            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = CirclesPalette.WarmInk
                        )
                    }
                }
            }
        }

        thread?.failure?.let { StatusLine(StatusKind.Warning, it.message) }

        if (thread != null) {
            thread.updates.forEach { u ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CirclesPalette.AccentSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Author update · ${relativeTime(now, u.createdAt)}",
                            style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = CirclesPalette.AccentDeep
                        )
                        SelectionContainer {
                            Text(u.body, style = MM.type.body, color = MM.colors.ink, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            if (thread.comments.isEmpty() && !thread.loading) {
                Text(
                    "No comments yet. Be the first to share an encouraging word or prayer.",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary,
                    modifier = Modifier.padding(vertical = MM.space.m)
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(thread.comments, key = { it.id }) { cm ->
                        val isMyComment = cm.authorUid != null && cm.authorUid == myUid
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            InitialsAvatar(
                                cm.authorName,
                                size = 36.dp,
                                modifier = Modifier.clickable { onMemberClick?.invoke(cm.authorUid ?: cm.authorName) }
                            )
                            Surface(
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                                color = MM.colors.surfaceSunk,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            cm.authorName,
                                            style = MM.type.bodyStrong.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                            color = MM.colors.ink,
                                            modifier = Modifier.clickable { onMemberClick?.invoke(cm.authorUid ?: cm.authorName) }
                                        )
                                        Text(
                                            relativeTime(now, cm.createdAt),
                                            style = MM.type.caption.copy(fontSize = 11.sp),
                                            color = MM.colors.inkSecondary
                                        )
                                    }
                                    SelectionContainer {
                                        Text(
                                            cm.body,
                                            style = MM.type.body.copy(fontSize = 14.sp),
                                            color = MM.colors.ink,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextAction("Copy", { clipboard.setText(AnnotatedString(cm.body)) })
                                        if (isMyComment || isAdmin) {
                                            Spacer(Modifier.width(10.dp))
                                            TextAction("Delete", { onDeleteComment(cm) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(MM.space.s)
        ) {
            CircleField(
                text,
                { text = it.take(2000) },
                label = "Add a comment",
                placeholder = "Write an encouraging comment…",
                modifier = Modifier.weight(1f),
                maxLines = 4
            )
            Surface(
                shape = CircleShape,
                color = if (text.isNotBlank()) CirclesPalette.Accent else MM.colors.surfaceSunk,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = text.isNotBlank()) {
                        val toSend = text.trim()
                        if (toSend.isNotBlank()) {
                            onSend(toSend)
                            text = ""
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Send,
                        "Send comment",
                        tint = if (text.isNotBlank()) CirclesPalette.White else MM.colors.inkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** The invite code and a share button. The code is always written out so a pasted message works anywhere. */
@Composable
fun InviteSheet(state: InviteUiState, onCreate: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    CircleSheet(onDismiss) {
        Text("Invite people", style = MM.type.title, color = MM.colors.ink)
        val invite = state.invite
        when {
            state.busy -> StatusLine(StatusKind.Info, "Making a code...")
            invite != null && state.shareText != null -> {
                InsetPanel { SelectionContainer { Text(invite.code, style = MM.type.title, color = MM.colors.ink) } }
                Text("Works for one week and up to ${invite.maxUses ?: 50} people. Anyone with the code can join, so share it only with people you know.", style = MM.type.secondary, color = MM.colors.inkSecondary)
                PrimaryButton("Share invite", {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, state.shareText)
                    runCatching { context.startActivity(Intent.createChooser(send, "Share invite").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, modifier = Modifier.fillMaxWidth())
                SecondaryButton("Copy message", { clipboard.setText(AnnotatedString(state.shareText)) }, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                state.error?.let { StatusLine(StatusKind.Error, it) }
                Text("Make a code to share on WhatsApp, SMS or in person.", style = MM.type.secondary, color = MM.colors.inkSecondary)
                PrimaryButton(if (state.error != null) "Try again" else "Make a code", onCreate, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
