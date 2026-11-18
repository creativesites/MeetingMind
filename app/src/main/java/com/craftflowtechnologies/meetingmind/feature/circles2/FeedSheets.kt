package com.craftflowtechnologies.meetingmind.feature.circles2

import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.rounded.Videocam
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.craftflowtechnologies.meetingmind.ui.theme.FixedWhite as FixedWhiteInk
import com.craftflowtechnologies.meetingmind.core.devotional.ClassicDevotionals
import com.craftflowtechnologies.meetingmind.core.devotional.ClassicReading
import com.craftflowtechnologies.meetingmind.core.database.MeetMindDatabase
import com.craftflowtechnologies.meetingmind.core.repository.NoteRepository
import com.craftflowtechnologies.meetingmind.core.biblestudy.StudyJournal
import java.time.LocalDate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.craftflowtechnologies.meetingmind.core.circles2.ChecklistItem
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.Comment
import com.craftflowtechnologies.meetingmind.core.circles2.MediaAttachment
import com.craftflowtechnologies.meetingmind.core.circles2.Member
import com.craftflowtechnologies.meetingmind.core.circles2.Post
import com.craftflowtechnologies.meetingmind.core.circles2.PostMeta
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.circles2.WorkPriority
import com.craftflowtechnologies.meetingmind.core.scripture.PassageResult
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.ui.mm.InsetPanel
import com.craftflowtechnologies.meetingmind.core.ui.mm.ListRow
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SecondaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.SegmentedControl
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.core.ui.mm.TextAction
import com.craftflowtechnologies.meetingmind.feature.scripture.rememberPassage
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

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
                Text("Share Devotional", style = MM.type.title.copy(fontWeight = FontWeight.Bold), color = MM.colors.ink)
                Text(classics.title, style = MM.type.caption, color = MM.colors.inkSecondary)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close", tint = MM.colors.inkSecondary) }
        }

        // Today's reading highlighted card
        today?.let { t ->
            Surface(
                shape = MM.radius.card,
                color = CirclesPalette.AccentSoft,
                border = BorderStroke(1.dp, CirclesPalette.Accent.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().clip(MM.radius.card).clickable { onPick(t); onDismiss() }
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.AutoStories, contentDescription = null, tint = CirclesPalette.Accent, modifier = Modifier.size(16.dp))
                            Text("Today's Reading", style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold), color = CirclesPalette.Accent)
                        }
                        Text(t.date, style = MM.type.caption, color = MM.colors.inkSecondary)
                    }
                    Text(t.heading.ifBlank { t.referenceText }, style = MM.type.title.copy(fontWeight = FontWeight.SemiBold), color = MM.colors.ink)
                    Text("“${t.keyText.take(160)}…”", style = MM.type.scripture, color = MM.colors.inkSecondary, maxLines = 2)
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
                    shape = MM.radius.card,
                    color = MM.colors.surfaceSunk,
                    border = BorderStroke(1.dp, MM.colors.line),
                    modifier = Modifier.fillMaxWidth().clip(MM.radius.card).clickable { onPick(r); onDismiss() }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(r.heading.ifBlank { r.referenceText }, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 1)
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
                Text("Share from Your Study", style = MM.type.title.copy(fontWeight = FontWeight.Bold), color = MM.colors.ink)
                Text("Select an entry from your study journal", style = MM.type.caption, color = MM.colors.inkSecondary)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close", tint = MM.colors.inkSecondary) }
        }

        if (entries.isEmpty()) {
            Surface(
                shape = MM.radius.card,
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
                        shape = MM.radius.card,
                        color = MM.colors.surfaceSunk,
                        border = BorderStroke(1.dp, MM.colors.line),
                        modifier = Modifier.fillMaxWidth().clip(MM.radius.card).clickable {
                            onPick(note.title, null, excerpt)
                            onDismiss()
                        }
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(note.title.ifBlank { "Untitled Study Note" }, style = MM.type.bodyStrong.copy(fontWeight = FontWeight.Bold), color = MM.colors.ink)
                            if (excerpt.isNotBlank()) {
                                Text(excerpt, style = MM.type.caption, color = MM.colors.inkSecondary, maxLines = 3)
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
    onPhotoRefused: () -> Unit = {},
    onDiscardDraft: () -> Unit = {},
    uploading: Boolean = state.uploading,
    members: List<Member> = emptyList(),
    meta: PostMeta? = null,
    onMetaChange: (PostMeta?) -> Unit = {},
    isAdmin: Boolean = false,
    /** False draws the composer in place instead of in a sheet window: screenshot tests use it. */
    inSheet: Boolean = true
) {
    BackHandler(enabled = state.open) { onDismiss() }
    // Type suggestion reads the body after a 400 ms pause, and a dismissed chip stays hidden for this composer session.
    var suggestBody by remember { mutableStateOf(state.body) }
    LaunchedEffect(state.body) {
        delay(400)
        suggestBody = state.body
    }
    var suggestionDismissed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videoNotice by remember { mutableStateOf<String?>(null) }
    var showDevotionalPicker by remember { mutableStateOf(false) }
    var showStudyPicker by remember { mutableStateOf(false) }
    var selectedDevotional by remember { mutableStateOf<ClassicReading?>(null) }
    var selectedStudyNote by remember { mutableStateOf<Triple<String, String?, String>?>(null) }
    val isUploading = uploading || state.uploading
    val attachmentsFull = state.attachments.size >= 4

    var localMeta by remember(state.type, meta, state.meta) {
        mutableStateOf(meta ?: state.meta ?: PostMeta())
    }
    fun updateMeta(transform: (PostMeta) -> PostMeta) {
        val updated = transform(localMeta)
        localMeta = updated
        onMetaChange(updated)
    }

    // Multi-modal media pickers
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val f = readPickedAsync(context, uri)
                if (f != null) onPhoto(f) else onPhotoRefused()
            }
        }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val file = readPickedAsync(context, uri)
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
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val f = readPickedAsync(context, uri)
                if (f != null) onPhoto(f) else onPhotoRefused()
            }
        }
    }

    val type = state.type ?: return
    val availableTypes = remember(circle) {
        (listOf(PostType.Note) + circle.settings.allowedTypes).distinct()
    }
    val suggestedType = if (suggestionDismissed) null else PostTypeSuggester.suggest(
        body = suggestBody,
        current = type,
        allowed = availableTypes,
        isWork = circle.isWork,
        isAdmin = isAdmin
    )
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

    val canSend = !state.sending && !isUploading &&
        (state.body.isNotBlank() || state.attachments.isNotEmpty() || selectedDevotional != null || selectedStudyNote != null)
    val sendLabel = when {
        state.sending -> "Posting…"
        isUploading -> "Uploading…"
        type == PostType.Prayer && circle.settings.prayerApproval -> "Send"
        else -> "Post"
    }
    var tagsChecklistExpanded by remember { mutableStateOf(localMeta.tags.isNotEmpty() || localMeta.checklist.isNotEmpty()) }
    var tagInput by remember { mutableStateOf("") }
    var checklistInput by remember { mutableStateOf("") }
    val quickPickTags = remember(circle.isWork, circle.isLearning) {
        when {
            circle.isWork -> listOf("sprint", "shipped", "blocked", "decision")
            circle.isLearning -> listOf("exam", "summary", "formula", "review")
            else -> listOf("gratitude", "prayer", "event", "resource")
        }
    }

    ComposerFrame(
        onDismiss = onDismiss,
        inSheet = inSheet,
        header = {
            // ---- header: close, what you're writing and where, and Post ----
            Row(
                Modifier.fillMaxWidth().padding(start = MM.space.xs, end = MM.space.l),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, if (state.hasText) "Save draft and close" else "Close", tint = MM.colors.ink)
                }
                Column(Modifier.weight(1f).padding(start = MM.space.xs)) {
                    Text(type.verb, style = MM.type.heading, color = MM.colors.ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        "To ${circle.name}" + if (state.hasText) " · draft saved" else "",
                        style = MM.type.caption, color = MM.colors.inkMuted, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (state.hasText) TextAction("Discard", onClick = onDiscardDraft)
                ComposerPostButton(sendLabel, canSend, onSend)
            }
            // ---- what kind of post: chips, the current one filled ----
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = MM.space.l, vertical = MM.space.s),
                horizontalArrangement = Arrangement.spacedBy(MM.space.s)
            ) {
                availableTypes.forEach { t -> MMChip(t.label, selected = t == type, onClick = { onTypeChange(t) }) }
            }
        },
        toolbar = {
            if (isUploading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = MM.colors.ink, trackColor = MM.colors.track)
            }
            HorizontalDividerLine()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = MM.space.s, vertical = MM.space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ComposerTool(Icons.Rounded.Image, "Add a photo", enabled = !attachmentsFull && !isUploading) {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                ComposerTool(Icons.Rounded.Videocam, "Add a video", enabled = !attachmentsFull && !isUploading) {
                    pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }
                ComposerTool(Icons.Rounded.PictureAsPdf, "Add a PDF", enabled = !attachmentsFull && !isUploading) {
                    pickPdf.launch("application/pdf")
                }
                ComposerTool(Icons.Rounded.Tag, if (tagsChecklistExpanded) "Hide tags and list" else "Tags and a shared list", active = tagsChecklistExpanded) {
                    tagsChecklistExpanded = !tagsChecklistExpanded
                }
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        attachmentsFull -> "4 of 4 attachments"
                        state.body.length > 3600 -> "${state.body.length} / 4000"
                        else -> ""
                    },
                    style = MM.type.caption,
                    color = if (state.body.length > 3800) MM.colors.danger else MM.colors.inkMuted,
                    modifier = Modifier.padding(end = MM.space.s)
                )
            }
        }
    ) {
        if (circle.template == "client_engagement") {
            StatusLine(StatusKind.Info, "External client space: visible to client members.")
        }
        if (state.draftRestored) {
            Text("Draft restored. It saves as you type.", style = MM.type.caption, color = MM.colors.inkMuted)
        }
        if (state.fromAnswered) {
            StatusLine(StatusKind.Info, "Thank God with the group! Edit this testimony however you like.")
        }

        // "The type follows the words": a dismissable hint above the field. Switch keeps the words (openComposer keeps the body).
        AnimatedVisibility(visible = suggestedType != null, enter = fadeIn(MM.motion.quick()) + expandVertically(MM.motion.standard()), exit = fadeOut(MM.motion.quick()) + shrinkVertically(MM.motion.standard())) {
            val st = suggestedType
            if (st != null) {
                Row(
                    Modifier.fillMaxWidth().clip(MM.radius.pill).background(MM.colors.surfaceSunk).padding(start = MM.space.l, end = MM.space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Looks like ${PostTypeSuggester.describe(st)}", style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.weight(1f))
                    TextAction("Switch", onClick = { onTypeChange(st) })
                    IconButton(onClick = { suggestionDismissed = true }) {
                        Icon(Icons.Rounded.Close, "Dismiss suggestion", tint = MM.colors.inkMuted, modifier = Modifier.size(MMSize.iconSmall))
                    }
                }
            }
        }

        // ---- the words: a borderless page, the way Notes writes ----
        ComposerTextArea(
            value = state.body,
            onValueChange = { onBody(it.take(4000)) },
            placeholder = when (type) {
                PostType.Devotional -> "What did God speak to you through this reading?"
                PostType.Study -> "Your study takeaways or reflection…"
                PostType.Prayer -> "What would you like prayer for?"
                PostType.Testimony -> "Tell the story of how God moved…"
                PostType.Encouragement -> "A word of encouragement for the group…"
                PostType.Task -> "What needs doing, and by whom?"
                PostType.Decision -> "What was decided, and why?"
                PostType.Brief -> "Key points, next steps and milestones…"
                PostType.Standup -> "Done, next, and anything in the way…"
                PostType.Blocker -> "What's in the way, and what would unblock it?"
                else -> if (circle.isWork) "Share an update with the team…" else "Share something with the ${circle.groupWord.lowercase()}…"
            }
        )

        if (type == PostType.Standup && state.body.isBlank()) {
            TextAction("Use the Done, Next, Blockers outline", { onBody("Done:\n- \n\nNext:\n- \n\nBlockers:\n- None") })
        }

        // ---- staged attachments: thumbnails you can remove ----
        if (state.attachments.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                state.attachments.forEach { a ->
                    Box(Modifier.size(88.dp).clip(MM.radius.small).background(MM.colors.surfaceSunk)) {
                        if (!a.isVideo && !a.isPdf) {
                            MediaImage(circleId = circle.id, path = a.path, modifier = Modifier.fillMaxSize(), contentDescription = a.name ?: "Attached photo", allowFullscreen = false)
                        } else {
                            Column(Modifier.align(Alignment.Center).padding(MM.space.s), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(if (a.isVideo) Icons.Rounded.Videocam else Icons.Rounded.PictureAsPdf, null, tint = MM.colors.inkSecondary, modifier = Modifier.size(MMSize.icon))
                                Text(
                                    a.durationMs?.takeIf { it > 0 }?.let(::formatDuration) ?: formatBytes(a.bytes),
                                    style = MM.type.caption, color = MM.colors.inkMuted, maxLines = 1
                                )
                            }
                        }
                        Surface(
                            onClick = { onRemovePhoto(a) },
                            shape = MM.radius.pill,
                            color = MM.colors.scrim,
                            modifier = Modifier.align(Alignment.TopEnd).padding(MM.space.xs).size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Close, "Remove attachment", tint = FixedWhiteInk, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        videoNotice?.let { StatusLine(StatusKind.Warning, it) }

        // ---- work details: who, when, how urgent (only what the server stores) ----
        if (circle.isWork && type in setOf(PostType.Task, PostType.Blocker, PostType.Decision)) {
            InsetPanel(contentPadding = MM.space.m) {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    ComposerFieldLabel(if (type == PostType.Decision) "Decision owner" else "Who")
                    if (members.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                            members.forEach { m ->
                                if (type == PostType.Decision) {
                                    val isSel = localMeta.decisionOwner == m.displayName
                                    MMChip(m.displayName, selected = isSel, onClick = { updateMeta { it.copy(decisionOwner = if (isSel) null else m.displayName) } })
                                } else {
                                    val isSel = localMeta.assigneeUid == m.uid
                                    MMChip(m.displayName, selected = isSel, onClick = {
                                        updateMeta { curr -> if (isSel) curr.copy(assigneeUid = null, assigneeName = null) else curr.copy(assigneeUid = m.uid, assigneeName = m.displayName) }
                                    })
                                }
                            }
                        }
                    } else if (type == PostType.Decision) {
                        CircleField(localMeta.decisionOwner.orEmpty(), { owner -> updateMeta { it.copy(decisionOwner = owner.ifBlank { null }) } }, label = "Decision owner", placeholder = "A person or a team")
                    } else {
                        CircleField(localMeta.assigneeName.orEmpty(), { name -> updateMeta { it.copy(assigneeName = name.ifBlank { null }) } }, label = "Who", placeholder = "A name")
                    }
                    if (type in setOf(PostType.Task, PostType.Blocker)) {
                        ComposerFieldLabel("When")
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                            listOf("Today", "Tomorrow", "Friday").forEach { pill ->
                                val isSel = localMeta.dueText == pill
                                MMChip(pill, selected = isSel, onClick = { updateMeta { it.copy(dueText = if (isSel) null else pill) } })
                            }
                        }
                        CircleField(localMeta.dueText.orEmpty(), { text -> updateMeta { it.copy(dueText = text.ifBlank { null }) } }, label = "Or a date or milestone", placeholder = "By Friday, end of Q3")
                    }
                    ComposerFieldLabel("Priority")
                    SegmentedControl(
                        options = listOf(WorkPriority.Normal, WorkPriority.High, WorkPriority.Urgent).map { it.label },
                        selectedIndex = listOf(WorkPriority.Normal, WorkPriority.High, WorkPriority.Urgent).indexOf(localMeta.priority ?: WorkPriority.Normal),
                        onSelect = { i -> updateMeta { it.copy(priority = listOf(WorkPriority.Normal, WorkPriority.High, WorkPriority.Urgent)[i]) } }
                    )
                    if (type == PostType.Blocker) {
                        CircleField(localMeta.blockedOn.orEmpty(), { blocked -> updateMeta { it.copy(blockedOn = blocked.ifBlank { null }) } }, label = "Blocked on", placeholder = "A person or a dependency")
                    }
                }
            }
        }

        // ---- devotional and study: pick the source, see it on warm paper ----
        if (type == PostType.Devotional || type == PostType.Study) {
            val isDevotional = type == PostType.Devotional
            Surface(shape = MM.radius.card, color = MM.colors.goldWash, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                        Icon(if (isDevotional) Icons.Rounded.AutoStories else Icons.Rounded.Bookmark, null, tint = MM.colors.goldInk, modifier = Modifier.size(MMSize.iconSmall))
                        Text(
                            if (isDevotional) selectedDevotional?.heading?.ifBlank { "Devotional" } ?: "Devotional" else selectedStudyNote?.first ?: "From your study journal",
                            style = MM.type.bodyStrong, color = MM.colors.goldInk, modifier = Modifier.weight(1f)
                        )
                        if (isDevotional) Text(selectedDevotional?.date ?: "Today", style = MM.type.caption, color = MM.colors.goldInk)
                    }
                    if (isDevotional) selectedDevotional?.let { d ->
                        Text("“${d.keyText}”", style = MM.type.scripture, color = MM.colors.ink)
                        Text(d.referenceText, style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = MM.colors.goldInk)
                    } else selectedStudyNote?.let { n ->
                        Text("“${n.third}”", style = MM.type.scripture, color = MM.colors.ink, maxLines = 4)
                    }
                    TextAction(
                        when {
                            isDevotional && selectedDevotional == null -> "Choose a devotional"
                            isDevotional -> "Change devotional"
                            selectedStudyNote == null -> "Pick from your study journal"
                            else -> "Change study note"
                        },
                        { if (isDevotional) showDevotionalPicker = true else showStudyPicker = true }
                    )
                }
            }
        }

        // ---- scripture (faith circles) with a live preview ----
        if (!circle.isWork && type != PostType.Announcement && type != PostType.Devotional) {
            CircleField(state.verse, onVerse, label = "Scripture (optional)", placeholder = "Romans 8:28")
            if (parsedRef != null) {
                Surface(shape = MM.radius.card, color = MM.colors.goldWash, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MM.colors.goldInk, modifier = Modifier.size(MMSize.iconSmall))
                            Text(parsedRef.display(), style = MM.type.bodyStrong, color = MM.colors.goldInk, modifier = Modifier.weight(1f))
                            (passageResult as? PassageResult.Found)?.let { Text(it.passage.versionAbbreviation, style = MM.type.caption, color = MM.colors.goldInk) }
                        }
                        when (passageResult) {
                            is PassageResult.Found -> {
                                Text("“${passageResult.passage.text.trim().trim('“', '”')}”", style = MM.type.scripture, color = MM.colors.ink)
                                if (passageResult.passage.attribution.isNotBlank()) {
                                    Text(passageResult.passage.attribution, style = MM.type.caption, color = MM.colors.inkMuted)
                                }
                            }
                            null -> Text("Looking up the passage…", style = MM.type.caption, color = MM.colors.inkMuted)
                            else -> Unit
                        }
                    }
                }
            }
        }

        // ---- tags and a shared list, behind the # tool ----
        AnimatedVisibility(visible = tagsChecklistExpanded, enter = fadeIn(MM.motion.quick()) + expandVertically(MM.motion.standard()), exit = fadeOut(MM.motion.quick()) + shrinkVertically(MM.motion.standard())) {
            InsetPanel(contentPadding = MM.space.m) {
                Column(verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    ComposerFieldLabel("Tags · ${localMeta.tags.size} of 4")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                        (localMeta.tags + quickPickTags.filter { it !in localMeta.tags }).forEach { tag ->
                            val isSelected = tag in localMeta.tags
                            MMChip("#$tag", selected = isSelected, onClick = {
                                if (isSelected) updateMeta { it.copy(tags = it.tags.filter { t -> t != tag }) }
                                else if (localMeta.tags.size < 4) updateMeta { it.copy(tags = (it.tags + tag).distinct().take(4)) }
                            })
                        }
                    }
                    if (localMeta.tags.size < 4) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                            Box(Modifier.weight(1f)) {
                                CircleField(tagInput, { tagInput = it.take(24) }, label = "Your own tag", placeholder = "launch")
                            }
                            TextAction("Add", {
                                val clean = tagInput.trim().lowercase().removePrefix("#")
                                if (clean.isNotEmpty() && clean !in localMeta.tags && localMeta.tags.size < 4) {
                                    updateMeta { it.copy(tags = (it.tags + clean).distinct().take(4)) }
                                    tagInput = ""
                                }
                            }, enabled = tagInput.trim().isNotEmpty())
                        }
                    }
                    ComposerFieldLabel("Shared list · ${localMeta.checklist.size} of 8")
                    localMeta.checklist.forEach { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                            IconButton(onClick = { updateMeta { m -> m.copy(checklist = m.checklist.map { if (it.id == item.id) it.copy(done = !it.done) else it }) } }) {
                                Icon(
                                    if (item.done) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline,
                                    if (item.done) "Mark incomplete" else "Mark complete",
                                    tint = if (item.done) MM.colors.success else MM.colors.inkMuted, modifier = Modifier.size(MMSize.iconSmall)
                                )
                            }
                            Text(item.text, style = MM.type.body, color = if (item.done) MM.colors.inkMuted else MM.colors.ink, modifier = Modifier.weight(1f))
                            IconButton(onClick = { updateMeta { m -> m.copy(checklist = m.checklist.filter { it.id != item.id }) } }) {
                                Icon(Icons.Rounded.Close, "Remove item", tint = MM.colors.inkMuted, modifier = Modifier.size(MMSize.iconSmall))
                            }
                        }
                    }
                    if (localMeta.checklist.size < 8) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                            Box(Modifier.weight(1f)) {
                                CircleField(checklistInput, { checklistInput = it.take(120) }, label = "Add an item", placeholder = "Bring snacks")
                            }
                            IconButton(
                                onClick = {
                                    val clean = checklistInput.trim()
                                    if (clean.isNotEmpty() && localMeta.checklist.size < 8) {
                                        val id = "chk_${System.currentTimeMillis()}_${localMeta.checklist.size}"
                                        updateMeta { it.copy(checklist = it.checklist + ChecklistItem(id = id, text = clean, done = false)) }
                                        checklistInput = ""
                                    }
                                },
                                enabled = checklistInput.trim().isNotEmpty()
                            ) { Icon(Icons.Rounded.Add, "Add item", tint = MM.colors.ink) }
                        }
                    }
                }
            }
        }

        // ---- prayer: anonymous, and the honest line about review ----
        if (type == PostType.Prayer) {
            ToggleRow("Post anonymously", "Nobody sees who asked, not even leaders.", state.anonymous, onAnonymous)
            if (circle.settings.prayerApproval) {
                Text("An admin reviews prayer requests before they appear.", style = MM.type.caption, color = MM.colors.inkMuted)
            }
        }

        state.error?.let { StatusLine(StatusKind.Error, it) }
    }
}

