package com.craftflowtechnologies.meetingmind.feature.work.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity
import com.craftflowtechnologies.meetingmind.core.documents.DocumentReadState
import com.craftflowtechnologies.meetingmind.core.ui.work.ProgressRing
import com.craftflowtechnologies.meetingmind.core.ui.work.SourceChip
import com.craftflowtechnologies.meetingmind.core.ui.work.TrustKind
import com.craftflowtechnologies.meetingmind.core.ui.work.TrustMark
import com.craftflowtechnologies.meetingmind.core.ui.work.WhereItRan
import com.craftflowtechnologies.meetingmind.core.ui.work.projectColor
import com.craftflowtechnologies.meetingmind.core.work.Filing
import com.craftflowtechnologies.meetingmind.core.work.FilingAction
import com.craftflowtechnologies.meetingmind.core.work.InboxStatus
import com.craftflowtechnologies.meetingmind.feature.work.InboxInlineAudioPlayer
import com.craftflowtechnologies.meetingmind.feature.work.InboxLinkedMeeting
import com.craftflowtechnologies.meetingmind.feature.work.WorkViewModel
import com.craftflowtechnologies.meetingmind.feature.work.formatImportDateTime
import com.craftflowtechnologies.meetingmind.feature.work.formatSource
import com.craftflowtechnologies.meetingmind.feature.work.kindMeta
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import kotlinx.coroutines.launch
import java.io.File

/**
 * The item screen for one inbox item (#8b, #8c, #8c2, #8c4, #8f). It replaces the list inside the same
 * Work Inbox route. Order: player (audio) · provenance · reading state · failed state · what it says ·
 * honest notes · MeetingMind suggests (with grounded items, each with a checkbox and a page chip) ·
 * filed state · transcript with the grounded sentence highlighted.
 *
 * Nothing is filed until "File it" is tapped; the checked items are the only ones filed.
 *
 * @param onOpenDocument opens the viewer; the page is the one a chip names, or null for the first page.
 * @param onOpenProject opens the project a filed item went to.
 */
