package com.craftflowtechnologies.meetingmind.feature.circles2

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.craftflowtechnologies.meetingmind.core.circles2.Circle
import com.craftflowtechnologies.meetingmind.core.circles2.MediaAttachment
import com.craftflowtechnologies.meetingmind.core.circles2.PostType
import com.craftflowtechnologies.meetingmind.core.scripture.ScriptureReferenceParser
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMChip
import com.craftflowtechnologies.meetingmind.core.ui.mm.PrimaryButton
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusKind
import com.craftflowtechnologies.meetingmind.core.ui.mm.StatusLine
import com.craftflowtechnologies.meetingmind.ui.theme.MM

private const val MAX_POST = 4000

/** The words for each kind of post: the heading over the box and the hint inside it. */
private fun hintFor(type: PostType, work: Boolean): String = when (type) {
    PostType.Prayer -> "What would you like prayer for?"
    PostType.Testimony -> "Tell the story of what God did…"
    PostType.Devotional -> "What spoke to you? Add your own thoughts…"
    PostType.Study -> "What did you learn? Add your own thoughts…"
    PostType.Encouragement -> "Share a word of encouragement…"
    PostType.Task -> "What needs doing, who owns it, and by when?"
    PostType.Decision -> "What was decided, and why?"
    PostType.Brief -> "Key points, decisions and next steps…"
    PostType.Standup -> "Done, doing next, anything in the way?"
    PostType.Blocker -> "What's in the way, and what would unblock it?"
    else -> if (work) "Share an update, question or note with the team…" else "Share something with the circle…"
}

/**
 * The new-post composer. One rounded writing card (words, attached photos and videos, and a tool row under them),
 * a devotional/study chooser when the post is one of those, and one clear send button. A post may carry
 * words together with media: people rarely share a video with nothing said.
 */
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
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) readPicked(context, uri)?.let { onPhoto(it) } ?: onPhotoRefused()
    }
    val type = state.type ?: return
    val c = MM.colors
    val availableTypes = remember(circle) { circle.offeredTypes }
    val parsedRef = remember(state.verse) { if (state.verse.isNotBlank()) ScriptureReferenceParser.parse(state.verse.trim()) else null }
    var chooser by remember { mutableStateOf<PostType?>(null) }
    var showVerse by remember(type) { mutableStateOf(state.verse.isNotBlank()) }

    CircleSheet(onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(type.verb, style = MM.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = c.ink)
                Text("Posting to ${circle.name}", style = MM.type.caption, color = c.inkSecondary)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Cancel", tint = c.inkSecondary) }
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MM.space.s)) {
            availableTypes.forEach { t -> MMChip(t.label, selected = t == type, onClick = { onTypeChange(t) }) }
        }

        if (state.fromAnswered) StatusLine(StatusKind.Info, "Thank God with the group! Edit this testimony however you like.")

        // Devotional and study posts start from something real: a one-tap chooser.
        if (type == PostType.Devotional || type == PostType.Study) {
            val devotional = type == PostType.Devotional
            Surface(
                onClick = { chooser = type },
                shape = RoundedCornerShape(16.dp), color = c.goldWash, border = BorderStroke(1.dp, c.gold.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = c.gold, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (devotional) "Choose a devotional" else "Choose from your study notes", style = MM.type.bodyStrong, color = c.ink)
                        Text(
                            if (devotional) "Today's reading or one you've saved" else "Your Bible study, sermon and reflection notes",
                            style = MM.type.caption, color = c.inkSecondary
                        )
                    }
                    Text("Browse", style = MM.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.gold)
                }
            }
        }

        // The writing card.
        Surface(shape = RoundedCornerShape(20.dp), color = c.surfaceSunk, border = BorderStroke(1.dp, c.line), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
                    if (state.body.isEmpty()) Text(hintFor(type, circle.isWork), style = MM.type.body, color = c.inkMuted)
                    BasicTextField(
                        value = state.body, onValueChange = { onBody(it.take(MAX_POST)) },
                        textStyle = MM.type.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        minLines = 4, maxLines = 12, modifier = Modifier.fillMaxWidth()
                    )
                }

                if (state.attachments.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.attachments.forEach { a ->
                            Surface(shape = RoundedCornerShape(12.dp), color = c.surfaceRaised, border = BorderStroke(1.dp, c.line)) {
                                Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(if (a.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Image, null, tint = CirclesPalette.Accent, modifier = Modifier.size(18.dp))
                                    Text(
                                        listOfNotNull(if (a.isVideo) "Video" else "Photo", a.durationMs?.takeIf { it > 0 }?.let(::formatDuration), formatBytes(a.bytes)).joinToString(" · "),
                                        style = MM.type.caption.copy(fontWeight = FontWeight.Medium), color = c.ink
                                    )
                                    IconButton(onClick = { onRemovePhoto(a) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Rounded.Close, "Remove attachment", tint = c.inkSecondary, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolPill(Icons.Rounded.Image, "Photo") { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    ToolPill(Icons.Rounded.Videocam, "Video") { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
                    if (!circle.isWork && type != PostType.Announcement) ToolPill(Icons.AutoMirrored.Filled.MenuBook, "Verse") { showVerse = !showVerse }
                    Spacer(Modifier.weight(1f))
                    Text("${state.body.length}/$MAX_POST", style = MM.type.caption.copy(fontSize = 11.sp), color = if (state.body.length > 3800) CirclesPalette.RoseInk else c.inkMuted)
                }
            }
        }

        if (showVerse && !circle.isWork && type != PostType.Announcement) {
            CircleField(state.verse, onVerse, label = "Scripture verse (optional)", placeholder = "e.g. Romans 8:28, Psalm 23:1")
            if (parsedRef != null) PostVerseCard(state.verse.trim(), onOpenVerse = {})
        }

        if (type == PostType.Prayer) {
            ToggleRow("Post anonymously", "Nobody sees who asked, admins included.", state.anonymous, onAnonymous)
            if (circle.settings.prayerApproval) StatusLine(StatusKind.Info, "An admin reviews prayer requests before they appear in the circle.")
        }

        state.error?.let { StatusLine(StatusKind.Error, it) }

        PrimaryButton(
            if (state.sending) "Posting…"
            else if (type == PostType.Prayer && circle.settings.prayerApproval) "Send for approval"
            else if (circle.isWork) "Share with team"
            else "Share with circle",
            onSend,
            enabled = !state.sending && (state.body.isNotBlank() || state.attachments.isNotEmpty()),
            modifier = Modifier.fillMaxWidth()
        )
    }

    when (chooser) {
        PostType.Devotional -> DevotionalPickerSheet(
            onPick = { p -> onBody(p.body); if (p.verse.isNotBlank()) { onVerse(p.verse); showVerse = true }; chooser = null },
            onDismiss = { chooser = null }
        )
        PostType.Study -> StudyNotesPickerSheet(
            onPick = { p -> onBody(p.body); chooser = null },
            onDismiss = { chooser = null }
        )
        else -> Unit
    }
}

@Composable
private fun ToolPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(999.dp), color = MM.colors.surfaceRaised, border = BorderStroke(1.dp, MM.colors.line)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = MM.colors.accent, modifier = Modifier.size(16.dp))
            Text(label, style = MM.type.caption.copy(fontWeight = FontWeight.Medium), color = MM.colors.ink)
        }
    }
}