/**
 * The composer's frame: a tall sheet with the header pinned at the top, the writing in the middle (it scrolls), and
 * the media toolbar pinned at the bottom, riding the keyboard and clearing the navigation bar.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ComposerFrame(
    onDismiss: () -> Unit,
    inSheet: Boolean,
    header: @Composable ColumnScope.() -> Unit,
    toolbar: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().fillMaxHeight(if (inSheet) 0.92f else 1f).imePadding()) {
            header()
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MM.space.l, vertical = MM.space.s),
                verticalArrangement = Arrangement.spacedBy(MM.space.m),
                content = content
            )
            Column(Modifier.fillMaxWidth().navigationBarsPadding(), content = toolbar)
        }
    }
    if (inSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MM.colors.surfaceRaised,
            shape = MM.radius.sheet
        ) { body() }
    } else {
        Surface(color = MM.colors.surfaceRaised, shape = MM.radius.sheet, modifier = Modifier.fillMaxSize()) { body() }
    }
}

@Composable
private fun HorizontalDividerLine() {
    androidx.compose.material3.HorizontalDivider(thickness = MMSize.hairline, color = MM.colors.lineSoft)
}

/** The writing area: no box, no outline, a generous minimum height, the same body type as Notes. */
@Composable
private fun ComposerTextArea(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Box(Modifier.fillMaxWidth().heightIn(min = 140.dp)) {
        if (value.isEmpty()) Text(placeholder, style = MM.type.body, color = MM.colors.inkMuted)
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MM.type.body.copy(color = MM.colors.ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MM.colors.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
                .semantics { contentDescription = placeholder }
        )
    }
}