@Composable
fun InboxItemScreen(
    item: InboxItemEntity,
    linked: InboxLinkedMeeting?,
    viewModel: WorkViewModel,
    onBack: () -> Unit,
    onChange: () -> Unit,
    onDismiss: () -> Unit,
    onOpenDocument: ((Int?) -> Unit)?,
    onOpenProject: ((String) -> Unit)?,
    onOpenNote: (() -> Unit)?,
    onOpenTranscript: (() -> Unit)?,
    onRetryTranscription: () -> Unit,
    onRetryRead: () -> Unit,
    onConfigureTranscribe: (() -> Unit)?,
    onFailed: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    val kindLabel = kindMeta(item.kind).label
    val isDocument = InboxRules.isDocument(item)
    val isYouTube = InboxRules.isYouTube(item)
    val isMediaItem = InboxRules.isMedia(item) || isYouTube
    val readState = DocumentReadState.from(item.readStatus, item.pageCount, item.readPages)
    val filed = item.status == InboxStatus.FILED.name
    val suggestion = InboxRules.suggestion(item)
    val proposal = suggestion?.takeIf { !filed && item.status == InboxStatus.PROPOSED.name }
    val summary = InboxRules.summaryOf(item, linked)
    val transcript = linked?.transcriptPreview?.takeIf { it.isNotBlank() }
    val runsOnDevice = viewModel.inboxRunsOnDevice()

    // The tasks the person has unticked. Everything the suggestion holds starts ticked.
    var unticked by remember(item.id) { mutableStateOf<Set<String>>(emptySet()) }

    val transcriptionFailed = item.transcriptionStatus == "FAILED"
    val readFailed = readState == DocumentReadState.Locked || readState == DocumentReadState.Failed
    val problem = transcriptionFailed || readFailed
    val reading = isDocument && (readState == DocumentReadState.Pending || readState is DocumentReadState.Reading)
    val waitingForSuggestion = isDocument && !filed && proposal == null &&
        (readState == DocumentReadState.Pending || readState is DocumentReadState.Reading)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MM.colors.ink)
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    item.title?.takeIf { it.isNotBlank() } ?: kindLabel,
                    style = MM.type.heading,
                    color = MM.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(kindLabel, style = MM.type.caption, color = MM.colors.inkMuted)
            }
        }

        // The player for audio and video (#8b).
        if (isMediaItem) {
            item.uri?.let { path ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    InboxInlineAudioPlayer(filePath = path, durationMs = item.mediaDurationMs)
                }
            }
        }

        // Provenance (#8b): From, the recognised person, Arrived, File and size.
        val fileSize = item.uri?.let { path -> runCatching { File(path).length() }.getOrNull() }
        Panel {
            KeyRow("From", formatSource(item.source) ?: kindLabel)
            proposal?.takeIf { isPersonTarget(it) }?.let { KeyRow("Recognised as", it.targetLabel ?: "a person") }
            KeyRow("Arrived", formatImportDateTime(item.createdAt))
            KeyRow("File", listOfNotNull(item.uri?.let { File(it).name }, fileSize?.let { formatBytes(it) }).joinToString(" · ").ifBlank { "Not kept" })
            if (isDocument && readState is DocumentReadState.Done) {
                KeyRow("Pages", InboxRules.pagesLabel(readState.pageCount))
            }
        }

        // A recording or YouTube link that has not been transcribed yet: start it here, or change its settings.
        if (isMediaItem && item.transcriptionStatus == "PENDING" && !filed) {
            Panel {
                Text(
                    if (isYouTube) "Extract the YouTube transcript" else "Not transcribed yet",
                    style = MM.type.bodyStrong,
                    color = MM.colors.ink
                )
                Text(
                    "Transcribe it to read what was said. You can leave; it carries on.",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetryTranscription) { Text("Transcribe") }
                    onConfigureTranscribe?.let { settings ->
                        OutlinedButton(onClick = settings) { Text("Settings…") }
                    }
                }
            }
        }

        // Reading state with page progress (#8c).
        if (reading) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val read = readState as? DocumentReadState.Reading
                    ProgressRing(done = read?.readPages ?: 0, total = read?.pageCount ?: 1)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            InboxRules.stateLine(item)?.text ?: "Reading on this phone",
                            style = MM.type.bodyStrong,
                            color = MM.colors.ink
                        )
                        Text(
                            "You can leave. When it finishes, MeetingMind suggests where it belongs, and every claim cites its page.",
                            style = MM.type.caption,
                            color = MM.colors.inkSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Failed state (#8f): the reason in plain words, then what to do next.
        if (problem && !filed) {
            Panel(danger = true) {
                Text("This couldn't be read", style = MM.type.bodyStrong, color = MM.colors.danger)
                Text(
                    if (transcriptionFailed) "The recording is safe. It couldn't be transcribed on this phone."
                    else readState.message().orEmpty(),
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (transcriptionFailed && isMediaItem) {
                        Button(onClick = onRetryTranscription) { Text("Try again") }
                    } else if (readState == DocumentReadState.Failed) {
                        Button(onClick = onRetryRead) { Text("Try again") }
                    }
                    OutlinedButton(onClick = onChange) { Text("Choose a place") }
                }
                Row(Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }

        // What it says (#8c2): an AI summary, marked as a suggestion, and where it ran.
        summary?.let {
            Panel {
                Text("What it says", style = MM.type.overline, color = MM.colors.inkMuted)
                Text(it, style = MM.type.body, color = MM.colors.ink, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrustMark(TrustKind.SUGGESTED)
                    WhereItRan(onDevice = runsOnDevice)
                }
            }
        }

        // Honest notes (#8c4 and the other states): what could not be done, and why.
        val note: String? = when {
            isYouTube -> null
            item.kind == "URL" -> "Reading web pages: later. Links are filed by name for now."
            isDocument && !problem -> readState.message()
            else -> null
        }
        note?.let {
            Panel {
                Text(it, style = MM.type.secondary, color = MM.colors.inkSecondary)
            }
        }

        if (isDocument) {
            Panel {
                OutlinedButton(onClick = { onOpenDocument?.invoke(null) }, enabled = onOpenDocument != null) {
                    Text("Open document")
                }
            }
        }

        // MeetingMind suggests (#8b, #8c2): the destination, why, the grounded items, and File it / Change.
        if (proposal != null) {
            val tasks = proposal.tasks
            val checked = tasks.filter { it !in unticked }
            val decision = proposal.decision
            Panel(accent = true) {
                Text("MeetingMind suggests", style = MM.type.overline, color = MM.colors.accent)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(MM.radius.pill).background(proposal.targetId?.let { projectColor(it) } ?: MM.colors.accent))
                    Spacer(Modifier.width(8.dp))
                    Text(destinationLine(proposal), style = MM.type.bodyStrong, color = MM.colors.ink)
                }
                Text(
                    "Why: " + (proposal.reason ?: "from what was shared"),
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                tasks.forEach { task ->
                    val page = proposal.citations[task]?.page
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = task !in unticked,
                            onCheckedChange = { on -> unticked = if (on) unticked - task else unticked + task }
                        )
                        Text(
                            "Task: $task",
                            style = MM.type.secondary,
                            color = MM.colors.ink,
                            modifier = Modifier.weight(1f)
                        )
                        page?.let { p ->
                            SourceChip(label = "p. $p", onClick = { onOpenDocument?.invoke(p) })
                        }
                    }
                }
                decision?.let { d ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Decision: $d", style = MM.type.secondary, color = MM.colors.ink, modifier = Modifier.weight(1f))
                        proposal.citations[d]?.page?.let { p ->
                            SourceChip(label = "p. $p", onClick = { onOpenDocument?.invoke(p) })
                        }
                    }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        // Only the ticked tasks are filed. With none ticked, the item is filed as a note.
                        val action = if (proposal.action == FilingAction.TASKS && checked.isEmpty()) FilingAction.NOTE else proposal.action
                        val toFile = proposal.copy(
                            action = action,
                            tasks = checked,
                            citations = proposal.citations.filterKeys { it in checked || it == decision }
                        )
                        scope.launch {
                            if (!viewModel.fileInbox(item, toFile)) onFailed("Couldn't file it")
                        }
                    }) { Text("File it") }
                    OutlinedButton(onClick = onChange) { Text("Change") }
                }
            }
        } else if (waitingForSuggestion) {
            Panel {
                Text(
                    "The suggestion comes when reading finishes. Nothing is filed until you confirm.",
                    style = MM.type.secondary,
                    color = MM.colors.inkSecondary
                )
            }
        } else if (!filed) {
            Panel {
                Text("No suggestion yet", style = MM.type.bodyStrong, color = MM.colors.ink)
                Text(
                    "Works without AI. Pick a place; MeetingMind remembers for next time.",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(Modifier.padding(top = 10.dp)) {
                    Button(onClick = onChange) { Text("File to…") }
                }
            }
        }

        // Filed state (#8b): where it went, Open project, and Undo.
        if (filed) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MM.colors.success, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Filed to ${suggestion?.let { destinationName(it) } ?: "a note"}",
                        style = MM.type.bodyStrong,
                        color = MM.colors.ink
                    )
                }
                Text(
                    "Find it in the project's notes. Tasks you added are in Tasks.",
                    style = MM.type.caption,
                    color = MM.colors.inkSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val projectTarget = suggestion?.targetId
                    if (onOpenProject != null && projectTarget != null) {
                        OutlinedButton(onClick = { onOpenProject(projectTarget) }) { Text("Open project") }
                    } else if (onOpenNote != null) {
                        OutlinedButton(onClick = { onOpenNote() }) { Text("Open note") }
                    }
                    TextButton(onClick = { viewModel.unfileInbox(item.id) }) { Text("Undo") }
                }
            }
        }

        // Transcript with the grounded sentence highlighted (#8b).
        transcript?.let { text ->
            val highlight = MM.colors.accentWash
            val span = remember(text, claimsOf(proposal)) { InboxRules.groundedSpan(text, claimsOf(proposal)) }
            val annotated = remember(text, span, highlight) {
                buildAnnotatedString {
                    val r = span
                    if (r == null) {
                        append(text)
                    } else {
                        append(text.substring(0, r.first))
                        withStyle(SpanStyle(background = highlight)) { append(text.substring(r.first, r.last + 1)) }
                        append(text.substring(r.last + 1))
                    }
                }
            }
            Panel {
                Text("Transcript", style = MM.type.overline, color = MM.colors.inkMuted)
                Text(annotated, style = MM.type.secondary, color = MM.colors.ink, modifier = Modifier.padding(top = 6.dp))
                onOpenTranscript?.let {
                    TextButton(onClick = it, modifier = Modifier.padding(top = 4.dp)) { Text("Open transcript") }
                }
            }
        }
    }
}

private fun claimsOf(filing: Filing?): List<String> =
    filing?.let { it.tasks + listOfNotNull(it.decision) }.orEmpty()

private fun destinationLine(filing: Filing): String = when (filing.action) {
    FilingAction.NOTE -> "Keep as a note in ${filing.targetLabel ?: "Inbox"}"
    FilingAction.TASKS -> "Make tasks in ${filing.targetLabel ?: "Inbox"}"
    FilingAction.DECISION -> "Record a decision in ${filing.targetLabel ?: "Inbox"}"
    FilingAction.CONTACT -> "Add ${filing.contactName ?: filing.contactEmail ?: "a person"} to people"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%d KB".format(bytes / 1_000L)
    else -> "$bytes B"
}

@Composable
private fun Panel(
    accent: Boolean = false,
    danger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MM.radius.card)
            .background(if (accent) MM.colors.accentWash else MM.colors.surface)
            .border(1.dp, if (danger) MM.colors.danger else MM.colors.lineSoft, MM.radius.card)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun KeyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MM.type.caption, color = MM.colors.inkMuted, modifier = Modifier.width(104.dp))
        Text(value, style = MM.type.secondary, color = MM.colors.ink, modifier = Modifier.weight(1f))
    }
}
