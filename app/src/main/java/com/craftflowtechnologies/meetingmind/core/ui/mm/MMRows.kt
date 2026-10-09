package com.craftflowtechnologies.meetingmind.core.ui.mm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import com.craftflowtechnologies.meetingmind.ui.theme.MMSize

/** A generic row: leading slot, title and subtitle, trailing meta text and a trailing slot. 48 dp minimum. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val base = if (onClick != null) modifier.clickable(role = Role.Button, onClick = onClick) else modifier
    Row(
        base.fillMaxWidth().heightIn(min = MMSize.minTouch).padding(vertical = MM.space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(MM.space.m)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MM.type.bodyStrong, color = MM.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MM.type.secondary, color = MM.colors.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (meta != null) { Spacer(Modifier.width(MM.space.s)); Text(meta, style = MM.type.caption, color = MM.colors.inkMuted) }
        if (trailing != null) { Spacer(Modifier.width(MM.space.s)); trailing() }
    }
}

enum class NoteTranscriptStatus { None, Processing, Ready, Failed }

/** What a [NoteRow] shows. Built by the screen from its own note model. */
@Immutable
data class NoteRowModel(
    val title: String,
    val preview: String = "",
    /** The space's colour, shown as a thin marker. */
    val spaceColor: Color,
    val hasRecording: Boolean = false,
    val transcriptStatus: NoteTranscriptStatus = NoteTranscriptStatus.None,
    val taskCount: Int = 0,
    val attachmentCount: Int = 0,
    val timeLabel: String = "",
    val pinned: Boolean = false,
    val isPrivate: Boolean = false
)

private fun NoteRowModel.summary(): String = buildList {
    add(title)
    if (pinned) add("Pinned")
    if (isPrivate) add("Private")
    if (hasRecording) add("Has recording")
    when (transcriptStatus) {
        NoteTranscriptStatus.Processing -> add("Transcript processing")
        NoteTranscriptStatus.Ready -> add("Transcript ready")
        NoteTranscriptStatus.Failed -> add("Transcript failed")
        NoteTranscriptStatus.None -> Unit
    }
    if (taskCount > 0) add("$taskCount tasks")
    if (attachmentCount > 0) add("$attachmentCount attachments")
    if (timeLabel.isNotBlank()) add(timeLabel)
}.joinToString(", ")

@Composable
private fun Glyph(icon: ImageVector, tint: Color, label: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(MMSize.iconSmall))
        if (label != null) {
            Spacer(Modifier.width(MM.space.xs))
            Text(label, style = MM.type.caption, color = tint)
        }
    }
}

/** A note in a list: space marker, title, two-line preview, one quiet line of status icons, time. */
@Composable
fun NoteRow(
    note: NoteRowModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val c = MM.colors
    val summary = note.summary()
    val base = if (onClick != null) modifier.clickable(role = Role.Button, onClick = onClick) else modifier
    Row(
        base.fillMaxWidth().heightIn(min = MMSize.minTouch).height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) { contentDescription = summary }
            .padding(vertical = MM.space.s)
    ) {
        Box(
            Modifier.fillMaxHeight().width(MMSize.marker).background(note.spaceColor, MM.radius.pill)
        )
        Spacer(Modifier.width(MM.space.m))
        Column(Modifier.weight(1f)) {
            Text(note.title, style = MM.type.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note.preview.isNotBlank()) {
                Text(note.preview, style = MM.type.secondary, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val hasIcons = note.hasRecording || note.transcriptStatus != NoteTranscriptStatus.None ||
                note.taskCount > 0 || note.attachmentCount > 0
            if (hasIcons) {
                Row(
                    Modifier.padding(top = MM.space.xs),
                    horizontalArrangement = Arrangement.spacedBy(MM.space.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (note.hasRecording) Glyph(Icons.Rounded.Mic, c.inkSecondary)
                    when (note.transcriptStatus) {
                        NoteTranscriptStatus.Ready -> Glyph(Icons.Rounded.Subject, c.inkSecondary)
                        NoteTranscriptStatus.Processing -> Glyph(Icons.Rounded.Subject, c.inkMuted, "Transcribing")
                        NoteTranscriptStatus.Failed -> Glyph(Icons.Rounded.Subject, c.danger, "Failed")
                        NoteTranscriptStatus.None -> Unit
                    }
                    if (note.taskCount > 0) Glyph(Icons.Rounded.CheckCircleOutline, c.inkSecondary, note.taskCount.toString())
                    if (note.attachmentCount > 0) Glyph(Icons.Rounded.AttachFile, c.inkSecondary, note.attachmentCount.toString())
                }
            }
        }
        Spacer(Modifier.width(MM.space.s))
        Column(horizontalAlignment = Alignment.End) {
            if (note.timeLabel.isNotBlank()) Text(note.timeLabel, style = MM.type.caption, color = c.inkMuted)
            if (note.pinned || note.isPrivate) {
                Row(Modifier.padding(top = MM.space.xs), horizontalArrangement = Arrangement.spacedBy(MM.space.xs)) {
                    if (note.pinned) Glyph(Icons.Rounded.PushPin, c.inkMuted)
                    if (note.isPrivate) Glyph(Icons.Rounded.Lock, c.inkMuted)
                }
            }
        }
    }
}
