package com.craftflowtechnologies.meetingmind.feature.work.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftflowtechnologies.meetingmind.core.database.InboxItemEntity
import com.craftflowtechnologies.meetingmind.core.ui.work.projectColor
import com.craftflowtechnologies.meetingmind.core.work.Filing
import com.craftflowtechnologies.meetingmind.core.work.FilingAction
import com.craftflowtechnologies.meetingmind.core.work.FilingTarget
import com.craftflowtechnologies.meetingmind.core.work.InboxStatus
import com.craftflowtechnologies.meetingmind.feature.work.InboxLinkedMeeting
import com.craftflowtechnologies.meetingmind.feature.work.formatSource
import com.craftflowtechnologies.meetingmind.feature.work.kindMeta
import com.craftflowtechnologies.meetingmind.feature.work.relativeTimeLabel
import com.craftflowtechnologies.meetingmind.ui.theme.MM

/**
 * The four ways in (#8a): Voice note · Scan · Paste note · File. Each opens the same thing it names;
 * nothing is saved until the person has chosen it.
 */
@Composable
internal fun InboxCaptureTiles(
    onVoice: () -> Unit,
    onScan: () -> Unit,
    onPaste: () -> Unit,
    onFile: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CaptureTile(Icons.Filled.GraphicEq, "Voice note", "Audio or video", Modifier.weight(1f), onVoice)
        CaptureTile(Icons.Filled.DocumentScanner, "Scan", "Camera to PDF", Modifier.weight(1f), onScan)
        CaptureTile(Icons.Filled.ContentPaste, "Paste note", "Text or a link", Modifier.weight(1f), onPaste)
        CaptureTile(Icons.AutoMirrored.Filled.InsertDriveFile, "File", "PDF, image, any", Modifier.weight(1f), onFile)
    }
}

@Composable
private fun CaptureTile(icon: ImageVector, label: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(MM.radius.card)
            .background(MM.colors.surface)
            .border(1.dp, MM.colors.lineSoft, MM.radius.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier.size(32.dp).clip(MM.radius.small).background(MM.colors.accentWash),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MM.colors.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MM.type.caption, color = MM.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One open item (#8a). Kind icon, title, source and arrival time, a two-line summary when there is one,
 * the state (transcribing, reading page N of M, couldn't be read), the suggestion pill in the project's
 * colour, and the actions: File to <project> (one tap, confirm-only), Change…, and Dismiss (with Undo, by the caller).
 */
@Composable
internal fun InboxItemCard(
    item: InboxItemEntity,
    linked: InboxLinkedMeeting?,
    suggestion: Filing?,
    onOpen: () -> Unit,
    onFile: (Filing) -> Unit,
    onChange: () -> Unit,
    onDismiss: () -> Unit,
    /** Starts transcription for a recording or a YouTube link that has not started yet; null when not applicable. */
    onTranscribe: (() -> Unit)? = null
) {
    val kind = kindMeta(item.kind)
    val source = formatSource(item.source) ?: kind.label
    val summary = InboxRules.summaryOf(item, linked)
    val state = InboxRules.stateLine(item)
    val proposal = suggestion?.takeIf { item.status == InboxStatus.PROPOSED.name }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .clip(MM.radius.card)
            .background(MM.colors.surface)
            .border(1.dp, MM.colors.lineSoft, MM.radius.card)
            .clickable(onClick = onOpen)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(38.dp).clip(MM.radius.small).background(MM.colors.surfaceSunk),
                contentAlignment = Alignment.Center
            ) {
                Icon(kind.icon, contentDescription = null, tint = MM.colors.inkSecondary, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    item.title?.takeIf { it.isNotBlank() } ?: kind.label,
                    style = MM.type.bodyStrong,
                    color = MM.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$source · ${relativeTimeLabel(item.createdAt)}",
                    style = MM.type.caption,
                    color = MM.colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                summary?.let {
                    Text(
                        it,
                        style = MM.type.secondary,
                        color = MM.colors.inkSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                state?.let {
                    Text(
                        it.text,
                        style = MM.type.caption,
                        color = if (it.problem) MM.colors.danger else MM.colors.inkSecondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        proposal?.let { SuggestionPill(it) }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            proposal?.let { p ->
                Button(onClick = { onFile(p) }) { Text(InboxRules.fileToLabel(p.targetLabel ?: "a note")) }
            }
            onTranscribe?.let { start ->
                OutlinedButton(onClick = start) { Text("Transcribe") }
            }
            OutlinedButton(onClick = onChange) { Text("Change…") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

/** "Suggested · Acme Integration", with a dot in the project's colour. */
@Composable
private fun SuggestionPill(filing: Filing) {
    val tint = filing.targetId?.let { projectColor(it) } ?: MM.colors.accent
    Row(
        Modifier
            .padding(top = 10.dp)
            .clip(MM.radius.pill)
            .background(MM.colors.surfaceSunk)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(MM.radius.pill).background(tint))
        Spacer(Modifier.width(6.dp))
        Text(
            "Suggested · ${destinationName(filing)}",
            style = MM.type.caption,
            color = MM.colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A filed item, in the Filed list. Tapping it opens its item screen, which holds Open project and Undo. */
@Composable
internal fun InboxFiledRow(item: InboxItemEntity, onOpen: () -> Unit) {
    val target = InboxRules.suggestion(item)?.let { destinationName(it) } ?: "a note"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .clip(MM.radius.card)
            .background(MM.colors.surface)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MM.colors.success, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                item.title?.takeIf { it.isNotBlank() } ?: "Filed item",
                style = MM.type.bodyStrong,
                color = MM.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Filed to $target · ${relativeTimeLabel(item.processedAt ?: item.createdAt)}",
                style = MM.type.caption,
                color = MM.colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** #8g: nothing is waiting. Says what belongs here and that nothing is filed without the person. */
@Composable
internal fun InboxEmptyState(onSeeFiled: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(MM.radius.card)
            .background(MM.colors.surface)
            .border(1.dp, MM.colors.lineSoft, MM.radius.card)
            .padding(20.dp)
    ) {
        Text("Inbox clear", style = MM.type.heading, color = MM.colors.ink)
        Text(
            "Nothing is waiting for you. MeetingMind suggests where each thing belongs; you decide.",
            style = MM.type.secondary,
            color = MM.colors.inkSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text("What belongs here", style = MM.type.overline, color = MM.colors.inkMuted, modifier = Modifier.padding(top = 14.dp))
        listOf(
            "Voice notes and recordings, transcribed on this phone",
            "PDFs and scans, read on this phone",
            "Links and text you share from any app"
        ).forEach { line ->
            Text("· $line", style = MM.type.secondary, color = MM.colors.inkSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.padding(top = 14.dp)) {
            OutlinedButton(onClick = onSeeFiled) { Text("See filed") }
        }
    }
}

/** The project or organisation or person a filing goes to, in words. */
internal fun destinationName(filing: Filing): String = when (filing.action) {
    FilingAction.DECISION -> "Decision in ${filing.targetLabel ?: "Inbox"}"
    FilingAction.CONTACT -> "Person ${filing.contactName ?: filing.contactEmail.orEmpty()}".trim()
    else -> filing.targetLabel ?: "Keep as a note"
}

/** Whether a filing goes to a person's page, which the item screen calls a recognised person. */
internal fun isPersonTarget(filing: Filing): Boolean = filing.target == FilingTarget.PERSON