/** The compact Post button in the composer header: accent when ready, a quiet track when not. */
@Composable
private fun ComposerPostButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        shape = MM.radius.pill,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MM.colors.accent, contentColor = MM.colors.onAccent,
            disabledContainerColor = MM.colors.track, disabledContentColor = MM.colors.inkMuted
        ),
        contentPadding = PaddingValues(horizontal = MM.space.l, vertical = MM.space.xs),
        modifier = Modifier.heightIn(min = 36.dp)
    ) { Text(label, style = MM.type.bodyStrong) }
}

/** One tool in the composer's bottom bar: a 48 dp icon in muted ink, ink when its panel is open. */
@Composable
private fun ComposerTool(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, description, tint = when { !enabled -> MM.colors.inkFaint; active -> MM.colors.ink; else -> MM.colors.inkSecondary })
    }
}

@Composable
private fun ComposerFieldLabel(text: String) {
    Text(text, style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = MM.colors.inkSecondary)
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
    onMemberClick: ((String) -> Unit)? = null,
    circleId: String? = null,
    members: List<Member> = emptyList(),
    isWorkOrLearning: Boolean = false,
    draft: String? = null,
    onDraftChange: ((String) -> Unit)? = null
) {
    var localText by remember(thread?.postId) { mutableStateOf("") }
    val text = draft ?: localText
    val setText: (String) -> Unit = { v ->
        val clean = v.take(2000)
        if (onDraftChange != null) onDraftChange(clean) else localText = clean
    }
    val clipboard = LocalClipboardManager.current
    CircleSheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (thread != null && thread.comments.isNotEmpty()) "Comments (${thread.comments.size})" else "Comments",
                style = MM.type.title,
                color = MM.colors.ink,
                modifier = Modifier.semantics { heading() }
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, "Close", tint = MM.colors.inkSecondary)
            }
        }

        if (post != null) {
            Surface(
                shape = MM.radius.card,
                color = MM.colors.surfaceSunk,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(MM.space.l), verticalArrangement = Arrangement.spacedBy(MM.space.s)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (circleId != null) {
                            val postAuthorVersion = members.firstOrNull { it.uid == post.authorUid }?.avatarVersion
                            MemberAvatar(
                                circleId = circleId,
                                uid = post.authorUid,
                                name = post.authorName ?: "Member",
                                avatarVersion = postAuthorVersion,
                                size = 28.dp,
                                modifier = Modifier.clickable { onMemberClick?.invoke(post.authorUid ?: post.authorName.orEmpty()) }
                            )
                        } else {
                            InitialsAvatar(
                                post.authorName ?: "Member",
                                size = 28.dp,
                                modifier = Modifier.clickable { onMemberClick?.invoke(post.authorUid ?: post.authorName.orEmpty()) }
                            )
                        }
                        Text(
                            post.authorName ?: "Member",
                            style = MM.type.bodyStrong,
                            color = MM.colors.ink,
                            modifier = Modifier.clickable { onMemberClick?.invoke(post.authorUid ?: post.authorName.orEmpty()) }
                        )
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
                    if (circleId != null && post.attachments.isNotEmpty()) {
                        if (post.attachments.size == 1) {
                            val a = post.attachments.first()
                            AttachmentTile(
                                circleId = circleId,
                                a = a,
                                modifier = Modifier
                                    .padding(top = MM.space.xs)
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 200.dp)
                                    .clip(MM.radius.card),
                                contentDescription = if (a.isVideo) "Video attachment" else "Photo attachment"
                            )
                        } else {
                            Column(Modifier.padding(top = MM.space.xs), verticalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                                post.attachments.chunked(2).forEach { rowItems ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                                        rowItems.forEach { a ->
                                            AttachmentTile(
                                                circleId = circleId,
                                                a = a,
                                                modifier = Modifier.weight(1f).height(100.dp).clip(MM.radius.card),
                                                contentDescription = if (a.isVideo) "Video attachment" else "Photo attachment"
                                            )
                                        }
                                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        thread?.failure?.let { StatusLine(StatusKind.Warning, it.message) }

        if (thread != null) {
            thread.updates.forEach { u ->
                Surface(
                    shape = MM.radius.card,
                    color = MM.colors.surfaceSunk,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(MM.space.m)) {
                        Text(
                            "UPDATE FROM THE AUTHOR · ${relativeTime(now, u.createdAt).uppercase()}",
                            style = MM.type.overline,
                            color = MM.colors.inkMuted
                        )
                        SelectionContainer {
                            Text(u.body, style = MM.type.body, color = MM.colors.ink, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            if (thread.comments.isEmpty() && !thread.loading) {
                Text(
                    if (isWorkOrLearning) "No comments yet. Start the conversation."
                    else "No comments yet. Be the first to share an encouraging word or prayer.",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary,
                    modifier = Modifier.padding(vertical = MM.space.m)
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(MM.space.m)
                ) {
                    items(thread.comments, key = { it.id }) { cm ->
                        val isMyComment = cm.authorUid != null && cm.authorUid == myUid
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (circleId != null) {
                                val commentAuthorVersion = members.firstOrNull { it.uid == cm.authorUid }?.avatarVersion
                                MemberAvatar(
                                    circleId = circleId,
                                    uid = cm.authorUid,
                                    name = cm.authorName,
                                    avatarVersion = commentAuthorVersion,
                                    size = 36.dp,
                                    modifier = Modifier.clickable { onMemberClick?.invoke(cm.authorUid ?: cm.authorName) }
                                )
                            } else {
                                InitialsAvatar(
                                    cm.authorName,
                                    size = 36.dp,
                                    modifier = Modifier.clickable { onMemberClick?.invoke(cm.authorUid ?: cm.authorName) }
                                )
                            }
                            Box(Modifier.weight(1f)) {
                                Column {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            cm.authorName,
                                            style = MM.type.bodyStrong,
                                            color = MM.colors.ink,
                                            modifier = Modifier.clickable { onMemberClick?.invoke(cm.authorUid ?: cm.authorName) }
                                        )
                                        Text(
                                            relativeTime(now, cm.createdAt),
                                            style = MM.type.caption,
                                            color = MM.colors.inkMuted
                                        )
                                    }
                                    SelectionContainer {
                                        Text(
                                            cm.body,
                                            style = MM.type.body,
                                            color = MM.colors.ink,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                    Row(
                                        Modifier.fillMaxWidth().offset(x = -MM.space.m),
                                        horizontalArrangement = Arrangement.Start,
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
                setText,
                label = "Add a comment",
                placeholder = "Write an encouraging comment…",
                modifier = Modifier.weight(1f),
                maxLines = 4
            )
            Surface(
                shape = MM.radius.pill,
                color = if (text.isNotBlank()) MM.colors.ink else MM.colors.surfaceSunk,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MM.radius.pill)
                    .clickable(enabled = text.isNotBlank()) {
                        val toSend = text.trim()
                        if (toSend.isNotBlank()) {
                            onSend(toSend)
                            setText("")
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Send,
                        "Send comment",
                        tint = if (text.isNotBlank()) MM.colors.onInk else MM.colors.inkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** The invite code and a share button. The code is always written out so a pasted message works anywhere. */
@Composable
fun InviteSheet(
    state: InviteUiState,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    onCreateEmailInvite: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var selectedTab by remember { mutableStateOf(0) }
    var emailInput by remember { mutableStateOf("") }

    CircleSheet(onDismiss) {
        Text("Invite people", style = MM.type.title, color = MM.colors.ink, modifier = Modifier.semantics { heading() })
        Text("Only people with the code can join. Circles are stored on MeetingMind's servers, not end-to-end encrypted.", style = MM.type.secondary, color = MM.colors.inkSecondary)

        if (onCreateEmailInvite != null) {
            SegmentedControl(
                options = listOf("Invite code", "By email"),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it }
            )
        }

        if (onCreateEmailInvite != null && selectedTab == 1) {
            if (state.invitedEmail != null) {
                StatusLine(StatusKind.Ok, "Invite created for ${state.invitedEmail}")
            }
            CircleField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = "Email address",
                placeholder = "colleague@company.com",
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None
                )
            )
            PrimaryButton(
                "Send email invite",
                onClick = {
                    val trimmed = emailInput.trim()
                    if (trimmed.isNotBlank()) {
                        onCreateEmailInvite(trimmed)
                    }
                },
                enabled = emailInput.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth()
            )

            val invite = state.invite
            if (invite != null && state.shareText != null) {
                InviteCodePanel(invite.code) { clipboard.setText(AnnotatedString(invite.code)) }
                PrimaryButton("Share invite", {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, state.shareText)
                    runCatching { context.startActivity(Intent.createChooser(send, "Share invite").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, modifier = Modifier.fillMaxWidth())
                SecondaryButton("Copy message", { clipboard.setText(AnnotatedString(state.shareText)) }, modifier = Modifier.fillMaxWidth())
            }

            if (state.busy) StatusLine(StatusKind.Info, "Sending invite...")
            state.error?.let { StatusLine(StatusKind.Error, it) }
        } else {
            val invite = state.invite
            when {
                state.busy -> StatusLine(StatusKind.Info, "Making a code...")
                invite != null && state.shareText != null -> {
                    InviteCodePanel(invite.code) { clipboard.setText(AnnotatedString(invite.code)) }
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
}

/** The invite code, large and spaced so it reads aloud easily, with one Copy action. */
@Composable
private fun InviteCodePanel(code: String, onCopy: () -> Unit) {
    Surface(shape = MM.radius.card, color = MM.colors.surfaceSunk, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = MM.space.l, end = MM.space.xs, top = MM.space.m, bottom = MM.space.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("INVITE CODE", style = MM.type.overline, color = MM.colors.inkMuted)
                SelectionContainer {
                    Text(code, style = MM.type.display.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp)), color = MM.colors.ink)
                }
            }
            TextAction("Copy", onCopy)
        }
    }
}
